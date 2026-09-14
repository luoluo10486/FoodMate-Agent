import { afterEach, describe, expect, it, vi } from 'vitest';
import { searchNutritionFoods } from './nutritionFoodService';

describe('nutritionFoodService', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('queries the authenticated nutrition catalog', async () => {
    const data = [
      {
        nutrition_food_id: '101',
        standard_name: 'Chicken breast, roasted',
        chinese_name: '烤鸡胸肉',
        category: 'Poultry',
        food_form: 'cooked',
        basis_unit: 'g',
        calories_kcal_per_100: 165,
        protein_g_per_100: 31,
        fat_g_per_100: 3.6,
        carbs_g_per_100: 0,
        source_name: 'USDA FoodData Central',
        source_version: '2025',
      },
    ];
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ success: true, data }), { status: 200 }));
    vi.stubGlobal('fetch', fetchMock);
    const controller = new AbortController();

    await expect(searchNutritionFoods('鸡胸肉', 6, controller.signal)).resolves.toEqual(data);
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/nutrition-foods/search?query=%E9%B8%A1%E8%83%B8%E8%82%89&limit=6',
      expect.objectContaining({ method: 'GET', credentials: 'include', signal: controller.signal }),
    );
  });
});
