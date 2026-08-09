import { useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { CalendarClock, CircleUserRound, History, Monitor, ShieldCheck, Utensils } from 'lucide-react';
import { Button, Card, Table, Tag, type TableColumnProps } from './AdminPrimitives';
import styles from '../AdminPage.module.css';
import { AdminFilters, AdminOnlyNotice, OperationAuditCard } from './AdminComponents';
import {
  type UserBusinessSessionRow,
  type UserOperationHistoryRow,
  type UserRow,
  adminUserBusinessSessionRows,
  adminUserOperationHistoryRows,
  adminUserRows,
  adminUserSessionRows,
  canAccessAdmin,
  canManage,
  roleTag,
  sessionColumns,
  statusTag,
} from './AdminShared';
import type { AdminActionPayload } from './types';
import { loadAdminUsers, revokeAdminUserSessions, updateAdminUserStatus } from '../../../services/adminService';

const isMockMode = import.meta.env.VITE_AGENT_MODE !== 'real';

const businessSessionColumns: TableColumnProps<UserBusinessSessionRow>[] = [
  { title: '会话 ID', dataIndex: 'sessionId' },
  { title: '类型', dataIndex: 'type' },
  { title: '标题', dataIndex: 'title' },
  { title: '状态', dataIndex: 'status', render: (_, record) => statusTag(record.status) },
  { title: '最近活动', dataIndex: 'lastActivityAt' },
];

const operationHistoryColumns: TableColumnProps<UserOperationHistoryRow>[] = [
  { title: '动作', dataIndex: 'action' },
  { title: '操作者', dataIndex: 'actor' },
  { title: '结果', dataIndex: 'result', render: (_, record) => statusTag(record.result) },
  { title: 'request_id', dataIndex: 'requestId' },
  { title: '时间', dataIndex: 'createdAt' },
];

export function UsersSection({ onAction }: { onAction: (payload: AdminActionPayload) => void }) {
  const [selectedUser, setSelectedUser] = useState<UserRow | undefined>(isMockMode ? adminUserRows[1] : undefined);
  const [users, setUsers] = useState<UserRow[]>(isMockMode ? adminUserRows : []);
  const [loadError, setLoadError] = useState('');

  useEffect(() => {
    loadAdminUsers()
      .then((items) => {
        setUsers(items as UserRow[]);
        setSelectedUser((isMockMode ? adminUserRows[1] : items[0]) as UserRow | undefined);
      })
      .catch((error) => {
        setUsers([]);
        setSelectedUser(undefined);
        setLoadError(error instanceof Error ? error.message : '用户列表加载失败');
      });
  }, []);

  if (!canAccessAdmin) return <AdminOnlyNotice title="无权访问用户管理" />;

  const requestUserStatus = (record: UserRow, action: string, status: string) => {
    onAction({
      action,
      targetLabel: record.userId,
      targetType: 'user',
      targetId: record.userId,
      execute: async () => {
        await updateAdminUserStatus(record.userId, status);
      },
      onApply: () => {
        record.status = status;
        record.lockedUntil = status === 'locked' ? '2026-06-30 23:59' : '-';
      },
    });
  };

  const userColumns: TableColumnProps<UserRow>[] = [
    { title: '用户 ID', dataIndex: 'userId' },
    { title: '用户名', dataIndex: 'username' },
    { title: '展示名', dataIndex: 'displayName' },
    { title: '邮箱', dataIndex: 'email' },
    { title: '角色', dataIndex: 'role', render: roleTag },
    { title: '状态', dataIndex: 'status', render: statusTag },
    { title: '最近登录', dataIndex: 'lastLoginAt' },
    {
      title: '操作',
      render: (_, record) => {
        const canWriteUser = canManage && record.role !== 'admin';
        const statusActions =
          record.status === 'active'
            ? [
                { label: '锁定', action: '锁定用户', status: 'locked' },
                { label: '禁用', action: '禁用用户', status: 'disabled' },
              ]
            : [{ label: '启用', action: '启用用户', status: 'active' }];

        return (
          <div className={styles.rowActions}>
            <Button size="mini" icon={<CircleUserRound aria-hidden="true" />} onClick={() => setSelectedUser(record)}>
              查看详情
            </Button>
            {statusActions.map((item) => (
              <Button
                key={item.status}
                size="mini"
                disabled={!canWriteUser}
                onClick={() => requestUserStatus(record, item.action, item.status)}
              >
                {item.label}
              </Button>
            ))}
            <Button
              size="mini"
              disabled={!canWriteUser}
              onClick={() =>
                onAction({
                  action: '重置会话',
                  targetLabel: record.userId,
                  targetType: 'user_session',
                  targetId: record.userId,
                  execute: async () => {
                    await revokeAdminUserSessions(record.userId);
                  },
                  onApply: () => {
                    adminUserSessionRows
                      .filter((session) => session.userId === record.userId)
                      .forEach((session) => {
                        session.status = 'revoked';
                      });
                  },
                })
              }
            >
              撤销全部会话
            </Button>
          </div>
        );
      },
    },
  ];

  return (
    <>
      <AdminFilters placeholder="username / userId / email" />
      {loadError ? <Tag color="red">{loadError}</Tag> : null}
      {!canManage ? (
        <div className={styles.readOnlyNotice} role="status">
          <ShieldCheck aria-hidden="true" />
          <span>当前为 operator，只能查看用户详情，状态和会话操作已禁用。</span>
        </div>
      ) : null}
      <section className={styles.sectionLayout}>
        <Card className={styles.wideCard} bordered={false}>
          <div className={styles.cardHead}>
            <strong>用户列表</strong>
            <Tag color="orange">状态变更写审计</Tag>
          </div>
          <Table columns={userColumns} data={users} pagination={{ pageSize: 5, total: users.length }} size="small" />
        </Card>
        <aside className={styles.side}>
          {selectedUser ? (
            <UserDetailCard user={selectedUser} />
          ) : (
            <Card className={styles.card} bordered={false}>
              <div className={styles.emptyState}>
                <CircleUserRound aria-hidden="true" />
                <strong>暂无用户详情</strong>
                <span>{isMockMode ? '请选择用户查看详情。' : '详情接口尚未返回数据。'}</span>
              </div>
            </Card>
          )}
        </aside>
      </section>
      <OperationAuditCard />
    </>
  );
}

function UserDetailCard({ user }: { user: UserRow }) {
  const sessions = isMockMode ? adminUserSessionRows.filter((item) => item.userId === user.userId) : [];
  const businessSessions = isMockMode ? adminUserBusinessSessionRows.filter((item) => item.userId === user.userId) : [];
  const operationHistory = isMockMode
    ? adminUserOperationHistoryRows.filter((item) => item.userId === user.userId)
    : [];
  const initials = user.displayName.slice(0, 1) || user.username.slice(0, 1).toUpperCase();

  return (
    <Card className={styles.userDetailCard} bordered={false}>
      <div className={styles.userDetailHeader}>
        <div className={styles.userDetailIdentity}>
          <div className={styles.userDetailAvatar} aria-hidden="true">
            {initials}
          </div>
          <div className={styles.userDetailName}>
            <strong>{user.displayName}</strong>
            <span>@{user.username}</span>
          </div>
        </div>
        <div className={styles.userDetailBadges}>
          {roleTag(user.role)}
          {statusTag(user.status)}
        </div>
      </div>
      <div className={styles.userDetailMeta}>
        <span>
          <CircleUserRound aria-hidden="true" /> {user.userId}
        </span>
        <span>
          <CalendarClock aria-hidden="true" /> 注册于 {user.createdAt}
        </span>
      </div>
      <Tabs defaultValue="profile" className={styles.userDetailTabsRoot}>
        <TabsList className={styles.userDetailTabsList} aria-label="用户详情分区">
          <TabsTrigger value="profile">资料</TabsTrigger>
          <TabsTrigger value="diet">饮食画像</TabsTrigger>
          <TabsTrigger value="login-sessions">登录会话</TabsTrigger>
          <TabsTrigger value="business-sessions">业务会话</TabsTrigger>
          <TabsTrigger value="history">操作历史</TabsTrigger>
        </TabsList>
        <TabsContent value="profile" className={styles.userDetailPanel}>
          <DetailSectionHeading icon={<CircleUserRound aria-hidden="true" />} title="账号资料" />
          <DetailGrid
            items={[
              ['用户 ID', user.userId],
              ['用户名', user.username],
              ['展示名', user.displayName],
              ['邮箱', user.email],
              ['手机号', user.phone],
              ['角色', user.role],
              ['状态', user.status],
              ['最近登录', user.lastLoginAt],
              ['失败次数', String(user.loginFailedCount)],
              ['锁定至', user.lockedUntil],
            ]}
          />
        </TabsContent>
        <TabsContent value="diet" className={styles.userDetailPanel}>
          <DetailSectionHeading icon={<Utensils aria-hidden="true" />} title="饮食画像" />
          <DetailGrid
            items={[
              ['性别', user.gender],
              ['身高', user.heightCm ? `${user.heightCm} cm` : '-'],
              ['体重', user.weightKg ? `${user.weightKg} kg` : '-'],
              ['活动水平', user.activityLevel],
              ['饮食目标', user.dietGoal],
              ['热量目标', user.calorieTarget ? `${user.calorieTarget} kcal` : '-'],
              ['蛋白质目标', user.proteinTarget ? `${user.proteinTarget} g` : '-'],
              ['过敏原', user.allergens],
              ['忌口', user.dislikes],
              ['常用单位', user.preferredUnits],
            ]}
          />
        </TabsContent>
        <TabsContent value="login-sessions" className={styles.userDetailPanel}>
          <DetailSectionHeading icon={<Monitor aria-hidden="true" />} title="登录会话" />
          <DetailTableState isMockMode={isMockMode} hasData={sessions.length > 0}>
            <Table columns={sessionColumns} data={sessions} pagination={false} size="mini" />
          </DetailTableState>
        </TabsContent>
        <TabsContent value="business-sessions" className={styles.userDetailPanel}>
          <DetailSectionHeading icon={<Utensils aria-hidden="true" />} title="业务会话" />
          <DetailTableState isMockMode={isMockMode} hasData={businessSessions.length > 0}>
            <Table columns={businessSessionColumns} data={businessSessions} pagination={false} size="mini" />
          </DetailTableState>
        </TabsContent>
        <TabsContent value="history" className={styles.userDetailPanel}>
          <DetailSectionHeading icon={<History aria-hidden="true" />} title="操作历史" />
          <DetailTableState isMockMode={isMockMode} hasData={operationHistory.length > 0}>
            <Table columns={operationHistoryColumns} data={operationHistory} pagination={false} size="mini" />
          </DetailTableState>
        </TabsContent>
      </Tabs>
    </Card>
  );
}

function DetailSectionHeading({ icon, title }: { icon: ReactNode; title: string }) {
  return (
    <div className={styles.detailSectionHeading}>
      {icon}
      <strong>{title}</strong>
    </div>
  );
}

function DetailGrid({ items }: { items: Array<[string, string]> }) {
  return (
    <dl className={styles.detailGrid}>
      {items.map(([label, value]) => (
        <div key={label}>
          <dt>{label}</dt>
          <dd>{value || '-'}</dd>
        </div>
      ))}
    </dl>
  );
}

function DetailTableState({
  isMockMode: mockMode,
  hasData,
  children,
}: {
  isMockMode: boolean;
  hasData: boolean;
  children: ReactNode;
}) {
  if (hasData) return children;
  return (
    <div className={styles.detailEmptyState} role="status">
      <span>{mockMode ? '暂无记录' : '该详情数据尚未接入真实接口'}</span>
    </div>
  );
}
