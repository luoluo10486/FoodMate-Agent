import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useNavigate } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { Button } from '@/components/ui/button';
import {
  createMealPlan,
  createShoppingList,
  deleteMealPlan,
  loadMealPlan,
  loadMealPlanProgress,
  loadMealPlans,
  loadShoppingList,
  restoreMealPlan,
  saveMealPlan,
  updateMealPlan,
  updateShoppingItemPurchased,
  validateMealPlan,
  type MealPlan,
  type MealPlanProgress,
  type ShoppingList,
} from '../../services/planningService';
import { createSession, sendUserMessage } from '../../services/sessionService';
import { ApiError } from '../../services/apiClient';
import { PlanningPage } from './PlanningPage';

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise;
    reject = rejectPromise;
  });
  return { promise, resolve, reject };
}

function NavigationProbe() {
  const navigate = useNavigate();
  return (
    <div>
      <Button type="button" onClick={() => navigate('/planning?planId=701')}>
        测试切换到计划 701
      </Button>
      <Button type="button" onClick={() => navigate('/planning?planId=702')}>
        测试切换到计划 702
      </Button>
    </div>
  );
}

vi.mock('../../services/planningService', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../services/planningService')>();
  return {
    ...actual,
    createMealPlan: vi.fn(),
    createShoppingList: vi.fn(),
    deleteMealPlan: vi.fn(),
    loadMealPlan: vi.fn(),
    loadMealPlanProgress: vi.fn(),
    loadMealPlans: vi.fn(),
    loadShoppingList: vi.fn(),
    restoreMealPlan: vi.fn(),
    saveMealPlan: vi.fn(),
    updateMealPlan: vi.fn(),
    updateShoppingItemPurchased: vi.fn(),
    validateMealPlan: vi.fn(),
  };
});

vi.mock('../../services/sessionService', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../services/sessionService')>();
  return {
    ...actual,
    createSession: vi.fn(),
    sendUserMessage: vi.fn(),
  };
});

const plan = {
  meal_plan_id: '701',
  session_id: null,
  plan_name: '服务端增肌计划',
  people: 2,
  days: 1,
  budget: 180,
  constraints: {
    people: 2,
    calorie_target: 2400,
    protein_target: 150,
    allergens: [],
    dislikes: ['猪肉'],
  },
  days_plan: [
    {
      breakfast: { name: '服务端燕麦碗', calories_kcal: 420, ingredients: [] },
      lunch: { name: '服务端鸡肉藜麦', ingredients: [] },
      dinner: { ingredients: [{ name: '服务端豆腐' }] },
    },
  ],
  validation: { valid: true, errors: [], warnings: [] },
  status: 'saved',
  revision: 3,
  deleted: false,
  created_at: '2026-08-20T12:00:00Z',
  updated_at: '2026-08-22T12:00:00Z',
};

