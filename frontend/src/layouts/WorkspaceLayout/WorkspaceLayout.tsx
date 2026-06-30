import { Button, Dropdown, Input, Menu, Tag, Tooltip } from '@arco-design/web-react';
import { IconBook, IconMessage, IconMenu, IconPlus, IconSearch, IconUser } from '@arco-design/web-react/icon';
import { Link, NavLink } from 'react-router-dom';
import { mockSessions } from '../../mock/sessions';
import { mockAuthScenarios, mockAuthStatus, mockAuthUser } from '../../mock/auth';
import { SidebarSessionList } from '../../components/workspace/SidebarSessionList';
import { BrandLogo } from '../../components/brand/BrandLogo';
import styles from './WorkspaceLayout.module.css';

type WorkspaceLayoutProps = {
  children: React.ReactNode;
  activeModule?: 'home' | 'chat' | 'analysis' | 'planning' | 'knowledge' | 'profile' | 'admin';
  moduleLabel?: React.ReactNode;
};

export function WorkspaceLayout({ children, activeModule = 'home', moduleLabel }: WorkspaceLayoutProps) {
  const currentAuth = mockAuthScenarios.find((item) => item.status === mockAuthStatus) ?? mockAuthScenarios[0];
  const isAuthenticated = mockAuthStatus === 'authenticated';
  const canAccessAdmin = isAuthenticated && (mockAuthUser.role === 'admin' || mockAuthUser.role === 'operator');
  const userMenu = (
    <Menu>
      <Menu.Item key="profile">
        <Link className={styles.menuLink} to={isAuthenticated ? '/profile' : '/login'}>
          个人资料
        </Link>
      </Menu.Item>
      {canAccessAdmin ? (
        <Menu.Item key="admin">
          <Link className={styles.menuLink} to="/admin">
            管理后台
          </Link>
        </Menu.Item>
      ) : null}
      <Menu.Item key="expired">模拟登录过期</Menu.Item>
      <Menu.Item key="logout">
        <Link className={styles.menuLink} to="/login">
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
        <Link className={styles.newButton} to="/chat/week-plan">
          <IconPlus />
          <span>新建 Agent 会话</span>
        </Link>
        <Input className={styles.search} prefix={<IconSearch />} placeholder="搜索会话" allowClear />
        <SidebarSessionList sessions={mockSessions} />
        <div className={styles.accountDock}>
          <Link className={styles.profile} to={isAuthenticated ? '/profile' : '/login'}>
            <div className={styles.avatar}>{isAuthenticated ? mockAuthUser.displayName.slice(0, 1) : '访'}</div>
            <div>
              <strong>{isAuthenticated ? mockAuthUser.displayName : '未登录'}</strong>
              <span>{isAuthenticated ? `${mockAuthUser.role} · ${mockAuthUser.profile.preference}` : currentAuth.title}</span>
            </div>
          </Link>
          {isAuthenticated ? (
            <Link className={styles.logoutLink} to="/login">
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
            <NavLink className={styles.navItem} to="/chat/week-plan">
              <Tag icon={<IconMessage />} color={activeModule === 'chat' ? 'green' : 'gray'}>
                Agent 会话
              </Tag>
            </NavLink>
            <NavLink className={({ isActive }) => (isActive || activeModule === 'planning' ? styles.navItem : styles.navItem)} to="/planning">
              <Tag color={activeModule === 'planning' ? 'green' : 'gray'}>饮食管理</Tag>
            </NavLink>
            <NavLink className={styles.navItem} to="/knowledge">
              <Tag icon={<IconBook />} color={activeModule === 'knowledge' ? 'green' : 'gray'}>
                知识库
              </Tag>
            </NavLink>
            <NavLink className={styles.navItem} to="/analysis">
              <Tag color={activeModule === 'analysis' ? 'green' : 'gray'}>数据分析</Tag>
            </NavLink>
            {canAccessAdmin ? (
              <NavLink className={styles.navItem} to="/admin">
                <Tag color={activeModule === 'admin' ? 'green' : 'gray'}>管理后台</Tag>
              </NavLink>
            ) : null}
          </nav>
          <div className={styles.userActions}>
            {isAuthenticated ? (
              <Link className={styles.topbarLogoutLink} to="/login">
                <Button className={styles.topbarLogoutButton}>退出登录</Button>
              </Link>
            ) : null}
            <Dropdown droplist={userMenu} position="br">
              <Button className={styles.userButton} icon={<IconUser />}>
                {isAuthenticated ? mockAuthUser.displayName : '登录'}
              </Button>
            </Dropdown>
          </div>
        </header>
        {children}
      </main>
    </div>
  );
}
