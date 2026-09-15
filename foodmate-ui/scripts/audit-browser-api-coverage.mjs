import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const HTTP_METHODS = ['GET', 'POST', 'PUT', 'PATCH', 'DELETE'];
const MAPPING_PATTERN = /@(?:[A-Za-z0-9_$]+\.)*(?<verb>Get|Post|Put|Patch|Delete)Mapping\s*(?:\((?<args>[\s\S]*?)\))?/g;

const scriptDirectory = path.dirname(fileURLToPath(import.meta.url));
const uiDirectory = path.resolve(scriptDirectory, '..');
const repositoryDirectory = path.resolve(uiDirectory, '..');
const controllerDirectory = path.join(
  repositoryDirectory,
  'foodmate-api',
  'src',
  'main',
  'java',
  'com',
  'foodmate',
  'api',
  'controller',
);
const sourceDirectory = path.join(uiDirectory, 'src');
const coverageDocument = path.join(repositoryDirectory, 'docxs', '功能实现说明', '前端接口覆盖清单.md');

function walk(directory) {
  return fs.readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const entryPath = path.join(directory, entry.name);
    return entry.isDirectory() ? walk(entryPath) : [entryPath];
  });
}

function readText(filePath) {
  return fs.readFileSync(filePath, 'utf8');
}

function joinPath(basePath, childPath) {
  const value = `${basePath.replace(/\/$/, '')}/${childPath.replace(/^\//, '')}`;
  return value === '/' ? '/' : value.replace(/\/$/, '');
}

function firstPathArgument(args = '') {
  const match = args.match(/(?:^|,)\s*(?:value|path)?\s*=\s*"([^"]*)"|"([^"]*)"/);
  return match?.[1] ?? match?.[2] ?? '';
}

function parseControllerMappings() {
  const rows = [];
  for (const filePath of walk(controllerDirectory).filter((value) => value.endsWith('.java'))) {
    const source = readText(filePath);
    const baseMatch = source.match(/@RequestMapping\s*\(\s*"([^"]*)"/);
    const basePath = baseMatch?.[1] ?? '';
    for (const match of source.matchAll(MAPPING_PATTERN)) {
      const pathValue = firstPathArgument(match.groups.args);
      rows.push({
        controller: path.relative(repositoryDirectory, filePath),
        method: match.groups.verb.toUpperCase(),
        path: joinPath(basePath, pathValue),
      });
    }
  }
  return rows;
}

function parseCoverageRows() {
  return readText(coverageDocument)
    .split(/\r?\n/)
    .flatMap((line) => {
      if (!/^\|\s*\d+[a-z]?\s*\|/.test(line)) return [];
      const cells = line.split('|').map((cell) => cell.trim());
      if (cells.length !== 9 || !HTTP_METHODS.includes(cells[2])) return [];
      const pathValue = cells[4].replaceAll('`', '');
      if (!pathValue.startsWith('/api/')) return [];
      return [
        {
          number: cells[1],
          method: cells[2],
          path: pathValue,
          page: cells[3],
          service: cells[5],
          status: cells[7],
        },
      ];
    });
}

function endpointKey(method, pathValue) {
  return `${method} ${pathValue}`;
}

function sourceFilesForProduction() {
  return walk(sourceDirectory).filter(
    (filePath) =>
      /\.(ts|tsx)$/.test(filePath) &&
      !/\.test\.(ts|tsx)$/.test(filePath) &&
      !filePath.includes(`${path.sep}mock${path.sep}`),
  );
}

function serviceReferences(serviceCell) {
  const references = [];
  for (const group of serviceCell.split('、')) {
    const firstReference = group.match(/\b([A-Za-z_$][\w$]*)\.([A-Za-z_$][\w$]*)\b/);
    if (!firstReference) continue;
    const module = firstReference[1];
    references.push({ module, functionName: firstReference[2] });
    const suffix = group.slice((firstReference.index ?? 0) + firstReference[0].length);
    for (const candidate of suffix.split('/')) {
      const functionName = candidate.trim();
      if (/^[A-Za-z_$][\w$]*$/.test(functionName)) references.push({ module, functionName });
    }
  }
  return references;
}

