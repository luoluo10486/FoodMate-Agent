import { apiRequest } from './apiClient';

export type CompositeDishComponent = {
  item_id: string;
  item_order: number;
  nutrition_food_id: string;
  raw_name: string;
  amount: number | string;
  unit: string;
  normalized_amount: number | string;
  normalized_unit: string;
  calories_kcal: number | string;
  protein_g: number | string;
  fat_g: number | string;
  carbs_g: number | string;
};

export type CompositeDish = {
  composite_dish_id: string;
  dish_name: string;
  total_servings: number | string;
  calories_kcal_per_serving: number | string;
  protein_g_per_serving: number | string;
  fat_g_per_serving: number | string;
  carbs_g_per_serving: number | string;
  nutrition_source: string;
  revision: number;
  deleted: boolean;
  created_at: string;
  updated_at: string;
  components: CompositeDishComponent[];
};

export type CompositeDishWriteRequest = {
  dish_name: string;
  total_servings: number;
  components: Array<{ nutrition_food_id: number; raw_name: string; amount: number; unit: string }>;
};

export async function loadCompositeDishes(signal?: AbortSignal): Promise<CompositeDish[]> {
  return apiRequest<CompositeDish[]>('/api/composite-dishes', signal ? { signal } : undefined);
}

export async function loadCompositeDish(compositeDishId: string, signal?: AbortSignal): Promise<CompositeDish> {
  return apiRequest<CompositeDish>(
    `/api/composite-dishes/${encodeURIComponent(compositeDishId)}`,
    signal ? { signal } : undefined,
  );
}

export async function createCompositeDish(
  request: CompositeDishWriteRequest,
  signal?: AbortSignal,
): Promise<CompositeDish> {
  return apiRequest<CompositeDish>('/api/composite-dishes', {
    method: 'POST',
    headers: { 'Idempotency-Key': idempotencyKey('composite-dish-create') },
    body: JSON.stringify(request),
    signal,
  });
}

export async function updateCompositeDish(
  compositeDishId: string,
  revision: number,
  request: CompositeDishWriteRequest,
  signal?: AbortSignal,
): Promise<CompositeDish> {
  return apiRequest<CompositeDish>(
    `/api/composite-dishes/${encodeURIComponent(compositeDishId)}?revision=${revision}`,
    {
      method: 'PATCH',
      headers: { 'Idempotency-Key': idempotencyKey('composite-dish-update') },
      body: JSON.stringify(request),
      signal,
    },
  );
}

export async function deleteCompositeDish(
  compositeDishId: string,
  revision: number,
  signal?: AbortSignal,
): Promise<void> {
  await apiRequest<void>(`/api/composite-dishes/${encodeURIComponent(compositeDishId)}?revision=${revision}`, {
    method: 'DELETE',
    headers: { 'Idempotency-Key': idempotencyKey('composite-dish-delete') },
    signal,
  });
}

function idempotencyKey(prefix: string) {
  const suffix = globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random().toString(16).slice(2)}`;
  return `${prefix}-${suffix}`;
}
