import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { login } from './authService';

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
});
