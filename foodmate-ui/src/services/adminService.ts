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
import type { AgentStreamConnection, AgentStreamHandle } from '../types/agent';
import { apiRequest } from './apiClient';
import { openSseStream } from './sseStream';

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
  degraded?: boolean;
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
  revision?: number;
};
type AdminToolRegistryResponse = {
  tool_id: number;
  name: string;
  display_name: string;
  description: string;
  category: string;
  risk_level: string;
  availability_scope: string;
  status: string;
  current_version: string;
  version: string;
  input_schema: unknown;
  output_schema: unknown;
  permissions: unknown;
  timeout_ms: number;
  retryable: boolean;
  idempotent: boolean;
  published_at: string | null;
  revision: number;
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
export type AdminQueryUsage = {
  provider: string;
  model: string;
  scene: string;
  tokens: string;
  cost: number | string | null;
  latency_ms: number | string | null;
  status: string | null;
};
type AdminKnowledgeResponse = {
  document_id: number | null;
  title: string;
  status: string;
  visibility?: string;
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
  revision?: number;
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
  degraded?: boolean;
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
  revision?: number;
  timeoutMs?: string;
  retryPolicy?: string;
  failedRate?: string;
  displayName?: string;
  description?: string;
  category?: string;
  currentVersion?: string;
  inputSchema?: unknown;
  outputSchema?: unknown;
  permissions?: unknown;
  retryable?: boolean;
  idempotent?: boolean;
  publishedAt?: string;
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
  visibility?: 'draft' | 'published' | 'disabled' | 'deleted' | string;
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
  revision?: number;
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

function normalizeKnowledgeRow(row: AdminKnowledgeResponse, index: number): AdminKnowledgeRow {
  return {
    key: `knowledge-${row.document_id ?? index}`,
    documentId: text(row.document_id),
    title: row.title,
    status: row.status,
    visibility: row.visibility || 'draft',
    chunks: row.chunks ?? 0,
    owner: row.owner,
    source: row.source,
    indexProgress: row.index_progress,
    updatedAt: text(row.updated_at),
  };
}

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
      degraded: row.degraded === true,
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
      revision: row.revision ?? 1,
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
    knowledge: data.knowledge.map(normalizeKnowledgeRow),
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
      revision: row.revision ?? 1,
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

function readRequestInit(signal?: AbortSignal): RequestInit {
  return signal ? { signal } : {};
}

export async function loadAdminDashboard(signal?: AbortSignal): Promise<AdminDashboard> {
  if (import.meta.env.VITE_AGENT_MODE !== 'real') throw new Error('Real admin API is disabled');
  return normalizeDashboard(await apiRequest<AdminDashboardResponse>('/api/admin/dashboard', readRequestInit(signal)));
}

function normalizeToolRegistryRow(row: AdminToolRegistryResponse): AdminToolRegistryRow {
  return {
    key: `tool-registry-${row.tool_id}`,
    name: row.name,
    version: row.version || row.current_version,
    risk: row.risk_level,
    status: row.status,
    scope: row.availability_scope,
    owner: row.category,
    schema: JSON.stringify(row.input_schema) ?? '-',
    lastCalledAt: '-',
    revision: row.revision,
    timeoutMs: String(row.timeout_ms),
    retryPolicy: row.retryable ? '可重试' : '不可重试',
    failedRate: '-',
    displayName: row.display_name,
    description: row.description,
    category: row.category,
    currentVersion: row.current_version,
    inputSchema: row.input_schema,
    outputSchema: row.output_schema,
    permissions: row.permissions,
    retryable: row.retryable,
    idempotent: row.idempotent,
    publishedAt: row.published_at ?? undefined,
  };
}

export async function loadAdminToolRegistry(signal?: AbortSignal): Promise<AdminToolRegistryRow[]> {
  if (import.meta.env.VITE_AGENT_MODE !== 'real') throw new Error('Real admin API is disabled');
  const response = await apiRequest<{ tools: AdminToolRegistryResponse[] }>(
    '/api/admin/tools/registry',
    readRequestInit(signal),
  );
  return response.tools.map(normalizeToolRegistryRow);
}

type AdminOperationalQueryResponse<T> = {
  items: T[];
  total: number;
  page: number;
  size: number;
};

export type AdminQueryRun = {
  agent_run_id: number | null;
  session_id: number | null;
  intent: string;
  status: string;
  trace_id: string;
  duration_ms: number | string | null;
  actor_ref: string;
  result_type?: string | null;
  error_code?: string | null;
  degraded?: boolean;
};

export type AdminQueryTrace = {
  trace_id: string;
  run_id: number | null;
  entry: string;
  status: string;
  started_at: string | null;
  duration_ms: number | string | null;
  span_count: number | null;
  root_service: string;
  error_code: string | null;
};

export type AdminTraceSpan = {
  span_id: string;
  span_type: string;
  name: string;
  service: string;
  status: string;
  started_at: string | null;
  finished_at: string | null;
  duration_ms: number | string | null;
  error_code: string | null;
  sequence_no: number | null;
};

export type AdminTraceDetail = {
  summary: AdminQueryTrace;
  spans: AdminTraceSpan[];
};

export type AdminQueryToolCall = {
  tool_call_id: number | null;
  agent_run_id: number | null;
  tool_name: string;
  status: string;
  latency_ms: number | null;
  trace_id: string;
};

export type AdminQuerySqlAudit = {
  sql_audit_id: number | null;
  actor: number | null;
  query_hash: string;
  result: string;
  trace_id: string;
  latency_ms: number | null;
  row_count: number | null;
  error_code: string;
  created_at: string | null;
};

export type AdminQueryDlq = {
  dlq_id: number | null;
  consumer_group: string;
  source_topic: string;
  message_id: string;
  run_id: string | null;
  dispatch_id: string | null;
  event_id: string | null;
  attempt: number | null;
  reconsume_times: number | null;
  error_code: string;
  reconciliation_state: string;
  first_seen_at: string | null;
  reconciled_at: string | null;
};

export type AdminQueryParams = {
  page?: number;
  size?: number;
  query?: string;
  status?: string;
  visibility?: string;
  role?: string;
  resourceType?: string;
  from?: string;
  action?: string;
  targetType?: string;
  resultType?: string;
  errorCode?: string;
  degraded?: boolean;
  sort?: string;
  direction?: 'asc' | 'desc';
};

export type AdminPageResult<T> = {
  items: T[];
  total: number;
  page: number;
  size: number;
};

export async function loadAdminQuery<T>(resource: string, params: AdminQueryParams = {}, signal?: AbortSignal) {
  if (import.meta.env.VITE_AGENT_MODE !== 'real') throw new Error('Real admin API is disabled');
  const search = new URLSearchParams();
  search.set('page', String(params.page ?? 1));
  search.set('size', String(params.size ?? 20));
  if (params.query) search.set('query', params.query);
  if (params.status && params.status !== 'all') search.set('status', params.status);
  if (params.visibility && params.visibility !== 'all') search.set('visibility', params.visibility);
  if (params.role && params.role !== 'all') search.set('role', params.role);
  if (params.resourceType && params.resourceType !== 'all') search.set('resource_type', params.resourceType);
  if (params.from) search.set('from', params.from);
  if (params.action && params.action !== 'all') search.set('action', params.action);
  if (params.targetType && params.targetType !== 'all') search.set('target_type', params.targetType);
  if (params.resultType && params.resultType !== 'all') search.set('result_type', params.resultType);
  if (params.errorCode) search.set('error_code', params.errorCode);
  if (params.degraded !== undefined) search.set('degraded', String(params.degraded));
  if (params.sort) search.set('sort', params.sort);
  if (params.direction) search.set('direction', params.direction);
  return apiRequest<AdminOperationalQueryResponse<T>>(
    `/api/admin/queries/${resource}?${search.toString()}`,
    readRequestInit(signal),
  );
}

/** 管理端知识库使用专用分页查询，避免把 dashboard 概览当成明细数据源。 */
export async function loadAdminKnowledge(
  params: AdminQueryParams = {},
  signal?: AbortSignal,
): Promise<AdminPageResult<AdminKnowledgeRow>> {
  const data = await loadAdminQuery<AdminKnowledgeResponse>(
    'knowledge',
    {
      size: 20,
      ...params,
    },
    signal,
  );
  return {
    items: data.items.map(normalizeKnowledgeRow),
    total: data.total,
    page: data.page,
    size: data.size,
  };
}

/** 管理端模型用量使用独立分页查询，避免把概览 Fixture 或治理聚合数据当成明细来源。 */
export async function loadAdminUsagePage(
  params: AdminQueryParams = {},
  signal?: AbortSignal,
): Promise<AdminPageResult<AdminUsageRow>> {
  const data = await loadAdminQuery<AdminQueryUsage>(
    'usage',
    {
      size: 20,
      ...params,
    },
    signal,
  );
  return {
    items: data.items.map((row, index) => ({
      key: `usage-${row.provider}-${row.model}-${index}`,
      provider: row.provider || '-',
      model: row.model || '-',
      scene: row.scene || '-',
      tokens: row.tokens || '-',
      cost: text(row.cost),
      latencyMs: numeric(row.latency_ms),
      status: row.status || '-',
    })),
    total: data.total,
    page: data.page,
    size: data.size,
  };
}

export async function loadAdminTraceDetail(traceId: string, signal?: AbortSignal): Promise<AdminTraceDetail> {
  if (import.meta.env.VITE_AGENT_MODE !== 'real') throw new Error('Real admin API is disabled');
  return apiRequest<AdminTraceDetail>(
    `/api/admin/queries/traces/${encodeURIComponent(traceId)}`,
    readRequestInit(signal),
  );
}

type AdminDeletedQueryItem = {
  resource_type: string;
  resource_id: number | null;
  owner_ref: string;
  deleted_at: string | null;
  reason: string;
  restorable?: boolean;
  revision?: number;
};

export async function loadAdminDeletedResourcesPage(
  params: AdminQueryParams = {},
  signal?: AbortSignal,
): Promise<AdminPageResult<AdminDeletedRow>> {
  if (import.meta.env.VITE_AGENT_MODE !== 'real') throw new Error('Real admin API is disabled');
  const data = await loadAdminQuery<AdminDeletedQueryItem>('deleted', params, signal);
  return {
    items: data.items.map((row, index) => ({
      key: `deleted-${row.resource_id ?? index}`,
      resourceType: row.resource_type,
      resourceId: text(row.resource_id),
      summary: row.reason || '-',
      owner: row.owner_ref || '-',
      deletedBy: '-',
      deletedAt: text(row.deleted_at),
      restorable: row.restorable ?? false,
      reason: row.reason || '-',
      revision: row.revision ?? 1,
    })),
    total: data.total,
    page: data.page,
    size: data.size,
  };
}

export async function loadAdminDeletedResources(): Promise<AdminDeletedRow[]> {
  return (await loadAdminDeletedResourcesPage({ size: 20 })).items;
}

export async function loadAdminOperationAuditsPage(
  params: AdminQueryParams = {},
  signal?: AbortSignal,
): Promise<AdminPageResult<AdminOperationAuditResponse>> {
  if (import.meta.env.VITE_AGENT_MODE !== 'real') throw new Error('Real admin API is disabled');
  const data = await loadAdminQuery<AdminOperationAuditResponse>(
    'operation-audits',
    {
      size: 20,
      ...params,
    },
    signal,
  );
  return data;
}

export async function loadAdminOperationAudits(): Promise<AdminOperationAuditResponse[]> {
  return (await loadAdminOperationAuditsPage()).items;
}

export type AdminAuditReport = {
  generated_at: string;
  stale_threshold_minutes: number;
  status: string;
  checks: Array<{
    code: string;
    status: string;
    pending_count: number;
    failed_count: number;
    oldest_at: string | null;
    reason_codes: string[];
  }>;
};

export async function loadAdminAuditReport(signal?: AbortSignal): Promise<AdminAuditReport> {
  if (import.meta.env.VITE_AGENT_MODE !== 'real') throw new Error('Real admin API is disabled');
  return apiRequest<AdminAuditReport>('/api/admin/audit-reports/current', readRequestInit(signal));
}

export type AdminDlqReplayResult = {
  replay_id: number;
  dlq_id: number;
  status: string;
  original_message_id: string;
};

export async function replayAdminDlq(dlqId: number, signal?: AbortSignal): Promise<AdminDlqReplayResult> {
  const digest = await sha256(`runtime.dlq.replay|${dlqId}||1`);
  return adminWrite<AdminDlqReplayResult>(
    `/api/admin/dlq/${encodeURIComponent(String(dlqId))}/replay`,
    'POST',
    { confirmed: true, confirmationDigest: digest },
    'admin-dlq-replay',
    signal,
  );
}

export type RetentionPurgeResult = {
  request_id: number;
  status: string;
  resource_type: string;
  resource_id: number;
  eligible_at: string;
  task_count: number;
};

export type RetentionPurgePreflight = {
  request_id: number;
  status: string;
  resource_type: string;
  resource_id: number;
  policy_found: boolean;
  hard_delete_enabled: boolean;
  resource_soft_deleted: boolean;
  retention_elapsed: boolean;
  legal_hold_clear: boolean;
  task_contract_valid: boolean;
  ready_to_execute: boolean;
  tasks: Array<{
    task_type: string;
    status: string;
    attempt_count: number;
    last_error_code: string | null;
  }>;
  blockers: string[];
};

export type RetentionHoldResult = {
  hold_id: number;
  status: string;
  resource_type: string;
  resource_id: number;
  reason_code: string;
};

export async function requestRetentionPurge(
  resourceType: string,
  resourceId: number,
  signal?: AbortSignal,
): Promise<RetentionPurgeResult> {
  const digest = await sha256(`retention.purge|${resourceType}|${resourceId}|1`);
  return adminWrite<RetentionPurgeResult>(
    '/api/admin/data-retention/purge-requests',
    'POST',
    {
      resource_type: resourceType,
      resource_id: resourceId,
      confirmed: true,
      confirmation_digest: digest,
    },
    'retention-purge',
    signal,
  );
}

export async function loadRetentionPurge(requestId: number, signal?: AbortSignal): Promise<RetentionPurgeResult> {
  return apiRequest<RetentionPurgeResult>(
    `/api/admin/data-retention/purge-requests/${requestId}`,
    readRequestInit(signal),
  );
}

export async function loadRetentionPurgePreflight(
  requestId: number,
  signal?: AbortSignal,
): Promise<RetentionPurgePreflight> {
  return apiRequest<RetentionPurgePreflight>(
    `/api/admin/data-retention/purge-requests/${requestId}/preflight`,
    readRequestInit(signal),
  );
}

export async function approveRetentionPurge(requestId: number, signal?: AbortSignal): Promise<RetentionPurgeResult> {
  const digest = await sha256(`retention.approve|${requestId}|1`);
  return adminWrite<RetentionPurgeResult>(
    `/api/admin/data-retention/purge-requests/${requestId}/approve`,
    'POST',
    { confirmed: true, confirmation_digest: digest },
    'retention-approve',
    signal,
  );
}

export async function placeRetentionHold(
  resourceType: string,
  resourceId: number,
  reasonCode: string,
  signal?: AbortSignal,
): Promise<RetentionHoldResult> {
  const digest = await sha256(`retention.hold|${resourceType}|${resourceId}|${reasonCode}|1`);
  return adminWrite<RetentionHoldResult>(
    '/api/admin/data-retention/holds',
    'POST',
    {
      resource_type: resourceType,
      resource_id: resourceId,
      reason_code: reasonCode,
      confirmed: true,
      confirmation_digest: digest,
    },
    'retention-hold',
    signal,
  );
}

export async function releaseRetentionHold(holdId: number, signal?: AbortSignal): Promise<RetentionHoldResult> {
  const digest = await sha256(`retention.release|${holdId}|1`);
  return adminWrite<RetentionHoldResult>(
    `/api/admin/data-retention/holds/${holdId}/release`,
    'POST',
    { confirmed: true, confirmation_digest: digest },
    'retention-release',
    signal,
  );
}

export type AdminExportStatus = {
  export_job_id: number;
  resource: string;
  status: string;
  expires_at: string | null;
  completed_at: string | null;
  download_consumed_at: string | null;
  failure_code: string | null;
};

export async function requestAdminExport(
  resource: string,
  filters: { query?: string; status?: string; visibility?: string; sort?: string; direction?: 'asc' | 'desc' } = {},
  fields?: string[],
  signal?: AbortSignal,
) {
  if (import.meta.env.VITE_AGENT_MODE !== 'real') throw new Error('Real admin API is disabled');
  return apiRequest<{ export_job_id: number }>('/api/admin/exports', {
    ...readRequestInit(signal),
    method: 'POST',
    headers: { 'Idempotency-Key': randomIdempotencyKey(`admin-export-${resource}`) },
    body: JSON.stringify({
      resource,
      ...filters,
      fields,
    }),
  });
}

export async function loadAdminExportStatus(jobId: number, signal?: AbortSignal) {
  if (import.meta.env.VITE_AGENT_MODE !== 'real') throw new Error('Real admin API is disabled');
  return apiRequest<AdminExportStatus>(`/api/admin/exports/${jobId}`, readRequestInit(signal));
}

export async function downloadAdminExport(jobId: number, signal?: AbortSignal) {
  if (import.meta.env.VITE_AGENT_MODE !== 'real') throw new Error('Real admin API is disabled');
  return apiRequest<{ download_url: string }>(`/api/admin/exports/${jobId}/download`, {
    ...readRequestInit(signal),
    method: 'POST',
  });
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
  revision?: number;
};

export type AdminUserDetail = {
  profile: {
    user_id: number;
    display_name?: string;
    gender?: string;
    birthday?: string;
    height_cm?: number;
    weight_kg?: number;
    activity_level?: string;
    diet_goal?: string;
    calorie_target?: number;
    protein_target?: number;
    allergens?: string;
    dislikes?: string;
    preferred_units?: string;
  } | null;
  login_sessions: Array<{
    auth_session_id: number;
    device_id?: string;
    user_agent?: string;
    ip_address?: string;
    expires_at?: string;
    last_seen_at?: string;
    created_at?: string;
    revoked_at?: string;
  }>;
  business_sessions: {
    items: Array<{
      session_id: number;
      user_id: number;
      title: string;
      mode: string;
      status: string;
      last_message_at?: string;
    }>;
    total: number;
    page: number;
    size: number;
  };
  operation_history: {
    items: Array<{
      operator_id: number | null;
      action: string;
      target_type: string;
      target_id: string;
      result: string;
      request_id: string;
      trace_id: string;
      created_at?: string;
    }>;
    total: number;
    page: number;
    size: number;
  };
};

type AdminUserResponse = {
  user_id: number;
  username: string;
  nickname?: string;
  email: string;
  role: string;
  status: string;
  revision?: number;
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

function normalizeAdminUser(user: AdminUserResponse | AdminQueryUser): AdminUserRow {
  return {
    key: `user-${user.user_id}`,
    userId: String(user.user_id),
    username: user.username,
    displayName: 'nickname' in user ? (user.nickname ?? user.username) : user.username,
    role: user.role,
    status: user.status,
    email: 'email' in user ? user.email : user.email_ref || '-',
    phone: 'phone' in user ? (user.phone ?? '-') : '-',
    gender: 'gender' in user ? (user.gender ?? '-') : '-',
    heightCm: 'height_cm' in user ? (user.height_cm ?? 0) : 0,
    weightKg: 'weight_kg' in user ? (user.weight_kg ?? 0) : 0,
    activityLevel: 'activity_level' in user ? (user.activity_level ?? '-') : '-',
    dietGoal: 'diet_goal' in user ? (user.diet_goal ?? '-') : '-',
    calorieTarget: 'calorie_target' in user ? (user.calorie_target ?? 0) : 0,
    proteinTarget: 'protein_target' in user ? (user.protein_target ?? 0) : 0,
    allergens: 'allergens' in user ? (user.allergens ?? '-') : '-',
    dislikes: 'dislikes' in user ? (user.dislikes ?? '-') : '-',
    preferredUnits: 'preferred_units' in user ? (user.preferred_units ?? '-') : '-',
    loginFailedCount: 'login_failed_count' in user ? (user.login_failed_count ?? 0) : 0,
    lockedUntil: 'locked_until' in user ? (user.locked_until ?? '-') : '-',
    lastLoginAt: 'last_login_at' in user ? (user.last_login_at ?? '-') : '-',
    createdAt: 'created_at' in user ? (user.created_at ?? '-') : '-',
    revision: user.revision ?? 1,
  };
}

type AdminQueryUser = {
  user_id: number;
  username: string;
  role: string;
  status: string;
  email_ref: string | null;
  revision?: number;
};

export async function loadAdminUsersPage(
  params: AdminQueryParams = {},
  signal?: AbortSignal,
): Promise<AdminPageResult<AdminUserRow>> {
  if (import.meta.env.VITE_AGENT_MODE !== 'real') {
    const items = adminUserRows as AdminUserRow[];
    return { items, total: items.length, page: 1, size: items.length };
  }
  const page = params.page ?? 1;
  const size = params.size ?? 20;
  const hasQueryFilter = [
    params.query,
    params.status,
    params.visibility,
    params.role,
    params.resourceType,
    params.from,
    params.action,
    params.targetType,
    params.sort,
    params.direction,
  ].some((value) => Boolean(value && value !== 'all'));
  if (page === 1 && !hasQueryFilter) {
    // 默认用户页使用专用列表接口；发生筛选或翻页时再切换到分页查询接口。
    const items = await loadAdminUsers(signal);
    return { items: items.slice(0, size), total: items.length, page: 1, size };
  }
  const data = await loadAdminQuery<AdminQueryUser>('users', { size: 20, ...params }, signal);
  return { ...data, items: data.items.map(normalizeAdminUser) };
}

export async function loadAdminUsers(signal?: AbortSignal): Promise<AdminUserRow[]> {
  if (import.meta.env.VITE_AGENT_MODE !== 'real') return adminUserRows;
  const data = await apiRequest<AdminUserResponse[]>('/api/admin/users', readRequestInit(signal));
  return data.map(normalizeAdminUser);
}

export async function loadAdminUserDetail(userId: string, signal?: AbortSignal): Promise<AdminUserDetail> {
  if (import.meta.env.VITE_AGENT_MODE !== 'real') throw new Error('Real admin API is disabled');
  return apiRequest<AdminUserDetail>(`/api/admin/users/${encodeURIComponent(userId)}/detail`, readRequestInit(signal));
}

async function adminWrite<T>(
  path: string,
  method: string,
  payload?: object,
  idempotencyPrefix?: string,
  signal?: AbortSignal,
): Promise<T> {
  return apiRequest<T>(path, {
    method,
    headers: idempotencyPrefix ? { 'Idempotency-Key': randomIdempotencyKey(idempotencyPrefix) } : undefined,
    body: payload === undefined ? undefined : JSON.stringify(payload),
    signal,
  });
}

export async function updateAdminUserStatus(id: string, status: string, revision = 1, signal?: AbortSignal) {
  const digest = await confirmationDigest('admin.user.status.update', id, status, revision);
  return adminWrite(
    `/api/admin/users/${encodeURIComponent(id)}/status`,
    'PATCH',
    { status, revision, confirmed: true, confirmationDigest: digest },
    'admin-user-status',
    signal,
  );
}

export async function revokeAdminUserSessions(id: string, revision = 1, signal?: AbortSignal) {
  const digest = await confirmationDigest('admin.user.sessions.revoke_all', id, '', revision);
  return adminWrite(
    `/api/admin/users/${encodeURIComponent(id)}/sessions/revoke-all`,
    'POST',
    { revision, confirmed: true, confirmationDigest: digest },
    'admin-user-sessions',
    signal,
  );
}

export async function resetAdminUserCredentials(id: string, revision = 1, signal?: AbortSignal) {
  const digest = await confirmationDigest('admin.user.credentials.reset', id, '', revision);
  return adminWrite(
    `/api/admin/users/${encodeURIComponent(id)}/credentials/reset`,
    'POST',
    { revision, confirmed: true, confirmationDigest: digest },
    'admin-user-credentials-reset',
    signal,
  );
}
export async function updateAdminToolStatus(name: string, status: string, revision = 1, signal?: AbortSignal) {
  const action = 'admin.tool.status.update';
  const digest = await confirmationDigest(action, name, status, revision);
  return modelGovernanceWrite<ModelGovernanceMutation>(
    `/api/admin/tools/${encodeURIComponent(name)}/status`,
    'PATCH',
    { status, revision, confirmed: true, confirmationDigest: digest },
    'admin-tool-status',
    signal,
  );
}
export const updateKnowledgeStatus = (id: string, status: string, signal?: AbortSignal) =>
  adminWrite(`/api/admin/knowledge/${encodeURIComponent(id)}/status`, 'PATCH', { status }, undefined, signal);
export async function restoreAdminResource(type: string, id: string, revision = 1, signal?: AbortSignal) {
  const action = 'admin.resource.restore';
  const digest = await confirmationDigest(action, type, id, revision);
  return modelGovernanceWrite<ModelGovernanceMutation>(
    `/api/admin/resources/${encodeURIComponent(type)}/${encodeURIComponent(id)}/restore`,
    'POST',
    { revision, confirmed: true, confirmationDigest: digest },
    'admin-resource-restore',
    signal,
  );
}

export async function uploadKnowledgeDocument(file: File, signal?: AbortSignal) {
  const form = new FormData();
  form.append('file', file);
  return apiRequest<{ document_id: number }>('/api/admin/knowledge', {
    method: 'POST',
    body: form,
    signal,
  });
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
    items: Array<{
      item_id: string;
      document_id: string;
      filename: string;
      upload_status: string;
      index_status: string;
      attempts: number;
      error_code?: string;
    }>;
  };
};

export type KnowledgeBatchEvent = {
  event_id: string;
  event_type: string;
  payload: unknown;
};

export type KnowledgeBatchStreamOptions = {
  lastEventId?: string;
  signal?: AbortSignal;
  maxAttempts?: number;
  reconnectDelayMs?: number;
  onStateChange?: (connection: AgentStreamConnection) => void;
  onError?: (connection: AgentStreamConnection) => void;
};

export async function uploadKnowledgeBatch(
  batch: KnowledgeUploadBatch,
  signal?: AbortSignal,
): Promise<{ batch_id: string }> {
  const form = new FormData();
  batch.files.forEach((file) => form.append('files', file));
  form.append('source_type', batch.sourceType);
  form.append('source_name', batch.sourceName);
  form.append('source_version', batch.sourceVersion);
  form.append('license_notice', batch.licenseNotice);
  form.append('idempotency_key', batch.idempotencyKey);
  return apiRequest<{ batch_id: string }>('/api/admin/knowledge-documents/upload-batches', {
    method: 'POST',
    body: form,
    signal,
  });
}

export const loadKnowledgeBatch = (batchId: string, signal?: AbortSignal) =>
  apiRequest<KnowledgeBatchDetail>(
    `/api/admin/knowledge-upload-batches/${encodeURIComponent(batchId)}`,
    readRequestInit(signal),
  );

const knowledgeBatchEventTypes = [
  'knowledge.index.indexed',
  'knowledge.index.index_failed',
  'knowledge.index.retry',
  'knowledge.index.reindex',
  'knowledge.batch.progress',
] as const;

function knowledgeBatchStringField(value: unknown, key: string) {
  if (!value || typeof value !== 'object') return undefined;
  const field = (value as Record<string, unknown>)[key];
  return field === undefined || field === null ? undefined : String(field);
}

function isTerminalKnowledgeBatchEvent(eventType: string, payload: unknown) {
  if (eventType !== 'knowledge.batch.progress') return false;
  const status = knowledgeBatchStringField(payload, 'status')?.toLowerCase();
  return status === 'completed' || status === 'partial_failed' || status === 'failed';
}

export function streamKnowledgeBatch(
  batchId: string,
  onEvent: (event: KnowledgeBatchEvent) => void,
  options: KnowledgeBatchStreamOptions = {},
): AgentStreamHandle {
  return openSseStream<unknown>({
    path: `/api/admin/knowledge-upload-batches/${encodeURIComponent(batchId)}/events`,
    eventTypes: knowledgeBatchEventTypes,
    lastEventId: options.lastEventId,
    signal: options.signal,
    maxAttempts: options.maxAttempts,
    reconnectDelayMs: options.reconnectDelayMs,
    onStateChange: options.onStateChange,
    onError: options.onError,
    parseEvent: (message, registeredType) => {
      let payload: unknown;
      try {
        payload = JSON.parse(message.data) as unknown;
      } catch {
        // 文本错误仍然保留为事件载荷，批次详情刷新负责提供权威状态。
        payload = { message: message.data };
      }
      const eventType =
        knowledgeBatchStringField(payload, 'event_type') ??
        knowledgeBatchStringField(payload, 'eventType') ??
        registeredType;
      const eventIds = [
        message.lastEventId,
        knowledgeBatchStringField(payload, 'sse_event_id'),
        knowledgeBatchStringField(payload, 'event_id'),
      ].filter((eventId): eventId is string => Boolean(eventId));
      const eventId = eventIds[0] ?? '';
      return { payload, eventId, eventIds, eventType };
    },
    onEvent: (eventType, payload, eventId) => onEvent({ event_id: eventId, event_type: eventType, payload }),
    isTerminal: isTerminalKnowledgeBatchEvent,
  });
}
export const retryKnowledgeItem = (batchId: string, itemId: string, signal?: AbortSignal) =>
  adminWrite(
    `/api/admin/knowledge-upload-batches/${encodeURIComponent(batchId)}/documents/${encodeURIComponent(itemId)}/retry`,
    'POST',
    undefined,
    undefined,
    signal,
  );
export const reindexKnowledgeItem = (batchId: string, itemId: string, signal?: AbortSignal) =>
  adminWrite(
    `/api/admin/knowledge-upload-batches/${encodeURIComponent(batchId)}/documents/${encodeURIComponent(itemId)}/reindex`,
    'POST',
    undefined,
    undefined,
    signal,
  );
export const changeKnowledgeVisibility = (
  documentId: string,
  visibility: 'published' | 'disabled' | 'draft' | 'deleted',
  signal?: AbortSignal,
) =>
  adminWrite(
    `/api/admin/knowledge-documents/${encodeURIComponent(documentId)}/${visibility === 'draft' ? 'restore' : visibility}`,
    'POST',
    undefined,
    undefined,
    signal,
  );

export type ModelGovernanceProvider = {
  provider_id: number;
  provider_code: string;
  display_name: string;
  status: 'active' | 'disabled' | string;
  endpoint_config_key: string;
  configured: boolean;
  fingerprint: string;
  revision: number;
};

export type ModelGovernanceModel = {
  model_id: number;
  provider_code: string;
  model_name: string;
  model_type: string;
  status: 'active' | 'disabled' | string;
  context_tokens: number | null;
  max_output_tokens: number | null;
  timeout_ms: number;
  revision: number;
};

export type ModelGovernanceRoute = {
  route_id: number;
  tenant_id: number;
  scene: string;
  model_type: string;
  provider_code: string;
  model_name: string;
  fallback_provider_code: string | null;
  fallback_model_name: string | null;
  priority: number;
  route_version: string;
  price_version: string;
  budget_policy_version: string;
  max_cost: number | string | null;
  max_latency_ms: number | null;
  status: 'active' | 'disabled' | string;
  revision: number;
};

export type ModelGovernancePrice = {
  price_version_id: number;
  provider_code: string;
  model_name: string;
  price_version: string;
  input_price_per_million: number | string;
  output_price_per_million: number | string;
  currency: string;
  status: string;
  effective_at: string;
};

export type ModelGovernanceBudget = {
  budget_policy_id: number;
  policy_key: string;
  scene: string;
  scope_type: string;
  max_total_tokens: number;
  max_cost_cny: number | string;
  max_model_calls: number;
  max_step_retries: number;
  window_type: string;
  policy_version: string;
  status: string;
  revision: number;
};

export type ModelGovernanceUsage = {
  provider_code: string;
  model_name: string;
  scene: string;
  status: string;
  calls: number;
  total_tokens: number;
  total_cost: number | string;
  average_latency_ms: number | string;
  first_seen_at: string | null;
  last_seen_at: string | null;
};

export type ModelGovernanceView = {
  providers: ModelGovernanceProvider[];
  models: ModelGovernanceModel[];
  routes: ModelGovernanceRoute[];
  prices: ModelGovernancePrice[];
  budgets: ModelGovernanceBudget[];
  usage: ModelGovernanceUsage[];
};

export type ModelGovernanceMutation = {
  changed: boolean;
  resource_id: number;
  version: string;
  revision: number;
};

export type ModelGovernanceUsageQuery = {
  from?: string;
  to?: string;
};

export type CreateModelPriceRequest = {
  providerCode: string;
  modelName: string;
  priceVersion: string;
  inputPricePerMillion: number | string;
  outputPricePerMillion: number | string;
  currency: string;
  effectiveAt: string;
  revision: number;
};

export type CreateModelBudgetRequest = {
  policyKey: string;
  scene: string;
  scopeType: string;
  maxTotalTokens: number;
  maxCostCny: number | string;
  maxModelCalls: number;
  maxStepRetries: number;
  windowType: string;
  policyVersion: string;
  revision: number;
};

function randomIdempotencyKey(prefix: string) {
  const suffix = globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random().toString(16).slice(2)}`;
  return `${prefix}-${suffix}`;
}

async function confirmationDigest(action: string, target: string, value: string, revision: number) {
  const input = new TextEncoder().encode(`${action}|${target}|${value}|${revision}`);
  const digest = await globalThis.crypto.subtle.digest('SHA-256', input);
  return Array.from(new Uint8Array(digest), (byte) => byte.toString(16).padStart(2, '0')).join('');
}

async function modelGovernanceWrite<T>(
  path: string,
  method: 'POST' | 'PATCH' | 'PUT',
  payload: object,
  idempotencyPrefix: string,
  signal?: AbortSignal,
) {
  return apiRequest<T>(path, {
    method,
    headers: { 'Idempotency-Key': randomIdempotencyKey(idempotencyPrefix) },
    body: JSON.stringify(payload),
    signal,
  });
}

export async function loadModelGovernance(
  query: ModelGovernanceUsageQuery = {},
  signal?: AbortSignal,
): Promise<ModelGovernanceView> {
  if (import.meta.env.VITE_AGENT_MODE !== 'real') throw new Error('Real model governance API is disabled');
  const search = new URLSearchParams();
  if (query.from) search.set('from', query.from);
  if (query.to) search.set('to', query.to);
  const suffix = search.toString();
  return apiRequest<ModelGovernanceView>(
    `/api/admin/model-governance${suffix ? `?${suffix}` : ''}`,
    readRequestInit(signal),
  );
}

export async function updateModelProviderStatus(
  provider: ModelGovernanceProvider,
  status: string,
  signal?: AbortSignal,
) {
  const action = 'model.provider.status.update';
  const target = provider.provider_code;
  const digest = await confirmationDigest(action, target, status, provider.revision);
  return modelGovernanceWrite<ModelGovernanceMutation>(
    `/api/admin/model-governance/providers/${encodeURIComponent(provider.provider_code)}/status`,
    'PATCH',
    { status, revision: provider.revision, confirmed: true, confirmationDigest: digest },
    'model-provider-status',
    signal,
  );
}

export async function updateModelCatalogStatus(model: ModelGovernanceModel, status: string, signal?: AbortSignal) {
  const action = 'model.catalog.status.update';
  const target = String(model.model_id);
  const digest = await confirmationDigest(action, target, status, model.revision);
  return modelGovernanceWrite<ModelGovernanceMutation>(
    `/api/admin/model-governance/models/${encodeURIComponent(model.model_id)}/status`,
    'PATCH',
    { status, revision: model.revision, confirmed: true, confirmationDigest: digest },
    'model-catalog-status',
    signal,
  );
}

export async function updateModelRoute(route: ModelGovernanceRoute, status: string, signal?: AbortSignal) {
  const action = 'model.route.update';
  const target = String(route.route_id);
  const digest = await confirmationDigest(action, target, route.route_version, route.revision);
  return modelGovernanceWrite<ModelGovernanceMutation>(
    `/api/admin/model-governance/routes/${encodeURIComponent(route.route_id)}`,
    'PUT',
    {
      providerCode: route.provider_code,
      modelName: route.model_name,
      fallbackProviderCode: route.fallback_provider_code,
      fallbackModelName: route.fallback_model_name,
      priority: route.priority,
      routeVersion: route.route_version,
      priceVersion: route.price_version,
      budgetPolicyVersion: route.budget_policy_version,
      maxCost: route.max_cost,
      maxLatencyMs: route.max_latency_ms,
      status,
      revision: route.revision,
      confirmed: true,
      confirmationDigest: digest,
    },
    'model-route-update',
    signal,
  );
}

export async function createModelPrice(
  request: CreateModelPriceRequest,
  signal?: AbortSignal,
): Promise<ModelGovernanceMutation> {
  const target = `${request.providerCode.trim()}:${request.modelName.trim()}:${request.priceVersion.trim()}`;
  const digest = await confirmationDigest('model.price.create', target, request.priceVersion, request.revision);
  return modelGovernanceWrite<ModelGovernanceMutation>(
    '/api/admin/model-governance/prices',
    'POST',
    {
      providerCode: request.providerCode,
      modelName: request.modelName,
      priceVersion: request.priceVersion,
      inputPricePerMillion: request.inputPricePerMillion,
      outputPricePerMillion: request.outputPricePerMillion,
      currency: request.currency,
      effectiveAt: request.effectiveAt,
      revision: request.revision,
      confirmed: true,
      confirmationDigest: digest,
    },
    'model-price-create',
    signal,
  );
}

export async function createModelBudget(
  request: CreateModelBudgetRequest,
  signal?: AbortSignal,
): Promise<ModelGovernanceMutation> {
  const target = `${request.policyKey.trim()}:${request.policyVersion.trim()}`;
  const digest = await confirmationDigest('model.budget.create', target, request.policyVersion, request.revision);
  return modelGovernanceWrite<ModelGovernanceMutation>(
    '/api/admin/model-governance/budgets',
    'POST',
    {
      policyKey: request.policyKey,
      scene: request.scene,
      scopeType: request.scopeType,
      maxTotalTokens: request.maxTotalTokens,
      maxCostCny: request.maxCostCny,
      maxModelCalls: request.maxModelCalls,
      maxStepRetries: request.maxStepRetries,
      windowType: request.windowType,
      policyVersion: request.policyVersion,
      revision: request.revision,
      confirmed: true,
      confirmationDigest: digest,
    },
    'model-budget-create',
    signal,
  );
}

async function sha256(value: string) {
  const bytes = await globalThis.crypto.subtle.digest('SHA-256', new TextEncoder().encode(value));
  return Array.from(new Uint8Array(bytes), (byte) => byte.toString(16).padStart(2, '0')).join('');
}

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
