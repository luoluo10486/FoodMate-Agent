import { useState } from 'react';
import { Button, Card, Modal, Progress, Tag } from '@arco-design/web-react';
import { WorkspaceLayout } from '../../layouts/WorkspaceLayout/WorkspaceLayout';
import { Composer } from '../../components/workspace/Composer';
import { MealPlanTable } from '../../components/planning/MealPlanTable';
import { ShoppingList } from '../../components/planning/ShoppingList';
import { mealRows, planConstraints, shoppingGroups, validationItems } from '../../services/planningService';
import styles from './PlanningPage.module.css';

export function PlanningPage() {
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [saved, setSaved] = useState(false);

  return (
    <WorkspaceLayout activeModule="planning" moduleLabel={<Tag color="green">备餐规划</Tag>}>
      <div className={`${styles.page} fm-enter`}>
        <section className={styles.header}>
          <div>
            <h1>2 人 7 天高蛋白计划</h1>
          </div>
          <div className={styles.headerActions}>
            {saved ? <Tag color="green">已模拟保存</Tag> : null}
            <Button type="primary" onClick={() => setConfirmOpen(true)}>
              确认保存计划
            </Button>
          </div>
        </section>

        <section className={styles.constraints}>
          {planConstraints.map(([label, value]) => (
            <article key={label}>
              <span>{label}</span>
              <strong>{value}</strong>
            </article>
          ))}
        </section>

        <section className={styles.body}>
          <Card className={styles.tableCard} bordered={false}>
            <div className={styles.cardHead}>
              <strong>多日菜单</strong>
              <Tag color="orange">Tools（3/6）knowledge_search · database_query · plan_validator</Tag>
            </div>
            <MealPlanTable rows={mealRows} />
          </Card>

          <aside className={styles.side}>
            <Card className={styles.card} bordered={false}>
              <strong>购物清单</strong>
              <ShoppingList groups={shoppingGroups} estimate="预估：286 元 / 预算 300 元" />
            </Card>

            <Card className={styles.card} bordered={false}>
              <strong>计划校验</strong>
              <Progress percent={95} size="small" showText={false} />
              <div className={styles.validation}>
                {validationItems.map((item) => (
                  <article key={item.label}>
                    <Tag color={item.status === 'success' ? 'green' : 'orange'}>{item.label}</Tag>
                    <span>{item.value}</span>
                  </article>
                ))}
              </div>
            </Card>
          </aside>
        </section>

        <Composer
          toolsUsed={3}
          toolsTotal={6}
          agentsUsed={1}
          agentsTotal={1}
          placeholder="继续要求 FoodMate 修改预算、替换食材或生成购物清单..."
        />
        <Modal
          title="保存这份备餐计划？"
          visible={confirmOpen}
          okText="确认保存"
          cancelText="再检查一下"
          onOk={() => {
            setSaved(true);
            setConfirmOpen(false);
          }}
          onCancel={() => setConfirmOpen(false)}
        >
          <p className={styles.confirmText}>
            将保存 2 人 7 天高蛋白计划，预算预估 286 元。当前仅模拟保存，不会写入真实后端。
          </p>
        </Modal>
      </div>
    </WorkspaceLayout>
  );
}
