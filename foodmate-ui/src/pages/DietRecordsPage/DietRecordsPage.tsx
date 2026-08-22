import { useMemo, useState, type CSSProperties } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { AlertTriangle, ChevronLeft, ChevronRight, Plus, RefreshCw, Trash2, Utensils } from 'lucide-react';
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
import { WorkspaceLayout } from '../../layouts/WorkspaceLayout/WorkspaceLayout';
import styles from './DietRecordsPage.module.css';

type FoodItem = {
  id: string;
  name: string;
  status: 'confirmed' | 'pending';
  carbs: string;
  protein: string;
  fat: string;
};

type MealSection = {
  id: 'breakfast' | 'lunch';
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

const metrics = [
  { label: '能量完成', value: '1,420', unit: '/ 2,000 kcal', percentage: 71, tone: 'purple' },
  { label: '蛋白质目标', value: '98', unit: '/ 120 g', percentage: 81, tone: 'green' },
  { label: '碳水目标', value: '150', unit: '/ 250 g', percentage: 60, tone: 'orange' },
  { label: '脂肪目标', value: '44', unit: '/ 70 g', percentage: 62, tone: 'red' },
] as const;

const emptyMetrics = metrics.map((metric) => ({ ...metric, value: '0', percentage: 0 }));

type RecordsState = 'default' | 'loading' | 'empty' | 'error';

function getRecordsState(value: string | null): RecordsState {
  return value === 'loading' || value === 'empty' || value === 'error' ? value : 'default';
}

function formatDateLabel(date: Date) {
  const isInitialDate = date.getTime() === initialDate.getTime();
  return isInitialDate ? '今天，3月14日' : `${date.getMonth() + 1}月${date.getDate()}日`;
}

function shiftDate(date: Date, amount: number) {
  const next = new Date(date);
  next.setDate(next.getDate() + amount);
  return next;
}

function ProgressRing({ percentage, tone }: { percentage: number; tone: (typeof metrics)[number]['tone'] }) {
  const style = { '--progress': percentage } as CSSProperties;
  return (
    <div className={`${styles.progressRing} ${styles[tone]}`} style={style} aria-label={`${percentage}% 完成`}>
      <span>{percentage}%</span>
    </div>
  );
}

export function DietRecordsPage() {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const recordsState = getRecordsState(searchParams.get('state'));
  const isFigmaFixture = searchParams.get('state') === 'v2';
  const [selectedDate, setSelectedDate] = useState(initialDate);
  const [view, setView] = useState<'day' | 'week'>('day');
  const [meals, setMeals] = useState<MealSection[]>(initialMeals);
  const [dialogMealId, setDialogMealId] = useState<MealSection['id']>();
  const [foodName, setFoodName] = useState('');
  const [notice, setNotice] = useState('');

  const selectedMeal = useMemo(() => meals.find((meal) => meal.id === dialogMealId), [dialogMealId, meals]);

  const openFoodDialog = (mealId: MealSection['id']) => {
    setDialogMealId(mealId);
    setFoodName('');
  };

  const closeFoodDialog = () => {
    setDialogMealId(undefined);
    setFoodName('');
  };

  const addFood = () => {
    const name = foodName.trim();
    if (!name || !dialogMealId) return;

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
    setMeals((current) =>
      current.map((meal) =>
        meal.id === mealId ? { ...meal, items: meal.items.filter((item) => item.id !== foodId) } : meal,
      ),
    );
    setNotice(`${foodNameToRemove} 已从当前记录移除。`);
  };

  const reloadRecords = () => {
    setSearchParams({ view: 'records' });
    setNotice('正在重新加载饮食记录。');
  };

  const recordMetrics = recordsState === 'empty' ? emptyMetrics : metrics;

  return (
    <WorkspaceLayout
      activeModule="records"
      displayNameOverride={isFigmaFixture ? 'Anddy' : undefined}
      profileIdOverride={isFigmaFixture ? '1234567' : undefined}
    >
      <div className={`${styles.page} fm-enter`}>
        <section className={styles.recordsBody} aria-label="饮食记录">
          <header className={styles.dateToolbar}>
            <div className={styles.dateNavigation}>
              <Button
                className={styles.dateButton}
                variant="ghost"
                size="icon"
                aria-label="前一天"
                onClick={() => setSelectedDate((current) => shiftDate(current, -1))}
              >
                <ChevronLeft aria-hidden="true" />
              </Button>
              <Button className={styles.dateLabel} variant="ghost" onClick={() => setSelectedDate(initialDate)}>
                {view === 'week' ? '本周，3月11日 - 3月17日' : formatDateLabel(selectedDate)}
              </Button>
              <Button
                className={styles.dateButton}
                variant="ghost"
                size="icon"
                aria-label="后一天"
                onClick={() => setSelectedDate((current) => shiftDate(current, 1))}
              >
                <ChevronRight aria-hidden="true" />
              </Button>
            </div>
            <div className={styles.viewSwitch} role="tablist" aria-label="记录视图">
              <Button
                variant="ghost"
                className={view === 'day' ? styles.viewActive : ''}
                type="button"
                role="tab"
                aria-selected={view === 'day'}
                onClick={() => setView('day')}
              >
                日视图
              </Button>
              <Button
                className={view === 'week' ? styles.viewActive : ''}
                type="button"
                variant="ghost"
                role="tab"
                aria-selected={view === 'week'}
                onClick={() => setView('week')}
              >
                周视图
              </Button>
            </div>
          </header>

          {recordsState === 'loading' ? (
            <section className={styles.loadingContent} aria-label="饮食记录加载中" aria-busy="true">
              <div className={styles.loadingMetrics}>
                {Array.from({ length: 4 }, (_, index) => (
                  <div className={styles.loadingMetric} key={index}>
                    <span />
                    <strong />
                    <i />
                  </div>
                ))}
              </div>
              <div className={styles.loadingMeal}>
                <div className={styles.loadingMealHeader}>
                  <span />
                  <i />
                </div>
                <strong />
              </div>
              <div className={styles.loadingMeal}>
                <div className={styles.loadingMealHeader}>
                  <span />
                  <i />
                </div>
                <strong />
                <strong />
              </div>
            </section>
          ) : recordsState === 'error' ? (
            <section className={styles.statePanel} aria-label="饮食记录加载失败" role="alert">
              <div className={`${styles.stateIcon} ${styles.stateIconError}`}>
                <AlertTriangle aria-hidden="true" />
              </div>
              <div className={styles.stateCopy}>
                <h2>饮食记录加载失败</h2>
                <p>请检查网络连接后重试</p>
              </div>
              <Button className={styles.stateAction} onClick={reloadRecords}>
                <RefreshCw aria-hidden="true" />
                重新加载
              </Button>
            </section>
          ) : (
            <>
              <section className={styles.metrics} aria-label="营养指标">
                {recordMetrics.map((metric) => (
                  <article className={styles.metricCard} key={metric.label}>
                    <div className={styles.metricCopy}>
                      <span>{metric.label}</span>
                      <div>
                        <strong>{metric.value}</strong>
                        <small>{metric.unit}</small>
                      </div>
                    </div>
                    <ProgressRing percentage={metric.percentage} tone={metric.tone} />
                  </article>
                ))}
              </section>

              {recordsState === 'empty' ? (
                <section className={styles.statePanel} aria-label="今天还没有饮食记录">
                  <div className={`${styles.stateIcon} ${styles.stateIconEmpty}`}>
                    <Utensils aria-hidden="true" />
                  </div>
                  <div className={styles.stateCopy}>
                    <h2>今天还没有饮食记录</h2>
                    <p>点击下方按钮记录你的第一餐</p>
                  </div>
                  <Button className={styles.stateAction} onClick={() => openFoodDialog('breakfast')}>
                    <Plus aria-hidden="true" />
                    记录一餐
                  </Button>
                </section>
              ) : (
                <section className={styles.meals} aria-label="餐次记录">
                  {meals.map((meal) => (
                    <article className={styles.mealCard} key={meal.id}>
                      <header className={styles.mealHeader}>
                        <div className={styles.mealHeading}>
                          <h2>
                            {meal.icon} {meal.title}
                          </h2>
                          <span>{meal.time}</span>
                        </div>
                        <Button
                          className={styles.addFoodButton}
                          variant="ghost"
                          type="button"
                          onClick={() => openFoodDialog(meal.id)}
                        >
                          + 添加食物
                        </Button>
                      </header>
                      <div className={styles.foodList}>
                        {meal.items.map((item) => (
                          <div className={styles.foodRow} key={item.id}>
                            <div className={styles.foodName}>
                              <strong>{item.name}</strong>
                              <span className={item.status === 'confirmed' ? styles.confirmed : styles.pending}>
                                {item.status === 'confirmed' ? '已确认' : '待确认'}
                              </span>
                            </div>
                            <div className={styles.foodMeta}>
                              <div className={styles.macroTags} aria-label="营养素">
                                <span className={styles.carb}>{item.carbs}</span>
                                <span className={styles.protein}>{item.protein}</span>
                                <span className={styles.fat}>{item.fat}</span>
                              </div>
                              <Button
                                className={styles.removeButton}
                                variant="ghost"
                                size="icon"
                                type="button"
                                aria-label={`删除${item.name}`}
                                title={`删除${item.name}`}
                                onClick={() => removeFood(meal.id, item.id, item.name)}
                              >
                                <Trash2 aria-hidden="true" />
                              </Button>
                            </div>
                          </div>
                        ))}
                      </div>
                    </article>
                  ))}
                </section>
              )}
            </>
          )}
        </section>

        {recordsState === 'default' ? (
          <section className={styles.entryDetail} aria-label="记录详情">
            <h2>记录详情 · 待确认记录可在这里补充后保存</h2>
            <p>蓝莓燕麦粥 · 早餐 · 08:30 · 估算值</p>
            <p>份量 350 | 单位 g | 热量 420 kcal | 蛋白质 18 g | 来源 USDA | 估算状态 待确认</p>
            <div className={styles.entryActions}>
              <button type="button" onClick={() => setNotice('已打开自然语言记录入口。')}>
                记录一餐（自然语言）
              </button>
              <button type="button" onClick={() => openFoodDialog('breakfast')}>
                编辑记录
              </button>
              <button type="button" onClick={() => setNotice('已复制到明天的记录草稿。')}>
                复制到明天
              </button>
              <button type="button" onClick={() => navigate('/analysis')}>
                分析当天
              </button>
              <button type="button" onClick={() => setNotice('待确认记录已标记为可软删除状态。')}>
                软删除
              </button>
            </div>
            <p className={styles.entryNote}>保存失败时保留草稿；已删除记录进入可恢复状态，不改变当天统计历史。</p>
            {notice ? (
              <p className={styles.notice} role="status" aria-live="polite">
                {notice}
              </p>
            ) : null}
          </section>
        ) : null}
      </div>

      <Dialog open={Boolean(dialogMealId)} onOpenChange={(open) => !open && closeFoodDialog()}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>添加食物</DialogTitle>
            <DialogDescription>添加到 {selectedMeal?.title ?? '当前餐次'}，营养值将在确认后估算。</DialogDescription>
          </DialogHeader>
          <Input
            autoFocus
            placeholder="例如：煮鸡蛋 2 个"
            value={foodName}
            onChange={(event) => setFoodName(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === 'Enter') addFood();
            }}
          />
          <DialogFooter>
            <Button variant="outline" onClick={closeFoodDialog}>
              取消
            </Button>
            <Button onClick={addFood} disabled={!foodName.trim()}>
              添加
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </WorkspaceLayout>
  );
}
