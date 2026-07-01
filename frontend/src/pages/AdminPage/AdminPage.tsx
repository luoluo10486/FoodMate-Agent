import { Button, Card, Message, Modal, Tag } from '@arco-design/web-react';
import { useState } from 'react';
import { IconApps, IconHome, IconLeft, IconSafe, IconUser } from '@arco-design/web-react/icon';
import { Link, NavLink, useLocation } from 'react-router-dom';
import { BrandLogo } from '../../components/brand/BrandLogo';
import { adminOperationAuditRows } from '../../mock/admin';
import { mockAuthStatus, mockAuthUser } from '../../mock/auth';
import styles from './AdminPage.module.css';
import {
  AdminHeader,
  adminNavItems,
  canAccessAdmin,
  canManage,
  getSectionKey,
  sectionMeta
} from './tabs/AdminShared';
import { DeletedSection } from './tabs/DeletedResourcesTab';
import { KnowledgeSection } from './tabs/KnowledgeTab';
import { OverviewSection } from './tabs/OverviewTab';
import { RunsSection } from './tabs/RunsTab';
import { ToolsSection } from './tabs/ToolsTab';
import { UsageSection } from './tabs/UsageTab';
import { UsersSection } from './tabs/UsersTab';
import type { AdminActionPayload, AdminSectionKey } from './tabs/types';

function renderSection(sectionKey: AdminSectionKey, onAction: (payload: AdminActionPayload) => void) {
  switch (sectionKey) {
    case 'users': return <UsersSection onAction={onAction} />;
    case 'runs': return <RunsSection />;
    case 'tools': return <ToolsSection onAction={onAction} />;
    case 'usage': return <UsageSection />;
    case 'knowledge': return <KnowledgeSection onAction={onAction} />;
    case 'deleted': return <DeletedSection onAction={onAction} />;
    default: return <OverviewSection onAction={onAction} />;
  }
}

export function AdminPage() {
  const { pathname } = useLocation();
  const sectionKey = getSectionKey(pathname) as AdminSectionKey;
  const [, forceRefresh] = useState(0);

  const confirmAdminAction = ({ action, targetLabel, targetType, targetId, onApply }: AdminActionPayload) => {
    if (!canManage) {
      Message.warning('operator 只读，不能执行管理写操作');
      return;
    }

    Modal.confirm({
      title: action,
      content: `确认对 ${targetLabel} 执行该管理操作？当前为 mock 流程，但会追加 request_id / trace_id 审计记录。`,
      okText: '确认执行',
      cancelText: '取消',
      onOk: () => {
        onApply?.();
        adminOperationAuditRows.unshift({
          key: `op-${Date.now()}`,
          operator_id: `user_${mockAuthUser.id}`,
          operator: mockAuthUser.role,
          action,
          target_type: targetType,
          target_id: targetId,
          result: 'success',
          request_id: `req_admin_${Date.now()}`,
          trace_id: `trace_admin_${Date.now()}`,
          created_at: new Date().toLocaleString('zh-CN', { hour12: false }).replace(/\//g, '-')
        });
        if (adminOperationAuditRows.length > 8) {
          adminOperationAuditRows.length = 8;
        }
        forceRefresh((current) => current + 1);
        Message.success(`${action} 已提交，mock 审计记录已更新`);
      }
    });
  };

  if (!canAccessAdmin) {
    return (
      <div className={styles.authShell}>
        <Card className={styles.noAccessCard} bordered={false}>
          <Tag color="red">AUTH_FORBIDDEN</Tag>
          <h1>无权访问管理后台</h1>
          <p>管理后台仅对 admin/operator 开放。普通用户不会看到入口。</p>
          <Link to="/">
            <Button icon={<IconLeft />}>返回工作台</Button>
          </Link>
        </Card>
      </div>
    );
  }

  return (
    <div className={styles.adminShell}>
      <aside className={styles.adminSidebar}>
        <div className={styles.brandBlock}>
          <BrandLogo />
          <Tag color="green">Admin Console</Tag>
        </div>
        <Link className={styles.workspaceLink} to="/">
          <IconHome />
          <span>返回 Agent 工作台</span>
        </Link>
        <nav className={styles.adminNav} aria-label="管理后台导航">
          {adminNavItems.map((item) => (
            <NavLink
              className={({ isActive }) =>
                `${styles.navButton} ${item.adminOnly && !canManage ? styles.navButtonLocked : ''} ${isActive ? styles.navButtonActive : ''}`
              }
              end={item.path === '/admin'}
              key={item.key}
              to={item.path}
            >
              {item.icon}
              <span>{item.label}</span>
            </NavLink>
          ))}
        </nav>
        <div className={styles.policyBox}>
          <IconSafe />
          <strong>{canManage ? 'admin 可操作' : 'operator 只读'}</strong>
          <span>高风险操作需要二次确认并写入审计。</span>
        </div>
      </aside>
      <main className={styles.adminMain}>
        <header className={styles.topbar}>
          <div className={styles.topbarTitle}>
            <IconApps />
            <span>FoodMate 管理后台</span>
          </div>
          <div className={styles.topbarActions}>
            <Tag color={canManage ? 'green' : 'orange'}>{canManage ? 'admin 可操作' : 'operator 只读'}</Tag>
            <Link to="/profile">
              <Button icon={<IconUser />}>{mockAuthUser.displayName}</Button>
            </Link>
          </div>
        </header>
        <div className={`${styles.page} fm-enter`}>
          <AdminHeader sectionKey={sectionKey} />
          {renderSection(sectionKey, confirmAdminAction)}
        </div>
      </main>
    </div>
  );
}
