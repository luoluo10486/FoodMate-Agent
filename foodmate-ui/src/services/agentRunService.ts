import type { AgentStreamConnection, AgentStreamConnectionState } from '../types/agent';

export type AgentRunEvent = {
  event_id?: string;
  sse_event_id?: string;
  event_type?: string;
  status?: string;
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
  details?: {
    meal_time?: string;
    meal_type?: string;
    notes?: string | null;
    items?: Array<{ name?: string; amount?: number; unit?: string }>;
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

export type AgentStreamHandle = {
  close: () => void;
  getConnection: () => AgentStreamConnection;
};

export type AgentStreamOptions = {
  maxAttempts?: number;
  reconnectDelayMs?: number;
  onStateChange?: (connection: AgentStreamConnection) => void;
  onError?: (connection: AgentStreamConnection) => void;
};

const baseUrl = import.meta.env.DEV ? '' : ((import.meta.env.VITE_API_BASE_URL as string | undefined) ?? '');

export function openAgentRunStream(
  runId: string,
  onEvent: (eventType: string, payload: AgentRunEvent, eventId: string) => void,
  options: AgentStreamOptions = {},
): AgentStreamHandle {
  const maxAttempts = Math.max(1, options.maxAttempts ?? 5);
  const reconnectDelayMs = Math.max(0, options.reconnectDelayMs ?? 500);
  const eventTypes = [
    'run.event',
    'run.accepted',
    'run.routed',
    'run.checkpoint_saved',
    'run.clarification_requested',
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
  let connection: AgentStreamConnection = { state: 'connecting', attempt: 1, maxAttempts };

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

  const connect = () => {
    if (closed || terminal) return;
    const lastEventId = connection.lastEventId;
    const suffix = lastEventId ? `?lastEventId=${encodeURIComponent(lastEventId)}` : '';
    publishState(connection.attempt === 1 ? 'connecting' : 'reconnecting');
    const nextSource = new EventSource(`${baseUrl}/api/agent-runs/${encodeURIComponent(runId)}/stream${suffix}`, {
      withCredentials: true,
    });
    source = nextSource;
    nextSource.onopen = () => publishState('connected');
    for (const registeredType of eventTypes) {
      nextSource.addEventListener(registeredType, (event) => {
        const message = event as MessageEvent<string>;
        let payload: AgentRunEvent;
        try {
          payload = JSON.parse(message.data) as AgentRunEvent;
        } catch {
          options.onError?.(connection);
          return;
        }
        const eventId = message.lastEventId || payload.sse_event_id || payload.event_id || '';
        if (eventId && seen.has(eventId)) return;
        if (eventId) seen.add(eventId);
        if (eventId && eventId !== connection.lastEventId) connection = { ...connection, lastEventId: eventId };
        const eventType = payload.event_type || registeredType;
        onEvent(eventType, payload, eventId);
        if (['run.completed', 'run.failed', 'run.cancelled', 'run.superseded'].includes(eventType)) {
          terminal = true;
          closeSource();
          publishState('closed');
        }
      });
    }
    nextSource.onerror = () => {
      if (closed || terminal) return;
      closeSource();
      if (connection.attempt >= maxAttempts) {
        publishState('exhausted');
        options.onError?.(connection);
        return;
      }
      const attempt = connection.attempt + 1;
      publishState('reconnecting', { attempt });
      reconnectTimer = window.setTimeout(() => {
        reconnectTimer = undefined;
        connect();
      }, reconnectDelayMs);
      options.onError?.(connection);
    };
  };

  connect();
  return { close, getConnection: () => connection };
}

export async function cancelAgentRun(runId: string): Promise<void> {
  // 取消是写操作，必须把可读 CSRF Cookie 转发给后端。
  const csrf = document.cookie
    .split('; ')
    .find((value) => value.startsWith('foodmate_csrf='))
    ?.split('=')
    .slice(1)
    .join('=');
  const response = await fetch(`${baseUrl}/api/agent-runs/${encodeURIComponent(runId)}/cancel`, {
    method: 'POST',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json', ...(csrf ? { 'X-CSRF-Token': csrf } : {}) },
    body: JSON.stringify({ reason: 'user_requested' }),
  });
  if (!response.ok) throw new Error('取消运行失败');
}

export async function extendAgentRunBudget(
  runId: string,
  additionalTokens: number,
  additionalCostCny: string,
): Promise<void> {
  const csrf = document.cookie
    .split('; ')
    .find((value) => value.startsWith('foodmate_csrf='))
    ?.split('=')
    .slice(1)
    .join('=');
  const raw = `${runId}:${additionalTokens}:${additionalCostCny}:${Date.now()}`;
  const digest = Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256', new TextEncoder().encode(raw))))
    .map((value) => value.toString(16).padStart(2, '0'))
    .join('');
  const response = await fetch(`${baseUrl}/api/agent-runs/${encodeURIComponent(runId)}/budget-extensions`, {
    method: 'POST',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json', ...(csrf ? { 'X-CSRF-Token': csrf } : {}) },
    body: JSON.stringify({
      additional_tokens: additionalTokens,
      additional_cost_cny: additionalCostCny,
      confirmation_digest: `sha256:${digest}`,
    }),
  });
  if (!response.ok) throw new Error('预算追加失败，请稍后重试。');
}

/**
 * 恢复必须由 Java 根据已持久化 checkpoint 对账，前端不提交 checkpoint 内容。
 */
export async function recoverAgentRun(
  runId: string,
): Promise<{ run_id: string; dispatch_id: string; attempt: number; status: string }> {
  const csrf = document.cookie
    .split('; ')
    .find((value) => value.startsWith('foodmate_csrf='))
    ?.split('=')
    .slice(1)
    .join('=');
  const response = await fetch(`${baseUrl}/api/agent-runs/${encodeURIComponent(runId)}/recover-from-checkpoint`, {
    method: 'POST',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json', ...(csrf ? { 'X-CSRF-Token': csrf } : {}) },
  });
  const body = (await response.json()) as {
    success: boolean;
    data?: { run_id: string; dispatch_id: string; attempt: number; status: string };
    error?: { message?: string };
  };
  if (!response.ok || !body.success || !body.data) throw new Error(body.error?.message ?? '运行恢复失败，请稍后重试。');
  return body.data;
}

export async function confirmAgentWrite(
  approvalRequestId: string | number,
  parameters: Record<string, unknown> = {},
): Promise<unknown> {
  const csrf = document.cookie
    .split('; ')
    .find((value) => value.startsWith('foodmate_csrf='))
    ?.split('=')
    .slice(1)
    .join('=');
  const response = await fetch(`${baseUrl}/api/approvals/${encodeURIComponent(String(approvalRequestId))}/confirm`, {
    method: 'POST',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json', ...(csrf ? { 'X-CSRF-Token': csrf } : {}) },
    body: JSON.stringify(parameters),
  });
  const body = (await response.json()) as { success?: boolean; data?: unknown; error?: { message?: string } };
  if (!response.ok || body.success === false) throw new Error(body.error?.message ?? '写入确认失败，请稍后重试。');
  return body.data;
}

export async function executeAgentWrite(
  approvalRequestId: string | number,
  parameters: Record<string, unknown>,
): Promise<unknown> {
  const csrf = document.cookie
    .split('; ')
    .find((value) => value.startsWith('foodmate_csrf='))
    ?.split('=')
    .slice(1)
    .join('=');
  const response = await fetch(`${baseUrl}/api/approvals/${encodeURIComponent(String(approvalRequestId))}/execute`, {
    method: 'POST',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json', ...(csrf ? { 'X-CSRF-Token': csrf } : {}) },
    body: JSON.stringify(parameters),
  });
  const body = (await response.json()) as { success?: boolean; data?: unknown; error?: { message?: string } };
  if (!response.ok || body.success === false) throw new Error(body.error?.message ?? '写入执行失败，请稍后重试。');
  return body.data;
}

export async function rejectAgentWrite(
  approvalRequestId: string | number,
  parameters: Record<string, unknown> = {},
): Promise<unknown> {
  const csrf = document.cookie
    .split('; ')
    .find((value) => value.startsWith('foodmate_csrf='))
    ?.split('=')
    .slice(1)
    .join('=');
  const response = await fetch(`${baseUrl}/api/approvals/${encodeURIComponent(String(approvalRequestId))}/reject`, {
    method: 'POST',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json', ...(csrf ? { 'X-CSRF-Token': csrf } : {}) },
    body: JSON.stringify(parameters),
  });
  const body = (await response.json()) as { success?: boolean; data?: unknown; error?: { message?: string } };
  if (!response.ok || body.success === false) throw new Error(body.error?.message ?? '取消写入失败，请稍后重试。');
  return body.data;
}
