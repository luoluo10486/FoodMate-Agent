import { apiRequest } from './apiClient';

export type MealPlanConstraints = {
  people?: number;
  calorie_target?: number;
  protein_target?: number;
  allergens?: string[];
  dislikes?: string[];
};

export type MealPlan = {
  meal_plan_id: string;
  session_id: string | null;
  plan_name: string | null;
  people: number;
  days: number;
  budget: number | string | null;
  constraints: MealPlanConstraints;
  days_plan: Array<Record<string, unknown>>;
  validation: { valid?: boolean; errors?: string[]; warnings?: string[] } | null;
  status: string;
  revision: number;
  deleted: boolean;
  created_at: string;
  updated_at: string;
};

export async function loadMealPlans(): Promise<MealPlan[]> {
  return apiRequest<MealPlan[]>('/api/meal-plans');
}

export async function loadMealPlan(mealPlanId: string): Promise<MealPlan> {
  return apiRequest<MealPlan>(`/api/meal-plans/${encodeURIComponent(mealPlanId)}`);
}

/** Fixture helpers remain available for the design preview mode. */
import { mealRows, planConstraints, shoppingGroups, validationItems } from '../mock/mealPlans';

export { mealRows, planConstraints, shoppingGroups, validationItems };
