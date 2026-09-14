import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  confirmPasswordReset,
  csrfToken,
  getAuthStatus,
  loadCurrentUser,
  login,
  logout,
  requestPasswordReset,
} from './authService';

describe('authService real identity hydration', () => {
  beforeEach(() => {
    vi.stubEnv('VITE_AGENT_MODE', 'real');
    localStorage.clear();
    vi.stubGlobal('fetch', vi.fn());
  });

  afterEach(() => {
    vi.unstubAllEnvs();
    vi.unstubAllGlobals();
    localStorage.clear();
  });

  it('reads the current user after login instead of caching the partial auth response', async () => {
    const fetchMock = vi.mocked(fetch);
    fetchMock
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            success: true,
            data: {
              user_id: 7,
              username: 'real-user',
              role: 'user',
              session_expires_at: '2026-09-14T00:00:00Z',
            },
          }),
          { status: 200 },
        ),
      )
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            success: true,
            data: {
              user_id: 7,
              username: 'real-user',
              email: 'real@example.com',
              nickname: '真实用户',
              role: 'user',
              status: 'active',
              gender: '女',
            },
          }),
          { status: 200 },
        ),
      );

    await expect(login({ username: 'real-user', password: 'StrongPass99!', rememberMe: true })).resolves.toMatchObject({
      id: '7',
      displayName: '真实用户',
      email: 'real@example.com',
      gender: '女',
    });

    expect(fetchMock.mock.calls.map(([url]) => url)).toEqual(['/api/auth/login', '/api/users/me']);
    expect(JSON.parse(localStorage.getItem('foodmate_auth_user') ?? '{}')).toMatchObject({
      email: 'real@example.com',
      gender: '女',
    });
  });

  it.each([
    ['active', 'authenticated'],
    ['disabled', 'disabled'],
    ['locked', 'locked'],
  ] as const)('maps persisted %s users to the matching auth status', (status, expected) => {
    localStorage.setItem('foodmate_auth_user', JSON.stringify({ id: '7', status }));

    expect(getAuthStatus()).toBe(expected);
  });

  it('does not trust a persisted identity without an active status', () => {
    localStorage.setItem('foodmate_auth_user', JSON.stringify({ id: '7', username: 'legacy-user' }));

    expect(getAuthStatus()).toBe('expired');
  });

  it('clears local identity without calling remote logout after the server already revoked it', async () => {
    localStorage.setItem('foodmate_auth_user', JSON.stringify({ id: '7', status: 'active' }));

    await logout({ skipRemote: true });

    expect(fetch).not.toHaveBeenCalled();
    expect(localStorage.getItem('foodmate_auth_user')).toBeNull();
  });

  it('clears the persisted identity only after the real logout request succeeds', async () => {
    localStorage.setItem('foodmate_auth_user', JSON.stringify({ id: '7', username: 'real-user' }));
    vi.mocked(fetch).mockResolvedValueOnce(
      new Response(JSON.stringify({ success: true, data: null }), { status: 200 }),
    );

    await logout();

    expect(fetch).toHaveBeenCalledWith('/api/auth/logout', expect.objectContaining({ method: 'POST' }));
    expect(localStorage.getItem('foodmate_auth_user')).toBeNull();
  });

  it('sends password reset requests through the real auth endpoints', async () => {
    vi.mocked(fetch)
      .mockResolvedValueOnce(new Response(JSON.stringify({ success: true, data: null }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ success: true, data: null }), { status: 200 }));

    await requestPasswordReset('real@example.com');
    await confirmPasswordReset('reset-token', 'StrongPass99!');

    expect(fetch).toHaveBeenNthCalledWith(
      1,
      '/api/auth/password-reset/request',
      expect.objectContaining({ method: 'POST', body: JSON.stringify({ email: 'real@example.com' }) }),
    );
    expect(fetch).toHaveBeenNthCalledWith(
      2,
      '/api/auth/password-reset/confirm',
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({ token: 'reset-token', new_password: 'StrongPass99!' }),
      }),
    );
  });

  it('forwards one cancellation signal across the auth request chain', async () => {
    const controller = new AbortController();
    vi.mocked(fetch)
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            success: true,
            data: { user_id: 7, username: 'real-user', role: 'user', session_expires_at: '2026-09-14T00:00:00Z' },
          }),
          { status: 200 },
        ),
      )
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            success: true,
            data: { user_id: 7, username: 'real-user', email: 'real@example.com', role: 'user', status: 'active' },
          }),
          { status: 200 },
        ),
      )
      .mockResolvedValueOnce(new Response(JSON.stringify({ success: true, data: null }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ success: true, data: null }), { status: 200 }));

    await login({ username: 'real-user', password: 'StrongPass99!', rememberMe: true }, controller.signal);
    await requestPasswordReset('real@example.com', controller.signal);
    await confirmPasswordReset('reset-token', 'StrongPass99!', controller.signal);

    expect(vi.mocked(fetch).mock.calls.every(([, init]) => init?.signal === controller.signal)).toBe(true);
  });

  it('preserves padding characters in the CSRF cookie value', () => {
    document.cookie = 'foodmate_csrf=token==';

    expect(csrfToken()).toBe('token==');

    document.cookie = 'foodmate_csrf=; Max-Age=0';
  });

  it('preserves the backend avatar endpoint when hydrating a real user', async () => {
    vi.mocked(fetch).mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          success: true,
          data: {
            user_id: 7,
            username: 'real-user',
            email: 'real@example.com',
            role: 'user',
            status: 'active',
            gender: '女',
            avatar_url: '/api/users/me/avatar',
          },
        }),
        { status: 200 },
      ),
    );

    await expect(loadCurrentUser()).resolves.toMatchObject({ avatarUrl: '/api/users/me/avatar' });
    expect(JSON.parse(localStorage.getItem('foodmate_auth_user') ?? '{}')).toMatchObject({
      avatarUrl: '/api/users/me/avatar',
    });
  });
});
