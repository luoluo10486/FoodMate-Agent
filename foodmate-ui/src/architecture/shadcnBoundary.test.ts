import { readdirSync, readFileSync, type Dirent } from 'node:fs';
import { dirname, join, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';

const sourceRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const controlledDirectories = [
  'pages',
  'layouts',
  'components/agent',
  'components/brand',
  'components/common',
  'components/planning',
  'components/workspace',
];

function collectTsxFiles(directory: string): string[] {
  return readdirSync(directory, { withFileTypes: true }).flatMap((entry: Dirent) => {
    const entryPath = join(directory, entry.name);
    if (entry.isDirectory()) return collectTsxFiles(entryPath);
    if (!entry.isFile() || !entry.name.endsWith('.tsx') || entry.name.endsWith('.test.tsx')) return [];
    return [entryPath];
  });
}

function findBoundaryViolations() {
  const violations: string[] = [];
  const forbiddenControlPattern = /<\s*(button|input|select|textarea)(?:\s|\/?>)/;
  const forbiddenRadixImportPattern = /(?:from\s+|import\s*\()(['"])@radix-ui\//;

  for (const directory of controlledDirectories) {
    for (const filePath of collectTsxFiles(join(sourceRoot, directory))) {
      const source = readFileSync(filePath, 'utf8');
      const relativePath = relative(sourceRoot, filePath);
      if (forbiddenControlPattern.test(source)) {
        violations.push(`${relativePath}: 页面层不能直接声明原生交互控件`);
      }
      if (forbiddenRadixImportPattern.test(source)) {
        violations.push(`${relativePath}: 页面层不能直接引入 Radix`);
      }
      if (/\bAdminPrimitives\b/.test(source)) {
        violations.push(`${relativePath}: 页面层不能依赖 AdminPrimitives`);
      }
    }
  }

  return violations;
}

describe('页面 shadcn 控件边界', () => {
  it('页面和业务组件只通过共享 UI 层使用交互控件', () => {
    expect(findBoundaryViolations()).toEqual([]);
  });
});
