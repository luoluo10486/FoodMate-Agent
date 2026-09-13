import type { AgentStreamConnection, AgentStreamConnectionState, AgentStreamHandle } from '../types/agent';
import { apiRequest } from './apiClient';

export type AgentRunEvent = {
  event_id?: string;
  sse_event_id?: string;
  event_type?: string;
  status?: string;
  intent?: string;
  complexity?: string;
  risk_level?: string;
  plan_version?: string;
  proposal_id?: string;
  invocation_id?: string;
  tool_type?: string;
  tool_name?: string;
  latency_ms?: number;
  text?: string;
  answer?: string;
  reason?: string;
  checkpoint_version?: number;
  checkpoint_digest?: string;
  current_node?: string;
  budget_revision?: number;
  error_code?: string;
  error_message?: string;
  result_type?: string;
  requires_confirmation?: boolean;
  budget_actions?: { requires_confirmation?: boolean };
  confirmation_ref?: string;
  approval_request_id?: string;
  operation?: string;
  resource_type?: string;
  plan?: {
    plan_name?: string;
    people?: number;
    days?: number;
    budget?: number | string;
    calorie_target?: number;
    protein_target?: number;
    allergens?: string[];
    dislikes?: string[];
    days_plan?: Array<Record<string, unknown>>;
  };
  details?: {
    operation?: string;
    resource_type?: string;
    meal_time?: string;
    meal_type?: string;
    notes?: string | null;
    items?: Array<{ name?: string; amount?: number; unit?: string }>;
    plan?: AgentRunEvent['plan'];
  };
  retryable?: boolean;
  citations?: Array<{
    citation_id: string;
    document_id: string;
    title: string;
    version: string;
    section_path?: string;
    snippet: string;
  }>;
};

export type AgentStreamOptions = {
  /** 重新订阅已有 Run 时使用的持久化 SSE 游标。 */
  lastEventId?: string;
  maxAttempts?: number;
  reconnectDelayMs?: number;
  onStateChange?: (connection: AgentStreamConnection) => void;
  onError?: (connection: AgentStreamConnection) => void;
};

export type AgentRunStatus = {
  run_id: string;
  status: string;
  accepted_event_count: number;
};

export type AgentCancellationResult = {
  run_id: string;
  status: string;
  terminal: boolean;
};

type AgentCancellationPayload = Partial<AgentCancellationResult> & {
  runId?: string | number;
};

export type AgentBudgetExtensionResult = {
  run_id: string;
  dispatch_id: string;
  attempt: number;
  budget_revision: number;
  status: string;
};

type AgentBudgetExtensionPayload = Partial<AgentBudgetExtensionResult> & {
  runId?: string | number;
  dispatchId?: string;
  budgetRevision?: number;
};

export type AgentRecoveryRequest = {
  checkpointVersion: number;
  checkpointDigest: string;
  completedInvocationIds?: string[];
};

export type AgentRecoveryResult = {
  run_id: string;
  dispatch_id: string;
  attempt: number;
  status: string;
};

export type AgentFeedbackRequest = {
  helpful: boolean;
  reasonCodes?: string[];
  comment?: string;
};

export type AgentFeedbackResponse = {
  feedback_id: string;
  run_id: string;
  message_id: string;
  helpful: boolean;
  reason_codes: string[];
  high_risk: boolean;
  idempotency_key: string;
};

export type ApprovalProposalRequest = {
  sessionId?: string | number;
  agentRunId?: string | number;
  operation: string;
  resourceType: string;
  resourceId?: string | number;
  parameters: Record<string, unknown>;
  idempotencyKey?: string;
  expiresInSeconds?: number;
};

export type ApprovalProposalResponse = {
  approval_request_id: string;
  operation: string;
  resource_type: string;
  resource_id: number | null;
  parameters_digest: string;
  status: string;
  expires_at: string;
  confirmed_at: string | null;
  executed_at: string | null;
};

export type ApprovalExecuteResponse = {
  approval_request_id: string;
  operation: string;
  status: string;
  resource_id: number | null;
};

const terminalEventTypes = new Set(['run.completed', 'run.failed', 'run.cancelled', 'run.superseded']);

const baseUrl = import.meta.env.DEV ? '' : ((import.meta.env.VITE_API_BASE_URL as string | undefined) ?? '');

