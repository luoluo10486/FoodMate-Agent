import type { AgentDisplayStatus, AgentRunView, Citation, ToolCall } from '../types/agent';
import type { Message } from '../types/session';
import type { ClarificationField } from '../components/agent/ClarificationCard';

export type AgentMode = 'planning' | 'record' | 'analysis' | 'knowledge_qna' | 'calculation';
export type AgentCard =
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

export type MockRunEventName =
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

export type MockRunEvent = {
  id: string;
  event: MockRunEventName;
  createdAt: string;
};

export type ReplayStepPayload =
  | { type: 'event'; event: MockRunEventName }
  | ({ type: 'status'; status: AgentDisplayStatus } & { event?: MockRunEventName })
  | ({ type: 'message'; content: string } & { event?: MockRunEventName })
  | ({ type: 'assistantStream'; content: string; chunkMs?: number } & { event?: MockRunEventName })
  | ({ type: 'tool'; tool: ToolCall } & { event?: MockRunEventName })
  | ({ type: 'toolUpdate'; id: string; patch: Partial<ToolCall> } & { event?: MockRunEventName })
  | ({ type: 'citation'; citation: Citation } & { event?: MockRunEventName })
  | ({ type: 'card'; card: AgentCard } & { event?: MockRunEventName });

export type ReplayStep = ReplayStepPayload & { event: MockRunEventName };

export const seededPrompts: Record<string, string> = {
  'protein-review': '分析我最近一周蛋白质摄入',
  'lunch-log': '帮我记录今天午餐',
  'week-plan': '给 2 个人制定一周高蛋白备餐计划，预算 300 元以内',
};

export const baseRun: AgentRunView = {
  id: 'mock-run',
  status: 'completed',
  intent: 'planning',
  toolsUsed: 0,
  toolsTotal: 6,
  agentsUsed: 1,
  agentsTotal: 1,
  toolCalls: [],
  citations: [],
};

export const initialMessages: Message[] = [
  {
    id: 'm1',
    role: 'user',
    content: '给 2 个人制定一周高蛋白备餐计划，预算 300 元以内。',
    time: '09:31',
  },
  {
    id: 'm2',
    role: 'assistant',
    content: '我会先检索食材搭配和保存建议，再校验预算、蛋白质目标和烹饪时间。',
    time: '09:31',
  },
];

const nowTime = () =>
  new Intl.DateTimeFormat('zh-CN', { hour: '2-digit', minute: '2-digit', hour12: false }).format(new Date());

export function createMessage(role: Message['role'], content: string): Message {
  return {
    id: `${role}-${Date.now()}-${Math.random().toString(16).slice(2)}`,
    role,
    content,
    time: nowTime(),
  };
}