function findProductionConsumers(rows) {
  const sourceFiles = sourceFilesForProduction();
  const sourceTexts = sourceFiles.map((filePath) => ({ filePath, text: readText(filePath) }));
  const sharedBoundaryFunctions = new Set(['refreshAuthSession', 'getAvatarUrl']);

  return rows.map((row) => {
    const references = serviceReferences(row.service);
    const consumers = [];
    const missingReferences = [];
    for (const reference of references) {
      const serviceFile = path.join(sourceDirectory, 'services', `${reference.module}.ts`);
      const serviceSource = fs.existsSync(serviceFile) ? readText(serviceFile) : '';
      const functionExists =
        sharedBoundaryFunctions.has(reference.functionName) ||
        (serviceSource.length > 0 &&
          new RegExp(`(?:export\\s+)?(?:async\\s+)?(?:function|const)\\s+${reference.functionName}\\b`).test(
            serviceSource,
          ));
      if (!functionExists) {
        missingReferences.push(`${reference.module}.${reference.functionName}`);
        continue;
      }

      const matches = sourceTexts.filter(({ filePath, text }) => {
        if (path.resolve(filePath) === path.resolve(serviceFile)) return false;
        return new RegExp(`(?<![A-Za-z0-9_$])${reference.functionName}(?![A-Za-z0-9_$])`).test(text);
      });
      if (matches.length > 0 || sharedBoundaryFunctions.has(reference.functionName)) {
        consumers.push(`${reference.module}.${reference.functionName}`);
      } else {
        missingReferences.push(`${reference.module}.${reference.functionName}`);
      }
    }

    return {
      ...row,
      references,
      consumers,
      missingReferences,
    };
  });
}

export function auditCoverage() {
  const controllerMappings = parseControllerMappings();
  const browserMappings = controllerMappings.filter(({ path: pathValue }) => pathValue.startsWith('/api/'));
  const internalMappings = controllerMappings.filter(({ path: pathValue }) => !pathValue.startsWith('/api/'));
  const coverageRows = parseCoverageRows();
  const controllerKeys = new Set(browserMappings.map(({ method, path: pathValue }) => endpointKey(method, pathValue)));
  const documentedKeys = new Set(coverageRows.map(({ method, path: pathValue }) => endpointKey(method, pathValue)));
  const missingDocumentation = [...controllerKeys].filter((key) => !documentedKeys.has(key));
  const staleDocumentation = [...documentedKeys].filter((key) => !controllerKeys.has(key));
  const consumerRows = findProductionConsumers(coverageRows);
  const missingConsumers = consumerRows.filter((row) => row.references.length === 0 || row.consumers.length === 0);

  return {
    controllerMappings,
    browserMappings,
    internalMappings,
    coverageRows,
    missingDocumentation,
    staleDocumentation,
    consumerRows,
    missingConsumers,
    staleStatuses: coverageRows.filter((row) => row.status.includes('已封装')),
  };
}

function printList(label, values) {
  if (values.length === 0) return;
  console.error(`${label}:`);
  for (const value of values) console.error(`- ${value}`);
}

export function main() {
  const result = auditCoverage();
  console.log(`Controller 映射: ${result.controllerMappings.length}`);
  console.log(`浏览器接口: ${result.browserMappings.length}`);
  console.log(`内部接口: ${result.internalMappings.length}`);
  console.log(`文档登记浏览器接口: ${result.coverageRows.length}`);
  console.log(
    `生产消费者证据: ${result.consumerRows.length - result.missingConsumers.length}/${result.coverageRows.length}`,
  );
  console.log(`历史状态仍写作“已封装”的当前行: ${result.staleStatuses.length}`);

  printList('文档缺少接口', result.missingDocumentation);
  printList('文档多出的接口', result.staleDocumentation);
  printList(
    '缺少 service 生产消费者的接口',
    result.missingConsumers.map((row) => `${row.method} ${row.path} [${row.service}]`),
  );

  if (
    result.controllerMappings.length !== 127 ||
    result.browserMappings.length !== 120 ||
    result.internalMappings.length !== 7 ||
    result.coverageRows.length !== 120 ||
    result.missingDocumentation.length > 0 ||
    result.staleDocumentation.length > 0 ||
    result.missingConsumers.length > 0
  ) {
    throw new Error('接口覆盖审计未通过，请先修正后端映射、覆盖清单或前端生产消费者。');
  }

  console.log('接口覆盖审计通过。');
}

const currentScript = process.argv[1] ? path.resolve(process.argv[1]) : '';
if (currentScript === fileURLToPath(import.meta.url)) main();
