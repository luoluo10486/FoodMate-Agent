import type { AgentDisplayStatus } from '../../types/agent';
import styles from './AgentStatusStrip.module.css';

const steps: Array<{ key: AgentDisplayStatus; label: string; tone: string }> = [
  { key: 'planning', label: 'Planning', tone: 'purple' },
  { key: 'retrieving', label: 'Retrieving', tone: 'blue' },
  { key: 'executing_tools', label: 'Executing', tone: 'orange' },
  { key: 'composing', label: 'Composing', tone: 'gray' },
];

const statusIndex: Record<AgentDisplayStatus, number> = {
  routing: -1,
  planning: 0,
  retrieving: 1,
  executing_tools: 2,
  validating: 2,
  composing: 3,
  waiting_user: 3,
  completed: 4,
  failed: 4,
  cancelled: 4,
  superseded: 4,
};

type AgentStatusStripProps = {
  status: AgentDisplayStatus;
  preserveTones?: boolean;
  failedStep?: AgentDisplayStatus;
};

export function AgentStatusStrip({ status, preserveTones = false, failedStep }: AgentStatusStripProps) {
  const currentIndex = statusIndex[status];
  const failedIndex = failedStep ? statusIndex[failedStep] : -1;
  return (
    <section className={styles.strip} aria-label="Agent 运行状态">
      <strong className={styles.label}>代理流程：</strong>
      <div className={styles.steps} role="list">
        {steps.map((step, index) => {
          const failed = status === 'failed' && failedIndex === index;
          const completed = failed
            ? false
            : failedIndex >= 0
              ? index < failedIndex
              : currentIndex > index || status === 'completed';
          const active = !failed && currentIndex === index && !completed;
          return (
            <span
              className={`${styles.step} ${styles[step.tone]} ${completed && !preserveTones ? styles.completed : ''} ${active ? styles.active : ''} ${failed ? styles.failed : ''}`}
              key={step.key}
              role="listitem"
            >
              {step.label}
              <small aria-hidden="true">{failed ? '×' : completed ? '✓' : active ? '●' : '○'}</small>
            </span>
          );
        })}
      </div>
    </section>
  );
}
