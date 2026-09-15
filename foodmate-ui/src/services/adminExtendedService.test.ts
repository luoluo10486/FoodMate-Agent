import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  approveRetentionPurge,
  changeKnowledgeVisibility,
  createModelBudget,
  createModelPrice,
  loadAdminKnowledge,
  loadAdminAuditReport,
  loadAdminDeletedResourcesPage,
  loadAdminQuery,
  loadAdminExportStatus,
  loadAdminOperationAuditsPage,
  loadAdminUsersPage,
  loadAdminUsagePage,
  loadKnowledgeBatch,
  loadModelGovernance,
  loadRetentionPurge,
  loadRetentionPurgePreflight,
  placeRetentionHold,
  reindexKnowledgeItem,
  releaseRetentionHold,
  replayAdminDlq,
  requestRetentionPurge,
  retryKnowledgeItem,
  restoreAdminResource,
  updateKnowledgeStatus,
  updateAdminToolStatus,
  updateAdminUserStatus,
  revokeAdminUserSessions,
} from './adminService';

function ok(data: unknown) {
  return new Response(JSON.stringify({ success: true, data }), { status: 200 });
}

describe('admin extended APIs', () => {
  beforeEach(() => {
    vi.stubEnv('VITE_AGENT_MODE', 'real');
  });

  afterEach(() => {
    vi.unstubAllEnvs();
    vi.unstubAllGlobals();
  });

  it('loads the audit report and governance usage range', async () => {
    const fetchMock = vi.fn().mockImplementation(() => Promise.resolve(ok({ status: 'healthy', checks: [] })));
    vi.stubGlobal('fetch', fetchMock);

    await loadAdminAuditReport();
    await loadModelGovernance({ from: '2026-09-01T00:00:00Z', to: '2026-09-13T00:00:00Z' });

    expect(fetchMock.mock.calls[0][0]).toBe('/api/admin/audit-reports/current');
    expect(fetchMock.mock.calls[1][0]).toBe(
      '/api/admin/model-governance?from=2026-09-01T00%3A00%3A00Z&to=2026-09-13T00%3A00%3A00Z',
    );
  });

  it('forwards the abort signal through paginated admin queries', async () => {
    const controller = new AbortController();
    const fetchMock = vi.fn().mockResolvedValue(ok({ items: [], total: 0, page: 2, size: 20 }));
    vi.stubGlobal('fetch', fetchMock);

    await loadAdminQuery('runs', { page: 2, size: 20 }, controller.signal);

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/admin/queries/runs?page=2&size=20',
      expect.objectContaining({ method: 'GET', signal: controller.signal }),
    );
  });

  it('uses the dedicated user list endpoint for the default page', async () => {
    const controller = new AbortController();
    const fetchMock = vi.fn().mockResolvedValue(
      ok([
        {
          user_id: 7,
          username: 'real-user',
          email: 'real@example.com',
          nickname: '真实用户',
          role: 'user',
          status: 'active',
          revision: 3,
        },
      ]),
    );
    vi.stubGlobal('fetch', fetchMock);

    const result = await loadAdminUsersPage({ page: 1, size: 20 }, controller.signal);

    expect(result.items[0]).toMatchObject({ userId: '7', displayName: '真实用户' });
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/admin/users',
      expect.objectContaining({ method: 'GET', signal: controller.signal }),
    );
  });

  it('uses the paginated admin query endpoint when the user page is filtered', async () => {
    const fetchMock = vi.fn().mockResolvedValue(ok({ items: [], total: 0, page: 1, size: 20 }));
    vi.stubGlobal('fetch', fetchMock);

    await loadAdminUsersPage({ page: 1, size: 20, role: 'operator' });

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/admin/queries/users?page=1&size=20&role=operator',
      expect.objectContaining({ method: 'GET' }),
    );
  });

  it('forwards cancellation through admin lifecycle reads', async () => {
    const controller = new AbortController();
    const fetchMock = vi.fn().mockImplementation((input: RequestInfo | URL) => {
      const path = new URL(String(input), 'http://foodmate.local').pathname;
      if (path.includes('/queries/')) return Promise.resolve(ok({ items: [], total: 0, page: 1, size: 4 }));
      if (path === '/api/admin/audit-reports/current')
        return Promise.resolve(ok({ generated_at: '', stale_threshold_minutes: 15, status: 'healthy', checks: [] }));
      return Promise.resolve(
        ok({
          export_job_id: 4,
          resource: 'operation-audits',
          status: 'queued',
          expires_at: null,
          completed_at: null,
          download_consumed_at: null,
          failure_code: null,
        }),
      );
    });
    vi.stubGlobal('fetch', fetchMock);

    await loadAdminDeletedResourcesPage({}, controller.signal);
    await loadAdminOperationAuditsPage({}, controller.signal);
    await loadAdminUsagePage({}, controller.signal);
    await loadAdminAuditReport(controller.signal);
    await loadAdminExportStatus(4, controller.signal);

    expect(fetchMock.mock.calls).toHaveLength(5);
    for (const [, init] of fetchMock.mock.calls)
      expect(init).toEqual(expect.objectContaining({ signal: controller.signal }));
  });

  it('uses the document id in the knowledge reindex route', async () => {
    const fetchMock = vi.fn().mockImplementation(() => Promise.resolve(ok({ status: 'pending' })));
    vi.stubGlobal('fetch', fetchMock);

    await reindexKnowledgeItem('9001', '42');

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/admin/knowledge-upload-batches/9001/documents/42/reindex',
      expect.objectContaining({ method: 'POST' }),
    );
  });

  it('forwards cancellation through knowledge reads and writes', async () => {
    const controller = new AbortController();
    const fetchMock = vi
      .fn()
      .mockImplementation(() =>
        Promise.resolve(
          ok({ resource: 'knowledge', items: [], total: 0, page: 1, size: 20, batch: { job: {}, items: [] } }),
        ),
      );
    vi.stubGlobal('fetch', fetchMock);

    await loadAdminKnowledge({}, controller.signal);
    await loadKnowledgeBatch('9001', controller.signal);
    await updateKnowledgeStatus('42', 'indexed', controller.signal);
    await retryKnowledgeItem('9001', '42', controller.signal);
    await changeKnowledgeVisibility('42', 'published', controller.signal);

    for (const [, init] of fetchMock.mock.calls) {
      expect(init).toEqual(expect.objectContaining({ signal: controller.signal }));
    }
  });

  it('forwards cancellation through admin mutations and retention reads', async () => {
    const controller = new AbortController();
    const fetchMock = vi.fn().mockImplementation(() => Promise.resolve(ok({})));
    vi.stubGlobal('fetch', fetchMock);

    await updateAdminUserStatus('7', 'locked', 3, controller.signal);
    await revokeAdminUserSessions('7', 3, controller.signal);
    await updateAdminToolStatus('food_log_writer', 'disabled', 7, controller.signal);
    await restoreAdminResource('user', '7', 4, controller.signal);
    await replayAdminDlq(11, controller.signal);
    await requestRetentionPurge('food_log', 42, controller.signal);
    await loadRetentionPurge(5, controller.signal);
    await loadRetentionPurgePreflight(5, controller.signal);
    await approveRetentionPurge(5, controller.signal);
    await placeRetentionHold('food_log', 42, 'legal_request', controller.signal);
    await releaseRetentionHold(9, controller.signal);
    await createModelPrice(
      {
        providerCode: 'cloud_primary',
        modelName: 'DeepSeek-V4-Flash',
        priceVersion: 'price-v1',
        inputPricePerMillion: '1.2',
        outputPricePerMillion: '2.4',
        currency: 'CNY',
        effectiveAt: '2026-09-13T00:00:00Z',
        revision: 1,
      },
      controller.signal,
    );
    await createModelBudget(
      {
        policyKey: 'agent-default',
        scene: 'chat',
        scopeType: 'global',
        maxTotalTokens: 50000,
        maxCostCny: '10.00',
        maxModelCalls: 10,
        maxStepRetries: 2,
        windowType: 'run',
        policyVersion: 'budget-v1',
        revision: 1,
      },
      controller.signal,
    );

    expect(fetchMock).toHaveBeenCalledTimes(13);
    const userStatusBody = JSON.parse(String(fetchMock.mock.calls[0][1].body));
    const revokeSessionsBody = JSON.parse(String(fetchMock.mock.calls[1][1].body));
    const toolStatusBody = JSON.parse(String(fetchMock.mock.calls[2][1].body));
    const restoreBody = JSON.parse(String(fetchMock.mock.calls[3][1].body));
    expect(userStatusBody).toMatchObject({ status: 'locked', revision: 3, confirmed: true });
    expect(revokeSessionsBody).toMatchObject({ revision: 3, confirmed: true });
    expect(toolStatusBody).toMatchObject({ status: 'disabled', revision: 7, confirmed: true });
    expect(restoreBody).toMatchObject({ revision: 4, confirmed: true });
    for (const body of [userStatusBody, revokeSessionsBody, toolStatusBody, restoreBody])
      expect(body.confirmationDigest).toMatch(/^[0-9a-f]{64}$/);
    for (const [, init] of fetchMock.mock.calls) {
      expect(init).toEqual(expect.objectContaining({ signal: controller.signal }));
    }
  });

  it('sends stable confirmation digests for DLQ and retention operations', async () => {
    const fetchMock = vi.fn().mockImplementation(() => Promise.resolve(ok({})));
    vi.stubGlobal('fetch', fetchMock);

    await replayAdminDlq(11);
    await requestRetentionPurge('food_log', 42);
    await loadRetentionPurge(5);
    await loadRetentionPurgePreflight(5);
    await approveRetentionPurge(5);
    await placeRetentionHold('food_log', 42, 'legal_request');
    await releaseRetentionHold(9);

    const replayBody = JSON.parse(String(fetchMock.mock.calls[0][1].body));
    const purgeBody = JSON.parse(String(fetchMock.mock.calls[1][1].body));
    const approveBody = JSON.parse(String(fetchMock.mock.calls[4][1].body));
    const holdBody = JSON.parse(String(fetchMock.mock.calls[5][1].body));
    const releaseBody = JSON.parse(String(fetchMock.mock.calls[6][1].body));
    expect(replayBody).toMatchObject({ confirmed: true });
    expect(replayBody.confirmationDigest).toMatch(/^[0-9a-f]{64}$/);
    expect(purgeBody).toMatchObject({ resource_type: 'food_log', resource_id: 42, confirmed: true });
    expect(purgeBody.confirmation_digest).toMatch(/^[0-9a-f]{64}$/);
    expect(approveBody.confirmation_digest).toMatch(/^[0-9a-f]{64}$/);
    expect(holdBody).toMatchObject({ resource_type: 'food_log', resource_id: 42, reason_code: 'legal_request' });
    expect(releaseBody.confirmation_digest).toMatch(/^[0-9a-f]{64}$/);
    expect(new Headers(fetchMock.mock.calls[0][1].headers).get('Idempotency-Key')).toMatch(/^admin-dlq-replay-/);
  });

  it('creates model prices and budgets with the backend confirmation contract', async () => {
    const fetchMock = vi
      .fn()
      .mockImplementation(() => Promise.resolve(ok({ changed: true, resource_id: 1, version: 'v1', revision: 1 })));
    vi.stubGlobal('fetch', fetchMock);

    await createModelPrice({
      providerCode: 'cloud_primary',
      modelName: 'DeepSeek-V4-Flash',
      priceVersion: 'price-v1',
      inputPricePerMillion: '1.2',
      outputPricePerMillion: '2.4',
      currency: 'CNY',
      effectiveAt: '2026-09-13T00:00:00Z',
      revision: 1,
    });
    await createModelBudget({
      policyKey: 'agent-default',
      scene: 'chat',
      scopeType: 'global',
      maxTotalTokens: 50000,
      maxCostCny: '10.00',
      maxModelCalls: 10,
      maxStepRetries: 2,
      windowType: 'run',
      policyVersion: 'budget-v1',
      revision: 1,
    });

    const priceBody = JSON.parse(String(fetchMock.mock.calls[0][1].body));
    const budgetBody = JSON.parse(String(fetchMock.mock.calls[1][1].body));
    expect(priceBody).toMatchObject({
      providerCode: 'cloud_primary',
      priceVersion: 'price-v1',
      revision: 1,
      confirmed: true,
    });
    expect(priceBody.confirmationDigest).toMatch(/^[0-9a-f]{64}$/);
    expect(budgetBody).toMatchObject({ policyKey: 'agent-default', scopeType: 'global', policyVersion: 'budget-v1' });
    expect(budgetBody.confirmationDigest).toMatch(/^[0-9a-f]{64}$/);
    expect(new Headers(fetchMock.mock.calls[0][1].headers).get('Idempotency-Key')).toMatch(/^model-price-create-/);
    expect(new Headers(fetchMock.mock.calls[1][1].headers).get('Idempotency-Key')).toMatch(/^model-budget-create-/);
  });
});
