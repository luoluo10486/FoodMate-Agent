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
});
