/**
 * 识别 Figma 页面使用的兼容查询参数，避免旧链接和当前验收链接渲染出不同壳层。
 */
export function isFigmaFixtureState(value: string | null): boolean {
  return value === 'v2' || value === 'figma-v2';
}
