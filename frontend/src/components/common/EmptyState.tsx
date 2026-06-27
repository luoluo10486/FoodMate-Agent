import { Button, Skeleton } from '@arco-design/web-react';
import type { UiComponentState } from '../../types/ui';
import styles from './EmptyState.module.css';

type EmptyStateProps = {
  state?: UiComponentState;
  title?: string;
  description?: string;
  actionLabel?: string;
  onAction?: () => void;
};

export function EmptyState({
  state = 'normal',
  title = '暂无会话',
  description = '从一个任务开始，让 FoodMate 帮你计算、记录、分析或规划。',
  actionLabel = '新建 Agent 会话',
  onAction
}: EmptyStateProps) {
  if (state === 'loading') {
    return (
      <section className={`${styles.empty} ${styles.loading}`}>
        <Skeleton text={{ rows: 2 }} animation />
      </section>
    );
  }

  return (
    <section className={`${styles.empty} ${styles[state]}`}>
      <strong>{state === 'error' ? '状态加载失败' : title}</strong>
      <span>{state === 'error' ? '请稍后重试，或从左侧新建一个会话。' : description}</span>
      <Button disabled={state === 'disabled'} status={state === 'error' ? 'danger' : 'default'} type="primary" onClick={onAction}>
        {state === 'error' ? '重试' : actionLabel}
      </Button>
    </section>
  );
}
