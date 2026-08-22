import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { WorkspaceLayout } from './WorkspaceLayout';

describe('WorkspaceLayout shell controls', () => {
  it('renders shell actions through the shared shadcn Button primitive', () => {
    render(
      <MemoryRouter initialEntries={['/']}>
        <WorkspaceLayout sidebarFixture={{ sessions: [], searchValue: '高蛋白' }}>
          <div>页面内容</div>
        </WorkspaceLayout>
      </MemoryRouter>,
    );

    expect(screen.getByRole('button', { name: '清除会话搜索' })).toHaveClass('inline-flex');
    expect(screen.getByRole('button', { name: '设置' })).toHaveClass('inline-flex');
    expect(screen.getByRole('button', { name: '收起导航' })).toHaveClass('inline-flex');
    expect(screen.getByRole('button', { name: '通知' })).toHaveClass('inline-flex');
    expect(screen.getByRole('button', { name: '梁同学' })).toHaveClass('inline-flex');
  });
});
