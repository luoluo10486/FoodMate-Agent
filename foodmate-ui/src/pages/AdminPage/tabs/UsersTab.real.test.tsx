import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

let UsersSection: typeof import('./UsersTab').UsersSection;

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

describe('Admin 用户管理真实模式', () => {
  beforeEach(async () => {
    vi.stubEnv('VITE_AGENT_MODE', 'real');
    localStorage.setItem(
      'foodmate_auth_user',
      JSON.stringify({
        id: '99',
        username: 'admin',
        displayName: '真实管理员',
        role: 'admin',
        status: 'active',
        email: 'admin@example.com',
      }),
    );
    ({ UsersSection } = await import('./UsersTab'));
  });

  afterEach(() => {
    vi.unstubAllEnvs();
    vi.unstubAllGlobals();
    localStorage.clear();
    vi.clearAllMocks();
  });

  it('真实模式禁用没有后端接口的凭证重置，并保持操作回调不写入 Fixture', async () => {
    const fetchMock = vi.fn((input: RequestInfo | URL) => {
      const path = new URL(String(input), 'http://foodmate.local').pathname;
      if (path === '/api/admin/users') {
        return Promise.resolve(
          jsonResponse({
            success: true,
            data: [
              {
                user_id: 7,
                username: 'real-user',
                email: 'real@example.com',
                nickname: '真实用户',
                role: 'user',
                status: 'active',
                revision: 3,
              },
            ],
          }),
        );
      }
      if (path === '/api/admin/users/7/detail') {
        return Promise.resolve(
          jsonResponse({
            success: true,
            data: {
              profile: { user_id: 7, display_name: '真实用户', gender: '男' },
              login_sessions: [],
              business_sessions: { items: [], total: 0, page: 1, size: 50 },
              operation_history: { items: [], total: 0, page: 1, size: 50 },
            },
          }),
        );
      }
      return Promise.resolve(jsonResponse({ success: true, data: {} }));
    });
    vi.stubGlobal('fetch', fetchMock);

    const onAction = vi.fn();
    const user = userEvent.setup();
    render(<UsersSection onAction={onAction} />);

    const row = await screen.findByRole('row', { name: /7 real-user/ });
    expect(screen.getByRole('button', { name: '重置凭证' })).toBeDisabled();
    expect(screen.getByText('后端当前未提供凭证重置接口')).toBeInTheDocument();

    await user.click(within(row).getByRole('button', { name: '7 操作' }));
    await user.click(screen.getByRole('menuitem', { name: '锁定用户' }));

    expect(onAction).toHaveBeenCalledTimes(1);
    const action = onAction.mock.calls[0][0];
    action.onApply?.();
    expect(within(row).getByText('活跃')).toBeInTheDocument();
    expect(fetchMock.mock.calls.some(([input]) => String(input) === '/api/admin/users')).toBe(true);
  });

  it('管理操作触发列表刷新时同步重新读取当前用户详情', async () => {
    let detailRequestCount = 0;
    const fetchMock = vi.fn((input: RequestInfo | URL) => {
      const path = new URL(String(input), 'http://foodmate.local').pathname;
      if (path === '/api/admin/users') {
        return Promise.resolve(
          jsonResponse({
            success: true,
            data: [
              {
                user_id: 7,
                username: 'real-user',
                email: 'real@example.com',
                nickname: '真实用户',
                role: 'user',
                status: 'active',
                revision: 4,
              },
            ],
          }),
        );
      }
      if (path === '/api/admin/users/7/detail') {
        detailRequestCount += 1;
        return Promise.resolve(
          jsonResponse({
            success: true,
            data: {
              profile: {
                user_id: 7,
                display_name: detailRequestCount === 1 ? '首次详情' : '服务端刷新详情',
                gender: '男',
              },
              login_sessions: [],
              business_sessions: { items: [], total: 0, page: 1, size: 50 },
              operation_history: { items: [], total: 0, page: 1, size: 50 },
            },
          }),
        );
      }
      return Promise.resolve(jsonResponse({ success: true, data: {} }));
    });
    vi.stubGlobal('fetch', fetchMock);

    const view = render(<UsersSection onAction={vi.fn()} refreshNonce={0} />);
    expect(await screen.findByText('首次详情')).toBeInTheDocument();

    view.rerender(<UsersSection onAction={vi.fn()} refreshNonce={1} />);

    expect(await screen.findByText('服务端刷新详情')).toBeInTheDocument();
    expect(detailRequestCount).toBe(2);
  });
});
