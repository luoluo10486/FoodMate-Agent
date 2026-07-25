import type { ReactNode } from 'react';
import { Tag } from '@arco-design/web-react';
import type { TableColumnProps } from '@arco-design/web-react';
import {
  IconBook,
  IconDashboard,
  IconHistory,
  IconStorage,
  IconThunderbolt,
  IconTool,
  IconUserGroup,
} from '@arco-design/web-react/icon';
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
  adminUserSessionRows,
} from '../../../services/adminService';
import { getAuthStatus, getAuthUser } from '../../../services/authService';

// ── Auth ──────────────────────────────────────────
const authStatus = getAuthStatus();
const authUser = getAuthUser();
export const canAccessAdmin =
  authStatus === 'authenticated' && (authUser.role === 'admin' || authUser.role === 'operator' || authUser.role === 'superadmin');
export const canManage = authStatus === 'authenticated' && (authUser.role === 'admin' || authUser.role === 'superadmin');

// ── Re-export mock data ───────────────────────────
export {
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
  adminUserSessionRows,
};

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
export const adminNavItems: Array<{ key: string; path: string; label: string; icon: ReactNode; adminOnly?: boolean }> =
  [
    { key: 'overview', path: '/admin', label: '概览', icon: <IconDashboard /> },
    { key: 'users', path: '/admin/users', label: '用户管理', icon: <IconUserGroup />, adminOnly: true },
    { key: 'runs', path: '/admin/runs', label: 'Agent 运行', icon: <IconThunderbolt /> },
    { key: 'tools', path: '/admin/tools', label: '工具调用', icon: <IconTool /> },
    { key: 'usage', path: '/admin/usage', label: '模型用量', icon: <IconStorage /> },
    { key: 'knowledge', path: '/admin/knowledge', label: '知识库', icon: <IconBook /> },
    { key: 'deleted', path: '/admin/deleted', label: '软删除资源', icon: <IconHistory />, adminOnly: true },
  ];

// ── Section metadata ──────────────────────────────
export const sectionMeta: Record<string, { title: string; description: string; tag: string }> = {
  overview: {
    title: '系统概览',
    description: '运行、用户、工具、模型和知识库索引状态。当前为 mock 管理视图。',
    tag: 'Overview',
  },
  users: {
    title: '用户管理',
    description: '查询用户详情、登录会话、角色和状态；状态变更与会话重置仅 admin 可执行。',
    tag: 'RBAC',
  },
  runs: {
    title: 'Agent 运行',
    description: '查看 AgentRun、ToolCall、SQLAudit 和 Trace，定位失败任务和异常链路。',
    tag: 'AgentRun',
  },
  tools: { title: '工具调用', description: '治理工具注册表、版本、权限范围、风险等级和启停状态。', tag: 'Tools' },
  usage: { title: '模型用量', description: '查看供应商、模型、场景、token、成本和耗时。', tag: 'Model Usage' },
  knowledge: { title: '知识库', description: '管理知识库文档、解析状态、索引进度和下线恢复。', tag: 'Knowledge' },
  deleted: { title: '软删除资源', description: '查看已删除业务资源，并由 admin 执行恢复操作。', tag: 'Recovery' },
};

// ── Tag helpers ────────────────────────────────────
export function statusTag(status: string) {
  const color =
    status === 'active' || status === 'success' || status === 'completed' || status === 'indexed'
      ? 'green'
      : status === 'failed' || status === 'disabled'
        ? 'red'
        : 'orange';
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
  { title: 'Trace', dataIndex: 'traceId' },
];

export const toolCallColumns: TableColumnProps<ToolCallRow>[] = [
  { title: 'Call ID', dataIndex: 'callId' },
  { title: 'Run', dataIndex: 'runId' },
  { title: '工具', dataIndex: 'toolName' },
  { title: '状态', dataIndex: 'status', render: statusTag },
  { title: '耗时 ms', dataIndex: 'latencyMs' },
  { title: 'Trace', dataIndex: 'traceId' },
];

export const sqlAuditColumns: TableColumnProps<SqlAuditRow>[] = [
  { title: 'Audit ID', dataIndex: 'auditId' },
  { title: '执行方', dataIndex: 'actor' },
  { title: '语句摘要', dataIndex: 'statement' },
  { title: '风险', dataIndex: 'risk', render: riskTag },
  { title: '结果', dataIndex: 'result' },
  { title: 'Trace', dataIndex: 'traceId' },
];

export const traceColumns: TableColumnProps<TraceRow>[] = [
  { title: 'Trace', dataIndex: 'traceId' },
  { title: '链路', dataIndex: 'entry' },
  { title: '状态', dataIndex: 'status', render: statusTag },
  { title: '开始时间', dataIndex: 'startedAt' },
];

export const modelUsageColumns: TableColumnProps<ModelUsageRow>[] = [
  { title: '供应商', dataIndex: 'provider' },
  { title: '模型', dataIndex: 'model' },
  { title: '场景', dataIndex: 'scene' },
  { title: 'Tokens', dataIndex: 'tokens' },
  { title: '成本', dataIndex: 'cost' },
  { title: '耗时 ms', dataIndex: 'latencyMs' },
  { title: '状态', dataIndex: 'status', render: statusTag },
];

export const operationAuditColumns: TableColumnProps<OperationAuditRow>[] = [
  { title: 'operator_id', dataIndex: 'operator_id' },
  { title: '操作人', dataIndex: 'operator' },
  { title: '动作', dataIndex: 'action' },
  { title: '目标', render: (_, record) => `${record.target_type}:${record.target_id}` },
  { title: '结果', dataIndex: 'result', render: statusTag },
  { title: 'request_id', dataIndex: 'request_id' },
  { title: 'trace_id', dataIndex: 'trace_id' },
];

export const sessionColumns: TableColumnProps<UserSessionRow>[] = [
  { title: '设备', dataIndex: 'device' },
  { title: 'IP', dataIndex: 'ip' },
  { title: '过期时间', dataIndex: 'expiresAt' },
  { title: '状态', dataIndex: 'status', render: statusTag },
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
