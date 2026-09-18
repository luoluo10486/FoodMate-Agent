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
  meal_slots?: MealPlanMealSlot[];
  executable_meal_count?: number;
  completed_meal_count?: number;
  completion_ratio?: number | string;
};

export type MealPlanMealSlot = {
  meal_plan_meal_id: string;
  day_index: number;
  meal_type: string;
  meal_name: string | null;
  meal: Record<string, unknown>;
  food_log_count: number;
  completed: boolean;
};

export type ShoppingListItem = {
  shopping_list_item_id?: string;
  item_key?: string;
  name: string;
  amount?: number | string | null;
  unit?: string | null;
  purchased?: boolean;
  purchased_at?: string | null;
};

export type ShoppingList = {
  shopping_list_id: string;
  meal_plan_id: string;
  items: ShoppingListItem[];
  status: string;
  created_at: string;
  updated_at: string;
};

export type MealPlanDraft = {
  planName: string;
  startDate: string;
  endDate: string;
  people: string;
  calories: string;
  protein: string;
  budget: string;
  allergens: string[];
  dislikes: string[];
  daysPlan?: Array<Record<string, unknown>>;
};

export type MealPlanUpdateRequest = {
  plan_name?: string | null;
  people: number;
  days: number;
  budget?: number | string | null;
  calorie_target?: number | null;
  protein_target?: number | null;
  allergens?: string[];
  dislikes?: string[];
  days_plan: unknown;
};

export type MealPlanProgress = {
  meal_plan_id: string;
  executable_meal_count: number;
  completed_meal_count: number;
  completion_ratio: number | string;
  meal_slots: MealPlanMealSlot[];
};

export async function loadMealPlans(signal?: AbortSignal): Promise<MealPlan[]> {
  const plans = await apiRequest<MealPlan[]>('/api/meal-plans', { signal });
  return plans.map(normalizeMealPlan);
}

export async function createMealPlan(draft: MealPlanDraft, signal?: AbortSignal): Promise<MealPlan> {
  const days = planDays(draft.startDate, draft.endDate);
  return apiRequest<MealPlan>('/api/meal-plans', {
    method: 'POST',
    headers: { 'Idempotency-Key': idempotencyKey('meal-plan-create') },
    body: JSON.stringify(draftToPlanRequest(draft, days)),
    signal,
  }).then(normalizeMealPlan);
}

export function mealPlanDraftToUpdateRequest(draft: MealPlanDraft): MealPlanUpdateRequest {
  return draftToPlanRequest(draft, planDays(draft.startDate, draft.endDate));
}

export async function loadMealPlan(mealPlanId: string, signal?: AbortSignal): Promise<MealPlan> {
  const plan = await apiRequest<MealPlan>(`/api/meal-plans/${encodeURIComponent(mealPlanId)}`, { signal });
  return normalizeMealPlan(plan);
}

export async function updateMealPlan(
  mealPlanId: string,
  revision: number,
  request: MealPlanUpdateRequest,
  signal?: AbortSignal,
): Promise<MealPlan> {
  const plan = await apiRequest<MealPlan>(`/api/meal-plans/${encodeURIComponent(mealPlanId)}?revision=${revision}`, {
    method: 'PATCH',
    headers: { 'Idempotency-Key': idempotencyKey('meal-plan-update') },
    body: JSON.stringify(request),
    signal,
  });
  return normalizeMealPlan(plan);
}

export async function validateMealPlan(mealPlanId: string, revision: number, signal?: AbortSignal): Promise<MealPlan> {
  const plan = await apiRequest<MealPlan>(
    `/api/meal-plans/${encodeURIComponent(mealPlanId)}/validate?revision=${revision}`,
    {
      method: 'POST',
      headers: { 'Idempotency-Key': idempotencyKey('meal-plan-validate') },
      signal,
    },
  );
  return normalizeMealPlan(plan);
}

export async function saveMealPlan(mealPlanId: string, revision: number, signal?: AbortSignal): Promise<MealPlan> {
  const plan = await apiRequest<MealPlan>(
    `/api/meal-plans/${encodeURIComponent(mealPlanId)}/save?revision=${revision}`,
    {
      method: 'POST',
      headers: { 'Idempotency-Key': idempotencyKey('meal-plan-save') },
      signal,
    },
  );
  return normalizeMealPlan(plan);
}

export async function deleteMealPlan(mealPlanId: string, revision: number, signal?: AbortSignal): Promise<void> {
  await apiRequest<void>(`/api/meal-plans/${encodeURIComponent(mealPlanId)}?revision=${revision}`, {
    method: 'DELETE',
    headers: { 'Idempotency-Key': idempotencyKey('meal-plan-delete') },
    signal,
  });
}

export async function restoreMealPlan(mealPlanId: string, revision: number, signal?: AbortSignal): Promise<MealPlan> {
  const plan = await apiRequest<MealPlan>(
    `/api/meal-plans/${encodeURIComponent(mealPlanId)}/restore?revision=${revision}`,
    {
      method: 'POST',
      headers: { 'Idempotency-Key': idempotencyKey('meal-plan-restore') },
      signal,
    },
  );
  return normalizeMealPlan(plan);
}

export async function loadShoppingList(mealPlanId: string, signal?: AbortSignal): Promise<ShoppingList> {
  return apiRequest<ShoppingList>(`/api/meal-plans/${encodeURIComponent(mealPlanId)}/shopping-list`, { signal });
}

