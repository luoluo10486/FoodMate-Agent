import { Archive, MoreHorizontal, Pencil, Trash2 } from 'lucide-react';
import { NavLink } from 'react-router-dom';
import { Badge } from '@/components/ui/badge';
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

const statusLabel: Record<string, string> = {
  validating: '校验中',
  completed: '完成',
  waiting_user: '待确认',
  archived: '已归档',
};

export function SidebarSessionList({ sessions, onAction }: SidebarSessionListProps) {
  return (
    <section className={styles.section}>
      <div className={styles.label}>最近 Agent 会话</div>
      <div className={styles.list}>
        {sessions.map((session) => {
          const archived = (session.status as string) === 'archived';
          return (
            <div className={`${styles.item} ${session.active ? styles.active : ''}`} key={session.id}>
              <NavLink className={styles.itemLink} to={`/chat/${session.id}`}>
                <div>
                  <strong>{session.title}</strong>
                  <span>{session.subtitle}</span>
                </div>
                {session.status ? (
                  <Badge className={styles.statusBadge} variant={session.active ? 'default' : 'secondary'}>
                    {statusLabel[session.status] ?? session.status}
                  </Badge>
                ) : null}
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
    </section>
  );
}

export type { SessionAction };
