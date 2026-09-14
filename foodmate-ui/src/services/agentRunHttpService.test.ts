import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  cancelAgentRun,
  confirmAgentWrite,
  createApprovalProposal,
  executeAgentWrite,
  extendAgentRunBudget,
  loadAgentRun,
  loadApprovalProposal,
  recoverAgentRun,
  recoverAgentRunFromCheckpoint,
  rejectAgentWrite,
  retryAgentRun,
  submitAgentFeedback,
} from './agentRunService';

function ok(data: unknown) {
  return new Response(JSON.stringify({ success: true, data }), { status: 200 });
}

describe('agentRunService HTTP APIs', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('reads and cancels a run through the typed service', async () => {
    const fetchMock = vi
      .fn()
      .mockImplementationOnce(() => Promise.resolve(ok({ run_id: '42', status: 'running', accepted_event_count: 3 })))
      .mockImplementationOnce(() => Promise.resolve(ok({ runId: '42', status: 'requested', terminal: false })));
    vi.stubGlobal('fetch', fetchMock);

    await expect(loadAgentRun('42')).resolves.toMatchObject({ run_id: '42' });
    await expect(cancelAgentRun('42', 'user_cancelled')).resolves.toEqual({
      run_id: '42',
      status: 'requested',
      terminal: false,
    });

    expect(fetchMock.mock.calls[0][0]).toBe('/api/agent-runs/42');
    expect(fetchMock.mock.calls[1][0]).toBe('/api/agent-runs/42/cancel');
    expect(JSON.parse(String(fetchMock.mock.calls[1][1].body))).toEqual({ reason: 'user_cancelled' });
  });

  it('uses camelCase fields required by the budget request DTO and a stable digest', async () => {
    const fetchMock = vi
      .fn()
      .mockImplementation(() =>
        Promise.resolve(ok({ runId: '42', dispatchId: 'dsp-1', attempt: 2, budgetRevision: 3, status: 'queued' })),
      );
    vi.stubGlobal('fetch', fetchMock);

    await expect(extendAgentRunBudget('42', 5000, '0.12')).resolves.toEqual({
      run_id: '42',
      dispatch_id: 'dsp-1',
      attempt: 2,
      budget_revision: 3,
      status: 'queued',
    });
    await expect(extendAgentRunBudget('42', 5000, '0.12')).resolves.toEqual({
      run_id: '42',
      dispatch_id: 'dsp-1',
      attempt: 2,
      budget_revision: 3,
      status: 'queued',
    });

    const firstBody = JSON.parse(String(fetchMock.mock.calls[0][1].body));
    const secondBody = JSON.parse(String(fetchMock.mock.calls[1][1].body));
    expect(firstBody).toMatchObject({ additionalTokens: 5000, additionalCostCny: '0.12' });
    expect(firstBody).not.toHaveProperty('additional_tokens');
    expect(firstBody.confirmationDigest).toMatch(/^sha256:[0-9a-f]{64}$/);
    expect(secondBody.confirmationDigest).toBe(firstBody.confirmationDigest);
  });

  it('supports explicit and persisted-checkpoint recovery endpoints', async () => {
    const fetchMock = vi
      .fn()
      .mockImplementation(() =>
        Promise.resolve(ok({ run_id: '42', dispatch_id: 'dsp-2', attempt: 2, status: 'queued' })),
      );
    vi.stubGlobal('fetch', fetchMock);

    await recoverAgentRun('42', {
      checkpointVersion: 4,
      checkpointDigest: 'sha256:checkpoint',
      completedInvocationIds: ['inv-1'],
    });
    await recoverAgentRunFromCheckpoint('42');

    expect(fetchMock.mock.calls[0][0]).toBe('/api/agent-runs/42/recover');
    expect(JSON.parse(String(fetchMock.mock.calls[0][1].body))).toEqual({
      checkpoint_version: 4,
      checkpoint_digest: 'sha256:checkpoint',
      completed_invocation_ids: ['inv-1'],
    });
    expect(fetchMock.mock.calls[1][0]).toBe('/api/agent-runs/42/recover-from-checkpoint');
  });

  it('uses the dedicated retry endpoint for a retryable failed Run', async () => {
    const fetchMock = vi
      .fn()
      .mockImplementation(() =>
        Promise.resolve(ok({ run_id: '42', dispatch_id: 'dsp-retry', attempt: 3, status: 'queued' })),
      );
    vi.stubGlobal('fetch', fetchMock);

    await expect(retryAgentRun('42')).resolves.toEqual({
      run_id: '42',
      dispatch_id: 'dsp-retry',
      attempt: 3,
      status: 'queued',
    });

    expect(fetchMock.mock.calls[0][0]).toBe('/api/agent-runs/42/retry');
    expect(fetchMock.mock.calls[0][1].method).toBe('POST');
  });

  it('maps feedback and approval operations to the existing endpoints', async () => {
    const fetchMock = vi.fn().mockImplementation(() =>
      Promise.resolve(
        ok({
          approval_request_id: '8',
          operation: 'food_log.create',
          resource_type: 'food_log',
          resource_id: null,
          parameters_digest: 'sha256:x',
          status: 'confirmed',
          expires_at: '2026-09-13T10:00:00Z',
          confirmed_at: null,
          executed_at: null,
        }),
      ),
    );
    vi.stubGlobal('fetch', fetchMock);

    await submitAgentFeedback('42', '99', { helpful: false, reasonCodes: ['incorrect'], comment: '需要修正' });
    await createApprovalProposal({
      sessionId: '7',
      agentRunId: '42',
      operation: 'food_log.create',
      resourceType: 'food_log',
      parameters: { meal_type: 'lunch' },
      idempotencyKey: 'proposal-1',
      expiresInSeconds: 600,
    });
    await loadApprovalProposal('8');
    await confirmAgentWrite('8', { confirmed: true });
    await executeAgentWrite('8', { confirmed: true });
    await rejectAgentWrite('8', { reason: 'user_cancelled' });

    expect(fetchMock.mock.calls.map(([path, init]) => [path, init.method])).toEqual([
      ['/api/agent-runs/42/messages/99/feedback', 'POST'],
      ['/api/approvals/proposals', 'POST'],
      ['/api/approvals/8', 'GET'],
      ['/api/approvals/8/confirm', 'POST'],
      ['/api/approvals/8/execute', 'POST'],
      ['/api/approvals/8/reject', 'POST'],
    ]);
    const proposalBody = JSON.parse(String(fetchMock.mock.calls[1][1].body));
    expect(proposalBody).toMatchObject({
      session_id: '7',
      agent_run_id: '42',
      resource_type: 'food_log',
      idempotency_key: 'proposal-1',
      expires_in_seconds: 600,
    });
    const feedbackBody = JSON.parse(String(fetchMock.mock.calls[0][1].body));
    expect(feedbackBody).toEqual({ helpful: false, reason_codes: ['incorrect'], comment: '需要修正' });
  });

  it('将同一个取消信号透传到所有 Agent 和 Approval HTTP 接口', async () => {
    const controller = new AbortController();
    const fetchMock = vi.fn().mockImplementation(() =>
      Promise.resolve(
        ok({
          run_id: '42',
          dispatch_id: 'dispatch-1',
          attempt: 1,
          budget_revision: 1,
          status: 'queued',
          accepted_event_count: 0,
          approval_request_id: '8',
          operation: 'food_log.create',
          resource_type: 'food_log',
          resource_id: null,
          parameters_digest: 'sha256:proposal',
          expires_at: '2026-09-14T13:00:00Z',
          confirmed_at: null,
          executed_at: null,
          terminal: false,
        }),
      ),
    );
    vi.stubGlobal('fetch', fetchMock);

    await loadAgentRun('42', controller.signal);
    await cancelAgentRun('42', 'user_cancelled', controller.signal);
    await extendAgentRunBudget('42', 5000, '0.12', 'sha256:digest', controller.signal);
    await recoverAgentRun(
      '42',
      { checkpointVersion: 4, checkpointDigest: 'sha256:checkpoint', completedInvocationIds: ['inv-1'] },
      controller.signal,
    );
    await recoverAgentRunFromCheckpoint('42', controller.signal);
    await retryAgentRun('42', controller.signal);
    await submitAgentFeedback('42', '99', { helpful: true }, controller.signal);
    await createApprovalProposal(
      {
        agentRunId: '42',
        operation: 'food_log.create',
        resourceType: 'food_log',
        parameters: { meal_type: 'lunch' },
      },
      controller.signal,
    );
    await loadApprovalProposal('8', controller.signal);
    await confirmAgentWrite('8', { confirmed: true }, controller.signal);
    await executeAgentWrite('8', { confirmed: true }, controller.signal);
    await rejectAgentWrite('8', { reason: 'user_cancelled' }, controller.signal);

    expect(fetchMock).toHaveBeenCalledTimes(12);
    expect(fetchMock.mock.calls.every(([, init]) => init.signal === controller.signal)).toBe(true);
  });
});
