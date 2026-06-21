import { Card, Tag } from '@arco-design/web-react';
import type { TaskCardData } from '../../types/ui';
import styles from './TaskCard.module.css';

type TaskCardProps = {
  task: TaskCardData;
};

export function TaskCard({ task }: TaskCardProps) {
  return (
    <Card className={`${styles.card} ${styles[task.accent]}`} bordered={false}>
      <Tag className={styles.tag}>{task.title}</Tag>
      <p>{task.description}</p>
      <strong>{task.prompt}</strong>
    </Card>
  );
}
