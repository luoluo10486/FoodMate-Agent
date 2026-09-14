import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  changePassword,
  deleteAvatar,
  downloadDataExport,
  getAuthSessions,
  getDataExport,
  requestAccountDeletion,
  requestDataExport,
  revokeAllAuthSessions,
  revokeAuthSession,
  updateProfile,
  uploadAvatar,
} from './accountService';

describe('accountService export response mapping', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn());
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('accepts the camelCase export job shape emitted by the current backend record', async () => {
    vi.mocked(fetch).mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          success: true,
          data: {
            exportJobId: 42,
            status: 'RUNNING',
            expiresAt: '2026-09-13T12:00:00Z',
            completedAt: null,
            downloadConsumedAt: null,
            failureCode: null,
          },
        }),
        { status: 200 },
      ),
    );

    await expect(getDataExport(42)).resolves.toEqual({
      export_job_id: 42,
      status: 'RUNNING',
      expires_at: '2026-09-13T12:00:00Z',
      completed_at: undefined,
      download_consumed_at: undefined,
      failure_code: undefined,
    });
  });

  it('passes a cancellation signal to the auth session request', async () => {
    const controller = new AbortController();
    vi.mocked(fetch).mockResolvedValueOnce(new Response(JSON.stringify({ success: true, data: [] }), { status: 200 }));

    await expect(getAuthSessions(controller.signal)).resolves.toEqual([]);

    expect(fetch).toHaveBeenCalledWith(
      '/api/users/me/sessions',
      expect.objectContaining({ signal: controller.signal }),
    );
  });

  it('forwards one cancellation signal across account mutations and export requests', async () => {
    const controller = new AbortController();
    vi.mocked(fetch).mockImplementation(async (input, init) => {
      const path = String(input);
      const method = init?.method ?? 'GET';
      let data: unknown = null;
      if (path.endsWith('/export')) data = { export_job_id: 42 };
      if (path.endsWith('/export/42')) data = { exportJobId: 42, status: 'completed' };
      if (path.endsWith('/export/42/download')) data = { download_url: 'https://example.com/export.zip' };
      if (path.endsWith('/avatar') && method === 'POST')
        data = { avatar_asset_id: 9, avatar_url: '/api/users/me/avatar', mime_type: 'image/png', size_bytes: 4 };
      return new Response(JSON.stringify({ success: true, data }), { status: 200 });
    });

    const file = new File(['test'], 'avatar.png', { type: 'image/png' });
    await updateProfile({ display_name: '真实用户' }, controller.signal);
    await changePassword('current-password', 'StrongPass99!', controller.signal);
    await revokeAuthSession(7, controller.signal);
    await revokeAllAuthSessions(controller.signal);
    await expect(uploadAvatar(file, controller.signal)).resolves.toMatchObject({
      avatar_url: '/api/users/me/avatar',
    });
    await deleteAvatar(controller.signal);
    await requestDataExport(controller.signal);
    await getDataExport(42, controller.signal);
    await downloadDataExport(42, controller.signal);
    await requestAccountDeletion('DELETE_MY_ACCOUNT', 'current-password', controller.signal);

    expect(vi.mocked(fetch).mock.calls).toHaveLength(10);
    expect(vi.mocked(fetch).mock.calls.every(([, init]) => init?.signal === controller.signal)).toBe(true);
  });
});
