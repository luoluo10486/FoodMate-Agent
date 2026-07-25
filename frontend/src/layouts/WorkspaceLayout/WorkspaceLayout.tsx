import { Button, Dropdown, Input, Menu, Message, Modal, Tag, Tooltip } from '@arco-design/web-react';
import { IconBook, IconMessage, IconMenu, IconPlus, IconSearch, IconUser } from '@arco-design/web-react/icon';
import { Link, NavLink, useLocation, useNavigate } from 'react-router-dom';
import { useEffect, useState } from 'react';
import { ROUTES, buildChatPath } from '../../constants/routes';
import { archiveSession, createSession, deleteSession, loadDeletedSessions, loadSessions, renameSession, restoreSession, searchSessions, unarchiveSession, type RealSession } from '../../services/sessionService';
import { getAuthScenarios, getAuthStatus, getAuthUser, loadCurrentUser, logout } from '../../services/authService';
import { SidebarSessionList, type SessionAction } from '../../components/workspace/SidebarSessionList';
import { BrandLogo } from '../../components/brand/BrandLogo';
import styles from './WorkspaceLayout.module.css';

type WorkspaceLayoutProps = { children: React.ReactNode; activeModule?: 'home' | 'chat' | 'analysis' | 'planning' | 'knowledge' | 'profile' | 'admin'; moduleLabel?: React.ReactNode };

