import type { AgentStreamConnection, AgentStreamHandle, AgentStreamConnectionState } from '../types/agent';

export type ParsedSseEvent<T> = {
  payload: T;
  eventId?: string;
  eventIds?: readonly string[];
  eventType?: string;
};

export type SseStreamOptions<T> = {
  path: string;
  eventTypes: readonly string[];
  lastEventId?: string;
  signal?: AbortSignal;
  maxAttempts?: number;
  reconnectDelayMs?: number;
  parseEvent?: (event: MessageEvent<string>, registeredType: string) => ParsedSseEvent<T>;
  onEvent: (eventType: string, payload: T, eventId: string) => void;
  isTerminal?: (eventType: string, payload: T) => boolean;
  onStateChange?: (connection: AgentStreamConnection) => void;
  onError?: (connection: AgentStreamConnection) => void;
};

const defaultParseEvent = <T>(event: MessageEvent<string>): ParsedSseEvent<T> => ({
  payload: JSON.parse(event.data) as T,
  eventId: event.lastEventId || undefined,
  eventIds: event.lastEventId ? [event.lastEventId] : [],
});

function normalizeEventIds(ids: readonly (string | undefined)[]) {
  return Array.from(new Set(ids.map((id) => id?.trim()).filter((id): id is string => Boolean(id))));
}

function resolveSseUrl(path: string) {
  const baseUrl = import.meta.env.DEV ? '' : ((import.meta.env.VITE_API_BASE_URL as string | undefined) ?? '');
  return `${baseUrl}${path}`;
}

/**
 * 为不同业务流统一管理 SSE 的重连、游标和终态生命周期。
 */
export function openSseStream<T>(options: SseStreamOptions<T>): AgentStreamHandle {
  const maxAttempts = Math.max(1, options.maxAttempts ?? 5);
  const reconnectDelayMs = Math.max(0, options.reconnectDelayMs ?? 500);
  const initialLastEventId = options.lastEventId?.trim() || undefined;
  const seenEventIds = new Set(initialLastEventId ? [initialLastEventId] : []);
  let source: EventSource | undefined;
  let reconnectTimer: number | undefined;
  let closed = false;
  let terminal = false;
  let connection: AgentStreamConnection = {
    state: 'connecting',
    attempt: 1,
    maxAttempts,
    lastEventId: initialLastEventId,
  };
  let removeAbortListener: () => void = () => undefined;

  const publishState = (state: AgentStreamConnectionState, patch: Partial<AgentStreamConnection> = {}) => {
    connection = { ...connection, ...patch, state };
    options.onStateChange?.(connection);
  };

  const closeSource = (target?: EventSource) => {
    const current = target ?? source;
    current?.close();
    if (!target || source === target) source = undefined;
  };

  const close = () => {
    if (closed) return;
    closed = true;
    terminal = true;
    if (reconnectTimer !== undefined) {
      window.clearTimeout(reconnectTimer);
      reconnectTimer = undefined;
    }
    closeSource();
    removeAbortListener();
    publishState('closed');
  };

  const abortListener = () => close();

  if (options.signal?.aborted) {
    closed = true;
    terminal = true;
    connection = { ...connection, state: 'closed' };
    options.onStateChange?.(connection);
    return { close, getConnection: () => connection };
  }

  if (options.signal) {
    options.signal.addEventListener('abort', abortListener, { once: true });
    removeAbortListener = () => {
      options.signal?.removeEventListener('abort', abortListener);
    };
  }

  const handleConnectionFailure = (failedSource?: EventSource) => {
    // 旧连接的延迟 error 或解析错误不能影响已经建立的新连接。
    if (closed || terminal || (failedSource && source !== failedSource)) return;
    closeSource(failedSource);
    if (connection.attempt >= maxAttempts) {
      terminal = true;
      removeAbortListener();
      publishState('exhausted');
      options.onError?.(connection);
      return;
    }
    if (reconnectTimer !== undefined) return;
    publishState('reconnecting', { attempt: connection.attempt + 1 });
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
      nextSource = new EventSource(`${resolveSseUrl(options.path)}${suffix}`, { withCredentials: true });
    } catch {
      handleConnectionFailure();
      return;
    }
    source = nextSource;
    nextSource.onopen = () => {
      if (closed || terminal || source !== nextSource) return;
      publishState('connected');
    };

    for (const registeredType of options.eventTypes) {
      nextSource.addEventListener(registeredType, (event) => {
        // EventSource.close() 后浏览器仍可能派发已排队的消息，必须丢弃旧连接事件。
        if (closed || terminal || source !== nextSource) return;
        const message = event as MessageEvent<string>;
        let parsed: ParsedSseEvent<T>;
        try {
          parsed = options.parseEvent?.(message, registeredType) ?? defaultParseEvent<T>(message);
        } catch {
          handleConnectionFailure(nextSource);
          return;
        }
        const eventIds = normalizeEventIds([parsed.eventId, message.lastEventId, ...(parsed.eventIds ?? [])]);
        const eventId = eventIds[0] ?? '';
        const duplicate = eventIds.some((id) => seenEventIds.has(id));
        eventIds.forEach((id) => seenEventIds.add(id));
        if (eventId && eventId !== connection.lastEventId) {
          // 游标变化必须同步给页面，保证下一次续接使用同一份 ID。
          publishState(connection.state, { lastEventId: eventId });
        }
        if (duplicate) return;
        const eventType = parsed.eventType?.trim() || registeredType;
        options.onEvent(eventType, parsed.payload, eventId);
        if (options.isTerminal?.(eventType, parsed.payload)) {
          // 终态统一走幂等关闭，避免业务回调或组件卸载先关闭时重复发布 closed。
          close();
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
