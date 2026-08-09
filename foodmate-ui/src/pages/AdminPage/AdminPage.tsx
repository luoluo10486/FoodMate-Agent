import { useEffect, useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { Button, Card, IconLeft, Tag } from './tabs/AdminPrimitives';
import { ROUTES } from '../../constants/routes';
import { adminOperationAuditRows } from '../../services/adminService';
import { getAuthUser } from '../../services/authService';
import styles from './AdminPage.module.css';
import { AdminHeader } from './tabs/AdminComponents';
import { adminNavItems, canAccessAdmin, canManage, getSectionKey, isAdminNavItemActive } from './tabs/AdminShared';
import { DeletedSection } from './tabs/DeletedResourcesTab';
import { KnowledgeSection } from './tabs/KnowledgeTab';
import { OverviewSection } from './tabs/OverviewTab';
import { RunsSection } from './tabs/RunsTab';
import { ToolsSection } from './tabs/ToolsTab';
import { UsageSection } from './tabs/UsageTab';
import { UsersSection } from './tabs/UsersTab';
import { AdminOperationStatus } from './tabs/AdminOperationStatus';
import type { AdminActionPayload, AdminOperationError, AdminOperationState, AdminSectionKey } from './tabs/types';

const defaultOperationError: AdminOperationError = {
  code: 'REGISTRY_TIMEOUT_504',
  requestId: 'req-foodmate-9082ac918',
  message: '管理服务未能在规定时间内完成请求，请检查服务状态后重试。',
};

function appendOperationAudit(
  authUser: ReturnType<typeof getAuthUser>,
  action: string,
  targetType: string,
  targetId: string,
  result: 'success' | 'failed' = 'success',
  requestId?: string,
) {
  const stamp = Date.now();
  adminOperationAuditRows.unshift({
    key: `op-${stamp}`,
    operator_id: `user_${authUser.id}`,
    operator: authUser.role,
    action,
    target_type: targetType,
    target_id: targetId,
    result,
    request_id: requestId ?? `req_admin_${stamp}`,
    trace_id: `trace_admin_${stamp}`,
    created_at: new Date(stamp).toLocaleString('zh-CN', { hour12: false }).replace(/\//g, '-'),
  });
  if (adminOperationAuditRows.length > 8) adminOperationAuditRows.splice(8);
}

function renderSection(
  sectionKey: AdminSectionKey,
  onAction: (payload: AdminActionPayload) => void,
  refreshNonce: number,
  operationStatus: AdminOperationState,
) {
  switch (sectionKey) {
    case 'users':
      return <UsersSection onAction={onAction} />;
    case 'runs':
      return <RunsSection />;
    case 'tools':
      return <ToolsSection onAction={onAction} operationStatus={operationStatus} refreshNonce={refreshNonce} />;
    case 'usage':
      return <UsageSection />;
    case 'knowledge':
      return <KnowledgeSection onAction={onAction} />;
    case 'deleted':
      return <DeletedSection onAction={onAction} />;
    default:
      return <OverviewSection onAction={onAction} refreshNonce={refreshNonce} />;
  }
}

export function AdminPage() {
  const authUser = getAuthUser();
  const { pathname, search } = useLocation();
  const sectionKey = getSectionKey(pathname) as AdminSectionKey;
  const isRegistryRoute = pathname.endsWith('/tools') && new URLSearchParams(search).get('tab') === 'registry';
  const isDeletedRoute = pathname.endsWith('/deleted');
  const [pendingAction, setPendingAction] = useState<AdminActionPayload>();
  const [operationStatus, setOperationStatus] = useState<AdminOperationState>('idle');
  const [operationError, setOperationError] = useState<AdminOperationError>();
  const [notice, setNotice] = useState('');
  const [refreshNonce, setRefreshNonce] = useState(0);

  useEffect(() => {
    const handleNotice = (event: Event) => {
      const detail = (event as CustomEvent<{ message?: string }>).detail;
      if (detail?.message) setNotice(detail.message);
    };
    window.addEventListener('foodmate:admin-notice', handleNotice);
    return () => window.removeEventListener('foodmate:admin-notice', handleNotice);
  }, []);

  const requestAdminAction = (payload: AdminActionPayload) => {
    setOperationError(undefined);
    setNotice('');
    setPendingAction(payload);
    if (!canManage) {
      setOperationStatus('no-permission');
      return;
    }
    setOperationStatus('confirm');
  };

  const executePendingAction = async () => {
    if (!pendingAction) return;
    const { action, targetType, targetId, onApply, execute } = pendingAction;
    setOperationStatus('submitting');
    try {
      if (import.meta.env.VITE_AGENT_MODE === 'real') {
        await execute?.();
      } else {
        await new Promise<void>((resolve) => window.setTimeout(resolve, 280));
      }
      onApply?.();
      appendOperationAudit(authUser, action, targetType, targetId);
      setRefreshNonce((current) => current + 1);
      setOperationStatus('success');
    } catch (error) {
      const candidate = (error ?? {}) as {
        code?: unknown;
        message?: unknown;
        requestId?: unknown;
        request_id?: unknown;
      };
      const fallback =
        targetType === 'tool'
          ? defaultOperationError
          : {
              code: 'ADMIN_OPERATION_FAILED',
              requestId: 'req_admin_operation_failed',
              message: '管理操作未完成，请检查服务状态后重试。',
            };
      const failedCode = typeof candidate.code === 'string' ? candidate.code : fallback.code;
      const failedRequestId =
        typeof candidate.requestId === 'string'
          ? candidate.requestId
          : typeof candidate.request_id === 'string'
            ? candidate.request_id
            : fallback.requestId;
      setOperationError({
        code: failedCode,
        requestId: failedRequestId,
        message: typeof candidate.message === 'string' ? candidate.message : fallback.message,
      });
      appendOperationAudit(authUser, action, targetType, targetId, 'failed', failedRequestId);
      setOperationStatus('failed');
    }
  };

  const dismissOperation = () => {
    setPendingAction(undefined);
    setOperationError(undefined);
    setOperationStatus('idle');
  };

  const handleRefresh = () => setRefreshNonce((current) => current + 1);

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
          <div className={styles.adminBrand}>
            <span className={styles.adminLogoMark}>F</span>
            <span className={styles.adminLogoCopy}>
              <strong>FoodMate</strong>
              <small>管理控制台</small>
            </span>
          </div>
          <span className={styles.adminTag}>FoodMate 管理</span>
        </div>
        <nav className={styles.adminNav} aria-label="管理后台导航">
          {adminNavItems.map((item) => {
            const isActive = isAdminNavItemActive(item.path, pathname, search);
            return (
              <Link
                aria-current={isActive ? 'page' : undefined}
                className={`${styles.navButton} ${item.adminOnly && !canManage ? styles.navButtonLocked : ''} ${isActive ? styles.navButtonActive : ''}`}
                key={item.key}
                to={item.path}
              >
                {item.icon}
                <span>{item.label}</span>
              </Link>
            );
          })}
        </nav>
        <div className={styles.sidebarFooter}>
          <div className={styles.privilegeBox}>
            <span className={styles.privilegeDot} aria-hidden="true" />
            <strong>{canManage ? '管理员：完全权限' : '操作员：只读'}</strong>
          </div>
          <Link className={styles.workspaceLink} to={ROUTES.HOME}>
            返回 Agent 工作区
          </Link>
          <div className={styles.userSection}>
            <div className={styles.userAvatar}>{authUser.displayName.slice(0, 1)}</div>
            <div className={styles.userMetadata}>
              <strong>{authUser.displayName}</strong>
              <small>ID: {authUser.id}</small>
            </div>
          </div>
        </div>
      </aside>
      <main className={styles.adminMain}>
        <header className={styles.topbar}>
          <div className={styles.topbarTitle}>
            <strong>
              {sectionKey === 'overview'
                ? '管理概览'
                : isRegistryRoute
                  ? '工具注册表'
                  : isDeletedRoute
                    ? '删除资源管理'
                    : '管理控制台'}
            </strong>
            {sectionKey === 'overview' || isRegistryRoute ? (
              <span className={styles.envBadge}>生产环境</span>
            ) : isDeletedRoute ? (
              <span className={styles.securityBadge}>审计存档区</span>
            ) : null}
          </div>
          <div className={styles.topbarActions}>
            <span className={styles.refreshStatus}>
              {isRegistryRoute
                ? '服务节点：healthy-cluster-0'
                : isDeletedRoute
                  ? '存档保留时长：90天安全窗口'
                  : '数据刷新：刚刚'}
            </span>
            <Button
              className={styles.topbarRefresh}
              onClick={isDeletedRoute ? () => setNotice('合规性审计记录仅供查看，恢复操作会写入审计。') : handleRefresh}
            >
              {isRegistryRoute ? '更新状态' : isDeletedRoute ? '合规性审计' : '刷新数据'}
            </Button>
          </div>
        </header>
        <div className={`${styles.page} fm-enter`}>
          {notice ? (
            <div className={styles.notice} role="status">
              {notice}
            </div>
          ) : null}
          <AdminOperationStatus
            status={operationStatus}
            action={pendingAction}
            error={operationError}
            onConfirm={() => void executePendingAction()}
            onCancel={dismissOperation}
            onRetry={() => void executePendingAction()}
            onDismiss={dismissOperation}
          />
          {sectionKey === 'overview' || isRegistryRoute || isDeletedRoute ? null : (
            <AdminHeader sectionKey={sectionKey} />
          )}
          {renderSection(sectionKey, requestAdminAction, refreshNonce, operationStatus)}
        </div>
      </main>
    </div>
  );
}
