import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { getAuthSessions, getDataExport } from './accountService';

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
});
