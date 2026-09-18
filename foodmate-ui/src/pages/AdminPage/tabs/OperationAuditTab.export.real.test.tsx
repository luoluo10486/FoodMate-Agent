import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

let OperationAuditSection: typeof import('./OperationAuditTab').OperationAuditSection;

function ok(data: unknown) {
  return new Response(JSON.stringify({ success: true, data }), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  });
}

const auditReport = {
  generated_at: '2026-09-14T10:01:00Z',
  stale_threshold_minutes: 15,
  status: 'healthy',
  checks: [],
};

describe('OperationAuditSection export lifecycle', () => {
  beforeEach(async () => {
    vi.stubEnv('VITE_AGENT_MODE', 'real');
    localStorage.setItem(
      'foodmate_auth_user',
      JSON.stringify({ id: '7', username: 'admin', displayName: 'Admin', role: 'admin', status: 'active' }),
    );
    vi.resetModules();
    ({ OperationAuditSection } = await import('./OperationAuditTab'));
  });

  afterEach(() => {
    vi.unstubAllEnvs();
    vi.unstubAllGlobals();
    localStorage.clear();
    vi.clearAllMocks();
  });

  it('polls queued exports until the server reports completion', async () => {
    let statusReads = 0;
    const expiresAt = new Date(Date.now() + 60 * 60 * 1000).toISOString();
    const fetchMock = vi.fn().mockImplementation((input: RequestInfo | URL, init?: RequestInit) => {
      const url = new URL(String(input), 'http://foodmate.local');
      if (url.pathname === '/api/admin/audit-reports/current') return Promise.resolve(ok(auditReport));
      if (url.pathname === '/api/admin/queries/operation-audits')
        return Promise.resolve(ok({ items: [], total: 0, page: 1, size: 8 }));
      if (url.pathname === '/api/admin/exports' && init?.method === 'POST')
        return Promise.resolve(ok({ export_job_id: 9 }));
      if (url.pathname === '/api/admin/exports/9') {
        statusReads += 1;
        return Promise.resolve(
          ok({
            export_job_id: 9,
            resource: 'operation-audits',
            status: statusReads === 1 ? 'queued' : 'completed',
            expires_at: expiresAt,
            completed_at: statusReads === 1 ? null : '2026-09-14T10:02:00Z',
            download_consumed_at: null,
            failure_code: null,
          }),
        );
      }
      return Promise.resolve(ok({}));
    });
    vi.stubGlobal('fetch', fetchMock);

    const user = userEvent.setup();
    render(<OperationAuditSection />);
    await user.click(await screen.findByRole('button', { name: '导出当前结果' }));

    await waitFor(() => expect(statusReads).toBe(1));
    expect(screen.getByText('导出任务 #9 当前状态：queued')).toBeInTheDocument();

    await waitFor(() => expect(statusReads).toBe(2), { timeout: 3000 });
    expect(screen.getByText('导出任务 #9 当前状态：completed')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '下载 JSON' })).toBeInTheDocument();
  });

  it('shows an expired state when a completed export is past its expiry time', async () => {
    const fetchMock = vi.fn().mockImplementation((input: RequestInfo | URL) => {
      const url = new URL(String(input), 'http://foodmate.local');
      if (url.pathname === '/api/admin/audit-reports/current') return Promise.resolve(ok(auditReport));
      if (url.pathname === '/api/admin/queries/operation-audits')
        return Promise.resolve(ok({ items: [], total: 0, page: 1, size: 8 }));
      if (url.pathname === '/api/admin/exports' && url.pathname.endsWith('/exports'))
        return Promise.resolve(ok({ export_job_id: 10 }));
      if (url.pathname === '/api/admin/exports/10')
        return Promise.resolve(
          ok({
            export_job_id: 10,
            resource: 'operation-audits',
            status: 'completed',
            expires_at: new Date(Date.now() - 60 * 1000).toISOString(),
            completed_at: '2026-09-14T10:02:00Z',
            download_consumed_at: null,
            failure_code: null,
          }),
        );
      return Promise.resolve(ok({}));
    });
    vi.stubGlobal('fetch', fetchMock);

    const user = userEvent.setup();
    render(<OperationAuditSection />);
    await user.click(await screen.findByRole('button', { name: '导出当前结果' }));

    await waitFor(() => expect(screen.getByText('导出任务 #10 当前状态：expired')).toBeInTheDocument());
    expect(screen.queryByRole('button', { name: '下载 JSON' })).not.toBeInTheDocument();
  });

  it('shows the backend failure code for a failed export', async () => {
    const fetchMock = vi.fn().mockImplementation((input: RequestInfo | URL) => {
      const url = new URL(String(input), 'http://foodmate.local');
      if (url.pathname === '/api/admin/audit-reports/current') return Promise.resolve(ok(auditReport));
      if (url.pathname === '/api/admin/queries/operation-audits')
        return Promise.resolve(ok({ items: [], total: 0, page: 1, size: 8 }));
      if (url.pathname === '/api/admin/exports' && url.pathname.endsWith('/exports'))
        return Promise.resolve(ok({ export_job_id: 11 }));
      if (url.pathname === '/api/admin/exports/11')
        return Promise.resolve(
          ok({
            export_job_id: 11,
            resource: 'operation-audits',
            status: 'failed',
            expires_at: null,
            completed_at: null,
            download_consumed_at: null,
            failure_code: 'OBJECT_STORAGE_UNAVAILABLE',
          }),
        );
      return Promise.resolve(ok({}));
    });
    vi.stubGlobal('fetch', fetchMock);

    const user = userEvent.setup();
    render(<OperationAuditSection />);
    await user.click(await screen.findByRole('button', { name: '导出当前结果' }));

    await waitFor(() =>
      expect(
        screen.getByText('导出任务 #11 当前状态：failed · 错误码：OBJECT_STORAGE_UNAVAILABLE'),
      ).toBeInTheDocument(),
    );
    expect(screen.queryByRole('button', { name: '下载 JSON' })).not.toBeInTheDocument();
  });
});
