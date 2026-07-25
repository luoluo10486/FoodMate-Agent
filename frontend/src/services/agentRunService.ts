export type AgentRunEvent = {
  status?: string;
  text?: string;
  answer?: string;
  reason?: string;
  error_code?: string;
  error_message?: string;
};

const baseUrl = import.meta.env.DEV ? '' : ((import.meta.env.VITE_API_BASE_URL as string | undefined) ?? '');

export function openAgentRunStream(
  runId: string,
  onEvent: (eventType: string, payload: AgentRunEvent, eventId: string) => void,
  onError: () => void,
): EventSource {
  // SSE 由服务端持久化 outbox 驱动；前端只负责监听、去重和转发事件。
  const source = new EventSource(`${baseUrl}/api/agent-runs/${encodeURIComponent(runId)}/stream`, { withCredentials: true });
  const eventTypes = ['run.accepted', 'run.routed', 'run.answer_stream', 'run.completed', 'run.failed', 'run.cancelled'];
  const seen = new Set<string>();
  for (const eventType of eventTypes) {
    source.addEventListener(eventType, (event) => {
      const message = event as MessageEvent<string>;
      // 浏览器重连可能重新收到旧事件，使用稳定的 SSE ID 避免重复追加回答文本。
      if (message.lastEventId && seen.has(message.lastEventId)) return;
      if (message.lastEventId) seen.add(message.lastEventId);
      try { onEvent(eventType, JSON.parse(message.data) as AgentRunEvent, message.lastEventId); }
      catch { onError(); }
    });
  }
  source.onerror = onError;
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
