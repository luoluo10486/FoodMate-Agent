import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { AdminPage } from '../AdminPage';

function renderUsers() {
  return render(
    <MemoryRouter initialEntries={['/admin/users']}>
      <Routes>
        <Route path="/admin/*" element={<AdminPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('Admin user details', () => {
  it('renders the user list and all five detail tabs', async () => {
    renderUsers();

    expect(await screen.findByRole('heading', { name: '用户管理' })).toBeInTheDocument();
    expect(await screen.findByRole('row', { name: /user_10002/ })).toBeInTheDocument();
    expect(screen.getAllByRole('tab')).toHaveLength(5);
    expect(screen.getByRole('tab', { name: '资料' })).toHaveAttribute('aria-selected', 'true');
    expect(screen.queryByText('蛋白质目标')).not.toBeInTheDocument();
  });

  it('switches between dietary, business session and operation history tabs', async () => {
    const user = userEvent.setup();
    renderUsers();

    await screen.findByRole('row', { name: /user_10002/ });
    await user.click(screen.getByRole('tab', { name: '饮食画像' }));
    expect(screen.getByText('蛋白质目标')).toBeVisible();
    expect(screen.getByText('105 g', { exact: true })).toBeVisible();

    await user.click(screen.getByRole('tab', { name: '业务会话' }));
    expect(screen.getByText('session_analysis_418')).toBeVisible();
    expect(screen.getByText('本周饮食分析')).toBeVisible();

    await user.click(screen.getByRole('tab', { name: '操作历史' }));
    const historyPanel = screen.getByRole('tabpanel');
    expect(within(historyPanel).getByText('LOGIN_FAILED')).toBeInTheDocument();
    expect(within(historyPanel).getByText('req_login_1003')).toBeInTheDocument();
  });

  it('keeps status actions on the shared operation state machine', async () => {
    const user = userEvent.setup();
    renderUsers();

    await screen.findByRole('row', { name: /user_10002/ });
    const userRow = screen.getByRole('row', { name: /user_10002/ });
    await user.click(within(userRow).getByRole('button', { name: '禁用' }));
    expect(screen.getByRole('dialog', { name: '确认禁用用户' })).toBeInTheDocument();
  });
});
