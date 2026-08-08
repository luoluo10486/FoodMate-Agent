import { useState } from 'react';
import { Button } from '@/components/ui/button';
import { WorkspaceLayout } from '../../layouts/WorkspaceLayout/WorkspaceLayout';
import styles from './PlanningPage.module.css';

type DayKey = '13' | '14' | '15' | '16' | '17';

type Meal = {
  name?: string;
  kcal?: string;
};

type MealRow = {
  label: string;
  meals: Meal[];
};

const days: Array<{ key: DayKey; label: string }> = [
  { key: '13', label: '周一 13' },
  { key: '14', label: '周二 14' },
  { key: '15', label: '周三 15' },
  { key: '16', label: '周四 16' },
  { key: '17', label: '周五 17' },
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

export function PlanningPage() {
  const [activeDay, setActiveDay] = useState<DayKey>('14');
  const [checkedItems, setCheckedItems] = useState<Record<string, boolean>>({});
  const [notice, setNotice] = useState('');

  const announce = (message: string) => setNotice(message);

  const toggleShoppingItem = (item: string) => {
    setCheckedItems((current) => ({ ...current, [item]: !current[item] }));
  };

  return (
    <WorkspaceLayout activeModule="planning">
      <div className={styles.page}>
        <main className={styles.planMain} aria-label="餐食规划">
          <section className={styles.planBanner} aria-labelledby="plan-title">
            <div className={styles.planSummary}>
              <h1 id="plan-title">增肌计划 v3</h1>
              <div className={styles.planMeta}>
                <span className={styles.goalTag}>目标：2,400千卡</span>
                <span className={styles.durationTag}>时长：7天</span>
              </div>
            </div>
            <div className={styles.bannerActions}>
              <Button
                className={styles.regenerateButton}
                variant="ghost"
                onClick={() => announce('已重新生成当前 7 天计划。')}
              >
                重新生成
              </Button>
              <Button className={styles.saveButton} variant="outline" onClick={() => announce('计划已保存。')}>
                保存计划
              </Button>
            </div>
          </section>

          <section className={styles.scheduleSection} aria-labelledby="schedule-title">
            <h2 id="schedule-title">每周日程</h2>
            <div className={styles.scheduleGrid}>
              <div className={styles.scheduleSpacer} aria-hidden="true" />
              <div className={styles.dayButtons} role="tablist" aria-label="每周日程日期">
                {days.map((day) => (
                  <button
                    className={`${styles.dayButton} ${activeDay === day.key ? styles.dayButtonActive : ''}`}
                    key={day.key}
                    type="button"
                    role="tab"
                    aria-selected={activeDay === day.key}
                    onClick={() => {
                      setActiveDay(day.key);
                      announce(`已查看${day.label}的计划。`);
                    }}
                  >
                    {day.label}
                  </button>
                ))}
              </div>

              {mealRows.map((row) => (
                <div className={styles.mealRow} key={row.label}>
                  <div className={styles.mealLabel}>{row.label}</div>
                  {row.meals.map((meal, index) =>
                    meal.name ? (
                      <article className={styles.mealCard} key={`${row.label}-${index}`}>
                        <strong>{meal.name}</strong>
                        <span>{meal.kcal}</span>
                      </article>
                    ) : (
                      <button
                        className={styles.emptyMeal}
                        key={`${row.label}-${index}`}
                        type="button"
                        onClick={() => announce(`已打开${row.label}的计划入口。`)}
                      >
                        + 计划
                      </button>
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

        <aside className={styles.planSidebar} aria-label="计划校验与购物清单">
          <section className={styles.constraintSection} aria-labelledby="constraints-title">
            <h2 id="constraints-title">约束校验</h2>
            <div className={styles.constraintList}>
              {constraints.map((item) => (
                <div className={styles.constraintRow} key={item.label}>
                  <span>{item.label}</span>
                  <strong className={item.tone === 'pass' ? styles.pass : styles.review}>{item.status}</strong>
                </div>
              ))}
            </div>
          </section>

          <div className={styles.divider} aria-hidden="true" />

          <section className={styles.shoppingSection} aria-labelledby="shopping-title">
            <h2 id="shopping-title">购物清单预览</h2>
            {shoppingGroups.map((group) => (
              <div className={styles.shoppingGroup} key={group.label}>
                <h3>{group.label}</h3>
                <div className={styles.shoppingItems}>
                  {group.items.map((item) => (
                    <label className={styles.shoppingItem} key={item}>
                      <input
                        type="checkbox"
                        checked={Boolean(checkedItems[item])}
                        onChange={() => toggleShoppingItem(item)}
                      />
                      <span>{item}</span>
                    </label>
                  ))}
                </div>
              </div>
            ))}
          </section>
        </aside>
      </div>
    </WorkspaceLayout>
  );
}
