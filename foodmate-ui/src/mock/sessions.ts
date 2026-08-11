import type { SessionSummary } from '../types/session';
import type { TaskCardData } from '../types/ui';

export const mockSessions: SessionSummary[] = [
  {
    id: 'weekly-adjustment',
    title: '每周饮食微调',
    subtitle: '12:45',
    pinned: true,
    active: true,
    status: 'validating',
  },
  {
    id: 'pre-workout-snack',
    title: '运动前零食建议',
    subtitle: '12:45',
    status: 'completed',
  },
  {
    id: 'allergen-rules',
    title: '过敏原排除规则',
    subtitle: '12:45',
    status: 'completed',
  },
  {
    id: 'protein-supplement',
    title: '蛋白质补充方案',
    subtitle: '12:45',
    status: 'completed',
  },
  {
    id: 'bedtime-snack',
    title: '睡前加餐建议',
    subtitle: '12:45',
    status: 'completed',
  },
  {
    id: 'breakfast-carbs',
    title: '早餐碳水搭配',
    subtitle: '12:45',
    status: 'completed',
  },
  {
    id: 'dinner-protein',
    title: '晚餐蛋白质补充',
    subtitle: '12:45',
    status: 'completed',
  },
  {
    id: 'low-carb-diet',
    title: '低碳水饮食建议',
    subtitle: '12:45',
    status: 'completed',
  },
];

export const mockHomeSessions: SessionSummary[] = [
  {
    id: 'week-plan',
    title: '每周宏量调整',
    subtitle: '5分钟前活跃',
    active: true,
    status: 'validating',
  },
  {
    id: 'protein-review',
    title: '生酮餐食计划制定',
    subtitle: '1小时前',
    status: 'completed',
  },
  {
    id: 'lunch-log',
    title: '食物照片标注代理',
    subtitle: '昨天完成',
    status: 'waiting_user',
  },
];

export const taskCards: TaskCardData[] = [
  {
    id: 'calorie',
    title: '热量计算',
    description: '快速估算食物热量和宏量营养',
    prompt: '计算 20 克鸡胸肉的卡路里',
    accent: 'orange',
  },
  {
    id: 'analysis',
    title: '摄入分析',
    description: '看懂一周趋势、缺口和异常',
    prompt: '分析我最近一周蛋白质摄入',
    accent: 'blue',
  },
  {
    id: 'planning',
    title: '复杂规划',
    description: '按预算、忌口和目标生成备餐计划',
    prompt: '为 2 人制定一周备餐计划',
    accent: 'green',
  },
];

export const recommendedPrompts = [
  '帮我记录今天的午餐',
  '为 2 人制定一周备餐计划',
  '分析豆腐和牛肉的蛋白质含量',
  '西兰花焯水多久比较合适',
];
