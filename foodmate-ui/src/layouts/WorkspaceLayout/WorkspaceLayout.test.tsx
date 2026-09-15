import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import userEvent from '@testing-library/user-event';
import { render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { mockAuthUser } from '../../mock/auth';
import {
  archiveSession,
  createSession,
  loadDeletedSessions,
  loadSessionSummariesPage,
  restoreSession,
  searchSessions,
  type RealSession,
} from '../../services/sessionService';
import { loadCurrentUser } from '../../services/authService';
import { WorkspaceLayout } from './WorkspaceLayout';
import styles from './WorkspaceLayout.module.css';

vi.mock('../../services/authService', async () => {
  const actual = await vi.importActual<typeof import('../../services/authService')>('../../services/authService');
  return { ...actual, loadCurrentUser: vi.fn() };
});

vi.mock('../../services/sessionService', async () => {
  const actual = await vi.importActual<typeof import('../../services/sessionService')>('../../services/sessionService');
  return {
    ...actual,
    archiveSession: vi.fn(),
    createSession: vi.fn(),
    loadDeletedSessions: vi.fn(),
    loadSessionSummariesPage: vi.fn(),
    restoreSession: vi.fn(),
    searchSessions: vi.fn(),
  };
});

afterEach(() => {
  vi.clearAllMocks();
  vi.unstubAllEnvs();
  localStorage.clear();
});

describe('WorkspaceLayout shell controls', () => {
  it('aborts an in-flight session mutation when the workspace unmounts', async () => {
    vi.stubEnv('VITE_AGENT_MODE', 'real');
    localStorage.setItem('foodmate_auth_user', JSON.stringify(mockAuthUser));
    vi.mocked(loadCurrentUser).mockResolvedValue(mockAuthUser);
    vi.mocked(loadSessionSummariesPage).mockResolvedValue({
      items: [{ id: 'session-1', title: '早餐记录', subtitle: '今天', active: true }],
      total: 1,
      page: 1,
      size: 50,
    });

    let requestSignal: AbortSignal | undefined;
    let releaseArchive: () => void = () => undefined;
    vi.mocked(archiveSession).mockImplementation((_sessionId, signal) => {
      requestSignal = signal;
      return new Promise<void>((resolve) => {
        releaseArchive = resolve;
      });
    });

    const user = userEvent.setup();
    const view = render(
      <MemoryRouter initialEntries={['/']}>
        <WorkspaceLayout>
          <div>页面内容</div>
        </WorkspaceLayout>
      </MemoryRouter>,
    );

    await user.click(await screen.findByRole('button', { name: '管理早餐记录' }));
    await user.click(await screen.findByRole('menuitem', { name: '归档' }));
    await waitFor(() => expect(requestSignal).toBeDefined());

    view.unmount();

    expect(requestSignal?.aborted).toBe(true);
    releaseArchive();
  });

  it('prevents duplicate real session creation while the first request is pending', async () => {
    vi.stubEnv('VITE_AGENT_MODE', 'real');
    localStorage.setItem('foodmate_auth_user', JSON.stringify(mockAuthUser));
    vi.mocked(loadCurrentUser).mockResolvedValue(mockAuthUser);
    vi.mocked(loadSessionSummariesPage).mockResolvedValue({ items: [], total: 0, page: 1, size: 50 });

    let resolveCreate: (session: RealSession) => void = () => undefined;
    vi.mocked(createSession).mockReturnValue(
      new Promise<RealSession>((resolve) => {
        resolveCreate = resolve;
      }),
    );

    const user = userEvent.setup();
    render(
      <MemoryRouter initialEntries={['/']}>
        <WorkspaceLayout>
          <div>页面内容</div>
        </WorkspaceLayout>
      </MemoryRouter>,
    );

    const createButton = await screen.findByRole('button', { name: '新建任务' });
    await user.click(createButton);
    await user.click(createButton);

    expect(createSession).toHaveBeenCalledTimes(1);
    expect(createButton).toBeDisabled();

    resolveCreate({
      session_id: 'session-1',
      title: '新会话',
      mode: 'chat',
      status: 'active',
    });
    await waitFor(() => expect(createButton).not.toBeDisabled());
  });

  it('clears the session search and reloads the first page', async () => {
    vi.stubEnv('VITE_AGENT_MODE', 'real');
    localStorage.setItem('foodmate_auth_user', JSON.stringify(mockAuthUser));
    vi.mocked(loadCurrentUser).mockResolvedValue(mockAuthUser);
    vi.mocked(loadSessionSummariesPage).mockResolvedValue({ items: [], total: 0, page: 1, size: 50 });
    vi.mocked(searchSessions).mockImplementation(async (_query, params = {}) => ({
      items: [
        {
          id: 'session-1',
          title: '早餐记录',
          subtitle: '今天',
          active: false,
        },
      ],
      total: 51,
      page: params.page ?? 1,
      size: params.size ?? 50,
    }));

    const user = userEvent.setup();
    render(
      <MemoryRouter initialEntries={['/']}>
        <WorkspaceLayout>
          <div>页面内容</div>
        </WorkspaceLayout>
      </MemoryRouter>,
    );

    const search = await screen.findByPlaceholderText('搜索会话...');
    await user.type(search, '早餐');
    await waitFor(() =>
      expect(searchSessions).toHaveBeenCalledWith('早餐', { page: 1, size: 50 }, expect.any(AbortSignal)),
    );

    await user.click(screen.getByRole('button', { name: '下一页' }));
    await waitFor(() =>
      expect(searchSessions).toHaveBeenCalledWith('早餐', { page: 2, size: 50 }, expect.any(AbortSignal)),
    );

    await user.click(screen.getByRole('button', { name: '清除会话搜索' }));
    await waitFor(() =>
      expect(loadSessionSummariesPage).toHaveBeenLastCalledWith({ page: 1, size: 50 }, expect.any(AbortSignal)),
    );
  });

  it('reloads the deleted session list from the backend after restoring a session', async () => {
    vi.stubEnv('VITE_AGENT_MODE', 'real');
    localStorage.setItem('foodmate_auth_user', JSON.stringify(mockAuthUser));
    vi.mocked(loadCurrentUser).mockResolvedValue(mockAuthUser);
    vi.mocked(loadSessionSummariesPage).mockResolvedValue({ items: [], total: 0, page: 1, size: 50 });
    vi.mocked(loadDeletedSessions)
      .mockResolvedValueOnce([
        {
          session_id: 'deleted-1',
          title: '待恢复会话',
          mode: 'chat',
          status: 'deleted',
        },
      ])
      .mockResolvedValueOnce([]);
    vi.mocked(restoreSession).mockResolvedValue(undefined);

    const user = userEvent.setup();
    render(
      <MemoryRouter initialEntries={['/']}>
        <WorkspaceLayout>
          <div>页面内容</div>
        </WorkspaceLayout>
      </MemoryRouter>,
    );

    await user.click(await screen.findByRole('button', { name: '查看已删除会话' }));
    await user.click(await screen.findByRole('button', { name: '恢复' }));

    await waitFor(() => expect(loadDeletedSessions).toHaveBeenCalledTimes(2));
    await waitFor(() => expect(screen.getByText('暂无可恢复的会话。')).toBeInTheDocument());
    expect(restoreSession).toHaveBeenCalledWith('deleted-1', expect.any(AbortSignal));
    expect(loadSessionSummariesPage).toHaveBeenCalledTimes(2);
  });

  it('does not keep stale sessions after a real list request fails', async () => {
    vi.stubEnv('VITE_AGENT_MODE', 'real');
    localStorage.setItem('foodmate_auth_user', JSON.stringify(mockAuthUser));
    vi.mocked(loadCurrentUser).mockResolvedValue(mockAuthUser);
    vi.mocked(loadSessionSummariesPage).mockResolvedValue({
      items: [
        {
          id: 'session-1',
          title: '旧查询结果',
          subtitle: 'chat',
          active: false,
        },
      ],
      total: 1,
      page: 1,
      size: 50,
    });
    vi.mocked(searchSessions).mockRejectedValue(new Error('搜索服务暂不可用'));

    const user = userEvent.setup();
    render(
      <MemoryRouter initialEntries={['/']}>
        <WorkspaceLayout>
          <div>页面内容</div>
        </WorkspaceLayout>
      </MemoryRouter>,
    );

    expect(await screen.findByText('旧查询结果')).toBeInTheDocument();
    await user.type(await screen.findByPlaceholderText('搜索会话...'), '早餐');

    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('搜索服务暂不可用'));
    expect(screen.queryByText('旧查询结果')).not.toBeInTheDocument();
  });

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

  it('can collapse and expand the real workspace navigation', async () => {
    const user = userEvent.setup();
    const { container } = render(
      <MemoryRouter initialEntries={['/']}>
        <WorkspaceLayout>
          <div>页面内容</div>
        </WorkspaceLayout>
      </MemoryRouter>,
    );

    const collapseButton = screen.getByRole('button', { name: '收起导航' });
    await user.click(collapseButton);

    expect(screen.getByRole('button', { name: '展开导航' })).toHaveAttribute('aria-expanded', 'false');
    expect(container.firstElementChild).toHaveClass('sidebarCollapsed');

    await user.click(screen.getByRole('button', { name: '展开导航' }));
    expect(screen.getByRole('button', { name: '收起导航' })).toHaveAttribute('aria-expanded', 'true');
    expect(container.firstElementChild).not.toHaveClass('sidebarCollapsed');
  });

  it('keeps navigation entry points available in the collapsed icon rail', async () => {
    const user = userEvent.setup();
    const { container } = render(
      <MemoryRouter initialEntries={['/']}>
        <WorkspaceLayout>
          <div>页面内容</div>
        </WorkspaceLayout>
      </MemoryRouter>,
    );

    await user.click(screen.getByRole('button', { name: '收起导航' }));

    const sidebar = within(container.querySelector('aside') as HTMLElement);
    expect(sidebar.getByRole('link', { name: '工作台' })).toBeInTheDocument();
    expect(sidebar.getByRole('link', { name: '饮食记录' })).toBeInTheDocument();
    expect(sidebar.getByRole('link', { name: '摄入分析' })).toBeInTheDocument();
    expect(sidebar.getByRole('link', { name: '餐食规划' })).toBeInTheDocument();
    expect(sidebar.getByRole('link', { name: '知识库' })).toBeInTheDocument();
    expect(sidebar.getByRole('button', { name: '设置' })).toBeInTheDocument();
    expect(container.querySelector('.searchWrap')).toBeInTheDocument();
    expect(container.querySelector('.sidebar-session-section')).toBeInTheDocument();
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

  it('hides only the Figma fixture topbar mark letter', () => {
    const { container } = render(
      <MemoryRouter initialEntries={['/']}>
        <WorkspaceLayout showKnowledgeTopNav={false} sidebarFixture={{ sessions: [] }}>
          <div>页面内容</div>
        </WorkspaceLayout>
      </MemoryRouter>,
    );

    const topbarMark = container.querySelector('main header .brand > span');
    const sidebarMark = container.querySelector('aside .brand > span');
    expect(topbarMark).toBeInTheDocument();
    expect(topbarMark).not.toHaveTextContent('F');
    expect(sidebarMark).toHaveTextContent('F');
  });

  it('hides the design chat topbar mark letter without a sidebar fixture', () => {
    const { container } = render(
      <MemoryRouter initialEntries={['/chat?state=figma-v2']}>
        <WorkspaceLayout designChat showKnowledgeTopNav={false}>
          <div>页面内容</div>
        </WorkspaceLayout>
      </MemoryRouter>,
    );

    const topbarMark = container.querySelector('main header .brand > span');
    const sidebarMark = container.querySelector('aside .brand > span');
    expect(topbarMark).toBeInTheDocument();
    expect(topbarMark).not.toHaveTextContent('F');
    expect(sidebarMark).toHaveTextContent('F');
    expect(container.firstElementChild).toHaveClass('designChat');
    expect(container.firstElementChild).toHaveClass('figmaFixture');
    expect(container.querySelector('[data-name="window-controls"]')).toBeInTheDocument();
  });

  it('uses the Figma selection surface colors for the design chat fixture', () => {
    const { container } = render(
      <MemoryRouter initialEntries={['/chat']}>
        <WorkspaceLayout
          activeModule="chat"
          designChat
          showKnowledgeTopNav={false}
          sidebarFixture={{
            sessions: [{ id: 'session-1', title: '本周饮食分析', subtitle: '今天 12:45', active: true }],
          }}
        >
          <div>页面内容</div>
        </WorkspaceLayout>
      </MemoryRouter>,
    );

    const shell = container.querySelector(`.${styles.designChat}`);
    const activeSection = container.querySelector('.sidebar-session-section-title.active');
    const activeSession = container.querySelector('.sidebar-session-list-item.active');

    expect(shell).toBeInTheDocument();
    expect(activeSection).toBeInTheDocument();
    expect(activeSession).toBeInTheDocument();
    const stylesheet = readFileSync(
      resolve(process.cwd(), 'src/layouts/WorkspaceLayout/WorkspaceLayout.module.css'),
      'utf8',
    );
    expect(stylesheet).toContain('--fm-fixture-sidebar-active-surface: var(--fm-figma-workspace-sidebar-active);');
    expect(stylesheet).toContain('--fm-fixture-session-active-surface: var(--fm-figma-workspace-session-active);');
    expect(stylesheet).toContain('--fm-fixture-top-nav-active-surface: var(--fm-figma-workspace-top-nav-active);');
  });

  it('uses the Figma green token for design chat brand and agent marks', () => {
    const stylesheet = readFileSync(resolve(process.cwd(), 'src/styles/tokens.css'), 'utf8');

    expect(stylesheet).toContain('--fm-green: #a6d997;');
  });

  it('allows a page to hide only the topbar mark letter while keeping its top navigation', () => {
    const { container } = render(
      <MemoryRouter initialEntries={['/knowledge']}>
        <WorkspaceLayout topbarShowMarkLetter={false} sidebarFixture={{ sessions: [] }}>
          <div>页面内容</div>
        </WorkspaceLayout>
      </MemoryRouter>,
    );

    expect(container.querySelector('main header .brand > span')).not.toHaveTextContent('F');
    expect(container.querySelector('main header a[href="/knowledge"]')).toBeInTheDocument();
    expect(container.querySelector('aside .brand > span')).toHaveTextContent('F');
  });

  it('supports a topbar-only Figma fixture without rendering a sidebar', () => {
    const { container } = render(
      <MemoryRouter initialEntries={['/knowledge']}>
        <WorkspaceLayout activeModule="knowledge" fixtureVariant="knowledge" hideSidebar topbarShowMarkLetter={false}>
          <div>页面内容</div>
        </WorkspaceLayout>
      </MemoryRouter>,
    );

    expect(container.querySelector('aside')).not.toBeInTheDocument();
    expect(container.firstElementChild).toHaveClass('noSidebar');
    expect(container.querySelector('main header .brand > span')).not.toHaveTextContent('F');
    expect(
      container.querySelector('main header img[src="/assets/figma/workspace/knowledge/topbar-search.svg"]'),
    ).toBeInTheDocument();
  });

  it('renders desktop window controls in the Home Figma fixture shell', () => {
    const { container } = render(
      <MemoryRouter initialEntries={['/']}>
        <WorkspaceLayout showKnowledgeTopNav={false} sidebarFixture={{ sessions: [] }}>
          <div>页面内容</div>
        </WorkspaceLayout>
      </MemoryRouter>,
    );

    expect(container.querySelector('[data-name="window-controls"]')).toBeInTheDocument();
    expect(container.querySelector('[data-name="window-controls"] img')).toHaveAttribute(
      'src',
      '/assets/figma/workspace/window-controls.svg',
    );
  });

  it('normalizes legacy avatar overrides before they reach the shared shell', () => {
    const { container } = render(
      <MemoryRouter initialEntries={['/']}>
        <WorkspaceLayout
          sidebarAvatarSrc="/legacy-assets/workspace/sidebar-person-avatar.png"
          topAvatarSrc="/legacy-assets/workspace/topbar-person-avatar.png"
        >
          <div>页面内容</div>
        </WorkspaceLayout>
      </MemoryRouter>,
    );

    expect(container.querySelector('aside .avatar img')).toHaveAttribute('src', '/assets/avatars/default-male.svg');
    expect(container.querySelector('main header .topAvatar img')).toHaveAttribute(
      'src',
      '/assets/avatars/default-male.svg',
    );
    expect(container.querySelector('[data-shell-avatar-policy="default-only"]')).toBeInTheDocument();
  });

  it('forces mock shell avatars to the registered SVGs even when a stale upload is supplied', () => {
    const { container } = render(
      <MemoryRouter initialEntries={['/']}>
        <WorkspaceLayout avatarSrc="/uploads/legacy-person.png">
          <div>页面内容</div>
        </WorkspaceLayout>
      </MemoryRouter>,
    );

    expect(container.querySelector('aside .avatar img')).toHaveAttribute('src', '/assets/avatars/default-male.svg');
    expect(container.querySelector('main header .topAvatar img')).toHaveAttribute(
      'src',
      '/assets/avatars/default-male.svg',
    );
    expect(container.querySelectorAll('[data-avatar-policy="default-only"]')).toHaveLength(2);
    expect(container.querySelector('[data-avatar-role="workspace-sidebar"]')).toHaveAttribute(
      'src',
      '/assets/avatars/default-male.svg',
    );
    expect(container.querySelector('[data-avatar-role="workspace-topbar"]')).toHaveAttribute(
      'src',
      '/assets/avatars/default-male.svg',
    );
    expect(container.querySelector('[data-avatar-role="workspace-sidebar"]')).toHaveAttribute(
      'src',
      container.querySelector('[data-avatar-role="workspace-topbar"]')?.getAttribute('src') ?? '',
    );
  });

  it('keeps an explicit Figma Fixture shell on the default-only avatar policy', () => {
    const { container } = render(
      <MemoryRouter initialEntries={['/']}>
        <WorkspaceLayout avatarSrc="/api/users/me/avatar" fixtureVariant="home" sidebarFixture={{ sessions: [] }}>
          <div>页面内容</div>
        </WorkspaceLayout>
      </MemoryRouter>,
    );

    expect(container.querySelector('[data-shell-avatar-policy="default-only"]')).toBeInTheDocument();
    expect(container.querySelector('[data-avatar-role="workspace-sidebar"]')).toHaveAttribute(
      'src',
      '/assets/avatars/default-male.svg',
    );
    expect(container.querySelector('[data-avatar-role="workspace-topbar"]')).toHaveAttribute(
      'src',
      '/assets/avatars/default-male.svg',
    );
  });

  it.each([
    ['records', '/analysis?view=records&state=v2'],
    ['analysis', '/analysis?state=v2'],
    ['planning', '/planning?state=v2'],
  ] as const)('renders desktop window controls in the %s Figma fixture shell', (activeModule, entry) => {
    const { container } = render(
      <MemoryRouter initialEntries={[entry]}>
        <WorkspaceLayout activeModule={activeModule} showKnowledgeTopNav={false} sidebarFixture={{ sessions: [] }}>
          <div>页面内容</div>
        </WorkspaceLayout>
      </MemoryRouter>,
    );

    expect(container.querySelector('[data-name="window-controls"]')).toBeInTheDocument();
  });

  it('supports the Profile Figma sidebar composition with history and fixture controls', () => {
    const { container } = render(
      <MemoryRouter initialEntries={['/profile?state=basic']}>
        <WorkspaceLayout
          activeModule="profile"
          profileActiveTab="basic"
          showKnowledgeTopNav
          showWindowControls
          sidebarFixture={{
            sessions: [],
            showTopStatus: true,
          }}
        >
          <div>页面内容</div>
        </WorkspaceLayout>
      </MemoryRouter>,
    );

    expect(screen.getByText('在线代理')).toBeInTheDocument();
    expect(screen.getByPlaceholderText('搜索会话...')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '收起导航' })).toBeInTheDocument();
    expect(container.querySelector('[data-name="window-controls"]')).toBeInTheDocument();
  });
});
