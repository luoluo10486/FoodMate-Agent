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

