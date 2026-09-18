import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

let RetentionSection: typeof import('./RetentionTab').RetentionSection;

function jsonResponse(data: unknown, status = 200) {
  return new Response(JSON.stringify({ success: true, data }), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

const purge = {
  request_id: 901,
  status: 'requested',
  resource_type: 'knowledge_document',
  resource_id: 42,
  eligible_at: '2026-08-22T00:00:00Z',
  task_count: 0,
};

const preflight = {
  request_id: 901,
  status: 'requested',
  resource_type: 'knowledge_document',
  resource_id: 42,
  policy_found: true,
  hard_delete_enabled: false,
  resource_soft_deleted: true,
  retention_elapsed: true,
  legal_hold_clear: true,
  task_contract_valid: true,
  ready_to_execute: false,
  tasks: [{ task_type: 'database', status: 'pending', attempt_count: 0, last_error_code: null }],
  blockers: ['RETENTION_HARD_DELETE_DISABLED'],
};

function renderRetention(onAction = vi.fn()) {
  return {
    ...render(
      <MemoryRouter initialEntries={['/admin/data-retention']}>
        <RetentionSection onAction={onAction} refreshNonce={0} />
      </MemoryRouter>,
    ),
    onAction,
  };
}

describe('RetentionSection real mode', () => {
  beforeEach(async () => {
    vi.stubEnv('VITE_AGENT_MODE', 'real');
    vi.resetModules();
    localStorage.setItem(
      'foodmate_auth_user',
      JSON.stringify({ id: '7', username: 'admin', displayName: 'Admin', role: 'admin', status: 'active' }),
    );
    ({ RetentionSection } = await import('./RetentionTab'));
  });

  afterEach(() => {
    vi.unstubAllEnvs();
    vi.unstubAllGlobals();
    localStorage.clear();
    vi.clearAllMocks();
  });

  it('creates a purge request and refreshes its detail and preflight from the server', async () => {
    const fetchMock = vi.fn().mockImplementation((input: RequestInfo | URL, _init?: RequestInit) => {
      const path = new URL(String(input), 'http://foodmate.local').pathname;
      if (path === '/api/admin/data-retention/purge-requests' && _init?.method === 'POST') {
        return Promise.resolve(jsonResponse(purge));
      }
      if (path === '/api/admin/data-retention/purge-requests/901/preflight') {
        return Promise.resolve(jsonResponse(preflight));
      }
      if (path === '/api/admin/data-retention/purge-requests/901') return Promise.resolve(jsonResponse(purge));
      return Promise.resolve(jsonResponse({}));
    });
    vi.stubGlobal('fetch', fetchMock);

    const user = userEvent.setup();
    const onAction = vi.fn();
    renderRetention(onAction);
    await user.type(screen.getByRole('spinbutton', { name: '清理资源 ID' }), '42');
    await user.click(screen.getByRole('button', { name: '提交清理申请' }));

    expect(onAction).toHaveBeenCalledWith(
      expect.objectContaining({
        action: '创建清理请求',
        targetType: 'retention_purge_request',
        targetId: '42',
      }),
    );
    const action = onAction.mock.calls[0][0] as { execute?: () => Promise<void> };
    await action.execute?.();

    expect(await screen.findByText('清理请求状态')).toBeInTheDocument();
    const createCall = fetchMock.mock.calls.find(
      ([input, init]) =>
        String(input) === '/api/admin/data-retention/purge-requests' &&
        (init as RequestInit | undefined)?.method === 'POST',
    );
    expect(JSON.parse(String(createCall?.[1]?.body))).toMatchObject({
      resource_type: 'knowledge_document',
      resource_id: 42,
      confirmed: true,
    });
    expect(new Headers(createCall?.[1]?.headers).get('Idempotency-Key')).toMatch(/^retention-purge-/);
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/admin/data-retention/purge-requests/901',
      expect.objectContaining({ method: 'GET' }),
    );
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/admin/data-retention/purge-requests/901/preflight',
      expect.objectContaining({ method: 'GET' }),
    );
  });

  it('uses real purge, approval, hold and release actions with role-gated controls', async () => {
    let approved = false;
    const fetchMock = vi.fn().mockImplementation((input: RequestInfo | URL, _init?: RequestInit) => {
      const path = new URL(String(input), 'http://foodmate.local').pathname;
      if (path.endsWith('/approve')) {
        approved = true;
        return Promise.resolve(jsonResponse({ ...purge, status: 'approved', task_count: 3 }));
      }
      if (path.endsWith('/preflight')) {
        return Promise.resolve(jsonResponse({ ...preflight, status: approved ? 'approved' : 'requested' }));
      }
      if (path.endsWith('/901')) {
        return Promise.resolve(
          jsonResponse({ ...purge, status: approved ? 'approved' : 'requested', task_count: approved ? 3 : 0 }),
        );
      }
      if (path === '/api/admin/data-retention/holds') {
        return Promise.resolve(
          jsonResponse({
            hold_id: 88,
            status: 'active',
            resource_type: 'knowledge_document',
            resource_id: 42,
            reason_code: 'legal_request',
          }),
        );
      }
      if (path.endsWith('/holds/88/release')) {
        return Promise.resolve(
          jsonResponse({
            hold_id: 88,
            status: 'released',
            resource_type: 'knowledge_document',
            resource_id: 42,
            reason_code: 'legal_request',
          }),
        );
      }
      return Promise.resolve(jsonResponse({}));
    });
    vi.stubGlobal('fetch', fetchMock);
    localStorage.setItem(
      'foodmate_auth_user',
      JSON.stringify({
        id: '8',
        username: 'superadmin',
        displayName: 'Superadmin',
        role: 'superadmin',
        status: 'active',
      }),
    );

    const user = userEvent.setup();
    const onAction = vi.fn();
    renderRetention(onAction);

    await user.type(screen.getByRole('spinbutton', { name: '清理请求 ID' }), '901');
    await user.click(screen.getByRole('button', { name: '读取详情与预检' }));
    expect(await screen.findByText('清理请求状态')).toBeInTheDocument();
    const approveButton = screen.getByRole('button', { name: '审批清理请求' });
    await user.click(approveButton);
    const approveAction = onAction.mock.calls.at(-1)?.[0] as { execute?: () => Promise<void> };
    await approveAction.execute?.();
    await waitFor(() => expect(screen.queryByRole('button', { name: '审批清理请求' })).not.toBeInTheDocument());

    await user.type(screen.getByRole('spinbutton', { name: '保留资源 ID' }), '42');
    await user.click(screen.getByRole('button', { name: '创建法律保留' }));
    const placeHoldAction = onAction.mock.calls.at(-1)?.[0] as { execute?: () => Promise<void> };
    await placeHoldAction.execute?.();
    expect(await screen.findByText('法律保留 #88')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '释放保留' }));
    const releaseAction = onAction.mock.calls.at(-1)?.[0] as { execute?: () => Promise<void> };
    await releaseAction.execute?.();
    await waitFor(() => expect(screen.getByText('released')).toBeInTheDocument());

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/admin/data-retention/purge-requests/901',
      expect.objectContaining({ method: 'GET' }),
    );
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/admin/data-retention/purge-requests/901/preflight',
      expect.objectContaining({ method: 'GET' }),
    );
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/admin/data-retention/purge-requests/901/approve',
      expect.objectContaining({ method: 'POST' }),
    );
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/admin/data-retention/holds',
      expect.objectContaining({ method: 'POST' }),
    );
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/admin/data-retention/holds/88/release',
      expect.objectContaining({ method: 'POST' }),
    );
  });

  it('keeps operator read-only and does not show write controls', async () => {
    localStorage.setItem(
      'foodmate_auth_user',
      JSON.stringify({ id: '9', username: 'operator', displayName: 'Operator', role: 'operator', status: 'active' }),
    );
    renderRetention();

    expect(screen.getByText(/当前角色为 operator/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '提交清理申请' })).toBeDisabled();
    expect(screen.getByRole('button', { name: '创建法律保留' })).toBeDisabled();
    expect(screen.queryByRole('button', { name: '审批清理请求' })).not.toBeInTheDocument();
  });

  it('shows a real query error and retries without falling back to fixture data', async () => {
    const fetchMock = vi.fn().mockImplementation((input: RequestInfo | URL) => {
      const path = new URL(String(input), 'http://foodmate.local').pathname;
      if (path.endsWith('/preflight') || path.endsWith('/901')) {
        const count = fetchMock.mock.calls.filter(([request]) => String(request).includes('/901')).length;
        if (count === 1) {
          return Promise.resolve(
            new Response(
              JSON.stringify({
                success: false,
                error: { code: 'RETENTION_NOT_FOUND', message: 'RETENTION_NOT_FOUND' },
              }),
              { status: 404, headers: { 'Content-Type': 'application/json' } },
            ),
          );
        }
        return Promise.resolve(jsonResponse(path.endsWith('/preflight') ? preflight : purge));
      }
      return Promise.resolve(jsonResponse({}));
    });
    vi.stubGlobal('fetch', fetchMock);

    const user = userEvent.setup();
    renderRetention();
    await user.type(screen.getByRole('spinbutton', { name: '清理请求 ID' }), '901');
    await user.click(screen.getByRole('button', { name: '读取详情与预检' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('RETENTION_NOT_FOUND');
    expect(screen.queryByText('清理请求状态')).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '重试' }));
    expect(await screen.findByText('清理请求状态')).toBeInTheDocument();
  });
});
