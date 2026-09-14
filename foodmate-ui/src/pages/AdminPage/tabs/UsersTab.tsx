import { useEffect, useMemo, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import {
  CalendarDays,
  CircleX,
  CircleUserRound,
  Copy,
  History,
  MoreHorizontal,
  Monitor,
  RefreshCw,
  Search,
  ShieldCheck,
  Utensils,
} from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { DataTable, type TableColumnProps } from '@/components/ui/data-table';
import { Input } from '@/components/ui/input';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import styles from '../AdminPage.module.css';
import { isAbortError } from '../../../services/apiClient';
import { AdminOnlyNotice } from './AdminComponents';
import {
  type UserBusinessSessionRow,
  type UserOperationHistoryRow,
  type UserRow,
  adminUserBusinessSessionRows,
  adminUserOperationHistoryRows,
  adminUserSessionRows,
  canAccessAdmin,
  canManage,
  sessionColumns,
  statusTag,
} from './AdminShared';
import type { AdminActionPayload } from './types';
import {
  loadAdminUserDetail,
  loadAdminUsersPage,
  revokeAdminUserSessions,
  type AdminUserDetail,
  updateAdminUserStatus,
} from '../../../services/adminService';
import { FIXTURE_ADMIN_AVATARS, resolveAvatarUrl } from '../../../lib/avatar';
import { AvatarImage } from '../../../components/common/AvatarImage';

const isMockMode = import.meta.env.VITE_AGENT_MODE !== 'real';

type AdminUserView = UserRow & {
  activeSessions?: number;
  customModel?: string;
  registeredLabel?: string;
  revision?: number;
};

// 该 Fixture 对应 Figma 节点 801:215；真实模式继续使用 API 返回结果。
const figmaUserRows: AdminUserView[] = [
  {
    key: 'figma-user-098a1',
    userId: 'usr_098a1',
    username: 'anddy_lab',
    email: 'anddy@lab.io',
    displayName: 'Anddy 实验室',
    role: 'admin',
    status: 'active',
    avatarUrl: FIXTURE_ADMIN_AVATARS.userDetail,
    phone: '-',
    gender: '男',
    heightCm: 0,
    weightKg: 0,
    activityLevel: '-',
    dietGoal: '生酮 - 高蛋白',
    calorieTarget: 0,
    proteinTarget: 0,
    allergens: '-',
    dislikes: '-',
    preferredUnits: '公制',
    loginFailedCount: 0,
    lockedUntil: '-',
    lastLoginAt: 'Today, 10:24 AM',
    createdAt: 'Mar 14, 2024',
    activeSessions: 3,
    customModel: 'KetoMealFormer_v4',
    registeredLabel: 'Registered Mar 14, 2024',
  },
  {
    key: 'figma-user-112b9',
    userId: 'usr_112b9',
    username: 'sarah_chen',
    email: 'sarah@chen.me',
    displayName: 'Sarah Chen',
    role: 'operator',
    status: 'active',
    avatarUrl: '',
    phone: '-',
    // Figma fixture 的女性示例必须明确性别，才能使用登记的女性默认头像。
    gender: '女',
    heightCm: 0,
    weightKg: 0,
    activityLevel: '-',
    dietGoal: '-',
    calorieTarget: 0,
    proteinTarget: 0,
    allergens: '-',
    dislikes: '-',
    preferredUnits: '公制',
    loginFailedCount: 0,
    lockedUntil: '-',
    lastLoginAt: '-',
    createdAt: '-',
    activeSessions: 1,
  },
  {
    key: 'figma-user-774x2',
    userId: 'usr_774x2',
    username: 'kyle_smith',
    email: 'kyle@smith.com',
    displayName: 'Kyle Smith',
    role: 'user',
    status: 'disabled',
    avatarUrl: '',
    phone: '-',
    gender: '男',
    heightCm: 0,
    weightKg: 0,
    activityLevel: '-',
    dietGoal: '-',
    calorieTarget: 0,
    proteinTarget: 0,
    allergens: '-',
    dislikes: '-',
    preferredUnits: '公制',
    loginFailedCount: 0,
    lockedUntil: '-',
    lastLoginAt: '-',
    createdAt: '-',
    activeSessions: 0,
  },
  {
    key: 'figma-user-889d4',
    userId: 'usr_889d4',
    username: 'malicious_bot',
    email: 'bot@spam.xyz',
    displayName: 'Malicious Bot',
    role: 'user',
    status: 'locked',
    avatarUrl: '',
    phone: '-',
    gender: '-',
    heightCm: 0,
    weightKg: 0,
    activityLevel: '-',
    dietGoal: '-',
    calorieTarget: 0,
    proteinTarget: 0,
    allergens: '-',
    dislikes: '-',
    preferredUnits: '公制',
    loginFailedCount: 0,
    lockedUntil: '-',
    lastLoginAt: '-',
    createdAt: '-',
    activeSessions: 0,
  },
];

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

export function UsersSection({
  onAction,
  figmaFixture = false,
  refreshNonce = 0,
}: {
  onAction: (payload: AdminActionPayload) => void;
  figmaFixture?: boolean;
  refreshNonce?: number;
}) {
  const isFigmaFixture = figmaFixture && isMockMode;
  const [selectedUser, setSelectedUser] = useState<AdminUserView | undefined>(
    isMockMode ? figmaUserRows[0] : undefined,
  );
  const [users, setUsers] = useState<AdminUserView[]>(isMockMode ? figmaUserRows : []);
  const [loadError, setLoadError] = useState('');
  const [loading, setLoading] = useState(!isMockMode);
  const [retryNonce, setRetryNonce] = useState(0);
  const [query, setQuery] = useState('');
  const [roleFilter, setRoleFilter] = useState('all');
  const [statusFilter, setStatusFilter] = useState('active');
  const [filtersChanged, setFiltersChanged] = useState(false);
  const [page, setPage] = useState(1);
  const [totalUsers, setTotalUsers] = useState(isMockMode ? figmaUserRows.length : 0);
  const pageSize = 20;
  const [selectedDetail, setSelectedDetail] = useState<AdminUserDetail>();
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailError, setDetailError] = useState('');
  const [detailRetryNonce, setDetailRetryNonce] = useState(0);
  const listRequestIdRef = useRef(0);
  const detailRequestIdRef = useRef(0);
  const selectedUserId = selectedUser?.userId;
  const displayedTotalUsers = isFigmaFixture ? 1284 : totalUsers;

  useEffect(() => {
    if (isMockMode) return;
    const requestId = ++listRequestIdRef.current;
    const controller = new AbortController();
    // 列表查询由当前 effect 独占，筛选或刷新时取消上一条请求。
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setLoading(true);
    setLoadError('');
    loadAdminUsersPage(
      {
        page,
        size: pageSize,
        query: query.trim() || undefined,
        role: roleFilter,
        status: filtersChanged ? statusFilter : undefined,
      },
      controller.signal,
    )
      .then((result) => {
        if (controller.signal.aborted || requestId !== listRequestIdRef.current) return;
        const items = result.items as AdminUserView[];
        setUsers(items);
        setTotalUsers(result.total);
        setSelectedUser(items[0]);
      })
      .catch((error) => {
        if (controller.signal.aborted || isAbortError(error) || requestId !== listRequestIdRef.current) return;
        setUsers([]);
        setTotalUsers(0);
        setSelectedUser(undefined);
        setLoadError(error instanceof Error ? error.message : '用户列表加载失败');
      })
      .finally(() => {
        if (!controller.signal.aborted && requestId === listRequestIdRef.current) setLoading(false);
      });
    return () => {
      listRequestIdRef.current += 1;
      controller.abort();
    };
  }, [filtersChanged, page, query, refreshNonce, retryNonce, roleFilter, statusFilter]);

  useEffect(() => {
    const requestId = ++detailRequestIdRef.current;
    if (isMockMode || !selectedUserId) {
      // 用户列表为空或切换到 Fixture 时不保留上一条详情请求状态。
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setDetailLoading(false);
      setDetailError('');
      setSelectedDetail(undefined);
      return;
    }
    const controller = new AbortController();
    // 详情单独加载，避免用户列表接口被迫携带会话和审计明细。
    setDetailLoading(true);
    setDetailError('');
    setSelectedDetail(undefined);
    loadAdminUserDetail(selectedUserId, controller.signal)
      .then((detail) => {
        if (controller.signal.aborted || requestId !== detailRequestIdRef.current) return;
        setSelectedDetail(detail);
      })
      .catch((error) => {
        if (controller.signal.aborted || isAbortError(error) || requestId !== detailRequestIdRef.current) return;
        setSelectedDetail(undefined);
        setDetailError(error instanceof Error ? error.message : '用户详情加载失败');
      })
      .finally(() => {
        if (!controller.signal.aborted && requestId === detailRequestIdRef.current) setDetailLoading(false);
      });
    return () => {
      detailRequestIdRef.current += 1;
      controller.abort();
    };
  }, [detailRetryNonce, selectedUserId]);

  const visibleUsers = useMemo(() => {
    if (!isMockMode) return users;
    const normalizedQuery = query.trim().toLowerCase();
    return users.filter((user) => {
      const matchesQuery =
        !normalizedQuery || [user.userId, user.username, user.email].join(' ').toLowerCase().includes(normalizedQuery);
      const matchesRole = roleFilter === 'all' || user.role === roleFilter;
      const matchesStatus = !filtersChanged || statusFilter === 'all' || user.status === statusFilter;
      return matchesQuery && matchesRole && matchesStatus;
    });
  }, [filtersChanged, query, roleFilter, statusFilter, users]);

  if (!canAccessAdmin) return <AdminOnlyNotice title="无权访问用户管理" />;

  const updateFilter = (setter: (value: string) => void, value: string) => {
    setter(value);
    setFiltersChanged(true);
    setPage(1);
  };

  const requestUserStatus = (record: AdminUserView, action: string, status: string) => {
    onAction({
      action,
      targetLabel: record.userId,
      targetType: 'user',
      targetId: record.userId,
      execute: async (signal) => {
        await updateAdminUserStatus(record.userId, status, record.revision ?? 1, signal);
      },
      onApply: () => {
        // 真实模式由 refreshNonce 触发服务端回读，不能直接修改本地 Fixture 数据。
        if (!isMockMode) return;
        setUsers((current) =>
          current.map((user) =>
            user.userId === record.userId
              ? { ...user, status, lockedUntil: status === 'locked' ? '2026-06-30 23:59' : '-' }
              : user,
          ),
        );
      },
    });
  };

  const revokeSessions = (record: AdminUserView) => {
    onAction({
      action: '撤销所有会话',
      targetLabel: record.userId,
      targetType: 'user_session',
      targetId: record.userId,
      execute: async (signal) => {
        await revokeAdminUserSessions(record.userId, record.revision ?? 1, signal);
      },
      onApply: () => {
        // 真实模式的会话状态必须来自用户详情接口，避免污染共享 Fixture 数组。
        if (!isMockMode) return;
        adminUserSessionRows
          .filter((session) => session.userId === record.userId)
          .forEach((session) => {
            session.status = 'revoked';
          });
      },
    });
  };

  return (
    <section className={`${styles.usersLayout} ${isMockMode ? styles.usersLayoutFigma : ''}`}>
      <div className={styles.usersListColumn}>
        <div className={styles.usersFilters}>
          <label className={styles.usersSearch}>
            <Search aria-hidden="true" />
            <Input
              className={styles.usersSearchInput}
              aria-label="搜索用户名、ID或邮箱"
              value={query}
              onChange={(event) => {
                setQuery(event.target.value);
                setPage(1);
              }}
              placeholder="搜索用户名、ID或邮箱..."
            />
          </label>
          <FilterSelect
            ariaLabel="角色筛选"
            value={roleFilter}
            onChange={(value) => updateFilter(setRoleFilter, value)}
            options={[
              ['all', '角色：全部'],
              ['admin', '角色：管理员'],
              ['operator', '角色：操作员'],
              ['user', '角色：用户'],
            ]}
          />
          <FilterSelect
            ariaLabel="状态筛选"
            value={statusFilter}
            onChange={(value) => updateFilter(setStatusFilter, value)}
            options={[
              ['active', '状态：活跃'],
              ['all', '状态：全部'],
              ['disabled', '状态：已禁用'],
              ['locked', '状态：已锁定'],
            ]}
          />
          <Button
            variant="outline"
            className={styles.usersDateFilter}
            type="button"
            aria-label="注册时间筛选"
            onClick={() => setFiltersChanged(true)}
          >
            <span>Registered: Last 30 Days</span>
            <CalendarDays aria-hidden="true" />
          </Button>
          <Button
            variant="ghost"
            className={styles.usersResetFilter}
            type="button"
            onClick={() => {
              setQuery('');
              setRoleFilter('all');
              setStatusFilter('active');
              setFiltersChanged(false);
              setPage(1);
            }}
          >
            重置筛选
          </Button>
        </div>

        {loadError ? (
          <div className={styles.auditError} role="alert">
            <span>{loadError}</span>
            <Button variant="outline" size="sm" disabled={loading} onClick={() => setRetryNonce((value) => value + 1)}>
              <RefreshCw aria-hidden="true" />
              重试
            </Button>
          </div>
        ) : null}
        {!canManage ? (
          <div className={styles.readOnlyNotice} role="status">
            <ShieldCheck aria-hidden="true" />
            <span>当前为 operator，只能查看用户详情，状态和会话操作已禁用。</span>
          </div>
        ) : null}

        <div className={styles.usersTable} role="table" aria-label="用户列表">
          <div className={styles.usersTableHeader} role="row">
            <span role="columnheader">用户 ID</span>
            <span role="columnheader">用户名</span>
            <span role="columnheader">邮箱</span>
            <span role="columnheader">角色</span>
            <span role="columnheader">状态</span>
            <span role="columnheader">活跃会话</span>
            <span role="columnheader">操作</span>
          </div>
          {visibleUsers.map((user, index) => (
            <UserTableRow
              key={user.key}
              user={user}
              index={index}
              isSelected={selectedUser?.userId === user.userId}
              canWrite={canManage && user.role !== 'admin'}
              onSelect={() => setSelectedUser(user)}
              onStatus={(status, action) => requestUserStatus(user, action, status)}
              onRevoke={() => revokeSessions(user)}
            />
          ))}
          {!visibleUsers.length ? (
            <div className={styles.usersTableEmpty}>{loading ? '正在加载用户列表...' : '暂无匹配用户'}</div>
          ) : null}
        </div>

        <div className={styles.usersPagination}>
          <span>
            {isFigmaFixture
              ? 'Showing 1-4 of 1,284 users'
              : `显示第 ${displayedTotalUsers === 0 ? 0 : (page - 1) * pageSize + 1} 到 ${Math.min(page * pageSize, displayedTotalUsers)} 条，共 ${displayedTotalUsers.toLocaleString('zh-CN')} 条用户`}
          </span>
          <div>
            <Button
              variant="outline"
              size="sm"
              type="button"
              disabled={loading || page <= 1}
              aria-label="上一页"
              onClick={() => setPage((current) => Math.max(1, current - 1))}
            >
              上一页
            </Button>
            <Button
              variant="outline"
              size="sm"
              className={page === 1 ? styles.usersPageActive : undefined}
              type="button"
              aria-current={page === 1 ? 'page' : undefined}
              onClick={() => setPage(1)}
            >
              1
            </Button>
            {isFigmaFixture ? (
              <Button
                variant="outline"
                size="sm"
                className={page === 2 ? styles.usersPageActive : undefined}
                type="button"
                aria-label="第 2 页"
                aria-current={page === 2 ? 'page' : undefined}
                onClick={() => setPage(2)}
              >
                2
              </Button>
            ) : null}
            <Button
              variant="outline"
              size="sm"
              type="button"
              disabled={loading || page >= Math.max(1, Math.ceil(displayedTotalUsers / pageSize))}
              onClick={() =>
                setPage((current) => Math.min(Math.max(1, Math.ceil(displayedTotalUsers / pageSize)), current + 1))
              }
            >
              下一页
            </Button>
          </div>
        </div>
      </div>

      <aside className={styles.usersDetailColumn}>
        {selectedUser ? (
          <UserDetailCard
            user={selectedUser}
            detail={selectedDetail}
            detailLoading={detailLoading}
            detailError={detailError}
            figmaFixture={isFigmaFixture}
            onRetryDetail={() => setDetailRetryNonce((value) => value + 1)}
            onRevoke={() => revokeSessions(selectedUser)}
          />
        ) : (
          <Card className={styles.userDetailCard}>
            <div className={styles.emptyState}>
              <CircleUserRound aria-hidden="true" />
              <strong>暂无用户详情</strong>
              <span>{isMockMode ? '请选择用户查看详情。' : '详情接口尚未返回数据。'}</span>
            </div>
          </Card>
        )}
      </aside>
    </section>
  );
}

