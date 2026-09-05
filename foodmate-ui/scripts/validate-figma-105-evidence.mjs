import { existsSync, readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { FIGMA_ACCEPTANCE_CONFIG, isAcceptedViewport } from './figma-acceptance-config.mjs';

const uiRoot = resolve(fileURLToPath(new URL('.', import.meta.url)), '..');
const repositoryRoot = resolve(uiRoot, '..');
const evidenceRoot = resolve(uiRoot, '.qa', 'figma-pixel-acceptance');
const mapping = JSON.parse(readFileSync(resolve(evidenceRoot, 'figma-105-mapping.json'), 'utf8'));
const runtime = JSON.parse(readFileSync(resolve(evidenceRoot, 'figma-105-runtime-checks.json'), 'utf8'));
const requiredFields = [
  'figmaNodeId',
  'artboardName',
  'artboardSize',
  'frontendRoute',
  'browserViewport',
  'figmaPng',
  'browserPng',
  'diffJson',
  'status',
];

function pngSize(filePath) {
  if (!existsSync(filePath)) return undefined;
  const buffer = readFileSync(filePath);
  if (buffer.length < 24 || !buffer.subarray(0, 8).equals(Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]))) {
    return undefined;
  }
  return { width: buffer.readUInt32BE(16), height: buffer.readUInt32BE(20) };
}

const errors = [];
const items = Array.isArray(mapping.items) ? mapping.items : [];
if (items.length !== 105) errors.push(`画板数量应为 105，实际为 ${items.length}`);

for (const item of items) {
  for (const field of requiredFields) {
    if (item[field] === undefined || item[field] === null || item[field] === '') {
      errors.push(`${item.artboardName ?? 'unknown'} 缺少字段 ${field}`);
    }
  }

  if (!isAcceptedViewport(item.artboardSize) || !isAcceptedViewport(item.browserViewport)) {
    errors.push(`${item.artboardName} 使用了未登记的验收视口`);
  }

  const figmaPath = resolve(repositoryRoot, item.figmaPng);
  const browserPath = resolve(uiRoot, item.browserPng);
  const expectedSize = pngSize(figmaPath);
  const actualSize = pngSize(browserPath);
  if (!expectedSize || !actualSize) {
    errors.push(`${item.artboardName} 缺少有效 Figma 或浏览器 PNG`);
  } else if (expectedSize.width !== actualSize.width || expectedSize.height !== actualSize.height) {
    errors.push(`${item.artboardName} PNG 尺寸不一致`);
  }
}

const runtimeSummary = runtime.summary ?? {};
const strictDprPass = runtimeSummary.dprPass === items.length;
const structuralPass =
  runtimeSummary.total === items.length &&
  runtimeSummary.viewportPass === items.length &&
  runtimeSummary.geometryPass === items.length &&
  runtimeSummary.textPass === items.length &&
  errors.length === 0;
const strictReady = structuralPass && strictDprPass && mapping.summary?.pass === items.length;
const result = {
  status: strictReady ? 'READY' : strictDprPass ? 'VISUAL_REVIEW_REQUIRED' : 'DPR_RECAPTURE_REQUIRED',
  total: items.length,
  dpr: FIGMA_ACCEPTANCE_CONFIG.dpr,
  structuralPass,
  strictDprPass,
  mappedPass: mapping.summary?.pass ?? 0,
  diffReview: mapping.summary?.diffReview ?? 0,
  unmapped: mapping.summary?.unmapped ?? 0,
  sizeMismatch: mapping.summary?.sizeMismatch ?? 0,
  errors,
};

console.log(JSON.stringify(result, null, 2));
if (process.argv.includes('--strict') && !strictReady) process.exitCode = 1;
