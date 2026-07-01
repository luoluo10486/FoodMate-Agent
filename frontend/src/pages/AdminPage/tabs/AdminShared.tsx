import type { ReactNode } from 'react';
import { Button, Card, Input, Message, Select, Table, Tag } from '@arco-design/web-react';
import type { TableColumnProps } from '@arco-design/web-react';
import { IconBook, IconDashboard, IconHistory, IconLeft, IconStorage, IconThunderbolt, IconTool, IconUserGroup } from '@arco-design/web-react/icon';
import { Link } from 'react-router-dom';
import {
  adminAuditRows,
  adminDeletedRows,
  adminKnowledgeRows,
  adminModelUsageRows,
  adminOperationAuditRows,
  adminOverviewMetrics,
  adminResourceCards,
  adminSqlAuditRows,
  adminToolCallRows,
  adminToolRows,
  adminTraceRows,
  adminUserRows,
  adminUserSessionRows
} from '../../../mock/admin';
import { mockAuthStatus, mockAuthUser } from '../../../mock/auth';
import type { AdminActionPayload } from './types';
import styles from '../AdminPage.module.css';

const Option = Select.Option;

// ── Auth ──────────────────────────────────────────
export const canAccessAdmin = mockAuthStatus === 'authenticated' && (mockAuthUser.role === 'admin' || mockAuthUser.role === 'operator');
export const canManage = mockAuthStatus === 'authenticated' && mockAuthUser.role === 'admin';

// ── Re-export mock data ───────────────────────────
export { adminAuditRows, adminDeletedRows, adminKnowledgeRows, adminModelUsageRows, adminOperationAuditRows, adminOverviewMetrics, adminResourceCards, adminSqlAuditRows, adminToolCallRows, adminToolRows, adminTraceRows, adminUserRows, adminUserSessionRows };

// ── Type aliases ──────────────────────────────────
export type AuditRow = (typeof adminAuditRows)[number];
export type ToolCallRow = (typeof adminToolCallRows)[number];
export type SqlAuditRow = (typeof adminSqlAuditRows)[number];
export type TraceRow = (typeof adminTraceRows)[number];
export type UserRow = (typeof adminUserRows)[number];
export type UserSessionRow = (typeof adminUserSessionRows)[number];
export type ToolRow = (typeof adminToolRows)[number];
export type ModelUsageRow = (typeof adminModelUsageRows)[number];
export type KnowledgeRow = (typeof adminKnowledgeRows)[number];
export type DeletedRow = (typeof adminDeletedRows)[number];
export type OperationAuditRow = (typeof adminOperationAuditRows)[number];

// ── Nav items ─────────────────────────────────────
export const adminNavItems: Array<{ key: string; path: string; label: string; icon: ReactNode; adminOnly?: boolean }> = [
  { key: 'overview', path: '/admin', label: '概览', icon: <IconDashboard /> },
  { key: 'users', path: '/admin/users', label: '用户管理', icon: <IconUserGroup />, adminOnly: true },
  { key: 'runs', path: '/admin/runs', label: 'Agent 运行', icon: <IconThunderbolt /> },
  { key: 'tools', path: '/admin/tools', label: '工具调用', icon: <IconTool /> },
  { key: 'usage', path: '/admin/usage', label: '模型用量', icon: <IconStorage /> },
  { key: 'knowledge', path: '/admin/knowledge', label: '知识库', icon: <IconBook /> },
  { key: 'deleted', path: '/admin/deleted', label: '软删除资源', icon: <IconHistory />, adminOnly: true }
];

// ── Section metadata ──────────────────────────────
export const sectionMeta: Record<string, { title: string; description: string; tag: string }> = {
  overview: { title: '系统概览', description: '运行、用户、工具、模型和知识库索引状态。当前为 mock 管理视图。', tag: 'Overview' },
  users: { title: '用户管理', description: '查询用户详情、登录会话、角色和状态；状态变更与会话重置仅 admin 可执行。', tag: 'RBAC' },
  runs: { title: 'Agent 运行', description: '查看 AgentRun、ToolCall、SQLAudit 和 Trace，定位失败任务和异常链路。', tag: 'AgentRun' },
  tools: { title: '工具调用', description: '治理工具注册表、版本、权限范围、风险等级和启停状态。', tag: 'Tools' },
  usage: { title: '模型用量', description: '查看供应商、模型、场景、token、成本和耗时。', tag: 'Model Usage' },
  knowledge: { title: '知识库', description: '管理知识库文档、解析状态、索引进度和下线恢复。', tag: 'Knowledge' },
  deleted: { title: '软删除资源', description: '查看已删除业务资源，并由 admin 执行恢复操作。', tag: 'Recovery' }
};

