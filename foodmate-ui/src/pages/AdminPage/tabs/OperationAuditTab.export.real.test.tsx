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
            expires_at: '2026-09-15T10:00:00Z',
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
});
