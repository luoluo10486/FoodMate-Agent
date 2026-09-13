import { useCallback, useEffect, useRef, useState } from 'react';
import type {
  AgentDisplayStatus,
  AgentRunView,
  AgentStreamConnection,
  AgentStreamHandle,
  ToolCall,
} from '../types/agent';
import type { Message } from '../types/session';
import type { AgentCard } from '../mock/agentReplayData';
import { flattenAgentEventPayload, resolveAgentEventType } from '../lib/agentEvent';
import { loadSessionMessages } from './sessionService';
import {
  cancelChatRun,
  createChatRun,
  getChatRun,
  getChatRunEvents,
  streamChatRun,
  type ChatRunEvent,
} from './chatApi';

const terminalStatuses = new Set(['SUCCEEDED', 'FAILED', 'CANCELED', 'CANCELLED', 'SUPERSEDED']);

const emptyRun = (id = 'real-run'): AgentRunView => ({
  id,
  status: 'routing',
  intent: 'knowledge_qna',
  toolsUsed: 0,
  toolsTotal: 0,
  agentsUsed: 1,
  agentsTotal: 1,
  toolCalls: [],
  citations: [],
});

export type RealAgentReplayOptions = {
  onSessionCreated?: (sessionId: string) => void;
  maxAttempts?: number;
  reconnectDelayMs?: number;
};

type RecordValue = Record<string, unknown>;

function isRecord(value: unknown): value is RecordValue {
  return typeof value === 'object' && value !== null;
}

function stringValue(value: unknown) {
  return typeof value === 'string' ? value : value === undefined || value === null ? '' : String(value);
}

function eventPayload(event: ChatRunEvent): RecordValue {
  return flattenAgentEventPayload(event as unknown as RecordValue);
}

function eventTypeFor(event: ChatRunEvent) {
  const eventRecord = event as unknown as Record<string, unknown>;
  const resolvedType = resolveAgentEventType(event.event_type, {
    ...eventRecord,
    payload: event.payload,
    state: event.state,
  });
  if (resolvedType !== 'run.event') return resolvedType;
  const payload = eventPayload(event);
  if (payload.text !== undefined) return 'run.answer_stream';
  return 'run.event';
}

function displayStatusFor(eventType: string, event: ChatRunEvent): AgentDisplayStatus {
  if (eventType === 'run.completed' || event.state === 'SUCCEEDED') return 'completed';
  if (eventType === 'run.failed' || event.state === 'FAILED') return 'failed';
  if (eventType === 'run.cancelled' || event.state === 'CANCELED' || event.state === 'CANCELLED') return 'cancelled';
  if (eventType === 'run.superseded' || event.state === 'SUPERSEDED') return 'superseded';
  if (eventType === 'run.accepted' || eventType === 'run.created') return 'routing';
  if (eventType === 'run.planned') return 'planning';
  if (
    eventType === 'run.retrieval_started' ||
    eventType === 'run.retrieval_finished' ||
    eventType === 'run.context_assembled'
  )
    return 'retrieving';
  if (eventType === 'run.tool_started' || eventType === 'run.tool_finished') return 'executing_tools';
  if (eventType === 'run.eval_decided') return 'validating';
  if (eventType === 'run.model_usage' || eventType === 'run.answer_stream') return 'composing';
  if (eventType === 'run.clarification_requested' || eventType === 'run.checkpoint_saved') return 'waiting_user';
  const status = stringValue(event.state).toUpperCase();
  if (status === 'DISPATCHED' || status === 'QUEUED') return 'routing';
  if (status === 'RUNNING') return 'executing_tools';
  return 'routing';
}

function isTerminalEvent(eventType: string, event: ChatRunEvent) {
  return (
    ['run.completed', 'run.failed', 'run.cancelled', 'run.superseded'].includes(eventType) ||
    terminalStatuses.has(event.state.toUpperCase())
  );
}

function isTerminalStatus(status: string) {
  return terminalStatuses.has(status.toUpperCase());
}

function eventCursor(event: ChatRunEvent) {
  if (event.sse_event_id) return event.sse_event_id;
  if (event.event_id) return event.event_id;
  return event.event_seq > 0 ? String(event.event_seq) : undefined;
}

