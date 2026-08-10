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
  it('renders the independent audit page instead of the overview fallback', () => {
    renderAudit();

    expect(screen.getByText('操作审计记录')).toBeInTheDocument();
    expect(screen.getByText('仅 admin / superadmin')).toBeInTheDocument();
    expect(screen.getByText('req_admin_7781')).toBeInTheDocument();
    expect(screen.queryByText('AgentRun 总量')).not.toBeInTheDocument();
  });

  it('filters audit rows and opens the read-only detail dialog', async () => {
    const user = userEvent.setup();
    renderAudit();

    await user.click(screen.getByRole('combobox', { name: '审计结果筛选' }));
    await user.click(screen.getByRole('option', { name: 'failed' }));
    expect(screen.getByText('暂无匹配的操作审计')).toBeInTheDocument();

    await user.click(screen.getByRole('combobox', { name: '审计结果筛选' }));
    await user.click(screen.getByRole('option', { name: '全部结果' }));
    await user.click(screen.getAllByRole('button', { name: '查看详情' })[0]);

    expect(screen.getByRole('dialog')).toHaveTextContent('操作审计详情');
    expect(screen.getByText('将 user_10003 状态从 active 更新为 locked')).toBeInTheDocument();
    expect(within(screen.getByRole('dialog')).getByText('trace_admin_7781')).toBeInTheDocument();
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
