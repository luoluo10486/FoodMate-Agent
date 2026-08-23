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

  it('renders the Figma fixture pagination as a compact control', () => {
    const { container } = render(
      <MemoryRouter initialEntries={['/']}>
        <WorkspaceLayout sidebarFixture={{ sessions: [], currentPage: 1 }}>
          <div>页面内容</div>
        </WorkspaceLayout>
      </MemoryRouter>,
    );

    expect(container.querySelector('.sidebar-session-pagination')).toBeInTheDocument();
    expect(container.querySelectorAll('.sidebar-session-pagination svg')).toHaveLength(2);
    expect(screen.getByText('1 / 3')).toBeInTheDocument();
  });

  it('omits the search clear control from the Figma fixture shell', () => {
    render(
      <MemoryRouter initialEntries={['/']}>
        <WorkspaceLayout designChat sidebarFixture={{ sessions: [], searchValue: '高蛋白' }}>
          <div>页面内容</div>
        </WorkspaceLayout>
      </MemoryRouter>,
    );

    expect(screen.getByPlaceholderText('搜索会话...')).toHaveValue('高蛋白');
    expect(screen.queryByRole('button', { name: '清除会话搜索' })).not.toBeInTheDocument();
  });
});