function eventIdentities(event: ChatRunEvent) {
  return [event.sse_event_id, event.event_id, event.event_seq > 0 ? String(event.event_seq) : undefined].filter(
    (value): value is string => Boolean(value?.trim()),
  );
}

function normalizeIntent(value: unknown): AgentRunView['intent'] {
  if (value === 'calculation' || value === 'record' || value === 'analysis' || value === 'planning') return value;
  return 'knowledge_qna';
}

function toolStatus(value: unknown): ToolCall['status'] {
  const status = stringValue(value).toLowerCase();
  if (['succeeded', 'success', 'completed'].includes(status)) return 'success';
  if (['confirmation_required', 'pending'].includes(status)) return 'pending';
  if (['timeout', 'timed_out'].includes(status)) return 'timeout';
  if (['cancelled', 'canceled'].includes(status)) return 'cancelled';
  if (['failed', 'error'].includes(status)) return 'failed';
  return 'running';
}

function mergeToolCall(current: ToolCall[], event: ChatRunEvent, phase: 'started' | 'finished') {
  const payload = eventPayload(event);
  const id =
    stringValue(payload.proposal_id) ||
    stringValue(payload.invocation_id) ||
    stringValue(payload.tool_name) ||
    `tool-${event.event_seq || event.event_id || Date.now()}`;
  const name = stringValue(payload.tool_name) || stringValue(payload.tool_type) || 'unknown_tool';
  const index = current.findIndex((tool) => tool.id === id);
  const next: ToolCall = {
    id,
    name,
    displayName: name,
    status: phase === 'started' ? 'running' : toolStatus(payload.status),
    latencyMs: typeof payload.latency_ms === 'number' ? payload.latency_ms : undefined,
    summary:
      phase === 'started'
        ? '正在执行'
        : stringValue(payload.error_code)
          ? `执行失败：${stringValue(payload.error_code)}`
          : toolStatus(payload.status) === 'pending'
            ? '等待确认'
            : '已完成',
    error: stringValue(payload.error_code) || undefined,
  };
  if (index < 0) return [...current, next];
  return current.map((tool, itemIndex) => (itemIndex === index ? { ...tool, ...next } : tool));
}

function mapCitations(value: unknown): AgentRunView['citations'] {
  if (!Array.isArray(value)) return [];
  return value.filter(isRecord).map((citation, index) => ({
    id: stringValue(citation.citation_id) || stringValue(citation.id) || `citation-${index}`,
    title: stringValue(citation.title) || '未命名来源',
    snippet: stringValue(citation.snippet),
    source: [stringValue(citation.version), stringValue(citation.section_path)].filter(Boolean).join(' · '),
  }));
}

function eventErrorMessage(event: ChatRunEvent) {
  const payload = eventPayload(event);
  return (
    stringValue(payload.error_message) ||
    stringValue(payload.message) ||
    stringValue(payload.error_code) ||
    'ChatRun 执行失败，请稍后重试。'
  );
}

function sortEvents(events: ChatRunEvent[]) {
  return [...events].sort((left, right) => {
    if (left.event_seq !== right.event_seq) return left.event_seq - right.event_seq;
    return String(left.occurred_at).localeCompare(String(right.occurred_at));
  });
}

