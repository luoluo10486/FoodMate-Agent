export type ChatRun = {
  run_id: string;
  dispatch_id: string;
  status: string;
  duplicate: boolean;
};

type ApiResponse<T> = {
  success: boolean;
  data: T;
  error?: { code: string; message: string };
};

const baseUrl = (import.meta.env.VITE_API_BASE_URL as string | undefined) ?? '';

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${baseUrl}${path}`, {
    ...init,
    headers: { 'Content-Type': 'application/json', ...(init?.headers ?? {}) },
  });
  const body = (await response.json()) as ApiResponse<T>;
  if (!response.ok || !body.success) {
    throw new Error(body.error?.message ?? `Request failed: ${response.status}`);
  }
  return body.data;
}

export function createChatRun(prompt: string, sessionId?: string): Promise<ChatRun> {
  return request<ChatRun>('/api/chat/runs', {
    method: 'POST',
    body: JSON.stringify({ prompt, session_id: sessionId }),
  });
}

export function getChatRun(runId: string): Promise<{ run_id: string; status: string }> {
  return request<{ run_id: string; status: string }>(`/api/chat/runs/${encodeURIComponent(runId)}`);
}

export type ChatRunEvent = {
  event_id: string;
  run_id: string;
  event_seq: number;
  state: string;
  payload: unknown;
  occurred_at: string;
};

export function getChatRunEvents(runId: string): Promise<ChatRunEvent[]> {
  return request<ChatRunEvent[]>(`/api/chat/runs/${encodeURIComponent(runId)}/events`);
}