export async function createShoppingList(mealPlanId: string, signal?: AbortSignal): Promise<ShoppingList> {
  return apiRequest<ShoppingList>(`/api/meal-plans/${encodeURIComponent(mealPlanId)}/shopping-list`, {
    method: 'POST',
    signal,
  });
}

export async function loadMealPlanProgress(mealPlanId: string, signal?: AbortSignal): Promise<MealPlanProgress> {
  const progress = await apiRequest<RawMealPlanProgress>(`/api/meal-plans/${encodeURIComponent(mealPlanId)}/progress`, {
    signal,
  });
  return normalizeProgress(progress);
}

export async function updateShoppingItemPurchased(
  mealPlanId: string,
  shoppingListItemId: string,
  purchased: boolean,
  signal?: AbortSignal,
): Promise<ShoppingList> {
  return apiRequest<ShoppingList>(
    `/api/meal-plans/${encodeURIComponent(mealPlanId)}/shopping-list/items/${encodeURIComponent(shoppingListItemId)}`,
    {
      method: 'PATCH',
      headers: { 'Idempotency-Key': idempotencyKey('shopping-item') },
      body: JSON.stringify({ purchased }),
      signal,
    },
  );
}

function planDays(startDate: string, endDate: string) {
  const start = Date.parse(`${startDate}T00:00:00Z`);
  const end = Date.parse(`${endDate}T00:00:00Z`);
  if (!Number.isFinite(start) || !Number.isFinite(end) || end < start) return 1;
  return Math.min(7, Math.max(1, Math.floor((end - start) / 86_400_000) + 1));
}

function draftToPlanRequest(draft: MealPlanDraft, days: number) {
  return {
    plan_name: draft.planName.trim() || '我的餐食计划',
    people: numberOrUndefined(draft.people) ?? 1,
    days,
    budget: numberOrUndefined(draft.budget),
    calorie_target: numberOrUndefined(draft.calories),
    protein_target: numberOrUndefined(draft.protein),
    allergens: draft.allergens,
    dislikes: draft.dislikes,
    // 编辑计划时沿用服务端的餐表，创建草稿时才生成满足后端结构校验的初始餐表。
    days_plan: draft.daysPlan ?? buildDaysPlan(days),
  };
}

type RawMealPlanMealSlot = Partial<MealPlanMealSlot> & {
  mealPlanMealId?: string;
  dayIndex?: number;
  mealType?: string;
  mealName?: string | null;
  foodLogCount?: number;
};

type RawMealPlanProgress = Partial<MealPlanProgress> & {
  mealPlanId?: string | number;
  executableMealCount?: number;
  completedMealCount?: number;
  completionRatio?: number | string;
  mealSlots?: RawMealPlanMealSlot[];
};

function normalizeMealSlot(slot: RawMealPlanMealSlot): MealPlanMealSlot {
  return {
    meal_plan_meal_id: String(slot.meal_plan_meal_id ?? slot.mealPlanMealId ?? ''),
    day_index: Number(slot.day_index ?? slot.dayIndex ?? 0),
    meal_type: String(slot.meal_type ?? slot.mealType ?? ''),
    meal_name: slot.meal_name ?? slot.mealName ?? null,
    meal: slot.meal ?? {},
    food_log_count: Number(slot.food_log_count ?? slot.foodLogCount ?? 0),
    completed: Boolean(slot.completed),
  };
}

function normalizeMealPlan(plan: MealPlan): MealPlan {
  const raw = plan as MealPlan & {
    mealPlanId?: string | number;
    sessionId?: string | number | null;
    planName?: string | null;
    daysPlan?: Array<Record<string, unknown>>;
    mealSlots?: RawMealPlanMealSlot[];
    executableMealCount?: number;
    completedMealCount?: number;
    completionRatio?: number | string;
  };
  return {
    ...plan,
    meal_plan_id: String(raw.meal_plan_id ?? raw.mealPlanId ?? ''),
    session_id:
      raw.session_id ?? (raw.sessionId === undefined || raw.sessionId === null ? null : String(raw.sessionId)),
    plan_name: raw.plan_name ?? raw.planName ?? null,
    days_plan: raw.days_plan ?? raw.daysPlan ?? [],
    meal_slots: (raw.meal_slots ?? raw.mealSlots ?? []).map(normalizeMealSlot),
    executable_meal_count: raw.executable_meal_count ?? raw.executableMealCount ?? 0,
    completed_meal_count: raw.completed_meal_count ?? raw.completedMealCount ?? 0,
    completion_ratio: raw.completion_ratio ?? raw.completionRatio ?? 0,
  };
}

function normalizeProgress(progress: RawMealPlanProgress): MealPlanProgress {
  return {
    meal_plan_id: String(progress.meal_plan_id ?? progress.mealPlanId ?? ''),
    executable_meal_count: progress.executable_meal_count ?? progress.executableMealCount ?? 0,
    completed_meal_count: progress.completed_meal_count ?? progress.completedMealCount ?? 0,
    completion_ratio: progress.completion_ratio ?? progress.completionRatio ?? 0,
    meal_slots: (progress.meal_slots ?? progress.mealSlots ?? []).map(normalizeMealSlot),
  };
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

/** 保留 Fixture 辅助数据，供设计预览模式使用。 */
import { mealRows, planConstraints, shoppingGroups, validationItems } from '../mock/mealPlans';

export { mealRows, planConstraints, shoppingGroups, validationItems };