export function detectMode(prompt: string): AgentMode {
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
      cancelled: 'run.cancelled',
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
          output: '等待数据库响应...',
        },
      },
      {
        type: 'toolUpdate',
        id: 'tool-database',
        patch: {
          status: 'failed',
          error: 'DATABASE_QUERY_TIMEOUT：查询超时，请稍后重试。',
          output: 'timeout after 3000ms',
        },
      },
      { type: 'status', status: 'failed' },
      { type: 'card', card: { type: 'error', message: 'DATABASE_QUERY_TIMEOUT：查询超时，请稍后重试。' } },
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
          output: '计算中...',
        },
      },
      {
        type: 'toolUpdate',
        id: 'tool-calorie',
        patch: {
          status: 'success',
          latencyMs: 140,
          summary: '计算完成：20g 鸡胸肉约 33 kcal，蛋白质约 6.2g。',
          output: '33 kcal · 蛋白质 6.2g · formula=165/100*20',
        },
      },
      { type: 'status', status: 'composing' },
      {
        type: 'assistantStream',
        content:
          '20 克熟鸡胸肉约 33 kcal，蛋白质约 6.2g。不同品牌和烹饪方式会有小幅差异，记录时可以按包装营养表优先修正。',
      },
      {
        type: 'card',
        card: {
          type: 'result',
          mode: 'calculation',
          label: '计算完成',
          title: '20g 鸡胸肉约 33 kcal',
          description: '按常见熟鸡胸肉 165 kcal/100g 换算，适合用于快速记录前的估算。',
          primaryAction: '记录这份食物',
          secondaryAction: '继续提问',
        },
      },
      { type: 'status', status: 'completed' },
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
          output: '估算中...',
        },
      },
      {
        type: 'toolUpdate',
        id: 'tool-calculator',
        patch: {
          status: 'success',
          latencyMs: 260,
          summary: '估算完成：约 620 kcal，蛋白质 54g。',
          output: '620 kcal · 蛋白质 54g · 碳水 59g · 脂肪 12g',
        },
      },
      { type: 'status', status: 'waiting_user' },
      {
        type: 'card',
        card: {
          type: 'confirmation',
          data: [
            { label: '餐型', value: '午餐' },
            { label: '食物', value: '鸡胸肉 200g、米饭 150g、西兰花 120g' },
            { label: '估算', value: '约 620 kcal · 蛋白质 54g' },
          ],
        },
      },
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
          summary: '正在解析"最近一周"。',
          input: '最近一周',
          output: '解析中...',
        },
      },
      {
        type: 'toolUpdate',
        id: 'tool-time',
        patch: {
          status: 'success',
          latencyMs: 120,
          summary: '解析为最近 7 天。',
          output: 'start=-7d · end=today · timezone=Asia/Shanghai',
        },
      },
      {
        type: 'tool',
        tool: {
          id: 'tool-db',
          name: 'database_query',
          displayName: '饮食日志查询',
          status: 'running',
          summary: '正在聚合蛋白质摄入。',
          input: 'group protein by day for recent 7 days',
          output: '聚合中...',
        },
      },
      {
        type: 'toolUpdate',
        id: 'tool-db',
        patch: {
          status: 'success',
          latencyMs: 390,
          summary: '聚合完成：7 天共 486g，目标达成 66%。',
          output: '42, 68, 54, 76, 82, 91, 73g · total=486g',
        },
      },
      { type: 'status', status: 'composing' },
      {
        type: 'assistantStream',
        content:
          '最近 7 天蛋白质摄入共 486g，按 70kg 体重的推荐区间 105-140g/天来看，当前达成约 66%。建议优先在早餐或加餐补充鸡蛋、豆腐、酸奶或鱼肉。',
      },
      {
        type: 'card',
        card: {
          type: 'result',
          mode: 'analysis',
          label: '分析完成',
          title: '最近 7 天蛋白质达成约 66%',
          description: '多数日期低于推荐摄入，建议把每日蛋白目标先提升到 105g 下限。',
          primaryAction: '查看分析页',
          secondaryAction: '生成补充计划',
        },
      },
      { type: 'status', status: 'completed' },
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
          output: '检索中...',
        },
      },
      {
        type: 'toolUpdate',
        id: 'tool-knowledge',
        patch: {
          status: 'success',
          latencyMs: 310,
          summary: '命中 2 条烹饪建议。',
          output: 'top hit score=0.91 · 蔬菜焯水与营养保留指南',
        },
      },
      {
        type: 'citation',
        citation: {
          id: 'c-knowledge',
          title: '蔬菜焯水与营养保留指南',
          snippet: '西兰花焯水通常控制在 60-90 秒，出锅后过冷水有助于保持口感和颜色。',
          source: '内部知识库',
          score: 0.91,
        },
      },
      { type: 'status', status: 'composing' },
      {
        type: 'assistantStream',
        content: '西兰花焯水建议控制在 60-90 秒。水开后下锅，加少量盐，出锅后快速过冷水，可以保持颜色和爽脆口感。',
      },
      { type: 'status', status: 'completed' },
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
              quickOptions: ['300 元以内', '400 元以内'],
            },
            {
              key: 'dislikes',
              label: '忌口',
              placeholder: '例如：不吃猪肉、少油',
              quickOptions: ['不吃猪肉', '少油少辣'],
            },
            {
              key: 'goal',
              label: '目标',
              placeholder: '例如：高蛋白、控预算',
              quickOptions: ['目标高蛋白', '高蛋白 + 控预算'],
            },
          ],
        },
      },
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
        output: '检索中...',
      },
    },
    {
      type: 'toolUpdate',
      id: 'tool-knowledge',
      patch: {
        status: 'success',
        latencyMs: 420,
        summary: '命中食材复用、冷藏保存、预算控制 3 条参考。',
        output: '3 references · avg score=0.88',
      },
    },
    {
      type: 'citation',
      citation: {
        id: 'c-plan',
        title: '备餐指南',
        snippet: '高蛋白备餐建议优先复用鸡蛋、豆腐、鸡胸肉等易保存食材。',
        source: '内部知识库',
        score: 0.92,
      },
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
        output: '查询中...',
      },
    },
    {
      type: 'toolUpdate',
      id: 'tool-db',
      patch: {
        status: 'success',
        latencyMs: 280,
        summary: '确认偏好：高蛋白、控预算、不吃猪肉。',
        output: 'preferences={highProtein:true,budget:true,avoid:["猪肉"]}',
      },
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
        input: 'plan draft · budget=300 · people=2 · days=3',
        output: '校验中...',
      },
    },
    {
      type: 'toolUpdate',
      id: 'tool-validator',
      patch: {
        status: 'success',
        latencyMs: 360,
        summary: '预算 286/300 元，蛋白质目标通过。',
        output: 'valid=true · budget=286/300 · proteinTarget=pass',
      },
    },
    { type: 'status', status: 'composing' },
    {
      type: 'assistantStream',
      content:
        '已生成 3 天高蛋白备餐草案，预算预计 286 元。计划复用鸡胸肉、鸡蛋、豆腐和西兰花，降低采购成本和食材浪费。',
    },
    {
      type: 'card',
      card: {
        type: 'result',
        mode: 'planning',
        label: '计划草案',
        title: '3 天高蛋白备餐已生成，预算预计 286 元',
        description: '计划优先复用鸡胸肉、鸡蛋、豆腐和西兰花，当前校验通过，可继续查看购物清单或确认保存。',
        primaryAction: '确认保存',
        secondaryAction: '查看购物清单',
      },
    },
    { type: 'status', status: 'completed' },
  ];
}

