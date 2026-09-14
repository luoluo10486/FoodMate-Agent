import type { AgentStreamConnection, AgentStreamHandle } from '../types/agent';
import { isTerminalAgentEvent } from '../lib/agentEvent';
import { apiRequest } from './apiClient';
import { openSseStream } from './sseStream';

function requestInit(signal?: AbortSignal): RequestInit {
  return signal ? { signal } : {};
}

export type AgentRunEvent = {
  event_id?: string;
  sse_event_id?: string;
  event_type?: string;
  state?: string;
  payload?: unknown;
  code?: string;
  message?: string;
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
  completed_invocation_ids?: string[];
  current_node?: string;
  budget_revision?: number;
  error_code?: string;
  error_message?: string;
  result_type?: string;
  usage?: {
    tokens?: number;
    cost_cny?: number | string;
    model_calls?: number;
    steps?: number;
  };
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
  /** 绑定页面或会话生命周期，取消后关闭流并阻止后续重连。 */
  signal?: AbortSignal;
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

const agentEventTypes = [
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
] as const;

export function openAgentRunStream(
  runId: string,
  onEvent: (eventType: string, payload: AgentRunEvent, eventId: string) => void,
  options: AgentStreamOptions = {},
): AgentStreamHandle {
  return openSseStream<AgentRunEvent>({
    path: `/api/agent-runs/${encodeURIComponent(runId)}/stream`,
    eventTypes: agentEventTypes,
    lastEventId: options.lastEventId,
    signal: options.signal,
    maxAttempts: options.maxAttempts,
    reconnectDelayMs: options.reconnectDelayMs,
    onStateChange: options.onStateChange,
    onError: options.onError,
    parseEvent: (message, registeredType) => {
      const payload = JSON.parse(message.data) as AgentRunEvent;
      const eventIds = [message.lastEventId, payload.sse_event_id, payload.event_id].filter(
        (eventId): eventId is string => Boolean(eventId),
      );
      return {
        payload,
        eventId: eventIds[0],
        eventIds,
        eventType: payload.event_type || registeredType,
      };
    },
    onEvent,
    isTerminal: (eventType, payload) => isTerminalAgentEvent(eventType, payload as unknown as Record<string, unknown>),
  });
}

export async function loadAgentRun(runId: string, signal?: AbortSignal): Promise<AgentRunStatus> {
  return apiRequest<AgentRunStatus>(`/api/agent-runs/${encodeURIComponent(runId)}`, requestInit(signal));
}

export async function cancelAgentRun(
  runId: string,
  reason = 'user_requested',
  signal?: AbortSignal,
): Promise<AgentCancellationResult> {
  const result = await apiRequest<AgentCancellationPayload>(`/api/agent-runs/${encodeURIComponent(runId)}/cancel`, {
    method: 'POST',
    body: JSON.stringify({ reason }),
    ...requestInit(signal),
  });
  return normalizeCancellationResult(result);
}

export async function extendAgentRunBudget(
  runId: string,
  additionalTokens: number,
  additionalCostCny: string,
  confirmationDigest?: string,
  signal?: AbortSignal,
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
      ...requestInit(signal),
    },
  );
  return normalizeBudgetExtensionResult(result);
}

/**
 * 根据已持久化的 checkpoint 恢复时，前端不提交 checkpoint 内容。
 */
export async function recoverAgentRun(
  runId: string,
  request: AgentRecoveryRequest,
  signal?: AbortSignal,
): Promise<AgentRecoveryResult> {
  return apiRequest<AgentRecoveryResult>(`/api/agent-runs/${encodeURIComponent(runId)}/recover`, {
    method: 'POST',
    body: JSON.stringify({
      checkpoint_version: request.checkpointVersion,
      checkpoint_digest: request.checkpointDigest,
      completed_invocation_ids: request.completedInvocationIds ?? [],
    }),
    ...requestInit(signal),
  });
}

export async function recoverAgentRunFromCheckpoint(runId: string, signal?: AbortSignal): Promise<AgentRecoveryResult> {
  return apiRequest<AgentRecoveryResult>(`/api/agent-runs/${encodeURIComponent(runId)}/recover-from-checkpoint`, {
    method: 'POST',
    ...requestInit(signal),
  });
}

/** 失败重试由 Java 根据 Runtime 的 retryable 事件和持久化事实裁决。 */
export async function retryAgentRun(runId: string, signal?: AbortSignal): Promise<AgentRecoveryResult> {
  return apiRequest<AgentRecoveryResult>(`/api/agent-runs/${encodeURIComponent(runId)}/retry`, {
    method: 'POST',
    ...requestInit(signal),
  });
}

export async function submitAgentFeedback(
  runId: string,
  messageId: string,
  request: AgentFeedbackRequest,
  signal?: AbortSignal,
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
      ...requestInit(signal),
    },
  );
}

export async function createApprovalProposal(
  request: ApprovalProposalRequest,
  signal?: AbortSignal,
): Promise<ApprovalProposalResponse> {
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
    ...requestInit(signal),
  });
}

export async function loadApprovalProposal(
  approvalRequestId: string | number,
  signal?: AbortSignal,
): Promise<ApprovalProposalResponse> {
  return apiRequest<ApprovalProposalResponse>(
    `/api/approvals/${encodeURIComponent(String(approvalRequestId))}`,
    requestInit(signal),
  );
}

export async function confirmAgentWrite(
  approvalRequestId: string | number,
  parameters: Record<string, unknown> = {},
  signal?: AbortSignal,
): Promise<ApprovalProposalResponse> {
  return apiRequest<ApprovalProposalResponse>(
    `/api/approvals/${encodeURIComponent(String(approvalRequestId))}/confirm`,
    {
      method: 'POST',
      body: JSON.stringify(parameters),
      ...requestInit(signal),
    },
  );
}

export async function executeAgentWrite(
  approvalRequestId: string | number,
  parameters: Record<string, unknown>,
  signal?: AbortSignal,
): Promise<ApprovalExecuteResponse> {
  return apiRequest<ApprovalExecuteResponse>(
    `/api/approvals/${encodeURIComponent(String(approvalRequestId))}/execute`,
    {
      method: 'POST',
      body: JSON.stringify(parameters),
      ...requestInit(signal),
    },
  );
}

export async function rejectAgentWrite(
  approvalRequestId: string | number,
  parameters: Record<string, unknown> = {},
  signal?: AbortSignal,
): Promise<ApprovalProposalResponse> {
  return apiRequest<ApprovalProposalResponse>(
    `/api/approvals/${encodeURIComponent(String(approvalRequestId))}/reject`,
    {
      method: 'POST',
      body: JSON.stringify(parameters),
      ...requestInit(signal),
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
