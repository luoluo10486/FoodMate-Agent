import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { AdminPage } from '../AdminPage';

function renderDeleted() {
  return render(
    <MemoryRouter initialEntries={['/admin/deleted']}>
      <Routes>
        <Route path="/admin/*" element={<AdminPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('Admin deleted resources', () => {
  it('matches the Figma archive structure and recovery policy', () => {
    renderDeleted();

    expect(screen.getByText('删除资源管理')).toBeInTheDocument();
    expect(screen.getByText('审计存档区')).toBeInTheDocument();
    expect(screen.getByText('存档数据保护规范与合规通告')).toBeInTheDocument();
    expect(screen.getByText('显示第 1 到 4 条，共 19 条结果')).toBeInTheDocument();
    expect(screen.getByText('doc_88291')).toBeInTheDocument();
    expect(screen.getByText('sess_19283')).toBeInTheDocument();
    expect(screen.getByText('plan_33910')).toBeInTheDocument();
    expect(screen.getByText('rec_77218')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '复制 doc_88291' })).toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: '查看详情' })).toHaveLength(4);
    expect(screen.getByRole('link', { name: '删除资源' })).toHaveAttribute('aria-current', 'page');
  });

  it('filters by resource type and archive owner query', async () => {
    const user = userEvent.setup();
    renderDeleted();

    await user.click(screen.getByRole('combobox', { name: '资源类型筛选' }));
    await user.click(screen.getByRole('option', { name: '计划' }));
    expect(screen.getByText('plan_33910')).toBeInTheDocument();
    expect(screen.queryByText('doc_88291')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: '恢复' })).toBeDisabled();

    const search = screen.getByRole('textbox', { name: '搜索存档ID或归档所有者' });
    await user.clear(search);
    await user.type(search, 'system_admin');
    expect(screen.getByText('显示第 0 到 0 条，共 0 条结果')).toBeInTheDocument();
  });

  it('opens a read-only archive detail dialog', async () => {
    const user = userEvent.setup();
    renderDeleted();

    await user.click(screen.getAllByRole('button', { name: '查看详情' })[0]);
    const dialog = screen.getByRole('dialog', { name: 'doc_88291 详情' });
    expect(dialog).toBeInTheDocument();
    expect(within(dialog).getByText('2024春季营养指南与低卡烘焙指引.pdf')).toBeInTheDocument();
    expect(within(dialog).getByText('anddy_operator_9')).toBeInTheDocument();
  });
});
