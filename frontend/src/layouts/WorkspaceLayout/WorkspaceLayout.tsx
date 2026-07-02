import { Button, Dropdown, Input, Menu, Tag, Tooltip } from '@arco-design/web-react';
import { IconBook, IconMessage, IconMenu, IconPlus, IconSearch, IconUser } from '@arco-design/web-react/icon';
import { Link, NavLink } from 'react-router-dom';
import { ROUTES, buildChatPath } from '../../constants/routes';
import { getSessions } from '../../services/sessionService';
import { getAuthScenarios, getAuthStatus, getAuthUser } from '../../services/authService';
import { SidebarSessionList } from '../../components/workspace/SidebarSessionList';
import { BrandLogo } from '../../components/brand/BrandLogo';
import styles from './WorkspaceLayout.module.css';

type WorkspaceLayoutProps = {
  children: React.ReactNode;
  activeModule?: 'home' | 'chat' | 'analysis' | 'planning' | 'knowledge' | 'profile' | 'admin';
  moduleLabel?: React.ReactNode;
};

export function WorkspaceLayout({ children, activeModule = 'home', moduleLabel }: WorkspaceLayoutProps) {
  const authStatus = getAuthStatus();
  const authUser = getAuthUser();
  const authScenarios = getAuthScenarios();
  const currentAuth = authScenarios.find((item) => item.status === authStatus) ?? authScenarios[0];
  const isAuthenticated = authStatus === 'authenticated';
  const canAccessAdmin = isAuthenticated && (authUser.role === 'admin' || authUser.role === 'operator');
  const userMenu = (
    <Menu>
      <Menu.Item key="profile">
        <Link className={styles.menuLink} to={isAuthenticated ? ROUTES.PROFILE : ROUTES.LOGIN}>
          个人资料
        </Link>
      </Menu.Item>
      {canAccessAdmin ? (
        <Menu.Item key="admin">
          <Link className={styles.menuLink} to={ROUTES.ADMIN}>
            管理后台
          </Link>
        </Menu.Item>
      ) : null}
      <Menu.Item key="expired">模拟登录过期</Menu.Item>
      <Menu.Item key="logout">
        <Link className={styles.menuLink} to={ROUTES.LOGIN}>
          退出登录占位
        </Link>
      </Menu.Item>
    </Menu>
  );

  return (
    <div className={styles.shell}>
      <aside className={styles.sidebar}>
        <div className={styles.brandRow}>
          <BrandLogo />
          <span className={styles.modePill}>Agent 模式</span>
        </div>
        <Link className={styles.newButton} to={buildChatPath('week-plan')}>
          <IconPlus />
          <span>新建 Agent 会话</span>
        </Link>
        <Input className={styles.search} prefix={<IconSearch />} placeholder="搜索会话" allowClear />
        <SidebarSessionList sessions={getSessions()} />
        <div className={styles.accountDock}>
          <Link className={styles.profile} to={isAuthenticated ? ROUTES.PROFILE : ROUTES.LOGIN}>
            <div className={styles.avatar}>{isAuthenticated ? authUser.displayName.slice(0, 1) : '访'}</div>
            <div>
              <strong>{isAuthenticated ? authUser.displayName : '未登录'}</strong>
              <span>{isAuthenticated ? `${authUser.role} · ${authUser.profile.preference}` : currentAuth.title}</span>
            </div>
          </Link>
          {isAuthenticated ? (
            <Link className={styles.logoutLink} to={ROUTES.LOGIN}>
              <Button className={styles.logoutButton} long>
                退出登录
              </Button>
            </Link>
          ) : null}
        </div>
      </aside>

      <main className={styles.main}>
        <header className={styles.topbar}>
          <Tooltip content="折叠导航">
            <Button shape="circle" icon={<IconMenu />} />
          </Tooltip>
          {moduleLabel ? <div className={styles.moduleLabel}>{moduleLabel}</div> : null}
          <nav className={styles.nav}>
            <NavLink className={styles.navItem} to={buildChatPath('week-plan')}>
              <Tag icon={<IconMessage />} color={activeModule === 'chat' ? 'green' : 'gray'}>
                Agent 会话
              </Tag>
            </NavLink>
            <NavLink className={styles.navItem} to={ROUTES.PLANNING}>
              <Tag color={activeModule === 'planning' ? 'green' : 'gray'}>饮食管理</Tag>
            </NavLink>
            <NavLink className={styles.navItem} to={ROUTES.KNOWLEDGE}>
              <Tag icon={<IconBook />} color={activeModule === 'knowledge' ? 'green' : 'gray'}>
                知识库
              </Tag>
            </NavLink>
            <NavLink className={styles.navItem} to={ROUTES.ANALYSIS}>
              <Tag color={activeModule === 'analysis' ? 'green' : 'gray'}>数据分析</Tag>
            </NavLink>
            {canAccessAdmin ? (
              <NavLink className={styles.navItem} to={ROUTES.ADMIN}>
                <Tag color={activeModule === 'admin' ? 'green' : 'gray'}>管理后台</Tag>
              </NavLink>
            ) : null}
          </nav>
          <div className={styles.userActions}>
            {isAuthenticated ? (
              <Link className={styles.topbarLogoutLink} to={ROUTES.LOGIN}>
                <Button className={styles.topbarLogoutButton}>退出登录</Button>
              </Link>
            ) : null}
            <Dropdown droplist={userMenu} position="br">
              <Button className={styles.userButton} icon={<IconUser />}>
                {isAuthenticated ? authUser.displayName : '登录'}
              </Button>
            </Dropdown>
          </div>
        </header>
        {children}
      </main>
    </div>
  );
}
