import { Badge } from '@/components/ui/badge';
import type { ToolCall } from '../../types/agent';
import styles from './ToolTraceItem.module.css';

const variants: Record<ToolCall['status'], 'secondary' | 'warning' | 'default' | 'destructive'> = {
  pending: 'secondary',
  running: 'warning',
  success: 'default',
  failed: 'destructive',
  timeout: 'destructive',
  cancelled: 'secondary',
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
      <Badge variant={variants[tool.status]}>
        {tool.status}
        {tool.latencyMs ? ` · ${tool.latencyMs}ms` : ''}
      </Badge>
      <p>{tool.error ?? tool.summary}</p>
      <details className={styles.details}>
        <summary>查看调用详情</summary>
        <dl>
          <dt>输入</dt>
          <dd>{tool.input ?? '等待工具入参'}</dd>
          <dt>输出</dt>
          <dd>{tool.error ?? tool.output ?? tool.summary}</dd>
        </dl>
      </details>
    </article>
  );
}
