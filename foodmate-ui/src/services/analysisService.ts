import { apiRequest } from './apiClient';

/** 保留 Fixture 分析辅助函数，供设计预览模式使用。 */
import {
  type AnalysisRange,
  analysisRangeOptions,
  getAnalysisInsights,
  getAnalysisMetrics,
  proteinGoal,
  proteinTargetMax,
  proteinTargetMin,
  proteinTrendByRange,
} from '../mock/analysis';

export type { AnalysisRange };

export type NutritionAnalysisRange = 'today' | '7d' | '30d';

export type NutritionAnalysis = {
  range: NutritionAnalysisRange;
  from: string;
  to: string;
  total_items: number;
  matched_items: number;
  coverage: number | string;
  calories_kcal: number | string;
  protein_g: number | string;
  fat_g: number | string;
  carbs_g: number | string;
  calorie_target: number | null;
  protein_target: number | null;
  incomplete: boolean;
  unmatched_names: string[];
  disclaimer: string;
};

export async function loadNutritionAnalysis(range: NutritionAnalysisRange): Promise<NutritionAnalysis> {
  return apiRequest<NutritionAnalysis>(`/api/nutrition-analysis?range=${encodeURIComponent(range)}`);
}

export {
  analysisRangeOptions,
  getAnalysisInsights,
  getAnalysisMetrics,
  proteinGoal,
  proteinTargetMax,
  proteinTargetMin,
  proteinTrendByRange,
};
