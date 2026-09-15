import { useCallback, useEffect, useMemo, useRef, useState, type CSSProperties } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import {
  AlertTriangle,
  ChevronLeft,
  ChevronRight,
  Pencil,
  Plus,
  RefreshCw,
  RotateCcw,
  Trash2,
  Utensils,
} from 'lucide-react';
import { Button } from '@/components/ui/button';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { FIXTURE_WORKSPACE_AVATARS } from '../../lib/avatar';
import { WorkspaceLayout } from '../../layouts/WorkspaceLayout/WorkspaceLayout';
import { ApiError, isAbortError } from '../../services/apiClient';
import {
  createFoodLog,
  deleteFoodLog,
  loadDeletedFoodLogs,
  loadFoodLogs,
  restoreFoodLog,
  updateFoodLog,
  type FoodLog,
  type FoodLogItem,
} from '../../services/foodLogService';
import {
  createCompositeDish,
  deleteCompositeDish,
  loadCompositeDish,
  loadCompositeDishes,
  updateCompositeDish,
  type CompositeDish,
} from '../../services/compositeDishService';
import { searchNutritionFoods, type NutritionFoodCandidate } from '../../services/nutritionFoodService';
import type { SessionSummary } from '../../types/session';
import styles from './DietRecordsPage.module.css';

type FoodItem = {
  id: string;
  name: string;
  status: 'confirmed' | 'pending' | 'ambiguous' | 'invalid';
  carbs: string;
  protein: string;
  fat: string;
  logId?: string;
  revision?: number;
};

type CompositeDishDraftComponent = {
  nutritionFoodId?: string;
  rawName: string;
  amount: string;
  unit: string;
};

type MealSection = {
  id: 'breakfast' | 'lunch' | 'dinner' | 'snack';
  icon: string;
  title: string;
  time: string;
  items: FoodItem[];
};

const initialDate = new Date(2024, 2, 14);

const initialMeals: MealSection[] = [
  {
    id: 'breakfast',
    icon: '🌅',
    title: 'Breakfast',
    time: '上午 8:30',
    items: [
      {
        id: 'blueberry-oatmeal',
        name: '蓝莓燕麦粥',
        status: 'confirmed',
        carbs: 'C: 45g',
        protein: 'P: 8g',
        fat: 'F: 4g',
      },
    ],
  },
  {
    id: 'lunch',
    icon: '🌞',
    title: 'Lunch',
    time: '下午 1:15',
    items: [
      {
        id: 'salmon-bowl',
        name: '煎三文鱼碗',
        status: 'confirmed',
        carbs: 'C: 55g',
        protein: 'P: 34g',
        fat: 'F: 18g',
      },
      {
        id: 'greek-yogurt',
        name: '希腊酸奶蜂蜜',
        status: 'pending',
        carbs: 'C: 18g',
        protein: 'P: 12g',
        fat: 'F: 2g',
      },
    ],
  },
];

type Metric = {
  label: string;
  value: string;
  unit: string;
  percentage: number;
  tone: 'purple' | 'green' | 'orange' | 'red';
};

const metrics: Metric[] = [
  { label: '能量完成', value: '1,420', unit: '/ 2,000 kcal', percentage: 71, tone: 'purple' },
  { label: '蛋白质目标', value: '98', unit: '/ 120 g', percentage: 81, tone: 'green' },
  { label: '碳水目标', value: '150', unit: '/ 250 g', percentage: 60, tone: 'orange' },
  { label: '脂肪目标', value: '44', unit: '/ 70 g', percentage: 62, tone: 'red' },
] as const;

// 默认 fixture 使用 Figma 导出的进度环，避免浏览器 conic-gradient 光栅化造成视觉偏差。
const figmaMetricAssets: Record<Metric['tone'], string> = {
  purple: '/assets/figma/diet-records/metric-ring-energy.svg',
  green: '/assets/figma/diet-records/metric-ring-protein.svg',
  orange: '/assets/figma/diet-records/metric-ring-carbs.svg',
  red: '/assets/figma/diet-records/metric-ring-fat.svg',
};

const emptyMetrics = metrics.map((metric) => ({ ...metric, value: '0', percentage: 0 }));

const figmaSidebarSessions: SessionSummary[] = [
  { id: 'weekly-adjustment', title: '每周饮食微调', subtitle: '12:45', active: true },
  { id: 'pre-workout-snack', title: '运动前零食建议', subtitle: '12:45', active: false },
  { id: 'allergen-rules', title: '过敏原排除规则', subtitle: '12:45', active: false },
  { id: 'protein-plan', title: '蛋白质补充方案', subtitle: '12:45', active: false },
  { id: 'bedtime-snack', title: '睡前加餐建议', subtitle: '12:45', active: false },
  { id: 'breakfast-carbs', title: '早餐碳水搭配', subtitle: '12:45', active: false },
  { id: 'dinner-protein', title: '晚餐蛋白质补充', subtitle: '12:45', active: false },
  { id: 'low-carb-plan', title: '低碳水饮食建议', subtitle: '12:45', active: false },
];

type RecordsState = 'default' | 'loading' | 'empty' | 'error';

type PendingFoodDeletion = {
  logId: string;
  itemId: string;
  itemName: string;
};

type FoodMutation = 'create' | 'update' | 'delete' | 'restore' | undefined;

type PendingCompositeDishDeletion = CompositeDish;

type MutationRequest = {
  requestId: number;
  controller: AbortController;
};

function isFoodLogConflict(cause: unknown): boolean {
  return (
    cause instanceof ApiError &&
    (cause.status === 409 || ['CONFLICT', 'VERSION_CONFLICT', 'REVISION_CONFLICT'].includes(cause.code))
  );
}

function foodLogErrorMessage(cause: unknown, fallback: string): string {
  if (cause instanceof ApiError) {
    if (isFoodLogConflict(cause)) return '饮食记录已被修改，请重新加载后再试。';
    if (cause.code === 'FORBIDDEN') return '当前账号无权操作这条饮食记录。';
    return cause.message || fallback;
  }
  return cause instanceof Error ? cause.message : fallback;
}

function compositeDishErrorMessage(cause: unknown, fallback: string): string {
  if (cause instanceof ApiError) {
    if (cause.status === 409 || ['CONFLICT', 'VERSION_CONFLICT', 'REVISION_CONFLICT'].includes(cause.code)) {
      return '复合菜已被修改，请重新加载后再试。';
    }
    if (cause.code === 'FORBIDDEN') return '当前账号无权操作这道复合菜。';
    return cause.message || fallback;
  }
  return cause instanceof Error ? cause.message : fallback;
}

function buildFoodLogWriteRequest(log: FoodLog, items: FoodLogItem[]): Parameters<typeof updateFoodLog>[2] {
  return {
    meal_time: log.meal_time,
    meal_type: log.meal_type,
    notes: log.notes ?? undefined,
    meal_plan_meal_id: log.meal_plan_meal_id ?? undefined,
    composite_dish_id: log.composite_dish_id ?? undefined,
    composite_dish_revision: log.composite_dish_revision ?? undefined,
    composite_dish_servings: log.composite_dish_servings == null ? undefined : Number(log.composite_dish_servings),
    items: items.map((item) => ({
      raw_name: item.raw_name,
      amount: asNumber(item.amount),
      unit: item.unit,
      ...(item.nutrition_food_id ? { nutrition_food_id: item.nutrition_food_id } : {}),
    })),
  };
}

function getRecordsState(value: string | null): RecordsState {
  return value === 'loading' || value === 'empty' || value === 'error' ? value : 'default';
}

function formatDateLabel(date: Date, realMode = false) {
  const today = new Date();
  const isInitialDate = realMode
    ? date.getFullYear() === today.getFullYear() &&
      date.getMonth() === today.getMonth() &&
      date.getDate() === today.getDate()
    : date.getTime() === initialDate.getTime();
  return isInitialDate
    ? `今天，${date.getMonth() + 1}月${date.getDate()}日`
    : `${date.getMonth() + 1}月${date.getDate()}日`;
}

function shiftDate(date: Date, amount: number) {
  const next = new Date(date);
  next.setDate(next.getDate() + amount);
  return next;
}

function startOfWeek(date: Date) {
  const start = new Date(date);
  const mondayOffset = (start.getDay() + 6) % 7;
  start.setDate(start.getDate() - mondayOffset);
  start.setHours(0, 0, 0, 0);
  return start;
}

function weekWindow(date: Date) {
  const from = startOfWeek(date);
  const to = shiftDate(from, 7);
  return { from: from.toISOString(), to: to.toISOString() };
}

function sameLocalDate(left: Date, right: Date) {
  return (
    left.getFullYear() === right.getFullYear() &&
    left.getMonth() === right.getMonth() &&
    left.getDate() === right.getDate()
  );
}

function formatWeekLabel(date: Date) {
  const from = startOfWeek(date);
  const to = shiftDate(from, 6);
  return `${from.getMonth() + 1}月${from.getDate()}日 - ${to.getMonth() + 1}月${to.getDate()}日`;
}

