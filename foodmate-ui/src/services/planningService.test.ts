import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  createMealPlan,
  createShoppingList,
  deleteMealPlan,
  loadMealPlan,
  loadMealPlans,
  loadMealPlanProgress,
  loadShoppingList,
  restoreMealPlan,
  saveMealPlan,
  updateMealPlan,
  updateShoppingItemPurchased,
  validateMealPlan,
} from './planningService';

describe('planningService lifecycle APIs', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('sends the backend meal plan shape when creating a plan', async () => {
    const response = {
      meal_plan_id: '10',
      plan_name: '一周计划',
      people: 1,
      days: 2,
      revision: 1,
      meal_slots: [],
    };
    const fetchMock = vi
      .fn()
      .mockImplementation(() =>
        Promise.resolve(new Response(JSON.stringify({ success: true, data: response }), { status: 200 })),
      );
    vi.stubGlobal('fetch', fetchMock);
    const controller = new AbortController();

    await createMealPlan(
      {
        planName: '一周计划',
        startDate: '2026-09-13',
        endDate: '2026-09-14',
        people: '2',
        calories: '2200',
        protein: '130',
        budget: '120',
        allergens: ['花生'],
        dislikes: ['香菜'],
      },
      controller.signal,
    );

    const [, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(JSON.parse(String(init.body))).toMatchObject({
      plan_name: '一周计划',
      people: 2,
      days: 2,
      calorie_target: 2200,
      protein_target: 130,
      allergens: ['花生'],
      dislikes: ['香菜'],
    });
    expect(new Headers(init.headers).get('Idempotency-Key')).toMatch(/^meal-plan-create-/);
    expect(init.signal).toBe(controller.signal);
  });

  it('uses revision and idempotency headers for the plan lifecycle', async () => {
    const response = {
      meal_plan_id: '10',
      plan_name: '一周计划',
      people: 1,
      days: 1,
      revision: 2,
      meal_slots: [],
    };
    const fetchMock = vi
      .fn()
      .mockImplementation(() =>
        Promise.resolve(new Response(JSON.stringify({ success: true, data: response }), { status: 200 })),
      );
    vi.stubGlobal('fetch', fetchMock);
    const controller = new AbortController();

    await updateMealPlan('10', 1, { people: 1, days: 1, days_plan: [] }, controller.signal);
    await validateMealPlan('10', 2, controller.signal);
    await saveMealPlan('10', 3, controller.signal);
    await deleteMealPlan('10', 4, controller.signal);
    await restoreMealPlan('10', 5, controller.signal);

    expect(fetchMock.mock.calls.map(([path, init]) => [path, init.method])).toEqual([
      ['/api/meal-plans/10?revision=1', 'PATCH'],
      ['/api/meal-plans/10/validate?revision=2', 'POST'],
      ['/api/meal-plans/10/save?revision=3', 'POST'],
      ['/api/meal-plans/10?revision=4', 'DELETE'],
      ['/api/meal-plans/10/restore?revision=5', 'POST'],
    ]);
    for (const [, init] of fetchMock.mock.calls.slice(0, 5) as Array<[string, RequestInit]>) {
      expect(new Headers(init.headers).get('Idempotency-Key')).toMatch(/^meal-plan-/);
      expect(init.signal).toBe(controller.signal);
    }
  });

  it('distinguishes shopping list creation from reading and normalizes progress fields', async () => {
    const fetchMock = vi.fn().mockImplementation(async (path: string) => {
      if (path.endsWith('/progress'))
        return new Response(
          JSON.stringify({
            success: true,
            data: {
              mealPlanId: 10,
              executableMealCount: 3,
              completedMealCount: 1,
              completionRatio: 0.3333,
              mealSlots: [
                {
                  mealPlanMealId: '100',
                  dayIndex: 0,
                  mealType: 'lunch',
                  mealName: '鸡肉藜麦碗',
                  meal: {},
                  foodLogCount: 1,
                  completed: true,
                },
              ],
            },
          }),
          { status: 200 },
        );
      return new Response(JSON.stringify({ success: true, data: { items: [] } }), { status: 200 });
    });
    vi.stubGlobal('fetch', fetchMock);

    await createShoppingList('10');
    const progress = await loadMealPlanProgress('10');

    expect(fetchMock.mock.calls[0][0]).toBe('/api/meal-plans/10/shopping-list');
    expect(fetchMock.mock.calls[0][1].method).toBe('POST');
    expect(progress).toMatchObject({
      meal_plan_id: '10',
      executable_meal_count: 3,
      completed_meal_count: 1,
      meal_slots: [{ meal_plan_meal_id: '100', day_index: 0, meal_type: 'lunch', completed: true }],
    });
  });

  it('forwards abort signals for planning reads and shopping mutations', async () => {
    const fetchMock = vi.fn().mockImplementation((path: string) => {
      const data = path === '/api/meal-plans' ? [] : path.endsWith('/shopping-list') ? { items: [] } : {};
      return Promise.resolve(new Response(JSON.stringify({ success: true, data }), { status: 200 }));
    });
    vi.stubGlobal('fetch', fetchMock);
    const signals = [
      new AbortController().signal,
      new AbortController().signal,
      new AbortController().signal,
      new AbortController().signal,
    ];

    await loadMealPlans(signals[0]);
    await loadMealPlan('10', signals[1]);
    await loadShoppingList('10', signals[2]);
    await loadMealPlanProgress('10', signals[3]);
    await createShoppingList('10', signals[0]);
    await updateShoppingItemPurchased('10', 'item-1', true, signals[0]);

    expect(fetchMock.mock.calls.map(([, init]) => init.signal)).toEqual([
      signals[0],
      signals[1],
      signals[2],
      signals[3],
      signals[0],
      signals[0],
    ]);
  });
});
