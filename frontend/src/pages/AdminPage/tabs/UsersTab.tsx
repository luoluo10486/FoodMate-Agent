import { Button, Card, Table, Tag } from '@arco-design/web-react';
import type { TableColumnProps } from '@arco-design/web-react';
import { useEffect, useState } from 'react';
import styles from '../AdminPage.module.css';
import { AdminFilters, AdminOnlyNotice, OperationAuditCard } from './AdminComponents';
import {
  type UserRow,
  adminUserRows,
  adminUserSessionRows,
  canManage,
  roleTag,
  sessionColumns,
  statusTag,
} from './AdminShared';
import type { AdminActionPayload } from './types';
import { loadAdminUsers, revokeAdminUserSessions, updateAdminUserStatus } from '../../../services/adminService';

export function UsersSection({ onAction }: { onAction: (payload: AdminActionPayload) => void }) {
  const [selectedUser, setSelectedUser] = useState<UserRow | undefined>(import.meta.env.VITE_AGENT_MODE === 'real' ? undefined : adminUserRows[1]);
  const [users, setUsers] = useState<UserRow[]>(import.meta.env.VITE_AGENT_MODE === 'real' ? [] : adminUserRows);
  const [loadError, setLoadError] = useState('');
  useEffect(() => { loadAdminUsers().then((items) => { setUsers(items as UserRow[]); setSelectedUser((items[0] ?? adminUserRows[1]) as UserRow); }).catch((error) => { setUsers([]); setLoadError(error instanceof Error ? error.message : '用户列表加载失败'); }); }, []);

  if (!canManage) return <AdminOnlyNotice title="无权访问用户管理" />;

  const userColumns: TableColumnProps<UserRow>[] = [
    { title: '用户 ID', dataIndex: 'userId' },
    { title: '用户名', dataIndex: 'username' },
    { title: '展示名', dataIndex: 'displayName' },
    { title: '角色', dataIndex: 'role', render: roleTag },
    { title: '状态', dataIndex: 'status', render: statusTag },
    { title: '最近登录', dataIndex: 'lastLoginAt' },
    {
      title: '操作',
      render: (_, record) => (
        <div className={styles.rowActions}>
          <Button size="mini" onClick={() => setSelectedUser(record)}>
            查看
          </Button>
          <Button
            size="mini"
            disabled={record.role === 'admin'}
            onClick={() =>
              onAction({
                action: '锁定用户',
                targetLabel: record.userId,
                targetType: 'user',
                targetId: record.userId,
                execute: async () => { await updateAdminUserStatus(record.userId, 'locked'); },
                onApply: () => {
                  record.status = 'locked';
                  record.lockedUntil = '2026-06-30 23:59';
                },
              })
            }
          >
            锁定
          </Button>
          <Button
            size="mini"
            disabled={record.role === 'admin'}
            onClick={() =>
              onAction({
                action: '禁用用户',
                targetLabel: record.userId,
                targetType: 'user',
                targetId: record.userId,
                execute: async () => { await updateAdminUserStatus(record.userId, 'disabled'); },
                onApply: () => {
                  record.status = 'disabled';
                },
              })
            }
          >
            禁用
          </Button>
          <Button
            size="mini"
            disabled={record.role === 'admin'}
            onClick={() =>
              onAction({
                action: '重置会话',
                targetLabel: record.userId,
                targetType: 'user_session',
                targetId: record.userId,
                execute: async () => { await revokeAdminUserSessions(record.userId); },
                onApply: () => {
                  adminUserSessionRows
                    .filter((s) => s.userId === record.userId)
                    .forEach((s) => {
                      s.status = 'revoked';
                    });
                },
              })
            }
          >
            重置会话
          </Button>
        </div>
      ),
    },
  ];

  return (
    <>
      <AdminFilters placeholder="username / userId / email" />
      {loadError ? <Tag color="red">{loadError}</Tag> : null}
      <section className={styles.sectionLayout}>
        <Card className={styles.wideCard} bordered={false}>
          <div className={styles.cardHead}>
            <strong>用户列表</strong>
            <Tag color="orange">状态变更写审计</Tag>
          </div>
          <Table
            columns={userColumns}
            data={users}
            pagination={{ pageSize: 5, total: users.length }}
            size="small"
          />
        </Card>
        <aside className={styles.side}>
          {selectedUser ? <UserDetailCard user={selectedUser} /> : <Card className={styles.card} bordered={false}>暂无用户详情</Card>}
        </aside>
      </section>
      <OperationAuditCard />
    </>
  );
}

function UserDetailCard({ user }: { user: UserRow }) {
  const sessions = adminUserSessionRows.filter((item) => item.userId === user.userId);
  return (
    <Card className={styles.card} bordered={false}>
      <div className={styles.cardHead}>
        <strong>用户详情</strong>
        {roleTag(user.role)}
      </div>
      <div className={styles.detailGrid}>
        <span>用户 ID</span>
        <strong>{user.userId}</strong>
        <span>邮箱</span>
        <strong>{user.email}</strong>
        <span>手机号</span>
        <strong>{user.phone}</strong>
        <span>饮食目标</span>
        <strong>{user.dietGoal}</strong>
        <span>热量目标</span>
        <strong>{user.calorieTarget} kcal</strong>
        <span>失败次数</span>
        <strong>{user.loginFailedCount}</strong>
        <span>锁定至</span>
        <strong>{user.lockedUntil}</strong>
      </div>
      <div className={styles.cardSubhead}>登录会话</div>
      <Table columns={sessionColumns} data={sessions} pagination={false} size="mini" />
    </Card>
  );
}
