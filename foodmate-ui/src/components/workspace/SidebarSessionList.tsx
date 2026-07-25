import { Dropdown, Menu, Tag, Tooltip } from '@arco-design/web-react';
import { IconArchive, IconDelete, IconEdit, IconMore } from '@arco-design/web-react/icon';
import { NavLink } from 'react-router-dom';
import type { SessionSummary } from '../../types/session';
import styles from './SidebarSessionList.module.css';

type SessionAction = 'rename' | 'archive' | 'unarchive' | 'delete';
type SidebarSessionListProps = {
  sessions: SessionSummary[];
  onAction?: (action: SessionAction, session: SessionSummary) => void;
};

const statusLabel: Record<string, string> = { validating: '校验中', completed: '完成', waiting_user: '待确认', archived: '已归档' };

export function SidebarSessionList({ sessions, onAction }: SidebarSessionListProps) {
  return (
    <section className={styles.section}>
      <div className={styles.label}>最近 Agent 会话</div>
      <div className={styles.list}>
        {sessions.map((session) => (
          <div className={`${styles.item} ${session.active ? styles.active : ''}`} key={session.id}>
            <NavLink className={styles.itemLink} to={`/chat/${session.id}`}>
              <div>
                <strong>{session.title}</strong>
                <span>{session.subtitle}</span>
              </div>
              {session.status ? <Tag color={session.active ? 'green' : 'gray'}>{statusLabel[session.status] ?? session.status}</Tag> : null}
            </NavLink>
            {onAction ? (
              <Dropdown
                droplist={(
                  <Menu onClickMenuItem={(key) => onAction(key as SessionAction, session)}>
                    <Menu.Item key="rename"><IconEdit />重命名</Menu.Item>
                    <Menu.Item key={(session.status as string) === 'archived' ? 'unarchive' : 'archive'}><IconArchive />{(session.status as string) === 'archived' ? '取消归档' : '归档'}</Menu.Item>
                    <Menu.Item key="delete"><IconDelete />删除</Menu.Item>
                  </Menu>
                )}
                trigger="click"
                position="br"
              >
                <Tooltip content="管理会话">
                  <button className={styles.moreButton} aria-label={`管理${session.title}`} type="button"><IconMore /></button>
                </Tooltip>
              </Dropdown>
            ) : null}
          </div>
        ))}
      </div>
    </section>
  );
}

export type { SessionAction };
