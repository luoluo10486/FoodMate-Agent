export const proteinGoal = {
  weightKg: 70,
  proteinMultiplierRange: [1.5, 2] as const
};

export const proteinTrend = [
  { day: '周一', protein: 58 },
  { day: '周二', protein: 62 },
  { day: '周三', protein: 70 },
  { day: '周四', protein: 64 },
  { day: '周五', protein: 80 },
  { day: '周六', protein: 72 },
  { day: '周日', protein: 80 }
];

export const proteinTargetMin = Math.round(proteinGoal.weightKg * proteinGoal.proteinMultiplierRange[0]);
export const proteinTargetMax = Math.round(proteinGoal.weightKg * proteinGoal.proteinMultiplierRange[1]);
export const proteinTotal = proteinTrend.reduce((total, item) => total + item.protein, 0);
export const proteinAchievement = Math.round((proteinTotal / (proteinTargetMin * proteinTrend.length)) * 100);

export const analysisMetrics = [
  { label: '总热量', value: '12,840', unit: 'kcal', tone: 'dark' },
  { label: '蛋白质', value: String(proteinTotal), unit: 'g', tone: 'green' },
  { label: '目标达成', value: String(proteinAchievement), unit: '%', tone: 'orange' },
  { label: '记录餐次', value: '18', unit: '餐', tone: 'blue' }
];

export const analysisInsights = [
  {
    title: '本周多数天低于推荐摄入',
    detail: '按 70kg 估算，建议每天 105-140g 蛋白质。',
    tone: 'warning'
  },
  {
    title: '两餐缺少份量',
    detail: '结果包含估算，请补全克重。',
    tone: 'info'
  }
];
