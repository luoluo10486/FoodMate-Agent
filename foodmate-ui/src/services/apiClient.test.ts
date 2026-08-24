import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { apiRequest } from './apiClient';

describe('apiClient authentication recovery', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.stubGlobal('fetch', vi.fn());
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it('refreshes once and retries the original request after a 401', async () => {
    const fetchMock = vi.mocked(fetch);
    fetchMock
      .mockResolvedValueOnce(new Response(JSON.stringify({ success: false }), { status: 401 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ success: true, data: null }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ success: true, data: { ok: true } }), { status: 200 }));

    await expect(apiRequest<{ ok: boolean }>('/api/users/me')).resolves.toEqual({ ok: true });
    expect(fetchMock).toHaveBeenCalledTimes(3);
    expect(fetchMock.mock.calls[1][0]).toBe('/api/auth/refresh');
    expect(fetchMock.mock.calls[2][0]).toBe('/api/users/me');
  });

  it('does not recursively refresh the refresh endpoint', async () => {
    window.history.pushState({}, '', '/login');
    const fetchMock = vi.mocked(fetch);
    fetchMock.mockResolvedValueOnce(
      new Response(JSON.stringify({ success: false, error: { code: 'AUTH_REFRESH_TOKEN_INVALID' } }), {
        status: 401,
      }),
    );

    await expect(apiRequest('/api/auth/refresh', { method: 'POST' })).rejects.toMatchObject({ code: 'AUTH_REQUIRED' });
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });
});
