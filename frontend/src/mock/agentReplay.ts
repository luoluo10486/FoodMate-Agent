import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import type { AgentDisplayStatus, AgentRunView, Citation, ToolCall } from '../types/agent';
import type { Message } from '../types/session';
import type { ClarificationField } from '../components/agent/ClarificationCard';

type AgentMode = 'planning' | 'record' | 'analysis' | 'knowledge_qna' | 'calculation';
type AgentCard =
  | { type: 'none' }
  | { type: 'clarification'; title: string; options?: string[]; fields?: ClarificationField[]; submitLabel?: string }
  | { type: 'confirmation'; title?: string; helperText?: string; data: Array<{ label: string; value: string }> }
  | {
      type: 'result';
      mode: AgentMode;
      label: string;
      title: string;
      description: string;
      primaryAction: string;
      secondaryAction: string;
    }
  | { type: 'error'; message: string };

type MockRunEventName =
  | 'run.created'
  | 'run.routed'
  | 'run.clarification_requested'
  | 'run.planned'
  | 'run.retrieval_started'
  | 'run.retrieval_finished'
  | 'run.tool_started'
  | 'run.tool_finished'
  | 'run.answer_stream'
  | 'run.completed'
  | 'run.failed'
  | 'run.cancelled';

type MockRunEvent = {
  id: string;
  event: MockRunEventName;
  createdAt: string;
};

type ReplayStepPayload =
  | { type: 'event'; event: MockRunEventName }
  | ({ type: 'status'; status: AgentDisplayStatus } & { event?: MockRunEventName })
  | ({ type: 'message'; content: string } & { event?: MockRunEventName })
  | ({ type: 'assistantStream'; content: string; chunkMs?: number } & { event?: MockRunEventName })
  | ({ type: 'tool'; tool: ToolCall } & { event?: MockRunEventName })
  | ({ type: 'toolUpdate'; id: string; patch: Partial<ToolCall> } & { event?: MockRunEventName })
  | ({ type: 'citation'; citation: Citation } & { event?: MockRunEventName })
  | ({ type: 'card'; card: AgentCard } & { event?: MockRunEventName });

type ReplayStep = ReplayStepPayload & { event: MockRunEventName };

type UseMockAgentReplayState = {
  messages: Message[];
  run: AgentRunView;
  card: AgentCard;
  events: MockRunEvent[];
  running: boolean;
  input: string;
};

const seededPrompts: Record<string, string> = {
  'protein-review': '分析我最近一周蛋白质摄入',
  'lunch-log': '帮我记录今天午餐',
  'week-plan': '给 2 个人制定一周高蛋白备餐计划，预算 300 元以内'
};

const baseRun: AgentRunView = {
  id: 'mock-run',
  status: 'completed',
  intent: 'planning',
  toolsUsed: 0,
  toolsTotal: 6,
  agentsUsed: 1,
  agentsTotal: 1,
  toolCalls: [],
  citations: []
};

const initialMessages: Message[] = [
  {
    id: 'm1',
    role: 'user',
    content: '给 2 个人制定一周高蛋白备餐计划，预算 300 元以内。',
    time: '09:31'
  },
  {
    id: 'm2',
    role: 'assistant',
    content: '我会先检索食材搭配和保存建议，再校验预算、蛋白质目标和烹饪时间。',
    time: '09:31'
  }
];

const nowTime = () => new Intl.DateTimeFormat('zh-CN', { hour: '2-digit', minute: '2-digit', hour12: false }).format(new Date());

function createMessage(role: Message['role'], content: string): Message {
  return {
    id: `${role}-${Date.now()}-${Math.random().toString(16).slice(2)}`,
    role,
    content,
    time: nowTime()
  };
}

function detectMode(prompt: string): AgentMode {
  if (/记录|午餐|早餐|晚餐|吃了/.test(prompt)) return 'record';
  if (/计算|卡路里|热量|多少|换算/.test(prompt)) return 'calculation';
  if (/分析|趋势|复盘|摄入/.test(prompt)) return 'analysis';
  if (/多久|怎么|含量|知识|焯水/.test(prompt)) return 'knowledge_qna';
  return 'planning';
}

