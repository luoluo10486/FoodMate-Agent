import {
  adminAuditRows,
  adminDeletedRows,
  adminKnowledgeRows,
  adminModelUsageRows,
  adminOperationAuditRows,
  adminOverviewMetrics,
  adminOverviewRows,
  adminResourceCards,
  adminSqlAuditRows,
  adminToolRegistryRows,
  adminToolCallRows,
  adminToolRows,
  adminTraceRows,
  adminUserRows,
  adminUserBusinessSessionRows,
  adminUserOperationHistoryRows,
  adminUserSessionRows,
} from '../mock/admin';
import { apiRequest } from './apiClient';

export type AdminDashboard = {
  overview_metrics: AdminMetricRow[];
  runs: AdminRunRow[];
  tool_calls: AdminToolCallRow[];
  sql_audits: AdminSqlAuditRow[];
  traces: AdminTraceRow[];
  tools: AdminToolRow[];
  usage: AdminUsageRow[];
  knowledge: AdminKnowledgeRow[];
  deleted: AdminDeletedRow[];
  operation_audits: AdminOperationAuditRow[];
};

type AdminDashboardResponse = {
  overview_metrics: AdminMetricResponse[];
  runs: AdminRunResponse[];
  tool_calls: AdminToolCallResponse[];
  sql_audits: AdminSqlAuditResponse[];
  traces?: AdminTraceResponse[];
  tools: AdminToolResponse[];
  usage: AdminUsageResponse[];
  knowledge: AdminKnowledgeResponse[];
  deleted: AdminDeletedResponse[];
  operation_audits: AdminOperationAuditResponse[];
};

type AdminMetricResponse = { label: string; value: string; hint: string; tone: string };
type AdminRunResponse = {
  agent_run_id: number | null;
  session_id: number | null;
  intent: string;
  status: string;
  trace_id: string;
  duration_ms: number | string | null;
  username: string;
  result_type?: string;
  error_code?: string;
  stage?: string;
  model?: string;
  created_at?: string;
};
type AdminToolCallResponse = {
  tool_call_id: number | null;
  agent_run_id: number | null;
  tool_name: string;
  status: string;
  latency_ms: number | null;
  trace_id: string;
  request_id?: string;
  input_summary?: string;
  output_summary?: string;
  error_code?: string;
  started_at?: string;
  completed_at?: string;
};
type AdminSqlAuditResponse = {
  sql_audit_id: number | null;
  actor: number | null;
  statement: string;
  result: string;
  trace_id: string;
  risk?: string;
  duration_ms?: number | null;
  row_count?: number | null;
  policy?: string;
  query_hash?: string;
  error_code?: string;
  created_at?: string;
};
type AdminTraceResponse = {
  trace_id: string;
  run_id?: number | string | null;
  entry?: string;
  status: string;
  started_at?: string;
  duration_ms?: number | null;
  span_count?: number | null;
  root_service?: string;
  error_code?: string;
};
type AdminToolResponse = {
  name: string;
  version: string;
  risk: string;
  status: string;
  scope: string;
  owner: string;
  last_called_at: string;
};
type AdminUsageResponse = {
  provider: string;
  model: string;
  scene: string;
  tokens: string;
  cost: number | string | null;
  latency_ms: number | null;
  status: string;
};
type AdminKnowledgeResponse = {
  document_id: number | null;
  title: string;
  status: string;
  chunks: number | null;
  owner: string;
  source: string;
  index_progress: string;
  updated_at: string | null;
};
type AdminDeletedResponse = {
  resource_type: string;
  resource_id: number | null;
  summary?: string;
  owner: string;
  deleted_by?: string;
  deleted_at: string | null;
  restorable?: boolean;
  reason: string;
};
type AdminOperationAuditResponse = {
  operator_id: number | null;
  action: string;
  target_type: string;
  target_id: string;
  result: string;
  request_id: string;
  trace_id: string;
  created_at: string | null;
  request_summary?: string;
  before_state?: string;
  after_state?: string;
  error_code?: string;
  client_info?: string;
};

