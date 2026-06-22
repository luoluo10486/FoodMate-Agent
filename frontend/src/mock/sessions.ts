import type { AgentRunView } from '../types/agent';
import type { Message, SessionSummary } from '../types/session';
import type { TaskCardData } from '../types/ui';

export const mockSessions: SessionSummary[] = [
  {
    id: 'week-plan',
    title: '一周备餐计划',
    subtitle: '预算 300 元 · 高蛋白',
    pinned: true,
    active: true,
    status: 'validating'
  },
  {
    id: 'protein-review',
    title: '最近一周蛋白质复盘',
    subtitle: '18 餐记录 · 66% 达成',
    status: 'completed'
  },
  {
    id: 'lunch-log',
    title: '午餐记录确认',
    subtitle: '鸡胸肉、米饭、西兰花',
    status: 'waiting_user'
  }
];

export const taskCards: TaskCardData[] = [
  {
    id: 'calorie',
    title: '热量计算',
    description: '快速估算食物热量和宏量营养',
    prompt: '计算 20 克鸡胸肉的卡路里',
    accent: 'orange'
  },
  {
    id: 'analysis',
    title: '摄入分析',
    description: '看懂一周趋势、缺口和异常',
    prompt: '分析我最近一周蛋白质摄入',
    accent: 'blue'
  },
  {
    id: 'planning',
    title: '复杂规划',
    description: '按预算、忌口和目标生成备餐计划',
    prompt: '为 2 人制定一周备餐计划',
    accent: 'green'
  }
];

export const recommendedPrompts = [
  '帮我记录今天的午餐',
  '为 2 人制定一周备餐计划',
  '分析豆腐和牛肉的蛋白质含量',
  '西兰花焯水多久比较合适'
];

export const chatMessages: Message[] = [
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

export const planningRun: AgentRunView = {
  id: 'run-week-plan',
  status: 'validating',
  intent: 'planning',
  toolsUsed: 2,
  toolsTotal: 6,
  agentsUsed: 1,
  agentsTotal: 1,
  toolCalls: [
    {
      id: 'tool-knowledge',
      name: 'knowledge_search',
      displayName: '知识检索',
      status: 'success',
      latencyMs: 420,
      summary: '命中食材复用、冷藏保存、预算控制 3 条参考。'
    },
    {
      id: 'tool-validator',
      name: 'plan_validator',
      displayName: '计划校验',
      status: 'running',
      summary: '正在校验日均蛋白质、预算和烹饪时间。'
    }
  ],
  citations: [
    {
      id: 'c1',
      title: '备餐指南',
      snippet: '高蛋白备餐建议优先复用鸡蛋、豆腐、鸡胸肉等易保存食材。',
      source: '内部知识库',
      score: 0.92
    }
  ]
};