function inferRunEvent(step: ReplayStepPayload): MockRunEventName {
  if (step.event) return step.event;

  if (step.type === 'status') {
    const statusEvents: Partial<Record<AgentDisplayStatus, MockRunEventName>> = {
      routing: 'run.routed',
      planning: 'run.planned',
      retrieving: 'run.retrieval_started',
      executing_tools: 'run.tool_started',
      validating: 'run.tool_started',
      composing: 'run.answer_stream',
      waiting_user: 'run.clarification_requested',
      completed: 'run.completed',
      failed: 'run.failed',
      cancelled: 'run.cancelled'
    };
    return statusEvents[step.status] ?? 'run.routed';
  }

  if (step.type === 'tool') return 'run.tool_started';
  if (step.type === 'toolUpdate') return step.id.includes('knowledge') ? 'run.retrieval_finished' : 'run.tool_finished';
  if (step.type === 'assistantStream') return 'run.answer_stream';
  if (step.type === 'citation') return 'run.retrieval_finished';
  if (step.type === 'card' && step.card.type === 'clarification') return 'run.clarification_requested';
  if (step.type === 'card' && step.card.type === 'error') return 'run.failed';
  return 'run.routed';
}

function withRunEvents(steps: ReplayStepPayload[]): ReplayStep[] {
  return steps.map((step) => ({ ...step, event: inferRunEvent(step) }));
}