type WeekDay = { date: Date; meals: MealSection[] };

function mapWeekLogs(logs: FoodLog[], date: Date): WeekDay[] {
  const from = startOfWeek(date);
  return Array.from({ length: 7 }, (_, index) => {
    const day = shiftDate(from, index);
    return {
      date: day,
      meals: mapFoodLogs(logs.filter((log) => sameLocalDate(new Date(log.meal_time), day))),
    };
  });
}

function ProgressRing({ percentage, tone, assetSrc }: { percentage: number; tone: Metric['tone']; assetSrc?: string }) {
  const style = { '--progress': percentage } as CSSProperties;
  return (
    <div
      className={`${styles.progressRing} ${styles[tone]} ${assetSrc ? styles.progressRingWithAsset : ''}`}
      style={style}
      aria-label={`${percentage}% 完成`}
    >
      {assetSrc ? <img className={styles.progressRingAsset} src={assetSrc} alt="" aria-hidden="true" /> : null}
      <span>{percentage}%</span>
    </div>
  );
}

const realMealMeta: Record<MealSection['id'], { icon: string; title: string }> = {
  breakfast: { icon: '🌅', title: '早餐' },
  lunch: { icon: '🌞', title: '午餐' },
  dinner: { icon: '🌙', title: '晚餐' },
  snack: { icon: '🍎', title: '加餐' },
};

function dayWindow(date: Date) {
  const from = new Date(date);
  from.setHours(0, 0, 0, 0);
  const to = new Date(from);
  to.setDate(to.getDate() + 1);
  return { from: from.toISOString(), to: to.toISOString() };
}

function formatMetricNumber(value: number) {
  return value.toLocaleString('zh-CN', { maximumFractionDigits: 1 });
}

function asNumber(value: number | string | null | undefined) {
  const number = Number(value ?? 0);
  return Number.isFinite(number) ? number : 0;
}

function candidateMetric(value: number | string | null) {
  if (value == null || (typeof value === 'string' && value.trim() === '')) return '未知';
  const number = Number(value);
  return Number.isFinite(number) ? number.toLocaleString('zh-CN', { maximumFractionDigits: 1 }) : '未知';
}

function nutritionDisplayStatus(value: string): FoodItem['status'] {
  if (value === 'matched') return 'confirmed';
  if (value === 'pending_confirmation') return 'ambiguous';
  if (value === 'invalid') return 'invalid';
  return 'pending';
}

function nutritionStatusLabel(status: FoodItem['status'], isFigmaFixture: boolean) {
  if (isFigmaFixture) {
    return {
      confirmed: '已确认',
      pending: '待确认',
      ambiguous: '候选待确认',
      invalid: '无法匹配',
    }[status];
  }

  return {
    confirmed: '已匹配',
    pending: '待估算',
    ambiguous: '候选待确认',
    invalid: '无法匹配',
  }[status];
}

function mapFoodLogs(logs: FoodLog[]): MealSection[] {
  const sections = Object.keys(realMealMeta).map((id) => {
    const mealId = id as MealSection['id'];
    const meta = realMealMeta[mealId];
    const relatedLogs = logs.filter((log) => log.meal_type === mealId);
    return {
      id: mealId,
      icon: meta.icon,
      title: meta.title,
      time: relatedLogs[0]
        ? new Date(relatedLogs[0].meal_time).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
        : '暂无记录',
      items: relatedLogs.flatMap((log) =>
        log.items.map((item) => ({
          id: `${log.food_log_id}-${item.food_log_item_id}`,
          name: item.raw_name,
          status: nutritionDisplayStatus(item.nutrition_status),
          carbs: item.carbs_g == null ? 'C: 待估算' : `C: ${formatMetricNumber(asNumber(item.carbs_g))}g`,
          protein: item.protein_g == null ? 'P: 待估算' : `P: ${formatMetricNumber(asNumber(item.protein_g))}g`,
          fat: item.fat_g == null ? 'F: 待估算' : `F: ${formatMetricNumber(asNumber(item.fat_g))}g`,
          logId: log.food_log_id,
          revision: log.revision,
        })),
      ),
    } satisfies MealSection;
  });
  return sections.filter((section) => section.items.length > 0);
}

function realMetrics(logs: FoodLog[]): Metric[] {
  const items = logs.flatMap((log) => log.items);
  const total = (key: 'calories_kcal' | 'protein_g' | 'carbs_g' | 'fat_g') =>
    items.reduce((sum, item) => sum + asNumber(item[key]), 0);
  return [
    {
      label: '能量合计',
      value: formatMetricNumber(total('calories_kcal')),
      unit: 'kcal · 未配置目标',
      percentage: 0,
      tone: 'purple',
    },
    {
      label: '蛋白质合计',
      value: formatMetricNumber(total('protein_g')),
      unit: 'g · 未配置目标',
      percentage: 0,
      tone: 'green',
    },
    {
      label: '碳水合计',
      value: formatMetricNumber(total('carbs_g')),
      unit: 'g · 当前日期',
      percentage: 0,
      tone: 'orange',
    },
    { label: '脂肪合计', value: formatMetricNumber(total('fat_g')), unit: 'g · 当前日期', percentage: 0, tone: 'red' },
  ];
}

