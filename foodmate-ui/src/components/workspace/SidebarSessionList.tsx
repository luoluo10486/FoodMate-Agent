import { Archive, ChevronLeft, ChevronRight, MessageCircle, MoreHorizontal, Pencil, Trash2 } from 'lucide-react';
import { NavLink } from 'react-router-dom';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import type { SessionSummary } from '../../types/session';
import styles from './SidebarSessionList.module.css';

type SessionAction = 'rename' | 'archive' | 'unarchive' | 'delete';
type SidebarSessionListProps = {
  sessions: SessionSummary[];
  onAction?: (action: SessionAction, session: SessionSummary) => void;
};

export function SidebarSessionList({ sessions, onAction }: SidebarSessionListProps) {
  return (
    <section className={styles.section}>
      <NavLink className={styles.sectionTitle} to="/chat">
        <MessageCircle aria-hidden="true" />
        <span>Agent 对话</span>
      </NavLink>
      <div className={styles.list}>
        {sessions.map((session) => {
          const archived = (session.status as string) === 'archived';
          return (
            <div className={`${styles.item} ${session.active ? styles.active : ''}`} key={session.id}>
              <NavLink className={styles.itemLink} to={`/chat/${session.id}`}>
                <span className={styles.dot} aria-hidden="true" />
                <span className={styles.title}>{session.title}</span>
                <span className={styles.meta}>{session.subtitle}</span>
              </NavLink>
              {onAction ? (
                <DropdownMenu>
                  <DropdownMenuTrigger asChild>
                    <button
                      className={styles.moreButton}
                      aria-label={`管理${session.title}`}
                      title={`管理${session.title}`}
                      type="button"
                    >
                      <MoreHorizontal aria-hidden="true" />
                    </button>
                  </DropdownMenuTrigger>
                  <DropdownMenuContent align="end">
                    <DropdownMenuItem onSelect={() => onAction('rename', session)}>
                      <Pencil aria-hidden="true" />
                      重命名
                    </DropdownMenuItem>
                    <DropdownMenuItem onSelect={() => onAction(archived ? 'unarchive' : 'archive', session)}>
                      <Archive aria-hidden="true" />
                      {archived ? '取消归档' : '归档'}
                    </DropdownMenuItem>
                    <DropdownMenuItem onSelect={() => onAction('delete', session)}>
                      <Trash2 aria-hidden="true" />
                      删除
                    </DropdownMenuItem>
                  </DropdownMenuContent>
                </DropdownMenu>
              ) : null}
            </div>
          );
        })}
      </div>
      <div className={styles.pagination} aria-label="会话分页">
        <button aria-label="上一页" disabled type="button">
          <ChevronLeft aria-hidden="true" />
        </button>
        <span>1 / 3</span>
        <button aria-label="下一页" type="button">
          <ChevronRight aria-hidden="true" />
        </button>
      </div>
    </section>
  );
}

export type { SessionAction };
