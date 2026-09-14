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

  it('shares one refresh request when concurrent calls receive 401', async () => {
    const fetchMock = vi.mocked(fetch);
    fetchMock
      .mockResolvedValueOnce(new Response(JSON.stringify({ success: false }), { status: 401 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ success: false }), { status: 401 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ success: true, data: null }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ success: true, data: { id: 1 } }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ success: true, data: { id: 2 } }), { status: 200 }));

    await expect(Promise.all([apiRequest('/api/users/me'), apiRequest('/api/sessions')])).resolves.toEqual([
      { id: 1 },
      { id: 2 },
    ]);
    expect(fetchMock.mock.calls.filter(([url]) => url === '/api/auth/refresh')).toHaveLength(1);
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

  it('keeps authentication business errors on the auth page', async () => {
    window.history.pushState({}, '', '/login');
    const fetchMock = vi.mocked(fetch);
    fetchMock.mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          success: false,
          error: { code: 'AUTH_INVALID_CREDENTIALS', message: '用户名或密码错误' },
        }),
        { status: 401 },
      ),
    );

    await expect(apiRequest('/api/auth/login', { method: 'POST', body: '{}' })).rejects.toMatchObject({
      code: 'AUTH_INVALID_CREDENTIALS',
      status: 401,
    });
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('preserves account lock and disable codes from forbidden responses', async () => {
    const fetchMock = vi.mocked(fetch);
    fetchMock.mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          success: false,
          error: { code: 'AUTH_ACCOUNT_LOCKED', message: '账号被锁定' },
        }),
        { status: 403 },
      ),
    );

    await expect(apiRequest('/api/auth/login', { method: 'POST', body: '{}' })).rejects.toMatchObject({
      code: 'AUTH_ACCOUNT_LOCKED',
      status: 403,
    });
  });

  it('accepts an empty 204 response as a successful void request', async () => {
    const fetchMock = vi.mocked(fetch);
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 }));

    await expect(apiRequest<void>('/api/users/me/sessions/7', { method: 'DELETE' })).resolves.toBeUndefined();
  });

  it('keeps multipart boundaries untouched while applying the shared CSRF policy', async () => {
    const fetchMock = vi.mocked(fetch);
    document.cookie = 'foodmate_csrf=test-csrf-token';
    fetchMock.mockResolvedValueOnce(
      new Response(JSON.stringify({ success: true, data: { document_id: 7 } }), { status: 200 }),
    );

    const form = new FormData();
    form.append('file', new File(['content'], 'meal.txt', { type: 'text/plain' }));
    await expect(
      apiRequest<{ document_id: number }>('/api/admin/knowledge', { method: 'POST', body: form }),
    ).resolves.toEqual({
      document_id: 7,
    });

    const init = fetchMock.mock.calls[0][1];
    const headers = new Headers(init?.headers);
    expect(headers.get('X-CSRF-Token')).toBe('test-csrf-token');
    expect(headers.get('Content-Type')).toBeNull();
    expect(init?.body).toBe(form);
  });

  it('keeps aborted requests distinguishable from network failures', async () => {
    const abortError = new DOMException('The operation was aborted.', 'AbortError');
    vi.mocked(fetch).mockRejectedValueOnce(abortError);

    await expect(apiRequest('/api/sessions', { signal: new AbortController().signal })).rejects.toMatchObject({
      name: 'AbortError',
    });
  });
});
