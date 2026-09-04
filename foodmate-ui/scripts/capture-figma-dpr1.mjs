import { spawn } from 'node:child_process';
import { existsSync, mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptDirectory = dirname(fileURLToPath(import.meta.url));
const uiRoot = resolve(scriptDirectory, '..');
const evidenceRoot = join(uiRoot, '.qa', 'figma-pixel-acceptance');
const mappingPath = join(evidenceRoot, 'figma-105-mapping.json');
const chromePath = process.env.CHROME_PATH ?? 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';
const captureDate = new Intl.DateTimeFormat('en-CA', {
  timeZone: 'Asia/Shanghai',
}).format(new Date());
const requestedNames = new Set(process.argv.slice(2).filter((value) => !value.startsWith('--')));

function sleep(milliseconds) {
  return new Promise((resolvePromise) => setTimeout(resolvePromise, milliseconds));
}

async function waitForJson(url) {
  for (let attempt = 0; attempt < 50; attempt += 1) {
    try {
      const response = await fetch(url);
      if (response.ok) return response.json();
    } catch {
      // Chrome 尚未启动完成时继续轮询，避免把启动时序误判为采集失败。
    }
    await sleep(200);
  }
  throw new Error(`Chrome 调试端点未就绪: ${url}`);
}

function parseViewport(value) {
  const match = /^(\d+)x(\d+)$/.exec(value);
  if (!match) throw new Error(`无法解析验收视口: ${value}`);
  return { width: Number(match[1]), height: Number(match[2]) };
}

function targetUrl(item) {
  const query = item.queryState ? `${item.queryState}&visual-qa=1` : 'visual-qa=1';
  return `http://127.0.0.1:5174${item.frontendRoute}${item.frontendRoute.includes('?') ? '&' : '?'}${query}`;
}

class CdpClient {
  constructor(socket) {
    this.socket = socket;
    this.nextId = 1;
    this.pending = new Map();
    socket.addEventListener('message', (event) => {
      const message = JSON.parse(event.data);
      const resolver = this.pending.get(message.id);
      if (!resolver) return;
      this.pending.delete(message.id);
      if (message.error) resolver.reject(new Error(message.error.message));
      else resolver.resolve(message.result);
    });
  }

  send(method, params = {}) {
    const id = this.nextId;
    this.nextId += 1;
    return new Promise((resolvePromise, rejectPromise) => {
      this.pending.set(id, { resolve: resolvePromise, reject: rejectPromise });
      this.socket.send(JSON.stringify({ id, method, params }));
    });
  }
}

async function connectSocket(url) {
  const socket = new WebSocket(url);
  await new Promise((resolvePromise, rejectPromise) => {
    socket.addEventListener('open', resolvePromise, { once: true });
    socket.addEventListener('error', () => rejectPromise(new Error('无法连接 Chrome CDP')), { once: true });
  });
  return socket;
}

async function evaluate(client, expression) {
  let lastError;
  for (let attempt = 0; attempt < 4; attempt += 1) {
    try {
      const result = await client.send('Runtime.evaluate', {
        expression,
        awaitPromise: true,
        returnByValue: true,
      });
      if (result.exceptionDetails) throw new Error('浏览器运行时检查失败');
      return result.result?.value;
    } catch (error) {
      lastError = error;
      await sleep(250);
    }
  }
  throw lastError;
}

async function capture(item, index) {
  const viewport = parseViewport(item.browserViewport);
  const outputPath = join(evidenceRoot, 'recaptured', `dpr1-${item.artboardName}-browser-${captureDate}.png`);
  const profilePath = join(uiRoot, '.tmp', `figma-dpr1-${process.pid}-${index}`);
  const port = 9222 + index;
  mkdirSync(dirname(outputPath), { recursive: true });
  mkdirSync(dirname(profilePath), { recursive: true });

  const chrome = spawn(
    chromePath,
    [
      '--headless=new',
      '--disable-gpu',
      '--no-first-run',
      '--no-default-browser-check',
      '--force-device-scale-factor=1',
      `--window-size=${viewport.width},${viewport.height}`,
      '--hide-scrollbars',
      '--remote-allow-origins=*',
      `--remote-debugging-port=${port}`,
      `--user-data-dir=${profilePath}`,
      targetUrl(item),
    ],
    { stdio: 'ignore', windowsHide: true },
  );

  try {
    const version = await waitForJson(`http://127.0.0.1:${port}/json/version`);
    const pages = await waitForJson(`http://127.0.0.1:${port}/json/list`);
    const page = pages.find((candidate) => candidate.type === 'page');
    if (!page?.webSocketDebuggerUrl) throw new Error(`未找到页面调试目标: ${item.artboardName}`);
    const socket = await connectSocket(page.webSocketDebuggerUrl);
    const client = new CdpClient(socket);
    await client.send('Runtime.enable');
    await client.send('Page.enable');
    await client.send('Emulation.setDeviceMetricsOverride', {
      width: viewport.width,
      height: viewport.height,
      deviceScaleFactor: 1,
      mobile: false,
    });
    // Vite 首次导航期间会重建执行上下文，稳定等待后再读取页面运行时数据。
    await sleep(1000);
    await evaluate(client, 'document.fonts.ready.then(() => new Promise((resolve) => setTimeout(resolve, 300)))');
    const metrics = await evaluate(
      client,
      `({
        dpr: window.devicePixelRatio,
        viewport: { width: window.innerWidth, height: window.innerHeight },
        fonts: document.fonts.status,
        bodyOverflow: document.documentElement.scrollWidth > window.innerWidth || document.body.scrollWidth > window.innerWidth,
        url: window.location.pathname + window.location.search
      })`,
    );
    const screenshot = await client.send('Page.captureScreenshot', { format: 'png', fromSurface: true });
    writeFileSync(outputPath, Buffer.from(screenshot.data, 'base64'));
    socket.close();
    return {
      artboardName: item.artboardName,
      outputPath: outputPath.replace(`${uiRoot}${process.platform === 'win32' ? '\\' : '/'}`, '').replaceAll('\\', '/'),
      chromeVersion: version.Browser,
      ...metrics,
    };
  } finally {
    chrome.kill();
    await sleep(250);
    if (existsSync(profilePath)) rmSync(profilePath, { recursive: true, force: true });
  }
}

const mapping = JSON.parse(readFileSync(mappingPath, 'utf8'));
const items = mapping.items.filter((item) => requestedNames.size === 0 || requestedNames.has(item.artboardName));
if (items.length === 0) throw new Error('没有匹配到待采集画板');

const results = [];
for (const [index, item] of items.entries()) {
  results.push(await capture(item, index));
}

console.log(JSON.stringify({ captureDate, total: results.length, results }, null, 2));
