import {
  Bell,
  BookOpen,
  CalendarDays,
  ChartColumn,
  Home,
  MoreHorizontal,
  Plus,
  RotateCcw,
  Search,
  Settings,
  Table2,
  Trash2,
  User,
  X,
} from 'lucide-react';
import { useEffect, useState } from 'react';
import { Link, NavLink, useLocation, useNavigate } from 'react-router-dom';
import { Button } from '@/components/ui/button';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { Input } from '@/components/ui/input';
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip';
import { DEFAULT_AVATARS, resolveAvatarUrl } from '../../lib/avatar';
import { SidebarSessionList, type SessionAction } from '../../components/workspace/SidebarSessionList';
import { BrandLogo } from '../../components/brand/BrandLogo';
import { ROUTES, buildChatPath } from '../../constants/routes';
import {
  archiveSession,
  createSession,
  deleteSession,
  loadDeletedSessions,
  loadSessions,
  renameSession,
  restoreSession,
  searchSessions,
  unarchiveSession,
  type RealSession,
} from '../../services/sessionService';
import { getAuthScenarios, getAuthStatus, getAuthUser, loadCurrentUser, logout } from '../../services/authService';
import styles from './WorkspaceLayout.module.css';

type WorkspaceLayoutProps = {
  children: React.ReactNode;
  activeModule?: 'home' | 'chat' | 'records' | 'analysis' | 'planning' | 'knowledge' | 'profile' | 'admin';
  moduleLabel?: React.ReactNode;
  rightRail?: React.ReactNode;
  rightRailWidth?: 320 | 340;
  avatarSrc?: string;
  sidebarAvatarSrc?: string;
  topAvatarSrc?: string;
  displayNameOverride?: string;
  profileIdOverride?: string;
  showKnowledgeTopNav?: boolean;
  designChat?: boolean;
  pageOverlay?: React.ReactNode;
};

