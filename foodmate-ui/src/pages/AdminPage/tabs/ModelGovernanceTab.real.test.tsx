import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ModelGovernanceSection } from './ModelGovernanceTab';

function ok(data: unknown) {
  return new Response(JSON.stringify({ success: true, data }), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  });
}

const governanceView = {
  providers: [],
  models: [],
  routes: [],
  prices: [],
  budgets: [],
  usage: [],
};

function setRole(role: string) {
  localStorage.setItem(
    'foodmate_auth_user',
    JSON.stringify({
      id: '7',
      username: role,
      displayName: role,
      email: `${role}@example.com`,
      role,
      status: 'active',
      gender: '男',
    }),
  );
}

function renderGovernance(onAction = vi.fn()) {
  return render(<ModelGovernanceSection onAction={onAction} refreshNonce={0} />);
}

describe('ModelGovernanceSection real mode', () => {
  beforeEach(() => {
    vi.stubEnv('VITE_AGENT_MODE', 'real');
    setRole('superadmin');
  });

  afterEach(() => {
    vi.unstubAllEnvs();
    vi.unstubAllGlobals();
    localStorage.clear();
    vi.clearAllMocks();
  });

  it('only exposes create actions to superadmin', async () => {
    const fetchMock = vi.fn().mockResolvedValue(ok(governanceView));
    vi.stubGlobal('fetch', fetchMock);
    setRole('operator');

    renderGovernance();

    expect(await screen.findByText('价格、预算与调用汇总')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '新增价格' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '新增预算' })).not.toBeInTheDocument();
    expect(screen.getByText('当前角色仅可查看模型治理；状态变更需要 superadmin。')).toBeInTheDocument();
  });

  it('validates a price form before entering the confirmation flow', async () => {
    const fetchMock = vi.fn().mockResolvedValue(ok(governanceView));
    vi.stubGlobal('fetch', fetchMock);
    const onAction = vi.fn();
    const user = userEvent.setup();

    renderGovernance(onAction);
    await screen.findByText('价格、预算与调用汇总');
    await user.click(screen.getByRole('button', { name: '新增价格' }));
    await user.click(screen.getByRole('button', { name: '提交新增价格' }));

    expect(screen.getByRole('alert')).toHaveTextContent('供应商代码不能为空');
    expect(onAction).not.toHaveBeenCalled();
  });

  it('passes a validated price through the shared admin action and real API', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(ok(governanceView))
      .mockResolvedValueOnce(ok({ changed: true, resource_id: 21, version: 'price-v2', revision: 1 }))
      .mockResolvedValueOnce(ok(governanceView));
    vi.stubGlobal('fetch', fetchMock);
    const onAction = vi.fn();
    const user = userEvent.setup();

    renderGovernance(onAction);
    await screen.findByText('价格、预算与调用汇总');
    await user.click(screen.getByRole('button', { name: '新增价格' }));
    await user.type(screen.getByLabelText('供应商代码'), 'cloud_primary');
    await user.type(screen.getByLabelText('模型名称'), 'Qwen3');
    await user.type(screen.getByLabelText('价格版本'), 'price-v2');
    await user.clear(screen.getByLabelText('输入价格 / 百万 Token'));
    await user.type(screen.getByLabelText('输入价格 / 百万 Token'), '1.2');
    await user.clear(screen.getByLabelText('输出价格 / 百万 Token'));
    await user.type(screen.getByLabelText('输出价格 / 百万 Token'), '2.4');
    fireEvent.change(screen.getByLabelText('生效时间'), { target: { value: '2026-09-14T10:30' } });
    await user.click(screen.getByRole('button', { name: '提交新增价格' }));

    expect(onAction).toHaveBeenCalledWith(
      expect.objectContaining({
        action: '新增模型价格',
        targetType: 'model_price_version',
        targetLabel: 'cloud_primary/Qwen3 · price-v2',
      }),
    );
    const action = onAction.mock.calls[0][0] as { execute: () => Promise<void> };
    await action.execute();

    const [, init] = fetchMock.mock.calls[1] as [string, RequestInit];
    const body = JSON.parse(String(init.body));
    expect(fetchMock.mock.calls[1][0]).toBe('/api/admin/model-governance/prices');
    expect(body).toMatchObject({
      providerCode: 'cloud_primary',
      modelName: 'Qwen3',
      priceVersion: 'price-v2',
      inputPricePerMillion: '1.2',
      outputPricePerMillion: '2.4',
      currency: 'CNY',
      effectiveAt: '2026-09-14T02:30:00.000Z',
      revision: 1,
      confirmed: true,
    });
    expect(body.confirmationDigest).toMatch(/^[0-9a-f]{64}$/);
    expect(new Headers(init.headers).get('Idempotency-Key')).toMatch(/^model-price-create-/);
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(3));
  });

  it('passes a budget with the backend scope and window values to the real API', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(ok(governanceView))
      .mockResolvedValueOnce(ok({ changed: true, resource_id: 22, version: 'budget-v2', revision: 1 }))
      .mockResolvedValueOnce(ok(governanceView));
    vi.stubGlobal('fetch', fetchMock);
    const onAction = vi.fn();
    const user = userEvent.setup();

    renderGovernance(onAction);
    await screen.findByText('价格、预算与调用汇总');
    await user.click(screen.getByRole('button', { name: '新增预算' }));
    await user.type(screen.getByLabelText('策略键'), 'agent-default');
    await user.clear(screen.getByLabelText('策略版本'));
    await user.type(screen.getByLabelText('策略版本'), 'budget-v2');
    await user.clear(screen.getByLabelText('最大 Token 数'));
    await user.type(screen.getByLabelText('最大 Token 数'), '50000');
    await user.click(screen.getByRole('button', { name: '提交新增预算' }));

    expect(onAction).toHaveBeenCalledWith(
      expect.objectContaining({
        action: '新增模型预算',
        targetType: 'model_budget_policy',
        targetLabel: 'agent-default · budget-v2',
      }),
    );
    const action = onAction.mock.calls[0][0] as { execute: () => Promise<void> };
    await action.execute();

    const [, init] = fetchMock.mock.calls[1] as [string, RequestInit];
    const body = JSON.parse(String(init.body));
    expect(fetchMock.mock.calls[1][0]).toBe('/api/admin/model-governance/budgets');
    expect(body).toMatchObject({
      policyKey: 'agent-default',
      scene: 'chat',
      scopeType: 'global',
      maxTotalTokens: 50000,
      maxCostCny: '0',
      maxModelCalls: 10,
      maxStepRetries: 2,
      windowType: 'run',
      policyVersion: 'budget-v2',
      revision: 1,
      confirmed: true,
    });
    expect(body.confirmationDigest).toMatch(/^[0-9a-f]{64}$/);
    expect(new Headers(init.headers).get('Idempotency-Key')).toMatch(/^model-budget-create-/);
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(3));
  });

  it('clears stale governance data when a refreshed read fails', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(
        ok({
          ...governanceView,
          providers: [
            {
              provider_id: 11,
              provider_code: 'cloud-primary',
              status: 'active',
              configured: true,
              fingerprint: 'sha256:test',
              revision: 3,
            },
          ],
        }),
      )
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({ success: false, error: { code: 'GOVERNANCE_UNAVAILABLE', message: '治理数据暂不可用' } }),
          { status: 503, headers: { 'Content-Type': 'application/json' } },
        ),
      );
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();

    renderGovernance();
    expect(await screen.findByText('cloud-primary')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: '刷新' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('治理数据暂不可用');
    expect(screen.queryByText('cloud-primary')).not.toBeInTheDocument();
  });
});