function buildStepPayloads(mode: AgentMode, prompt: string): ReplayStepPayload[] {
  if (/失败|报错|error/i.test(prompt)) {
    return [
      { type: 'status', status: 'routing' },
      { type: 'message', content: '我会模拟一次失败路径，方便检查错误态。' },
      { type: 'status', status: 'executing_tools' },
      {
        type: 'tool',
        tool: {
          id: 'tool-database',
          name: 'database_query',
          displayName: '数据查询',
          status: 'running',
          summary: '正在查询近期饮食记录。',
          input: 'SELECT summary FROM food_logs WHERE range = recent',
          output: '等待数据库响应...'
        }
      },
      {
        type: 'toolUpdate',
        id: 'tool-database',
        patch: { status: 'failed', error: 'DATABASE_QUERY_TIMEOUT：查询超时，请稍后重试。', output: 'timeout after 3000ms' }
      },
      { type: 'status', status: 'failed' },
      { type: 'card', card: { type: 'error', message: 'DATABASE_QUERY_TIMEOUT：查询超时，请稍后重试。' } }
    ];
  }

  if (mode === 'calculation') {
    return [
      { type: 'status', status: 'routing' },
      { type: 'message', content: '我识别到这是营养计算任务，会用确定性换算工具给出结果。' },
      { type: 'status', status: 'executing_tools' },
      {
        type: 'tool',
        tool: {
          id: 'tool-calorie',
          name: 'calculator',
          displayName: '热量换算',
          status: 'running',
          summary: '正在按鸡胸肉常见营养数据换算 20g 的热量。',
          input: '鸡胸肉 20g · 约 165 kcal / 100g',
          output: '计算中...'
        }
      },
      {
        type: 'toolUpdate',
        id: 'tool-calorie',
        patch: {
          status: 'success',
          latencyMs: 140,
          summary: '计算完成：20g 鸡胸肉约 33 kcal，蛋白质约 6.2g。',
          output: '33 kcal · 蛋白质 6.2g · formula=165/100*20'
        }
      },
      { type: 'status', status: 'composing' },
      { type: 'assistantStream', content: '20 克熟鸡胸肉约 33 kcal，蛋白质约 6.2g。不同品牌和烹饪方式会有小幅差异，记录时可以按包装营养表优先修正。' },
      {
        type: 'card',
        card: {
          type: 'result',
          mode: 'calculation',
          label: '计算完成',
          title: '20g 鸡胸肉约 33 kcal',
          description: '按常见熟鸡胸肉 165 kcal/100g 换算，适合用于快速记录前的估算。',
          primaryAction: '记录这份食物',
          secondaryAction: '继续提问'
        }
      },
      { type: 'status', status: 'completed' }
    ];
  }

  if (mode === 'record') {
    return [
      { type: 'status', status: 'routing' },
      { type: 'message', content: '我识别到这是饮食记录任务，会先估算营养，再请你确认写入。' },
      { type: 'status', status: 'executing_tools' },
      {
        type: 'tool',
        tool: {
          id: 'tool-calculator',
          name: 'calculator',
          displayName: '营养估算',
          status: 'running',
          summary: '正在估算热量、蛋白质、碳水和脂肪。',
          input: '鸡胸肉 200g、米饭 150g、西兰花 120g',
          output: '估算中...'
        }
      },
      {
        type: 'toolUpdate',
        id: 'tool-calculator',
        patch: { status: 'success', latencyMs: 260, summary: '估算完成：约 620 kcal，蛋白质 54g。', output: '620 kcal · 蛋白质 54g · 碳水 59g · 脂肪 12g' }
      },
      { type: 'status', status: 'waiting_user' },
      {
        type: 'card',
        card: {
          type: 'confirmation',
          data: [
            { label: '餐型', value: '午餐' },
            { label: '食物', value: '鸡胸肉 200g、米饭 150g、西兰花 120g' },
            { label: '估算', value: '约 620 kcal · 蛋白质 54g' }
          ]
        }
      }
    ];
  }

  if (mode === 'analysis') {
    return [
      { type: 'status', status: 'routing' },
      { type: 'message', content: '我会解析时间范围，查询饮食日志，再汇总蛋白质趋势。' },
      { type: 'status', status: 'executing_tools' },
      {
        type: 'tool',
        tool: {
          id: 'tool-time',
          name: 'time_parser',
          displayName: '时间解析',
          status: 'running',
          summary: '正在解析“最近一周”。',
          input: '最近一周',
          output: '解析中...'
        }
      },
      { type: 'toolUpdate', id: 'tool-time', patch: { status: 'success', latencyMs: 120, summary: '解析为最近 7 天。', output: 'start=-7d · end=today · timezone=Asia/Shanghai' } },
      {
        type: 'tool',
        tool: {
          id: 'tool-db',
          name: 'database_query',
          displayName: '饮食日志查询',
          status: 'running',
          summary: '正在聚合蛋白质摄入。',
          input: 'group protein by day for recent 7 days',
          output: '聚合中...'
        }
      },
      { type: 'toolUpdate', id: 'tool-db', patch: { status: 'success', latencyMs: 390, summary: '聚合完成：7 天共 486g，目标达成 66%。', output: '42, 68, 54, 76, 82, 91, 73g · total=486g' } },
      { type: 'status', status: 'composing' },
      { type: 'assistantStream', content: '最近 7 天蛋白质摄入共 486g，按 70kg 体重的推荐区间 105-140g/天来看，当前达成约 66%。建议优先在早餐或加餐补充鸡蛋、豆腐、酸奶或鱼肉。' },
      {
        type: 'card',
        card: {
          type: 'result',
          mode: 'analysis',
          label: '分析完成',
          title: '最近 7 天蛋白质达成约 66%',
          description: '多数日期低于推荐摄入，建议把每日蛋白目标先提升到 105g 下限。',
          primaryAction: '查看分析页',
          secondaryAction: '生成补充计划'
        }
      },
      { type: 'status', status: 'completed' }
    ];
  }

  if (mode === 'knowledge_qna') {
    return [
      { type: 'status', status: 'retrieving' },
      { type: 'message', content: '我会先查知识库，再给出有引用的回答。' },
      {
        type: 'tool',
        tool: {
          id: 'tool-knowledge',
          name: 'knowledge_search',
          displayName: '知识检索',
          status: 'running',
          summary: '正在检索烹饪和营养知识。',
          input: '西兰花 焯水 多久',
          output: '检索中...'
        }
      },
      { type: 'toolUpdate', id: 'tool-knowledge', patch: { status: 'success', latencyMs: 310, summary: '命中 2 条烹饪建议。', output: 'top hit score=0.91 · 蔬菜焯水与营养保留指南' } },
      {
        type: 'citation',
        citation: {
          id: 'c-knowledge',
          title: '蔬菜焯水与营养保留指南',
          snippet: '西兰花焯水通常控制在 60-90 秒，出锅后过冷水有助于保持口感和颜色。',
          source: '内部知识库',
          score: 0.91
        }
      },
      { type: 'status', status: 'composing' },
      { type: 'assistantStream', content: '西兰花焯水建议控制在 60-90 秒。水开后下锅，加少量盐，出锅后快速过冷水，可以保持颜色和爽脆口感。' },
      { type: 'status', status: 'completed' }
    ];
  }

  if (!/预算|忌口|高蛋白|目标|不吃/.test(prompt)) {
    return [
      { type: 'status', status: 'routing' },
      { type: 'message', content: '我可以做备餐计划，不过需要先补齐关键约束。' },
      { type: 'status', status: 'waiting_user' },
      {
        type: 'card',
        card: {
          type: 'clarification',
          title: '为了生成可执行计划，请补充预算、忌口和目标',
          submitLabel: '继续生成计划',
          fields: [
            {
              key: 'budget',
              label: '预算',
              placeholder: '例如：300 元以内',
              quickOptions: ['300 元以内', '400 元以内']
            },
            {
              key: 'dislikes',
              label: '忌口',
              placeholder: '例如：不吃猪肉、少油',
              quickOptions: ['不吃猪肉', '少油少辣']
            },
            {
              key: 'goal',
              label: '目标',
              placeholder: '例如：高蛋白、控预算',
              quickOptions: ['目标高蛋白', '高蛋白 + 控预算']
            }
          ]
        }
      }
    ];
  }

  return [
    { type: 'status', status: 'routing' },
    { type: 'message', content: '我会先检索食材搭配和保存建议，再校验预算、蛋白质目标和烹饪时间。' },
    { type: 'status', status: 'planning' },
    { type: 'status', status: 'retrieving' },
    {
      type: 'tool',
      tool: {
        id: 'tool-knowledge',
        name: 'knowledge_search',
        displayName: '知识检索',
        status: 'running',
        summary: '正在检索高蛋白备餐和冷藏保存建议。',
        input: '高蛋白 备餐 冷藏 保存 预算',
        output: '检索中...'
      }
    },
    { type: 'toolUpdate', id: 'tool-knowledge', patch: { status: 'success', latencyMs: 420, summary: '命中食材复用、冷藏保存、预算控制 3 条参考。', output: '3 references · avg score=0.88' } },
    {
      type: 'citation',
      citation: {
        id: 'c-plan',
        title: '备餐指南',
        snippet: '高蛋白备餐建议优先复用鸡蛋、豆腐、鸡胸肉等易保存食材。',
        source: '内部知识库',
        score: 0.92
      }
    },
    { type: 'status', status: 'executing_tools' },
    {
      type: 'tool',
      tool: {
        id: 'tool-db',
        name: 'database_query',
        displayName: '历史偏好查询',
        status: 'running',
        summary: '正在查询近期常用食材和忌口。',
        input: 'user_food_preferences: recent 30 days',
        output: '查询中...'
      }
    },
    { type: 'toolUpdate', id: 'tool-db', patch: { status: 'success', latencyMs: 280, summary: '确认偏好：高蛋白、控预算、不吃猪肉。', output: 'preferences={highProtein:true,budget:true,avoid:["猪肉"]}' } },
    { type: 'status', status: 'validating' },
    {
      type: 'tool',
      tool: {
        id: 'tool-validator',
        name: 'plan_validator',
        displayName: '计划校验',
        status: 'running',
        summary: '正在校验日均蛋白质、预算和烹饪时间。',
        input: 'plan draft · budget=300 · people=2 · days=3',
        output: '校验中...'
      }
    },
    { type: 'toolUpdate', id: 'tool-validator', patch: { status: 'success', latencyMs: 360, summary: '预算 286/300 元，蛋白质目标通过。', output: 'valid=true · budget=286/300 · proteinTarget=pass' } },
    { type: 'status', status: 'composing' },
    { type: 'assistantStream', content: '已生成 3 天高蛋白备餐草案，预算预计 286 元。计划复用鸡胸肉、鸡蛋、豆腐和西兰花，降低采购成本和食材浪费。' },
    {
      type: 'card',
      card: {
        type: 'result',
        mode: 'planning',
        label: '计划草案',
        title: '3 天高蛋白备餐已生成，预算预计 286 元',
        description: '计划优先复用鸡胸肉、鸡蛋、豆腐和西兰花，当前校验通过，可继续查看购物清单或确认保存。',
        primaryAction: '确认保存',
        secondaryAction: '查看购物清单'
      }
    },
    { type: 'status', status: 'completed' }
  ];
}

