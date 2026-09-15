import { CircleAlert, Info, Plus, RotateCcw, UtensilsCrossed } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { WorkspaceLayout } from '../../layouts/WorkspaceLayout/WorkspaceLayout';
import { FIXTURE_WORKSPACE_AVATARS } from '../../lib/avatar';
import { isFigmaFixtureState } from '../../lib/figmaFixture';
import type { SessionSummary } from '../../types/session';
import { ApiError, isAbortError } from '../../services/apiClient';
import {
  createMealPlan,
  createShoppingList,
  deleteMealPlan,
  loadMealPlan,
  loadMealPlans,
  loadMealPlanProgress,
  loadShoppingList,
  mealPlanDraftToUpdateRequest,
  restoreMealPlan,
  saveMealPlan,
  updateMealPlan,
  updateShoppingItemPurchased,
  validateMealPlan,
  type MealPlan,
  type MealPlanMealSlot,
  type MealPlanDraft,
  type MealPlanProgress,
  type ShoppingList,
} from '../../services/planningService';
import { createSession, sendUserMessage } from '../../services/sessionService';
import { MealPlanningFlow, type MealPlanningFlowView } from './MealPlanningFlow';
import styles from './PlanningPage.module.css';

type DayKey = string;
type PlanningView = 'default' | 'loading' | 'empty' | 'error' | MealPlanningFlowView;

type Meal = {
  name?: string;
  kcal?: string;
  mealPlanMealId?: string;
  completed?: boolean;
};

type MealRow = {
  label: string;
  mealType?: string;
  meals: Meal[];
};

const mealSlots = [
  { key: 'breakfast', label: '早餐' },
  { key: 'lunch', label: '午餐' },
  { key: 'dinner', label: '晚餐' },
] as const;

const initialMealPlanDraft: MealPlanDraft = {
  planName: '我的本地餐食计划',
  startDate: '2026-08-24',
  endDate: '2026-08-30',
  people: '1',
  calories: '2200',
  protein: '130',
  budget: '120',
  allergens: [],
  dislikes: [],
};

function planDaysBetween(startDate: string, endDate: string) {
  const start = Date.parse(`${startDate}T00:00:00Z`);
  const end = Date.parse(`${endDate}T00:00:00Z`);
  if (!Number.isFinite(start) || !Number.isFinite(end) || end < start) return 1;
  return Math.min(7, Math.max(1, Math.floor((end - start) / 86_400_000) + 1));
}

function promptValue(value: string, fallback: string) {
  const normalized = value.trim();
  return normalized || fallback;
}

function planningErrorMessage(cause: unknown, fallback: string) {
  if (cause instanceof ApiError) {
    if (cause.status === 409 || ['CONFLICT', 'VERSION_CONFLICT', 'REVISION_CONFLICT'].includes(cause.code)) {
      return '计划版本已变化，请重新加载后再试。';
    }
    if (cause.code === 'FORBIDDEN') return '当前账号没有权限操作这份餐食计划。';
    return cause.message || fallback;
  }
  return cause instanceof Error ? cause.message : fallback;
}

function formatDate(value: Date) {
  return value.toISOString().slice(0, 10);
}

function draftFromPlan(plan: MealPlan): MealPlanDraft {
  const start = new Date();
  const end = new Date(start);
  end.setUTCDate(end.getUTCDate() + Math.max(1, plan.days) - 1);
  return {
    planName: plan.plan_name ?? '',
    startDate: formatDate(start),
    endDate: formatDate(end),
    people: String(plan.people),
    calories: plan.constraints.calorie_target == null ? '' : String(plan.constraints.calorie_target),
    protein: plan.constraints.protein_target == null ? '' : String(plan.constraints.protein_target),
    budget: plan.budget == null ? '' : String(plan.budget),
    allergens: [...(plan.constraints.allergens ?? [])],
    dislikes: [...(plan.constraints.dislikes ?? [])],
    daysPlan: plan.days_plan,
  };
}

function buildMealPlanPrompt(draft: MealPlanDraft) {
  const allergens = draft.allergens.length ? draft.allergens.join('、') : '无';
  const dislikes = draft.dislikes.length ? draft.dislikes.join('、') : '无';
  const days = planDaysBetween(draft.startDate, draft.endDate);

  return [
    '请根据下面的结构化约束生成一份餐食计划候选。',
    '',
    `计划名称：${promptValue(draft.planName, '我的餐食计划')}`,
    `规划日期：${promptValue(draft.startDate, '未设置')} 至 ${promptValue(draft.endDate, '未设置')}（共 ${days} 天）`,
    `用餐人数：${promptValue(draft.people, '1')} 人`,
    `每日能量目标：${promptValue(draft.calories, '未设置')} kcal`,
    `每日蛋白质目标：${promptValue(draft.protein, '未设置')} g`,
    `每日预算：${promptValue(draft.budget, '未设置')} 元`,
    `过敏源：${allergens}`,
    `忌口：${dislikes}`,
    '',
    '处理要求：',
    '1. 先生成候选计划，并说明每日餐次、食材、用量和营养估算。',
    '2. 必须先通过 plan_validator 校验目标、人数、日期和饮食约束，再进入写入确认。',
    '3. 等待用户确认后才能调用 meal_plan.save_plan；在确认前不得声称计划已保存。',
    '4. 如果约束无法同时满足，明确指出冲突并给出可选择的调整方案，不要擅自放宽约束。',
  ].join('\n');
}

function objectValue(value: unknown): Record<string, unknown> | undefined {
  return value && typeof value === 'object' && !Array.isArray(value) ? (value as Record<string, unknown>) : undefined;
}

function firstText(value: Record<string, unknown>, keys: string[]) {
  for (const key of keys) {
    const candidate = value[key];
    if (typeof candidate === 'string' && candidate.trim()) return candidate.trim();
  }
  return undefined;
}

function realMeal(value: unknown, slot?: MealPlanMealSlot): Meal {
  const meal = objectValue(value);
  if (!meal) return {};
  const directName = firstText(meal, ['name', 'title', 'dish_name', 'dishName']);
  const ingredients = Array.isArray(meal.ingredients) ? meal.ingredients : [];
  const ingredientNames = ingredients
    .map((ingredient) => objectValue(ingredient))
    .map((ingredient) => (ingredient ? firstText(ingredient, ['name', 'raw_name', 'rawName']) : undefined))
    .filter((name): name is string => Boolean(name));
  const calories = meal.calories_kcal ?? meal.calories ?? meal.kcal;
  const kcal = typeof calories === 'number' || typeof calories === 'string' ? `${calories} kcal` : undefined;
  return {
    name: directName ?? (ingredientNames.length ? ingredientNames.join('、') : undefined),
    kcal,
    mealPlanMealId: slot?.meal_plan_meal_id,
    completed: slot?.completed,
  };
}

