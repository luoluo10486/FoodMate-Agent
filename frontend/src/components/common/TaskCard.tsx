import { Card, Tag } from '@arco-design/web-react';
import { Link } from 'react-router-dom';
import type { TaskCardData } from '../../types/ui';
import styles from './TaskCard.module.css';

type TaskCardProps = {
  task: TaskCardData;
};

export function TaskCard({ task }: TaskCardProps) {
  const target = `/chat/${task.id}?prompt=${encodeURIComponent(task.prompt)}`;

  return (
    <Card className={`${styles.card} ${styles[task.accent]}`} bordered={false}>
      <Link className={styles.linkOverlay} to={target} aria-label={`开始任务：${task.prompt}`} />
      <Tag className={styles.tag}>{task.title}</Tag>
      <p>{task.description}</p>
      <strong>{task.prompt}</strong>
    </Card>
  );
}
