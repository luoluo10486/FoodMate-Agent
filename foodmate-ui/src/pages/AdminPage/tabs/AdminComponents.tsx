/** Admin-specific composed components built on the shared shadcn primitives. */
import { Link } from 'react-router-dom';
import { ArrowLeft } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { DataTable } from '@/components/ui/data-table';
import { Input } from '@/components/ui/input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
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
      <Badge variant="outline">{meta.tag}</Badge>
    </section>
  );
}

export function AdminFilters({ placeholder = 'trace_id / user_id' }: { placeholder?: string }) {
  return (
    <section className={styles.filters}>
      <strong>筛选</strong>
      <Select defaultValue="all">
        <SelectTrigger className={styles.filterControl} aria-label="状态筛选">
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
          <SelectItem value="all">全部状态</SelectItem>
          <SelectItem value="active">active</SelectItem>
          <SelectItem value="completed">completed</SelectItem>
          <SelectItem value="failed">failed</SelectItem>
        </SelectContent>
      </Select>
      <Select defaultValue="24h">
        <SelectTrigger className={styles.filterControl} aria-label="时间范围">
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
          <SelectItem value="24h">近 24h</SelectItem>
          <SelectItem value="7d">近 7 天</SelectItem>
          <SelectItem value="30d">近 30 天</SelectItem>
        </SelectContent>
      </Select>
      <Input className={styles.filterInput} placeholder={placeholder} aria-label={placeholder} />
      <Button variant="outline" onClick={() => emitNotice('筛选为 mock 操作')}>
        查询
      </Button>
    </section>
  );
}

export function AdminOnlyNotice({ title }: { title: string }) {
  return (
    <Card className={styles.noAccessCard}>
      <Badge variant="destructive">ADMIN_ONLY</Badge>
      <h1>{title}</h1>
      <p>该页面包含用户敏感信息或恢复类高风险能力，按后端接口契约仅 admin 可访问。</p>
      <Link to={ROUTES.ADMIN}>
        <Button variant="outline">
          <ArrowLeft aria-hidden="true" />
          返回概览
        </Button>
      </Link>
    </Card>
  );
}

export function MiniStat({
  label,
  value,
  hint,
  tone = 'green',
}: {
  label: string;
  value: string;
  hint: string;
  tone?: string;
}) {
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
        <Badge variant="outline">operator_id / target_type / request_id / trace_id</Badge>
      </div>
      <DataTable columns={operationAuditColumns} data={adminOperationAuditRows} />
    </Card>
  );
}

export function AdminActionsCard({ onAction }: { onAction: (payload: AdminActionPayload) => void }) {
  return (
    <Card className={styles.card}>
      <strong>管理操作</strong>
      <div className={styles.actionGrid}>
        <Button
          variant="outline"
          disabled={!canManage}
          onClick={() =>
            onAction({
              action: '禁用用户',
              targetLabel: 'user_10002',
              targetType: 'user',
              targetId: 'user_10002',
              onApply: () => {
                const target = adminUserRows.find((item) => item.userId === 'user_10002');
                if (target) target.status = 'disabled';
              },
            })
          }
        >
          禁用用户
        </Button>
        <Button
          variant="outline"
          disabled={!canManage}
          onClick={() =>
            onAction({
              action: '重置会话',
              targetLabel: 'user_10002',
              targetType: 'user_session',
              targetId: 'user_10002',
              onApply: () =>
                adminUserSessionRows
                  .filter((session) => session.userId === 'user_10002')
                  .forEach((session) => {
                    session.status = 'revoked';
                  }),
            })
          }
        >
          重置会话
        </Button>
        <Button
          variant="outline"
          disabled={!canManage}
          onClick={() =>
            onAction({
              action: '工具启停',
              targetLabel: 'food_log_writer',
              targetType: 'tool',
              targetId: 'food_log_writer',
              onApply: () => {
                const tool = adminToolRows.find((item) => item.name === 'food_log_writer');
                if (tool) tool.status = tool.status === 'active' ? 'disabled' : 'active';
              },
            })
          }
        >
          工具启停
        </Button>
        <Button
          variant="outline"
          disabled={!canManage}
          onClick={() =>
            onAction({
              action: '恢复资源',
              targetLabel: 'plan_33910',
              targetType: 'plan',
              targetId: 'plan_33910',
              onApply: () => {
                const rowIndex = adminDeletedRows.findIndex((row) => row.resourceId === 'plan_33910');
                if (rowIndex >= 0) adminDeletedRows.splice(rowIndex, 1);
              },
            })
          }
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
