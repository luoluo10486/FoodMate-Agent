import { useCallback, useEffect, useRef, useState } from 'react';
import type { AgentRunView, AgentDisplayStatus } from '../types/agent';
import type { Message } from '../types/session';
import type { AgentCard } from '../mock/agentReplayData';
import {
  cancelChatRun,
  createChatRun,
  getChatRun,
  getChatRunEvents,
  streamChatRun,
  type ChatRunEvent,
} from './chatApi';

const emptyRun = (id = 'real-run'): AgentRunView => ({
  id,
  status: 'routing',
  intent: 'analysis',
  toolsUsed: 0,
  toolsTotal: 0,
  agentsUsed: 1,
  agentsTotal: 1,
  toolCalls: [],
  citations: [],
});

function statusFor(event: ChatRunEvent): AgentDisplayStatus {
  if (event.state === 'SUCCEEDED') return 'completed';
  if (event.state === 'FAILED') return 'failed';
  if (event.state === 'CANCELED') return 'cancelled';
  if (event.state === 'RUNNING') return 'executing_tools';
  return 'routing';
}

export function useRealAgentReplay(enabled: boolean, sessionId?: string, seedPrompt?: string | null) {
  const [messages, setMessages] = useState<Message[]>([]);
  const [run, setRun] = useState<AgentRunView>(emptyRun());
  const [events, setEvents] = useState<ChatRunEvent[]>([]);
  const [running, setRunning] = useState(false);
  const [input, setInput] = useState('');
  const [card, setCard] = useState<AgentCard>({ type: 'none' });
  const runIdRef = useRef<string>();
  const stopStreamRef = useRef<(() => void) | undefined>(undefined);
  const seededRef = useRef(false);

  const refresh = useCallback(async (runId: string) => {
    const [status, nextEvents] = await Promise.all([getChatRun(runId), getChatRunEvents(runId)]);
    setEvents(nextEvents);
    const latest = nextEvents.at(-1);
    const displayStatus = latest ? statusFor(latest) : status.status === 'DISPATCHED' ? 'routing' : 'executing_tools';
    setRun((current) => ({ ...current, id: runId, status: displayStatus }));
    const payload = latest?.payload as { answer?: unknown } | undefined;
    if (payload?.answer !== undefined) {
      setMessages((current) => [
        ...current.filter((message) => message.id !== `answer-${runId}`),
        {
          id: `answer-${runId}`,
          role: 'assistant',
          content: String(payload.answer),
          time: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
        },
      ]);
    }
    if (['completed', 'failed', 'cancelled'].includes(displayStatus)) {
      setRunning(false);
      if (displayStatus === 'failed') setCard({ type: 'error', message: '运行失败，请稍后重试' });
      return true;
    }
    return false;
  }, []);

  const send = useCallback(
    async (overridePrompt?: string) => {
      if (!enabled) return;
      const prompt = (overridePrompt ?? input).trim();
      if (!prompt || running) return;
      setInput('');
      setMessages((current) => [
        ...current,
        {
          id: `user-${Date.now()}`,
          role: 'user',
          content: prompt,
          time: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
        },
      ]);
      setRunning(true);
      setCard({ type: 'none' });
      try {
        const started = await createChatRun(prompt, sessionId);
        runIdRef.current = started.run_id;
        setRun(emptyRun(started.run_id));
        const initialDone = await refresh(started.run_id);
        if (!initialDone) {
          let latestEventId = 0;
          stopStreamRef.current = streamChatRun(
            started.run_id,
            (event) => {
              latestEventId = event.event_seq;
              setEvents((current) => [...current.filter((item) => item.event_id !== event.event_id), event]);
              setRun((current) => ({ ...current, status: statusFor(event) }));
              const payload = event.payload as { answer?: unknown } | undefined;
              if (payload?.answer !== undefined)
                setMessages((current) => [
                  ...current.filter((message) => message.id !== `answer-${started.run_id}`),
                  {
                    id: `answer-${started.run_id}`,
                    role: 'assistant',
                    content: String(payload.answer),
                    time: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
                  },
                ]);
              if (['SUCCEEDED', 'FAILED', 'CANCELED'].includes(event.state)) {
                setRunning(false);
                stopStreamRef.current?.();
              }
            },
            latestEventId,
          );
        }
      } catch (error) {
        setRunning(false);
        setCard({ type: 'error', message: error instanceof Error ? error.message : '请求失败' });
      }
    },
    [enabled, input, refresh, running, sessionId],
  );

  useEffect(
    () => () => {
      stopStreamRef.current?.();
    },
    [],
  );
  // Seeded URLs should start exactly one real run per mounted session.
  useEffect(() => {
    if (enabled && seedPrompt && !seededRef.current) {
      seededRef.current = true;
      void send(seedPrompt);
    }
  }, [enabled, seedPrompt, send]);

  return {
    messages,
    run,
    card,
    events,
    running,
    input,
    setInput,
    send,
    stop: () => {
      const runId = runIdRef.current;
      if (runId) void cancelChatRun(runId).finally(() => stopStreamRef.current?.());
      setRunning(false);
    },
    answerClarification: () => {},
    confirmWrite: () => {},
    handleResultPrimary: () => {},
    handleResultSecondary: () => {},
    editWrite: () => {},
    cancelWrite: () => {},
  };
}
