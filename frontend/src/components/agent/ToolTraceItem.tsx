import { Collapse, Tag } from '@arco-design/web-react';
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
      <Collapse className={styles.details} bordered={false}>
        <Collapse.Item header="查看调用详情" name="detail">
          <dl>
            <dt>输入</dt>
            <dd>{tool.input ?? '等待工具入参'}</dd>
            <dt>输出</dt>
            <dd>{tool.error ?? tool.output ?? tool.summary}</dd>
          </dl>
        </Collapse.Item>
      </Collapse>
    </article>
  );
}