export function buildSteps(mode: AgentMode, prompt: string): ReplayStep[] {
  return withRunEvents([
    { type: 'event', event: 'run.created' } as ReplayStepPayload,
    ...buildStepPayloads(mode, prompt),
  ]);
}

export function buildPlanningContinuationSteps(summary: string): ReplayStep[] {
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
        output: '检索中...',
      },
    },
    {
      type: 'toolUpdate',
      id: 'tool-knowledge',
      patch: {
        status: 'success',
        latencyMs: 420,
        summary: '命中食材复用、冷藏保存、预算控制 3 条参考。',
        output: '3 references · avg score=0.88',
      },
    },
    {
      type: 'citation',
      citation: {
        id: 'c-plan',
        title: '备餐指南',
        snippet: '高蛋白备餐建议优先复用鸡蛋、豆腐、鸡胸肉等易保存食材。',
        source: '内部知识库',
        score: 0.92,
      },
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
        output: '查询中...',
      },
    },
    {
      type: 'toolUpdate',
      id: 'tool-db',
      patch: { status: 'success', latencyMs: 280, summary: '已把预算、忌口和目标映射进计划上下文。', output: summary },
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
        output: '校验中...',
      },
    },
    {
      type: 'toolUpdate',
      id: 'tool-validator',
      patch: {
        status: 'success',
        latencyMs: 360,
        summary: '预算与蛋白质目标校验通过。',
        output: 'valid=true · budget=pass · protein_target=pass',
      },
    },
    { type: 'status', status: 'composing' },
    {
      type: 'assistantStream',
      content: `已根据"${summary}"生成高蛋白备餐草案。当前方案优先复用鸡胸肉、鸡蛋、豆腐和西兰花，兼顾预算控制、可执行性和批量备餐效率。`,
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
        secondaryAction: '查看购物清单',
      },
    },
    { type: 'status', status: 'completed' },
  ]);
}
