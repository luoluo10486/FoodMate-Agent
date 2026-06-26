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
  activeModule?: 'home' | 'chat' | 'analysis' | 'planning' | 'knowledge';
  moduleLabel?: React.ReactNode;
};

export function WorkspaceLayout({ children, activeModule = 'home', moduleLabel }: WorkspaceLayoutProps) {
  const currentAuth = mockAuthScenarios.find((item) => item.status === mockAuthStatus) ?? mockAuthScenarios[0];
  const isAuthenticated = mockAuthStatus === 'authenticated';
  const userMenu = (
    <Menu>
      <Menu.Item key="profile">查看登录信息</Menu.Item>
      <Menu.Item key="expired">模拟登录过期</Menu.Item>
      <Menu.Item key="logout">退出登录占位</Menu.Item>
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
        <Link className={styles.profile} to="/login">
          <div className={styles.avatar}>{isAuthenticated ? '梁' : '访'}</div>
          <div>
            <strong>{isAuthenticated ? mockAuthUser.displayName : '未登录'}</strong>
            <span>{isAuthenticated ? mockAuthUser.profile.preference : currentAuth.title}</span>
          </div>
        </Link>
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
          </nav>
          <Dropdown droplist={userMenu} position="br">
            <Link className={styles.userButton} to="/login">
              <Button icon={<IconUser />}>{isAuthenticated ? mockAuthUser.displayName : '登录'}</Button>
            </Link>
          </Dropdown>
        </header>
        {children}
      </main>
    </div>
  );
}
