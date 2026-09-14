import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { DietRecordsPage } from './DietRecordsPage';
import {
  createFoodLog,
  deleteFoodLog,
  loadDeletedFoodLogs,
  loadFoodLogs,
  restoreFoodLog,
  updateFoodLog,
} from '../../services/foodLogService';
import { searchNutritionFoods } from '../../services/nutritionFoodService';
import {
  createCompositeDish,
  deleteCompositeDish,
  loadCompositeDish,
  loadCompositeDishes,
  updateCompositeDish,
} from '../../services/compositeDishService';
import { ApiError } from '../../services/apiClient';

vi.mock('../../services/foodLogService', () => ({
  createFoodLog: vi.fn(),
  deleteFoodLog: vi.fn(),
  loadDeletedFoodLogs: vi.fn(),
  loadFoodLogs: vi.fn(),
  restoreFoodLog: vi.fn(),
  updateFoodLog: vi.fn(),
}));

vi.mock('../../services/nutritionFoodService', () => ({
  searchNutritionFoods: vi.fn(),
}));

vi.mock('../../services/compositeDishService', () => ({
  loadCompositeDishes: vi.fn(),
  loadCompositeDish: vi.fn(),
  createCompositeDish: vi.fn(),
  updateCompositeDish: vi.fn(),
  deleteCompositeDish: vi.fn(),
}));

const log = {
  food_log_id: '11',
  meal_time: '2026-08-22T08:30:00Z',
  meal_type: 'breakfast',
  notes: null,
  source: 'manual',
  revision: 2,
  deleted: false,
  items: [
    {
      food_log_item_id: '101',
      item_order: 0,
      raw_name: '服务端燕麦',
      amount: 100,
      unit: 'g',
      nutrition_status: 'matched',
      calories_kcal: 380,
      protein_g: 13,
      fat_g: 7,
      carbs_g: 68,
    },
  ],
};

const multiItemLog = {
  ...log,
  items: [
    log.items[0],
    {
      ...log.items[0],
      food_log_item_id: '102',
      raw_name: '保留香蕉',
      item_order: 1,
    },
  ],
};

const compositeDish = {
  composite_dish_id: '21',
  dish_name: '鸡肉饭',
  total_servings: 2,
  calories_kcal_per_serving: 330,
  protein_g_per_serving: 31,
  fat_g_per_serving: 7,
  carbs_g_per_serving: 42,
  nutrition_source: 'USDA:2025',
  revision: 4,
  deleted: false,
  created_at: '2026-08-22T08:30:00Z',
  updated_at: '2026-08-22T08:30:00Z',
  components: [
    {
      item_id: '201',
      item_order: 0,
      nutrition_food_id: '171477',
      raw_name: '熟鸡胸肉',
      amount: 300,
      unit: 'g',
      normalized_amount: 300,
      normalized_unit: 'g',
      calories_kcal: 495,
      protein_g: 93,
      fat_g: 10.8,
      carbs_g: 0,
    },
  ],
};

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/analysis?view=records']}>
      <DietRecordsPage />
    </MemoryRouter>,
  );
}

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((promiseResolve, promiseReject) => {
    resolve = promiseResolve;
    reject = promiseReject;
  });
  return { promise, resolve, reject };
}

