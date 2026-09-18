import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

let DeletedSection: typeof import('./DeletedResourcesTab').DeletedSection;

function ok(data: unknown) {
  return new Response(JSON.stringify({ success: true, data }), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  });
}

describe('DeletedSection real mode', () => {
  beforeEach(async () => {
    vi.stubEnv('VITE_AGENT_MODE', 'real');
    localStorage.setItem(
      'foodmate_auth_user',
      JSON.stringify({
        id: '7',
        username: 'admin',
        displayName: 'Admin',
        role: 'admin',
        status: 'active',
      }),
    );
    vi.resetModules();
    ({ DeletedSection } = await import('./DeletedResourcesTab'));
  });

  afterEach(() => {
    vi.unstubAllEnvs();
    vi.unstubAllGlobals();
    localStorage.clear();
    vi.clearAllMocks();
  });

  it('clears the real list on failure and retries the server query', async () => {
    const fetchMock = vi
      .fn()
      .mockRejectedValueOnce(new Error('deleted query unavailable'))
      .mockResolvedValueOnce(
        ok({
          items: [
            {
              resource_type: 'food_log',
              resource_id: 77,
              owner_ref: 'user-hash',
              deleted_at: '2026-09-14T10:00:00Z',
              reason: '用户请求删除',
              restorable: true,
              revision: 3,
            },
          ],
          total: 1,
          page: 1,
          size: 4,
        }),
      );
    vi.stubGlobal('fetch', fetchMock);

    render(<DeletedSection onAction={vi.fn()} />);

    expect(await screen.findByRole('alert')).toHaveTextContent('网络连接失败');
    expect(screen.queryByText('77')).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: '重试' }));

    expect(await screen.findByText('77')).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(fetchMock.mock.calls[0][1]).toEqual(expect.objectContaining({ signal: expect.any(AbortSignal) }));
  });

  it('does not apply a late response after a new filter request starts', async () => {
    const firstResponse = new Promise<Response>(() => undefined);
    const fetchMock = vi.fn().mockReturnValue(firstResponse);
    vi.stubGlobal('fetch', fetchMock);

    const user = userEvent.setup();
    render(<DeletedSection onAction={vi.fn()} />);
    await user.click(screen.getByRole('combobox', { name: '资源类型筛选' }));
    await user.click(screen.getByRole('option', { name: '记录' }));

    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect((fetchMock.mock.calls[0][1] as RequestInit).signal).toBeInstanceOf(AbortSignal);
    expect((fetchMock.mock.calls[0][1] as RequestInit).signal).not.toBe(
      (fetchMock.mock.calls[1][1] as RequestInit).signal,
    );
  });
});
