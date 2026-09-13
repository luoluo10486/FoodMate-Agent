import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  approveRetentionPurge,
  createModelBudget,
  createModelPrice,
  loadAdminAuditReport,
  loadModelGovernance,
  loadRetentionPurge,
  loadRetentionPurgePreflight,
  placeRetentionHold,
  reindexKnowledgeItem,
  releaseRetentionHold,
  replayAdminDlq,
  requestRetentionPurge,
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

  it('uses the document id in the knowledge reindex route', async () => {
    const fetchMock = vi.fn().mockImplementation(() => Promise.resolve(ok({ status: 'pending' })));
    vi.stubGlobal('fetch', fetchMock);

    await reindexKnowledgeItem('9001', '42');

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/admin/knowledge-upload-batches/9001/documents/42/reindex',
      expect.objectContaining({ method: 'POST' }),
    );
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
