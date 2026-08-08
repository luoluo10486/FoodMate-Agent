/** Admin-specific composed components built on the shared shadcn primitives. */
import { Button, Card, Input, Table, Tag, IconLeft } from './AdminPrimitives';
import { Link } from 'react-router-dom';
import { ROUTES } from '../../../constants/routes';
import {
  adminDeletedRows,
  adminOperationAuditRows,
  adminResourceCards,
  adminUserRows,
  adminUserSessionRows,
  adminToolRows,
  canManage,
  operationAuditColumns,
  sectionMeta,
} from './AdminShared';
import type { AdminActionPayload } from './types';
import styles from '../AdminPage.module.css';

function emitNotice(message: string) {
  window.dispatchEvent(new CustomEvent('foodmate:admin-notice', { detail: { message, tone: 'info' } }));
}

export function AdminHeader({ sectionKey }: { sectionKey: string }) {
  const meta = sectionMeta[sectionKey] ?? sectionMeta.overview;
  return (
    <section className={styles.header}>
      <div>
        <h1>{meta.title}</h1>
        <p>{meta.description}</p>
      </div>
      <Tag color="blue">{meta.tag}</Tag>
    </section>
  );
}

export function AdminFilters({ placeholder = 'trace_id / user_id' }: { placeholder?: string }) {
  return (
    <section className={styles.filters}>
      <strong>筛选</strong>
      <select className={styles.filterControl} defaultValue="all" aria-label="状态筛选">
        <option value="all">全部状态</option>
        <option value="active">active</option>
        <option value="completed">completed</option>
        <option value="failed">failed</option>
      </select>
      <select className={styles.filterControl} defaultValue="24h" aria-label="时间范围">
        <option value="24h">近 24h</option>
        <option value="7d">近 7 天</option>
        <option value="30d">近 30 天</option>
      </select>
      <Input className={styles.filterInput} placeholder={placeholder} aria-label={placeholder} />
      <Button onClick={() => emitNotice('筛选为 mock 操作')}>查询</Button>
    </section>
  );
}

export function AdminOnlyNotice({ title }: { title: string }) {
  return (
    <Card className={styles.noAccessCard}>
      <Tag color="red">ADMIN_ONLY</Tag>
      <h1>{title}</h1>
      <p>该页面包含用户敏感信息或恢复类高风险能力，按后端接口契约仅 admin 可访问。</p>
      <Link to={ROUTES.ADMIN}>
        <Button icon={<IconLeft />}>返回概览</Button>
      </Link>
    </Card>
  );
}

export function MiniStat({ label, value, hint, tone = 'green' }: { label: string; value: string; hint: string; tone?: string }) {
  return (
    <article className={`${styles.metric} ${styles[tone]}`}>
      <span>{label}</span>
      <strong>{value}</strong>
      <em>{hint}</em>
    </article>
  );
}

export function OperationAuditCard() {
  return (
    <Card className={styles.wideCard}>
      <div className={styles.cardHead}>
        <strong>管理操作审计</strong>
        <Tag color="blue">operator_id / target_type / request_id / trace_id</Tag>
      </div>
      <Table columns={operationAuditColumns} data={adminOperationAuditRows} pagination={{ pageSize: 4 }} size="small" />
    </Card>
  );
}

export function AdminActionsCard({ onAction }: { onAction: (payload: AdminActionPayload) => void }) {
  return (
    <Card className={styles.card}>
      <strong>管理操作</strong>
      <div className={styles.actionGrid}>
        <Button
          disabled={!canManage}
          onClick={() => onAction({
            action: '禁用用户',
            targetLabel: 'user_10002',
            targetType: 'user',
            targetId: 'user_10002',
            onApply: () => {
              const target = adminUserRows.find((item) => item.userId === 'user_10002');
              if (target) target.status = 'disabled';
            },
          })}
        >
          禁用用户
        </Button>
        <Button
          disabled={!canManage}
          onClick={() => onAction({
            action: '重置会话',
            targetLabel: 'user_10002',
            targetType: 'user_session',
            targetId: 'user_10002',
            onApply: () => adminUserSessionRows.filter((session) => session.userId === 'user_10002').forEach((session) => { session.status = 'revoked'; }),
          })}
        >
          重置会话
        </Button>
        <Button
          disabled={!canManage}
          onClick={() => onAction({
            action: '工具启停',
            targetLabel: 'food_log_writer',
            targetType: 'tool',
            targetId: 'food_log_writer',
            onApply: () => {
              const tool = adminToolRows.find((item) => item.name === 'food_log_writer');
              if (tool) tool.status = tool.status === 'active' ? 'disabled' : 'active';
            },
          })}
        >
          工具启停
        </Button>
        <Button
          disabled={!canManage}
          onClick={() => onAction({
            action: '恢复资源',
            targetLabel: 'meal_plan_73',
            targetType: 'meal_plan',
            targetId: 'meal_plan_73',
            onApply: () => {
              const rowIndex = adminDeletedRows.findIndex((row) => row.resourceId === 'meal_plan_73');
              if (rowIndex >= 0) adminDeletedRows.splice(rowIndex, 1);
            },
          })}
        >
          恢复资源
        </Button>
      </div>
      <p>高风险操作必须二次确认并写审计。operator 只能查看，不能执行。</p>
    </Card>
  );
}

export function GovernanceResourceCard() {
  return (
    <Card className={styles.card}>
      <strong>治理资源</strong>
      <div className={styles.resourceList}>
        {adminResourceCards.map((item) => (
          <article key={item.title}>
            <span>{item.title}</span>
            <strong>{item.value}</strong>
            <em>{item.detail}</em>
          </article>
        ))}
      </div>
    </Card>
  );
}
