export const FIGMA_ACCEPTANCE_CONFIG = Object.freeze({
  dpr: 1,
  locale: 'zh-CN',
  colorScheme: 'light',
  visualQaQuery: 'visual-qa=1',
  waitForFonts: 'document.fonts.ready',
  disableAnimations: true,
  viewports: Object.freeze([
    Object.freeze({ width: 1440, height: 1024, label: 'desktop' }),
    Object.freeze({ width: 1440, height: 900, label: 'auth' }),
    Object.freeze({ width: 1366, height: 768, label: 'special-desktop' }),
    Object.freeze({ width: 1024, height: 768, label: 'tablet' }),
    Object.freeze({ width: 390, height: 844, label: 'mobile' }),
  ]),
});

export function viewportKey(viewport) {
  return `${viewport.width}x${viewport.height}`;
}

export function isAcceptedViewport(viewport) {
  const normalized =
    typeof viewport === 'string'
      ? viewport.split('x').map((value) => Number(value))
      : [viewport?.width, viewport?.height];
  const [width, height] = normalized;
  return FIGMA_ACCEPTANCE_CONFIG.viewports.some(
    (item) => item.width === width && item.height === height,
  );
}
