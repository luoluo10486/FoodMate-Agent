import { afterEach, describe, expect, it, vi } from 'vitest';
import { createCompositeDish, loadCompositeDishes } from './compositeDishService';

describe('compositeDishService', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('loads user-owned composite dishes', async () => {
    const data = [{ composite_dish_id: '10', dish_name: '鸡肉饭', components: [] }];
    const fetchMock = vi
      .fn()
      .mockResolvedValue(new Response(JSON.stringify({ success: true, data }), { status: 200 }));
    vi.stubGlobal('fetch', fetchMock);

    await expect(loadCompositeDishes()).resolves.toEqual(data);
    expect(fetchMock).toHaveBeenCalledWith('/api/composite-dishes', expect.objectContaining({ method: 'GET' }));
  });

  it('sends the ingredient catalog IDs when creating a dish', async () => {
    const data = { composite_dish_id: '10', dish_name: '鸡肉饭' };
    const fetchMock = vi
      .fn()
      .mockResolvedValue(new Response(JSON.stringify({ success: true, data }), { status: 200 }));
    vi.stubGlobal('fetch', fetchMock);

    await createCompositeDish({
      dish_name: '鸡肉饭',
      total_servings: 2,
      components: [{ nutrition_food_id: 171477, raw_name: '熟鸡胸肉', amount: 300, unit: 'g' }],
    });
    expect(fetchMock.mock.calls[0][0]).toBe('/api/composite-dishes');
    expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toMatchObject({
      components: [{ nutrition_food_id: 171477, amount: 300 }],
    });
  });
});