export function useRealAgentReplay(
  enabled: boolean,
  sessionId?: string,
  seedPrompt?: string | null,
  options: RealAgentReplayOptions = {},
) {
  const [messages, setMessages] = useState<Message[]>([]);
  const [run, setRun] = useState<AgentRunView>(emptyRun());
  const [events, setEvents] = useState<ChatRunEvent[]>([]);
  const [running, setRunning] = useState(false);
  const [loading, setLoading] = useState(Boolean(enabled && sessionId));
  const [input, setInput] = useState('');
  const [card, setCard] = useState<AgentCard>({ type: 'none' });
  const [error, setError] = useState<string>();
  const [cancelling, setCancelling] = useState(false);
  const [cancelAcknowledged, setCancelAcknowledged] = useState(false);
  const [connection, setConnection] = useState<AgentStreamConnection>({
    state: 'closed',
    attempt: 0,
    maxAttempts: options.maxAttempts ?? 5,
  });
  const [assistantText, setAssistantText] = useState('');
  const [assistantTime, setAssistantTime] = useState('');
  const [activeRunId, setActiveRunId] = useState<string>();
  const streamRef = useRef<AgentStreamHandle>();
  const activeRunIdRef = useRef<string>();
  const lastEventIdRef = useRef<string>();
  const eventIdentitiesRef = useRef(new Set<string>());
  const eventsRef = useRef<ChatRunEvent[]>([]);
  const persistedAssistantRef = useRef(false);
  const seededRef = useRef(false);
  const mountedRef = useRef(true);
  const sessionGenerationRef = useRef(0);
  const maxAttempts = Math.max(1, options.maxAttempts ?? 5);

  const publishConnection = useCallback((nextConnection: AgentStreamConnection) => {
    setConnection(nextConnection);
    setRun((current) => ({ ...current, connection: nextConnection }));
  }, []);

  const closeStream = useCallback(() => {
    const current = streamRef.current;
    streamRef.current = undefined;
    current?.close();
  }, []);

  const applyEvent = useCallback((eventType: string, event: ChatRunEvent) => {
    const payload = eventPayload(event);
    const status = displayStatusFor(eventType, event);
    setRun((current) => {
      const next = { ...current, status };
      if (eventType === 'run.routed') next.intent = normalizeIntent(payload.intent);
      if (eventType === 'run.tool_started') next.toolCalls = mergeToolCall(current.toolCalls, event, 'started');
      if (eventType === 'run.tool_finished') next.toolCalls = mergeToolCall(current.toolCalls, event, 'finished');
      if (eventType === 'run.completed' && !stringValue(payload.result_type).includes('degraded'))
        next.citations = mapCitations(payload.citations);
      return next;
    });

    if (eventType === 'run.answer_stream') {
      const text = stringValue(payload.text);
      if (!persistedAssistantRef.current && text) {
        setAssistantText((current) => current + text);
        setAssistantTime((current) => current || new Date().toISOString());
      }
      return;
    }
    if (eventType === 'run.completed') {
      setRunning(false);
      setCancelling(false);
      setCancelAcknowledged(false);
      setCard({ type: 'none' });
      setAssistantTime((current) => current || new Date().toISOString());
      const answer = stringValue(payload.answer);
      if (!persistedAssistantRef.current && answer) setAssistantText(answer);
      return;
    }
    if (eventType === 'run.failed') {
      setRunning(false);
      setCancelling(false);
      setCancelAcknowledged(false);
      const message = eventErrorMessage(event);
      setError(message);
      setCard({ type: 'error', message });
      return;
    }
    if (eventType === 'run.cancelled') {
      setRunning(false);
      setCancelling(false);
      setCancelAcknowledged(false);
      return;
    }
    if (eventType === 'run.superseded') {
      setRunning(false);
      setCancelling(false);
      setCancelAcknowledged(false);
      return;
    }
    if (eventType === 'run.cancel_acknowledged') setCancelAcknowledged(true);
  }, []);

  const acceptEvent = useCallback(
    (event: ChatRunEvent) => {
      if (activeRunIdRef.current && String(event.run_id) !== String(activeRunIdRef.current)) return false;
      const identities = eventIdentities(event);
      if (identities.some((identity) => eventIdentitiesRef.current.has(identity))) return false;
      identities.forEach((identity) => eventIdentitiesRef.current.add(identity));
      const nextEvents = sortEvents([...eventsRef.current, event]);
      eventsRef.current = nextEvents;
      setEvents(nextEvents);
      const eventType = eventTypeFor(event);
      applyEvent(eventType, event);
      const cursor = eventCursor(event);
      if (cursor) lastEventIdRef.current = cursor;
      if (isTerminalEvent(eventType, event)) setRunning(false);
      return true;
    },
    [applyEvent],
  );

  const openStream = useCallback(
    (runId: string, cursor?: string, preserveContent = false) => {
      closeStream();
      if (!preserveContent) {
        setAssistantText('');
        setAssistantTime('');
      }
      const stream = streamChatRun(
        runId,
        (event) => {
          if (!mountedRef.current || activeRunIdRef.current !== runId) return;
          acceptEvent(event);
        },
        cursor,
        {
          maxAttempts,
          reconnectDelayMs: options.reconnectDelayMs,
          onStateChange: (nextConnection) => {
            if (!mountedRef.current || activeRunIdRef.current !== runId) return;
            publishConnection(nextConnection);
            if (nextConnection.state === 'exhausted') {
              setRunning(false);
              setCancelling(false);
              setError('SSE 连接重试已耗尽，请手动重新连接。');
            }
          },
          onError: (nextConnection) => {
            if (!mountedRef.current || activeRunIdRef.current !== runId) return;
            if (nextConnection.state === 'exhausted') setRunning(false);
          },
        },
      );
      streamRef.current = stream;
      return stream;
    },
    [acceptEvent, closeStream, maxAttempts, options.reconnectDelayMs, publishConnection],
  );

  const refreshRun = useCallback(
    async (runId: string, preserveContent = false) => {
      const [status, history] = await Promise.all([getChatRun(runId), getChatRunEvents(runId)]);
      if (!mountedRef.current || activeRunIdRef.current !== runId) return false;
      closeStream();
      eventIdentitiesRef.current = new Set();
      eventsRef.current = [];
      setEvents([]);
      if (!preserveContent) {
        setAssistantText('');
        setAssistantTime('');
      }
      setRun(emptyRun(runId));
      const ordered = sortEvents(history);
      ordered.forEach((event) => acceptEvent(event));
      const latestWithCursor = [...ordered].reverse().find((event) => eventCursor(event));
      lastEventIdRef.current = latestWithCursor ? eventCursor(latestWithCursor) : undefined;
      const terminal =
        isTerminalStatus(status.status) || ordered.some((event) => isTerminalEvent(eventTypeFor(event), event));
      if (terminal) {
        setRunning(false);
        publishConnection({
          state: 'closed',
          attempt: 0,
          maxAttempts,
          lastEventId: lastEventIdRef.current,
        });
        return true;
      }
      setRunning(true);
      openStream(runId, lastEventIdRef.current, preserveContent);
      return false;
    },
    [acceptEvent, closeStream, maxAttempts, openStream, publishConnection],
  );

  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
      closeStream();
    };
  }, [closeStream]);

  useEffect(() => {
    const generation = ++sessionGenerationRef.current;
    seededRef.current = false;
    closeStream();
    activeRunIdRef.current = undefined;
    eventIdentitiesRef.current = new Set();
    eventsRef.current = [];
    persistedAssistantRef.current = false;
    // 会话切换必须先清理旧运行状态，再加载新会话的消息和最近 Run。
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setActiveRunId(undefined);
    setMessages([]);
    setEvents([]);
    setRun(emptyRun());
    setRunning(false);
    setCancelling(false);
    setCancelAcknowledged(false);
    setAssistantText('');
    setAssistantTime('');
    setCard({ type: 'none' });
    setError(undefined);
    publishConnection({ state: 'closed', attempt: 0, maxAttempts });
    if (!enabled || !sessionId) {
      setLoading(false);
      return undefined;
    }
    setLoading(true);
    let cancelled = false;
    loadSessionMessages(sessionId)
      .then((rows) => {
        if (cancelled || generation !== sessionGenerationRef.current || !mountedRef.current) return;
        const ordered = [...rows].sort((left, right) => left.sequence_no - right.sequence_no);
        setMessages(
          ordered.map((message) => ({
            id: message.message_id,
            role: message.role,
            content: message.content,
            time: message.created_at,
          })),
        );
        persistedAssistantRef.current = ordered.some((message) => message.role === 'assistant');
        const latestRunId = [...ordered].reverse().find((message) => message.agent_run_id)?.agent_run_id;
        if (latestRunId) {
          const normalizedRunId = String(latestRunId);
          activeRunIdRef.current = normalizedRunId;
          setActiveRunId(normalizedRunId);
        }
      })
      .catch((reason) => {
        if (!cancelled && generation === sessionGenerationRef.current && mountedRef.current)
          setError(reason instanceof Error ? reason.message : 'ChatRun 消息加载失败。');
      })
      .finally(() => {
        if (!cancelled && generation === sessionGenerationRef.current && mountedRef.current) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [closeStream, enabled, maxAttempts, publishConnection, sessionId]);

  useEffect(() => {
    if (!enabled || !activeRunId) return undefined;
    let cancelled = false;
    // 状态查询会启动外部 SSE 订阅，并在回调中同步 React 状态。
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void refreshRun(activeRunId).catch((reason) => {
      if (!cancelled && mountedRef.current) {
        setRunning(false);
        setError(reason instanceof Error ? reason.message : 'ChatRun 状态加载失败。');
      }
    });
    return () => {
      cancelled = true;
      closeStream();
    };
  }, [activeRunId, closeStream, enabled, refreshRun]);

  const send = useCallback(
    async (overridePrompt?: string) => {
      if (!enabled) return;
      const prompt = (overridePrompt ?? input).trim();
      if (!prompt || running || cancelling) return;
      setInput('');
      setError(undefined);
      setCard({ type: 'none' });
      setRunning(true);
      try {
        const started = await createChatRun(prompt, sessionId);
        if (!mountedRef.current) return;
        const targetSessionId = started.session_id;
        const optimisticMessage: Message = {
          id: started.user_message_id ?? `chat-run-user-${Date.now()}`,
          role: 'user',
          content: prompt,
          time: new Date().toISOString(),
        };
        setMessages((current) => [...current, optimisticMessage]);
        persistedAssistantRef.current = false;
        if (!sessionId && targetSessionId) options.onSessionCreated?.(targetSessionId);
        const normalizedRunId = String(started.run_id);
        activeRunIdRef.current = normalizedRunId;
        eventIdentitiesRef.current = new Set();
        eventsRef.current = [];
        lastEventIdRef.current = undefined;
        setEvents([]);
        setAssistantText('');
        setAssistantTime('');
        setRun(emptyRun(normalizedRunId));
        setActiveRunId(normalizedRunId);
      } catch (reason) {
        if (!mountedRef.current) return;
        setRunning(false);
        const message = reason instanceof Error ? reason.message : 'ChatRun 创建失败，请稍后重试。';
        setError(message);
        setCard({ type: 'error', message });
      }
    },
    [cancelling, enabled, input, options, running, sessionId],
  );

  useEffect(() => {
    if (!enabled || !seedPrompt || seededRef.current || loading) return;
    seededRef.current = true;
    void send(seedPrompt);
  }, [enabled, loading, seedPrompt, send]);

  const stop = useCallback(() => {
    const runId = activeRunIdRef.current;
    if (!runId || cancelling || !running) return;
    const currentStream = streamRef.current;
    const cursor = currentStream?.getConnection().lastEventId ?? lastEventIdRef.current;
    closeStream();
    lastEventIdRef.current = cursor;
    setCancelling(true);
    setRunning(false);
    setCancelAcknowledged(false);
    setError(undefined);
    void cancelChatRun(runId)
      .then(() => {
        if (!mountedRef.current || activeRunIdRef.current !== runId) return;
        // 取消 HTTP 响应只代表请求被接受，必须重新订阅并等待 cancelled 事件。
        openStream(runId, cursor, true);
      })
      .catch((reason) => {
        if (!mountedRef.current || activeRunIdRef.current !== runId) return;
        setCancelling(false);
        setRunning(true);
        setError(reason instanceof Error ? reason.message : '取消 ChatRun 失败，请稍后重试。');
        openStream(runId, cursor, true);
      });
  }, [cancelling, closeStream, openStream, running]);

  const reconnect = useCallback(() => {
    const runId = activeRunIdRef.current;
    if (!runId || connection.state !== 'exhausted') return;
    setError(undefined);
    setRunning(!cancelling);
    openStream(runId, connection.lastEventId ?? lastEventIdRef.current, true);
  }, [cancelling, connection.lastEventId, connection.state, openStream]);

  return {
    messages,
    run: { ...run, connection },
    card,
    events,
    running,
    loading,
    error,
    cancelling,
    cancelAcknowledged,
    assistantText,
    assistantTime,
    activeRunId,
    input,
    setInput,
    send,
    stop,
    reconnect,
    answerClarification: () => undefined,
    confirmWrite: () => undefined,
    handleResultPrimary: () => undefined,
    handleResultSecondary: () => undefined,
    editWrite: () => undefined,
    cancelWrite: () => undefined,
  };
}
