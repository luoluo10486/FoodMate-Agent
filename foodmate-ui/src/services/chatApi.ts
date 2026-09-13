export type ChatRun = {
  run_id: string;
  dispatch_id: string;
  status: string;
  duplicate: boolean;
};

export type ChatCancellationResult = {
  run_id: string;
  status: string;
  terminal?: boolean;
  command_id?: string;
  dispatch_id?: string;
  duplicate?: boolean;
};

type ChatCancellationPayload = Partial<ChatCancellationResult> & {
  runId?: string | number;
  commandId?: string;
  dispatchId?: string;
};
import { apiRequest } from './apiClient';

export function createChatRun(prompt: string, sessionId?: string): Promise<ChatRun> {
  return apiRequest<ChatRun>('/api/chat/runs', {
    method: 'POST',
    body: JSON.stringify({ prompt, session_id: sessionId }),
  });
}

export function getChatRun(runId: string): Promise<{ run_id: string; status: string }> {
  return apiRequest<{ run_id: string; status: string }>(`/api/chat/runs/${encodeURIComponent(runId)}`);
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
  return apiRequest<ChatRunEvent[]>(`/api/chat/runs/${encodeURIComponent(runId)}/events`);
}

export async function cancelChatRun(runId: string): Promise<ChatCancellationResult> {
  const result = await apiRequest<ChatCancellationPayload>(`/api/chat/runs/${encodeURIComponent(runId)}/cancel`, {
    method: 'POST',
    body: JSON.stringify({ reason: 'user_cancelled' }),
  });
  return {
    run_id: String(result.run_id ?? result.runId ?? runId),
    status: String(result.status ?? ''),
    ...(result.terminal === undefined ? {} : { terminal: Boolean(result.terminal) }),
    ...(result.command_id || result.commandId ? { command_id: String(result.command_id ?? result.commandId) } : {}),
    ...(result.dispatch_id || result.dispatchId
      ? { dispatch_id: String(result.dispatch_id ?? result.dispatchId) }
      : {}),
    ...(result.duplicate === undefined ? {} : { duplicate: Boolean(result.duplicate) }),
  };
}

export function streamChatRun(runId: string, onEvent: (event: ChatRunEvent) => void, lastEventId?: number): () => void {
  const baseUrl = import.meta.env.DEV ? '' : ((import.meta.env.VITE_API_BASE_URL as string | undefined) ?? '');
  const suffix = lastEventId && lastEventId > 0 ? `?lastEventId=${lastEventId}` : '';
  const source = new EventSource(`${baseUrl}/api/chat/runs/${encodeURIComponent(runId)}/stream${suffix}`, {
    withCredentials: true,
  });
  const listener = (message: Event) => {
    try {
      onEvent(JSON.parse((message as MessageEvent<string>).data) as ChatRunEvent);
    } catch {
      source.close();
    }
  };
  source.addEventListener('run.event', listener);
  return () => {
    source.removeEventListener('run.event', listener);
    source.close();
  };
}
