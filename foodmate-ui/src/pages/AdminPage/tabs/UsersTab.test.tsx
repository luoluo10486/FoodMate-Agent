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

    expect(await screen.findByText('用户管理', { selector: 'strong' })).toBeInTheDocument();
    expect(await screen.findByRole('row', { name: /usr_098a1/ })).toBeInTheDocument();
    expect(screen.getAllByRole('tab')).toHaveLength(5);
    expect(screen.getByRole('tab', { name: '资料' })).toHaveAttribute('aria-selected', 'true');
    expect(screen.getByText('KetoMealFormer_v4')).toBeInTheDocument();
  });

  it('switches between dietary, business session and operation history tabs', async () => {
    const user = userEvent.setup();
    renderUsers();

    await screen.findByRole('row', { name: /usr_098a1/ });
    await user.click(screen.getByRole('tab', { name: '饮食' }));
    expect(screen.getByText('蛋白质目标')).toBeVisible();

    await user.click(screen.getByRole('tab', { name: '业务会话' }));
    expect(screen.getByText('session_keto_418')).toBeVisible();
    expect(screen.getByText('Keto meal planning')).toBeVisible();

    await user.click(screen.getByRole('tab', { name: '历史' }));
    const historyPanel = screen.getByRole('tabpanel');
    expect(within(historyPanel).getByText('LOGIN')).toBeInTheDocument();
    expect(within(historyPanel).getByText('req_login_098a1')).toBeInTheDocument();
  });

  it('keeps status actions on the shared operation state machine', async () => {
    const user = userEvent.setup();
    renderUsers();

    await screen.findByRole('row', { name: /usr_112b9/ });
    const userRow = screen.getByRole('row', { name: /usr_112b9/ });
    await user.click(within(userRow).getByRole('button', { name: 'usr_112b9 操作' }));
    await user.click(screen.getByRole('menuitem', { name: '锁定用户' }));
    expect(screen.getByRole('dialog', { name: '确认锁定用户' })).toBeInTheDocument();
  });
});
