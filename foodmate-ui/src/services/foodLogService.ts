import { apiRequest } from './apiClient';

export type FoodLogItem = {
  food_log_item_id: string;
  item_order: number;
  raw_name: string;
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
  revision: number;
  deleted: boolean;
  items: FoodLogItem[];
};

type CreateFoodLogRequest = {
  meal_time: string;
  meal_type: FoodLog['meal_type'];
  notes?: string;
  items: Array<{ raw_name: string; amount: number; unit: string }>;
};

export async function loadFoodLogs(from: string, to: string): Promise<FoodLog[]> {
  return apiRequest<FoodLog[]>(`/api/food-logs?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`);
}

export async function createFoodLog(request: CreateFoodLogRequest): Promise<FoodLog> {
  return apiRequest<FoodLog>('/api/food-logs', {
    method: 'POST',
    headers: { 'Idempotency-Key': idempotencyKey('food-log-create') },
    body: JSON.stringify(request),
  });
}

export async function deleteFoodLog(foodLogId: string, revision: number): Promise<void> {
  await apiRequest<void>(`/api/food-logs/${encodeURIComponent(foodLogId)}?revision=${revision}`, {
    method: 'DELETE',
    headers: { 'Idempotency-Key': idempotencyKey('food-log-delete') },
  });
}

function idempotencyKey(prefix: string) {
  const suffix = globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random().toString(16).slice(2)}`;
  return `${prefix}-${suffix}`;
}
