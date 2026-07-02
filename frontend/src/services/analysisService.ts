/**
 * 营养分析服务 — 当前转发 mock，后续替换为真实 API 调用。
 */
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

export {
  analysisRangeOptions,
  getAnalysisInsights,
  getAnalysisMetrics,
  proteinGoal,
  proteinTargetMax,
  proteinTargetMin,
  proteinTrendByRange,
};