export type AdminMetricRow = AdminMetricResponse;
export type AdminRunRow = {
  key: string;
  runId: string;
  userId?: string;
  user: string;
  intent: string;
  status: string;
  durationMs: number;
  toolCalls?: number;
  traceId: string;
  sessionId?: string;
  resultType?: string;
  errorCode?: string;
  stage?: string;
  model?: string;
  createdAt?: string;
};
export type AdminToolCallRow = {
  key: string;
  callId: string;
  runId: string;
  toolName: string;
  status: string;
  latencyMs: number;
  traceId: string;
  requestId?: string;
  inputSummary?: string;
  outputSummary?: string;
  errorCode?: string;
  startedAt?: string;
  completedAt?: string;
};
export type AdminSqlAuditRow = {
  key: string;
  auditId: string;
  actor: string;
  statement: string;
  risk: string;
  result: string;
  traceId: string;
  durationMs?: number;
  rowCount?: number;
  policy?: string;
  queryHash?: string;
  errorCode?: string;
  createdAt?: string;
};
export type AdminTraceRow = {
  key: string;
  traceId: string;
  runId?: string;
  entry: string;
  status: string;
  startedAt: string;
  durationMs?: number;
  spanCount?: number;
  rootService?: string;
  errorCode?: string;
};
export type AdminToolRow = {
  key: string;
  name: string;
  version: string;
  risk: string;
  status: string;
  scope: string;
  owner: string;
  schema: string;
  lastCalledAt: string;
  timeoutMs?: string;
  retryPolicy?: string;
  failedRate?: string;
};
export type AdminToolRegistryRow = AdminToolRow & {
  timeoutMs: string;
  retryPolicy: string;
  failedRate: string;
};
export type AdminUsageRow = {
  key: string;
  provider: string;
  model: string;
  scene: string;
  tokens: string;
  cost: string;
  latencyMs: number;
  status: string;
};
export type AdminKnowledgeRow = {
  key: string;
  documentId: string;
  title: string;
  status: string;
  chunks: number;
  owner: string;
  source: string;
  indexProgress: string;
  updatedAt: string;
};
export type AdminDeletedRow = {
  key: string;
  resourceType: string;
  resourceId: string;
  summary: string;
  owner: string;
  deletedBy: string;
  deletedAt: string;
  restorable: boolean;
  reason: string;
};
export type AdminOperationAuditRow = {
  key: string;
  operator_id: string;
  operator: string;
  action: string;
  target_type: string;
  target_id: string;
  result: string;
  request_id: string;
  trace_id: string;
  createdAt: string;
  requestSummary: string;
  beforeState: string;
  afterState: string;
  errorCode: string;
  clientInfo: string;
};

const text = (value: string | number | null | undefined) => (value == null ? '-' : String(value));
const numeric = (value: number | string | null | undefined) => (value == null ? 0 : Number(value));

function normalizeDashboard(data: AdminDashboardResponse): AdminDashboard {
  return {
    overview_metrics: data.overview_metrics,
    runs: data.runs.map((row, index) => ({
      key: `run-${row.agent_run_id ?? index}`,
      runId: text(row.agent_run_id),
      userId: row.session_id == null ? undefined : String(row.session_id),
      user: row.username || '-',
      intent: row.intent || '-',
      status: row.status || '-',
      durationMs: numeric(row.duration_ms),
      traceId: row.trace_id || '-',
      toolCalls: 0,
      sessionId: row.session_id == null ? undefined : String(row.session_id),
      resultType: row.result_type || '-',
      errorCode: row.error_code || '-',
      stage: row.stage || '-',
      model: row.model || '-',
      createdAt: row.created_at || '-',
    })),
    tool_calls: data.tool_calls.map((row, index) => ({
      key: `call-${row.tool_call_id ?? index}`,
      callId: text(row.tool_call_id),
      runId: text(row.agent_run_id),
      toolName: row.tool_name,
      status: row.status,
      latencyMs: row.latency_ms ?? 0,
      traceId: row.trace_id || '-',
      requestId: row.request_id || '-',
      inputSummary: row.input_summary || '-',
      outputSummary: row.output_summary || '-',
      errorCode: row.error_code || '-',
      startedAt: row.started_at || '-',
      completedAt: row.completed_at || '-',
    })),
    sql_audits: data.sql_audits.map((row, index) => ({
      key: `sql-${row.sql_audit_id ?? index}`,
      auditId: text(row.sql_audit_id),
      actor: text(row.actor),
      statement: row.statement,
      risk: row.risk || 'low',
      result: row.result,
      traceId: row.trace_id || '-',
      durationMs: row.duration_ms ?? 0,
      rowCount: row.row_count ?? 0,
      policy: row.policy || '-',
      queryHash: row.query_hash || '-',
      errorCode: row.error_code || '-',
      createdAt: row.created_at || '-',
    })),
    traces: (data.traces ?? []).map((row, index) => ({
      key: `trace-${row.trace_id || index}`,
      traceId: row.trace_id || '-',
      runId: row.run_id == null ? undefined : String(row.run_id),
      entry: row.entry || '-',
      status: row.status || '-',
      startedAt: row.started_at || '-',
      durationMs: row.duration_ms ?? 0,
      spanCount: row.span_count ?? 0,
      rootService: row.root_service || '-',
      errorCode: row.error_code || '-',
    })),
    tools: data.tools.map((row, index) => ({
      key: `tool-${row.name || index}`,
      name: row.name,
      version: row.version,
      risk: row.risk,
      status: row.status,
      scope: row.scope,
      owner: row.owner,
      schema: '-',
      lastCalledAt: row.last_called_at || '-',
    })),
    usage: data.usage.map((row, index) => ({
      key: `usage-${row.provider}-${row.model}-${index}`,
      provider: row.provider,
      model: row.model,
      scene: row.scene,
      tokens: row.tokens,
      cost: text(row.cost),
      latencyMs: row.latency_ms ?? 0,
      status: row.status,
    })),
    knowledge: data.knowledge.map((row, index) => ({
      key: `knowledge-${row.document_id ?? index}`,
      documentId: text(row.document_id),
      title: row.title,
      status: row.status,
      chunks: row.chunks ?? 0,
      owner: row.owner,
      source: row.source,
      indexProgress: row.index_progress,
      updatedAt: text(row.updated_at),
    })),
    deleted: data.deleted.map((row, index) => ({
      key: `deleted-${row.resource_id ?? index}`,
      resourceType: row.resource_type,
      resourceId: text(row.resource_id),
      summary: row.summary || row.reason || '-',
      owner: row.owner,
      deletedBy: row.deleted_by || 'system_cleanup',
      deletedAt: text(row.deleted_at),
      restorable: row.restorable ?? true,
      reason: row.reason,
    })),
    operation_audits: data.operation_audits.map((row, index) => ({
      key: `operation-${row.request_id || index}`,
      operator_id: text(row.operator_id),
      operator: text(row.operator_id),
      action: row.action,
      target_type: row.target_type,
      target_id: row.target_id,
      result: row.result,
      request_id: row.request_id,
      trace_id: row.trace_id,
      createdAt: text(row.created_at),
      requestSummary: row.request_summary || '-',
      beforeState: row.before_state || '-',
      afterState: row.after_state || '-',
      errorCode: row.error_code || '-',
      clientInfo: row.client_info || '-',
    })),
  };
}

