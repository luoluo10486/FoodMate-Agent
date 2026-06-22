import { Button, Input, Tag, Tooltip } from '@arco-design/web-react';
import { IconMessage, IconMenu, IconPlus, IconSearch, IconUser } from '@arco-design/web-react/icon';
import { NavLink } from 'react-router-dom';
import { mockSessions } from '../../mock/sessions';
import { SidebarSessionList } from '../../components/workspace/SidebarSessionList';
import { BrandLogo } from '../../components/brand/BrandLogo';
import styles from './WorkspaceLayout.module.css';

type WorkspaceLayoutProps = {
  children: React.ReactNode;
  activeModule?: 'home' | 'chat' | 'analysis' | 'planning';
  moduleLabel?: React.ReactNode;
};

export function WorkspaceLayout({ children, activeModule = 'home', moduleLabel }: WorkspaceLayoutProps) {
  return (
    <div className={styles.shell}>
      <aside className={styles.sidebar}>
        <div className={styles.brandRow}>
          <BrandLogo />
          <span className={styles.modePill}>Agent 模式</span>
        </div>
        <Button className={styles.newButton} type="primary" icon={<IconPlus />} href="/chat/week-plan">
          新建 Agent 会话
        </Button>
        <Input className={styles.search} prefix={<IconSearch />} placeholder="搜索会话" allowClear />
        <SidebarSessionList sessions={mockSessions} />
        <div className={styles.profile}>
          <div className={styles.avatar}>梁</div>
          <div>
            <strong>营养目标</strong>
            <span>高蛋白 · 控预算</span>
          </div>
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
            <NavLink className={styles.navItem} to="/analysis">
              <Tag color={activeModule === 'analysis' ? 'green' : 'gray'}>数据分析</Tag>
            </NavLink>
          </nav>
          <Button icon={<IconUser />}>用户</Button>
        </header>
        {children}
      </main>
    </div>
  );
}