function buildSteps(mode: AgentMode, prompt: string): ReplayStep[] {
  return withRunEvents([{ type: 'event', event: 'run.created' }, ...buildStepPayloads(mode, prompt)]);
}

function buildPlanningContinuationSteps(summary: string): ReplayStep[] {
  return withRunEvents([
    { type: 'status', status: 'planning' },
    { type: 'message', content: `收到补充约束：${summary}。我会按这些条件继续生成计划。` },
    { type: 'status', status: 'retrieving' },
    {
      type: 'tool',
      tool: {
        id: 'tool-knowledge',
        name: 'knowledge_search',
        displayName: '知识检索',
        status: 'running',
        summary: '正在检索高蛋白备餐和冷藏保存建议。',
        input: `高蛋白 备餐 冷藏 保存 ${summary}`,
        output: '检索中...'
      }
    },
    {
      type: 'toolUpdate',
      id: 'tool-knowledge',
      patch: { status: 'success', latencyMs: 420, summary: '命中食材复用、冷藏保存、预算控制 3 条参考。', output: '3 references · avg score=0.88' }
    },
    {
      type: 'citation',
      citation: {
        id: 'c-plan',
        title: '备餐指南',
        snippet: '高蛋白备餐建议优先复用鸡蛋、豆腐、鸡胸肉等易保存食材。',
        source: '内部知识库',
        score: 0.92
      }
    },
    { type: 'status', status: 'executing_tools' },
    {
      type: 'tool',
      tool: {
        id: 'tool-db',
        name: 'database_query',
        displayName: '历史偏好查询',
        status: 'running',
        summary: '正在查询近期常用食材和忌口。',
        input: `user_food_preferences: ${summary}`,
        output: '查询中...'
      }
    },
    {
      type: 'toolUpdate',
      id: 'tool-db',
      patch: { status: 'success', latencyMs: 280, summary: '已把预算、忌口和目标映射进计划上下文。', output: summary }
    },
    { type: 'status', status: 'validating' },
    {
      type: 'tool',
      tool: {
        id: 'tool-validator',
        name: 'plan_validator',
        displayName: '计划校验',
        status: 'running',
        summary: '正在校验日均蛋白质、预算和烹饪时间。',
        input: `plan draft · ${summary}`,
        output: '校验中...'
      }
    },
    {
      type: 'toolUpdate',
      id: 'tool-validator',
      patch: { status: 'success', latencyMs: 360, summary: '预算与蛋白质目标校验通过。', output: 'valid=true · budget=pass · protein_target=pass' }
    },
    { type: 'status', status: 'composing' },
    {
      type: 'assistantStream',
      content: `已根据“${summary}”生成高蛋白备餐草案。当前方案优先复用鸡胸肉、鸡蛋、豆腐和西兰花，兼顾预算控制、可执行性和批量备餐效率。`
    },
    {
      type: 'card',
      card: {
        type: 'result',
        mode: 'planning',
        label: '计划草案',
        title: '备餐计划已更新，可继续查看购物清单或确认保存',
        description: `计划已经纳入 ${summary}，当前校验通过。`,
        primaryAction: '确认保存',
        secondaryAction: '查看购物清单'
      }
    },
    { type: 'status', status: 'completed' }
  ]);
}

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

  const appendRunEvent = useCallback((event: MockRunEventName) => {
    setEvents((current) => [
      ...current,
      {
        id: `${event}-${Date.now()}-${current.length}`,
        event,
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
      setMessages((current) => [...current, createMessage('assistant', '已模拟保存这个备餐计划。真实接入后会生成 `meal_plan_id`，并同步到“饮食管理”页面。')]);
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
    [answerClarification, cancelWrite, card, confirmWrite, editWrite, events, handleResultPrimary, handleResultSecondary, input, messages, run, running, send, stop]
  );
}
