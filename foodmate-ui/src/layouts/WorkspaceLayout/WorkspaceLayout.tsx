import {
  Bell,
  BookOpen,
  CalendarDays,
  ChartColumn,
  Home,
  Plus,
  PanelLeftClose,
  PanelLeftOpen,
  RotateCcw,
  Search,
  Settings,
  Table2,
  Trash2,
  User,
  X,
} from 'lucide-react';
import { useCallback, useEffect, useRef, useState } from 'react';
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
import { resolveAvatarUrl } from '../../lib/avatar';
import { AvatarImage } from '../../components/common/AvatarImage';
import { SidebarSessionList, type SessionAction } from '../../components/workspace/SidebarSessionList';
import {
  FigmaWorkspaceAsset,
  type FigmaWorkspaceAssetName,
  type WorkspaceFixtureVariant,
} from '../../components/workspace/FigmaWorkspaceAsset';
import type { SessionSummary } from '../../types/session';
import { BrandLogo } from '../../components/brand/BrandLogo';
import { ROUTES, buildChatPath } from '../../constants/routes';
import {
  archiveSession,
  createSession,
  deleteSession,
  loadDeletedSessions,
  loadSessionSummariesPage,
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
  profileActiveTab?: 'basic' | 'memories' | 'security' | 'privacy';
  showKnowledgeTopNav?: boolean;
  /** 顶栏型 Figma 画板不渲染工作区侧栏，也不需要加载会话列表。 */
  hideSidebar?: boolean;
  topbarShowMarkLetter?: boolean;
  showWindowControls?: boolean;
  designChat?: boolean;
  fixtureVariant?: WorkspaceFixtureVariant;
  topbarVariant?: 'planning-list';
  hideSessionHistory?: boolean;
  sidebarFixture?: {
    sessions: SessionSummary[];
    searchValue?: string;
    currentPage?: number;
    sessionCountLabel?: string;
    showTopStatus?: boolean;
    hideSessionSearch?: boolean;
    hideSessionPagination?: boolean;
    hideSecondaryNavigation?: boolean;
    hideCollapseButton?: boolean;
  };
  pageOverlay?: React.ReactNode;
};

type SidebarTooltipProps = {
  collapsed: boolean;
  label: string;
  children: React.ReactElement;
};

