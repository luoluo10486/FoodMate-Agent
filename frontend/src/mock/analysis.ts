export const proteinGoal = {
  weightKg: 70,
  proteinMultiplierRange: [1.5, 2] as const
};

export type AnalysisRange = '7d' | '14d' | '30d';
export type ProteinTrendPoint = {
  day: string;
  protein: number;
};
type AnalysisSummary = {
  calories: number;
  mealCount: number;
};

export const analysisRangeOptions: Array<{ label: string; value: AnalysisRange }> = [
  { label: '最近 7 天', value: '7d' },
  { label: '最近 14 天', value: '14d' },
  { label: '最近 30 天', value: '30d' }
];

export const proteinTrendByRange: Record<AnalysisRange, ProteinTrendPoint[]> = {
  '7d': [
    { day: '周一', protein: 58 },
    { day: '周二', protein: 62 },
    { day: '周三', protein: 70 },
    { day: '周四', protein: 64 },
    { day: '周五', protein: 80 },
    { day: '周六', protein: 72 },
    { day: '周日', protein: 80 }
  ],
  '14d': [
    { day: '1日', protein: 56 },
    { day: '2日', protein: 60 },
    { day: '3日', protein: 68 },
    { day: '4日', protein: 63 },
    { day: '5日', protein: 76 },
    { day: '6日', protein: 72 },
    { day: '7日', protein: 82 },
    { day: '8日', protein: 66 },
    { day: '9日', protein: 74 },
    { day: '10日', protein: 86 },
    { day: '11日', protein: 78 },
    { day: '12日', protein: 92 },
    { day: '13日', protein: 84 },
    { day: '14日', protein: 96 }
  ],
  '30d': [
    { day: '1日', protein: 54 },
    { day: '2日', protein: 61 },
    { day: '3日', protein: 64 },
    { day: '4日', protein: 58 },
    { day: '5日', protein: 72 },
    { day: '6日', protein: 69 },
    { day: '7日', protein: 80 },
    { day: '8日', protein: 62 },
    { day: '9日', protein: 76 },
    { day: '10日', protein: 83 },
    { day: '11日', protein: 74 },
    { day: '12日', protein: 88 },
    { day: '13日', protein: 90 },
    { day: '14日', protein: 96 },
    { day: '15日', protein: 71 },
    { day: '16日', protein: 78 },
    { day: '17日', protein: 85 },
    { day: '18日', protein: 92 },
    { day: '19日', protein: 87 },
    { day: '20日', protein: 98 },
    { day: '21日', protein: 102 },
    { day: '22日', protein: 80 },
    { day: '23日', protein: 86 },
    { day: '24日', protein: 95 },
    { day: '25日', protein: 104 },
    { day: '26日', protein: 90 },
    { day: '27日', protein: 108 },
    { day: '28日', protein: 112 },
    { day: '29日', protein: 96 },
    { day: '30日', protein: 118 }
  ]
};

export const analysisSummaryByRange: Record<AnalysisRange, AnalysisSummary> = {
  '7d': { calories: 12840, mealCount: 18 },
  '14d': { calories: 26160, mealCount: 37 },
  '30d': { calories: 56320, mealCount: 82 }
};

export const proteinTargetMin = Math.round(proteinGoal.weightKg * proteinGoal.proteinMultiplierRange[0]);
export const proteinTargetMax = Math.round(proteinGoal.weightKg * proteinGoal.proteinMultiplierRange[1]);

export function getAnalysisMetrics(range: AnalysisRange, proteinTrend: ProteinTrendPoint[]) {
  const summary = analysisSummaryByRange[range];
  const proteinTotal = proteinTrend.reduce((total, item) => total + item.protein, 0);
  const proteinAchievement = proteinTrend.length > 0 ? Math.round((proteinTotal / (proteinTargetMin * proteinTrend.length)) * 100) : 0;

  return [
    { label: '总热量', value: summary.calories.toLocaleString('zh-CN'), unit: 'kcal', tone: 'dark' },
    { label: '蛋白质', value: String(proteinTotal), unit: 'g', tone: 'green' },
    { label: '目标达成', value: String(proteinAchievement), unit: '%', tone: 'orange' },
    { label: '记录餐次', value: String(summary.mealCount), unit: '餐', tone: 'blue' }
  ];
}

export function getAnalysisInsights(proteinTrend: ProteinTrendPoint[]) {
  const lowDays = proteinTrend.filter((item) => item.protein < proteinTargetMin).length;

  return [
    {
      title: lowDays > proteinTrend.length / 2 ? '多数天低于推荐摄入' : '部分日期低于推荐摄入',
      detail: `按 ${proteinGoal.weightKg}kg 估算，建议每天 ${proteinTargetMin}-${proteinTargetMax}g 蛋白质。`,
      tone: 'warning'
    },
    {
      title: '两餐缺少份量',
      detail: '结果包含估算，请补全克重。',
      tone: 'info'
    }
  ];
}
