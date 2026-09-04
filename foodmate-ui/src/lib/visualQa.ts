export const VISUAL_QA_QUERY = 'visual-qa';
export const VISUAL_QA_VALUE = '1';
export const VISUAL_QA_FIXED_NOW = '2024-03-14T12:46:00+08:00';

function currentSearch(): string {
  return typeof window === 'undefined' ? '' : window.location.search;
}

export function isVisualQaEnabled(search = currentSearch()): boolean {
  return new URLSearchParams(search).get(VISUAL_QA_QUERY) === VISUAL_QA_VALUE;
}

/** 视觉验收使用固定时间，保证 mock 截图不会因系统时钟变化。 */
export function getVisualQaNow(search = currentSearch()): Date {
  return isVisualQaEnabled(search) ? new Date(VISUAL_QA_FIXED_NOW) : new Date();
}
