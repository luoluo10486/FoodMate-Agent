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

export type ShoppingList = {
  shopping_list_id: string;
  meal_plan_id: string;
  items: Array<Record<string, unknown>>;
  status: string;
  created_at: string;
  updated_at: string;
};

export type MealPlanDraft = {
  planName: string;
  startDate: string;
  endDate: string;
  calories: string;
  protein: string;
  budget: string;
  allergens: string[];
  dislikes: string[];
};

export async function loadMealPlans(): Promise<MealPlan[]> {
  return apiRequest<MealPlan[]>('/api/meal-plans');
}

export async function createMealPlan(draft: MealPlanDraft): Promise<MealPlan> {
  const days = planDays(draft.startDate, draft.endDate);
  return apiRequest<MealPlan>('/api/meal-plans', {
    method: 'POST',
    headers: { 'Idempotency-Key': idempotencyKey('meal-plan-create') },
    body: JSON.stringify({
      plan_name: draft.planName.trim() || '我的餐食计划',
      people: 1,
      days,
      budget: numberOrUndefined(draft.budget),
      calorie_target: numberOrUndefined(draft.calories),
      protein_target: numberOrUndefined(draft.protein),
      allergens: draft.allergens,
      dislikes: draft.dislikes,
      days_plan: buildDaysPlan(days),
    }),
  });
}

export async function loadMealPlan(mealPlanId: string): Promise<MealPlan> {
  return apiRequest<MealPlan>(`/api/meal-plans/${encodeURIComponent(mealPlanId)}`);
}

export async function loadShoppingList(mealPlanId: string): Promise<ShoppingList> {
  return apiRequest<ShoppingList>(`/api/meal-plans/${encodeURIComponent(mealPlanId)}/shopping-list`);
}

function planDays(startDate: string, endDate: string) {
  const start = Date.parse(`${startDate}T00:00:00Z`);
  const end = Date.parse(`${endDate}T00:00:00Z`);
  if (!Number.isFinite(start) || !Number.isFinite(end) || end < start) return 1;
  return Math.min(7, Math.max(1, Math.floor((end - start) / 86_400_000) + 1));
}

function numberOrUndefined(value: string) {
  const parsed = Number(value);
  return Number.isFinite(parsed) && parsed >= 0 ? parsed : undefined;
}

function buildDaysPlan(days: number) {
  return Array.from({ length: days }, (_, index) => ({
    day: index + 1,
    breakfast: {
      name: '燕麦莓果碗',
      ingredients: [
        { name: '燕麦', amount: 60, unit: 'g' },
        { name: '蓝莓', amount: 80, unit: 'g' },
      ],
    },
    lunch: {
      name: '鸡胸藜麦碗',
      ingredients: [
        { name: '鸡胸肉', amount: 150, unit: 'g' },
        { name: '藜麦', amount: 80, unit: 'g' },
      ],
    },
    dinner: {
      name: '番茄豆腐汤',
      ingredients: [
        { name: '豆腐', amount: 200, unit: 'g' },
        { name: '番茄', amount: 150, unit: 'g' },
      ],
    },
  }));
}

function idempotencyKey(prefix: string) {
  const suffix = globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random().toString(16).slice(2)}`;
  return `${prefix}-${suffix}`;
}

/** Fixture helpers remain available for the design preview mode. */
import { mealRows, planConstraints, shoppingGroups, validationItems } from '../mock/mealPlans';

export { mealRows, planConstraints, shoppingGroups, validationItems };