function renderPage(initialEntry = '/planning', withNavigationProbe = false) {
  return render(
    <MemoryRouter initialEntries={[initialEntry]}>
      <Routes>
        <Route
          path="/planning"
          element={
            <>
              <PlanningPage />
              {withNavigationProbe ? <NavigationProbe /> : null}
            </>
          }
        />
        <Route path="/chat/:sessionId" element={<div data-testid="chat-route">chat</div>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('PlanningPage real mode', () => {
  beforeEach(() => {
    vi.stubEnv('VITE_AGENT_MODE', 'real');
    localStorage.setItem(
      'foodmate_auth_user',
      JSON.stringify({ id: '7', username: 'tester', displayName: 'Tester', role: 'user', status: 'active' }),
    );
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(
          JSON.stringify({
            success: true,
            data: { user_id: 7, username: 'tester', email: 'tester@example.com', role: 'user' },
          }),
          { status: 200 },
        ),
      ),
    );
    vi.mocked(loadMealPlans).mockResolvedValue([plan]);
    vi.mocked(loadMealPlan).mockResolvedValue(plan);
    vi.mocked(loadMealPlanProgress).mockResolvedValue({
      meal_plan_id: '701',
      executable_meal_count: 3,
      completed_meal_count: 1,
      completion_ratio: 1 / 3,
      meal_slots: [],
    });
    vi.mocked(loadShoppingList).mockResolvedValue({
      shopping_list_id: '901',
      meal_plan_id: '701',
      items: [{ name: '服务端鸡胸肉', amount: 600, unit: 'g' }],
      status: 'generated',
      created_at: '2026-08-22T12:00:00Z',
      updated_at: '2026-08-22T12:00:00Z',
    });
    vi.mocked(createMealPlan).mockResolvedValue({ ...plan, meal_plan_id: '705', status: 'draft' });
    vi.mocked(updateMealPlan).mockResolvedValue({ ...plan, plan_name: '编辑后的计划', revision: 4 });
    vi.mocked(validateMealPlan).mockResolvedValue({ ...plan, status: 'validated', revision: 4 });
    vi.mocked(saveMealPlan).mockResolvedValue({ ...plan, status: 'saved', revision: 4 });
    vi.mocked(deleteMealPlan).mockResolvedValue(undefined);
    vi.mocked(restoreMealPlan).mockResolvedValue({ ...plan, deleted: false, revision: 4 });
    vi.mocked(updateShoppingItemPurchased).mockResolvedValue({
      shopping_list_id: '901',
      meal_plan_id: '701',
      items: [{ shopping_list_item_id: 'item-1', name: '服务端鸡胸肉', amount: 600, unit: 'g', purchased: true }],
      status: 'generated',
      created_at: '2026-08-22T12:00:00Z',
      updated_at: '2026-08-22T12:00:00Z',
    });
    vi.mocked(createShoppingList).mockResolvedValue({
      shopping_list_id: '902',
      meal_plan_id: '701',
      items: [{ shopping_list_item_id: 'item-1', name: '服务端三文鱼', amount: 450, unit: 'g', purchased: false }],
      status: 'generated',
      created_at: '2026-08-22T12:00:00Z',
      updated_at: '2026-08-22T12:00:00Z',
    });
  });

  afterEach(() => {
    vi.unstubAllEnvs();
    vi.unstubAllGlobals();
    localStorage.clear();
    vi.clearAllMocks();
  });

  it('renders server plans and uses server meal content after opening a plan', async () => {
    const user = userEvent.setup();
    renderPage('/planning?state=list');

    expect(await screen.findByRole('heading', { name: '服务端增肌计划' })).toBeInTheDocument();
    expect(screen.queryByText('夏日减脂轻食计划')).not.toBeInTheDocument();
    expect(screen.getByText(/每日目标: 2,400 kcal/)).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: '进入计划' }));

    await waitFor(() => expect(screen.getByRole('heading', { name: '服务端增肌计划' })).toBeInTheDocument());
    expect(screen.getByText('服务端燕麦碗')).toBeInTheDocument();
    expect(screen.getByText('服务端鸡肉藜麦')).toBeInTheDocument();
    expect(screen.getByText('服务端豆腐')).toBeInTheDocument();
    expect(await screen.findByRole('checkbox', { name: '服务端鸡胸肉 (600g)' })).toBeInTheDocument();
    expect(screen.queryByText('燕麦莓果碗')).not.toBeInTheDocument();
  });

  it('submits planning constraints to an Agent session instead of directly saving a plan', async () => {
    const user = userEvent.setup();
    vi.mocked(createSession).mockResolvedValue({
      session_id: 'session-702',
      user_id: '7',
      title: '我的本地餐食计划',
      mode: 'chat',
      status: 'active',
    });
    vi.mocked(sendUserMessage).mockResolvedValue({
      message_id: 'message-702',
      session_id: 'session-702',
      role: 'user',
      content: '餐食计划约束',
      sequence_no: 1,
      created_at: '2026-08-22T12:00:00Z',
    });
    renderPage('/planning?state=list');

    await user.click(await screen.findByRole('button', { name: '+ 新建膳食计划' }));
    await user.click(screen.getByRole('button', { name: '下一步: 膳食约束' }));
    await user.click(screen.getByRole('button', { name: '下一步: 确认并生成' }));
    await user.click(screen.getByRole('button', { name: '提交给 Agent 生成' }));

    await waitFor(() => expect(createSession).toHaveBeenCalledWith('我的本地餐食计划'));
    expect(sendUserMessage).toHaveBeenCalledWith('session-702', expect.any(String));
    const prompt = vi.mocked(sendUserMessage).mock.calls[0][1];
    expect(prompt).toContain('计划名称：我的本地餐食计划');
    expect(prompt).toContain('规划日期：2026-08-24 至 2026-08-30（共 7 天）');
    expect(prompt).toContain('用餐人数：1 人');
    expect(prompt).toContain('每日能量目标：2200 kcal');
    expect(prompt).toContain('每日蛋白质目标：130 g');
    expect(prompt).toContain('每日预算：120 元');
    expect(prompt).toContain('等待用户确认后才能调用 meal_plan.save_plan');
    expect(await screen.findByTestId('chat-route')).toBeInTheDocument();
  });

  it('persists a real shopping item toggle through the backend', async () => {
    vi.mocked(loadShoppingList).mockResolvedValue({
      shopping_list_id: '901',
      meal_plan_id: '701',
      items: [{ shopping_list_item_id: 'item-1', name: '服务端鸡胸肉', amount: 600, unit: 'g', purchased: false }],
      status: 'generated',
      created_at: '2026-08-22T12:00:00Z',
      updated_at: '2026-08-22T12:00:00Z',
    });
    const user = userEvent.setup();
    renderPage('/planning?planId=701');

    const checkbox = await screen.findByRole('checkbox', { name: '服务端鸡胸肉 (600g)' });
    await user.click(checkbox);

    await waitFor(() =>
      expect(updateShoppingItemPurchased).toHaveBeenCalledWith('701', 'item-1', true, expect.any(AbortSignal)),
    );
    expect(await screen.findByRole('checkbox', { name: '服务端鸡胸肉 (600g)' })).toBeChecked();
  });

  it('lets an empty real account enter the create wizard', async () => {
    const user = userEvent.setup();
    vi.mocked(loadMealPlans).mockResolvedValue([]);
    renderPage('/planning');

    expect(await screen.findByRole('heading', { name: '暂无周餐食规划' })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '创建首个规划方案' }));

    expect(screen.getByRole('heading', { name: '步骤 1: 设置基本目标' })).toBeInTheDocument();
  });

  it('retries a failed plan list request without showing stale plan data', async () => {
    const user = userEvent.setup();
    vi.mocked(loadMealPlans).mockRejectedValueOnce(new Error('列表暂不可用')).mockResolvedValueOnce([plan]);
    renderPage('/planning');

    expect(await screen.findByRole('heading', { name: '规划方案加载失败' })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: '服务端增肌计划' })).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: '重新加载' }));

    expect(await screen.findByRole('heading', { name: '服务端增肌计划' })).toBeInTheDocument();
    expect(loadMealPlans).toHaveBeenCalledTimes(2);
  });

  it('does not fall back to another plan when the selected plan detail fails', async () => {
    const otherPlan = { ...plan, meal_plan_id: '702', plan_name: '不应显示的其它计划' };
    vi.mocked(loadMealPlans).mockResolvedValue([otherPlan]);
    vi.mocked(loadMealPlan).mockRejectedValue(new ApiError('NOT_FOUND', '计划不存在', 404));
    renderPage('/planning?planId=701');

    expect(await screen.findByRole('heading', { name: '餐食计划详情加载失败' })).toBeInTheDocument();
    expect(screen.getByText('计划不存在')).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: '不应显示的其它计划' })).not.toBeInTheDocument();
  });

  it('clears the previous shopping list while a manual refresh fails', async () => {
    const user = userEvent.setup();
    vi.mocked(createShoppingList).mockRejectedValue(new Error('购物清单服务不可用'));
    renderPage('/planning?planId=701');

    expect(await screen.findByRole('checkbox', { name: '服务端鸡胸肉 (600g)' })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '刷新清单' }));

    expect(await screen.findByText('购物清单服务不可用')).toBeInTheDocument();
    expect(screen.queryByRole('checkbox', { name: '服务端鸡胸肉 (600g)' })).not.toBeInTheDocument();
  });

  it('keeps validated and saved plans in separate real status views', async () => {
    vi.mocked(loadMealPlans).mockResolvedValue([
      plan,
      { ...plan, meal_plan_id: '703', plan_name: '待发布计划', status: 'validated' },
      { ...plan, meal_plan_id: '704', plan_name: '历史计划', deleted: true },
    ]);
    renderPage('/planning?state=list');

    expect(await screen.findByRole('heading', { name: '服务端增肌计划' })).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: '已保存' })).toBeInTheDocument();
    const savedPlanCard = screen.getByRole('heading', { name: '服务端增肌计划' }).closest('article');
    expect(savedPlanCard).not.toBeNull();
    expect(within(savedPlanCard as HTMLElement).getByText('已保存')).toBeInTheDocument();
    await userEvent.setup().click(screen.getByRole('tab', { name: '已校验' }));
    expect(screen.getByRole('heading', { name: '待发布计划' })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: '服务端增肌计划' })).not.toBeInTheDocument();
  });

  it('saves a new real draft through the meal plan API', async () => {
    const user = userEvent.setup();
    renderPage('/planning?state=list');

    await user.click(await screen.findByRole('button', { name: '+ 新建膳食计划' }));
    await user.click(screen.getByRole('button', { name: '下一步: 膳食约束' }));
    await user.click(screen.getByRole('button', { name: '下一步: 确认并生成' }));
    await user.click(screen.getByRole('button', { name: '保存为草稿' }));

    await waitFor(() => expect(createMealPlan).toHaveBeenCalledTimes(1));
    expect(createMealPlan).toHaveBeenCalledWith(
      expect.objectContaining({
        planName: '我的本地餐食计划',
        people: '1',
        calories: '2200',
        protein: '130',
        budget: '120',
      }),
    );
    expect(updateMealPlan).not.toHaveBeenCalled();
  });

  it('edits a real plan with the authoritative revision and meal table', async () => {
    const user = userEvent.setup();
    renderPage('/planning?state=list');

    await user.click(await screen.findByRole('button', { name: '服务端增肌计划更多操作' }));
    await user.click(screen.getByRole('menuitem', { name: '编辑计划' }));
    await user.click(screen.getByRole('button', { name: '下一步: 膳食约束' }));
    await user.click(screen.getByRole('button', { name: '下一步: 确认并生成' }));
    await user.click(screen.getByRole('button', { name: '保存为草稿' }));

    await waitFor(() => expect(updateMealPlan).toHaveBeenCalledTimes(1));
    expect(updateMealPlan).toHaveBeenCalledWith(
      '701',
      3,
      expect.objectContaining({
        plan_name: '服务端增肌计划',
        days: 1,
        days_plan: plan.days_plan,
      }),
    );
    expect(createMealPlan).not.toHaveBeenCalled();
  });

  it('validates a draft and saves only after the server returns validated status', async () => {
    const draftPlan = { ...plan, meal_plan_id: '706', status: 'draft' };
    const validatedPlan = { ...draftPlan, status: 'validated', revision: 4 };
    vi.mocked(loadMealPlans).mockResolvedValueOnce([draftPlan]).mockResolvedValue([validatedPlan]);
    vi.mocked(loadMealPlan).mockResolvedValue(draftPlan);
    vi.mocked(validateMealPlan).mockResolvedValue(validatedPlan);
    vi.mocked(saveMealPlan).mockResolvedValue({ ...draftPlan, status: 'saved', revision: 5 });
    const user = userEvent.setup();
    renderPage('/planning?planId=706');

    expect(await screen.findByRole('heading', { name: '服务端增肌计划' })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '校验计划' }));
    await waitFor(() => expect(validateMealPlan).toHaveBeenCalledWith('706', 3));
    await user.click(screen.getByRole('button', { name: '保存计划' }));
    await waitFor(() => expect(saveMealPlan).toHaveBeenCalledWith('706', 4));
  });

  it('keeps the delete dialog open after a revision conflict', async () => {
    vi.mocked(deleteMealPlan).mockRejectedValue(new ApiError('VERSION_CONFLICT', '版本已变化', 409));
    const user = userEvent.setup();
    renderPage('/planning?state=list');

    await user.click(await screen.findByRole('button', { name: '服务端增肌计划更多操作' }));
    await user.click(screen.getByRole('menuitem', { name: '删除计划' }));
    await user.click(screen.getByRole('button', { name: '确认删除' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('计划版本已变化，请重新加载后再试。');
    expect(screen.getByRole('dialog')).toBeInTheDocument();
  });

  it('restores a deleted plan and regenerates its shopping list from the server', async () => {
    const deletedPlan = { ...plan, meal_plan_id: '707', deleted: true, status: 'saved' };
    vi.mocked(loadMealPlans).mockResolvedValue([deletedPlan]);
    vi.mocked(restoreMealPlan).mockResolvedValue({ ...deletedPlan, deleted: false, revision: 4 });
    const user = userEvent.setup();
    const deletedRender = renderPage('/planning?state=list');

    await user.click(await screen.findByRole('tab', { name: '已删除' }));
    await user.click(screen.getByRole('button', { name: '服务端增肌计划更多操作' }));
    await user.click(screen.getByRole('menuitem', { name: '恢复计划' }));
    await waitFor(() => expect(restoreMealPlan).toHaveBeenCalledWith('707', 3));

    deletedRender.unmount();
    vi.mocked(loadMealPlans).mockResolvedValue([plan]);
    renderPage('/planning?planId=701');
    expect(await screen.findByRole('button', { name: '刷新清单' })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '刷新清单' }));
    await waitFor(() => expect(createShoppingList).toHaveBeenCalledWith('701', expect.any(AbortSignal)));
  });

  it('aborts the plan list request when the page unmounts', async () => {
    const pending = deferred<MealPlan[]>();
    let requestSignal: AbortSignal | undefined;
    vi.mocked(loadMealPlans).mockImplementation((signal) => {
      requestSignal = signal;
      return pending.promise;
    });
    const view = renderPage('/planning');

    await waitFor(() => expect(loadMealPlans).toHaveBeenCalledTimes(1));
    expect(requestSignal).toBeDefined();
    view.unmount();
    expect(requestSignal?.aborted).toBe(true);
    pending.resolve([plan]);
  });

  it('does not let a previous plan detail response replace the current plan', async () => {
    const plan702: MealPlan = { ...plan, meal_plan_id: '702', plan_name: '服务端耐力计划' };
    const detail701 = deferred<MealPlan>();
    const detail702 = deferred<MealPlan>();
    vi.mocked(loadMealPlans).mockResolvedValue([plan, plan702]);
    vi.mocked(loadMealPlan).mockImplementation((mealPlanId) =>
      mealPlanId === '701' ? detail701.promise : detail702.promise,
    );
    const user = userEvent.setup();
    renderPage('/planning?planId=701', true);

    await waitFor(() => expect(loadMealPlan).toHaveBeenCalledWith('701', expect.any(AbortSignal)));
    await user.click(screen.getByRole('button', { name: '测试切换到计划 702' }));
    await waitFor(() => expect(loadMealPlan).toHaveBeenCalledWith('702', expect.any(AbortSignal)));

    detail702.resolve(plan702);
    expect(await screen.findByRole('heading', { name: '服务端耐力计划' })).toBeInTheDocument();
    detail701.resolve({ ...plan, plan_name: '不应覆盖当前计划' });
    await waitFor(() => expect(screen.getByRole('heading', { name: '服务端耐力计划' })).toBeInTheDocument());
    expect(screen.queryByRole('heading', { name: '不应覆盖当前计划' })).not.toBeInTheDocument();
  });

  it('does not let a previous shopping list response replace the current plan', async () => {
    const plan702: MealPlan = { ...plan, meal_plan_id: '702', plan_name: '服务端耐力计划' };
    const shopping701 = deferred<ShoppingList>();
    const shopping702 = deferred<ShoppingList>();
    vi.mocked(loadMealPlans).mockResolvedValue([plan, plan702]);
    vi.mocked(loadMealPlan).mockImplementation((mealPlanId) => Promise.resolve(mealPlanId === '701' ? plan : plan702));
    vi.mocked(loadShoppingList).mockImplementation((mealPlanId) =>
      mealPlanId === '701' ? shopping701.promise : shopping702.promise,
    );
    const user = userEvent.setup();
    renderPage('/planning?planId=701', true);

    await waitFor(() => expect(loadShoppingList).toHaveBeenCalledWith('701', expect.any(AbortSignal)));
    await user.click(screen.getByRole('button', { name: '测试切换到计划 702' }));
    await waitFor(() => expect(loadShoppingList).toHaveBeenCalledWith('702', expect.any(AbortSignal)));

    shopping702.resolve({
      shopping_list_id: '902',
      meal_plan_id: '702',
      items: [{ shopping_list_item_id: 'item-702', name: '耐力燕麦', purchased: false }],
      status: 'generated',
      created_at: '2026-08-22T12:00:00Z',
      updated_at: '2026-08-22T12:00:00Z',
    });
    expect(await screen.findByRole('checkbox', { name: '耐力燕麦' })).toBeInTheDocument();

    shopping701.resolve({
      shopping_list_id: '901',
      meal_plan_id: '701',
      items: [{ shopping_list_item_id: 'item-701', name: '旧计划食材', purchased: false }],
      status: 'generated',
      created_at: '2026-08-22T12:00:00Z',
      updated_at: '2026-08-22T12:00:00Z',
    });
    await waitFor(() => expect(screen.getByRole('checkbox', { name: '耐力燕麦' })).toBeInTheDocument());
    expect(screen.queryByRole('checkbox', { name: '旧计划食材' })).not.toBeInTheDocument();
  });

  it('does not let a previous progress response replace the current plan', async () => {
    const plan702: MealPlan = { ...plan, meal_plan_id: '702', plan_name: '服务端耐力计划' };
    const progress701 = deferred<MealPlanProgress>();
    const progress702 = deferred<MealPlanProgress>();
    vi.mocked(loadMealPlans).mockResolvedValue([plan, plan702]);
    vi.mocked(loadMealPlan).mockImplementation((mealPlanId) => Promise.resolve(mealPlanId === '701' ? plan : plan702));
    vi.mocked(loadMealPlanProgress).mockImplementation((mealPlanId) =>
      mealPlanId === '701' ? progress701.promise : progress702.promise,
    );
    const user = userEvent.setup();
    renderPage('/planning?planId=701', true);

    await waitFor(() => expect(loadMealPlanProgress).toHaveBeenCalledWith('701', expect.any(AbortSignal)));
    await user.click(screen.getByRole('button', { name: '测试切换到计划 702' }));
    await waitFor(() => expect(loadMealPlanProgress).toHaveBeenCalledWith('702', expect.any(AbortSignal)));

    progress702.resolve({
      meal_plan_id: '702',
      executable_meal_count: 4,
      completed_meal_count: 2,
      completion_ratio: 0.5,
      meal_slots: [],
    });
    expect(await screen.findByText(/已完成 2 \/ 4 餐次/)).toBeInTheDocument();
    progress701.resolve({
      meal_plan_id: '701',
      executable_meal_count: 3,
      completed_meal_count: 1,
      completion_ratio: 1 / 3,
      meal_slots: [],
    });
    await waitFor(() => expect(screen.getByText(/已完成 2 \/ 4 餐次/)).toBeInTheDocument());
    expect(screen.queryByText(/已完成 1 \/ 3 餐次/)).not.toBeInTheDocument();
  });
});