function realSchedule(plan: MealPlan) {
  const planDays = Array.isArray(plan.days_plan) ? plan.days_plan : [];
  const scheduleDays = planDays.map((_, index) => ({ key: String(index), label: `第${index + 1}天` }));
  const rows = mealSlots.map<MealRow>(({ key, label }) => ({
    label,
    mealType: key,
    meals: planDays.map((day, index) =>
      realMeal(
        objectValue(day)?.[key],
        (plan.meal_slots ?? []).find((slot) => slot.day_index === index && slot.meal_type === key),
      ),
    ),
  }));
  return { days: scheduleDays, rows };
}

const days: Array<{ key: DayKey; label: string }> = [
  { key: '13', label: '周一 13' },
  { key: '14', label: '周二 14' },
  { key: '15', label: '周三 15' },
  { key: '16', label: '周四 16' },
  { key: '17', label: '周五 17' },
];

const figmaSidebarSessions: SessionSummary[] = [
  { id: 'weekly-adjustment', title: '每周饮食微调', subtitle: '12:45', active: true },
  { id: 'pre-workout-snack', title: '运动前零食建议', subtitle: '12:45', active: false },
  { id: 'allergen-rules', title: '过敏原排除规则', subtitle: '12:45', active: false },
  { id: 'protein-supplement', title: '蛋白质补充方案', subtitle: '12:45', active: false },
  { id: 'bedtime-snack', title: '睡前加餐建议', subtitle: '12:45', active: false },
  { id: 'breakfast-carbs', title: '早餐碳水搭配', subtitle: '12:45', active: false },
  { id: 'dinner-protein', title: '晚餐蛋白质补充', subtitle: '12:45', active: false },
  { id: 'low-carb-diet', title: '低碳水饮食建议', subtitle: '12:45', active: false },
  { id: 'breakfast-smoothie', title: '早餐奶昔配方', subtitle: '12:45', active: false },
];

const mealRows: MealRow[] = [
  {
    label: '早餐',
    meals: [
      { name: '燕麦莓果碗', kcal: '420 kcal' },
      { name: '蛋白酸面包', kcal: '420 kcal' },
      { name: '牛油果奶昔', kcal: '420 kcal' },
      { name: '燕麦莓果碗', kcal: '420 kcal' },
      {},
    ],
  },
  {
    label: '午餐',
    meals: [
      { name: '三文鱼饭碗', kcal: '420 kcal' },
      { name: '鸡肉藜麦', kcal: '420 kcal' },
      {},
      { name: '三文鱼饭碗', kcal: '420 kcal' },
      { name: '火鸡卷', kcal: '420 kcal' },
    ],
  },
  {
    label: '晚餐',
    meals: [
      {},
      { name: 'Sirloin Sweet Potato', kcal: '420 kcal' },
      { name: 'Baked Cod Broccoli', kcal: '420 kcal' },
      { name: 'Sirloin Sweet Potato', kcal: '420 kcal' },
      { name: 'Tofu Brown Rice', kcal: '420 kcal' },
    ],
  },
];

const constraints = [
  { label: '蛋白质目标（最低110g）', status: 'Pass ✓', tone: 'pass' },
  { label: '每日热量缺口', status: 'Pass ✓', tone: 'pass' },
  { label: '钠上限（<2300mg）', status: 'Pass ✓', tone: 'pass' },
  { label: '过敏原验证', status: 'Review ✗', tone: 'review' },
] as const;

const shoppingGroups = [
  {
    label: '蛋白质类',
    items: ['野生三文鱼 (450g)', 'Chicken Breast (600g)', '火鸡胸肉 (200g)'],
  },
  {
    label: '蔬果类',
    items: ['蓝莓 (2盒)', '新鲜西兰花 (1颗)', '红薯 (3个)'],
  },
];

const isPlanningView = (value: string | null): value is PlanningView =>
  value === 'loading' ||
  value === 'empty' ||
  value === 'error' ||
  value === 'list' ||
  value === 'wizard-step1' ||
  value === 'wizard-step2' ||
  value === 'wizard-step3' ||
  value === 'conflict' ||
  value === 'shopping-list' ||
  value === 'generating';

function PlanLoadingView() {
  return (
    <div className={`${styles.statePage} ${styles.loadingPage}`} aria-label="餐食规划加载中" aria-busy="true">
      <div className={styles.loadingMain} aria-hidden="true">
        <div className={styles.loadingBanner}>
          <div className={styles.loadingSummary}>
            <span className={`${styles.skeleton} ${styles.loadingTitle}`} />
            <div className={styles.loadingMeta}>
              <span className={`${styles.skeleton} ${styles.loadingGoal}`} />
              <span className={`${styles.skeleton} ${styles.loadingDuration}`} />
            </div>
          </div>
          <div className={styles.loadingActions}>
            <span className={`${styles.skeleton} ${styles.loadingAction}`} />
            <span className={`${styles.skeleton} ${styles.loadingAction}`} />
          </div>
        </div>

        <section className={styles.loadingSchedule}>
          <span className={`${styles.skeleton} ${styles.loadingSectionTitle}`} />
          <div className={styles.loadingDays}>
            <span className={styles.loadingDaySpacer} aria-hidden="true" />
            {days.map((day) => (
              <span className={styles.loadingDay} key={day.key}>
                <span className={`${styles.skeleton} ${styles.loadingDaySkeleton}`} />
              </span>
            ))}
          </div>
          {['早餐', '午餐', '晚餐'].map((label) => (
            <div className={styles.loadingMealRow} key={label}>
              <span className={styles.loadingMealLabel}>{label}</span>
              {days.map((day) => (
                <span className={styles.loadingMealCard} key={`${label}-${day.key}`}>
                  <span className={`${styles.skeleton} ${styles.loadingMealName}`} />
                  <span className={`${styles.skeleton} ${styles.loadingKcal}`} />
                </span>
              ))}
            </div>
          ))}
        </section>
      </div>

      <aside className={styles.loadingSidebar} aria-hidden="true">
        <span className={`${styles.skeleton} ${styles.loadingSidebarTitle}`} />
        <div className={styles.loadingChecks}>
          {Array.from({ length: 4 }, (_, index) => (
            <span className={styles.loadingCheck} key={index}>
              <span className={`${styles.skeleton} ${styles.loadingCheckLabel}`} />
              <span className={`${styles.skeleton} ${styles.loadingCheckBadge}`} />
            </span>
          ))}
        </div>
        <div className={styles.loadingDivider} />
        <span className={`${styles.skeleton} ${styles.loadingShoppingTitle}`} />
        <div className={styles.loadingShoppingGroup}>
          <span className={`${styles.skeleton} ${styles.loadingGroupTitle}`} />
          {Array.from({ length: 3 }, (_, index) => (
            <span className={`${styles.skeleton} ${styles.loadingShoppingItem}`} key={index} />
          ))}
        </div>
      </aside>
    </div>
  );
}

