import {
  BarChart3,
  BookOpen,
  MessageSquare,
  PanelLeft,
  Plus,
  RotateCcw,
  Search,
  ShieldCheck,
  Trash2,
  Utensils,
  User,
  X,
} from 'lucide-react';
import { useEffect, useState } from 'react';
import { Link, NavLink, useLocation, useNavigate } from 'react-router-dom';
import { Badge } from '@/components/ui/badge';
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
import { SidebarSessionList, type SessionAction } from '../../components/workspace/SidebarSessionList';
import { BrandLogo } from '../../components/brand/BrandLogo';
import styles from './WorkspaceLayout.module.css';

type WorkspaceLayoutProps = {
  children: React.ReactNode;
  activeModule?: 'home' | 'chat' | 'analysis' | 'planning' | 'knowledge' | 'profile' | 'admin';
  moduleLabel?: React.ReactNode;
};

export function WorkspaceLayout({ children, activeModule = 'home', moduleLabel }: WorkspaceLayoutProps) {
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
    if (authReady && isAuthenticated)
      loadSessions()
        .then(setSessions)
        .catch(() => undefined);
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

  if (!authReady) return <div style={{ padding: 32 }}>正在校验登录状态...</div>;
  if (realMode && !isAuthenticated) return null;

  return (
    <TooltipProvider delayDuration={300}>
      <div className={styles.shell}>
        <aside className={styles.sidebar}>
          <div className={styles.brandRow}>
            <BrandLogo />
            <span className={styles.modePill}>Agent 模式</span>
          </div>
          <Button
            className={styles.newButton}
            onClick={() => {
              if (!realMode) {
                navigate(buildChatPath('week-plan'));
                return;
              }
              void createSession().then((session) => {
                void refreshSessions();
                navigate(buildChatPath(session.session_id));
              });
            }}
          >
            <Plus aria-hidden="true" />
            <span>新建 Agent 会话</span>
          </Button>
          <div className={styles.searchWrap}>
            <Search className={styles.searchIcon} aria-hidden="true" />
            <Input
              className={styles.search}
              placeholder="搜索会话"
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
            <SidebarSessionList sessions={sessions} onAction={handleSessionAction} />
            {realMode ? (
              <Button className={styles.deletedButton} variant="ghost" onClick={() => void openDeletedSessions()}>
                查看已删除会话
              </Button>
            ) : null}
          </div>
          <div className={styles.accountDock}>
            <Link className={styles.profile} to={isAuthenticated ? ROUTES.PROFILE : ROUTES.LOGIN}>
              <div className={styles.avatar}>{isAuthenticated ? authUser.displayName.slice(0, 1) : '访'}</div>
              <div>
                <strong>{isAuthenticated ? authUser.displayName : '未登录'}</strong>
                <span>{isAuthenticated ? `${authUser.role} · ${authUser.profile.preference}` : currentAuth.title}</span>
              </div>
            </Link>
            {isAuthenticated ? (
              <Link className={styles.logoutLink} to={ROUTES.LOGIN} onClick={() => void logout()}>
                <Button className={styles.logoutButton} variant="outline">
                  退出登录
                </Button>
              </Link>
            ) : null}
          </div>
        </aside>
        <main className={styles.main}>
          <header className={styles.topbar}>
            <Tooltip>
              <TooltipTrigger asChild>
                <Button
                  aria-label="折叠导航"
                  size="icon"
                  variant="ghost"
                  onClick={() => announce('导航折叠将在侧栏响应式迁移阶段启用。')}
                >
                  <PanelLeft aria-hidden="true" />
                </Button>
              </TooltipTrigger>
              <TooltipContent>折叠导航</TooltipContent>
            </Tooltip>
            {moduleLabel ? <div className={styles.moduleLabel}>{moduleLabel}</div> : null}
            <nav className={styles.nav} aria-label="主导航">
              <NavLink className={styles.navItem} to={buildChatPath('week-plan')}>
                <Badge variant={activeModule === 'chat' ? 'default' : 'secondary'}>
                  <MessageSquare aria-hidden="true" />
                  Agent 会话
                </Badge>
              </NavLink>
              <NavLink className={styles.navItem} to={ROUTES.PLANNING}>
                <Badge variant={activeModule === 'planning' ? 'default' : 'secondary'}>
                  <Utensils aria-hidden="true" />
                  饮食管理
                </Badge>
              </NavLink>
              <NavLink className={styles.navItem} to={ROUTES.KNOWLEDGE}>
                <Badge variant={activeModule === 'knowledge' ? 'default' : 'secondary'}>
                  <BookOpen aria-hidden="true" />
                  知识库
                </Badge>
              </NavLink>
              <NavLink className={styles.navItem} to={ROUTES.ANALYSIS}>
                <Badge variant={activeModule === 'analysis' ? 'default' : 'secondary'}>
                  <BarChart3 aria-hidden="true" />
                  数据分析
                </Badge>
              </NavLink>
              {canAccessAdmin ? (
                <NavLink className={styles.navItem} to={ROUTES.ADMIN}>
                  <Badge variant={activeModule === 'admin' ? 'default' : 'secondary'}>
                    <ShieldCheck aria-hidden="true" />
                    管理后台
                  </Badge>
                </NavLink>
              ) : null}
            </nav>
            <div className={styles.userActions}>
              {notice ? (
                <div className={styles.notice} role="status" aria-live="polite">
                  {notice}
                </div>
              ) : null}
              {isAuthenticated ? (
                <Link className={styles.topbarLogoutLink} to={ROUTES.LOGIN} onClick={() => void logout()}>
                  <Button className={styles.topbarLogoutButton} variant="outline">
                    退出登录
                  </Button>
                </Link>
              ) : null}
              <DropdownMenu>
                <DropdownMenuTrigger asChild>
                  <Button className={styles.userButton} variant="ghost">
                    <User aria-hidden="true" />
                    {isAuthenticated ? authUser.displayName : '登录'}
                  </Button>
                </DropdownMenuTrigger>
                <DropdownMenuContent align="end">
                  <DropdownMenuItem asChild>
                    <Link className={styles.menuLink} to={isAuthenticated ? ROUTES.PROFILE : ROUTES.LOGIN}>
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
          </header>
          {children}
        </main>
        <Dialog
          open={Boolean(renameTarget)}
          onOpenChange={(open) => {
            if (!open) setRenameTarget(undefined);
          }}
        >
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
        <Dialog
          open={Boolean(deleteTarget)}
          onOpenChange={(open) => {
            if (!open) setDeleteTarget(undefined);
          }}
        >
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
