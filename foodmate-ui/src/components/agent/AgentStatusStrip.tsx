import { Progress, Tag } from '@arco-design/web-react';
import type { AgentDisplayStatus } from '../../types/agent';
import styles from './AgentStatusStrip.module.css';

const steps: AgentDisplayStatus[] = ['routing', 'planning', 'retrieving', 'executing_tools', 'validating', 'composing'];

const labels: Record<AgentDisplayStatus, string> = {
  routing: 'routing',
  planning: 'planning',
  retrieving: 'retrieving',
  executing_tools: 'executing',
  validating: 'validating',
  composing: 'composing',
  waiting_user: 'waiting',
  completed: 'completed',
  failed: 'failed',
  cancelled: 'cancelled',
};

type AgentStatusStripProps = {
  status: AgentDisplayStatus;
};

export function AgentStatusStrip({ status }: AgentStatusStripProps) {
  const index = Math.max(steps.indexOf(status), 0);
  const percent = status === 'completed' ? 100 : Math.min(96, Math.round(((index + 1) / steps.length) * 100));

  return (
    <section className={styles.strip}>
      <div className={styles.head}>
        <strong>Agent 运行状态</strong>
        <Tag color={status === 'failed' || status === 'cancelled' ? 'red' : 'green'}>{labels[status]}</Tag>
      </div>
      <Progress percent={percent} showText={false} size="small" />
      <div className={styles.steps}>
        {steps.map((step) => (
          <span className={step === status ? styles.active : ''} key={step}>
            {labels[step]}
          </span>
        ))}
      </div>
    </section>
  );
}