function FilterSelect({
  ariaLabel,
  value,
  onChange,
  options,
}: {
  ariaLabel: string;
  value: string;
  onChange: (value: string) => void;
  options: Array<[string, string]>;
}) {
  return (
    <Select value={value} onValueChange={onChange}>
      <SelectTrigger className={styles.usersSelect} aria-label={ariaLabel}>
        <SelectValue />
      </SelectTrigger>
      <SelectContent>
        {options.map(([optionValue, label]) => (
          <SelectItem key={optionValue} value={optionValue}>
            {label}
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  );
}

function UserTableRow({
  user,
  index,
  isSelected,
  canWrite,
  onSelect,
  onStatus,
  onRevoke,
}: {
  user: AdminUserView;
  index: number;
  isSelected: boolean;
  canWrite: boolean;
  onSelect: () => void;
  onStatus: (status: string, action: string) => void;
  onRevoke: () => void;
}) {
  const statusAction = user.status === 'active' ? 'locked' : 'active';
  const statusActionLabel = user.status === 'active' ? '锁定用户' : '启用用户';
  return (
    <div
      className={`${styles.usersTableRow} ${index === 3 ? styles.usersTableRowMuted : ''} ${isSelected ? styles.usersTableRowSelected : ''}`}
      role="row"
      aria-label={`${user.userId} ${user.username}`}
      onClick={onSelect}
    >
      <div className={styles.userIdCell} role="cell">
        <code>{user.userId}</code>
        <Button
          variant="ghost"
          size="icon"
          type="button"
          aria-label={`复制 ${user.userId}`}
          onClick={(event) => {
            event.stopPropagation();
            void navigator.clipboard?.writeText(user.userId);
          }}
        >
          <Copy aria-hidden="true" />
        </Button>
      </div>
      <strong role="cell">{user.username}</strong>
      <span className={styles.userEmailCell} role="cell">
        {user.email}
      </span>
      <span role="cell" className={styles.userTagCell}>
        <UserRoleTag role={user.role} />
      </span>
      <span role="cell" className={styles.userTagCell}>
        <UserStatusTag status={user.status} />
      </span>
      <span role="cell" className={styles.activeSessionsCell}>
        {user.activeSessions == null ? '-' : `${user.activeSessions} active`}
      </span>
      <div role="cell" className={styles.userRowMenu} onClick={(event) => event.stopPropagation()}>
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button variant="ghost" size="icon" type="button" aria-label={`${user.userId} 操作`}>
              <MoreHorizontal aria-hidden="true" />
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end" className={styles.userActionMenu}>
            <DropdownMenuItem onSelect={onSelect}>查看详情</DropdownMenuItem>
            <DropdownMenuSeparator />
            <DropdownMenuItem disabled={!canWrite} onSelect={() => onStatus(statusAction, statusActionLabel)}>
              {statusActionLabel}
            </DropdownMenuItem>
            <DropdownMenuItem disabled={!canWrite} onSelect={onRevoke}>
              撤销所有会话
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>
    </div>
  );
}

function UserRoleTag({ role }: { role: string }) {
  const label = role === 'admin' ? '管理员' : role === 'operator' ? '操作员' : '用户';
  return <span className={`${styles.userStatusTag} ${styles[`userRole${role}`]}`}>{label}</span>;
}

function UserStatusTag({ status }: { status: string }) {
  const label =
    status === 'active' ? '活跃' : status === 'disabled' ? '已禁用' : status === 'locked' ? '已锁定' : status;
  return <span className={`${styles.userStatusTag} ${styles[`userStatus${status}`]}`}>{label}</span>;
}

function UserDetailCard({
  user,
  detail,
  detailLoading,
  detailError,
  figmaFixture,
  onRetryDetail,
  onRevoke,
}: {
  user: AdminUserView;
  detail?: AdminUserDetail;
  detailLoading: boolean;
  detailError: string;
  figmaFixture: boolean;
  onRetryDetail: () => void;
  onRevoke: () => void;
}) {
  const profile = detail?.profile;
  const sessions = isMockMode
    ? adminUserSessionRows.filter((item) => item.userId === user.userId)
    : (detail?.login_sessions ?? []).map((item) => ({
        key: `session-${item.auth_session_id}`,
        userId: user.userId,
        device: item.user_agent || item.device_id || '-',
        ip: item.ip_address || '-',
        expiresAt: item.expires_at || '-',
        status: item.revoked_at ? 'revoked' : 'active',
      }));
  const businessSessions = isMockMode
    ? adminUserBusinessSessionRows.filter((item) => item.userId === user.userId)
    : (detail?.business_sessions.items ?? []).map((item) => ({
        key: `business-session-${item.session_id}`,
        userId: user.userId,
        sessionId: String(item.session_id),
        type: item.mode,
        title: item.title,
        status: item.status,
        lastActivityAt: item.last_message_at || '-',
      }));
  const operationHistory = isMockMode
    ? adminUserOperationHistoryRows.filter((item) => item.userId === user.userId)
    : (detail?.operation_history.items ?? []).map((item, index) => ({
        key: `user-history-${item.request_id || index}`,
        userId: user.userId,
        action: item.action,
        actor: item.operator_id == null ? '-' : String(item.operator_id),
        result: item.result,
        requestId: item.request_id,
        createdAt: item.created_at || '-',
      }));
  const displayName = profile?.display_name || user.displayName;
  const avatarSource = resolveAvatarUrl(user.avatarUrl, profile?.gender || user.gender);
  // Figma 用户详情使用登记的默认头像；真实用户详情仍允许展示后端上传头像。
  const isFixtureUser = isMockMode || user.key.startsWith('figma-');
  const canPreviewCredentialReset = isMockMode;

  return (
    <Card className={styles.userDetailCard}>
      <div className={styles.userDetailTitle}>
        <strong>用户详情</strong>
        <Button
          variant="ghost"
          size="icon"
          type="button"
          aria-label="关闭用户详情"
          data-figma-asset="admin-user-detail-close"
          onClick={() =>
            window.dispatchEvent(
              new CustomEvent('foodmate:admin-notice', { detail: { message: '详情面板保持打开以便对照用户信息。' } }),
            )
          }
        >
          <CircleX aria-hidden="true" />
        </Button>
      </div>
      <div className={styles.userDetailIdentity}>
        <div className={styles.userDetailAvatar} aria-hidden="true">
          <AvatarImage
            avatarUrl={avatarSource}
            allowUploaded={!isFixtureUser}
            defaultOnly={isFixtureUser}
            gender={profile?.gender || user.gender}
            alt=""
          />
        </div>
        <div className={styles.userDetailName}>
          <strong>{displayName}</strong>
          <span>{user.registeredLabel ?? `Registered ${user.createdAt}`}</span>
        </div>
        <UserRoleTag role={user.role} />
      </div>
      <Tabs defaultValue="profile" className={styles.userDetailTabsRoot}>
        <TabsList className={styles.userDetailTabsList} aria-label="用户详情分区">
          <TabsTrigger value="profile">资料</TabsTrigger>
          <TabsTrigger value="diet">饮食</TabsTrigger>
          <TabsTrigger value="login-sessions">{figmaFixture ? '会话' : '登录会话'}</TabsTrigger>
          <TabsTrigger value="history">历史</TabsTrigger>
          {!figmaFixture ? <TabsTrigger value="business-sessions">业务会话</TabsTrigger> : null}
        </TabsList>
        <TabsContent value="profile" className={styles.userDetailPanel}>
          <DetailGrid
            items={[
              ['账号 ID', user.userId],
              ['最近登录', user.lastLoginAt],
              ['饮食类型', user.dietGoal],
              ['自定义模型', user.customModel ?? '-'],
            ]}
          />
        </TabsContent>
        <TabsContent value="diet" className={styles.userDetailPanel}>
          <DetailSectionHeading icon={<Utensils aria-hidden="true" />} title="饮食画像" />
          <DetailGrid
            items={[
              ['性别', profile?.gender || user.gender],
              ['身高', profile?.height_cm ? `${profile.height_cm} cm` : user.heightCm ? `${user.heightCm} cm` : '-'],
              ['体重', profile?.weight_kg ? `${profile.weight_kg} kg` : user.weightKg ? `${user.weightKg} kg` : '-'],
              ['活动水平', profile?.activity_level || user.activityLevel],
              ['饮食目标', profile?.diet_goal || user.dietGoal],
              [
                '热量目标',
                profile?.calorie_target
                  ? `${profile.calorie_target} kcal`
                  : user.calorieTarget
                    ? `${user.calorieTarget} kcal`
                    : '-',
              ],
              [
                '蛋白质目标',
                profile?.protein_target
                  ? `${profile.protein_target} g`
                  : user.proteinTarget
                    ? `${user.proteinTarget} g`
                    : '-',
              ],
              ['过敏原', profile?.allergens || user.allergens],
              ['忌口', profile?.dislikes || user.dislikes],
              ['常用单位', profile?.preferred_units || user.preferredUnits],
            ]}
          />
        </TabsContent>
        <TabsContent value="login-sessions" className={styles.userDetailPanel}>
          <DetailSectionHeading icon={<Monitor aria-hidden="true" />} title={figmaFixture ? '会话' : '登录会话'} />
          <DetailTableState
            isMockMode={isMockMode}
            loading={detailLoading}
            error={detailError}
            onRetry={onRetryDetail}
            hasData={sessions.length > 0}
          >
            <DataTable columns={sessionColumns} data={sessions} />
          </DetailTableState>
        </TabsContent>
        <TabsContent value="history" className={styles.userDetailPanel}>
          <DetailSectionHeading icon={<History aria-hidden="true" />} title="操作历史" />
          <DetailTableState
            isMockMode={isMockMode}
            loading={detailLoading}
            error={detailError}
            onRetry={onRetryDetail}
            hasData={operationHistory.length > 0}
          >
            <DataTable columns={operationHistoryColumns} data={operationHistory} />
          </DetailTableState>
        </TabsContent>
        {!figmaFixture ? (
          <TabsContent value="business-sessions" className={styles.userDetailPanel}>
            <DetailSectionHeading icon={<Utensils aria-hidden="true" />} title="业务会话" />
            <DetailTableState
              isMockMode={isMockMode}
              loading={detailLoading}
              error={detailError}
              onRetry={onRetryDetail}
              hasData={businessSessions.length > 0}
            >
              <DataTable columns={businessSessionColumns} data={businessSessions} />
            </DetailTableState>
          </TabsContent>
        ) : null}
      </Tabs>
      <div className={styles.userDetailActions}>
        <Button
          variant="outline"
          className={styles.userCredentialButton}
          type="button"
          disabled={!canPreviewCredentialReset}
          aria-describedby={!canPreviewCredentialReset ? 'user-credential-reset-hint' : undefined}
          title={!canPreviewCredentialReset ? '后端当前未提供凭证重置接口' : undefined}
          onClick={
            canPreviewCredentialReset
              ? () =>
                  window.dispatchEvent(
                    new CustomEvent('foodmate:admin-notice', {
                      detail: { message: 'Fixture 仅展示凭证重置入口，真实接口尚未提供。' },
                    }),
                  )
              : undefined
          }
        >
          重置凭证
        </Button>
        {!canPreviewCredentialReset ? (
          <span id="user-credential-reset-hint" className={styles.userCredentialHint}>
            后端当前未提供凭证重置接口
          </span>
        ) : null}
        <Button variant="outline" className={styles.userRevokeButton} disabled={!canManage} onClick={onRevoke}>
          撤销所有会话
        </Button>
      </div>
      {!figmaFixture ? (
        <aside className={styles.userDetailGuidance} aria-label="用户详情 Tab">
          <h2>用户详情 Tab</h2>
          <p>资料 · 饮食画像 · 登录会话 · 业务会话 · 操作历史</p>
          <p>资料字段：注册时间 · 最近登录 · 账号状态 · 角色 · 活跃会话数</p>
          <p className={styles.userDetailGuidanceDanger}>
            禁用 / 锁定前显示影响：撤销会话、停止新运行、保留审计记录；admin 需二次确认。
          </p>
          <p className={styles.userDetailGuidanceMuted}>operator：只读；无启用、禁用、锁定和撤销全部会话权限。</p>
        </aside>
      ) : null}
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
  loading,
  error,
  onRetry,
  hasData,
  children,
}: {
  isMockMode: boolean;
  loading: boolean;
  error: string;
  onRetry: () => void;
  hasData: boolean;
  children: ReactNode;
}) {
  if (hasData) return children;
  if (loading) return <div className={styles.detailEmptyState}>正在加载详情...</div>;
  if (error) {
    return (
      <div className={styles.detailEmptyState} role="alert">
        <span>{error}</span>
        <Button variant="outline" size="sm" onClick={onRetry}>
          <RefreshCw aria-hidden="true" />
          重试
        </Button>
      </div>
    );
  }
  return (
    <div className={styles.detailEmptyState} role="status">
      <span>{mockMode ? '暂无记录' : '暂无记录'}</span>
    </div>
  );
}
