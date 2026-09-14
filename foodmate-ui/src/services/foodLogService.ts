import { apiRequest } from './apiClient';

export type FoodLogItem = {
  food_log_item_id: string;
  item_order: number;
  raw_name: string;
  nutrition_food_id?: string | null;
  amount: number | string;
  unit: string;
  nutrition_status: string;
  calories_kcal: number | string | null;
  protein_g: number | string | null;
  fat_g: number | string | null;
  carbs_g: number | string | null;
};

export type FoodLog = {
  food_log_id: string;
  meal_time: string;
  meal_type: 'breakfast' | 'lunch' | 'dinner' | 'snack' | string;
  notes: string | null;
  source: string;
  meal_plan_meal_id?: string | null;
  composite_dish_id?: string | null;
  composite_dish_revision?: number | null;
  composite_dish_servings?: number | string | null;
  revision: number;
  deleted: boolean;
  items: FoodLogItem[];
};

export type FoodLogWriteRequest = {
  meal_time: string;
  meal_type: FoodLog['meal_type'];
  notes?: string;
  meal_plan_meal_id?: string;
  composite_dish_id?: string;
  composite_dish_revision?: number;
  composite_dish_servings?: number;
  items: Array<{ raw_name: string; amount: number; unit: string; nutrition_food_id?: string }>;
};

export async function loadFoodLogs(from: string, to: string, signal?: AbortSignal): Promise<FoodLog[]> {
  return apiRequest<FoodLog[]>(
    `/api/food-logs?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`,
    signal ? { signal } : undefined,
  );
}

export async function createFoodLog(request: FoodLogWriteRequest): Promise<FoodLog> {
  return apiRequest<FoodLog>('/api/food-logs', {
    method: 'POST',
    headers: { 'Idempotency-Key': idempotencyKey('food-log-create') },
    body: JSON.stringify(request),
  });
}

export async function updateFoodLog(
  foodLogId: string,
  revision: number,
  request: FoodLogWriteRequest,
): Promise<FoodLog> {
  return apiRequest<FoodLog>(`/api/food-logs/${encodeURIComponent(foodLogId)}?revision=${revision}`, {
    method: 'PATCH',
    headers: { 'Idempotency-Key': idempotencyKey('food-log-update') },
    body: JSON.stringify(request),
  });
}

export async function deleteFoodLog(foodLogId: string, revision: number): Promise<void> {
  await apiRequest<void>(`/api/food-logs/${encodeURIComponent(foodLogId)}?revision=${revision}`, {
    method: 'DELETE',
    headers: { 'Idempotency-Key': idempotencyKey('food-log-delete') },
  });
}

export async function loadDeletedFoodLogs(signal?: AbortSignal): Promise<FoodLog[]> {
  return apiRequest<FoodLog[]>('/api/food-logs/deleted', signal ? { signal } : undefined);
}

export async function restoreFoodLog(foodLogId: string, revision: number): Promise<FoodLog> {
  return apiRequest<FoodLog>(`/api/food-logs/${encodeURIComponent(foodLogId)}/restore?revision=${revision}`, {
    method: 'POST',
    headers: { 'Idempotency-Key': idempotencyKey('food-log-restore') },
  });
}

function idempotencyKey(prefix: string) {
  const suffix = globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random().toString(16).slice(2)}`;
  return `${prefix}-${suffix}`;
}