export function DietRecordsPage() {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const recordsState = getRecordsState(searchParams.get('state'));
  const linkedMealPlanMealId = searchParams.get('mealPlanMealId') ?? undefined;
  const linkedMealType = searchParams.get('mealType');
  const isRealMode = import.meta.env.VITE_AGENT_MODE === 'real';
  const isFigmaFixture = !isRealMode && (searchParams.get('state') === 'v2' || recordsState !== 'default');
  const [selectedDate, setSelectedDate] = useState(() => (isRealMode ? new Date() : initialDate));
  const [view, setView] = useState<'day' | 'week'>('day');
  const [meals, setMeals] = useState<MealSection[]>(initialMeals);
  const [realLogs, setRealLogs] = useState<FoodLog[]>([]);
  const [realLoading, setRealLoading] = useState(isRealMode);
  const [realError, setRealError] = useState<string>();
  const [realReloadNonce, setRealReloadNonce] = useState(0);
  const [dialogMealId, setDialogMealId] = useState<MealSection['id']>();
  const [dialogMode, setDialogMode] = useState<'create' | 'edit'>('create');
  const [editingLogId, setEditingLogId] = useState<string>();
  const [dialogDate, setDialogDate] = useState(selectedDate);
  const [foodName, setFoodName] = useState('');
  const [foodAmount, setFoodAmount] = useState('100');
  const [foodUnit, setFoodUnit] = useState('g');
  const [nutritionFoodId, setNutritionFoodId] = useState<string>();
  const [nutritionCandidates, setNutritionCandidates] = useState<NutritionFoodCandidate[]>([]);
  const [nutritionCandidatesLoading, setNutritionCandidatesLoading] = useState(false);
  const [nutritionCandidatesError, setNutritionCandidatesError] = useState<string>();
  const [compositeDishes, setCompositeDishes] = useState<CompositeDish[]>([]);
  const [compositeDishesLoading, setCompositeDishesLoading] = useState(isRealMode);
  const [compositeDishesError, setCompositeDishesError] = useState<string>();
  const [compositeReloadNonce, setCompositeReloadNonce] = useState(0);
  const [selectedCompositeDishId, setSelectedCompositeDishId] = useState<string>();
  const [compositeDishServings, setCompositeDishServings] = useState('1');
  const [dishDialogOpen, setDishDialogOpen] = useState(false);
  const [editingDishId, setEditingDishId] = useState<string>();
  const [dishName, setDishName] = useState('');
  const [dishTotalServings, setDishTotalServings] = useState('2');
  const [dishComponents, setDishComponents] = useState<CompositeDishDraftComponent[]>([
    { rawName: '', amount: '', unit: 'g' },
  ]);
  const [dishCandidateMap, setDishCandidateMap] = useState<Record<number, NutritionFoodCandidate[]>>({});
  const [dishDetailReady, setDishDetailReady] = useState(true);
  const [dishLoading, setDishLoading] = useState(false);
  const [dishSaving, setDishSaving] = useState(false);
  const [dishDeleting, setDishDeleting] = useState(false);
  const [pendingCompositeDishDeletion, setPendingCompositeDishDeletion] = useState<PendingCompositeDishDeletion>();
  const [dishError, setDishError] = useState<string>();
  const [deletedLogs, setDeletedLogs] = useState<FoodLog[]>([]);
  const [deletedLoading, setDeletedLoading] = useState(false);
  const [deletedError, setDeletedError] = useState<string>();
  const [showDeleted, setShowDeleted] = useState(false);
  const [notice, setNotice] = useState('');
  const [foodMutation, setFoodMutation] = useState<FoodMutation>();
  const [pendingFoodDeletion, setPendingFoodDeletion] = useState<PendingFoodDeletion>();
  const realLogsRequestId = useRef(0);
  const compositeListRequestId = useRef(0);
  const deletedRequestId = useRef(0);
  const deletedAbortController = useRef<AbortController>();
  const dishDetailAbortController = useRef<AbortController>();
  const dishRequestId = useRef(0);
  const foodMutationAbortController = useRef<AbortController>();
  const foodMutationRequestId = useRef(0);
  const dishMutationAbortController = useRef<AbortController>();
  const dishMutationRequestId = useRef(0);

  const beginFoodMutation = (mutation: Exclude<FoodMutation, undefined>): MutationRequest => {
    // 新的写操作开始前终止旧请求，避免多个操作同时回写同一份页面状态。
    foodMutationAbortController.current?.abort();
    const requestId = ++foodMutationRequestId.current;
    const controller = new AbortController();
    foodMutationAbortController.current = controller;
    setFoodMutation(mutation);
    return { requestId, controller };
  };

  const isCurrentFoodMutation = ({ requestId, controller }: MutationRequest) =>
    !controller.signal.aborted &&
    requestId === foodMutationRequestId.current &&
    foodMutationAbortController.current === controller;

  const cancelFoodMutation = () => {
    foodMutationRequestId.current += 1;
    foodMutationAbortController.current?.abort();
    foodMutationAbortController.current = undefined;
    setFoodMutation(undefined);
  };

  const beginDishMutation = (): MutationRequest => {
    // 复合菜保存和删除共用一个控制器，确保操作替换时不会留下旧写入。
    dishMutationAbortController.current?.abort();
    const requestId = ++dishMutationRequestId.current;
    const controller = new AbortController();
    dishMutationAbortController.current = controller;
    return { requestId, controller };
  };

  const isCurrentDishMutation = ({ requestId, controller }: MutationRequest) =>
    !controller.signal.aborted &&
    requestId === dishMutationRequestId.current &&
    dishMutationAbortController.current === controller;

  const cancelDishMutation = () => {
    dishMutationRequestId.current += 1;
    dishMutationAbortController.current?.abort();
    dishMutationAbortController.current = undefined;
    setDishSaving(false);
    setDishDeleting(false);
  };

  useEffect(() => {
    return () => {
      // 组件卸载时终止由交互事件发起、但不受读取 effect 管理的请求。
      deletedRequestId.current += 1;
      deletedAbortController.current?.abort();
      dishRequestId.current += 1;
      dishDetailAbortController.current?.abort();
      foodMutationRequestId.current += 1;
      foodMutationAbortController.current?.abort();
      dishMutationRequestId.current += 1;
      dishMutationAbortController.current?.abort();
    };
  }, []);

  useEffect(() => {
    if (!isRealMode) return;
    const requestId = ++realLogsRequestId.current;
    const controller = new AbortController();
    // 每次外部请求都由当前 effect 管理完整生命周期，因此请求开始时重置加载状态。
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setRealLoading(true);
    setRealError(undefined);
    const window = view === 'week' ? weekWindow(selectedDate) : dayWindow(selectedDate);
    loadFoodLogs(window.from, window.to, controller.signal)
      .then((logs) => {
        if (controller.signal.aborted || requestId !== realLogsRequestId.current) return;
        setRealLogs(logs);
        setMeals(mapFoodLogs(logs));
      })
      .catch((cause) => {
        if (controller.signal.aborted || isAbortError(cause) || requestId !== realLogsRequestId.current) return;
        setRealLogs([]);
        setMeals([]);
        setRealError(cause instanceof Error ? cause.message : '饮食记录加载失败');
      })
      .finally(() => {
        if (!controller.signal.aborted && requestId === realLogsRequestId.current) setRealLoading(false);
      });
    return () => {
      controller.abort();
    };
  }, [isRealMode, realReloadNonce, selectedDate, view]);

  useEffect(() => {
    if (!isRealMode) return;
    const requestId = ++compositeListRequestId.current;
    const controller = new AbortController();
    // 复合菜的列表状态以服务端重新读取结果为准，避免本地乐观数据覆盖并发修改。
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setCompositeDishesLoading(true);
    setCompositeDishesError(undefined);
    void loadCompositeDishes(controller.signal)
      .then((dishes) => {
        if (controller.signal.aborted || requestId !== compositeListRequestId.current) return;
        setCompositeDishes(dishes.filter((dish) => !dish.deleted));
      })
      .catch((cause) => {
        if (controller.signal.aborted || isAbortError(cause) || requestId !== compositeListRequestId.current) return;
        // 刷新失败时清空旧列表，避免把过期复合菜继续当作服务端当前数据展示。
        setCompositeDishes([]);
        setCompositeDishesError(compositeDishErrorMessage(cause, '复合菜加载失败'));
      })
      .finally(() => {
        if (!controller.signal.aborted && requestId === compositeListRequestId.current)
          setCompositeDishesLoading(false);
      });
    return () => {
      controller.abort();
    };
  }, [compositeReloadNonce, isRealMode]);

  const selectedMeal = useMemo(() => meals.find((meal) => meal.id === dialogMealId), [dialogMealId, meals]);
  const weekDays = useMemo(() => mapWeekLogs(realLogs, selectedDate), [realLogs, selectedDate]);

  const openFoodDialog = useCallback(
    (mealId: MealSection['id'], date = selectedDate) => {
      setNotice('');
      setDialogMode('create');
      setEditingLogId(undefined);
      setDialogMealId(mealId);
      setDialogDate(date);
      setFoodName('');
      setFoodAmount('100');
      setFoodUnit('g');
      setNutritionFoodId(undefined);
      setNutritionCandidates([]);
      setNutritionCandidatesError(undefined);
      setSelectedCompositeDishId(undefined);
      setCompositeDishServings('1');
    },
    [selectedDate],
  );

  const openEditDialog = (logId: string) => {
    const log = realLogs.find((candidate) => candidate.food_log_id === logId);
    const firstItem = log?.items[0];
    if (!log || !firstItem) return;
    setNotice('');
    setDialogMode('edit');
    setEditingLogId(log.food_log_id);
    setDialogMealId(log.meal_type as MealSection['id']);
    setDialogDate(new Date(log.meal_time));
    setFoodName(firstItem.raw_name);
    setNutritionFoodId(firstItem.nutrition_food_id ?? undefined);
    setFoodAmount(String(firstItem.amount));
    setFoodUnit(firstItem.unit);
    setNutritionCandidates([]);
    setNutritionCandidatesError(undefined);
    setSelectedCompositeDishId(log.composite_dish_id ?? undefined);
    setCompositeDishServings(String(log.composite_dish_servings ?? '1'));
  };

  const closeFoodDialog = (cancelMutation = true) => {
    if (cancelMutation) cancelFoodMutation();
    setDialogMealId(undefined);
    setDialogMode('create');
    setEditingLogId(undefined);
    setFoodName('');
    setFoodAmount('100');
    setFoodUnit('g');
    setNutritionFoodId(undefined);
    setNutritionCandidates([]);
    setNutritionCandidatesError(undefined);
    setSelectedCompositeDishId(undefined);
    setCompositeDishServings('1');
  };

  useEffect(() => {
    if (
      !isRealMode ||
      !linkedMealPlanMealId ||
      dialogMealId != null ||
      !['breakfast', 'lunch', 'dinner', 'snack'].includes(linkedMealType ?? '')
    )
      return;
    // URL 参数驱动打开编辑对话框，属于外部导航状态同步，允许在此处更新表单状态。
    // eslint-disable-next-line react-hooks/set-state-in-effect
    openFoodDialog(linkedMealType as MealSection['id']);
  }, [dialogMealId, isRealMode, linkedMealPlanMealId, linkedMealType, openFoodDialog]);

  useEffect(() => {
    if (!isRealMode || dialogMealId == null || selectedCompositeDishId || foodName.trim().length < 2) {
      // 输入不满足检索条件时清除上一次异步请求的候选结果，避免展示过期数据。
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setNutritionCandidates([]);
      setNutritionCandidatesError(undefined);
      setNutritionCandidatesLoading(false);
      return;
    }
    let active = true;
    const controller = new AbortController();
    const timer = window.setTimeout(() => {
      setNutritionCandidatesLoading(true);
      setNutritionCandidatesError(undefined);
      void searchNutritionFoods(foodName.trim(), 8, controller.signal)
        .then((candidates) => {
          if (active && !controller.signal.aborted) setNutritionCandidates(candidates);
        })
        .catch((cause) => {
          if (active && !controller.signal.aborted && !isAbortError(cause)) {
            setNutritionCandidates([]);
            setNutritionCandidatesError(cause instanceof Error ? cause.message : '营养候选加载失败');
          }
        })
        .finally(() => {
          if (active && !controller.signal.aborted) setNutritionCandidatesLoading(false);
        });
    }, 300);
    return () => {
      active = false;
      window.clearTimeout(timer);
      controller.abort();
    };
  }, [dialogMealId, foodName, isRealMode, selectedCompositeDishId]);

  const selectNutritionCandidate = (candidate: NutritionFoodCandidate) => {
    setNutritionFoodId(candidate.nutrition_food_id);
    setFoodName(candidate.chinese_name?.trim() || candidate.standard_name);
    setNotice(`已选择${candidate.chinese_name?.trim() || candidate.standard_name}，保存时将按目录营养值计算。`);
  };

  const selectCompositeDish = (dish: CompositeDish) => {
    setSelectedCompositeDishId(dish.composite_dish_id);
    setFoodName(dish.dish_name);
    setFoodUnit('份');
    setNutritionFoodId(undefined);
    setCompositeDishServings('1');
    setNotice(`已选择${dish.dish_name}，服务端将按食用份数保存营养快照。`);
  };

  const populateDishEditor = (dish?: CompositeDish) => {
    setDishName(dish?.dish_name ?? '');
    setDishTotalServings(String(dish?.total_servings ?? '2'));
    setDishComponents(
      dish?.components.map((component) => ({
        nutritionFoodId: component.nutrition_food_id,
        rawName: component.raw_name,
        amount: String(component.amount),
        unit: component.unit,
      })) ?? [{ rawName: '', amount: '', unit: 'g' }],
    );
  };

  const openDishEditor = (dish?: CompositeDish) => {
    dishDetailAbortController.current?.abort();
    dishDetailAbortController.current = undefined;
    const requestId = ++dishRequestId.current;
    setDishError(undefined);
    setEditingDishId(dish?.composite_dish_id);
    setDishDetailReady(!dish || !isRealMode);
    setDishLoading(Boolean(dish && isRealMode));
    populateDishEditor(dish);
    setDishCandidateMap({});
    setDishDialogOpen(true);
    if (!dish || !isRealMode) return;

    const controller = new AbortController();
    dishDetailAbortController.current = controller;
    void loadCompositeDish(dish.composite_dish_id, controller.signal)
      .then((detail) => {
        if (controller.signal.aborted || dishRequestId.current !== requestId) return;
        if (detail.deleted) {
          setDishDetailReady(false);
          setDishError('这道复合菜已被删除，请重新加载列表。');
          return;
        }
        setEditingDishId(detail.composite_dish_id);
        populateDishEditor(detail);
        setDishDetailReady(true);
      })
      .catch((cause) => {
        if (controller.signal.aborted || isAbortError(cause) || dishRequestId.current !== requestId) return;
        setDishDetailReady(false);
        setDishError(compositeDishErrorMessage(cause, '复合菜详情加载失败，请重试。'));
      })
      .finally(() => {
        if (!controller.signal.aborted && dishRequestId.current === requestId) setDishLoading(false);
        if (dishDetailAbortController.current === controller) dishDetailAbortController.current = undefined;
      });
  };

  const closeDishEditor = (cancelMutation = true) => {
    if (cancelMutation) cancelDishMutation();
    dishRequestId.current += 1;
    dishDetailAbortController.current?.abort();
    dishDetailAbortController.current = undefined;
    setDishDialogOpen(false);
    setEditingDishId(undefined);
    setDishDetailReady(true);
    setDishLoading(false);
    setDishError(undefined);
  };

  const updateDishComponent = (index: number, patch: Partial<CompositeDishDraftComponent>) => {
    setDishComponents((current) =>
      current.map((component, componentIndex) => (componentIndex === index ? { ...component, ...patch } : component)),
    );
  };

  const saveDish = () => {
    if (dishLoading || (editingDishId && !dishDetailReady)) {
      setDishError('请先读取最新的复合菜详情后再保存。');
      return;
    }
    const servings = Number(dishTotalServings);
    if (!dishName.trim() || !Number.isFinite(servings) || servings <= 0) {
      setDishError('请填写菜名和有效的总份数。');
      return;
    }
    if (
      dishComponents.some(
        (component) =>
          !component.nutritionFoodId ||
          !component.rawName.trim() ||
          !Number.isFinite(Number(component.amount)) ||
          Number(component.amount) <= 0 ||
          !component.unit.trim(),
      )
    ) {
      setDishError('每项食材都必须选择营养目录并填写有效用量。');
      return;
    }
    const request = {
      dish_name: dishName.trim(),
      total_servings: servings,
      components: dishComponents.map((component) => ({
        nutrition_food_id: Number(component.nutritionFoodId),
        raw_name: component.rawName.trim(),
        amount: Number(component.amount),
        unit: component.unit.trim(),
      })),
    };
    const editingDish = editingDishId
      ? compositeDishes.find((dish) => dish.composite_dish_id === editingDishId)
      : undefined;
    if (editingDishId && !editingDish) {
      setDishError('复合菜版本已失效，请重新加载后再编辑。');
      return;
    }
    setDishSaving(true);
    setDishError(undefined);
    const mutation = beginDishMutation();
    const operation = editingDishId
      ? updateCompositeDish(editingDishId, editingDish?.revision ?? 0, request, mutation.controller.signal)
      : createCompositeDish(request, mutation.controller.signal);
    void operation
      .then((saved) => {
        if (!isCurrentDishMutation(mutation)) return;
        setCompositeDishes((current) => [
          saved,
          ...current.filter((dish) => dish.composite_dish_id !== saved.composite_dish_id),
        ]);
        setNotice(`${saved.dish_name} 已保存。`);
        closeDishEditor(false);
        setCompositeReloadNonce((current) => current + 1);
      })
      .catch((cause) => {
        if (!isCurrentDishMutation(mutation) || isAbortError(cause)) return;
        setDishError(compositeDishErrorMessage(cause, '复合菜保存失败'));
      })
      .finally(() => {
        if (!isCurrentDishMutation(mutation)) return;
        setDishSaving(false);
        dishMutationAbortController.current = undefined;
      });
  };

  const requestRemoveDish = (dish: CompositeDish) => {
    setDishError(undefined);
    setPendingCompositeDishDeletion(dish);
  };

  const confirmRemoveDish = () => {
    const dish = pendingCompositeDishDeletion;
    if (!dish || dishDeleting) return;
    setDishDeleting(true);
    setDishError(undefined);
    const mutation = beginDishMutation();
    void deleteCompositeDish(dish.composite_dish_id, dish.revision, mutation.controller.signal)
      .then(() => {
        if (!isCurrentDishMutation(mutation)) return;
        setCompositeDishes((current) => current.filter((item) => item.composite_dish_id !== dish.composite_dish_id));
        if (selectedCompositeDishId === dish.composite_dish_id) setSelectedCompositeDishId(undefined);
        setNotice(`${dish.dish_name} 已删除，历史饮食记录不受影响。`);
        setPendingCompositeDishDeletion(undefined);
        setCompositeReloadNonce((current) => current + 1);
      })
      .catch((cause) => {
        if (!isCurrentDishMutation(mutation) || isAbortError(cause)) return;
        setDishError(compositeDishErrorMessage(cause, '复合菜删除失败'));
      })
      .finally(() => {
        if (!isCurrentDishMutation(mutation)) return;
        setDishDeleting(false);
        dishMutationAbortController.current = undefined;
      });
  };

  const addFood = () => {
    const selectedDish = selectedCompositeDishId
      ? compositeDishes.find((dish) => dish.composite_dish_id === selectedCompositeDishId)
      : undefined;
    const name = foodName.trim();
    if ((!name && !selectedDish) || !dialogMealId) return;
    const amount = Number(selectedDish ? compositeDishServings : foodAmount);
    const unit = foodUnit.trim();
    if (!Number.isFinite(amount) || amount <= 0 || !unit) {
      setNotice('请填写有效的份量和单位。');
      return;
    }

    if (isRealMode) {
      if (dialogMode === 'edit' && editingLogId) {
        const current = realLogs.find((log) => log.food_log_id === editingLogId);
        if (!current || current.items.length === 0 || foodMutation) return;
        const mutation = beginFoodMutation('update');
        void updateFoodLog(
          editingLogId,
          current.revision,
          {
            meal_time: current.meal_time,
            meal_type: current.meal_type,
            notes: current.notes ?? undefined,
            meal_plan_meal_id: linkedMealPlanMealId ?? current.meal_plan_meal_id ?? undefined,
            composite_dish_id: selectedDish?.composite_dish_id,
            composite_dish_revision: selectedDish?.revision,
            composite_dish_servings: selectedDish ? amount : undefined,
            items: selectedDish
              ? []
              : current.items.map((item, index) =>
                  index === 0
                    ? {
                        raw_name: name,
                        amount,
                        unit,
                        ...(nutritionFoodId ? { nutrition_food_id: nutritionFoodId } : {}),
                      }
                    : {
                        raw_name: item.raw_name,
                        amount: asNumber(item.amount),
                        unit: item.unit,
                        ...(item.nutrition_food_id ? { nutrition_food_id: item.nutrition_food_id } : {}),
                      },
                ),
          },
          mutation.controller.signal,
        )
          .then((updated) => {
            if (!isCurrentFoodMutation(mutation)) return;
            const nextLogs = realLogs.map((log) => (log.food_log_id === updated.food_log_id ? updated : log));
            setRealLogs(nextLogs);
            setMeals(mapFoodLogs(nextLogs));
            setNotice(`${name} 已更新。`);
            setRealReloadNonce((current) => current + 1);
            closeFoodDialog(false);
          })
          .catch((cause) => {
            if (!isCurrentFoodMutation(mutation) || isAbortError(cause)) return;
            if (isFoodLogConflict(cause)) setRealReloadNonce((current) => current + 1);
            setNotice(foodLogErrorMessage(cause, '饮食记录更新失败'));
          })
          .finally(() => {
            if (!isCurrentFoodMutation(mutation)) return;
            setFoodMutation(undefined);
            foodMutationAbortController.current = undefined;
          });
        return;
      }
      if (foodMutation) return;
      const mutation = beginFoodMutation('create');
      void createFoodLog(
        {
          meal_time: new Date(dialogDate).toISOString(),
          meal_type: dialogMealId,
          meal_plan_meal_id: linkedMealPlanMealId,
          composite_dish_id: selectedDish?.composite_dish_id,
          composite_dish_revision: selectedDish?.revision,
          composite_dish_servings: selectedDish ? amount : undefined,
          items: selectedDish
            ? []
            : [
                {
                  raw_name: name,
                  amount,
                  unit,
                  ...(nutritionFoodId ? { nutrition_food_id: nutritionFoodId } : {}),
                },
              ],
        },
        mutation.controller.signal,
      )
        .then((created) => {
          if (!isCurrentFoodMutation(mutation)) return;
          setRealLogs((current) => [...current, created]);
          setMeals(mapFoodLogs([...realLogs, created]));
          setNotice(`${name} 已提交，营养值由服务端权威计算。`);
          setRealReloadNonce((current) => current + 1);
          closeFoodDialog(false);
        })
        .catch((cause) => {
          if (!isCurrentFoodMutation(mutation) || isAbortError(cause)) return;
          setNotice(foodLogErrorMessage(cause, '饮食记录保存失败'));
        })
        .finally(() => {
          if (!isCurrentFoodMutation(mutation)) return;
          setFoodMutation(undefined);
          foodMutationAbortController.current = undefined;
        });
      return;
    }

    setMeals((current) =>
      current.map((meal) =>
        meal.id === dialogMealId
          ? {
              ...meal,
              items: [
                ...meal.items,
                {
                  id: `${dialogMealId}-${Date.now()}`,
                  name,
                  status: 'pending',
                  carbs: 'C: 待估算',
                  protein: 'P: 待估算',
                  fat: 'F: 待估算',
                },
              ],
            }
          : meal,
      ),
    );
    setNotice(`${name} 已添加到${selectedMeal?.title ?? '餐次'}，等待营养估算。`);
    closeFoodDialog();
  };

  const removeFood = (mealId: MealSection['id'], foodId: string, foodNameToRemove: string) => {
    if (isRealMode) {
      const item = meals.find((meal) => meal.id === mealId)?.items.find((candidate) => candidate.id === foodId);
      if (!item?.logId || item.revision == null) return;
      setNotice('');
      setPendingFoodDeletion({ logId: item.logId, itemId: foodId, itemName: foodNameToRemove });
      return;
    }
    setMeals((current) =>
      current.map((meal) =>
        meal.id === mealId ? { ...meal, items: meal.items.filter((item) => item.id !== foodId) } : meal,
      ),
    );
    setNotice(`${foodNameToRemove} 已从当前记录移除。`);
  };

  const confirmRemoveFood = () => {
    if (!pendingFoodDeletion || foodMutation) return;
    const { logId, itemId, itemName } = pendingFoodDeletion;
    const current = realLogs.find((log) => log.food_log_id === logId);
    if (!current) {
      setPendingFoodDeletion(undefined);
      setNotice('记录已不在当前日期范围内，请重新加载。');
      return;
    }
    const remainingItems = current.items.filter((item) => `${logId}-${item.food_log_item_id}` !== itemId);
    const mutation = beginFoodMutation('delete');
    const operation: Promise<FoodLog | undefined> =
      remainingItems.length === 0
        ? deleteFoodLog(logId, current.revision, mutation.controller.signal).then(() => undefined)
        : updateFoodLog(
            logId,
            current.revision,
            buildFoodLogWriteRequest(current, remainingItems),
            mutation.controller.signal,
          );
    void operation
      .then((updated) => {
        if (!isCurrentFoodMutation(mutation)) return;
        if (remainingItems.length > 0 && !updated) {
          throw new Error('服务端未返回更新后的饮食记录');
        }
        const nextLogs =
          remainingItems.length === 0
            ? realLogs.filter((log) => log.food_log_id !== logId)
            : realLogs.map((log) => (log.food_log_id === logId ? (updated as FoodLog) : log));
        setRealLogs(nextLogs);
        setMeals(mapFoodLogs(nextLogs));
        setPendingFoodDeletion(undefined);
        setRealReloadNonce((current) => current + 1);
        setNotice(`${itemName} 已从当前记录移除。`);
      })
      .catch((cause) => {
        if (!isCurrentFoodMutation(mutation) || isAbortError(cause)) return;
        if (isFoodLogConflict(cause)) setRealReloadNonce((current) => current + 1);
        setNotice(foodLogErrorMessage(cause, '饮食记录删除失败'));
      })
      .finally(() => {
        if (!isCurrentFoodMutation(mutation)) return;
        setFoodMutation(undefined);
        foodMutationAbortController.current = undefined;
      });
  };

  const loadDeletedRecords = () => {
    if (!isRealMode || deletedLoading) return;
    deletedAbortController.current?.abort();
    const requestId = ++deletedRequestId.current;
    const controller = new AbortController();
    deletedAbortController.current = controller;
    setDeletedLoading(true);
    setDeletedError(undefined);
    void loadDeletedFoodLogs(controller.signal)
      .then((logs) => {
        if (controller.signal.aborted || requestId !== deletedRequestId.current) return;
        setDeletedLogs(logs);
      })
      .catch((cause) => {
        if (controller.signal.aborted || isAbortError(cause) || requestId !== deletedRequestId.current) return;
        // 重新读取失败时不保留旧回收站数据，避免用户对过期记录执行恢复操作。
        setDeletedLogs([]);
        setDeletedError(foodLogErrorMessage(cause, '已删除记录加载失败'));
      })
      .finally(() => {
        if (!controller.signal.aborted && requestId === deletedRequestId.current) setDeletedLoading(false);
        if (deletedAbortController.current === controller) deletedAbortController.current = undefined;
      });
  };

  const toggleDeleted = () => {
    const nextVisible = !showDeleted;
    setShowDeleted(nextVisible);
    if (nextVisible) {
      loadDeletedRecords();
    } else {
      // 收起回收站时取消尚未完成的读取，重新展开后只展示新的服务端结果。
      if (foodMutation === 'restore') cancelFoodMutation();
      deletedRequestId.current += 1;
      deletedAbortController.current?.abort();
      deletedAbortController.current = undefined;
      setDeletedLoading(false);
    }
  };

  const restoreDeleted = (log: FoodLog) => {
    if (foodMutation) return;
    const mutation = beginFoodMutation('restore');
    void restoreFoodLog(log.food_log_id, log.revision, mutation.controller.signal)
      .then(() => {
        if (!isCurrentFoodMutation(mutation)) return;
        setDeletedLogs((current) => current.filter((item) => item.food_log_id !== log.food_log_id));
        setRealReloadNonce((current) => current + 1);
        setNotice(`${log.items[0]?.raw_name ?? '饮食记录'} 已恢复。`);
      })
      .catch((cause) => {
        if (!isCurrentFoodMutation(mutation) || isAbortError(cause)) return;
        if (isFoodLogConflict(cause)) loadDeletedRecords();
        setNotice(foodLogErrorMessage(cause, '饮食记录恢复失败'));
      })
      .finally(() => {
        if (!isCurrentFoodMutation(mutation)) return;
        setFoodMutation(undefined);
        foodMutationAbortController.current = undefined;
      });
  };

  const reloadRecords = () => {
    if (isRealMode) {
      setRealReloadNonce((current) => current + 1);
      return;
    }
    setSearchParams({ view: 'records' });
    setNotice('正在重新加载饮食记录。');
  };

  const realEmpty = isRealMode && !realLoading && !realError && realLogs.length === 0;
  const visibleState: RecordsState = isRealMode
    ? realLoading
      ? 'loading'
      : realError
        ? 'error'
        : realEmpty
          ? 'empty'
          : 'default'
    : recordsState;
  const recordMetrics = isRealMode ? realMetrics(realLogs) : recordsState === 'empty' ? emptyMetrics : metrics;

  const renderMealCard = (meal: MealSection, date = selectedDate) => (
    <article className={styles.mealCard} key={meal.id}>
      <header className={styles.mealHeader}>
        <div className={styles.mealHeading}>
          <h2>
            <span className={styles.mealIcon} aria-hidden="true">
              {meal.icon}
            </span>
            <span>{meal.title}</span>
          </h2>
          <span>{meal.time}</span>
        </div>
        <Button
          className={styles.addFoodButton}
          variant="ghost"
          type="button"
          onClick={() => openFoodDialog(meal.id, date)}
        >
          + 添加食物
        </Button>
      </header>
      <div className={styles.foodList}>
        {meal.items.map((item) => (
          <div className={styles.foodRow} key={item.id}>
            <div className={styles.foodName}>
              <strong>{item.name}</strong>
              <span
                className={
                  item.status === 'confirmed'
                    ? styles.confirmed
                    : item.status === 'ambiguous'
                      ? styles.ambiguous
                      : item.status === 'invalid'
                        ? styles.invalid
                        : styles.pending
                }
              >
                {nutritionStatusLabel(item.status, isFigmaFixture)}
              </span>
            </div>
            <div className={styles.foodMeta}>
              <div className={styles.macroTags} aria-label="营养素">
                <span className={styles.carb}>{item.carbs}</span>
                <span className={styles.protein}>{item.protein}</span>
                <span className={styles.fat}>{item.fat}</span>
              </div>
              {isRealMode && item.logId ? (
                <Button
                  className={styles.iconAction}
                  variant="ghost"
                  size="icon"
                  type="button"
                  aria-label={`编辑${item.name}所在记录`}
                  title={`编辑${item.name}所在记录`}
                  onClick={() => openEditDialog(item.logId as string)}
                >
                  <Pencil aria-hidden="true" />
                </Button>
              ) : null}
              <Button
                className={styles.removeButton}
                variant="ghost"
                size="icon"
                type="button"
                aria-label={`删除${item.name}${isRealMode ? '所在记录' : ''}`}
                title={`删除${item.name}${isRealMode ? '所在记录' : ''}`}
                onClick={() => removeFood(meal.id, item.id, item.name)}
              >
                <Trash2 aria-hidden="true" />
              </Button>
            </div>
          </div>
        ))}
      </div>
    </article>
  );

  return (
    <WorkspaceLayout
      activeModule="records"
      fixtureVariant={isFigmaFixture ? 'diet-records' : undefined}
      displayNameOverride={isFigmaFixture ? 'Anddy' : undefined}
      profileIdOverride={isFigmaFixture ? '1234567' : undefined}
      sidebarAvatarSrc={isFigmaFixture ? FIXTURE_WORKSPACE_AVATARS.sidebar : undefined}
      topAvatarSrc={isFigmaFixture ? FIXTURE_WORKSPACE_AVATARS.topbar : undefined}
      showKnowledgeTopNav={!isFigmaFixture}
      showWindowControls={isFigmaFixture}
      sidebarFixture={
        isFigmaFixture
          ? {
              // 所有状态画板都保留 Figma 中的完整工作区侧栏。
              sessions: figmaSidebarSessions,
            }
          : undefined
      }
    >
      <div className={`${styles.page} fm-enter`}>
        <section className={styles.recordsBody} aria-label="饮食记录" data-figma-node-id="640:660">
          <header className={styles.dateToolbar}>
            <div className={styles.dateNavigation}>
              <Button
                className={styles.dateButton}
                variant="ghost"
                size="icon"
                aria-label="前一天"
                onClick={() => setSelectedDate((current) => shiftDate(current, view === 'week' ? -7 : -1))}
              >
                <ChevronLeft aria-hidden="true" />
              </Button>
              <Button
                className={styles.dateLabel}
                variant="ghost"
                onClick={() => setSelectedDate(isRealMode ? new Date() : initialDate)}
              >
                {view === 'week'
                  ? isRealMode
                    ? formatWeekLabel(selectedDate)
                    : '本周，3月11日 - 3月17日'
                  : formatDateLabel(selectedDate, isRealMode)}
              </Button>
              <Button
                className={styles.dateButton}
                variant="ghost"
                size="icon"
                aria-label="后一天"
                onClick={() => setSelectedDate((current) => shiftDate(current, view === 'week' ? 7 : 1))}
              >
                <ChevronRight aria-hidden="true" />
              </Button>
            </div>
            <Tabs className={styles.tabsRoot} value={view} onValueChange={(value) => setView(value as 'day' | 'week')}>
              <TabsList aria-label="记录视图" className={styles.viewSwitch}>
                <TabsTrigger className={view === 'day' ? styles.viewActive : ''} value="day">
                  日视图
                </TabsTrigger>
                <TabsTrigger className={view === 'week' ? styles.viewActive : ''} value="week">
                  周视图
                </TabsTrigger>
              </TabsList>
            </Tabs>
          </header>

          {visibleState === 'loading' ? (
            <section
              className={`${styles.loadingContent} ${isFigmaFixture ? styles.figmaLoadingContent : ''}`}
              aria-label="饮食记录加载中"
              aria-busy="true"
            >
              <div className={styles.loadingMetrics}>
                {Array.from({ length: 4 }, (_, index) => (
                  <div className={styles.loadingMetric} key={index}>
                    <span />
                    <strong />
                    <i />
                  </div>
                ))}
              </div>
              <div className={styles.loadingMeals}>
                <div className={styles.loadingMeal}>
                  <div className={styles.loadingMealHeader}>
                    <span />
                    <i />
                  </div>
                  <div className={styles.loadingMealRows}>
                    <strong />
                  </div>
                </div>
                <div className={`${styles.loadingMeal} ${styles.loadingMealDouble}`}>
                  <div className={styles.loadingMealHeader}>
                    <span />
                    <i />
                  </div>
                  <div className={styles.loadingMealRows}>
                    <strong />
                    <strong />
                  </div>
                </div>
              </div>
            </section>
          ) : visibleState === 'error' ? (
            <section
              className={`${styles.statePanel} ${isFigmaFixture ? styles.figmaErrorStatePanel : ''}`}
              aria-label="饮食记录加载失败"
              role="alert"
            >
              <div className={`${styles.stateIcon} ${styles.stateIconError}`}>
                <AlertTriangle aria-hidden="true" />
              </div>
              <div className={styles.stateCopy}>
                <h2>饮食记录加载失败</h2>
                <p>请检查网络连接后重试</p>
                {isRealMode && realError ? <p>{realError}</p> : null}
              </div>
              <Button className={styles.stateAction} onClick={reloadRecords}>
                <RefreshCw aria-hidden="true" />
                重新加载
              </Button>
            </section>
          ) : (
            <>
              <section className={styles.metrics} aria-label="营养指标" data-figma-node-id="640:674">
                {recordMetrics.map((metric) => (
                  <article className={styles.metricCard} key={metric.label}>
                    <div className={styles.metricCopy}>
                      <span>{metric.label}</span>
                      <div>
                        <strong>{metric.value}</strong>
                        <small>{metric.unit}</small>
                      </div>
                    </div>
                    <ProgressRing
                      percentage={metric.percentage}
                      tone={metric.tone}
                      assetSrc={isFigmaFixture && recordsState !== 'empty' ? figmaMetricAssets[metric.tone] : undefined}
                    />
                  </article>
                ))}
              </section>

              {visibleState === 'empty' ? (
                <section
                  className={`${styles.statePanel} ${isFigmaFixture ? styles.figmaEmptyStatePanel : ''}`}
                  aria-label="今天还没有饮食记录"
                >
                  <div className={`${styles.stateIcon} ${styles.stateIconEmpty}`}>
                    <Utensils aria-hidden="true" />
                  </div>
                  <div className={styles.stateCopy}>
                    <h2>{view === 'week' ? '本周还没有饮食记录' : '今天还没有饮食记录'}</h2>
                    <p>{view === 'week' ? '选择一个日期或记录一餐，开始建立饮食记录' : '点击下方按钮记录你的第一餐'}</p>
                  </div>
                  <Button className={styles.stateAction} onClick={() => openFoodDialog('breakfast')}>
                    <Plus aria-hidden="true" />
                    记录一餐
                  </Button>
                </section>
              ) : (
                <section className={styles.meals} aria-label="餐次记录" data-figma-node-id="640:711">
                  {isRealMode && view === 'week'
                    ? weekDays.map((day) => (
                        <section className={styles.weekDay} key={day.date.toISOString()} aria-label="周视图日期">
                          <header className={styles.weekDayHeader}>
                            <h2>{day.date.toLocaleDateString('zh-CN', { month: 'long', day: 'numeric' })}</h2>
                            <span>{sameLocalDate(day.date, new Date()) ? '今天' : ''}</span>
                          </header>
                          {day.meals.length > 0 ? (
                            day.meals.map((meal) => renderMealCard(meal, day.date))
                          ) : (
                            <p className={styles.weekDayEmpty}>暂无记录</p>
                          )}
                        </section>
                      ))
                    : meals.map((meal) => renderMealCard(meal, selectedDate))}
                </section>
              )}
            </>
          )}
        </section>

        {visibleState === 'default' || (visibleState === 'empty' && isRealMode) ? (
          <section className={styles.recordsActions} aria-label="饮食记录操作">
            {visibleState === 'default' ? (
              <>
                <Button className={styles.logMealButton} type="button" onClick={() => openFoodDialog('breakfast')}>
                  记录一餐
                </Button>
                <Button
                  className={styles.analyzeDayButton}
                  variant="outline"
                  type="button"
                  onClick={() => navigate('/analysis')}
                >
                  分析这一天
                </Button>
              </>
            ) : null}
            {isRealMode ? (
              <Button
                className={styles.deletedButton}
                variant="outline"
                type="button"
                aria-expanded={showDeleted}
                onClick={toggleDeleted}
              >
                <RotateCcw aria-hidden="true" />
                {showDeleted ? '收起已删除' : '已删除记录'}
              </Button>
            ) : null}
          </section>
        ) : null}

        {isRealMode ? (
          <section className={styles.compositeDishPanel} aria-label="我的复合菜">
            <header className={styles.compositeDishPanelHeader}>
              <div>
                <h2>我的复合菜</h2>
                <p>保存常用食材组成，记录时按实际食用份数计算。</p>
              </div>
              <Button type="button" variant="outline" onClick={() => openDishEditor()}>
                <Plus aria-hidden="true" />
                新建复合菜
              </Button>
            </header>
            {compositeDishesLoading ? <p className={styles.deletedState}>正在加载复合菜…</p> : null}
            {compositeDishesError ? (
              <div className={styles.deletedError} role="alert">
                <p className={styles.deletedState}>{compositeDishesError}</p>
                <Button
                  type="button"
                  variant="outline"
                  onClick={() => setCompositeReloadNonce((current) => current + 1)}
                  disabled={compositeDishesLoading}
                >
                  <RefreshCw aria-hidden="true" />
                  重试加载
                </Button>
              </div>
            ) : null}
            {!compositeDishesLoading && !compositeDishesError && compositeDishes.length === 0 ? (
              <p className={styles.deletedState}>还没有保存的复合菜。</p>
            ) : null}
            <div className={styles.compositeDishList}>
              {compositeDishes.map((dish) => (
                <article className={styles.compositeDishCard} key={dish.composite_dish_id}>
                  <div>
                    <strong>{dish.dish_name}</strong>
                    <span>
                      {Number(dish.calories_kcal_per_serving).toFixed(0)} kcal/份 · {dish.components.length} 项食材 · 共{' '}
                      {dish.total_servings} 份
                    </span>
                  </div>
                  <div className={styles.compositeDishActions}>
                    <Button
                      type="button"
                      variant="ghost"
                      onClick={() => {
                        openFoodDialog('breakfast');
                        selectCompositeDish(dish);
                      }}
                    >
                      记录这道菜
                    </Button>
                    <Button type="button" variant="ghost" onClick={() => openDishEditor(dish)}>
                      <Pencil aria-hidden="true" />
                      编辑
                    </Button>
                    <Button type="button" variant="ghost" onClick={() => requestRemoveDish(dish)}>
                      <Trash2 aria-hidden="true" />
                      删除
                    </Button>
                  </div>
                </article>
              ))}
            </div>
          </section>
        ) : null}

        {isRealMode && showDeleted ? (
          <section className={styles.deletedPanel} aria-label="已删除饮食记录">
            <header className={styles.deletedHeader}>
              <div>
                <h2>已删除记录</h2>
                <p>恢复后记录会回到原来的用餐日期。</p>
              </div>
              <RotateCcw aria-hidden="true" />
            </header>
            {deletedLoading ? <p className={styles.deletedState}>正在加载已删除记录…</p> : null}
            {deletedError ? (
              <div className={styles.deletedError} role="alert">
                <p className={styles.deletedState}>{deletedError}</p>
                <Button type="button" variant="outline" onClick={loadDeletedRecords} disabled={deletedLoading}>
                  <RefreshCw aria-hidden="true" />
                  重试加载
                </Button>
              </div>
            ) : null}
            {!deletedLoading && !deletedError && deletedLogs.length === 0 ? (
              <p className={styles.deletedState}>暂无可恢复记录。</p>
            ) : null}
            <div className={styles.deletedList}>
              {deletedLogs.map((log) => (
                <div className={styles.deletedRow} key={log.food_log_id}>
                  <div>
                    <strong>{log.items.map((item) => item.raw_name).join('、')}</strong>
                    <span>
                      {new Date(log.meal_time).toLocaleString('zh-CN', {
                        month: 'numeric',
                        day: 'numeric',
                        hour: '2-digit',
                        minute: '2-digit',
                      })}
                    </span>
                  </div>
                  <Button
                    variant="outline"
                    type="button"
                    onClick={() => restoreDeleted(log)}
                    disabled={foodMutation === 'restore'}
                    aria-label={`恢复${log.items[0]?.raw_name ?? '饮食记录'}`}
                  >
                    <RotateCcw aria-hidden="true" />
                    {foodMutation === 'restore' ? '恢复中…' : '恢复'}
                  </Button>
                </div>
              ))}
            </div>
          </section>
        ) : null}

        {visibleState === 'default' && !isRealMode ? (
          <section className={styles.entryDetail} aria-label="记录详情" data-figma-node-id="974:3">
            <h2>{'记录详情  ·  待确认记录可在这里补充后保存'}</h2>
            <p>{'蓝莓燕麦粥  ·  早餐  ·  08:30  ·  估算值'}</p>
            <p>{'份量  350  |  单位  g  |  热量  420 kcal  |  蛋白质  18 g  |  来源  USDA  |  估算状态  待确认'}</p>
            <div className={styles.entryActions}>
              <Button variant="ghost" type="button" onClick={() => setNotice('已打开自然语言记录入口。')}>
                记录一餐（自然语言）
              </Button>
              <Button variant="ghost" type="button" onClick={() => openFoodDialog('breakfast')}>
                编辑记录
              </Button>
              <Button variant="ghost" type="button" onClick={() => setNotice('已复制到明天的记录草稿。')}>
                复制到明天
              </Button>
              <Button variant="ghost" type="button" onClick={() => navigate('/analysis')}>
                分析当天
              </Button>
              <Button variant="ghost" type="button" onClick={() => setNotice('待确认记录已标记为可软删除状态。')}>
                软删除
              </Button>
            </div>
            <p className={styles.entryNote}>保存失败时保留草稿；已删除记录进入可恢复状态，不改变当天统计历史。</p>
          </section>
        ) : null}
        {notice ? (
          <p className={styles.notice} role="status" aria-live="polite">
            {notice}
          </p>
        ) : null}
      </div>

      <Dialog open={Boolean(dialogMealId)} onOpenChange={(open) => !open && closeFoodDialog()}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{dialogMode === 'edit' ? '编辑饮食记录' : '添加食物'}</DialogTitle>
            <DialogDescription>
              {dialogMode === 'edit'
                ? '只修改当前记录的第一项食物，其余明细会保留。'
                : `添加到 ${selectedMeal?.title ?? '当前餐次'}，营养值将在确认后估算。`}
            </DialogDescription>
          </DialogHeader>
          {notice ? (
            <p className={styles.dialogNotice} role="alert">
              {notice}
            </p>
          ) : null}
          {isRealMode && compositeDishes.length > 0 ? (
            <div className={styles.compositeDishPicker} aria-label="选择复合菜">
              <div className={styles.compositeDishPickerHeader}>
                <strong>选择已保存的复合菜</strong>
                {selectedCompositeDishId ? (
                  <Button type="button" variant="ghost" onClick={() => setSelectedCompositeDishId(undefined)}>
                    改为单项食材
                  </Button>
                ) : null}
              </div>
              <div className={styles.compositeDishOptions}>
                {compositeDishes.map((dish) => (
                  <Button
                    key={dish.composite_dish_id}
                    type="button"
                    variant={selectedCompositeDishId === dish.composite_dish_id ? 'secondary' : 'outline'}
                    onClick={() => selectCompositeDish(dish)}
                  >
                    <span>{dish.dish_name}</span>
                    <small>
                      {Number(dish.calories_kcal_per_serving).toFixed(0)} kcal/份 · {dish.total_servings} 份
                    </small>
                  </Button>
                ))}
              </div>
            </div>
          ) : null}
          <Input
            autoFocus
            placeholder="例如：煮鸡蛋 2 个"
            aria-label="食物名称"
            value={foodName}
            onChange={(event) => {
              setFoodName(event.target.value);
              setNutritionFoodId(undefined);
              setSelectedCompositeDishId(undefined);
            }}
            onKeyDown={(event) => {
              if (event.key === 'Enter') addFood();
            }}
          />
          {selectedCompositeDishId ? (
            <Input
              type="number"
              min="0.001"
              step="0.001"
              aria-label="复合菜食用份数"
              value={compositeDishServings}
              onChange={(event) => setCompositeDishServings(event.target.value)}
            />
          ) : null}
          {isRealMode ? (
            <div className={styles.nutritionCandidates} aria-label="营养目录候选">
              {nutritionCandidatesLoading ? <p>正在查找营养目录…</p> : null}
              {nutritionCandidatesError ? <p role="alert">{nutritionCandidatesError}</p> : null}
              {!nutritionCandidatesLoading && !nutritionCandidatesError && nutritionCandidates.length > 0 ? (
                <ul>
                  {nutritionCandidates.map((candidate) => (
                    <li key={candidate.nutrition_food_id}>
                      <Button
                        type="button"
                        variant={nutritionFoodId === candidate.nutrition_food_id ? 'secondary' : 'ghost'}
                        onClick={() => selectNutritionCandidate(candidate)}
                      >
                        <span>{candidate.chinese_name?.trim() || candidate.standard_name}</span>
                        <small>
                          {candidate.food_form || '未标注形态'} · 每 100{candidate.basis_unit}：
                          {candidateMetric(candidate.calories_kcal_per_100)} kcal · 蛋白质{' '}
                          {candidateMetric(candidate.protein_g_per_100)} g · {candidate.source_name || '目录来源未知'}
                        </small>
                      </Button>
                    </li>
                  ))}
                </ul>
              ) : null}
              {!nutritionCandidatesLoading &&
              !nutritionCandidatesError &&
              foodName.trim().length >= 2 &&
              nutritionCandidates.length === 0 ? (
                <p>没有可靠候选，保存后会标记为待确认，不会猜测营养值。</p>
              ) : null}
              {nutritionFoodId ? <p>已选择明确营养目录，服务端将按目录和单位换算计算。</p> : null}
            </div>
          ) : null}
          <Input
            type="number"
            min="0.001"
            step="0.001"
            placeholder="份量"
            aria-label="食物份量"
            value={foodAmount}
            onChange={(event) => setFoodAmount(event.target.value)}
          />
          <Input
            placeholder="单位，例如：g、克、份、个、杯"
            aria-label="食物单位"
            value={foodUnit}
            onChange={(event) => setFoodUnit(event.target.value)}
          />
          <DialogFooter>
            <Button variant="outline" onClick={() => closeFoodDialog()}>
              取消
            </Button>
            <Button
              onClick={addFood}
              disabled={Boolean(foodMutation) || !foodName.trim() || !foodAmount.trim() || !foodUnit.trim()}
            >
              {foodMutation === 'update'
                ? '保存中…'
                : foodMutation === 'create'
                  ? '添加中…'
                  : dialogMode === 'edit'
                    ? '保存'
                    : '添加'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={dishDialogOpen} onOpenChange={(open) => !open && closeDishEditor()}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{editingDishId ? '编辑复合菜' : '新建复合菜'}</DialogTitle>
            <DialogDescription>只保存食材组成和用量，不推断烹饪损耗或熟重。</DialogDescription>
          </DialogHeader>
          {dishLoading ? (
            <p className={styles.dishLoading} role="status">
              正在读取服务端的最新复合菜详情…
            </p>
          ) : null}
          <Input
            aria-label="复合菜名称"
            placeholder="例如：鸡肉蔬菜饭"
            value={dishName}
            disabled={dishLoading}
            onChange={(event) => setDishName(event.target.value)}
          />
          <Input
            type="number"
            min="0.001"
            step="0.001"
            aria-label="复合菜总份数"
            placeholder="成品总份数"
            value={dishTotalServings}
            disabled={dishLoading}
            onChange={(event) => setDishTotalServings(event.target.value)}
          />
          <div className={styles.dishComponentEditor} aria-label="复合菜食材组成">
            {dishComponents.map((component, index) => (
              <div className={styles.dishComponentRow} key={`${index}-${component.nutritionFoodId ?? 'new'}`}>
                <Input
                  aria-label={`第${index + 1}项食材名称`}
                  placeholder="搜索食材，例如：鸡胸肉"
                  value={component.rawName}
                  disabled={dishLoading}
                  onChange={(event) => {
                    const value = event.target.value;
                    updateDishComponent(index, { rawName: value, nutritionFoodId: undefined });
                    if (value.trim().length >= 2) {
                      void searchNutritionFoods(value.trim())
                        .then((candidates) => setDishCandidateMap((current) => ({ ...current, [index]: candidates })))
                        .catch(() => setDishCandidateMap((current) => ({ ...current, [index]: [] })));
                    }
                  }}
                />
                <Input
                  type="number"
                  min="0.001"
                  step="0.001"
                  aria-label={`第${index + 1}项食材用量`}
                  placeholder="用量"
                  value={component.amount}
                  disabled={dishLoading}
                  onChange={(event) => updateDishComponent(index, { amount: event.target.value })}
                />
                <Input
                  aria-label={`第${index + 1}项食材单位`}
                  placeholder="单位"
                  value={component.unit}
                  disabled={dishLoading}
                  onChange={(event) => updateDishComponent(index, { unit: event.target.value })}
                />
                {dishComponents.length > 1 ? (
                  <Button
                    type="button"
                    variant="ghost"
                    size="icon"
                    aria-label={`删除第${index + 1}项食材`}
                    disabled={dishLoading}
                    onClick={() =>
                      setDishComponents((current) => current.filter((_, itemIndex) => itemIndex !== index))
                    }
                  >
                    <Trash2 aria-hidden="true" />
                  </Button>
                ) : null}
                {(dishCandidateMap[index] ?? []).length > 0 ? (
                  <div className={styles.dishCandidates}>
                    {(dishCandidateMap[index] ?? []).map((candidate) => (
                      <Button
                        key={candidate.nutrition_food_id}
                        type="button"
                        variant={component.nutritionFoodId === candidate.nutrition_food_id ? 'secondary' : 'ghost'}
                        disabled={dishLoading}
                        onClick={() => {
                          updateDishComponent(index, {
                            nutritionFoodId: candidate.nutrition_food_id,
                            rawName: candidate.chinese_name?.trim() || candidate.standard_name,
                          });
                          setDishCandidateMap((current) => ({ ...current, [index]: [] }));
                        }}
                      >
                        {candidate.chinese_name?.trim() || candidate.standard_name} ·{' '}
                        {candidate.food_form || '未标注形态'}
                      </Button>
                    ))}
                  </div>
                ) : null}
              </div>
            ))}
          </div>
          <Button
            type="button"
            variant="ghost"
            disabled={dishLoading}
            onClick={() => setDishComponents((current) => [...current, { rawName: '', amount: '', unit: 'g' }])}
          >
            <Plus aria-hidden="true" />
            添加食材
          </Button>
          {dishError ? (
            <div className={styles.dishError} role="alert">
              <p>{dishError}</p>
              {editingDishId && !dishDetailReady ? (
                <Button
                  type="button"
                  variant="outline"
                  onClick={() => {
                    const current = compositeDishes.find((item) => item.composite_dish_id === editingDishId);
                    if (current) openDishEditor(current);
                  }}
                >
                  重新读取详情
                </Button>
              ) : null}
            </div>
          ) : null}
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => closeDishEditor()}>
              取消
            </Button>
            <Button
              type="button"
              onClick={saveDish}
              disabled={dishSaving || dishLoading || Boolean(editingDishId && !dishDetailReady)}
            >
              {dishLoading ? '读取中…' : dishSaving ? '保存中…' : '保存复合菜'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog
        open={Boolean(pendingCompositeDishDeletion)}
        onOpenChange={(open) => {
          if (!open && !dishDeleting) {
            setPendingCompositeDishDeletion(undefined);
            setDishError(undefined);
          }
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>确认删除复合菜</DialogTitle>
            <DialogDescription>
              {pendingCompositeDishDeletion
                ? `将删除“${pendingCompositeDishDeletion.dish_name}”。已有饮食记录会保留营养快照，不会被删除。`
                : ''}
            </DialogDescription>
          </DialogHeader>
          {dishError ? (
            <p className={styles.dishError} role="alert">
              {dishError}
            </p>
          ) : null}
          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              onClick={() => setPendingCompositeDishDeletion(undefined)}
              disabled={dishDeleting}
            >
              取消
            </Button>
            <Button type="button" onClick={confirmRemoveDish} disabled={dishDeleting}>
              {dishDeleting ? '删除中…' : '确认删除'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog
        open={Boolean(pendingFoodDeletion)}
        onOpenChange={(open) => {
          if (!open) {
            if (foodMutation === 'delete') cancelFoodMutation();
            setPendingFoodDeletion(undefined);
          }
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>确认移除饮食记录</DialogTitle>
            <DialogDescription>
              {pendingFoodDeletion
                ? `将从服务端记录中移除“${pendingFoodDeletion.itemName}”。如果记录中还有其它食物，只会更新当前这一项。`
                : ''}
            </DialogDescription>
          </DialogHeader>
          {notice ? (
            <p className={styles.dialogNotice} role="alert">
              {notice}
            </p>
          ) : null}
          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              onClick={() => {
                if (foodMutation === 'delete') cancelFoodMutation();
                setPendingFoodDeletion(undefined);
              }}
            >
              取消
            </Button>
            <Button type="button" onClick={confirmRemoveFood} disabled={foodMutation === 'delete'}>
              {foodMutation === 'delete' ? '处理中…' : '确认移除'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </WorkspaceLayout>
  );
}