type PlanningFeedbackViewProps = {
  kind: 'empty' | 'error';
  onPrimary: () => void;
  onSecondary?: () => void;
  title?: string;
  description?: string;
  errorCode?: string;
};

function PlanningFeedbackView({
  kind,
  onPrimary,
  onSecondary,
  title,
  description,
  errorCode,
}: PlanningFeedbackViewProps) {
  const isError = kind === 'error';
  const feedbackTitle = title ?? (isError ? '规划方案加载失败' : '暂无周餐食规划');
  const feedbackDescription =
    description ??
    (isError
      ? '由于网络连接中断或云端模型服务异常，暂时无法加载您在 FoodMate 上的餐食规划日程。'
      : 'FoodMate 还没有为您生成本周的科学减脂/增肌饮食方案。即刻告诉 AI 助手您的膳食目标，一键生成健康食谱。');

  return (
    <div
      className={`${styles.statePage} ${styles.feedbackPage}`}
      aria-label={isError ? '餐食规划加载失败' : '暂无周餐食规划'}
    >
      <section className={`${styles.feedbackCard} ${isError ? styles.feedbackCardError : styles.feedbackCardEmpty}`}>
        <div className={`${styles.feedbackIcon} ${isError ? styles.feedbackIconError : ''}`} aria-hidden="true">
          {isError ? <CircleAlert /> : <UtensilsCrossed />}
        </div>
        <div className={styles.feedbackCopy}>
          <h1>{feedbackTitle}</h1>
          <p>{feedbackDescription}</p>
        </div>
        {isError ? <span className={styles.errorCode}>{errorCode ?? '错误代码: GATEWAY_TIMEOUT (504)'}</span> : null}
        <div className={styles.feedbackActions}>
          <Button className={styles.feedbackPrimary} onClick={onPrimary}>
            <span className={styles.feedbackPrimaryIcon}>
              {isError ? <RotateCcw aria-hidden="true" /> : <Plus aria-hidden="true" />}
            </span>
            {isError ? '重新加载' : '创建首个规划方案'}
          </Button>
          {onSecondary ? (
            <Button className={styles.feedbackSecondary} variant="outline" onClick={onSecondary}>
              返回工作台
            </Button>
          ) : null}
        </div>
      </section>
      <p className={styles.feedbackHint}>
        {isError ? (
          <>
            仍有疑问？请联系 <span className={styles.feedbackHintSupport}>系统客服支持</span>
          </>
        ) : (
          <>
            <Info aria-hidden="true" />
            <span className={styles.feedbackHintText}>
              提示：可在"饮食记录"中快速上传日常用餐，数据越准确规划越懂你
            </span>
          </>
        )}
      </p>
    </div>
  );
}

function DefaultPlanningView({
  plan,
  onOpenMeal,
  onEditPlan,
  onValidatePlan,
  onSavePlan,
  actionBusy,
  actionError,
  progress,
  progressError,
}: {
  plan?: MealPlan;
  onOpenMeal?: (mealPlanMealId: string, mealType: string) => void;
  onEditPlan?: () => void;
  onValidatePlan?: () => void;
  onSavePlan?: () => void;
  actionBusy?: boolean;
  actionError?: string;
  progress?: MealPlanProgress;
  progressError?: string;
}) {
  const schedule = plan ? realSchedule(plan) : { days, rows: mealRows };
  const [activeDay, setActiveDay] = useState<DayKey>(plan ? (schedule.days[0]?.key ?? '0') : '14');
  const [notice, setNotice] = useState('');
  const firstDayKey = plan ? (schedule.days[0]?.key ?? '0') : '14';

  useEffect(() => {
    // 权威计划餐表变化后，重置当前选中的日期。
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setActiveDay(firstDayKey);
  }, [firstDayKey]);

  const announce = (message: string) => setNotice(message);
  const planName = plan ? plan.plan_name?.trim() || '餐食计划' : '增肌计划 v3';
  const calorieTarget = plan ? plan.constraints.calorie_target : 2400;
  const dayCount = Math.max(schedule.days.length, 1);
  const scheduleColumns = { gridTemplateColumns: `100px repeat(${dayCount}, minmax(0, 1fr))` };
  const dayButtonColumns = { gridTemplateColumns: `repeat(${dayCount}, minmax(0, 1fr))` };

  return (
    <main className={styles.planMain} aria-label="餐食规划" data-figma-node-id="640:974">
      <section className={styles.planBanner} aria-labelledby="plan-title" data-figma-node-id="640:975">
        <div className={styles.planSummary}>
          <h1 id="plan-title">{planName}</h1>
          <div className={styles.planMeta}>
            <span className={styles.goalTag}>
              目标：{calorieTarget == null ? '未设置' : `${calorieTarget.toLocaleString()}千卡`}
            </span>
            <span className={styles.durationTag}>时长：{plan?.days ?? 7}天</span>
          </div>
        </div>
        <div className={styles.bannerActions}>
          {plan && onEditPlan ? (
            <Button className={styles.regenerateButton} variant="ghost" onClick={onEditPlan} disabled={actionBusy}>
              编辑计划
            </Button>
          ) : (
            <Button
              className={styles.regenerateButton}
              variant="ghost"
              onClick={() => announce(plan ? '重新生成需要通过聊天 AgentRun 发起。' : '已重新生成当前 7 天计划。')}
            >
              重新生成
            </Button>
          )}
          {plan && onValidatePlan ? (
            <Button
              className={styles.saveButton}
              variant="outline"
              onClick={onValidatePlan}
              disabled={actionBusy || plan.status === 'saved'}
            >
              {actionBusy ? '校验中...' : plan.status === 'validated' ? '重新校验' : '校验计划'}
            </Button>
          ) : null}
          {plan && onSavePlan ? (
            <Button
              className={styles.saveButton}
              variant="outline"
              onClick={onSavePlan}
              disabled={actionBusy || plan.status !== 'validated'}
            >
              {actionBusy ? '保存中...' : plan.status === 'saved' ? '已保存' : '保存计划'}
            </Button>
          ) : !plan ? (
            <Button className={styles.saveButton} variant="outline" onClick={() => announce('计划已保存。')}>
              保存计划
            </Button>
          ) : null}
        </div>
      </section>

      {progress ? (
        <p className={styles.notice} role="status">
          已完成 {progress.completed_meal_count} / {progress.executable_meal_count} 餐次（
          {Math.round(Number(progress.completion_ratio) * 100)}%）
        </p>
      ) : null}
      {progressError ? (
        <p className={styles.notice} role="alert">
          {progressError}
        </p>
      ) : null}
      {actionError ? (
        <p className={styles.notice} role="alert">
          {actionError}
        </p>
      ) : null}

      <section className={styles.scheduleSection} aria-labelledby="schedule-title" data-figma-node-id="640:988">
        <h2 id="schedule-title">每周日程</h2>
        <div className={styles.scheduleGrid} style={scheduleColumns}>
          <div className={styles.scheduleSpacer} aria-hidden="true" />
          <Tabs
            className={styles.tabsRoot}
            value={activeDay}
            onValueChange={(value) => {
              setActiveDay(value);
              announce(`已查看${schedule.days.find((day) => day.key === value)?.label ?? ''}的计划。`);
            }}
          >
            <TabsList aria-label="每周日程日期" className={styles.dayButtons} style={dayButtonColumns}>
              {schedule.days.map((day) => (
                <TabsTrigger
                  className={`${styles.dayButton} ${activeDay === day.key ? styles.dayButtonActive : ''}`}
                  key={day.key}
                  value={day.key}
                >
                  {day.label}
                </TabsTrigger>
              ))}
            </TabsList>
          </Tabs>

          {schedule.rows.map((row) => (
            <div className={styles.mealRow} key={row.label}>
              <div className={styles.mealLabel}>{row.label}</div>
              {row.meals.map((meal, index) =>
                meal.name ? (
                  <article className={styles.mealCard} key={`${row.label}-${index}`}>
                    <strong>{meal.name}</strong>
                    <span>{meal.kcal}</span>
                    {meal.mealPlanMealId && onOpenMeal ? (
                      <Button
                        type="button"
                        variant="ghost"
                        onClick={() => onOpenMeal(meal.mealPlanMealId as string, row.mealType ?? '')}
                      >
                        {meal.completed ? '已记录，查看饮食记录' : '记录这餐'}
                      </Button>
                    ) : null}
                  </article>
                ) : (
                  <Button
                    className={styles.emptyMeal}
                    variant="ghost"
                    key={`${row.label}-${index}`}
                    type="button"
                    onClick={() => announce(`已打开${row.label}的计划入口。`)}
                  >
                    + 计划
                  </Button>
                ),
              )}
            </div>
          ))}
        </div>
      </section>

      {notice ? (
        <p className={styles.notice} role="status" aria-live="polite">
          {notice}
        </p>
      ) : null}
    </main>
  );
}

