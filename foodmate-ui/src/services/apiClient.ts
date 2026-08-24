export type ApiErrorCode = 'AUTH_REQUIRED' | 'FORBIDDEN' | 'NETWORK_ERROR' | 'SERVER_ERROR' | string;

export class ApiError extends Error {
  constructor(
    public readonly code: ApiErrorCode,
    message: string,
    public readonly status?: number,
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

type ApiEnvelope<T> = { success: boolean; data: T; error?: { code: string; message: string } };
const baseUrl = import.meta.env.DEV ? '' : ((import.meta.env.VITE_API_BASE_URL as string | undefined) ?? '');
const unsafeMethods = new Set(['POST', 'PUT', 'PATCH', 'DELETE']);
let refreshInFlight: Promise<void> | undefined;

function csrfToken() {
  return document.cookie
    .split('; ')
    .find((value) => value.startsWith('foodmate_csrf='))
    ?.split('=')
    .slice(1)
    .join('=');
}

async function send(path: string, init: RequestInit): Promise<Response> {
  const method = (init.method ?? 'GET').toUpperCase();
  const headers = new Headers(init.headers);
  if (init.body && !(init.body instanceof FormData) && !headers.has('Content-Type'))
    headers.set('Content-Type', 'application/json');
  const csrf = csrfToken();
  if (unsafeMethods.has(method) && csrf) headers.set('X-CSRF-Token', csrf);
  try {
    return await fetch(`${baseUrl}${path}`, { ...init, method, credentials: 'include', headers });
  } catch {
    throw new ApiError('NETWORK_ERROR', '网络连接失败，请检查网络后重试');
  }
}

async function envelope<T>(response: Response): Promise<ApiEnvelope<T> | undefined> {
  let body: ApiEnvelope<T> | undefined;
  try {
    body = (await response.json()) as ApiEnvelope<T>;
  } catch {
    body = undefined;
  }
  return body;
}

async function refreshAuthSession(): Promise<void> {
  if (!refreshInFlight) {
    refreshInFlight = (async () => {
      const response = await send('/api/auth/refresh', { method: 'POST' });
      const body = await envelope<void>(response);
      if (!response.ok || !body?.success)
        throw new ApiError(
          body?.error?.code ?? 'AUTH_REFRESH_TOKEN_INVALID',
          body?.error?.message ?? '登录已失效，请重新登录',
          response.status,
        );
    })().finally(() => {
      refreshInFlight = undefined;
    });
  }
  await refreshInFlight;
}

function redirectToLogin(): void {
  localStorage.removeItem('foodmate_auth_user');
  const redirect = `${window.location.pathname}${window.location.search}`;
  if (window.location.pathname !== '/login') window.location.assign(`/login?redirect=${encodeURIComponent(redirect)}`);
}

export async function apiRequest<T>(path: string, init: RequestInit = {}): Promise<T> {
  let response = await send(path, init);
  if (response.status === 401 && path !== '/api/auth/refresh' && !path.startsWith('/api/auth/')) {
    try {
      await refreshAuthSession();
      response = await send(path, init);
    } catch {
      redirectToLogin();
      throw new ApiError('AUTH_REQUIRED', '登录已失效，请重新登录', 401);
    }
  }
  const body = await envelope<T>(response);
  if (response.status === 401) {
    redirectToLogin();
    throw new ApiError('AUTH_REQUIRED', '登录已失效，请重新登录', 401);
  }
  if (response.status === 403) throw new ApiError('FORBIDDEN', body?.error?.message ?? '当前账号无权执行此操作', 403);
  if (!response.ok || !body?.success)
    throw new ApiError(
      body?.error?.code ?? 'SERVER_ERROR',
      body?.error?.message ?? `请求失败（${response.status}）`,
      response.status,
    );
  return body.data;
}
