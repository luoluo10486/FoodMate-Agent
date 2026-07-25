import { Card, Skeleton, Tag } from '@arco-design/web-react';
import { Link } from 'react-router-dom';
import type { TaskCardData } from '../../types/ui';
import type { UiComponentState } from '../../types/ui';
import styles from './TaskCard.module.css';

type TaskCardProps = {
  task: TaskCardData;
  state?: UiComponentState;
  errorText?: string;
};

export function TaskCard({ task, state = 'normal', errorText = '任务模板暂不可用' }: TaskCardProps) {
  const target = `/chat/${task.id}?prompt=${encodeURIComponent(task.prompt)}`;
  const isInteractive = state === 'normal';

  if (state === 'loading') {
    return (
      <Card className={`${styles.card} ${styles.loading}`} bordered={false}>
        <Skeleton text={{ rows: 3 }} animation />
      </Card>
    );
  }

  return (
    <Card className={`${styles.card} ${styles[task.accent]} ${styles[state]}`} bordered={false}>
      {isInteractive ? (
        <Link className={styles.linkOverlay} to={target} aria-label={`开始任务：${task.prompt}`} />
      ) : null}
      <Tag className={styles.tag} color={state === 'error' ? 'red' : undefined}>
        {task.title}
      </Tag>
      <p>{state === 'error' ? errorText : task.description}</p>
      <strong>{task.prompt}</strong>
    </Card>
  );
}
