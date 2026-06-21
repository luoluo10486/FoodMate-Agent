import { Tag } from '@arco-design/web-react';
import type { ToolCall } from '../../types/agent';
import styles from './ToolTraceItem.module.css';

const colors: Record<ToolCall['status'], 'gray' | 'green' | 'red' | 'orange' | 'blue'> = {
  pending: 'gray',
  running: 'orange',
  success: 'green',
  failed: 'red',
  timeout: 'red',
  cancelled: 'gray'
};

type ToolTraceItemProps = {
  tool: ToolCall;
};

export function ToolTraceItem({ tool }: ToolTraceItemProps) {
  return (
    <article className={styles.item}>
      <div>
        <strong>{tool.name}</strong>
        <span>{tool.displayName}</span>
      </div>
      <Tag color={colors[tool.status]}>{tool.status}{tool.latencyMs ? ` · ${tool.latencyMs}ms` : ''}</Tag>
      <p>{tool.error ?? tool.summary}</p>
    </article>
  );
}