export async function loadAdminDashboard(): Promise<AdminDashboard> {
  if (import.meta.env.VITE_AGENT_MODE !== 'real') throw new Error('Real admin API is disabled');
  return normalizeDashboard(await apiRequest<AdminDashboardResponse>('/api/admin/dashboard'));
}

export type AdminUserRow = {
  key: string;
  userId: string;
  username: string;
  displayName: string;
  role: string;
  status: string;
  email: string;
  phone: string;
  gender: string;
  heightCm: number;
  weightKg: number;
  activityLevel: string;
  dietGoal: string;
  calorieTarget: number;
  proteinTarget: number;
  allergens: string;
  dislikes: string;
  preferredUnits: string;
  loginFailedCount: number;
  lockedUntil: string;
  lastLoginAt: string;
  createdAt: string;
};

type AdminUserResponse = {
  user_id: number;
  username: string;
  nickname?: string;
  email: string;
  role: string;
  status: string;
  phone?: string;
  gender?: string;
  height_cm?: number;
  weight_kg?: number;
  activity_level?: string;
  diet_goal?: string;
  calorie_target?: number;
  protein_target?: number;
  allergens?: string;
  dislikes?: string;
  preferred_units?: string;
  login_failed_count?: number;
  locked_until?: string;
  last_login_at?: string;
  created_at?: string;
};

export async function loadAdminUsers(): Promise<AdminUserRow[]> {
  if (import.meta.env.VITE_AGENT_MODE !== 'real') return adminUserRows;
  const data = await apiRequest<AdminUserResponse[]>('/api/admin/users');
  return data.map((user) => ({
    key: `user-${user.user_id}`,
    userId: String(user.user_id),
    username: user.username,
    displayName: user.nickname ?? user.username,
    role: user.role,
    status: user.status,
    email: user.email,
    phone: user.phone ?? '-',
    gender: user.gender ?? '-',
    heightCm: user.height_cm ?? 0,
    weightKg: user.weight_kg ?? 0,
    activityLevel: user.activity_level ?? '-',
    dietGoal: user.diet_goal ?? '-',
    calorieTarget: user.calorie_target ?? 0,
    proteinTarget: user.protein_target ?? 0,
    allergens: user.allergens ?? '-',
    dislikes: user.dislikes ?? '-',
    preferredUnits: user.preferred_units ?? '-',
    loginFailedCount: user.login_failed_count ?? 0,
    lockedUntil: user.locked_until ?? '-',
    lastLoginAt: user.last_login_at ?? '-',
    createdAt: user.created_at ?? '-',
  }));
}

async function adminWrite<T>(path: string, method: string, payload?: object): Promise<T> {
  return apiRequest<T>(path, {
    method,
    body: payload === undefined ? undefined : JSON.stringify(payload),
  });
}

