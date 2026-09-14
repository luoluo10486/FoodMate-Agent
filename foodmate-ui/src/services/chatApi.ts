export type ChatRun = {
  run_id: string;
  dispatch_id: string;
  status: string;
  duplicate: boolean;
  session_id?: string;
  user_message_id?: string;
};

export type ChatCancellationResult = {
  run_id: string;
  status: string;
  terminal?: boolean;
  command_id?: string;
  dispatch_id?: string;
  duplicate?: boolean;
};

type ChatRunPayload = Partial<ChatRun> & {
  runId?: string | number;
  dispatchId?: string;
  sessionId?: string | number;
  userMessageId?: string | number;
};

type ChatCancellationPayload = Partial<ChatCancellationResult> & {
  runId?: string | number;
  commandId?: string;
  dispatchId?: string;
};
import { apiRequest } from './apiClient';
import type { AgentStreamConnection, AgentStreamHandle } from '../types/agent';
import { openSseStream } from './sseStream';

export function createChatRun(prompt: string, sessionId?: string, signal?: AbortSignal): Promise<ChatRun> {
  return apiRequest<ChatRunPayload>('/api/chat/runs', {
    method: 'POST',
    signal,
    body: JSON.stringify({ prompt, session_id: sessionId }),
  }).then(normalizeChatRun);
}

export function getChatRun(runId: string, signal?: AbortSignal): Promise<{ run_id: string; status: string }> {
  return apiRequest<{ run_id?: string; runId?: string | number; status?: string }>(
    `/api/chat/runs/${encodeURIComponent(runId)}`,
    { signal },
  ).then((result) => ({
    run_id: String(result.run_id ?? result.runId ?? runId),
    status: String(result.status ?? ''),
  }));
}

export type ChatRunEvent = {
  event_id: string;
  run_id: string;
  event_seq: number;
  state: string;
  payload: unknown;
  occurred_at: string;
  event_type?: string;
  sse_event_id?: string;
};

export function getChatRunEvents(runId: string, signal?: AbortSignal): Promise<ChatRunEvent[]> {
  return apiRequest<ChatRunEvent[]>(`/api/chat/runs/${encodeURIComponent(runId)}/events`, { signal }).then((events) =>
    events.map((event) => ({
      ...event,
      event_id: String(event.event_id ?? ''),
      run_id: String(event.run_id ?? runId),
      event_seq: Number(event.event_seq ?? 0),
      state: String(event.state ?? ''),
    })),
  );
}

export async function cancelChatRun(runId: string, signal?: AbortSignal): Promise<ChatCancellationResult> {
  const result = await apiRequest<ChatCancellationPayload>(`/api/chat/runs/${encodeURIComponent(runId)}/cancel`, {
    method: 'POST',
    signal,
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

export type ChatStreamOptions = {
  maxAttempts?: number;
  reconnectDelayMs?: number;
  onStateChange?: (connection: AgentStreamConnection) => void;
  onError?: (connection: AgentStreamConnection) => void;
};

const chatEventTypes = [
  'run.event',
  'run.created',
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

export function streamChatRun(
  runId: string,
  onEvent: (event: ChatRunEvent) => void,
  lastEventId?: string | number,
  options: ChatStreamOptions = {},
): AgentStreamHandle {
  return openSseStream<ChatRunEvent>({
    path: `/api/chat/runs/${encodeURIComponent(runId)}/stream`,
    eventTypes: chatEventTypes,
    lastEventId: lastEventId === undefined ? undefined : String(lastEventId),
    maxAttempts: options.maxAttempts,
    reconnectDelayMs: options.reconnectDelayMs,
    onStateChange: options.onStateChange,
    onError: options.onError,
    parseEvent: (message, registeredType) => {
      const raw = JSON.parse(message.data) as Record<string, unknown>;
      const eventType = String(raw.event_type ?? raw.eventType ?? registeredType);
      const eventIds = [message.lastEventId, stringValue(raw.sse_event_id), stringValue(raw.event_id)].filter(
        (eventId): eventId is string => Boolean(eventId),
      );
      const eventId = eventIds[0] ?? '';
      return {
        payload: normalizeChatRunEvent(raw, runId, eventType, eventId),
        eventId,
        eventIds,
        eventType,
      };
    },
    onEvent: (_eventType, event) => onEvent(event),
    isTerminal: (eventType, event) =>
      ['run.completed', 'run.failed', 'run.cancelled', 'run.superseded'].includes(eventType) ||
      ['SUCCEEDED', 'FAILED', 'CANCELED', 'CANCELLED', 'SUPERSEDED'].includes(event.state),
  });
}

function normalizeChatRunEvent(
  raw: Record<string, unknown>,
  runId: string,
  eventType: string,
  eventId: string,
): ChatRunEvent {
  const sequence = Number(raw.event_seq ?? raw.eventSeq ?? (eventId && /^\d+$/.test(eventId) ? eventId : 0));
  return {
    event_id: stringValue(raw.event_id) || eventId,
    run_id: stringValue(raw.run_id) || stringValue(raw.runId) || runId,
    event_seq: Number.isFinite(sequence) ? sequence : 0,
    state: stringValue(raw.state) || stringValue(raw.status) || stateForEventType(eventType),
    payload: raw.payload ?? raw,
    occurred_at: stringValue(raw.occurred_at) || stringValue(raw.occurredAt) || '',
    event_type: eventType,
    sse_event_id: eventId || undefined,
  };
}

function normalizeChatRun(payload: ChatRunPayload): ChatRun {
  return {
    run_id: String(payload.run_id ?? payload.runId ?? ''),
    dispatch_id: String(payload.dispatch_id ?? payload.dispatchId ?? ''),
    status: String(payload.status ?? ''),
    duplicate: Boolean(payload.duplicate),
    ...((payload.session_id ?? payload.sessionId)
      ? { session_id: String(payload.session_id ?? payload.sessionId) }
      : {}),
    ...((payload.user_message_id ?? payload.userMessageId)
      ? { user_message_id: String(payload.user_message_id ?? payload.userMessageId) }
      : {}),
  };
}

function stringValue(value: unknown) {
  return typeof value === 'string' ? value : value === undefined || value === null ? '' : String(value);
}

function stateForEventType(eventType: string) {
  if (eventType === 'run.accepted') return 'DISPATCHED';
  if (eventType === 'run.completed') return 'SUCCEEDED';
  if (eventType === 'run.failed') return 'FAILED';
  if (eventType === 'run.cancelled') return 'CANCELED';
  return 'RUNNING';
}