export function openAgentRunStream(
  runId: string,
  onEvent: (eventType: string, payload: AgentRunEvent, eventId: string) => void,
  options: AgentStreamOptions = {},
): AgentStreamHandle {
  const maxAttempts = Math.max(1, options.maxAttempts ?? 5);
  const reconnectDelayMs = Math.max(0, options.reconnectDelayMs ?? 500);
  const eventTypes = [
    'run.created',
    'run.event',
    'run.accepted',
    'run.routed',
    'run.planned',
    'run.retrieval_started',
    'run.retrieval_finished',
    'run.context_assembled',
    'run.tool_started',
    'run.tool_finished',
    'run.eval_decided',
    'run.model_usage',
    'run.checkpoint_saved',
    'run.clarification_requested',
    'run.cancel_acknowledged',
    'run.answer_stream',
    'run.completed',
    'run.failed',
    'run.cancelled',
    'run.superseded',
  ];
  const seen = new Set<string>();
  let source: EventSource | undefined;
  let reconnectTimer: number | undefined;
  let terminal = false;
  let closed = false;
  let connection: AgentStreamConnection = {
    state: 'connecting',
    attempt: 1,
    maxAttempts,
    lastEventId: options.lastEventId?.trim() || undefined,
  };

  const publishState = (state: AgentStreamConnectionState, patch: Partial<AgentStreamConnection> = {}) => {
    connection = { ...connection, ...patch, state };
    options.onStateChange?.(connection);
  };

  const closeSource = () => {
    source?.close();
    source = undefined;
  };

  const close = () => {
    if (closed) return;
    closed = true;
    terminal = true;
    if (reconnectTimer !== undefined) window.clearTimeout(reconnectTimer);
    closeSource();
    publishState('closed');
  };

  const handleConnectionFailure = (failedSource?: EventSource) => {
    // 旧连接的延迟 error 或解析错误不能影响已经建立的新连接。
    if (closed || terminal || (failedSource && source !== failedSource)) return;
    closeSource();
    if (connection.attempt >= maxAttempts) {
      publishState('exhausted');
      options.onError?.(connection);
      return;
    }
    if (reconnectTimer !== undefined) return;
    const attempt = connection.attempt + 1;
    publishState('reconnecting', { attempt });
    reconnectTimer = window.setTimeout(() => {
      reconnectTimer = undefined;
      connect();
    }, reconnectDelayMs);
    options.onError?.(connection);
  };

  const connect = () => {
    if (closed || terminal) return;
    const lastEventId = connection.lastEventId;
    const suffix = lastEventId ? `?lastEventId=${encodeURIComponent(lastEventId)}` : '';
    const nextState = connection.attempt === 1 ? 'connecting' : 'reconnecting';
    if (connection.state !== nextState) publishState(nextState);
    let nextSource: EventSource;
    try {
      nextSource = new EventSource(`${baseUrl}/api/agent-runs/${encodeURIComponent(runId)}/stream${suffix}`, {
        withCredentials: true,
      });
    } catch {
      handleConnectionFailure();
      return;
    }
    source = nextSource;
    nextSource.onopen = () => {
      if (closed || terminal || source !== nextSource) return;
      publishState('connected');
    };
    for (const registeredType of eventTypes) {
      nextSource.addEventListener(registeredType, (event) => {
        // EventSource.close() 后浏览器仍可能派发已排队的消息，必须丢弃旧连接事件。
        if (closed || terminal || source !== nextSource) return;
        const message = event as MessageEvent<string>;
        let payload: AgentRunEvent;
        try {
          payload = JSON.parse(message.data) as AgentRunEvent;
        } catch {
          handleConnectionFailure(nextSource);
          return;
        }
        const eventId = message.lastEventId || payload.sse_event_id || payload.event_id || '';
        if (eventId && seen.has(eventId)) return;
        if (eventId) seen.add(eventId);
        if (eventId && eventId !== connection.lastEventId) {
          // 游标变化也必须通知页面，保证连接状态面板和下一次续接使用同一份 ID。
          publishState(connection.state, { lastEventId: eventId });
        }
        const eventType = payload.event_type || registeredType;
        onEvent(eventType, payload, eventId);
        if (terminalEventTypes.has(eventType)) {
          terminal = true;
          closeSource();
          publishState('closed');
        }
      });
    }
    nextSource.onerror = () => handleConnectionFailure(nextSource);
  };

  // 建立 EventSource 前先发布初始连接状态，页面可以立即显示连接中的运行态。
  options.onStateChange?.(connection);
  connect();
  return { close, getConnection: () => connection };
}

export async function loadAgentRun(runId: string): Promise<AgentRunStatus> {
  return apiRequest<AgentRunStatus>(`/api/agent-runs/${encodeURIComponent(runId)}`);
}

export async function cancelAgentRun(runId: string, reason = 'user_requested'): Promise<AgentCancellationResult> {
  const result = await apiRequest<AgentCancellationPayload>(`/api/agent-runs/${encodeURIComponent(runId)}/cancel`, {
    method: 'POST',
    body: JSON.stringify({ reason }),
  });
  return normalizeCancellationResult(result);
}

export async function extendAgentRunBudget(
  runId: string,
  additionalTokens: number,
  additionalCostCny: string,
  confirmationDigest?: string,
): Promise<AgentBudgetExtensionResult> {
  const digest =
    confirmationDigest ??
    (await stableDigest(`agent-run.budget.extension|${runId}|${additionalTokens}|${additionalCostCny}`));
  const result = await apiRequest<AgentBudgetExtensionPayload>(
    `/api/agent-runs/${encodeURIComponent(runId)}/budget-extensions`,
    {
      method: 'POST',
      body: JSON.stringify({
        additionalTokens,
        additionalCostCny,
        confirmationDigest: digest,
      }),
    },
  );
  return normalizeBudgetExtensionResult(result);
}

