import { apiRequest } from './apiClient';

export type NutritionFoodCandidate = {
  nutrition_food_id: string;
  standard_name: string;
  chinese_name: string | null;
  category: string | null;
  food_form: string | null;
  basis_unit: string;
  calories_kcal_per_100: number | string | null;
  protein_g_per_100: number | string | null;
  fat_g_per_100: number | string | null;
  carbs_g_per_100: number | string | null;
  source_name: string | null;
  source_version: string | null;
};

/** 查询已审核目录，供用户明确选择营养事实。 */
export async function searchNutritionFoods(query: string, limit = 8): Promise<NutritionFoodCandidate[]> {
  return apiRequest<NutritionFoodCandidate[]>(
    `/api/nutrition-foods/search?query=${encodeURIComponent(query)}&limit=${limit}`,
  );
}
