import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync, writeFileSync } from 'node:fs';
import { basename, dirname, join, relative, resolve } from 'node:path';

const scriptDirectory = dirname(new URL(import.meta.url).pathname.replace(/^\/(\w):/, '$1:'));
const uiRoot = resolve(scriptDirectory, '..');
const repositoryRoot = resolve(uiRoot, '..');
const evidenceRoot = join(uiRoot, '.qa', 'figma-pixel-acceptance');
const mappingPath = join(evidenceRoot, 'figma-105-mapping.json');
const resultsPath = join(evidenceRoot, 'figma-105-diff-results.json');
const pngSignature = Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]);

function isPng(filePath) {
  if (!filePath || !existsSync(filePath)) return false;
  return readFileSync(filePath).subarray(0, 8).equals(pngSignature);
}

function pngSize(filePath) {
  if (!isPng(filePath)) return undefined;
  const buffer = readFileSync(filePath);
  return { width: buffer.readUInt32BE(16), height: buffer.readUInt32BE(20) };
}

function relativeEvidencePath(filePath) {
  return relative(uiRoot, filePath).replaceAll('\\', '/');
}

function resolveFigmaPath(item) {
  if (item.figmaPng) {
    const candidate = resolve(repositoryRoot, item.figmaPng);
    if (isPng(candidate)) return candidate;
  }
  const candidate = join(repositoryRoot, 'docxs', '设计', 'figma-png', `${item.artboardName}.png`);
  return isPng(candidate) ? candidate : undefined;
}

function browserCandidates(item) {
  const candidates = [];
  if (item.browserPng) {
    const original = resolve(uiRoot, item.browserPng);
    candidates.push(original);
    if (original.endsWith('.png')) {
      candidates.push(original.replace(/\.png$/, '-rgba.png'));
      candidates.push(original.replace(/\.png$/, '-full-rgba.png'));
      candidates.push(original.replace(/\.png$/, '-stable-rgba.png'));
    }
  }
  const conventionalBases = [item.artboardName, `chat-${item.artboardName}`];
  for (const base of conventionalBases) {
    candidates.push(join(evidenceRoot, `${base}-browser.png`));
    candidates.push(join(evidenceRoot, `${base}-browser-1440x1024.png`));
    candidates.push(join(evidenceRoot, `${base}-browser-1440x1024-rgba.png`));
  }
  const base = item.browserPng ? basename(item.browserPng).replace(/\.png$/, '') : '';
  if (base) {
    candidates.push(join(evidenceRoot, `${base}-1440x900-converted.png`));
    candidates.push(join(evidenceRoot, `${base}-1440x900-current.png`));
  }
  return [...new Set(candidates)];
}

function resolveBrowserPath(item, expectedSize) {
  const candidates = browserCandidates(item);
  return candidates.find((candidate) => {
    const size = pngSize(candidate);
    return size?.width === expectedSize.width && size.height === expectedSize.height;
  });
}

function compare(expectedPath, actualPath) {
  const output = execFileSync(process.execPath, ['scripts/png-diff.mjs', expectedPath, actualPath], {
    cwd: uiRoot,
    encoding: 'utf8',
  });
  return JSON.parse(output.trim());
}

const mapping = JSON.parse(readFileSync(mappingPath, 'utf8'));
const results = {};
let compared = 0;
let diffReview = 0;
let unmapped = 0;
let sizeMismatch = 0;

for (const item of mapping.items) {
  const figmaPath = resolveFigmaPath(item);
  const expectedSize = figmaPath ? pngSize(figmaPath) : undefined;
  const browserPath = expectedSize ? resolveBrowserPath(item, expectedSize) : undefined;

  if (figmaPath && browserPath) {
    const diff = compare(figmaPath, browserPath);
    const key = item.artboardName;
    if (diff.status === 'SIZE_MISMATCH') {
      sizeMismatch += 1;
      results[key] = diff;
      item.status = 'SIZE_MISMATCH';
      item.evidenceNote = 'Figma and browser evidence dimensions differ';
    } else {
      compared += 1;
      diffReview += 1;
      results[key] = diff;
      item.status = 'DIFF_REVIEW';
      item.evidenceNote = item.manualReview?.status === 'PENDING'
        ? 'Automated diff exists; manual visual review remains open'
        : 'Automated diff exists; see manual visual review conclusion';
    }
    item.figmaPng = figmaPath.startsWith(repositoryRoot)
      ? figmaPath.slice(repositoryRoot.length + 1).replaceAll('\\', '/')
      : item.figmaPng;
    item.browserPng = relativeEvidencePath(browserPath);
    item.diffJson = `.qa/figma-pixel-acceptance/figma-105-diff-results.json#${key}`;
    continue;
  }

  unmapped += 1;
  item.status = 'UNMAPPED';
  item.manualReviewConclusion = 'PENDING';
  item.evidenceNote = !figmaPath
    ? 'Figma PNG evidence is missing'
    : 'Browser route or fixture evidence is missing';
  item.figmaPng = figmaPath ? figmaPath.slice(repositoryRoot.length + 1).replaceAll('\\', '/') : null;
  item.browserPng = null;
  item.diffJson = `.qa/figma-pixel-acceptance/figma-105-diff-results.json#${item.artboardName}`;
  results[item.artboardName] = { status: 'UNMAPPED', reason: item.evidenceNote };
}

mapping.summary = {
  total: mapping.items.length,
  pass: 0,
  diffReview,
  unmapped,
  sizeMismatch,
  automatedDiffInputs: compared,
};
mapping.source.capturedAt = new Date().toISOString().slice(0, 10);

writeFileSync(mappingPath, `${JSON.stringify(mapping, null, 2)}${String.fromCharCode(10)}`);
writeFileSync(
  resultsPath,
  `${JSON.stringify({ generatedAt: mapping.source.capturedAt, tool: 'scripts/png-diff.mjs', results }, null, 2)}${String.fromCharCode(10)}`,
);

console.log(JSON.stringify(mapping.summary));
