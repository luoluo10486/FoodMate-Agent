import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AdminPage } from './AdminPage';

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

function renderAdmin(initialEntry: string) {
  return render(
    <MemoryRouter initialEntries={[initialEntry]}>
      <Routes>
        <Route path="/admin/*" element={<AdminPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('AdminPage real mode fixture isolation', () => {
  beforeEach(() => {
    vi.stubEnv('VITE_AGENT_MODE', 'real');
    localStorage.setItem(
      'foodmate_auth_user',
      JSON.stringify({
        id: '7',
        username: 'admin',
        displayName: '真实管理员',
        role: 'admin',
        status: 'active',
        email: 'admin@example.com',
      }),
    );
    vi.stubGlobal(
      'fetch',
      vi.fn().mockImplementation((input: RequestInfo | URL) => {
        const path = new URL(String(input), 'http://foodmate.local').pathname;
        if (path === '/api/admin/dashboard') {
          return Promise.resolve(
            jsonResponse({
              success: true,
              data: {
                overview_metrics: [
                  { label: '真实运行总量', value: '1', hint: '-', tone: 'green' },
                  { label: '真实成功率', value: '100%', hint: '-', tone: 'teal' },
                  { label: '真实成本', value: '0', hint: '-', tone: 'amber' },
                ],
                runs: [],
                tool_calls: [],
                sql_audits: [],
                traces: [],
                tools: [],
                usage: [],
                knowledge: [],
                deleted: [],
                operation_audits: [],
              },
            }),
          );
        }
        if (path === '/api/admin/queries/runs') {
          return Promise.resolve(
            jsonResponse({
              success: true,
              data: {
                resource: 'runs',
                items: [
                  {
                    agent_run_id: 7001,
                    session_id: 8001,
                    intent: 'REAL_QUERY',
                    status: 'completed',
                    trace_id: 'trace-real-7001',
                    duration_ms: 120,
                    actor_ref: 'real_actor',
                  },
                ],
                total: 1,
                page: 1,
                size: 6,
              },
            }),
          );
        }
        return Promise.resolve(jsonResponse({ success: true, data: {} }));
      }),
    );
  });

  afterEach(() => {
    vi.unstubAllEnvs();
    vi.unstubAllGlobals();
    localStorage.clear();
    vi.clearAllMocks();
  });

  it('ignores a Figma run fixture query and renders the real overview consumer', async () => {
    renderAdmin('/admin?state=run-detail');

    expect(await screen.findByText('真实运行总量')).toBeInTheDocument();
    expect(screen.getByText('real_actor')).toBeInTheDocument();
    expect(screen.queryByText('Agent 运行控制台')).not.toBeInTheDocument();
    expect(screen.queryByText("Anddy's Lab")).not.toBeInTheDocument();
    expect(screen.queryByRole('region', { name: '执行事件追踪' })).not.toBeInTheDocument();

    const fetchCalls = (fetch as ReturnType<typeof vi.fn>).mock.calls.map(([input]) => String(input));
    expect(fetchCalls.some((input) => input.includes('/api/admin/queries/runs'))).toBe(true);
  });

  it('does not open an operation fixture dialog in real mode', async () => {
    renderAdmin('/admin?state=op-confirm');

    expect(await screen.findByText('真实运行总量')).toBeInTheDocument();
    expect(screen.queryByRole('dialog', { name: '确认停用工具' })).not.toBeInTheDocument();
    expect(screen.queryByText('已注册工具')).not.toBeInTheDocument();
  });

  it('概览读取失败后支持重新请求真实数据', async () => {
    const user = userEvent.setup();
    const dashboardResponses = [
      jsonResponse({ success: false, error: { code: 'ADMIN_UNAVAILABLE', message: '管理查询暂不可用' } }, 503),
      jsonResponse({
        success: true,
        data: {
          overview_metrics: [{ label: '重试后运行总量', value: '2', hint: '-', tone: 'green' }],
          runs: [],
          tool_calls: [],
          sql_audits: [],
          traces: [],
          tools: [],
          usage: [],
          knowledge: [],
          deleted: [],
          operation_audits: [],
        },
      }),
    ];
    const runResponses = [
      jsonResponse({ success: false, error: { code: 'ADMIN_UNAVAILABLE', message: '运行查询暂不可用' } }, 503),
      jsonResponse({ success: true, data: { items: [], total: 0, page: 1, size: 6 } }),
    ];
    vi.stubGlobal(
      'fetch',
      vi.fn((input: RequestInfo | URL) => {
        const path = new URL(String(input), 'http://foodmate.local').pathname;
        if (path === '/api/admin/dashboard') return Promise.resolve(dashboardResponses.shift() ?? jsonResponse({}));
        if (path === '/api/admin/queries/runs') return Promise.resolve(runResponses.shift() ?? jsonResponse({}));
        return Promise.resolve(jsonResponse({ success: true, data: {} }));
      }),
    );

    renderAdmin('/admin');

    expect(await screen.findByText('管理查询暂不可用')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '重试' }));
    expect(await screen.findByText('重试后运行总量')).toBeInTheDocument();
  });

  it('同一确认周期内重复确认只发起一个真实工具状态请求', async () => {
    let resolveMutation: ((response: Response) => void) | undefined;
    const fetchMock = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const path = new URL(String(input), 'http://foodmate.local').pathname;
      if (path === '/api/admin/tools/registry') {
        return Promise.resolve(
          jsonResponse({
            success: true,
            data: {
              tools: [
                {
                  tool_id: 720005,
                  name: 'nutrition_lookup',
                  display_name: 'Nutrition lookup',
                  description: 'Look up nutrition data.',
                  category: 'read',
                  risk_level: 'medium',
                  availability_scope: 'read-only',
                  status: 'active',
                  current_version: 'v1',
                  version: 'v1',
                  input_schema: { type: 'object' },
                  output_schema: { type: 'object' },
                  permissions: { approval: 'not_required' },
                  timeout_ms: 10000,
                  retryable: true,
                  idempotent: true,
                  published_at: null,
                  revision: 7,
                },
              ],
            },
          }),
        );
      }
      if (path === '/api/admin/tools/nutrition_lookup/status') {
        return new Promise<Response>((resolve) => {
          resolveMutation = resolve;
          init?.signal?.addEventListener('abort', () => resolve(jsonResponse({ success: false })), {
            once: true,
          });
        });
      }
      return Promise.resolve(jsonResponse({ success: true, data: {} }));
    });
    vi.stubGlobal('fetch', fetchMock);

    renderAdmin('/admin/tools?tab=registry');
    expect(await screen.findByText('nutrition_lookup')).toBeInTheDocument();
    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: '配置详情' }));
    await user.click(screen.getByRole('button', { name: '停用工具' }));

    const confirmButton = screen.getByRole('button', { name: '确认停用' });
    fireEvent.click(confirmButton);
    fireEvent.click(confirmButton);

    await waitFor(() => {
      const mutationCalls = fetchMock.mock.calls.filter(([input]) =>
        String(input).includes('/api/admin/tools/nutrition_lookup/status'),
      );
      expect(mutationCalls).toHaveLength(1);
    });

    await waitFor(() => expect(resolveMutation).toBeDefined());
    resolveMutation?.(jsonResponse({ success: true, data: { revision: 8 } }));
    expect(await screen.findByText('操作成功：工具 nutrition_lookup 已成功停用')).toBeInTheDocument();
  });

  it('卸载管理页面时中止中心操作且不进入失败态', async () => {
    const user = userEvent.setup();
    let mutationSignal: AbortSignal | undefined;
    const fetchMock = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const path = new URL(String(input), 'http://foodmate.local').pathname;
      if (path === '/api/admin/tools/registry') {
        return Promise.resolve(
          jsonResponse({
            success: true,
            data: {
              tools: [
                {
                  tool_id: 720005,
                  name: 'nutrition_lookup',
                  display_name: 'Nutrition lookup',
                  description: 'Look up nutrition data.',
                  category: 'read',
                  risk_level: 'medium',
                  availability_scope: 'read-only',
                  status: 'active',
                  current_version: 'v1',
                  version: 'v1',
                  input_schema: { type: 'object' },
                  output_schema: { type: 'object' },
                  permissions: { approval: 'not_required' },
                  timeout_ms: 10000,
                  retryable: true,
                  idempotent: true,
                  published_at: null,
                  revision: 7,
                },
              ],
            },
          }),
        );
      }
      if (path === '/api/admin/tools/nutrition_lookup/status') {
        mutationSignal = init?.signal ?? undefined;
        return new Promise<Response>((_resolve, reject) => {
          init?.signal?.addEventListener('abort', () => reject(new DOMException('aborted', 'AbortError')), {
            once: true,
          });
        });
      }
      return Promise.resolve(jsonResponse({}));
    });
    vi.stubGlobal('fetch', fetchMock);

    const view = renderAdmin('/admin/tools?tab=registry');
    expect(await screen.findByText('nutrition_lookup')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '配置详情' }));
    await user.click(screen.getByRole('button', { name: '停用工具' }));
    await user.click(screen.getByRole('button', { name: '确认停用' }));
    await waitFor(() => expect(mutationSignal).toBeDefined());

    view.unmount();

    expect(mutationSignal?.aborted).toBe(true);
  });
});
