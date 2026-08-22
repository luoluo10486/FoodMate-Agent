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

  it('loads user-scoped nutrition analysis for a supported range', async () => {
    const data = {
      range: '7d',
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

    await expect(loadNutritionAnalysis('7d')).resolves.toEqual(data);
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/nutrition-analysis?range=7d',
      expect.objectContaining({ method: 'GET', credentials: 'include' }),
    );
  });
});
