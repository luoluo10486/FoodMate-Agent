import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import type { AgentRunView } from '../types/agent';
import type { Message } from '../types/session';
import {
  type AgentCard,
  type AgentMode,
  type MockRunEvent,
  type ReplayStep,
  baseRun,
  buildPlanningContinuationSteps,
  buildSteps,
  createMessage,
  detectMode,
  initialMessages,
  seededPrompts
} from './agentReplayData';

export type UseMockAgentReplayState = {
  messages: Message[];
  run: AgentRunView;
  card: AgentCard;
  events: MockRunEvent[];
  running: boolean;
  input: string;
};

export function useMockAgentReplay(seedKey?: string, seedPrompt?: string | null): UseMockAgentReplayState & {
  setInput: (value: string) => void;
  send: (overridePrompt?: string) => void;
  stop: () => void;
  answerClarification: (value: string | Record<string, string>) => void;
  confirmWrite: () => void;
  handleResultPrimary: () => void;
  handleResultSecondary: () => void;
  editWrite: () => void;
  cancelWrite: () => void;
} {
  const [messages, setMessages] = useState<Message[]>(initialMessages);
  const [run, setRun] = useState<AgentRunView>(baseRun);
  const [card, setCard] = useState<AgentCard>({
    type: 'result',
    mode: 'planning',
    label: '计划草案',
    title: '3 天高蛋白备餐已生成，预算预计 286 元',
    description: '计划优先复用鸡胸肉、鸡蛋、豆腐和西兰花，降低采购成本和食材浪费。当前正在校验晚餐烹饪时间和蛋白质目标。',
    primaryAction: '确认保存',
    secondaryAction: '查看购物清单'
  });
  const [events, setEvents] = useState<MockRunEvent[]>([]);
  const [running, setRunning] = useState(false);
  const [input, setInput] = useState('');
  const timeoutsRef = useRef<number[]>([]);
  const seedTimeoutRef = useRef<number | null>(null);
  const streamMessageIdRef = useRef<string | null>(null);
  const seededRef = useRef(false);
  const lastPromptRef = useRef('');
  const lastConfirmationDataRef = useRef<Array<{ label: string; value: string }>>([]);

  const clearTimers = useCallback(() => {
    timeoutsRef.current.forEach((id) => window.clearTimeout(id));
    timeoutsRef.current = [];
    streamMessageIdRef.current = null;
  }, []);

  useEffect(() => clearTimers, [clearTimers]);

  useEffect(
    () => () => {
      if (seedTimeoutRef.current) {
        window.clearTimeout(seedTimeoutRef.current);
      }
    },
    []
  );

  const updateRun = useCallback((updater: (current: AgentRunView) => AgentRunView) => {
    setRun((current) => {
      const next = updater(current);
      const completedTools = next.toolCalls.filter((tool) => tool.status === 'success').length;
      return { ...next, toolsUsed: completedTools };
    });
  }, []);

  const appendRunEvent = useCallback((event: string) => {
    setEvents((current) => [
      ...current,
      {
        id: `${event}-${Date.now()}-${current.length}`,
        event: event as MockRunEvent['event'],
        createdAt: new Date().toISOString()
      }
    ]);
  }, []);

  const applyStep = useCallback(
    (step: ReplayStep) => {
      appendRunEvent(step.event);

      if (step.type === 'event') {
        return;
      }

      if (step.type === 'status') {
        updateRun((current) => ({ ...current, status: step.status }));
        if (step.status === 'completed' || step.status === 'failed' || step.status === 'cancelled' || step.status === 'waiting_user') {
          setRunning(false);
        }
        return;
      }

      if (step.type === 'message') {
        setMessages((current) => [...current, createMessage('assistant', step.content)]);
        return;
      }

      if (step.type === 'assistantStream') {
        const message = createMessage('assistant', '');
        streamMessageIdRef.current = message.id;
        setMessages((current) => [...current, message]);
        step.content.split('').forEach((char, index) => {
          const timeout = window.setTimeout(() => {
            setMessages((current) =>
              current.map((item) => (item.id === message.id ? { ...item, content: `${item.content}${char}` } : item))
            );
          }, index * (step.chunkMs ?? 18));
          timeoutsRef.current.push(timeout);
        });
        return;
      }

      if (step.type === 'tool') {
        updateRun((current) => ({ ...current, toolCalls: [...current.toolCalls, step.tool] }));
        return;
      }

      if (step.type === 'toolUpdate') {
        updateRun((current) => ({
          ...current,
          toolCalls: current.toolCalls.map((tool) => (tool.id === step.id ? { ...tool, ...step.patch } : tool))
        }));
        return;
      }

      if (step.type === 'citation') {
        updateRun((current) => ({ ...current, citations: [...current.citations, step.citation] }));
        return;
      }

      if (step.type === 'card') {
        if (step.card.type === 'confirmation') {
          lastConfirmationDataRef.current = step.card.data;
        }
        setCard(step.card);
      }
    },
    [appendRunEvent, updateRun]
  );

  const scheduleSteps = useCallback(
    (steps: ReplayStep[]) => {
      let delay = 360;
      steps.forEach((step) => {
        const timeout = window.setTimeout(() => applyStep(step), delay);
        timeoutsRef.current.push(timeout);

        if (step.type === 'assistantStream') {
          delay += step.content.length * (step.chunkMs ?? 18) + 420;
        } else {
          delay += 620;
        }
      });
    },
    [applyStep]
  );

  const send = useCallback(
    (overridePrompt?: string) => {
      const prompt = (overridePrompt ?? input).trim();
      if (!prompt || running) return;

      clearTimers();
      const mode = detectMode(prompt);
      lastPromptRef.current = prompt;
      setMessages((current) => [...current, createMessage('user', prompt)]);
      setInput('');
      setCard({ type: 'none' });
      setEvents([]);
      setRunning(true);
      setRun({ ...baseRun, id: `run-${Date.now()}`, status: 'routing', intent: mode, toolCalls: [], citations: [], toolsUsed: 0 });

      scheduleSteps(buildSteps(mode, prompt));
    },
    [clearTimers, input, running, scheduleSteps]
  );

  useEffect(() => {
    if (seededRef.current) return;
    const seeded = seedPrompt?.trim() || (seedKey ? seededPrompts[seedKey] : '');
    if (!seeded) return;

    seedTimeoutRef.current = window.setTimeout(() => {
      if (seededRef.current) return;
      seededRef.current = true;
      send(seeded);
    }, 80);
  }, [seedKey, seedPrompt, send]);

  const stop = useCallback(() => {
    clearTimers();
    setRunning(false);
    updateRun((current) => ({
      ...current,
      status: 'cancelled',
      toolCalls: current.toolCalls.map((tool) => (tool.status === 'running' ? { ...tool, status: 'cancelled' } : tool))
    }));
    setMessages((current) => [...current, createMessage('assistant', '已停止当前任务，保留现有上下文，可以随时继续。')]);
    appendRunEvent('run.cancelled');
    setCard({ type: 'none' });
  }, [appendRunEvent, clearTimers, updateRun]);

  const answerClarification = useCallback(
    (value: string | Record<string, string>) => {
      const answers =
        typeof value === 'string'
          ? { budget: value, dislikes: '不吃猪肉', goal: '目标高蛋白' }
          : value;
      const summary = `预算：${answers.budget}；忌口：${answers.dislikes}；目标：${answers.goal}`;

      clearTimers();
      setMessages((current) => [...current, createMessage('user', summary)]);
      setInput('');
      setCard({ type: 'none' });
      setRunning(true);
      updateRun((current) => ({ ...current, status: 'planning' }));
      scheduleSteps(buildPlanningContinuationSteps(summary));
    },
    [clearTimers, scheduleSteps, updateRun]
  );

  const confirmWrite = useCallback(() => {
    setCard({ type: 'none' });
    setMessages((current) => [...current, createMessage('assistant', '已模拟保存这条午餐记录。真实接入后会返回 `food_log_id`，并可在饮食日志中查询。')]);
    updateRun((current) => ({ ...current, status: 'completed' }));
    appendRunEvent('run.completed');
  }, [appendRunEvent, updateRun]);

  const editWrite = useCallback(() => {
    setInput('把鸡胸肉改成 180g，米饭 120g');
    setMessages((current) => [...current, createMessage('assistant', '可以，已把修改建议放到输入框，发送后我会重新估算。')]);
  }, []);

  const handleResultPrimary = useCallback(() => {
    if (card.type !== 'result') return;

    setCard({ type: 'none' });
    if (card.mode === 'planning') {
      setMessages((current) => [...current, createMessage('assistant', '已模拟保存这个备餐计划。真实接入后会生成 `meal_plan_id`，并同步到"饮食管理"页面。')]);
      updateRun((current) => ({ ...current, status: 'completed' }));
      appendRunEvent('run.completed');
      return;
    }

    if (card.mode === 'record') {
      setCard({
        type: 'confirmation',
        title: '重新确认写入内容',
        helperText: '这是重新打开的确认卡，仍然不会写入真实后端。',
        data: lastConfirmationDataRef.current
      });
      updateRun((current) => ({ ...current, status: 'waiting_user' }));
      return;
    }

    if (card.mode === 'analysis') {
      window.location.assign('/analysis');
      return;
    }

    if (card.mode === 'calculation') {
      send('帮我记录今天吃了 20 克鸡胸肉');
    }
  }, [appendRunEvent, card, send, updateRun]);

  const handleResultSecondary = useCallback(() => {
    if (card.type !== 'result') return;

    if (card.mode === 'planning') {
      window.location.assign('/planning');
      return;
    }

    if (card.mode === 'record') {
      editWrite();
      return;
    }

    if (card.mode === 'calculation') {
      setInput('再计算 100 克豆腐的蛋白质');
      return;
    }

    setCard({ type: 'none' });
    send('按当前蛋白质缺口生成补充计划，高蛋白，预算 100 元以内');
  }, [card, editWrite, send]);

  const cancelWrite = useCallback(() => {
    setInput(lastPromptRef.current);
    setMessages((current) => [...current, createMessage('assistant', '已取消写入，没有保存任何饮食记录。')]);
    updateRun((current) => ({ ...current, status: 'cancelled' }));
    appendRunEvent('run.cancelled');
    setCard({
      type: 'result',
      mode: 'record',
      label: '已取消写入',
      title: '没有保存任何饮食记录',
      description: '已保留原始输入，可以重新确认写入，或继续修改后再发送。',
      primaryAction: '重新确认写入',
      secondaryAction: '继续修改'
    });
  }, [appendRunEvent, updateRun]);

  return useMemo(
    () => ({
      messages,
      run,
      card,
      events,
      running,
      input,
      setInput,
      send,
      stop,
      answerClarification,
      confirmWrite,
      handleResultPrimary,
      handleResultSecondary,
      editWrite,
      cancelWrite
    }),
    [
      answerClarification, cancelWrite, card, confirmWrite, editWrite, events,
      handleResultPrimary, handleResultSecondary, input, messages, run, running, send, stop
    ]
  );
}
