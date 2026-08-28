import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  createFoodLog,
  deleteFoodLog,
  loadDeletedFoodLogs,
  loadFoodLogs,
  restoreFoodLog,
  updateFoodLog,
} from './foodLogService';

describe('foodLogService', () => {
  beforeEach(() => {
    vi.stubEnv('VITE_AGENT_MODE', 'real');
  });

  afterEach(() => {
    vi.unstubAllEnvs();
    vi.unstubAllGlobals();
  });

  it('loads logs for an explicit date window', async () => {
    const data = [{ food_log_id: '11', meal_time: '2026-08-22T08:00:00Z', meal_type: 'breakfast', items: [] }];
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ success: true, data }), { status: 200 }));
    vi.stubGlobal('fetch', fetchMock);

    await expect(loadFoodLogs('2026-08-22T00:00:00.000Z', '2026-08-23T00:00:00.000Z')).resolves.toEqual(data);
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/food-logs?from=2026-08-22T00%3A00%3A00.000Z&to=2026-08-23T00%3A00%3A00.000Z',
      expect.objectContaining({ method: 'GET', credentials: 'include' }),
    );
  });

  it('uses idempotency keys for create and delete operations', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ success: true, data: { food_log_id: '12' } }), { status: 200 }),
      )
      .mockResolvedValueOnce(new Response(JSON.stringify({ success: true, data: null }), { status: 200 }))
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ success: true, data: { food_log_id: '12' } }), { status: 200 }),
      )
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ success: true, data: { food_log_id: '12' } }), { status: 200 }),
      );
    vi.stubGlobal('fetch', fetchMock);

    await createFoodLog({
      meal_time: '2026-08-22T08:00:00Z',
      meal_type: 'breakfast',
      items: [{ raw_name: '燕麦', amount: 1, unit: '份' }],
    });
    await deleteFoodLog('12', 2);
    await updateFoodLog('12', 2, {
      meal_time: '2026-08-22T08:00:00Z',
      meal_type: 'breakfast',
      items: [{ raw_name: '燕麦', amount: 2, unit: '份' }],
    });
    await restoreFoodLog('12', 3);
    expect(new Headers(fetchMock.mock.calls[0][1].headers).get('Idempotency-Key')).toMatch(/^food-log-create-/);
    expect(new Headers(fetchMock.mock.calls[1][1].headers).get('Idempotency-Key')).toMatch(/^food-log-delete-/);
    expect(new Headers(fetchMock.mock.calls[2][1].headers).get('Idempotency-Key')).toMatch(/^food-log-update-/);
    expect(new Headers(fetchMock.mock.calls[3][1].headers).get('Idempotency-Key')).toMatch(/^food-log-restore-/);
  });

  it("loads the authenticated user's deleted records endpoint", async () => {
    const data = [{ food_log_id: '12', deleted: true }];
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ success: true, data }), { status: 200 }));
    vi.stubGlobal('fetch', fetchMock);

    await expect(loadDeletedFoodLogs()).resolves.toEqual(data);
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/food-logs/deleted',
      expect.objectContaining({ method: 'GET', credentials: 'include' }),
    );
  });
});
