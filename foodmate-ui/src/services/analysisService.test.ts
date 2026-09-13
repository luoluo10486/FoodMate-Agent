import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { loadNutritionAnalysis } from './analysisService';

describe('analysisService', () => {
  beforeEach(() => {
    vi.stubEnv('VITE_AGENT_MODE', 'real');
  });

  afterEach(() => {
    vi.unstubAllEnvs();
    vi.unstubAllGlobals();
  });

  it('loads user-scoped nutrition analysis for today', async () => {
    const data = {
      range: 'today',
      from: '2026-08-15T00:00:00Z',
      to: '2026-08-22T00:00:00Z',
      total_items: 4,
      matched_items: 3,
      coverage: 0.75,
      calories_kcal: 4200,
      protein_g: 220,
      fat_g: 130,
      carbs_g: 310,
      calorie_target: 1800,
      protein_target: 120,
      incomplete: true,
      unmatched_names: ['自制酱料'],
      disclaimer: '仅用于饮食记录参考',
    };
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ success: true, data }), { status: 200 }));
    vi.stubGlobal('fetch', fetchMock);

    await expect(loadNutritionAnalysis('today')).resolves.toEqual(data);
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/nutrition-analysis?range=today',
      expect.objectContaining({ method: 'GET', credentials: 'include' }),
    );
  });

  it.each(['7d', '30d'] as const)('loads the backend-supported %s range without remapping', async (range) => {
    const data = {
      range,
      from: '2026-08-15T00:00:00Z',
      to: '2026-08-22T00:00:00Z',
      total_items: 0,
      matched_items: 0,
      coverage: 0,
      calories_kcal: 0,
      protein_g: 0,
      fat_g: 0,
      carbs_g: 0,
      calorie_target: null,
      protein_target: null,
      incomplete: false,
      unmatched_names: [],
      disclaimer: '仅用于饮食记录参考',
    };
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ success: true, data }), { status: 200 }));
    vi.stubGlobal('fetch', fetchMock);

    await expect(loadNutritionAnalysis(range)).resolves.toEqual(data);
    expect(fetchMock).toHaveBeenCalledWith(
      `/api/nutrition-analysis?range=${range}`,
      expect.objectContaining({ method: 'GET', credentials: 'include' }),
    );
  });
});
