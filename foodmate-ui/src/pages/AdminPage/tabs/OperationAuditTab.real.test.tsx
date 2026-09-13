import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

let OperationAuditSection: typeof import('./OperationAuditTab').OperationAuditSection;

function ok(data: unknown) {
  return new Response(JSON.stringify({ success: true, data }), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  });
}

const auditRow = {
  operator_id: 7,
  action: '查看运行详情',
  target_type: 'agent_run',
  target_id: '42',
  result: 'success',
  request_id: 'req-audit-42',
  trace_id: 'trace-audit-42',
  created_at: '2026-09-14T10:00:00Z',
  request_summary: '查看运行详情 42',
  before_state: 'running',
  after_state: 'running',
  error_code: 'OK',
  client_info: 'FoodMate Admin Console',
};

const auditReport = {
  generated_at: '2026-09-14T10:01:00Z',
  stale_threshold_minutes: 15,
  status: 'attention',
  checks: [
    {
      code: 'RUNTIME_DLQ',
      status: 'attention',
      pending_count: 2,
      failed_count: 1,
      oldest_at: '2026-09-14T09:45:00Z',
      reason_codes: ['DLQ_NEEDS_ATTENTION'],
    },
    {
      code: 'OPERATION_AUDIT',
      status: 'passed',
      pending_count: 0,
      failed_count: 0,
      oldest_at: null,
      reason_codes: [],
    },
  ],
};

function renderAudit() {
  return render(
    <MemoryRouter initialEntries={['/admin?view=audit']}>
      <OperationAuditSection />
    </MemoryRouter>,
  );
}

describe('OperationAuditSection real mode', () => {
  beforeEach(async () => {
    vi.stubEnv('VITE_AGENT_MODE', 'real');
    localStorage.setItem(
      'foodmate_auth_user',
      JSON.stringify({ id: '7', username: 'operator', displayName: 'Operator', role: 'operator', status: 'active' }),
    );
    vi.stubGlobal(
      'fetch',
      vi.fn().mockImplementation((input: RequestInfo | URL) => {
        const path = new URL(String(input), 'http://foodmate.local').pathname;
        if (path === '/api/admin/audit-reports/current') return Promise.resolve(ok(auditReport));
        if (path === '/api/admin/queries/operation-audits') {
          return Promise.resolve(ok({ items: [auditRow], total: 1, page: 1, size: 8 }));
        }
        return Promise.resolve(ok({ items: [], total: 0, page: 1, size: 8 }));
      }),
    );
    ({ OperationAuditSection } = await import('./OperationAuditTab'));
  });

  afterEach(() => {
    vi.unstubAllEnvs();
    vi.unstubAllGlobals();
    localStorage.clear();
    vi.clearAllMocks();
  });

  it('loads the server audit report and keeps operator access read-only', async () => {
    renderAudit();

    expect(await screen.findByText('运营审计报告')).toBeInTheDocument();
    expect(screen.getAllByText('attention')).toHaveLength(2);
    expect(screen.getByText('运行 DLQ')).toBeInTheDocument();
    const reportRegion = screen.getByRole('region', { name: '运营审计报告' });
    expect(reportRegion).toHaveTextContent('DLQ_NEEDS_ATTENTION');
    expect(reportRegion).toHaveTextContent('15 分钟');
    expect(screen.getByText('operator 只读 · admin/superadmin 可导出')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '导出当前结果' })).not.toBeInTheDocument();

    const fetchMock = vi.mocked(fetch);
    expect(fetchMock.mock.calls.some(([input]) => String(input) === '/api/admin/audit-reports/current')).toBe(true);
  });

  it('shows the report error and retries without replacing the real page with fixture data', async () => {
    const fetchMock = vi.fn().mockImplementation((input: RequestInfo | URL) => {
      const path = new URL(String(input), 'http://foodmate.local').pathname;
      if (path === '/api/admin/audit-reports/current') {
        return fetchMock.mock.calls.filter(([request]) => String(request) === '/api/admin/audit-reports/current')
          .length === 1
          ? Promise.resolve(
              new Response(
                JSON.stringify({
                  success: false,
                  error: { code: 'AUDIT_REPORT_UNAVAILABLE', message: 'AUDIT_REPORT_UNAVAILABLE' },
                }),
                { status: 503, headers: { 'Content-Type': 'application/json' } },
              ),
            )
          : Promise.resolve(ok(auditReport));
      }
      if (path === '/api/admin/queries/operation-audits') {
        return Promise.resolve(ok({ items: [], total: 0, page: 1, size: 8 }));
      }
      return Promise.resolve(ok({ items: [], total: 0, page: 1, size: 8 }));
    });
    vi.stubGlobal('fetch', fetchMock);

    renderAudit();

    expect(await screen.findByRole('alert')).toHaveTextContent('AUDIT_REPORT_UNAVAILABLE');
    expect(screen.queryByText('更新权限')).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: '重试' }));
    await waitFor(() => expect(screen.getByText('运行 DLQ')).toBeInTheDocument());
    expect(
      fetchMock.mock.calls.filter(([request]) => String(request) === '/api/admin/audit-reports/current'),
    ).toHaveLength(2);
  });
});
