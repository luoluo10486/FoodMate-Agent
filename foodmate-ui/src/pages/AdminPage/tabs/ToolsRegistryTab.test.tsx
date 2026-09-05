import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { AdminPage } from '../AdminPage';

function renderRegistry() {
  return render(
    <MemoryRouter initialEntries={['/admin/tools?tab=registry']}>
      <Routes>
        <Route path="/admin/*" element={<AdminPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

function renderOperationFixture(state: string) {
  return render(
    <MemoryRouter initialEntries={[`/admin?state=${state}`]}>
      <Routes>
        <Route path="/admin/*" element={<AdminPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('Admin tool registry', () => {
  it('matches the Figma registry structure and query route', () => {
    renderRegistry();

    expect(within(screen.getByRole('main')).getByText('工具注册表')).toBeInTheDocument();
    expect(screen.getByText('24 个')).toBeInTheDocument();
    expect(screen.getByText('3 个')).toBeInTheDocument();
    expect(screen.getByText('1,420,951 次')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '工具注册表' })).toHaveAttribute('aria-current', 'page');
    expect(screen.getAllByRole('button', { name: '配置详情' })).toHaveLength(6);
    expect(screen.getByRole('button', { name: '复制 nutrition_lookup' })).toBeInTheDocument();
    expect(screen.getByText('nutrition_lookup')).toBeInTheDocument();
    expect(screen.getByText('user_memory_write')).toBeInTheDocument();
  });

  it('filters tools by status and search query', async () => {
    const user = userEvent.setup();
    renderRegistry();

    await user.click(screen.getByRole('combobox', { name: '工具状态筛选' }));
    await user.click(screen.getByRole('option', { name: '已停用' }));
    expect(screen.getByText('sql_query')).toBeInTheDocument();
    expect(screen.queryByText('nutrition_lookup')).not.toBeInTheDocument();

    const search = screen.getByRole('textbox', { name: '搜索工具、指令或版本' });
    await user.type(search, 'food_image');
    expect(screen.queryByText('sql_query')).not.toBeInTheDocument();
    expect(screen.getByText('显示第 0 到 0 条，共 0 条结果')).toBeInTheDocument();
  });

  it('opens a read-only tool contract from the configuration action', async () => {
    const user = userEvent.setup();
    renderRegistry();

    await user.click(screen.getAllByRole('button', { name: '配置详情' })[0]);
    expect(screen.getByRole('dialog', { name: 'nutrition_lookup 配置详情' })).toBeInTheDocument();
    expect(
      within(screen.getByRole('dialog', { name: 'nutrition_lookup 配置详情' })).getByText('3x exponential'),
    ).toBeInTheDocument();
    expect(screen.getByText('foodId, serving')).toBeInTheDocument();
  });

  it('runs the registry operation through confirm, submitting and success states', async () => {
    const user = userEvent.setup();
    renderRegistry();

    await user.click(screen.getAllByRole('button', { name: '配置详情' })[0]);
    const details = screen.getByRole('dialog', { name: 'nutrition_lookup 配置详情' });
    await user.click(within(details).getByRole('button', { name: '停用工具' }));

    expect(screen.getByRole('dialog', { name: '确认停用工具' })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '确认停用' }));
    expect(screen.getByRole('dialog', { name: '确认停用工具' })).toBeInTheDocument();

    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('操作成功'), { timeout: 1200 });
    expect(screen.getByRole('alert')).toHaveTextContent('nutrition_lookup');
  });

  it('matches Figma disabled controls for the operator permission fixture', () => {
    renderOperationFixture('op-no-permission');

    expect(screen.getByText('管理员：Operator（无写权限）')).toBeInTheDocument();
    const actionButtons = screen.getAllByRole('button', { name: '停用工具' });
    expect(actionButtons).toHaveLength(4);
    expect(actionButtons.every((button) => (button as HTMLButtonElement).disabled)).toBe(true);
    expect(actionButtons.every((button) => button.className.includes('registryActionDisabled'))).toBe(true);
    expect(actionButtons.every((button) => button.querySelector('svg'))).toBe(true);
  });

  it('keeps the disabled tool row and attention actions in the success fixture', () => {
    renderOperationFixture('op-success');

    expect(screen.getByText('已停用')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '启用工具' })).toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: '停用工具' })).toHaveLength(3);
    expect(
      screen
        .getAllByRole('button', { name: '停用工具' })
        .every((button) => button.className.includes('registryActionAttention')),
    ).toBe(true);
  });
});
