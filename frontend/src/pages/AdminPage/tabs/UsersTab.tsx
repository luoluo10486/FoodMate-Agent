import { Button, Card, Table, Tag } from '@arco-design/web-react';
import type { TableColumnProps } from '@arco-design/web-react';
import { useState } from 'react';
import styles from '../AdminPage.module.css';
import {
  AdminFilters,
  AdminOnlyNotice,
  OperationAuditCard,
  type UserRow,
  adminUserRows,
  adminUserSessionRows,
  canManage,
  roleTag,
  sessionColumns,
  statusTag
} from './AdminShared';
import type { AdminActionPayload } from './types';

export function UsersSection({ onAction }: { onAction: (payload: AdminActionPayload) => void }) {
  const [selectedUser, setSelectedUser] = useState<UserRow>(adminUserRows[1]);

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
          <Button size="mini" onClick={() => setSelectedUser(record)}>查看</Button>
          <Button size="mini" disabled={record.role === 'admin'}
            onClick={() => onAction({ action: '锁定用户', targetLabel: record.userId, targetType: 'user', targetId: record.userId, onApply: () => { record.status = 'locked'; record.lockedUntil = '2026-06-30 23:59'; } })}>锁定</Button>
          <Button size="mini" disabled={record.role === 'admin'}
            onClick={() => onAction({ action: '禁用用户', targetLabel: record.userId, targetType: 'user', targetId: record.userId, onApply: () => { record.status = 'disabled'; } })}>禁用</Button>
          <Button size="mini" disabled={record.role === 'admin'}
            onClick={() => onAction({ action: '重置会话', targetLabel: record.userId, targetType: 'user_session', targetId: record.userId, onApply: () => { adminUserSessionRows.filter((s) => s.userId === record.userId).forEach((s) => { s.status = 'revoked'; }); } })}>重置会话</Button>
        </div>
      )
    }
  ];

  return (
    <>
      <AdminFilters placeholder="username / userId / email" />
      <section className={styles.sectionLayout}>
        <Card className={styles.wideCard} bordered={false}>
          <div className={styles.cardHead}>
            <strong>用户列表</strong>
            <Tag color="orange">状态变更写审计</Tag>
          </div>
          <Table columns={userColumns} data={adminUserRows} pagination={{ pageSize: 5, total: adminUserRows.length }} size="small" />
        </Card>
        <aside className={styles.side}>
          <UserDetailCard user={selectedUser} />
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
        <span>用户 ID</span><strong>{user.userId}</strong>
        <span>邮箱</span><strong>{user.email}</strong>
        <span>手机号</span><strong>{user.phone}</strong>
        <span>饮食目标</span><strong>{user.dietGoal}</strong>
        <span>热量目标</span><strong>{user.calorieTarget} kcal</strong>
        <span>失败次数</span><strong>{user.loginFailedCount}</strong>
        <span>锁定至</span><strong>{user.lockedUntil}</strong>
      </div>
      <div className={styles.cardSubhead}>登录会话</div>
      <Table columns={sessionColumns} data={sessions} pagination={false} size="mini" />
    </Card>
  );
}
