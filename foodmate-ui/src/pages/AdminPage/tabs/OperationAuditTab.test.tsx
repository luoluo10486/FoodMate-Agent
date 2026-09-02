import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { AdminPage } from '../AdminPage';
import { resolveAdminAccess } from './AdminShared';

function renderAudit() {
  return render(
    <MemoryRouter initialEntries={['/admin?view=audit']}>
      <Routes>
        <Route path="/admin/*" element={<AdminPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('OperationAuditSection', () => {
  it('renders the Figma audit board with filters, metrics, six rows, pagination, and detail cards', () => {
    renderAudit();

    expect(screen.getByRole('heading', { name: '操作审计', level: 1 })).toBeInTheDocument();
    expect(screen.getByText('生产环境')).toBeInTheDocument();
    expect(screen.getByText('数据刷新：刚刚')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '导出审计' })).toBeInTheDocument();
    expect(screen.getByRole('region', { name: '操作审计筛选' })).toBeInTheDocument();
    expect(screen.getAllByText('结果:')).toHaveLength(1);
    expect(screen.getAllByText('目标类型:')).toHaveLength(1);
    expect(screen.getAllByText('动作:')).toHaveLength(1);
    expect(screen.getByPlaceholderText('搜索 request_id / trace_id...')).toBeInTheDocument();

    expect(screen.getByText('近 24h 操作')).toBeInTheDocument();
    expect(screen.getByText('1,284')).toBeInTheDocument();
    expect(screen.getByText('失败操作')).toBeInTheDocument();
    expect(screen.getByText('18')).toBeInTheDocument();
    expect(screen.getByText('待复核')).toBeInTheDocument();
    expect(screen.getByText('4 条')).toBeInTheDocument();

    expect(screen.getByRole('region', { name: '操作审计明细' })).toBeInTheDocument();
    expect(screen.getAllByRole('row')[0].parentElement).toHaveClass('auditFigmaTableHeader');
    expect(screen.getByText('更新权限')).toBeInTheDocument();
    expect(screen.getByText('tool_registry')).toBeInTheDocument();
    expect(screen.getByText('user_1234567')).toBeInTheDocument();
    expect(screen.getByText('ERR_POLICY')).toBeInTheDocument();
    expect(screen.getByText('DENIED')).toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: '查看详情' })).toHaveLength(6);
    expect(screen.getByText('显示第 1 到 6 条，共 1,284 条结果')).toBeInTheDocument();
    expect(screen.getByRole('navigation', { name: '操作审计分页' })).toBeInTheDocument();

    expect(screen.getByText('审计详情')).toBeInTheDocument();
    expect(screen.getByText('最近异常')).toBeInTheDocument();
    expect(screen.getByText('关联追踪')).toBeInTheDocument();
    expect(screen.queryByText('操作审计记录')).not.toBeInTheDocument();
  });

  it('filters Figma audit rows and opens the read-only detail dialog', async () => {
    const user = userEvent.setup();
    renderAudit();

    await user.click(screen.getByRole('combobox', { name: '审计结果筛选' }));
    await user.click(screen.getByRole('option', { name: '失败' }));
    expect(screen.getByText('operator_chen')).toBeInTheDocument();
    expect(screen.queryByText('tool_registry')).not.toBeInTheDocument();

    await user.click(screen.getByRole('combobox', { name: '审计结果筛选' }));
    await user.click(screen.getByRole('option', { name: '全部' }));
    await user.type(screen.getByPlaceholderText('搜索 request_id / trace_id...'), 'req_7c75');
    expect(screen.getByText('sql_query')).toBeInTheDocument();
    expect(screen.queryByText('tool_registry')).not.toBeInTheDocument();

    await user.clear(screen.getByPlaceholderText('搜索 request_id / trace_id...'));
    await user.click(screen.getAllByRole('button', { name: '查看详情' })[0]);

    expect(screen.getByRole('dialog')).toHaveTextContent('操作审计详情');
    expect(screen.getByText('更新权限 tool_registry')).toBeInTheDocument();
    expect(within(screen.getByRole('dialog')).getByText('tr_88192a')).toBeInTheDocument();
  });
});

describe('admin access matrix', () => {
  it('allows only authenticated operator-level roles into admin pages', () => {
    expect(resolveAdminAccess('authenticated', 'user')).toMatchObject({
      canAccess: false,
      canManage: false,
      canViewUserDetails: false,
      canViewAudit: false,
      canRestoreResources: false,
    });
    expect(resolveAdminAccess('authenticated', 'operator')).toMatchObject({
      canAccess: true,
      canManage: false,
      canViewUserDetails: true,
      canViewAudit: false,
      canRestoreResources: false,
    });
    expect(resolveAdminAccess('authenticated', 'admin')).toMatchObject({
      canAccess: true,
      canManage: true,
      canViewUserDetails: true,
      canViewAudit: true,
      canRestoreResources: true,
    });
    expect(resolveAdminAccess('authenticated', 'superadmin')).toMatchObject({
      canAccess: true,
      canManage: true,
      canViewUserDetails: true,
      canViewAudit: true,
      canRestoreResources: true,
    });
  });

  it('rejects anonymous and expired sessions regardless of role', () => {
    expect(resolveAdminAccess('anonymous', 'admin').canAccess).toBe(false);
    expect(resolveAdminAccess('expired', 'superadmin').canAccess).toBe(false);
  });
});