export const updateAdminUserStatus = (id: string, status: string) =>
  adminWrite(`/api/admin/users/${encodeURIComponent(id)}/status`, 'PATCH', { status });
export const revokeAdminUserSessions = (id: string) =>
  adminWrite(`/api/admin/users/${encodeURIComponent(id)}/sessions/revoke-all`, 'POST');
export const updateAdminToolStatus = (name: string, status: string) =>
  adminWrite(`/api/admin/tools/${encodeURIComponent(name)}/status`, 'PATCH', { status });
export const updateKnowledgeStatus = (id: string, status: string) =>
  adminWrite(`/api/admin/knowledge/${encodeURIComponent(id)}/status`, 'PATCH', { status });
export const restoreAdminResource = (type: string, id: string) =>
  adminWrite(`/api/admin/resources/${encodeURIComponent(type)}/${encodeURIComponent(id)}/restore`, 'POST');

export async function uploadKnowledgeDocument(file: File) {
  const baseUrl = (import.meta.env.VITE_API_BASE_URL as string | undefined) ?? '';
  const csrf = document.cookie
    .split('; ')
    .find((value) => value.startsWith('foodmate_csrf='))
    ?.split('=')[1];
  const form = new FormData();
  form.append('file', file);
  const response = await fetch(`${baseUrl}/api/admin/knowledge`, {
    method: 'POST',
    credentials: 'include',
    headers: csrf ? { 'X-CSRF-Token': csrf } : {},
    body: form,
  });
  const body = (await response.json()) as {
    success: boolean;
    data: { document_id: number };
    error?: { message?: string };
  };
  if (!response.ok || !body.success) throw new Error(body.error?.message ?? 'Knowledge document upload failed');
  return body.data;
}

export type KnowledgeUploadBatch = {
  sourceType: string;
  sourceName: string;
  sourceVersion: string;
  licenseNotice: string;
  idempotencyKey: string;
  files: File[];
};

export type KnowledgeBatchDetail = {
  batch: {
    job: { job_id: string; status: string; total_items: number; indexed_items: number; failed_items: number };
    items: Array<{ item_id: string; document_id: string; filename: string; upload_status: string; index_status: string; attempts: number; error_code?: string }>;
  };
};

export async function uploadKnowledgeBatch(batch: KnowledgeUploadBatch): Promise<{ batch_id: string }> {
  const baseUrl = (import.meta.env.VITE_API_BASE_URL as string | undefined) ?? '';
  const csrf = document.cookie.split('; ').find((value) => value.startsWith('foodmate_csrf='))?.split('=')[1];
  const form = new FormData();
  batch.files.forEach((file) => form.append('files', file));
  form.append('source_type', batch.sourceType);
  form.append('source_name', batch.sourceName);
  form.append('source_version', batch.sourceVersion);
  form.append('license_notice', batch.licenseNotice);
  form.append('idempotency_key', batch.idempotencyKey);
  const response = await fetch(`${baseUrl}/api/admin/knowledge-documents/upload-batches`, {
    method: 'POST', credentials: 'include', headers: csrf ? { 'X-CSRF-Token': csrf } : {}, body: form,
  });
  const body = (await response.json()) as { success: boolean; data?: { batch_id: string }; error?: { message?: string } };
  if (!response.ok || !body.success || !body.data) throw new Error(body.error?.message ?? '知识库批次上传失败');
  return body.data;
}

export const loadKnowledgeBatch = (batchId: string) =>
  apiRequest<KnowledgeBatchDetail>(`/api/admin/knowledge-upload-batches/${encodeURIComponent(batchId)}`);
export const retryKnowledgeItem = (batchId: string, itemId: string) =>
  adminWrite(`/api/admin/knowledge-upload-batches/${encodeURIComponent(batchId)}/documents/${encodeURIComponent(itemId)}/retry`, 'POST');
export const changeKnowledgeVisibility = (documentId: string, visibility: 'published' | 'disabled' | 'draft' | 'deleted') =>
  adminWrite(`/api/admin/knowledge-documents/${encodeURIComponent(documentId)}/${visibility === 'draft' ? 'restore' : visibility}`, 'POST');

export {
  adminAuditRows,
  adminDeletedRows,
  adminKnowledgeRows,
  adminModelUsageRows,
  adminOperationAuditRows,
  adminOverviewMetrics,
  adminOverviewRows,
  adminResourceCards,
  adminSqlAuditRows,
  adminToolRegistryRows,
  adminToolCallRows,
  adminToolRows,
  adminTraceRows,
  adminUserRows,
  adminUserBusinessSessionRows,
  adminUserOperationHistoryRows,
  adminUserSessionRows,
};