// ── Tag helpers ────────────────────────────────────
export function statusTag(status: string) {
  const color = status === 'active' || status === 'success' || status === 'completed' || status === 'indexed' ? 'green' : status === 'failed' || status === 'disabled' ? 'red' : 'orange';
  return <Tag color={color}>{status}</Tag>;
}

export function roleTag(role: string) {
  return <Tag color={role === 'admin' ? 'arcoblue' : role === 'operator' ? 'orange' : 'gray'}>{role}</Tag>;
}

export function riskTag(risk: string) {
  return <Tag color={risk === 'high' ? 'red' : risk === 'medium' ? 'orange' : 'green'}>{risk}</Tag>;
}

// ── Table columns ──────────────────────────────────
export const auditColumns: TableColumnProps<AuditRow>[] = [
  { title: 'Run', dataIndex: 'runId' },
  { title: '用户', dataIndex: 'user' },
  { title: '意图', dataIndex: 'intent' },
  { title: '状态', dataIndex: 'status', render: statusTag },
  { title: '耗时 ms', dataIndex: 'durationMs' },
  { title: 'Trace', dataIndex: 'traceId' }
];

export const toolCallColumns: TableColumnProps<ToolCallRow>[] = [
  { title: 'Call ID', dataIndex: 'callId' },
  { title: 'Run', dataIndex: 'runId' },
  { title: '工具', dataIndex: 'toolName' },
  { title: '状态', dataIndex: 'status', render: statusTag },
  { title: '耗时 ms', dataIndex: 'latencyMs' },
  { title: 'Trace', dataIndex: 'traceId' }
];

export const sqlAuditColumns: TableColumnProps<SqlAuditRow>[] = [
  { title: 'Audit ID', dataIndex: 'auditId' },
  { title: '执行方', dataIndex: 'actor' },
  { title: '语句摘要', dataIndex: 'statement' },
  { title: '风险', dataIndex: 'risk', render: riskTag },
  { title: '结果', dataIndex: 'result' },
  { title: 'Trace', dataIndex: 'traceId' }
];

export const traceColumns: TableColumnProps<TraceRow>[] = [
  { title: 'Trace', dataIndex: 'traceId' },
  { title: '链路', dataIndex: 'entry' },
  { title: '状态', dataIndex: 'status', render: statusTag },
  { title: '开始时间', dataIndex: 'startedAt' }
];

export const modelUsageColumns: TableColumnProps<ModelUsageRow>[] = [
  { title: '供应商', dataIndex: 'provider' },
  { title: '模型', dataIndex: 'model' },
  { title: '场景', dataIndex: 'scene' },
  { title: 'Tokens', dataIndex: 'tokens' },
  { title: '成本', dataIndex: 'cost' },
  { title: '耗时 ms', dataIndex: 'latencyMs' },
  { title: '状态', dataIndex: 'status', render: statusTag }
];

export const operationAuditColumns: TableColumnProps<OperationAuditRow>[] = [
  { title: 'operator_id', dataIndex: 'operator_id' },
  { title: '操作人', dataIndex: 'operator' },
  { title: '动作', dataIndex: 'action' },
  { title: '目标', render: (_, record) => `${record.target_type}:${record.target_id}` },
  { title: '结果', dataIndex: 'result', render: statusTag },
  { title: 'request_id', dataIndex: 'request_id' },
  { title: 'trace_id', dataIndex: 'trace_id' }
];

export const sessionColumns: TableColumnProps<UserSessionRow>[] = [
  { title: '设备', dataIndex: 'device' },
  { title: 'IP', dataIndex: 'ip' },
  { title: '过期时间', dataIndex: 'expiresAt' },
  { title: '状态', dataIndex: 'status', render: statusTag }
];

// ── Utility ────────────────────────────────────────
export function getSectionKey(pathname: string): string {
  if (pathname.endsWith('/users')) return 'users';
  if (pathname.endsWith('/runs')) return 'runs';
  if (pathname.endsWith('/tools')) return 'tools';
  if (pathname.endsWith('/usage')) return 'usage';
  if (pathname.endsWith('/knowledge')) return 'knowledge';
  if (pathname.endsWith('/deleted')) return 'deleted';
  return 'overview';
}