function SidebarTooltip({ collapsed, label, children }: SidebarTooltipProps) {
  if (!collapsed) return children;
  return (
    <Tooltip>
      <TooltipTrigger asChild>{children}</TooltipTrigger>
      <TooltipContent side="right">{label}</TooltipContent>
    </Tooltip>
  );
}

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
  profileActiveTab,
  showKnowledgeTopNav = true,
  hideSidebar = false,
  topbarShowMarkLetter = true,
  showWindowControls,
  designChat = false,
  fixtureVariant,
  topbarVariant,
  hideSessionHistory = false,
  sidebarFixture,
  pageOverlay,
}: WorkspaceLayoutProps) {
  const realMode = import.meta.env.VITE_AGENT_MODE === 'real';
  const [authReady, setAuthReady] = useState(!realMode);
  const [currentUser, setCurrentUser] = useState(getAuthUser());
  const [sessions, setSessions] = useState<SessionSummary[]>([]);
  const [sessionQuery, setSessionQuery] = useState('');
  const [sessionPage, setSessionPage] = useState(1);
  const [sessionTotal, setSessionTotal] = useState(0);
  const [sessionLoading, setSessionLoading] = useState(false);
  const [sessionError, setSessionError] = useState('');
  const sessionRequestRef = useRef(0);
  const pendingSessionOperationRef = useRef<string>();
  const [pendingSessionOperation, setPendingSessionOperation] = useState<string>();
  const [renameTarget, setRenameTarget] = useState<{ id: string; title: string }>();
  const [deleteTarget, setDeleteTarget] = useState<{ id: string; title: string }>();
  const [deletedOpen, setDeletedOpen] = useState(false);
  const [deletedSessions, setDeletedSessions] = useState<RealSession[]>([]);
  const [notice, setNotice] = useState('');
  // 侧栏折叠只影响当前工作区壳层，不改变路由、会话数据或页面业务状态。
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const navigate = useNavigate();
  const location = useLocation();
  const authStatus = getAuthStatus();
  const authUser = currentUser;
  const authScenarios = getAuthScenarios();
  const currentAuth = authScenarios.find((item) => item.status === authStatus) ?? authScenarios[0];
  const isAuthenticated = authStatus === 'authenticated';
  const canAccessAdmin = isAuthenticated && ['admin', 'operator', 'superadmin'].includes(authUser.role);
  const isFixtureLayout = Boolean(fixtureVariant || designChat || sidebarFixture);
  // Figma 工作台的示例账号固定为男性；不能让当前登录缓存的性别改变 Fixture 视觉和资源来源。
  const layoutAvatarGender = isFixtureLayout ? '男' : authUser.gender;
  const defaultAvatar = resolveAvatarUrl(avatarSrc ?? authUser.avatarUrl, layoutAvatarGender);
  // 所有布局覆盖头像都必须经过统一解析，阻断历史 Figma 人物素材绕过默认资源策略。
  // 只有传入 Fixture 覆盖头像时才使用覆盖值，真实模式默认沿用用户上传头像。
  const sidebarAvatar = sidebarAvatarSrc ? resolveAvatarUrl(sidebarAvatarSrc, layoutAvatarGender) : defaultAvatar;
  const topAvatar = topAvatarSrc ? resolveAvatarUrl(topAvatarSrc, layoutAvatarGender) : defaultAvatar;
  // Mock/Fixture 页面没有真实用户上传语义，必须只展示登记的男女默认 SVG。
  const defaultOnlyAvatar = !realMode || isFixtureLayout;
  const displayName = displayNameOverride ?? (isAuthenticated ? authUser.displayName : '登录');
  const profileId = profileIdOverride ?? (isAuthenticated ? authUser.id : currentAuth.code);
  const displayedSessions = sidebarFixture?.sessions ?? sessions;
  const displayedSessionQuery = sidebarFixture?.searchValue ?? sessionQuery;
  const activeSessionId = location.pathname.startsWith('/chat/')
    ? decodeURIComponent(location.pathname.slice('/chat/'.length).split('/')[0])
    : undefined;
  // 窗口控制点只由 Figma fixture 显式开启，避免装饰元素进入真实业务壳层。
  const showFixtureWindowControls =
    showWindowControls ?? (designChat || Boolean(sidebarFixture && !showKnowledgeTopNav));
  const isFigmaSidebarFixture = Boolean(sidebarFixture && (!showKnowledgeTopNav || showWindowControls));
  // Chat 的默认 Figma 画板不依赖固定会话 Fixture，但仍必须启用同一套视觉边界。
  // 真实模式不会传入 designChat，因此不会改变真实工作区的头像和导航样式。
  const isFigmaFixture = isFigmaSidebarFixture || designChat || (hideSidebar && Boolean(fixtureVariant));
  const renderWorkspaceIcon = (name: FigmaWorkspaceAssetName, fallback: React.ReactNode) =>
    fixtureVariant ? <FigmaWorkspaceAsset variant={fixtureVariant} name={name} /> : fallback;

  useEffect(() => {
    const syncCurrentUser = () => setCurrentUser(getAuthUser());
    window.addEventListener('foodmate:auth-changed', syncCurrentUser);
    return () => window.removeEventListener('foodmate:auth-changed', syncCurrentUser);
  }, []);

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

  const loadSessionList = useCallback(
    async (query: string, page: number) => {
      const requestId = ++sessionRequestRef.current;
      setSessionLoading(true);
      setSessionError('');
      try {
        if (query.trim()) {
          const result = await searchSessions(query.trim(), { page, size: 50 });
          if (requestId !== sessionRequestRef.current) return;
          setSessions(result.items.map((item) => ({ ...item, active: item.id === activeSessionId })));
          setSessionTotal(result.total);
          setSessionPage(result.page);
          return;
        }
        const result = await loadSessionSummariesPage({ page, size: 50 });
        if (requestId !== sessionRequestRef.current) return;
        setSessions(result.items.map((item) => ({ ...item, active: item.id === activeSessionId })));
        setSessionTotal(result.total);
        setSessionPage(result.page);
      } catch (error) {
        if (requestId !== sessionRequestRef.current) return;
        // 请求失败时清空旧列表，避免用户把上一次查询结果误认为当前结果。
        setSessions([]);
        setSessionTotal(0);
        setSessionError(error instanceof Error ? error.message : '会话列表加载失败，请重试。');
      } finally {
        if (requestId === sessionRequestRef.current) setSessionLoading(false);
      }
    },
    [activeSessionId],
  );

  useEffect(() => {
    if (sidebarFixture || hideSidebar || !realMode || !authReady || !isAuthenticated) return;
    const timer = window.setTimeout(
      () => void loadSessionList(sessionQuery, sessionPage),
      sessionQuery.trim() ? 250 : 0,
    );
    return () => window.clearTimeout(timer);
  }, [authReady, hideSidebar, isAuthenticated, loadSessionList, realMode, sessionPage, sessionQuery, sidebarFixture]);

  const refreshSessions = () => loadSessionList(sessionQuery, sessionPage);
  const announce = (message: string) => setNotice(message);
  // 使用 ref 抢占操作锁，避免连续点击在同一轮渲染内发出重复写请求。
  const beginSessionOperation = (key: string) => {
    if (pendingSessionOperationRef.current) return false;
    pendingSessionOperationRef.current = key;
    setPendingSessionOperation(key);
    return true;
  };
  const endSessionOperation = (key: string) => {
    if (pendingSessionOperationRef.current !== key) return;
    pendingSessionOperationRef.current = undefined;
    setPendingSessionOperation(undefined);
  };
  const handleSessionAction = async (action: SessionAction, session: { id: string; title: string }) => {
    if (action === 'rename') {
      setRenameTarget({ id: session.id, title: session.title });
      return;
    }
    if (action === 'delete') {
      setDeleteTarget({ id: session.id, title: session.title });
      return;
    }
    const operationKey = `${action}:${session.id}`;
    if (!beginSessionOperation(operationKey)) return;
    try {
      await (action === 'archive' ? archiveSession(session.id) : unarchiveSession(session.id));
      await refreshSessions();
      announce(action === 'archive' ? '会话已归档。' : '会话已取消归档。');
    } catch (error) {
      const message = error instanceof Error ? error.message : '会话状态更新失败，请重试。';
      setSessionError(message);
      announce(message);
    } finally {
      endSessionOperation(operationKey);
    }
  };
  const openDeletedSessions = async () => {
    const operationKey = 'deleted:list';
    if (!beginSessionOperation(operationKey)) return;
    try {
      setDeletedSessions(await loadDeletedSessions());
      setDeletedOpen(true);
    } catch (error) {
      const message = error instanceof Error ? error.message : '回收站加载失败，请重试。';
      setSessionError(message);
      announce(message);
    } finally {
      endSessionOperation(operationKey);
    }
  };
  const saveRename = async () => {
    if (!renameTarget?.title.trim()) return;
    const operationKey = `rename:${renameTarget.id}`;
    if (!beginSessionOperation(operationKey)) return;
    try {
      await renameSession(renameTarget.id, renameTarget.title.trim());
      setRenameTarget(undefined);
      await refreshSessions();
      announce('会话名称已更新。');
    } catch (error) {
      const message = error instanceof Error ? error.message : '会话重命名失败，请重试。';
      setSessionError(message);
      announce(message);
    } finally {
      endSessionOperation(operationKey);
    }
  };
  const confirmDelete = async () => {
    if (!deleteTarget) return;
    const operationKey = `delete:${deleteTarget.id}`;
    if (!beginSessionOperation(operationKey)) return;
    try {
      await deleteSession(deleteTarget.id);
      setDeleteTarget(undefined);
      await refreshSessions();
      if (location.pathname === `/chat/${deleteTarget.id}`) navigate('/chat', { replace: true });
      announce('会话已移入回收站，可在 30 天内恢复。');
    } catch (error) {
      const message = error instanceof Error ? error.message : '会话删除失败，请重试。';
      setSessionError(message);
      announce(message);
    } finally {
      endSessionOperation(operationKey);
    }
  };
  const createNewSession = async () => {
    if (!realMode) {
      navigate(buildChatPath('week-plan'));
      return;
    }
    const operationKey = 'session:create';
    if (!beginSessionOperation(operationKey)) return;
    try {
      const session = await createSession();
      await refreshSessions();
      navigate(buildChatPath(session.session_id));
    } catch (error) {
      const message = error instanceof Error ? error.message : '新建会话失败，请重试。';
      setSessionError(message);
      announce(message);
    } finally {
      endSessionOperation(operationKey);
    }
  };
  const restoreDeletedSession = async (sessionId: string) => {
    const operationKey = `restore:${sessionId}`;
    if (!beginSessionOperation(operationKey)) return;
    try {
      await restoreSession(sessionId);
      setDeletedSessions((items) => items.filter((item) => item.session_id !== sessionId));
      await refreshSessions();
      announce('会话已恢复。');
    } catch (error) {
      const message = error instanceof Error ? error.message : '会话恢复失败，请重试。';
      setSessionError(message);
      announce(message);
    } finally {
      endSessionOperation(operationKey);
    }
  };
  const sideLink = ({ isActive }: { isActive: boolean }) => `${styles.sideLink} ${isActive ? styles.active : ''}`;
  const fixedSideLink = (active: boolean) => `${styles.sideLink} ${active ? styles.active : ''}`;
  const topLink = (active: boolean) => `${styles.topNavLink} ${active ? styles.topNavActive : ''}`;

  if (!authReady) return <div className={styles.loadingState}>正在校验登录状态...</div>;
  if (realMode && !isAuthenticated) return null;

  return (
    <TooltipProvider delayDuration={300}>
      <div
        className={`${styles.shell} ${rightRail ? styles.withRail : ''} ${rightRailWidth === 340 ? styles.withWideRail : ''} ${activeModule === 'knowledge' ? styles.knowledgeLayout : ''} ${designChat ? styles.designChat : ''} ${hideSidebar ? styles.noSidebar : ''} ${isFigmaFixture ? styles.figmaFixture : ''} ${sidebarCollapsed ? styles.sidebarCollapsed : ''}`}
        data-shell-avatar-policy={defaultOnlyAvatar ? 'default-only' : 'uploaded-allowed'}
        data-shell-avatar-assets="default-male.svg,default-female.svg"
      >
        {!hideSidebar ? (
          <aside className={`${styles.sidebar} ${sidebarFixture?.showTopStatus ? styles.profileFixture : ''}`}>
            {showFixtureWindowControls ? (
              <div className={styles.windowControls} data-name="window-controls" aria-hidden="true">
                {fixtureVariant ? (
                  <FigmaWorkspaceAsset variant={fixtureVariant} name="windowControls" />
                ) : (
                  <img src="/assets/figma/workspace/window-controls.svg" alt="" />
                )}
              </div>
            ) : null}
            <div className={styles.sidebarBrand}>
              <BrandLogo showTagline />
            </div>
            {sidebarFixture?.showTopStatus ? <div className={styles.fixtureOnlineStatus}>在线代理</div> : null}
            <SidebarTooltip collapsed={sidebarCollapsed} label="新建任务">
              <Button
                aria-label="新建任务"
                className={styles.newButton}
                onClick={() => void createNewSession()}
                disabled={Boolean(pendingSessionOperation)}
              >
                {renderWorkspaceIcon('newTask', <Plus aria-hidden="true" />)}
                <span>新建任务</span>
              </Button>
            </SidebarTooltip>
            {!hideSessionHistory && !sidebarFixture?.hideSessionSearch ? (
              <div className={styles.searchWrap}>
                {fixtureVariant ? (
                  <FigmaWorkspaceAsset variant={fixtureVariant} name="sessionSearch" className={styles.searchIcon} />
                ) : (
                  <Search className={styles.searchIcon} aria-hidden="true" />
                )}
                <Input
                  className={styles.search}
                  placeholder="搜索会话..."
                  value={displayedSessionQuery}
                  onChange={(event) => {
                    setSessionQuery(event.target.value);
                    setSessionPage(1);
                  }}
                />
                {displayedSessionQuery && !designChat ? (
                  <Button
                    className={styles.clearSearch}
                    variant="ghost"
                    size="icon"
                    type="button"
                    aria-label="清除会话搜索"
                    onClick={() => {
                      setSessionQuery('');
                      setSessionPage(1);
                    }}
                  >
                    <X aria-hidden="true" />
                  </Button>
                ) : null}
              </div>
            ) : null}
            <div className={styles.sessionTools}>
              <nav className={styles.primarySideNav} aria-label="工作区导航">
                <SidebarTooltip collapsed={sidebarCollapsed} label="工作台">
                  <NavLink aria-label="工作台" className={sideLink} to={ROUTES.HOME} end>
                    {renderWorkspaceIcon('home', <Home aria-hidden="true" />)}
                    <span>工作台</span>
                  </NavLink>
                </SidebarTooltip>
              </nav>
              <SidebarSessionList
                currentPage={sidebarFixture?.currentPage ?? sessionPage}
                fixtureVariant={fixtureVariant}
                hidePagination={sidebarFixture?.hideSessionPagination}
                totalPages={sidebarFixture ? undefined : Math.max(1, Math.ceil(sessionTotal / 50))}
                sessionCountLabel={sidebarFixture?.sessionCountLabel}
                actionsDisabled={Boolean(pendingSessionOperation)}
                sessions={displayedSessions}
                showHistory={!hideSessionHistory}
                onAction={sidebarFixture ? undefined : handleSessionAction}
                onPageChange={
                  sidebarFixture
                    ? undefined
                    : (page) => {
                        setSessionPage(page);
                      }
                }
              />
              {realMode && !sidebarFixture ? (
                <>
                  {sessionLoading ? (
                    <div className={styles.sessionStatus} role="status">
                      正在加载会话...
                    </div>
                  ) : null}
                  {sessionError ? (
                    <div className={styles.sessionError} role="alert">
                      <span>{sessionError}</span>
                      <Button variant="ghost" size="sm" type="button" onClick={() => void refreshSessions()}>
                        重试
                      </Button>
                    </div>
                  ) : null}
                </>
              ) : null}
              {realMode ? (
                <Button
                  className={styles.deletedButton}
                  variant="ghost"
                  disabled={Boolean(pendingSessionOperation)}
                  onClick={() => void openDeletedSessions()}
                >
                  查看已删除会话
                </Button>
              ) : null}
            </div>
            {!sidebarFixture?.hideSecondaryNavigation ? (
              <nav className={styles.secondarySideNav} aria-label="饮食工具">
                <SidebarTooltip collapsed={sidebarCollapsed} label="饮食记录">
                  <NavLink
                    aria-label="饮食记录"
                    className={fixedSideLink(activeModule === 'records')}
                    to={`${ROUTES.ANALYSIS}?view=records`}
                  >
                    {renderWorkspaceIcon('dietRecords', <Table2 aria-hidden="true" />)}
                    <span>饮食记录</span>
                  </NavLink>
                </SidebarTooltip>
                <SidebarTooltip collapsed={sidebarCollapsed} label="摄入分析">
                  <NavLink
                    aria-label="摄入分析"
                    className={fixedSideLink(activeModule === 'analysis')}
                    to={ROUTES.ANALYSIS}
                    end
                  >
                    {renderWorkspaceIcon('intakeAnalysis', <ChartColumn aria-hidden="true" />)}
                    <span>摄入分析</span>
                  </NavLink>
                </SidebarTooltip>
                <SidebarTooltip collapsed={sidebarCollapsed} label="餐食规划">
                  <NavLink aria-label="餐食规划" className={sideLink} to={ROUTES.PLANNING}>
                    {renderWorkspaceIcon('mealPlanning', <CalendarDays aria-hidden="true" />)}
                    <span>餐食规划</span>
                  </NavLink>
                </SidebarTooltip>
                <SidebarTooltip collapsed={sidebarCollapsed} label="知识库">
                  <NavLink aria-label="知识库" className={sideLink} to={ROUTES.KNOWLEDGE}>
                    {renderWorkspaceIcon('knowledge', <BookOpen aria-hidden="true" />)}
                    <span>知识库</span>
                  </NavLink>
                </SidebarTooltip>
                <SidebarTooltip collapsed={sidebarCollapsed} label="设置">
                  <Button
                    aria-label="设置"
                    className={styles.sideButton}
                    variant="ghost"
                    type="button"
                    onClick={() => announce('设置入口将在设置页面完成后启用。')}
                  >
                    {renderWorkspaceIcon('settings', <Settings aria-hidden="true" />)}
                    <span>设置</span>
                  </Button>
                </SidebarTooltip>
              </nav>
            ) : null}
            <div className={styles.accountDock}>
              {!sidebarFixture?.hideCollapseButton ? (
                <Button
                  className={styles.collapseButton}
                  variant="ghost"
                  type="button"
                  aria-expanded={!sidebarCollapsed}
                  aria-label={sidebarCollapsed ? '展开导航' : '收起导航'}
                  title={sidebarCollapsed ? '展开导航' : '收起导航'}
                  onClick={() => setSidebarCollapsed((current) => !current)}
                >
                  {sidebarCollapsed ? <PanelLeftOpen aria-hidden="true" /> : <PanelLeftClose aria-hidden="true" />}
                  <span>{sidebarCollapsed ? '展开导航' : '收起导航'}</span>
                </Button>
              ) : null}
              <div className={styles.statusPill}>
                {fixtureVariant ? <FigmaWorkspaceAsset variant={fixtureVariant} name="statusDot" /> : <span />}
                <span>就绪 (Fustat-v2)</span>
              </div>
              <Link className={styles.profile} to={isAuthenticated ? ROUTES.PROFILE : ROUTES.LOGIN}>
                <div className={styles.avatar}>
                  <AvatarImage
                    avatarUrl={sidebarAvatar}
                    allowUploaded={realMode && !isFixtureLayout}
                    data-avatar-role="workspace-sidebar"
                    defaultOnly={defaultOnlyAvatar}
                    gender={layoutAvatarGender}
                    alt=""
                  />
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
        ) : null}
        <main className={styles.main}>
          <header
            className={`${styles.topbar} ${topbarVariant === 'planning-list' ? styles.planningListTopbar : ''}`}
            data-topbar-variant={topbarVariant}
          >
            <BrandLogo
              size="compact"
              showMarkLetter={topbarShowMarkLetter && (showKnowledgeTopNav || (!sidebarFixture && !designChat))}
            />
            <nav className={styles.nav} aria-label={activeModule === 'profile' ? '个人中心导航' : '主导航'}>
              {activeModule === 'profile' ? (
                profileActiveTab ? (
                  [
                    { key: 'basic', label: '基本资料', to: ROUTES.PROFILE },
                    { key: 'memories', label: '记忆与偏好', to: ROUTES.PROFILE_MEMORIES },
                    { key: 'security', label: '安全与设备', to: ROUTES.PROFILE_SECURITY },
                    { key: 'privacy', label: '数据与隐私', to: ROUTES.PROFILE_DATA },
                  ].map((item) => {
                    const isActive = profileActiveTab === item.key;
                    return (
                      <Link
                        aria-current={isActive ? 'page' : undefined}
                        className={topLink(isActive)}
                        key={item.key}
                        to={item.to}
                      >
                        {item.label}
                      </Link>
                    );
                  })
                ) : (
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
                )
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
                {renderWorkspaceIcon('topbarSearch', <Search aria-hidden="true" />)}
                <Input placeholder="搜索工作区..." aria-label="搜索工作区" />
              </div>
              <Tooltip>
                <TooltipTrigger asChild>
                  <Button
                    className={styles.iconButton}
                    variant="ghost"
                    size="icon"
                    type="button"
                    aria-label="通知"
                    onClick={() => announce('暂无新的工作区通知。')}
                  >
                    {renderWorkspaceIcon('notification', <Bell aria-hidden="true" />)}
                  </Button>
                </TooltipTrigger>
                <TooltipContent>通知</TooltipContent>
              </Tooltip>
              <DropdownMenu>
                <DropdownMenuTrigger asChild>
                  <Button className={styles.userButton} variant="ghost" type="button">
                    <span className={styles.topAvatar}>
                      <AvatarImage
                        avatarUrl={topAvatar}
                        allowUploaded={realMode && !isFixtureLayout}
                        data-avatar-role="workspace-topbar"
                        defaultOnly={defaultOnlyAvatar}
                        gender={layoutAvatarGender}
                        alt=""
                      />
                    </span>
                    <span>{displayName}</span>
                  </Button>
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
              <Button
                variant="outline"
                disabled={Boolean(pendingSessionOperation)}
                onClick={() => setRenameTarget(undefined)}
              >
                取消
              </Button>
              <Button disabled={Boolean(pendingSessionOperation)} onClick={() => void saveRename()}>
                保存
              </Button>
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
              <Button
                variant="outline"
                disabled={Boolean(pendingSessionOperation)}
                onClick={() => setDeleteTarget(undefined)}
              >
                取消
              </Button>
              <Button
                variant="destructive"
                disabled={Boolean(pendingSessionOperation)}
                onClick={() => void confirmDelete()}
              >
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
                    disabled={Boolean(pendingSessionOperation)}
                    onClick={() => void restoreDeletedSession(String(session.session_id))}
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
