import { Badge } from '@/components/ui/badge';
import { Card } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { Link } from 'react-router-dom';
import type { TaskCardData, UiComponentState } from '../../types/ui';
import styles from './TaskCard.module.css';

type TaskCardProps = {
  task: TaskCardData;
  state?: UiComponentState;
  errorText?: string;
};

export function TaskCard({ task, state = 'normal', errorText = '任务模板暂不可用' }: TaskCardProps) {
  const target = import.meta.env.VITE_AGENT_MODE === 'real'
    ? `/chat?prompt=${encodeURIComponent(task.prompt)}`
    : `/chat/${task.id}?prompt=${encodeURIComponent(task.prompt)}`;
  const isInteractive = state === 'normal';

  if (state === 'loading') {
    return (
      <Card className={`${styles.card} ${styles.loading}`}>
        <div className="grid gap-3">
          <Skeleton className="h-5 w-1/3" />
          <Skeleton className="h-4 w-full" />
          <Skeleton className="h-4 w-3/4" />
        </div>
      </Card>
    );
  }

  return (
    <Card className={`${styles.card} ${styles[task.accent]} ${styles[state]}`}>
      {isInteractive ? <Link className={styles.linkOverlay} to={target} aria-label={`开始任务：${task.prompt}`} /> : null}
      <Badge className={styles.tag} variant={state === 'error' ? 'destructive' : 'secondary'}>
        {task.title}
      </Badge>
      <p>{state === 'error' ? errorText : task.description}</p>
      <strong>{task.prompt}</strong>
    </Card>
  );
}