export function WorkspaceLayout({
  children,
  activeModule = 'home',
  moduleLabel,
  rightRail,
  rightRailWidth,
  avatarSrc,
  sidebarAvatarSrc,
  topAvatarSrc,
  displayNameOverride,
  profileIdOverride,
  showKnowledgeTopNav = true,
  designChat = false,
  pageOverlay,
}: WorkspaceLayoutProps) {
  const realMode = import.meta.env.VITE_AGENT_MODE === 'real';
  const [authReady, setAuthReady] = useState(!realMode);
  const [currentUser, setCurrentUser] = useState(getAuthUser());
  const [sessions, setSessions] = useState<Awaited<ReturnType<typeof loadSessions>>>([]);
  const [sessionQuery, setSessionQuery] = useState('');
  const [renameTarget, setRenameTarget] = useState<{ id: string; title: string }>();
  const [deleteTarget, setDeleteTarget] = useState<{ id: string; title: string }>();
  const [deletedOpen, setDeletedOpen] = useState(false);
  const [deletedSessions, setDeletedSessions] = useState<RealSession[]>([]);
  const [notice, setNotice] = useState('');
  const navigate = useNavigate();
  const location = useLocation();
  const authStatus = getAuthStatus();
  const authUser = currentUser;
  const authScenarios = getAuthScenarios();
  const currentAuth = authScenarios.find((item) => item.status === authStatus) ?? authScenarios[0];
  const isAuthenticated = authStatus === 'authenticated';
  const canAccessAdmin = isAuthenticated && ['admin', 'operator', 'superadmin'].includes(authUser.role);
  const defaultAvatar = resolveAvatarUrl(avatarSrc ?? authUser.avatarUrl, authUser.gender) ?? DEFAULT_AVATARS.male;
  const sidebarAvatar = sidebarAvatarSrc ?? defaultAvatar;
  const topAvatar = topAvatarSrc ?? defaultAvatar;
  const displayName = displayNameOverride ?? (isAuthenticated ? authUser.displayName : '登录');
  const profileId = profileIdOverride ?? (isAuthenticated ? authUser.id : currentAuth.code);

  useEffect(() => {
    if (!realMode) return;
    let cancelled = false;
    loadCurrentUser()
      .then((user) => {
        if (!cancelled) setCurrentUser(user);
      })
      .catch(() => undefined)
      .finally(() => {
        if (!cancelled) setAuthReady(true);
      });
    return () => {
      cancelled = true;
    };
  }, [realMode]);

  useEffect(() => {
    if (authReady && realMode && !isAuthenticated) {
      navigate(`/login?redirect=${encodeURIComponent(location.pathname + location.search)}`, { replace: true });
    }
  }, [authReady, realMode, isAuthenticated, location.pathname, location.search, navigate]);

  useEffect(() => {
    if (authReady && isAuthenticated) {
      loadSessions()
        .then(setSessions)
        .catch(() => undefined);
    }
  }, [authReady, isAuthenticated]);

  useEffect(() => {
    if (!realMode || !sessionQuery.trim()) return;
    const timer = window.setTimeout(() => {
      searchSessions(sessionQuery.trim())
        .then(setSessions)
        .catch(() => undefined);
    }, 250);
    return () => window.clearTimeout(timer);
  }, [realMode, sessionQuery]);

  const refreshSessions = () =>
    loadSessions()
      .then(setSessions)
      .catch(() => undefined);
  const announce = (message: string) => setNotice(message);
  const handleSessionAction = async (action: SessionAction, session: { id: string; title: string }) => {
    if (action === 'rename') {
      setRenameTarget({ id: session.id, title: session.title });
      return;
    }
    if (action === 'delete') {
      setDeleteTarget({ id: session.id, title: session.title });
      return;
    }
    await (action === 'archive' ? archiveSession(session.id) : unarchiveSession(session.id));
    await refreshSessions();
    announce(action === 'archive' ? '会话已归档。' : '会话已取消归档。');
  };
  const openDeletedSessions = async () => {
    setDeletedSessions(await loadDeletedSessions());
    setDeletedOpen(true);
  };
  const saveRename = async () => {
    if (!renameTarget?.title.trim()) return;
    await renameSession(renameTarget.id, renameTarget.title.trim());
    setRenameTarget(undefined);
    await refreshSessions();
    announce('会话名称已更新。');
  };
  const confirmDelete = async () => {
    if (!deleteTarget) return;
    await deleteSession(deleteTarget.id);
    setDeleteTarget(undefined);
    await refreshSessions();
    if (location.pathname === `/chat/${deleteTarget.id}`) navigate('/chat', { replace: true });
    announce('会话已移入回收站，可在 30 天内恢复。');
  };
  const createNewSession = () => {
    if (!realMode) {
      navigate(buildChatPath('week-plan'));
      return;
    }
    void createSession().then((session) => {
      void refreshSessions();
      navigate(buildChatPath(session.session_id));
    });
  };
  const sideLink = ({ isActive }: { isActive: boolean }) => `${styles.sideLink} ${isActive ? styles.active : ''}`;
  const fixedSideLink = (active: boolean) => `${styles.sideLink} ${active ? styles.active : ''}`;
  const topLink = (active: boolean) => `${styles.topNavLink} ${active ? styles.topNavActive : ''}`;

  if (!authReady) return <div className={styles.loadingState}>正在校验登录状态...</div>;
  if (realMode && !isAuthenticated) return null;

  return (
    <TooltipProvider delayDuration={300}>
      <div
        className={`${styles.shell} ${rightRail ? styles.withRail : ''} ${rightRailWidth === 340 ? styles.withWideRail : ''} ${activeModule === 'knowledge' ? styles.knowledgeLayout : ''} ${designChat ? styles.designChat : ''}`}
      >
        <aside className={styles.sidebar}>
          <div className={styles.windowControls} aria-hidden="true">
            <span className={styles.windowRed} />
            <span className={styles.windowYellow} />
            <span className={styles.windowGreen} />
          </div>
          <div className={styles.sidebarBrand}>
            <BrandLogo showTagline />
          </div>
          <Button className={styles.newButton} onClick={createNewSession}>
            <Plus aria-hidden="true" />
            <span>新建任务</span>
          </Button>
          <div className={styles.searchWrap}>
            <Search className={styles.searchIcon} aria-hidden="true" />
            <Input
              className={styles.search}
              placeholder="搜索会话..."
              value={sessionQuery}
              onChange={(event) => setSessionQuery(event.target.value)}
            />
            {sessionQuery ? (
              <button
                className={styles.clearSearch}
                type="button"
                aria-label="清除会话搜索"
                onClick={() => setSessionQuery('')}
              >
                <X aria-hidden="true" />
              </button>
            ) : null}
          </div>
          <div className={styles.sessionTools}>
            <nav className={styles.primarySideNav} aria-label="工作区导航">
              <NavLink className={sideLink} to={ROUTES.HOME} end>
                <Home aria-hidden="true" />
                <span>工作台</span>
              </NavLink>
            </nav>
            <SidebarSessionList sessions={sessions} onAction={handleSessionAction} />
            {realMode ? (
              <Button className={styles.deletedButton} variant="ghost" onClick={() => void openDeletedSessions()}>
                查看已删除会话
              </Button>
            ) : null}
          </div>
          <nav className={styles.secondarySideNav} aria-label="饮食工具">
            <NavLink className={fixedSideLink(activeModule === 'records')} to={`${ROUTES.ANALYSIS}?view=records`}>
              <Table2 aria-hidden="true" />
              <span>饮食记录</span>
            </NavLink>
            <NavLink className={fixedSideLink(activeModule === 'analysis')} to={ROUTES.ANALYSIS} end>
              <ChartColumn aria-hidden="true" />
              <span>摄入分析</span>
            </NavLink>
            <NavLink className={sideLink} to={ROUTES.PLANNING}>
              <CalendarDays aria-hidden="true" />
              <span>餐食规划</span>
            </NavLink>
            <NavLink className={sideLink} to={ROUTES.KNOWLEDGE}>
              <BookOpen aria-hidden="true" />
              <span>知识库</span>
            </NavLink>
            <button
              className={styles.sideButton}
              type="button"
              onClick={() => announce('设置入口将在设置页面完成后启用。')}
            >
              <Settings aria-hidden="true" />
              <span>设置</span>
            </button>
          </nav>
          <div className={styles.accountDock}>
            <button
              className={styles.collapseButton}
              type="button"
              onClick={() => announce('导航折叠将在响应式侧栏阶段启用。')}
            >
              <MoreHorizontal aria-hidden="true" />
              <span>收起导航</span>
            </button>
            <div className={styles.statusPill}>
              <span />
              <span>就绪 (Fustat-v2)</span>
            </div>
            <Link className={styles.profile} to={isAuthenticated ? ROUTES.PROFILE : ROUTES.LOGIN}>
              <div className={styles.avatar}>
                <img src={sidebarAvatar} alt="" />
              </div>
              <div>
                <strong>
                  {displayNameOverride
                    ? `${displayNameOverride} 的工作区`
                    : isAuthenticated
                      ? `${authUser.displayName} 的工作区`
                      : '未登录'}
                </strong>
                <span>ID: {profileId}</span>
              </div>
            </Link>
          </div>
        </aside>
        <main className={styles.main}>
          <header className={styles.topbar}>
            <BrandLogo size="compact" />
            <nav className={styles.nav} aria-label={activeModule === 'profile' ? '个人中心导航' : '主导航'}>
              {activeModule === 'profile' ? (
                <>
                  <NavLink className={({ isActive }) => topLink(isActive)} to={ROUTES.PROFILE} end>
                    基本资料
                  </NavLink>
                  <NavLink className={({ isActive }) => topLink(isActive)} to={ROUTES.PROFILE_MEMORIES}>
                    记忆与偏好
                  </NavLink>
                  <NavLink className={({ isActive }) => topLink(isActive)} to={ROUTES.PROFILE_SECURITY}>
                    安全与设备
                  </NavLink>
                  <NavLink className={({ isActive }) => topLink(isActive)} to={ROUTES.PROFILE_DATA}>
                    数据与隐私
                  </NavLink>
                </>
              ) : (
                <>
                  <NavLink className={topLink(activeModule === 'home' || designChat)} to={ROUTES.HOME} end>
                    工作台
                  </NavLink>
                  <NavLink className={topLink(activeModule === 'records')} to={`${ROUTES.ANALYSIS}?view=records`}>
                    饮食记录
                  </NavLink>
                  <NavLink className={topLink(activeModule === 'analysis')} to={ROUTES.ANALYSIS} end>
                    摄入分析
                  </NavLink>
                  <NavLink className={topLink(activeModule === 'planning')} to={ROUTES.PLANNING}>
                    餐食规划
                  </NavLink>
                  {showKnowledgeTopNav ? (
                    <NavLink className={topLink(activeModule === 'knowledge')} to={ROUTES.KNOWLEDGE}>
                      知识库
                    </NavLink>
                  ) : null}
                  {moduleLabel ? <span className={styles.moduleLabel}>{moduleLabel}</span> : null}
                </>
              )}
            </nav>
            <div className={styles.userActions}>
              <div className={styles.workspaceSearch}>
                <Search aria-hidden="true" />
                <Input placeholder="搜索工作区..." aria-label="搜索工作区" />
              </div>
              <Tooltip>
                <TooltipTrigger asChild>
                  <button
                    className={styles.iconButton}
                    type="button"
                    aria-label="通知"
                    onClick={() => announce('暂无新的工作区通知。')}
                  >
                    <Bell aria-hidden="true" />
                  </button>
                </TooltipTrigger>
                <TooltipContent>通知</TooltipContent>
              </Tooltip>
              <DropdownMenu>
                <DropdownMenuTrigger asChild>
                  <button className={styles.userButton} type="button">
                    <span className={styles.topAvatar}>
                      <img src={topAvatar} alt="" />
                    </span>
                    <span>{displayName}</span>
                  </button>
                </DropdownMenuTrigger>
                <DropdownMenuContent align="end">
                  <DropdownMenuItem asChild>
                    <Link className={styles.menuLink} to={isAuthenticated ? ROUTES.PROFILE : ROUTES.LOGIN}>
                      <User aria-hidden="true" />
                      个人资料
                    </Link>
                  </DropdownMenuItem>
                  {canAccessAdmin ? (
                    <DropdownMenuItem asChild>
                      <Link className={styles.menuLink} to={ROUTES.ADMIN}>
                        管理后台
                      </Link>
                    </DropdownMenuItem>
                  ) : null}
                  <DropdownMenuItem onSelect={() => announce('真实模式下会话失效由服务端 401 处理。')}>
                    检查登录状态
                  </DropdownMenuItem>
                  <DropdownMenuSeparator />
                  <DropdownMenuItem asChild>
                    <Link className={styles.menuLink} to={ROUTES.LOGIN} onClick={() => void logout()}>
                      退出登录
                    </Link>
                  </DropdownMenuItem>
                </DropdownMenuContent>
              </DropdownMenu>
            </div>
            {notice ? (
              <div className={styles.notice} role="status" aria-live="polite">
                {notice}
              </div>
            ) : null}
          </header>
          {children}
        </main>
        {rightRail ? <div className={styles.rightRail}>{rightRail}</div> : null}
        {pageOverlay}
        <Dialog open={Boolean(renameTarget)} onOpenChange={(open) => !open && setRenameTarget(undefined)}>
          <DialogContent>
            <DialogHeader>
              <DialogTitle>重命名会话</DialogTitle>
              <DialogDescription>名称只影响当前会话列表显示。</DialogDescription>
            </DialogHeader>
            <Input
              autoFocus
              value={renameTarget?.title ?? ''}
              maxLength={255}
              onChange={(event) =>
                setRenameTarget((current) => (current ? { ...current, title: event.target.value } : current))
              }
            />
            <DialogFooter>
              <Button variant="outline" onClick={() => setRenameTarget(undefined)}>
                取消
              </Button>
              <Button onClick={() => void saveRename()}>保存</Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>
        <Dialog open={Boolean(deleteTarget)} onOpenChange={(open) => !open && setDeleteTarget(undefined)}>
          <DialogContent>
            <DialogHeader>
              <DialogTitle>删除会话</DialogTitle>
              <DialogDescription>“{deleteTarget?.title}”将进入回收站，并可在 30 天内恢复。</DialogDescription>
            </DialogHeader>
            <DialogFooter>
              <Button variant="outline" onClick={() => setDeleteTarget(undefined)}>
                取消
              </Button>
              <Button variant="destructive" onClick={() => void confirmDelete()}>
                <Trash2 aria-hidden="true" />
                删除
              </Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>
        <Dialog open={deletedOpen} onOpenChange={setDeletedOpen}>
          <DialogContent>
            <DialogHeader>
              <DialogTitle>已删除会话</DialogTitle>
              <DialogDescription>恢复后会话会回到最近 Agent 会话列表。</DialogDescription>
            </DialogHeader>
            {deletedSessions.length === 0 ? (
              <p>暂无可恢复的会话。</p>
            ) : (
              deletedSessions.map((session) => (
                <div className={styles.deletedRow} key={session.session_id}>
                  <span>{session.title}</span>
                  <Button
                    variant="ghost"
                    onClick={async () => {
                      await restoreSession(String(session.session_id));
                      setDeletedSessions((items) => items.filter((item) => item.session_id !== session.session_id));
                      await refreshSessions();
                      announce('会话已恢复。');
                    }}
                  >
                    <RotateCcw aria-hidden="true" />
                    恢复
                  </Button>
                </div>
              ))
            )}
          </DialogContent>
        </Dialog>
      </div>
    </TooltipProvider>
  );
}
