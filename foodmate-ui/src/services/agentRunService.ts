export type AgentRunEvent = {
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
};

const baseUrl = import.meta.env.DEV ? '' : ((import.meta.env.VITE_API_BASE_URL as string | undefined) ?? '');

export function openAgentRunStream(
  runId: string,
  onEvent: (eventType: string, payload: AgentRunEvent, eventId: string) => void,
  onError: () => void,
): EventSource {
  // SSE 由服务端持久化 outbox 驱动；前端只负责监听、去重和转发事件。
  const source = new EventSource(`${baseUrl}/api/agent-runs/${encodeURIComponent(runId)}/stream`, { withCredentials: true });
  let terminal = false;
  const eventTypes = ['run.accepted', 'run.routed', 'run.checkpoint_saved', 'run.clarification_requested', 'run.answer_stream', 'run.completed', 'run.failed', 'run.cancelled', 'run.superseded'];
  const seen = new Set<string>();
  for (const eventType of eventTypes) {
    source.addEventListener(eventType, (event) => {
      const message = event as MessageEvent<string>;
      // 浏览器重连可能重新收到旧事件，使用稳定的 SSE ID 避免重复追加回答文本。
      if (message.lastEventId && seen.has(message.lastEventId)) return;
      if (message.lastEventId) seen.add(message.lastEventId);
      try {
        onEvent(eventType, JSON.parse(message.data) as AgentRunEvent, message.lastEventId);
        if (['run.completed', 'run.failed', 'run.cancelled', 'run.superseded'].includes(eventType)) {
          terminal = true;
          source.close();
        }
      }
      catch { onError(); }
    });
  }
  source.onerror = () => { if (!terminal) onError(); };
  return source;
}

export async function cancelAgentRun(runId: string): Promise<void> {
  // 取消是写操作，必须把可读 CSRF Cookie 转发给后端。
  const csrf = document.cookie.split('; ').find((value) => value.startsWith('foodmate_csrf='))?.split('=').slice(1).join('=');
  const response = await fetch(`${baseUrl}/api/agent-runs/${encodeURIComponent(runId)}/cancel`, {
    method: 'POST', credentials: 'include', headers: { 'Content-Type': 'application/json', ...(csrf ? { 'X-CSRF-Token': csrf } : {}) },
    body: JSON.stringify({ reason: 'user_requested' }),
  });
  if (!response.ok) throw new Error('取消运行失败');
}

export async function extendAgentRunBudget(runId: string, additionalTokens: number, additionalCostCny: string): Promise<void> {
  const csrf = document.cookie.split('; ').find((value) => value.startsWith('foodmate_csrf='))?.split('=').slice(1).join('=');
  const raw = `${runId}:${additionalTokens}:${additionalCostCny}:${Date.now()}`;
  const digest = Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256', new TextEncoder().encode(raw))))
    .map((value) => value.toString(16).padStart(2, '0')).join('');
  const response = await fetch(`${baseUrl}/api/agent-runs/${encodeURIComponent(runId)}/budget-extensions`, {
    method: 'POST', credentials: 'include', headers: { 'Content-Type': 'application/json', ...(csrf ? { 'X-CSRF-Token': csrf } : {}) },
    body: JSON.stringify({ additional_tokens: additionalTokens, additional_cost_cny: additionalCostCny, confirmation_digest: `sha256:${digest}` }),
  });
  if (!response.ok) throw new Error('预算追加失败，请稍后重试。');
}

/**
 * 恢复必须由 Java 根据已持久化 checkpoint 对账，前端不提交 checkpoint 内容。
 */
export async function recoverAgentRun(runId: string): Promise<{ run_id: string; dispatch_id: string; attempt: number; status: string }> {
  const csrf = document.cookie.split('; ').find((value) => value.startsWith('foodmate_csrf='))?.split('=').slice(1).join('=');
  const response = await fetch(`${baseUrl}/api/agent-runs/${encodeURIComponent(runId)}/recover-from-checkpoint`, {
    method: 'POST',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json', ...(csrf ? { 'X-CSRF-Token': csrf } : {}) },
  });
  const body = await response.json() as { success: boolean; data?: { run_id: string; dispatch_id: string; attempt: number; status: string }; error?: { message?: string } };
  if (!response.ok || !body.success || !body.data) throw new Error(body.error?.message ?? '运行恢复失败，请稍后重试。');
  return body.data;
}
