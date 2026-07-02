import { Tag } from '@arco-design/web-react';
import { NavLink } from 'react-router-dom';
import type { SessionSummary } from '../../types/session';
import styles from './SidebarSessionList.module.css';

type SidebarSessionListProps = {
  sessions: SessionSummary[];
};

const statusLabel: Record<string, string> = {
  validating: '校验中',
  completed: '完成',
  waiting_user: '追问',
};

export function SidebarSessionList({ sessions }: SidebarSessionListProps) {
  return (
    <section className={styles.section}>
      <div className={styles.label}>最近 Agent 会话</div>
      <div className={styles.list}>
        {sessions.map((session) => (
          <NavLink
            className={`${styles.item} ${session.active ? styles.active : ''}`}
            to={`/chat/${session.id}`}
            key={session.id}
          >
            <div>
              <strong>{session.title}</strong>
              <span>{session.subtitle}</span>
            </div>
            {session.status ? (
              <Tag color={session.active ? 'green' : 'gray'}>{statusLabel[session.status] ?? session.status}</Tag>
            ) : null}
          </NavLink>
        ))}
      </div>
    </section>
  );
}