export function WorkspaceLayout({ children, activeModule = 'home', moduleLabel }: WorkspaceLayoutProps) {
  const realMode = import.meta.env.VITE_AGENT_MODE === 'real';
  const [authReady, setAuthReady] = useState(!realMode);
  const [currentUser, setCurrentUser] = useState(getAuthUser());
  const [sessions, setSessions] = useState<Awaited<ReturnType<typeof loadSessions>>>([]);
  const [sessionQuery, setSessionQuery] = useState('');
  const [renameTarget, setRenameTarget] = useState<{ id: string; title: string }>();
  const [deletedOpen, setDeletedOpen] = useState(false);
  const [deletedSessions, setDeletedSessions] = useState<RealSession[]>([]);
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
    loadCurrentUser().then((user) => { if (!cancelled) setCurrentUser(user); }).catch(() => undefined).finally(() => { if (!cancelled) setAuthReady(true); });
    return () => { cancelled = true; };
  }, [realMode]);
  useEffect(() => {
    if (authReady && realMode && !isAuthenticated) navigate(`/login?redirect=${encodeURIComponent(location.pathname + location.search)}`, { replace: true });
  }, [authReady, realMode, isAuthenticated, location.pathname, location.search, navigate]);
  useEffect(() => { if (authReady && isAuthenticated) loadSessions().then(setSessions).catch(() => undefined); }, [authReady, isAuthenticated]);
  useEffect(() => {
    if (!realMode || !sessionQuery.trim()) return;
    const timer = window.setTimeout(() => { searchSessions(sessionQuery.trim()).then(setSessions).catch(() => undefined); }, 250);
    return () => window.clearTimeout(timer);
  }, [realMode, sessionQuery]);

  const refreshSessions = () => loadSessions().then(setSessions).catch(() => undefined);
  const handleSessionAction = async (action: SessionAction, session: { id: string; title: string }) => {
    if (action === 'rename') { setRenameTarget({ id: session.id, title: session.title }); return; }
    if (action === 'delete') {
      Modal.confirm({ title: '删除会话', content: '会话会进入回收站，并可在 30 天内恢复。', okText: '删除', cancelText: '取消', onOk: async () => { await deleteSession(session.id); await refreshSessions(); if (location.pathname === `/chat/${session.id}`) navigate('/chat', { replace: true }); } });
      return;
    }
    await (action === 'archive' ? archiveSession(session.id) : unarchiveSession(session.id));
    await refreshSessions();
  };
  const openDeletedSessions = async () => { setDeletedSessions(await loadDeletedSessions()); setDeletedOpen(true); };

  if (!authReady) return <div style={{ padding: 32 }}>正在校验登录状态...</div>;
  if (realMode && !isAuthenticated) return null;

  const userMenu = (
    <Menu>
      <Menu.Item key="profile"><Link className={styles.menuLink} to={isAuthenticated ? ROUTES.PROFILE : ROUTES.LOGIN}>个人资料</Link></Menu.Item>
      {canAccessAdmin ? <Menu.Item key="admin"><Link className={styles.menuLink} to={ROUTES.ADMIN}>管理后台</Link></Menu.Item> : null}
      <Menu.Item key="expired" onClick={() => Message.info('真实模式下会话失效由服务端 401 处理。')}>检查登录状态</Menu.Item>
      <Menu.Item key="logout"><Link className={styles.menuLink} to={ROUTES.LOGIN} onClick={() => { void logout(); }}>退出登录</Link></Menu.Item>
    </Menu>
  );

  return (
    <div className={styles.shell}>
      <aside className={styles.sidebar}>
        <div className={styles.brandRow}><BrandLogo /><span className={styles.modePill}>Agent 模式</span></div>
        <button className={styles.newButton} onClick={() => { if (!realMode) { navigate(buildChatPath('week-plan')); return; } void createSession().then((session) => { void refreshSessions(); navigate(buildChatPath(String(session.session_id))); }); }}><IconPlus /><span>新建 Agent 会话</span></button>
        <Input className={styles.search} prefix={<IconSearch />} placeholder="搜索会话" allowClear value={sessionQuery} onChange={setSessionQuery} />
        <div className={styles.sessionTools}>
          <SidebarSessionList sessions={sessions} onAction={handleSessionAction} />
          {realMode ? <Button className={styles.deletedButton} type="text" onClick={() => void openDeletedSessions()}>查看已删除会话</Button> : null}
        </div>
        <div className={styles.accountDock}>
          <Link className={styles.profile} to={isAuthenticated ? ROUTES.PROFILE : ROUTES.LOGIN}><div className={styles.avatar}>{isAuthenticated ? authUser.displayName.slice(0, 1) : '访'}</div><div><strong>{isAuthenticated ? authUser.displayName : '未登录'}</strong><span>{isAuthenticated ? `${authUser.role} · ${authUser.profile.preference}` : currentAuth.title}</span></div></Link>
          {isAuthenticated ? <Link className={styles.logoutLink} to={ROUTES.LOGIN} onClick={() => { void logout(); }}><Button className={styles.logoutButton} long>退出登录</Button></Link> : null}
        </div>
      </aside>
      <main className={styles.main}>
        <header className={styles.topbar}>
          <Tooltip content="折叠导航"><Button shape="circle" icon={<IconMenu />} onClick={() => Message.info('导航折叠功能暂未启用。')} /></Tooltip>
          {moduleLabel ? <div className={styles.moduleLabel}>{moduleLabel}</div> : null}
          <nav className={styles.nav}>
            <NavLink className={styles.navItem} to={buildChatPath('week-plan')}><Tag icon={<IconMessage />} color={activeModule === 'chat' ? 'green' : 'gray'}>Agent 会话</Tag></NavLink>
            <NavLink className={styles.navItem} to={ROUTES.PLANNING}><Tag color={activeModule === 'planning' ? 'green' : 'gray'}>饮食管理</Tag></NavLink>
            <NavLink className={styles.navItem} to={ROUTES.KNOWLEDGE}><Tag icon={<IconBook />} color={activeModule === 'knowledge' ? 'green' : 'gray'}>知识库</Tag></NavLink>
            <NavLink className={styles.navItem} to={ROUTES.ANALYSIS}><Tag color={activeModule === 'analysis' ? 'green' : 'gray'}>数据分析</Tag></NavLink>
            {canAccessAdmin ? <NavLink className={styles.navItem} to={ROUTES.ADMIN}><Tag color={activeModule === 'admin' ? 'green' : 'gray'}>管理后台</Tag></NavLink> : null}
          </nav>
          <div className={styles.userActions}>{isAuthenticated ? <Link className={styles.topbarLogoutLink} to={ROUTES.LOGIN} onClick={() => { void logout(); }}><Button className={styles.topbarLogoutButton}>退出登录</Button></Link> : null}<Dropdown droplist={userMenu} position="br"><Button className={styles.userButton} icon={<IconUser />}>{isAuthenticated ? authUser.displayName : '登录'}</Button></Dropdown></div>
        </header>
        {children}
      </main>
      <Modal title="重命名会话" visible={Boolean(renameTarget)} onCancel={() => setRenameTarget(undefined)} onOk={async () => { if (!renameTarget?.title.trim()) return; await renameSession(renameTarget.id, renameTarget.title.trim()); setRenameTarget(undefined); await refreshSessions(); }}><Input autoFocus value={renameTarget?.title ?? ''} maxLength={255} onChange={(title) => setRenameTarget((current) => current ? { ...current, title } : current)} /></Modal>
      <Modal title="已删除会话" visible={deletedOpen} footer={null} onCancel={() => setDeletedOpen(false)}>{deletedSessions.length === 0 ? <p>暂无可恢复的会话。</p> : deletedSessions.map((session) => <div className={styles.deletedRow} key={session.session_id}><span>{session.title}</span><Button type="text" onClick={async () => { await restoreSession(String(session.session_id)); setDeletedSessions((items) => items.filter((item) => item.session_id !== session.session_id)); await refreshSessions(); }}>恢复</Button></div>)}</Modal>
    </div>
  );
}