describe('DietRecordsPage real mode', () => {
  beforeEach(() => {
    vi.stubEnv('VITE_AGENT_MODE', 'real');
    localStorage.setItem(
      'foodmate_auth_user',
      JSON.stringify({ id: '7', username: 'tester', displayName: 'Tester', role: 'user', status: 'active' }),
    );
    vi.mocked(searchNutritionFoods).mockResolvedValue([]);
    vi.mocked(loadCompositeDishes).mockResolvedValue([]);
    vi.mocked(loadCompositeDish).mockResolvedValue(compositeDish);
  });

  afterEach(() => {
    vi.unstubAllEnvs();
    localStorage.clear();
    vi.clearAllMocks();
  });

  it('renders server records and does not fall back to fixture foods', async () => {
    vi.mocked(loadFoodLogs).mockResolvedValue([log]);
    renderPage();

    await waitFor(() => expect(screen.getByText('服务端燕麦')).toBeInTheDocument());
    expect(screen.queryByText('蓝莓燕麦粥')).not.toBeInTheDocument();
    expect(screen.getByText('C: 68g')).toBeInTheDocument();
    expect(screen.getByText('能量合计')).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: '周视图' })).toBeEnabled();
  });

  it('creates a composite dish and refreshes the server list', async () => {
    vi.mocked(loadFoodLogs).mockResolvedValue([]);
    vi.mocked(loadCompositeDishes).mockResolvedValueOnce([]).mockResolvedValueOnce([compositeDish]);
    vi.mocked(searchNutritionFoods).mockResolvedValue([
      {
        nutrition_food_id: '171477',
        standard_name: 'Chicken breast, cooked',
        chinese_name: '熟鸡胸肉',
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
    ]);
    vi.mocked(createCompositeDish).mockResolvedValue(compositeDish);
    const user = userEvent.setup();
    renderPage();

    await waitFor(() => expect(screen.getByRole('button', { name: '新建复合菜' })).toBeInTheDocument());
    await user.click(screen.getByRole('button', { name: '新建复合菜' }));
    await user.type(screen.getByRole('textbox', { name: '复合菜名称' }), '鸡肉饭');
    await user.type(screen.getByRole('textbox', { name: '第1项食材名称' }), '鸡胸肉');
    await waitFor(() => expect(screen.getByRole('button', { name: /熟鸡胸肉/ })).toBeInTheDocument());
    await user.click(screen.getByRole('button', { name: /熟鸡胸肉/ }));
    await user.clear(screen.getByRole('spinbutton', { name: '第1项食材用量' }));
    await user.type(screen.getByRole('spinbutton', { name: '第1项食材用量' }), '300');
    await user.click(screen.getByRole('button', { name: '保存复合菜' }));

    await waitFor(() =>
      expect(createCompositeDish).toHaveBeenCalledWith(
        expect.objectContaining({
          dish_name: '鸡肉饭',
          components: [{ nutrition_food_id: 171477, raw_name: '熟鸡胸肉', amount: 300, unit: 'g' }],
        }),
        expect.any(AbortSignal),
      ),
    );
    await waitFor(() => expect(screen.getByText('鸡肉饭')).toBeInTheDocument());
    expect(loadCompositeDishes).toHaveBeenCalledTimes(2);
  });

  it('updates a composite dish with its revision and refreshes the server list', async () => {
    const updated = { ...compositeDish, dish_name: '更新后的鸡肉饭', revision: 5 };
    vi.mocked(loadFoodLogs).mockResolvedValue([]);
    vi.mocked(loadCompositeDishes).mockResolvedValueOnce([compositeDish]).mockResolvedValueOnce([updated]);
    vi.mocked(updateCompositeDish).mockResolvedValue(updated);
    const user = userEvent.setup();
    renderPage();

    await waitFor(() => expect(screen.getByText('鸡肉饭')).toBeInTheDocument());
    await user.click(screen.getByRole('button', { name: '编辑' }));
    const nameInput = screen.getByRole('textbox', { name: '复合菜名称' });
    await user.clear(nameInput);
    await user.type(nameInput, '更新后的鸡肉饭');
    await user.click(screen.getByRole('button', { name: '保存复合菜' }));

    await waitFor(() =>
      expect(updateCompositeDish).toHaveBeenCalledWith(
        '21',
        4,
        expect.objectContaining({ dish_name: '更新后的鸡肉饭' }),
        expect.any(AbortSignal),
      ),
    );
    await waitFor(() => expect(screen.getByText('更新后的鸡肉饭')).toBeInTheDocument());
    expect(loadCompositeDishes).toHaveBeenCalledTimes(2);
    expect(loadCompositeDish).toHaveBeenCalledWith('21', expect.any(AbortSignal));
  });

  it('confirms composite dish deletion and keeps the dialog open on a conflict', async () => {
    vi.mocked(loadFoodLogs).mockResolvedValue([]);
    vi.mocked(loadCompositeDishes).mockResolvedValue([compositeDish]);
    vi.mocked(deleteCompositeDish).mockRejectedValue(new ApiError('CONFLICT', 'stale revision', 409));
    const user = userEvent.setup();
    renderPage();

    await waitFor(() => expect(screen.getByText('鸡肉饭')).toBeInTheDocument());
    await user.click(screen.getByRole('button', { name: /^删除$/ }));
    expect(screen.getByRole('dialog')).toHaveTextContent('确认删除复合菜');
    await user.click(screen.getByRole('button', { name: '确认删除' }));

    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('复合菜已被修改，请重新加载后再试。'));
    expect(screen.getByRole('dialog')).toBeInTheDocument();
    expect(screen.getByText('鸡肉饭')).toBeInTheDocument();
    expect(deleteCompositeDish).toHaveBeenCalledWith('21', 4, expect.any(AbortSignal));
  });

  it('can retry the composite dish list after a real request failure', async () => {
    vi.mocked(loadFoodLogs).mockResolvedValue([]);
    vi.mocked(loadCompositeDishes)
      .mockRejectedValueOnce(new ApiError('NETWORK_ERROR', 'network'))
      .mockResolvedValue([compositeDish]);
    const user = userEvent.setup();
    renderPage();

    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('network'));
    await user.click(screen.getByRole('button', { name: '重试加载' }));
    await waitFor(() => expect(screen.getByText('鸡肉饭')).toBeInTheDocument());
    expect(loadCompositeDishes).toHaveBeenCalledTimes(2);
  });

  it('clears stale composite dishes when a refresh fails', async () => {
    vi.mocked(loadFoodLogs).mockResolvedValue([]);
    vi.mocked(loadCompositeDishes)
      .mockResolvedValueOnce([compositeDish])
      .mockRejectedValueOnce(new ApiError('NETWORK_ERROR', '复合菜服务不可用'));
    vi.mocked(updateCompositeDish).mockResolvedValue({ ...compositeDish, dish_name: '更新后的鸡肉饭' });
    const user = userEvent.setup();
    renderPage();

    await waitFor(() => expect(screen.getByText('鸡肉饭')).toBeInTheDocument());
    await user.click(screen.getByRole('button', { name: '编辑' }));
    await waitFor(() => expect(loadCompositeDish).toHaveBeenCalledWith('21', expect.any(AbortSignal)));
    const nameInput = screen.getByRole('textbox', { name: '复合菜名称' });
    await user.clear(nameInput);
    await user.type(nameInput, '更新后的鸡肉饭');
    await user.click(screen.getByRole('button', { name: '保存复合菜' }));

    await waitFor(() => expect(screen.getByText('复合菜服务不可用')).toBeInTheDocument());
    expect(screen.queryByRole('button', { name: '记录这道菜' })).not.toBeInTheDocument();
  });

  it('creates a real food log from the add-food dialog', async () => {
    const created = {
      ...log,
      food_log_id: '12',
      items: [{ ...log.items[0], raw_name: '新食物' }],
    };
    vi.mocked(loadFoodLogs).mockResolvedValueOnce([]).mockResolvedValue([created]);
    vi.mocked(createFoodLog).mockResolvedValue(created);
    const user = userEvent.setup();
    renderPage();

    await waitFor(() => expect(screen.getByRole('button', { name: '记录一餐' })).toBeInTheDocument());
    await user.click(screen.getByRole('button', { name: '记录一餐' }));
    await user.type(screen.getByPlaceholderText('例如：煮鸡蛋 2 个'), '新食物');
    await user.click(screen.getByRole('button', { name: /^添加$/ }));

    await waitFor(() =>
      expect(createFoodLog).toHaveBeenCalledWith(
        expect.objectContaining({ meal_type: 'breakfast' }),
        expect.any(AbortSignal),
      ),
    );
    expect(screen.getByText('新食物')).toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent('新食物 已提交');
  });

  it('cancels a pending food-log create when the dialog closes and ignores the late response', async () => {
    vi.mocked(loadFoodLogs).mockResolvedValue([]);
    const pending = deferred<typeof log>();
    vi.mocked(createFoodLog).mockImplementation((_request, signal) => {
      expect(signal).toBeInstanceOf(AbortSignal);
      return pending.promise;
    });
    const user = userEvent.setup();
    renderPage();

    await waitFor(() => expect(screen.getByRole('button', { name: '记录一餐' })).toBeInTheDocument());
    await user.click(screen.getByRole('button', { name: '记录一餐' }));
    await user.type(screen.getByPlaceholderText('例如：煮鸡蛋 2 个'), '待取消食物');
    await user.click(screen.getByRole('button', { name: /^添加$/ }));

    await waitFor(() => expect(createFoodLog).toHaveBeenCalledTimes(1));
    const signal = vi.mocked(createFoodLog).mock.calls[0][1];
    await user.click(screen.getByRole('button', { name: '取消' }));

    expect(signal?.aborted).toBe(true);
    pending.resolve({ ...log, food_log_id: 'cancelled', items: [{ ...log.items[0], raw_name: '待取消食物' }] });
    await Promise.resolve();
    expect(screen.queryByText('待取消食物 已提交')).not.toBeInTheDocument();
  });

  it('submits the explicitly selected nutrition catalog candidate', async () => {
    vi.mocked(loadFoodLogs).mockResolvedValue([]);
    vi.mocked(searchNutritionFoods).mockResolvedValue([
      {
        nutrition_food_id: '171477',
        standard_name: 'Chicken breast, cooked',
        chinese_name: '熟鸡胸肉',
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
    ]);
    vi.mocked(createFoodLog).mockResolvedValue({
      ...log,
      food_log_id: '13',
      items: [{ ...log.items[0], raw_name: '熟鸡胸肉', nutrition_food_id: '171477' }],
    });
    const user = userEvent.setup();
    renderPage();

    await waitFor(() => expect(screen.getByRole('button', { name: '记录一餐' })).toBeInTheDocument());
    await user.click(screen.getByRole('button', { name: '记录一餐' }));
    await user.type(screen.getByRole('textbox', { name: '食物名称' }), '鸡胸肉');
    await waitFor(() => expect(screen.getByRole('button', { name: /熟鸡胸肉/ })).toBeInTheDocument());
    expect(screen.getByText(/每 100g：165 kcal · 蛋白质 31 g/)).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /熟鸡胸肉/ }));
    await user.clear(screen.getByRole('spinbutton', { name: '食物份量' }));
    await user.type(screen.getByRole('spinbutton', { name: '食物份量' }), '150');
    await user.clear(screen.getByRole('textbox', { name: '食物单位' }));
    await user.type(screen.getByRole('textbox', { name: '食物单位' }), 'g');
    await user.click(screen.getByRole('button', { name: /^添加$/ }));

    await waitFor(() =>
      expect(createFoodLog).toHaveBeenCalledWith(
        expect.objectContaining({
          items: [{ raw_name: '熟鸡胸肉', amount: 150, unit: 'g', nutrition_food_id: '171477' }],
        }),
        expect.any(AbortSignal),
      ),
    );
  });

  it('deletes a real log with the server revision', async () => {
    vi.mocked(loadFoodLogs).mockResolvedValueOnce([log]).mockResolvedValue([]);
    vi.mocked(deleteFoodLog).mockResolvedValue();
    const user = userEvent.setup();
    renderPage();

    await waitFor(() => expect(screen.getByRole('button', { name: '删除服务端燕麦所在记录' })).toBeInTheDocument());
    await user.click(screen.getByRole('button', { name: '删除服务端燕麦所在记录' }));
    await user.click(screen.getByRole('button', { name: '确认移除' }));

    await waitFor(() => expect(deleteFoodLog).toHaveBeenCalledWith('11', 2, expect.any(AbortSignal)));
    expect(screen.queryByText('服务端燕麦')).not.toBeInTheDocument();
  });

  it('updates only the removed item when a food log contains multiple items', async () => {
    const updated = { ...multiItemLog, revision: 3, items: [multiItemLog.items[1]] };
    vi.mocked(loadFoodLogs).mockResolvedValueOnce([multiItemLog]).mockResolvedValue([updated]);
    vi.mocked(updateFoodLog).mockResolvedValue(updated);
    const user = userEvent.setup();
    renderPage();

    await waitFor(() => expect(screen.getByText('保留香蕉')).toBeInTheDocument());
    await user.click(screen.getByRole('button', { name: '删除服务端燕麦所在记录' }));
    await user.click(screen.getByRole('button', { name: '确认移除' }));

    await waitFor(() =>
      expect(updateFoodLog).toHaveBeenCalledWith(
        '11',
        2,
        expect.objectContaining({
          items: [{ raw_name: '保留香蕉', amount: 100, unit: 'g' }],
        }),
        expect.any(AbortSignal),
      ),
    );
    expect(deleteFoodLog).not.toHaveBeenCalled();
    expect(screen.queryByText('服务端燕麦')).not.toBeInTheDocument();
    expect(screen.getByText('保留香蕉')).toBeInTheDocument();
  });

  it('keeps the edit dialog open and explains a revision conflict', async () => {
    vi.mocked(loadFoodLogs).mockResolvedValue([log]);
    vi.mocked(updateFoodLog).mockRejectedValue(new ApiError('CONFLICT', 'stale revision', 409));
    const user = userEvent.setup();
    renderPage();

    await waitFor(() => expect(screen.getByRole('button', { name: '编辑服务端燕麦所在记录' })).toBeInTheDocument());
    await user.click(screen.getByRole('button', { name: '编辑服务端燕麦所在记录' }));
    const nameInput = screen.getByRole('textbox', { name: '食物名称' });
    await user.clear(nameInput);
    await user.type(nameInput, '冲突后的名称');
    await user.click(screen.getByRole('button', { name: '保存' }));

    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('饮食记录已被修改，请重新加载后再试。'));
    expect(screen.getByRole('dialog')).toBeInTheDocument();
    expect(screen.getByDisplayValue('冲突后的名称')).toBeInTheDocument();
  });

  it('can retry loading deleted records after a real request failure', async () => {
    vi.mocked(loadFoodLogs).mockResolvedValue([]);
    vi.mocked(loadDeletedFoodLogs)
      .mockRejectedValueOnce(new ApiError('NETWORK_ERROR', 'network', undefined))
      .mockResolvedValue([log]);
    const user = userEvent.setup();
    renderPage();

    await waitFor(() => expect(screen.getByRole('button', { name: '已删除记录' })).toBeInTheDocument());
    await user.click(screen.getByRole('button', { name: '已删除记录' }));
    await waitFor(() => expect(screen.getByText('网络连接失败，请检查网络后重试')).toBeInTheDocument());
    await user.click(screen.getByRole('button', { name: '重试加载' }));

    await waitFor(() => expect(screen.getByRole('button', { name: '恢复服务端燕麦' })).toBeInTheDocument());
    expect(loadDeletedFoodLogs).toHaveBeenCalledTimes(2);
  });

  it('clears stale deleted records when the reload fails', async () => {
    vi.mocked(loadFoodLogs).mockResolvedValue([]);
    vi.mocked(loadDeletedFoodLogs).mockResolvedValueOnce([log]).mockRejectedValueOnce(new Error('回收站服务不可用'));
    const user = userEvent.setup();
    renderPage();

    await waitFor(() => expect(screen.getByRole('button', { name: '已删除记录' })).toBeInTheDocument());
    await user.click(screen.getByRole('button', { name: '已删除记录' }));
    await waitFor(() => expect(screen.getByRole('button', { name: '恢复服务端燕麦' })).toBeInTheDocument());
    await user.click(screen.getByRole('button', { name: '收起已删除' }));
    await user.click(screen.getByRole('button', { name: '已删除记录' }));

    await waitFor(() => expect(screen.getByText('回收站服务不可用')).toBeInTheDocument());
    expect(screen.queryByRole('button', { name: '恢复服务端燕麦' })).not.toBeInTheDocument();
  });

  it('edits the first item without dropping other server items', async () => {
    const updated = {
      ...log,
      revision: 3,
      items: [{ ...log.items[0], raw_name: '编辑后的燕麦' }],
    };
    vi.mocked(loadFoodLogs).mockResolvedValueOnce([log]).mockResolvedValue([updated]);
    vi.mocked(updateFoodLog).mockResolvedValue(updated);
    const user = userEvent.setup();
    renderPage();

    await waitFor(() => expect(screen.getByRole('button', { name: '编辑服务端燕麦所在记录' })).toBeInTheDocument());
    await user.click(screen.getByRole('button', { name: '编辑服务端燕麦所在记录' }));
    const nameInput = screen.getByRole('textbox', { name: '食物名称' });
    await user.clear(nameInput);
    await user.type(nameInput, '编辑后的燕麦');
    await user.click(screen.getByRole('button', { name: '保存' }));

    await waitFor(() =>
      expect(updateFoodLog).toHaveBeenCalledWith(
        '11',
        2,
        expect.objectContaining({
          items: [{ raw_name: '编辑后的燕麦', amount: 100, unit: 'g' }],
        }),
        expect.any(AbortSignal),
      ),
    );
    expect(screen.getByText('编辑后的燕麦')).toBeInTheDocument();
  });

  it('loads and restores deleted records from the real endpoint', async () => {
    vi.mocked(loadFoodLogs).mockResolvedValue([]);
    vi.mocked(loadDeletedFoodLogs).mockResolvedValue([log]);
    vi.mocked(restoreFoodLog).mockResolvedValue({ ...log, deleted: false, revision: 3 });
    const user = userEvent.setup();
    renderPage();

    await waitFor(() => expect(screen.getByRole('button', { name: '已删除记录' })).toBeInTheDocument());
    await user.click(screen.getByRole('button', { name: '已删除记录' }));
    await waitFor(() => expect(screen.getByRole('button', { name: '恢复服务端燕麦' })).toBeInTheDocument());
    await user.click(screen.getByRole('button', { name: '恢复服务端燕麦' }));

    await waitFor(() => expect(restoreFoodLog).toHaveBeenCalledWith('11', 2, expect.any(AbortSignal)));
    expect(screen.queryByRole('button', { name: '恢复服务端燕麦' })).not.toBeInTheDocument();
  });

  it('cancels a pending restore when the page unmounts', async () => {
    vi.mocked(loadFoodLogs).mockResolvedValue([]);
    vi.mocked(loadDeletedFoodLogs).mockResolvedValue([log]);
    const pending = deferred<typeof log>();
    vi.mocked(restoreFoodLog).mockImplementation((_foodLogId, _revision, signal) => {
      expect(signal).toBeInstanceOf(AbortSignal);
      return pending.promise;
    });
    const user = userEvent.setup();
    const { unmount } = renderPage();

    await waitFor(() => expect(screen.getByRole('button', { name: '已删除记录' })).toBeInTheDocument());
    await user.click(screen.getByRole('button', { name: '已删除记录' }));
    await waitFor(() => expect(screen.getByRole('button', { name: '恢复服务端燕麦' })).toBeInTheDocument());
    await user.click(screen.getByRole('button', { name: '恢复服务端燕麦' }));
    await waitFor(() => expect(restoreFoodLog).toHaveBeenCalledTimes(1));

    const signal = vi.mocked(restoreFoodLog).mock.calls[0][2];
    unmount();
    expect(signal?.aborted).toBe(true);
    pending.resolve({ ...log, deleted: false });
    await Promise.resolve();
  });

  it('cancels a pending composite-dish delete when the page unmounts', async () => {
    vi.mocked(loadFoodLogs).mockResolvedValue([]);
    vi.mocked(loadCompositeDishes).mockResolvedValue([compositeDish]);
    const pending = deferred<void>();
    vi.mocked(deleteCompositeDish).mockImplementation((_dishId, _revision, signal) => {
      expect(signal).toBeInstanceOf(AbortSignal);
      return pending.promise;
    });
    const user = userEvent.setup();
    const { unmount } = renderPage();

    await waitFor(() => expect(screen.getByText('鸡肉饭')).toBeInTheDocument());
    await user.click(screen.getByRole('button', { name: /^删除$/ }));
    await user.click(screen.getByRole('button', { name: '确认删除' }));
    await waitFor(() => expect(deleteCompositeDish).toHaveBeenCalledTimes(1));

    const signal = vi.mocked(deleteCompositeDish).mock.calls[0][2];
    unmount();
    expect(signal?.aborted).toBe(true);
    pending.resolve();
    await Promise.resolve();
  });

  it('loads a seven-day range when switching to week view', async () => {
    const todayLog = { ...log, meal_time: new Date().toISOString() };
    vi.mocked(loadFoodLogs).mockResolvedValue([todayLog]);
    const user = userEvent.setup();
    renderPage();

    await waitFor(() => expect(screen.getByText('服务端燕麦')).toBeInTheDocument());
    await user.click(screen.getByRole('tab', { name: '周视图' }));

    await waitFor(() => expect(loadFoodLogs).toHaveBeenCalledTimes(2));
    const [, to] = vi.mocked(loadFoodLogs).mock.calls[1];
    const fromDate = new Date(vi.mocked(loadFoodLogs).mock.calls[1][0]);
    const toDate = new Date(to);
    expect(toDate.getTime() - fromDate.getTime()).toBe(7 * 24 * 60 * 60 * 1000);
  });

  it('cancels the previous date request and ignores its late response', async () => {
    const oldLog = { ...log, items: [{ ...log.items[0], raw_name: '旧日期食物' }] };
    const newLog = { ...log, items: [{ ...log.items[0], raw_name: '新日期食物' }] };
    const first = deferred<(typeof log)[]>();
    const second = deferred<(typeof log)[]>();
    vi.mocked(loadFoodLogs).mockImplementation((_from, _to, signal) => {
      const callCount = vi.mocked(loadFoodLogs).mock.calls.length;
      expect(signal).toBeInstanceOf(AbortSignal);
      return callCount === 1 ? first.promise : second.promise;
    });
    const user = userEvent.setup();
    renderPage();

    await waitFor(() => expect(loadFoodLogs).toHaveBeenCalledTimes(1));
    const firstSignal = vi.mocked(loadFoodLogs).mock.calls[0][2];
    await user.click(screen.getByRole('button', { name: '后一天' }));
    await waitFor(() => expect(loadFoodLogs).toHaveBeenCalledTimes(2));

    expect(firstSignal?.aborted).toBe(true);
    first.resolve([oldLog]);
    second.resolve([newLog]);

    await waitFor(() => expect(screen.getByText('新日期食物')).toBeInTheDocument());
    expect(screen.queryByText('旧日期食物')).not.toBeInTheDocument();
  });

  it('shows an explicit empty state for no server records', async () => {
    vi.mocked(loadFoodLogs).mockResolvedValue([]);
    renderPage();

    await waitFor(() => expect(screen.getByText('今天还没有饮食记录')).toBeInTheDocument());
    expect(screen.queryByText('蓝莓燕麦粥')).not.toBeInTheDocument();
  });

  it('keeps nutrition matching, ambiguity, and invalid states distinct', async () => {
    vi.mocked(loadFoodLogs).mockResolvedValue([
      log,
      {
        ...log,
        food_log_id: '12',
        items: [
          { ...log.items[0], food_log_item_id: '102', raw_name: '候选食物', nutrition_status: 'pending_confirmation' },
        ],
      },
      {
        ...log,
        food_log_id: '13',
        items: [{ ...log.items[0], food_log_item_id: '103', raw_name: '无法识别食物', nutrition_status: 'invalid' }],
      },
    ]);
    renderPage();

    expect(await screen.findByText('候选待确认')).toBeInTheDocument();
    expect(screen.getByText('无法匹配')).toBeInTheDocument();
    expect(screen.getByText('已匹配')).toBeInTheDocument();
  });
});