function shoppingItemLabel(item: Record<string, unknown>) {
  const name = typeof item.name === 'string' && item.name.trim() ? item.name.trim() : '未命名食材';
  const amount = item.amount == null ? '' : `${item.amount}`;
  const unit = typeof item.unit === 'string' ? item.unit : '';
  const detail = amount || unit ? ` (${amount}${unit})` : '';
  return `${name}${detail}`;
}

function PlanSidebar({
  plan,
  shoppingList,
  shoppingLoading,
  onShoppingListChange,
  onCreateShoppingList,
  creatingShoppingList,
  shoppingError,
}: {
  plan?: MealPlan;
  shoppingList?: ShoppingList;
  shoppingLoading?: boolean;
  onShoppingListChange?: (value: ShoppingList) => void;
  onCreateShoppingList?: () => void;
  creatingShoppingList?: boolean;
  shoppingError?: string;
}) {
  const [updatingItemId, setUpdatingItemId] = useState<string>();
  const [fixturePurchasedItems, setFixturePurchasedItems] = useState<Record<string, boolean>>({});
  const [shoppingMutationError, setShoppingMutationError] = useState<string>();
  const shoppingMutationRequestId = useRef(0);
  const shoppingMutationController = useRef<AbortController>();

  useEffect(() => {
    shoppingMutationRequestId.current += 1;
    shoppingMutationController.current?.abort();
    shoppingMutationController.current = undefined;
    // 计划切换后，上一份购物清单的提交状态不能阻塞新计划。
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setUpdatingItemId(undefined);
    setShoppingMutationError(undefined);
    return () => {
      shoppingMutationRequestId.current += 1;
      shoppingMutationController.current?.abort();
      shoppingMutationController.current = undefined;
    };
  }, [plan?.meal_plan_id]);

  const toggleShoppingItem = (item: ShoppingList['items'][number]) => {
    if (!plan || !item.shopping_list_item_id || !onShoppingListChange || updatingItemId) return;
    const nextPurchased = !item.purchased;
    const mealPlanId = plan.meal_plan_id;
    const shoppingListItemId = item.shopping_list_item_id;
    const requestId = ++shoppingMutationRequestId.current;
    const controller = new AbortController();
    shoppingMutationController.current?.abort();
    shoppingMutationController.current = controller;
    setUpdatingItemId(item.shopping_list_item_id);
    setShoppingMutationError(undefined);
    void updateShoppingItemPurchased(mealPlanId, shoppingListItemId, nextPurchased, controller.signal)
      .then((value) => {
        if (controller.signal.aborted || requestId !== shoppingMutationRequestId.current) return;
        onShoppingListChange(value);
      })
      .catch((cause) => {
        if (controller.signal.aborted || isAbortError(cause) || requestId !== shoppingMutationRequestId.current) return;
        setShoppingMutationError(planningErrorMessage(cause, '购物项更新失败，请重试。'));
      })
      .finally(() => {
        if (requestId === shoppingMutationRequestId.current && shoppingMutationController.current === controller) {
          shoppingMutationController.current = undefined;
          setUpdatingItemId(undefined);
        }
      });
  };

  const toggleFixtureShoppingItem = (itemKey: string, checked: boolean) => {
    // Fixture 购物清单只维护当前页面的勾选状态，不伪造真实购物清单接口结果。
    setFixturePurchasedItems((current) => ({ ...current, [itemKey]: checked }));
  };

  return (
    <aside className={styles.planSidebar} aria-label="计划校验与购物清单" data-figma-node-id="640:1077">
      <section className={styles.constraintSection} aria-labelledby="constraints-title">
        <h2 id="constraints-title">约束校验</h2>
        <div className={styles.constraintList}>
          {(plan
            ? [
                {
                  label: '每日热量目标',
                  status:
                    plan.constraints.calorie_target == null ? '未设置' : `${plan.constraints.calorie_target} kcal`,
                  tone: plan.constraints.calorie_target == null ? 'review' : 'pass',
                },
                {
                  label: '蛋白质目标',
                  status: plan.constraints.protein_target == null ? '未设置' : `${plan.constraints.protein_target} g`,
                  tone: plan.constraints.protein_target == null ? 'review' : 'pass',
                },
                {
                  label: '过敏原',
                  status: plan.constraints.allergens?.length ? `${plan.constraints.allergens.length} 项` : '无',
                  tone: 'pass',
                },
                {
                  label: '忌口',
                  status: plan.constraints.dislikes?.length ? `${plan.constraints.dislikes.length} 项` : '无',
                  tone: 'pass',
                },
              ]
            : constraints
          ).map((item) => (
            <div className={styles.constraintRow} key={item.label}>
              <span>{item.label}</span>
              <strong className={item.tone === 'pass' ? styles.pass : styles.review}>{item.status}</strong>
            </div>
          ))}
        </div>
      </section>

      <div className={styles.divider} aria-hidden="true" />

      <section className={styles.shoppingSection} aria-labelledby="shopping-title">
        <div className={styles.shoppingTitleRow}>
          <h2 id="shopping-title">购物清单预览</h2>
          {plan && onCreateShoppingList ? (
            <Button
              type="button"
              variant="ghost"
              onClick={onCreateShoppingList}
              disabled={Boolean(shoppingLoading) || Boolean(creatingShoppingList)}
            >
              <RotateCcw aria-hidden="true" />
              {creatingShoppingList ? '生成中...' : '刷新清单'}
            </Button>
          ) : null}
        </div>
        {shoppingError || shoppingMutationError ? (
          <p className={styles.notice} role="alert">
            {shoppingError ?? shoppingMutationError}
          </p>
        ) : null}
        {plan ? (
          shoppingLoading ? (
            <p className={styles.notice}>正在读取购物清单...</p>
          ) : shoppingList?.items?.length ? (
            <div className={styles.shoppingItems}>
              {shoppingList.items.map((item, index) => {
                const label = shoppingItemLabel(item as Record<string, unknown>);
                return (
                  <div className={styles.shoppingRow} key={item.shopping_list_item_id ?? `${label}-${index}`}>
                    <Checkbox
                      aria-label={label}
                      checked={Boolean(item.purchased)}
                      disabled={!item.shopping_list_item_id || Boolean(updatingItemId)}
                      className={styles.shoppingCheckbox}
                      onCheckedChange={() => toggleShoppingItem(item)}
                    />
                    <span>{label}</span>
                  </div>
                );
              })}
            </div>
          ) : (
            <p className={styles.notice}>当前计划暂无购物清单。</p>
          )
        ) : (
          shoppingGroups.map((group) => (
            <div className={styles.shoppingGroup} key={group.label}>
              <h3>{group.label}</h3>
              <div className={styles.shoppingItems}>
                {group.items.map((item) => (
                  <div className={styles.shoppingRow} key={`${group.label}-${item}`}>
                    <Checkbox
                      aria-label={item}
                      checked={fixturePurchasedItems[`${group.label}-${item}`] ?? false}
                      className={styles.shoppingCheckbox}
                      onCheckedChange={(checked) =>
                        toggleFixtureShoppingItem(`${group.label}-${item}`, checked === true)
                      }
                    />
                    <span>{item}</span>
                  </div>
                ))}
              </div>
            </div>
          ))
        )}
      </section>
    </aside>
  );
}

