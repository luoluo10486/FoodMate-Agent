import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { loadModelGovernance, updateAdminToolStatus, updateModelProviderStatus } from './adminService';

const provider = {
  provider_id: 11,
  provider_code: 'local-stub',
  display_name: 'Local Stub',
  status: 'active',
  endpoint_config_key: 'FOODMATE_RAG_MODE',
  configured: true,
  fingerprint: 'sha256:abc',
  revision: 3,
};

describe('admin model governance API', () => {
  beforeEach(() => {
    vi.stubEnv('VITE_AGENT_MODE', 'real');
  });

  afterEach(() => {
    vi.unstubAllEnvs();
    vi.unstubAllGlobals();
  });

  it('loads the safe governance response without a fixture fallback', async () => {
    const response = {
      providers: [provider],
      models: [],
      routes: [],
      prices: [],
      budgets: [],
      usage: [],
    };
    const fetchMock = vi
      .fn()
      .mockResolvedValue(new Response(JSON.stringify({ success: true, data: response }), { status: 200 }));
    vi.stubGlobal('fetch', fetchMock);

    await expect(loadModelGovernance()).resolves.toEqual(response);
    expect(fetchMock).toHaveBeenCalledWith('/api/admin/model-governance', expect.objectContaining({ method: 'GET' }));
  });

  it('sends revision, confirmation digest and idempotency key for status changes', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({ success: true, data: { changed: true, resource_id: 11, version: 'disabled', revision: 4 } }),
        {
          status: 200,
        },
      ),
    );
    vi.stubGlobal('fetch', fetchMock);

    await updateModelProviderStatus(provider, 'disabled');
    const [, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    const body = JSON.parse(String(init.body)) as {
      status: string;
      revision: number;
      confirmed: boolean;
      confirmationDigest: string;
    };
    expect(init.method).toBe('PATCH');
    expect(body).toMatchObject({ status: 'disabled', revision: 3, confirmed: true });
    expect(body.confirmationDigest).toMatch(/^[0-9a-f]{64}$/);
    expect(new Headers(init.headers).get('Idempotency-Key')).toMatch(/^model-provider-status-/);
  });

  it('uses the dashboard tool revision for the real tool status contract', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ success: true, data: { updated: true, status: 'disabled', revision: 8 } }), {
        status: 200,
      }),
    );
    vi.stubGlobal('fetch', fetchMock);

    await updateAdminToolStatus('food_log_writer', 'disabled', 7);
    const [, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(init.method).toBe('PATCH');
    expect(JSON.parse(String(init.body))).toMatchObject({ revision: 7, confirmed: true, status: 'disabled' });
    expect(new Headers(init.headers).get('Idempotency-Key')).toMatch(/^admin-tool-status-/);
  });
});
