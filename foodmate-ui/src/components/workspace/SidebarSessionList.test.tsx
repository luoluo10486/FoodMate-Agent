import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import { SidebarSessionList } from './SidebarSessionList';

describe('SidebarSessionList controls', () => {
  it('uses shadcn buttons for session actions and pagination', async () => {
    const user = userEvent.setup();
    const onAction = vi.fn();
    const session = { id: 'session-1', title: '本周饮食分析', subtitle: '今天 12:45', active: true };

    render(
      <MemoryRouter initialEntries={['/chat']}>
        <SidebarSessionList sessions={[session]} onAction={onAction} currentPage={2} />
      </MemoryRouter>,
    );

    expect(screen.getByRole('button', { name: '上一页' })).toHaveClass('inline-flex');
    expect(screen.getByRole('button', { name: '上一页' })).toBeDisabled();
    expect(screen.getByRole('button', { name: '下一页' })).toHaveClass('inline-flex');

    const moreButton = screen.getByRole('button', { name: '管理本周饮食分析' });
    expect(moreButton).toHaveClass('inline-flex');
    await user.click(moreButton);
    await user.click(screen.getByRole('menuitem', { name: '重命名' }));

    expect(onAction).toHaveBeenCalledWith('rename', session);
  });
});
