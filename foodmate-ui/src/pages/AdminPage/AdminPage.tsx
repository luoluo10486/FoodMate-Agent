import { useEffect, useState } from 'react';
import { Link, NavLink, useLocation } from 'react-router-dom';
import { Button, Card, IconApps, IconHome, IconLeft, IconSafe, IconUser, Tag } from './tabs/AdminPrimitives';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { ROUTES } from '../../constants/routes';
import { BrandLogo } from '../../components/brand/BrandLogo';
import { adminOperationAuditRows } from '../../services/adminService';
import { getAuthUser } from '../../services/authService';
import styles from './AdminPage.module.css';
import { AdminHeader } from './tabs/AdminComponents';
import { adminNavItems, canAccessAdmin, canManage, getSectionKey } from './tabs/AdminShared';
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
    case 'users':
      return <UsersSection onAction={onAction} />;
    case 'runs':
      return <RunsSection />;
    case 'tools':
      return <ToolsSection onAction={onAction} />;
    case 'usage':
      return <UsageSection />;
    case 'knowledge':
      return <KnowledgeSection onAction={onAction} />;
    case 'deleted':
      return <DeletedSection onAction={onAction} />;
    default:
      return <OverviewSection onAction={onAction} />;
  }
}

export function AdminPage() {
  const authUser = getAuthUser();
  const { pathname } = useLocation();
  const sectionKey = getSectionKey(pathname) as AdminSectionKey;
  const [pendingAction, setPendingAction] = useState<AdminActionPayload>();
  const [notice, setNotice] = useState('');

  useEffect(() => {
    const handleNotice = (event: Event) => {
      const detail = (event as CustomEvent<{ message?: string }>).detail;
      if (detail?.message) setNotice(detail.message);
    };
    window.addEventListener('foodmate:admin-notice', handleNotice);
    return () => window.removeEventListener('foodmate:admin-notice', handleNotice);
  }, []);

  const requestAdminAction = (payload: AdminActionPayload) => {
    if (!canManage) {
      setNotice('operator 只读，不能执行管理写操作');
      return;
    }
    setPendingAction(payload);
  };

  const executePendingAction = async () => {
    if (!pendingAction) return;
    const { action, targetType, targetId, onApply, execute } = pendingAction;
    setPendingAction(undefined);
    if (import.meta.env.VITE_AGENT_MODE === 'real') {
      await execute?.();
      setNotice(`${action} 已完成`);
      window.location.reload();
      return;
    }
    onApply?.();
    adminOperationAuditRows.unshift({
      key: `op-${Date.now()}`,
      operator_id: `user_${authUser.id}`,
      operator: authUser.role,
      action,
      target_type: targetType,
      target_id: targetId,
      result: 'success',
      request_id: `req_admin_${Date.now()}`,
      trace_id: `trace_admin_${Date.now()}`,
      created_at: new Date().toLocaleString('zh-CN', { hour12: false }).replace(/\//g, '-'),
    });
    if (adminOperationAuditRows.length > 8) adminOperationAuditRows.length = 8;
    setNotice(`${action} 已提交，审计记录已写入`);
  };

  if (!canAccessAdmin) {
    return (
      <div className={styles.authShell}>
        <Card className={styles.noAccessCard}>
          <Tag color="red">AUTH_FORBIDDEN</Tag>
          <h1>无权访问管理后台</h1>
          <p>管理后台仅对 admin/operator 开放，普通用户不会看到入口。</p>
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
        <Link className={styles.workspaceLink} to={ROUTES.HOME}>
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
            <Link to={ROUTES.PROFILE}>
              <Button icon={<IconUser />}>{authUser.displayName}</Button>
            </Link>
          </div>
        </header>
        <div className={`${styles.page} fm-enter`}>
          {notice ? <div className={styles.notice} role="status">{notice}</div> : null}
          <AdminHeader sectionKey={sectionKey} />
          {renderSection(sectionKey, requestAdminAction)}
        </div>
      </main>
      <Dialog open={Boolean(pendingAction)} onOpenChange={(open) => { if (!open) setPendingAction(undefined); }}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{pendingAction?.action}</DialogTitle>
            <DialogDescription>确认对 {pendingAction?.targetLabel} 执行该管理操作？操作完成后会记录审计事件。</DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setPendingAction(undefined)}>取消</Button>
            <Button onClick={() => void executePendingAction()}>确认执行</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
