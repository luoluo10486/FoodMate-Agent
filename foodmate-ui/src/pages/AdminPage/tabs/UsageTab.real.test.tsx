import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { UsageSection } from './UsageTab';

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

function usageResponse(items: Array<Record<string, unknown>>, total = items.length, page = 1, size = 20) {
  return jsonResponse({
    success: true,
    data: { resource: 'usage', items, total, page, size },
  });
}

const realUsageRow = {
  provider: 'openai',
  model: 'gpt-4.1-mini',
  scene: 'planning',
  tokens: '128k',
  cost: '42.8',
  latency_ms: 860,
  status: 'success',
};

describe('UsageSection real mode', () => {
  beforeEach(() => {
    vi.stubEnv('VITE_AGENT_MODE', 'real');
  });

  afterEach(() => {
    vi.unstubAllEnvs();
    vi.unstubAllGlobals();
    vi.clearAllMocks();
  });

  it('renders only fields returned by the real usage endpoint', async () => {
    const fetchMock = vi.fn().mockResolvedValue(usageResponse([realUsageRow]));
    vi.stubGlobal('fetch', fetchMock);

    render(<UsageSection onAction={vi.fn()} refreshNonce={0} />);

    expect(await screen.findByText('gpt-4.1-mini')).toBeInTheDocument();
    expect(screen.getByText('当前页 Token')).toBeInTheDocument();
    expect(screen.getByText('128.00K')).toBeInTheDocument();
    expect(screen.getByText('42.8')).toBeInTheDocument();
    expect(screen.queryByText('run_98218a')).not.toBeInTheDocument();
    expect(screen.queryByText('12.4M')).not.toBeInTheDocument();
    expect(String(fetchMock.mock.calls[0][0])).toContain('/api/admin/queries/usage?page=1&size=20');
  });

  it('passes status and text filters to the paginated endpoint', async () => {
    const fetchMock = vi.fn().mockImplementation((input: RequestInfo | URL) => {
      const url = new URL(String(input), 'http://foodmate.local');
      const status = url.searchParams.get('status');
      const query = url.searchParams.get('query');
      return Promise.resolve(
        usageResponse(
          [
            {
              ...realUsageRow,
              status: status ?? 'success',
              model: query ?? realUsageRow.model,
            },
          ],
          1,
        ),
      );
    });
    vi.stubGlobal('fetch', fetchMock);

    const user = userEvent.setup();
    render(<UsageSection onAction={vi.fn()} refreshNonce={0} />);
    expect(await screen.findByText('gpt-4.1-mini')).toBeInTheDocument();

    await user.click(screen.getByRole('combobox', { name: '结果筛选' }));
    await user.click(screen.getByRole('option', { name: '失败' }));
    await user.type(screen.getByRole('textbox', { name: '供应商 / 模型 / 场景' }), 'timeout-model');

    await waitFor(() => {
      const calls = fetchMock.mock.calls.map(([input]) => String(input));
      expect(calls.some((input) => input.includes('status=failed'))).toBe(true);
      expect(calls.some((input) => input.includes('query=timeout-model'))).toBe(true);
    });
  });

  it('shows an API error and retries without falling back to Figma usage data', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(
        jsonResponse({ success: false, error: { code: 'USAGE_UNAVAILABLE', message: '用量接口暂不可用' } }, 503),
      )
      .mockResolvedValueOnce(usageResponse([realUsageRow]));
    vi.stubGlobal('fetch', fetchMock);

    const user = userEvent.setup();
    render(<UsageSection onAction={vi.fn()} refreshNonce={0} />);

    expect(await screen.findByRole('alert')).toHaveTextContent('用量接口暂不可用');
    expect(screen.queryByText('run_98218a')).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '重试' }));

    expect(await screen.findByText('gpt-4.1-mini')).toBeInTheDocument();
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2));
  });

  it('creates a usage export with the current filters and downloads the server result once completed', async () => {
    let exportRequest: Record<string, unknown> | undefined;
    let downloadConsumedAt: string | null = null;
    const expiresAt = new Date(Date.now() + 60 * 60 * 1000).toISOString();
    const fetchMock = vi.fn().mockImplementation((input: RequestInfo | URL, init?: RequestInit) => {
      const url = new URL(String(input), 'http://foodmate.local');
      if (url.pathname === '/api/admin/queries/usage') return Promise.resolve(usageResponse([realUsageRow]));
      if (url.pathname === '/api/admin/exports' && init?.method === 'POST') {
        exportRequest = JSON.parse(String(init.body)) as Record<string, unknown>;
        return Promise.resolve(jsonResponse({ success: true, data: { export_job_id: 12 } }));
      }
      if (url.pathname === '/api/admin/exports/12') {
        return Promise.resolve(
          jsonResponse({
            success: true,
            data: {
              export_job_id: 12,
              resource: 'usage',
              status: 'completed',
              expires_at: expiresAt,
              completed_at: '2026-09-17T10:02:00Z',
              download_consumed_at: downloadConsumedAt,
              failure_code: null,
            },
          }),
        );
      }
      if (url.pathname === '/api/admin/exports/12/download' && init?.method === 'POST') {
        downloadConsumedAt = '2026-09-17T10:03:00Z';
        return Promise.resolve(
          jsonResponse({ success: true, data: { download_url: 'https://files.foodmate.local/12' } }),
        );
      }
      return Promise.resolve(jsonResponse({ success: true, data: {} }));
    });
    vi.stubGlobal('fetch', fetchMock);
    const openMock = vi.spyOn(window, 'open').mockImplementation(() => null);

    const user = userEvent.setup();
    render(<UsageSection onAction={vi.fn()} refreshNonce={0} />);
    expect(await screen.findByText('gpt-4.1-mini')).toBeInTheDocument();

    await user.click(screen.getByRole('combobox', { name: '结果筛选' }));
    await user.click(screen.getByRole('option', { name: '失败' }));
    await user.click(screen.getByRole('button', { name: '导出当前结果' }));

    await waitFor(() => expect(screen.getByText('导出任务 #12 当前状态：completed')).toBeInTheDocument());
    expect(exportRequest).toMatchObject({ resource: 'usage', status: 'failed' });
    expect(exportRequest?.fields).toEqual(['provider', 'model', 'scene', 'tokens', 'cost', 'latency_ms', 'status']);

    await user.click(screen.getByRole('button', { name: '下载 JSON' }));
    await waitFor(() =>
      expect(openMock).toHaveBeenCalledWith('https://files.foodmate.local/12', '_blank', 'noopener,noreferrer'),
    );
    expect(await screen.findByText('下载链接已生成，下载资格已消费一次。')).toBeInTheDocument();
  });

  it('cancels the usage request when the page is unmounted', async () => {
    let requestSignal: AbortSignal | undefined;
    const fetchMock = vi.fn((_input: RequestInfo | URL, init?: RequestInit) => {
      requestSignal = init?.signal ?? undefined;
      return new Promise<Response>(() => undefined);
    });
    vi.stubGlobal('fetch', fetchMock);

    const view = render(<UsageSection onAction={vi.fn()} refreshNonce={0} />);
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(1));
    expect(requestSignal?.aborted).toBe(false);

    view.unmount();

    expect(requestSignal?.aborted).toBe(true);
  });
});
