import { Button } from '@arco-design/web-react';
import styles from './EmptyState.module.css';

export function EmptyState() {
  return (
    <section className={styles.empty}>
      <strong>暂无会话</strong>
      <span>从一个任务开始，让 FoodMate 帮你计算、记录、分析或规划。</span>
      <Button type="primary">新建 Agent 会话</Button>
    </section>
  );
}