/**
 * 根据已持久化的 checkpoint 恢复时，前端不提交 checkpoint 内容。
 */
export async function recoverAgentRun(runId: string, request: AgentRecoveryRequest): Promise<AgentRecoveryResult> {
  return apiRequest<AgentRecoveryResult>(`/api/agent-runs/${encodeURIComponent(runId)}/recover`, {
    method: 'POST',
    body: JSON.stringify({
      checkpoint_version: request.checkpointVersion,
      checkpoint_digest: request.checkpointDigest,
      completed_invocation_ids: request.completedInvocationIds ?? [],
    }),
  });
}

export async function recoverAgentRunFromCheckpoint(runId: string): Promise<AgentRecoveryResult> {
  return apiRequest<AgentRecoveryResult>(`/api/agent-runs/${encodeURIComponent(runId)}/recover-from-checkpoint`, {
    method: 'POST',
  });
}

export async function submitAgentFeedback(
  runId: string,
  messageId: string,
  request: AgentFeedbackRequest,
): Promise<AgentFeedbackResponse> {
  return apiRequest<AgentFeedbackResponse>(
    `/api/agent-runs/${encodeURIComponent(runId)}/messages/${encodeURIComponent(messageId)}/feedback`,
    {
      method: 'POST',
      headers: { 'Idempotency-Key': `agent-feedback-${runId}-${messageId}` },
      body: JSON.stringify({
        helpful: request.helpful,
        reason_codes: request.reasonCodes ?? [],
        comment: request.comment || undefined,
      }),
    },
  );
}

export async function createApprovalProposal(request: ApprovalProposalRequest): Promise<ApprovalProposalResponse> {
  return apiRequest<ApprovalProposalResponse>('/api/approvals/proposals', {
    method: 'POST',
    body: JSON.stringify({
      session_id: request.sessionId,
      agent_run_id: request.agentRunId,
      operation: request.operation,
      resource_type: request.resourceType,
      resource_id: request.resourceId,
      parameters: request.parameters,
      idempotency_key: request.idempotencyKey ?? idempotencyKey('approval-proposal'),
      expires_in_seconds: request.expiresInSeconds ?? 900,
    }),
  });
}

export async function loadApprovalProposal(approvalRequestId: string | number): Promise<ApprovalProposalResponse> {
  return apiRequest<ApprovalProposalResponse>(`/api/approvals/${encodeURIComponent(String(approvalRequestId))}`);
}

export async function confirmAgentWrite(
  approvalRequestId: string | number,
  parameters: Record<string, unknown> = {},
): Promise<ApprovalProposalResponse> {
  return apiRequest<ApprovalProposalResponse>(
    `/api/approvals/${encodeURIComponent(String(approvalRequestId))}/confirm`,
    {
      method: 'POST',
      body: JSON.stringify(parameters),
    },
  );
}

export async function executeAgentWrite(
  approvalRequestId: string | number,
  parameters: Record<string, unknown>,
): Promise<ApprovalExecuteResponse> {
  return apiRequest<ApprovalExecuteResponse>(
    `/api/approvals/${encodeURIComponent(String(approvalRequestId))}/execute`,
    {
      method: 'POST',
      body: JSON.stringify(parameters),
    },
  );
}

export async function rejectAgentWrite(
  approvalRequestId: string | number,
  parameters: Record<string, unknown> = {},
): Promise<ApprovalProposalResponse> {
  return apiRequest<ApprovalProposalResponse>(
    `/api/approvals/${encodeURIComponent(String(approvalRequestId))}/reject`,
    {
      method: 'POST',
      body: JSON.stringify(parameters),
    },
  );
}

function idempotencyKey(prefix: string) {
  const suffix = globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random().toString(16).slice(2)}`;
  return `${prefix}-${suffix}`;
}

async function stableDigest(value: string) {
  const bytes = await globalThis.crypto.subtle.digest('SHA-256', new TextEncoder().encode(value));
  return `sha256:${Array.from(new Uint8Array(bytes), (byte) => byte.toString(16).padStart(2, '0')).join('')}`;
}

function normalizeCancellationResult(payload: AgentCancellationPayload): AgentCancellationResult {
  return {
    run_id: String(payload.run_id ?? payload.runId ?? ''),
    status: String(payload.status ?? ''),
    terminal: Boolean(payload.terminal),
  };
}

function normalizeBudgetExtensionResult(payload: AgentBudgetExtensionPayload): AgentBudgetExtensionResult {
  return {
    run_id: String(payload.run_id ?? payload.runId ?? ''),
    dispatch_id: String(payload.dispatch_id ?? payload.dispatchId ?? ''),
    attempt: Number(payload.attempt ?? 0),
    budget_revision: Number(payload.budget_revision ?? payload.budgetRevision ?? 0),
    status: String(payload.status ?? ''),
  };
}