// ── Shared UI components ───────────────────────────
export function AdminHeader({ sectionKey }: { sectionKey: string }) {
  const meta = sectionMeta[sectionKey];
  return (
    <section className={styles.header}>
      <div>
        <h1>{meta.title}</h1>
        <p>{meta.description}</p>
      </div>
      <Tag color="arcoblue">{meta.tag}</Tag>
    </section>
  );
}

export function AdminFilters({ placeholder = 'trace_id / user_id' }: { placeholder?: string }) {
  return (
    <section className={styles.filters}>
      <strong>筛选</strong>
      <Select className={styles.filterControl} size="small" defaultValue="all" triggerProps={{ autoAlignPopupWidth: false }}>
        <Option value="all">全部状态</Option>
        <Option value="active">active</Option>
        <Option value="completed">completed</Option>
        <Option value="failed">failed</Option>
      </Select>
      <Select className={styles.filterControl} size="small" defaultValue="24h" triggerProps={{ autoAlignPopupWidth: false }}>
        <Option value="24h">近 24h</Option>
        <Option value="7d">近 7 天</Option>
        <Option value="30d">近 30 天</Option>
      </Select>
      <Input className={styles.filterInput} size="small" placeholder={placeholder} allowClear />
      <Button type="primary" onClick={() => Message.info('筛选为 mock 操作')}>查询</Button>
    </section>
  );
}

export function AdminOnlyNotice({ title }: { title: string }) {
  return (
    <Card className={styles.noAccessCard} bordered={false}>
      <Tag color="red">ADMIN_ONLY</Tag>
      <h1>{title}</h1>
      <p>该页面包含用户敏感信息或恢复类高风险能力，按后端接口契约仅 admin 可访问。</p>
      <Link to="/admin">
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
    <Card className={styles.wideCard} bordered={false}>
      <div className={styles.cardHead}>
        <strong>管理操作审计</strong>
        <Tag color="arcoblue">operator_id / target_type / request_id / trace_id</Tag>
      </div>
      <Table columns={operationAuditColumns} data={adminOperationAuditRows} pagination={{ pageSize: 4, total: adminOperationAuditRows.length }} size="small" />
    </Card>
  );
}

export function AdminActionsCard({ onAction }: { onAction: (payload: AdminActionPayload) => void }) {
  return (
    <Card className={styles.card} bordered={false}>
      <strong>管理操作</strong>
      <div className={styles.actionGrid}>
        <Button disabled={!canManage} onClick={() => onAction({ action: '禁用用户', targetLabel: 'user_10002', targetType: 'user', targetId: 'user_10002', onApply: () => { const target = adminUserRows.find((item) => item.userId === 'user_10002'); if (target) target.status = 'disabled'; } })}>禁用用户</Button>
        <Button disabled={!canManage} onClick={() => onAction({ action: '重置会话', targetLabel: 'user_10002', targetType: 'user_session', targetId: 'user_10002', onApply: () => { adminUserSessionRows.filter((s) => s.userId === 'user_10002').forEach((s) => { s.status = 'revoked'; }); } })}>重置会话</Button>
        <Button disabled={!canManage} onClick={() => onAction({ action: '工具启停', targetLabel: 'food_log_writer', targetType: 'tool', targetId: 'food_log_writer', onApply: () => { const tool = adminToolRows.find((t) => t.name === 'food_log_writer'); if (tool) tool.status = tool.status === 'active' ? 'disabled' : 'active'; } })}>工具启停</Button>
        <Button disabled={!canManage} onClick={() => onAction({ action: '恢复资源', targetLabel: 'meal_plan_73', targetType: 'meal_plan', targetId: 'meal_plan_73', onApply: () => { const rowIndex = adminDeletedRows.findIndex((r) => r.resourceId === 'meal_plan_73'); if (rowIndex >= 0) adminDeletedRows.splice(rowIndex, 1); } })}>恢复资源</Button>
      </div>
      <p>高风险操作必须二次确认并写审计。operator 只能查看，不可执行。</p>
    </Card>
  );
}

export function GovernanceResourceCard() {
  return (
    <Card className={styles.card} bordered={false}>
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
