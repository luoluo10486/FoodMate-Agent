import { useCallback, useEffect, useRef, useState } from 'react';
import type { AgentRunView, AgentDisplayStatus } from '../types/agent';
import type { Message } from '../types/session';
import type { AgentCard } from '../mock/agentReplayData';
import { createChatRun, getChatRun, getChatRunEvents, type ChatRunEvent } from './chatApi';

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
  const timerRef = useRef<number | undefined>(undefined);
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
        const poll = async () => {
          if (!runIdRef.current) return;
          const done = await refresh(runIdRef.current);
          if (!done) timerRef.current = window.setTimeout(poll, 800);
        };
        await poll();
      } catch (error) {
        setRunning(false);
        setCard({ type: 'error', message: error instanceof Error ? error.message : '请求失败' });
      }
    },
    [enabled, input, refresh, running, sessionId],
  );

  useEffect(
    () => () => {
      if (timerRef.current) window.clearTimeout(timerRef.current);
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
      if (timerRef.current) window.clearTimeout(timerRef.current);
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