export function PlanningPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const requestedView = searchParams.get('state');
  const view: PlanningView = isPlanningView(requestedView) ? requestedView : 'default';
  const isRealMode = import.meta.env.VITE_AGENT_MODE === 'real';
  const [realPlans, setRealPlans] = useState<MealPlan[]>([]);
  const [realLoading, setRealLoading] = useState(isRealMode);
  const [realError, setRealError] = useState<string>();
  const [realPlanDetailLoading, setRealPlanDetailLoading] = useState(false);
  const [realPlanDetailError, setRealPlanDetailError] = useState<string>();
  const [loadedPlanDetailId, setLoadedPlanDetailId] = useState<string>();
  const [realShoppingList, setRealShoppingList] = useState<ShoppingList>();
  const [realShoppingLoading, setRealShoppingLoading] = useState(false);
  const [realShoppingError, setRealShoppingError] = useState<string>();
  const [creatingShoppingList, setCreatingShoppingList] = useState(false);
  const [realProgress, setRealProgress] = useState<MealPlanProgress>();
  const [realProgressError, setRealProgressError] = useState<string>();
  const [realDraft, setRealDraft] = useState<MealPlanDraft>(initialMealPlanDraft);
  const [creatingPlan, setCreatingPlan] = useState(false);
  const [savingDraft, setSavingDraft] = useState(false);
  const [createPlanError, setCreatePlanError] = useState<string>();
  const [editingPlanId, setEditingPlanId] = useState<string>();
  const [planActionId, setPlanActionId] = useState<string>();
  const [planActionError, setPlanActionError] = useState<string>();
  const [planReloadNonce, setPlanReloadNonce] = useState(0);
  const [planDetailReloadNonce, setPlanDetailReloadNonce] = useState(0);
  const mountedRef = useRef(false);
  const planListRequestId = useRef(0);
  const planDetailRequestId = useRef(0);
  const shoppingListRequestId = useRef(0);
  const progressRequestId = useRef(0);
  const shoppingActionRequestId = useRef(0);
  const shoppingActionController = useRef<AbortController>();
  const planActionRequestId = useRef(0);
  const planActionController = useRef<AbortController>();
  const submitPlanRequestId = useRef(0);
  const submitPlanController = useRef<AbortController>();

  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
      planListRequestId.current += 1;
      planDetailRequestId.current += 1;
      shoppingListRequestId.current += 1;
      progressRequestId.current += 1;
      shoppingActionRequestId.current += 1;
      shoppingActionController.current?.abort();
      shoppingActionController.current = undefined;
      planActionRequestId.current += 1;
      planActionController.current?.abort();
      planActionController.current = undefined;
      submitPlanRequestId.current += 1;
      submitPlanController.current?.abort();
      submitPlanController.current = undefined;
    };
  }, []);

  useEffect(() => {
    if (!isRealMode) return;
    const requestId = ++planListRequestId.current;
    const controller = new AbortController();
    // 列表请求拥有完整生命周期，重试时先清理旧错误，避免旧状态覆盖新结果。
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setRealLoading(true);
    setRealError(undefined);
    loadMealPlans(controller.signal)
      .then((value) => {
        if (controller.signal.aborted || requestId !== planListRequestId.current) return;
        setRealPlans(value);
      })
      .catch((error: unknown) => {
        if (controller.signal.aborted || isAbortError(error) || requestId !== planListRequestId.current) return;
        // 列表请求失败后清空旧事实，避免用户继续操作过期的餐食计划。
        setRealPlans([]);
        setRealShoppingList(undefined);
        setRealProgress(undefined);
        setRealError(planningErrorMessage(error, '餐食计划加载失败，请重试。'));
      })
      .finally(() => {
        if (!controller.signal.aborted && requestId === planListRequestId.current) setRealLoading(false);
      });
    return () => {
      planListRequestId.current += 1;
      controller.abort();
    };
  }, [isRealMode, planReloadNonce]);

  const selectedPlanId = searchParams.get('planId');
  const selectedPlan = selectedPlanId
    ? realPlans.find((plan) => plan.meal_plan_id === selectedPlanId)
    : (realPlans.find((plan) => !plan.deleted) ?? realPlans[0]);
  const activePlanId = selectedPlan?.meal_plan_id;

  useEffect(() => {
    // 路由或默认计划变化时，旧的写操作结果不得回写到当前计划。
    planActionRequestId.current += 1;
    planActionController.current?.abort();
    planActionController.current = undefined;
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setPlanActionId(undefined);
    setPlanActionError(undefined);
    setSavingDraft(false);
  }, [activePlanId, selectedPlanId, view]);

  useEffect(() => {
    const requestId = ++planDetailRequestId.current;
    if (!isRealMode || !selectedPlanId) {
      // 没有指定详情路由时，不保留上一条计划详情的加载状态。
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setRealPlanDetailLoading(false);
      setRealPlanDetailError(undefined);
      setLoadedPlanDetailId(undefined);
      return;
    }
    const controller = new AbortController();
    // 详情请求单独管理生命周期，不能把列表中的旧计划当作最新详情。
    setRealPlanDetailLoading(true);
    setRealPlanDetailError(undefined);
    setLoadedPlanDetailId(undefined);
    setPlanActionError(undefined);
    loadMealPlan(selectedPlanId, controller.signal)
      .then((value) => {
        if (controller.signal.aborted || requestId !== planDetailRequestId.current) return;
        setRealPlans((current) => [value, ...current.filter((plan) => plan.meal_plan_id !== value.meal_plan_id)]);
        setLoadedPlanDetailId(selectedPlanId);
      })
      .catch((error: unknown) => {
        if (controller.signal.aborted || isAbortError(error) || requestId !== planDetailRequestId.current) return;
        setRealPlanDetailError(planningErrorMessage(error, '餐食计划详情加载失败，请重试。'));
      })
      .finally(() => {
        if (!controller.signal.aborted && requestId === planDetailRequestId.current) setRealPlanDetailLoading(false);
      });
    return () => {
      planDetailRequestId.current += 1;
      controller.abort();
    };
  }, [isRealMode, selectedPlanId, planDetailReloadNonce]);

  useEffect(() => {
    const requestId = ++shoppingListRequestId.current;
    const mealPlanId = activePlanId;
    const canLoadShoppingList =
      isRealMode && Boolean(mealPlanId) && !selectedPlan?.deleted && selectedPlan?.status === 'saved';
    if (!canLoadShoppingList || !mealPlanId) {
      // 当前计划不再支持购物清单时，清理上一个计划残留的清单和错误。
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setRealShoppingList(undefined);
      setRealShoppingLoading(false);
      setRealShoppingError(undefined);
      return;
    }
    const controller = new AbortController();
    setRealShoppingList(undefined);
    setRealShoppingLoading(true);
    setRealShoppingError(undefined);
    loadShoppingList(mealPlanId, controller.signal)
      .then((value) => {
        if (controller.signal.aborted || requestId !== shoppingListRequestId.current) return;
        setRealShoppingList(value);
      })
      .catch((error: unknown) => {
        if (controller.signal.aborted || isAbortError(error) || requestId !== shoppingListRequestId.current) return;
        setRealShoppingList(undefined);
        setRealShoppingError(planningErrorMessage(error, '购物清单加载失败，请重试。'));
      })
      .finally(() => {
        if (!controller.signal.aborted && requestId === shoppingListRequestId.current) setRealShoppingLoading(false);
      });
    return () => {
      shoppingListRequestId.current += 1;
      controller.abort();
    };
  }, [isRealMode, activePlanId, selectedPlan?.deleted, selectedPlan?.status, planReloadNonce]);

  useEffect(() => {
    const requestId = ++progressRequestId.current;
    const mealPlanId = activePlanId;
    const canLoadProgress =
      isRealMode && Boolean(mealPlanId) && !selectedPlan?.deleted && selectedPlan?.status === 'saved';
    if (!canLoadProgress || !mealPlanId) {
      // 只有已保存计划存在可计算的真实完成进度。
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setRealProgress(undefined);
      setRealProgressError(undefined);
      return;
    }
    const controller = new AbortController();
    setRealProgress(undefined);
    setRealProgressError(undefined);
    loadMealPlanProgress(mealPlanId, controller.signal)
      .then((value) => {
        if (controller.signal.aborted || requestId !== progressRequestId.current) return;
        setRealProgress(value);
      })
      .catch((error: unknown) => {
        if (controller.signal.aborted || isAbortError(error) || requestId !== progressRequestId.current) return;
        setRealProgress(undefined);
        setRealProgressError(planningErrorMessage(error, '计划进度加载失败，请重试。'));
      });
    return () => {
      progressRequestId.current += 1;
      controller.abort();
    };
  }, [isRealMode, activePlanId, selectedPlan?.deleted, selectedPlan?.status, planReloadNonce]);

  useEffect(() => {
    // 计划切换或组件卸载时，手动生成购物清单的结果不能写回新计划。
    shoppingActionRequestId.current += 1;
    shoppingActionController.current?.abort();
    shoppingActionController.current = undefined;
    // 计划切换后，上一份清单的生成状态不能阻塞当前计划。
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setCreatingShoppingList(false);
    return () => {
      shoppingActionRequestId.current += 1;
      shoppingActionController.current?.abort();
      shoppingActionController.current = undefined;
    };
  }, [activePlanId, selectedPlan?.deleted, selectedPlan?.status]);

  const isFigmaFixture = !isRealMode && (isFigmaFixtureState(requestedView) || view !== 'default');
  // 所有 Figma fixture 状态页都复用完整工作区侧栏，保证状态切换不改变壳层结构。

  const navigatePlanningView = (nextView: MealPlanningFlowView | 'default') => {
    navigate(nextView === 'default' ? '/planning' : `/planning?state=${nextView}`);
  };

  const openRealPlan = (mealPlanId: string) => navigate(`/planning?planId=${encodeURIComponent(mealPlanId)}`);

  const updateRealDraft = (patch: Partial<MealPlanDraft>) => {
    setCreatePlanError(undefined);
    setRealDraft((current) => ({ ...current, ...patch }));
  };

  const replacePlanInState = (nextPlan: MealPlan) => {
    setRealPlans((current) => {
      const exists = current.some((plan) => plan.meal_plan_id === nextPlan.meal_plan_id);
      return exists
        ? current.map((plan) => (plan.meal_plan_id === nextPlan.meal_plan_id ? nextPlan : plan))
        : [nextPlan, ...current];
    });
    return nextPlan;
  };

  const runPlanAction = async <T,>(
    mealPlanId: string,
    action: (signal: AbortSignal) => Promise<T>,
    fallback: string,
  ): Promise<{ value: T; requestId: number }> => {
    if (planActionId || planActionController.current) throw new Error('当前已有餐食计划操作进行中');
    const requestId = ++planActionRequestId.current;
    const controller = new AbortController();
    planActionController.current = controller;
    setPlanActionId(mealPlanId);
    setPlanActionError(undefined);
    try {
      return { value: await action(controller.signal), requestId };
    } catch (error: unknown) {
      if (mountedRef.current && requestId === planActionRequestId.current && !isAbortError(error))
        setPlanActionError(planningErrorMessage(error, fallback));
      throw error;
    } finally {
      if (mountedRef.current && requestId === planActionRequestId.current) setPlanActionId(undefined);
      if (planActionController.current === controller) planActionController.current = undefined;
    }
  };

  const startEditingPlan = (plan: MealPlan) => {
    if (plan.deleted) return;
    setEditingPlanId(plan.meal_plan_id);
    setRealDraft(draftFromPlan(plan));
    setCreatePlanError(undefined);
    setPlanActionError(undefined);
    navigate(`/planning?state=wizard-step1&planId=${encodeURIComponent(plan.meal_plan_id)}`);
  };

  const saveDraft = async () => {
    if (savingDraft || planActionId || planActionController.current) return;
    const requestId = ++planActionRequestId.current;
    const editingPlan = editingPlanId
      ? realPlans.find((plan) => plan.meal_plan_id === editingPlanId && !plan.deleted)
      : undefined;
    if (editingPlanId && !editingPlan) {
      setCreatePlanError('编辑的餐食计划已不存在，请重新打开计划列表。');
      return;
    }
    const controller = new AbortController();
    planActionController.current = controller;
    setSavingDraft(true);
    setCreatePlanError(undefined);
    try {
      const savedPlan = editingPlan
        ? await updateMealPlan(
            editingPlan.meal_plan_id,
            editingPlan.revision,
            mealPlanDraftToUpdateRequest(realDraft),
            controller.signal,
          )
        : await createMealPlan(realDraft, controller.signal);
      if (!mountedRef.current || requestId !== planActionRequestId.current) return;
      replacePlanInState(savedPlan);
      setEditingPlanId(undefined);
      setPlanReloadNonce((value) => value + 1);
      navigate(`/planning?planId=${encodeURIComponent(savedPlan.meal_plan_id)}`);
    } catch (error: unknown) {
      if (mountedRef.current && requestId === planActionRequestId.current && !isAbortError(error))
        setCreatePlanError(planningErrorMessage(error, '餐食计划保存失败，请重试。'));
    } finally {
      if (mountedRef.current && requestId === planActionRequestId.current) setSavingDraft(false);
      if (planActionController.current === controller) planActionController.current = undefined;
    }
  };

  const validateSelectedPlan = async () => {
    if (!selectedPlan || selectedPlan.deleted) return;
    try {
      const result = await runPlanAction(
        selectedPlan.meal_plan_id,
        (signal) => validateMealPlan(selectedPlan.meal_plan_id, selectedPlan.revision, signal),
        '计划校验失败，请重试。',
      );
      if (!mountedRef.current || result.requestId !== planActionRequestId.current) return;
      replacePlanInState(result.value);
      setPlanReloadNonce((value) => value + 1);
    } catch {
      // 错误已由 runPlanAction 转换并展示在当前计划操作区域。
    }
  };

  const saveSelectedPlan = async () => {
    if (!selectedPlan || selectedPlan.deleted || selectedPlan.status !== 'validated') return;
    try {
      const result = await runPlanAction(
        selectedPlan.meal_plan_id,
        (signal) => saveMealPlan(selectedPlan.meal_plan_id, selectedPlan.revision, signal),
        '计划保存失败，请重试。',
      );
      if (!mountedRef.current || result.requestId !== planActionRequestId.current) return;
      replacePlanInState(result.value);
      setPlanReloadNonce((value) => value + 1);
    } catch {
      // 错误已由 runPlanAction 转换并展示在当前计划操作区域。
    }
  };

  const deleteRealPlan = async (plan: MealPlan) => {
    const result = await runPlanAction(
      plan.meal_plan_id,
      (signal) => deleteMealPlan(plan.meal_plan_id, plan.revision, signal),
      '餐食计划删除失败，请重试。',
    );
    if (!mountedRef.current || result.requestId !== planActionRequestId.current) return;
    // 删除接口只返回空响应，删除后的计划状态必须以服务端列表回读为准。
    setPlanReloadNonce((value) => value + 1);
    if (selectedPlanId === plan.meal_plan_id) navigate('/planning?state=list');
  };

  const restoreRealPlan = async (plan: MealPlan) => {
    try {
      const result = await runPlanAction(
        plan.meal_plan_id,
        (signal) => restoreMealPlan(plan.meal_plan_id, plan.revision, signal),
        '餐食计划恢复失败，请重试。',
      );
      if (!mountedRef.current || result.requestId !== planActionRequestId.current) return;
      replacePlanInState(result.value);
      setPlanReloadNonce((value) => value + 1);
    } catch {
      // 错误已由 runPlanAction 转换并展示在计划列表区域。
    }
  };

  const createRealShoppingList = async () => {
    if (!selectedPlan || selectedPlan.deleted || selectedPlan.status !== 'saved' || creatingShoppingList) return;
    const mealPlanId = selectedPlan.meal_plan_id;
    const requestId = ++shoppingActionRequestId.current;
    const controller = new AbortController();
    shoppingActionController.current?.abort();
    shoppingActionController.current = controller;
    setCreatingShoppingList(true);
    setRealShoppingError(undefined);
    // 手动刷新期间不继续展示上一份可能已经过期的清单。
    setRealShoppingList(undefined);
    try {
      const shoppingList = await createShoppingList(mealPlanId, controller.signal);
      if (controller.signal.aborted || requestId !== shoppingActionRequestId.current) return;
      setRealShoppingList(shoppingList);
    } catch (error: unknown) {
      if (controller.signal.aborted || isAbortError(error) || requestId !== shoppingActionRequestId.current) return;
      setRealShoppingError(planningErrorMessage(error, '购物清单生成失败，请重试。'));
    } finally {
      if (requestId === shoppingActionRequestId.current && shoppingActionController.current === controller) {
        shoppingActionController.current = undefined;
        setCreatingShoppingList(false);
      }
    }
  };

  const submitRealPlan = async () => {
    if (creatingPlan) return;
    submitPlanController.current?.abort();
    const requestId = ++submitPlanRequestId.current;
    const controller = new AbortController();
    submitPlanController.current = controller;
    setCreatingPlan(true);
    setCreatePlanError(undefined);
    try {
      const session = await createSession(realDraft.planName.trim() || '餐食计划生成', controller.signal);
      await sendUserMessage(session.session_id, buildMealPlanPrompt(realDraft), controller.signal);
      if (!mountedRef.current || controller.signal.aborted || requestId !== submitPlanRequestId.current) return;
      navigate(`/chat/${encodeURIComponent(session.session_id)}`);
    } catch (error: unknown) {
      if (!isAbortError(error) && mountedRef.current && requestId === submitPlanRequestId.current) {
        setCreatePlanError(error instanceof Error ? error.message : '计划创建失败，请检查参数后重试');
      }
    } finally {
      if (mountedRef.current && requestId === submitPlanRequestId.current) setCreatingPlan(false);
      if (submitPlanController.current === controller) submitPlanController.current = undefined;
    }
  };

  const content = isRealMode ? (
    realLoading ? (
      <PlanLoadingView />
    ) : realError ? (
      <PlanningFeedbackView
        kind="error"
        onPrimary={() => setPlanReloadNonce((value) => value + 1)}
        onSecondary={() => navigate('/')}
      />
    ) : realPlanDetailError ? (
      <PlanningFeedbackView
        kind="error"
        title="餐食计划详情加载失败"
        description={realPlanDetailError}
        errorCode="错误代码: PLAN_DETAIL_LOAD_FAILED"
        onPrimary={() => setPlanDetailReloadNonce((value) => value + 1)}
        onSecondary={() => navigate('/planning?state=list')}
      />
    ) : realPlanDetailLoading || (selectedPlanId != null && loadedPlanDetailId !== selectedPlanId) ? (
      <PlanLoadingView />
    ) : view === 'list' ? (
      <MealPlanningFlow
        view="list"
        onNavigate={navigatePlanningView}
        realPlans={realPlans}
        onOpenPlan={openRealPlan}
        realDraft={realDraft}
        onDraftChange={updateRealDraft}
        onCreatePlan={() => void submitRealPlan()}
        onSaveDraft={() => void saveDraft()}
        creatingPlan={creatingPlan}
        savingDraft={savingDraft}
        createError={createPlanError}
        onEditPlan={startEditingPlan}
        onDeletePlan={deleteRealPlan}
        onRestorePlan={restoreRealPlan}
        actionId={planActionId}
        actionError={planActionError}
      />
    ) : view === 'wizard-step1' ||
      view === 'wizard-step2' ||
      view === 'wizard-step3' ||
      view === 'conflict' ||
      view === 'shopping-list' ||
      view === 'generating' ? (
      <MealPlanningFlow
        view={view}
        onNavigate={navigatePlanningView}
        realPlans={realPlans}
        onOpenPlan={openRealPlan}
        realDraft={realDraft}
        onDraftChange={updateRealDraft}
        onCreatePlan={() => void submitRealPlan()}
        onSaveDraft={() => void saveDraft()}
        creatingPlan={creatingPlan}
        savingDraft={savingDraft}
        createError={createPlanError}
        onEditPlan={startEditingPlan}
        onDeletePlan={deleteRealPlan}
        onRestorePlan={restoreRealPlan}
        actionId={planActionId}
        actionError={planActionError}
      />
    ) : view === 'empty' || realPlans.length === 0 ? (
      <PlanningFeedbackView kind="empty" onPrimary={() => navigate('/planning?state=wizard-step1')} />
    ) : selectedPlan?.deleted ? (
      <MealPlanningFlow
        view="list"
        onNavigate={navigatePlanningView}
        realPlans={realPlans}
        onOpenPlan={openRealPlan}
        realDraft={realDraft}
        onDraftChange={updateRealDraft}
        onCreatePlan={() => void submitRealPlan()}
        onSaveDraft={() => void saveDraft()}
        creatingPlan={creatingPlan}
        savingDraft={savingDraft}
        createError={createPlanError}
        onEditPlan={startEditingPlan}
        onDeletePlan={deleteRealPlan}
        onRestorePlan={restoreRealPlan}
        actionId={planActionId}
        actionError={planActionError}
      />
    ) : (
      <DefaultPlanningView
        plan={selectedPlan}
        onOpenMeal={(mealPlanMealId, mealType) =>
          navigate(
            `/diet-records?mealPlanMealId=${encodeURIComponent(mealPlanMealId)}&mealType=${encodeURIComponent(mealType)}`,
          )
        }
        onEditPlan={selectedPlan ? () => startEditingPlan(selectedPlan) : undefined}
        onValidatePlan={() => void validateSelectedPlan()}
        onSavePlan={() => void saveSelectedPlan()}
        actionBusy={Boolean(planActionId)}
        actionError={planActionError}
        progress={realProgress}
        progressError={realProgressError}
      />
    )
  ) : view === 'loading' ? (
    <PlanLoadingView />
  ) : view === 'empty' ? (
    <PlanningFeedbackView kind="empty" onPrimary={() => navigate('/chat?prompt=请为我创建本周餐食规划')} />
  ) : view === 'error' ? (
    <PlanningFeedbackView kind="error" onPrimary={() => navigate('/planning')} onSecondary={() => navigate('/')} />
  ) : view === 'list' ||
    view === 'wizard-step1' ||
    view === 'wizard-step2' ||
    view === 'wizard-step3' ||
    view === 'conflict' ||
    view === 'shopping-list' ||
    view === 'generating' ? (
    <MealPlanningFlow view={view} onNavigate={navigatePlanningView} />
  ) : (
    <DefaultPlanningView />
  );

  return (
    <WorkspaceLayout
      activeModule="planning"
      fixtureVariant={isFigmaFixture ? 'planning' : undefined}
      rightRail={
        view === 'default' && (!isRealMode || (selectedPlan && !selectedPlan.deleted)) ? (
          <PlanSidebar
            plan={isRealMode ? selectedPlan : undefined}
            shoppingList={isRealMode ? realShoppingList : undefined}
            shoppingLoading={isRealMode ? realShoppingLoading : false}
            onShoppingListChange={isRealMode ? setRealShoppingList : undefined}
            onCreateShoppingList={isRealMode ? () => void createRealShoppingList() : undefined}
            creatingShoppingList={isRealMode ? creatingShoppingList : false}
            shoppingError={isRealMode ? realShoppingError : undefined}
          />
        ) : undefined
      }
      rightRailWidth={view === 'default' && (!isRealMode || (selectedPlan && !selectedPlan.deleted)) ? 340 : undefined}
      displayNameOverride={isFigmaFixture ? 'Anddy' : undefined}
      profileIdOverride={isFigmaFixture ? '1234567' : undefined}
      topbarVariant={isFigmaFixture && view === 'list' ? 'planning-list' : undefined}
      sidebarAvatarSrc={isFigmaFixture ? FIXTURE_WORKSPACE_AVATARS.sidebar : undefined}
      topAvatarSrc={isFigmaFixture ? FIXTURE_WORKSPACE_AVATARS.topbar : undefined}
      showKnowledgeTopNav={!isFigmaFixture}
      sidebarFixture={
        isFigmaFixture
          ? {
              sessions: figmaSidebarSessions,
            }
          : undefined
      }
    >
      {content}
    </WorkspaceLayout>
  );
}
