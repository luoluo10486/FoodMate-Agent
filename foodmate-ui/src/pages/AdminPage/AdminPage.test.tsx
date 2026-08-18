import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { AdminPage } from './AdminPage';

function renderAdmin(initialEntry = '/admin') {
  return render(
    <MemoryRouter initialEntries={[initialEntry]}>
      <Routes>
        <Route path="/admin/*" element={<AdminPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('AdminPage overview', () => {
  it('matches the Figma overview structure and keeps admin navigation visible', () => {
    renderAdmin();

    expect(screen.getByText('管理概览')).toBeInTheDocument();
    expect(screen.getByText('生产环境')).toBeInTheDocument();
    expect(screen.getByText('AgentRun 总量')).toBeInTheDocument();
    expect(screen.getByText('12,480')).toBeInTheDocument();
    expect(screen.getByText('成功率')).toBeInTheDocument();
    expect(screen.getByText('91.4%')).toBeInTheDocument();
    expect(screen.getByText('$128.45')).toBeInTheDocument();
    expect(screen.getAllByText('查看详情')).toHaveLength(6);

    for (const label of [
      '概览',
      '用户管理',
      'Agent 运行',
      '工具调用',
      'SQL 审计',
      'Trace',
      '模型用量',
      '知识库管理',
      '工具注册表',
      '软删除资源',
      '操作审计',
    ]) {
      expect(screen.getByRole('link', { name: label })).toBeInTheDocument();
    }
  });

  it('filters the overview table by result and search query', async () => {
    const user = userEvent.setup();
    renderAdmin();

    await user.click(screen.getByRole('combobox', { name: '结果筛选' }));
    await user.click(screen.getByRole('option', { name: 'completed' }));
    expect(screen.queryByText('run_889a4')).not.toBeInTheDocument();
    expect(screen.queryByText('run_133c9')).not.toBeInTheDocument();
    expect(screen.queryByText('run_98218a')).not.toBeInTheDocument();
    expect(screen.getByText('run_774x2')).toBeInTheDocument();

    const search = screen.getByRole('textbox', { name: '搜索运行或用户' });
    await user.clear(search);
    await user.type(search, 'sarah_chen');
    expect(screen.getByText('run_774x2')).toBeInTheDocument();
    expect(screen.queryByText('run_552b1')).not.toBeInTheDocument();
  });

  it('highlights only the exact query route in the admin navigation', () => {
    const { unmount } = renderAdmin();

    expect(screen.getByRole('link', { name: '概览' })).toHaveAttribute('aria-current', 'page');
    expect(screen.getByRole('link', { name: '操作审计' })).not.toHaveAttribute('aria-current');

    unmount();
    renderAdmin('/admin?view=audit');

    expect(screen.getByRole('link', { name: '概览' })).not.toHaveAttribute('aria-current');
    expect(screen.getByRole('link', { name: '操作审计' })).toHaveAttribute('aria-current', 'page');
  });

  it('maps admin visual fixture query states to their real sections', () => {
    const { unmount } = renderAdmin('/admin?state=tool-registry');
    expect(screen.getByText('已注册工具')).toBeInTheDocument();

    unmount();
    renderAdmin('/admin?state=deleted-resources');
    expect(screen.getByText('存档数据保护规范与合规通告')).toBeInTheDocument();

    unmount();
    renderAdmin('/admin?state=user-detail');
    expect(screen.getByText('用户详情')).toBeInTheDocument();
  });
});
