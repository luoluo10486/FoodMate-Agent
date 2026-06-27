import { Button, Card, Input, Message, Select, Table, Tag } from '@arco-design/web-react';
import type { TableColumnProps } from '@arco-design/web-react';
import type { ReactNode } from 'react';
import {
  IconApps,
  IconBook,
  IconDashboard,
  IconFile,
  IconHistory,
  IconHome,
  IconLeft,
  IconSafe,
  IconStorage,
  IconThunderbolt,
  IconTool,
  IconUser,
  IconUserGroup
} from '@arco-design/web-react/icon';
import { Link, NavLink, useLocation } from 'react-router-dom';
import { BrandLogo } from '../../components/brand/BrandLogo';
import {
  adminAuditRows,
  adminDeletedRows,
  adminKnowledgeRows,
  adminModelUsageRows,
  adminOverviewMetrics,
  adminResourceCards,
  adminToolRows,
  adminUserRows
} from '../../mock/admin';
import { mockAuthStatus, mockAuthUser } from '../../mock/auth';
import styles from './AdminPage.module.css';

type AuditRow = (typeof adminAuditRows)[number];
type UserRow = (typeof adminUserRows)[number];
type ToolRow = (typeof adminToolRows)[number];
type ModelUsageRow = (typeof adminModelUsageRows)[number];
type KnowledgeRow = (typeof adminKnowledgeRows)[number];
type DeletedRow = (typeof adminDeletedRows)[number];
type AdminSectionKey = 'overview' | 'users' | 'runs' | 'tools' | 'usage' | 'knowledge' | 'deleted';

const canAccessAdmin = mockAuthStatus === 'authenticated' && (mockAuthUser.role === 'admin' || mockAuthUser.role === 'operator');
const canManage = mockAuthStatus === 'authenticated' && mockAuthUser.role === 'admin';
const Option = Select.Option;

const adminNavItems: Array<{ key: AdminSectionKey; path: string; label: string; icon: ReactNode }> = [
  { key: 'overview', path: '/admin', label: '概览', icon: <IconDashboard /> },
  { key: 'users', path: '/admin/users', label: '用户管理', icon: <IconUserGroup /> },
  { key: 'runs', path: '/admin/runs', label: 'Agent 运行', icon: <IconThunderbolt /> },
  { key: 'tools', path: '/admin/tools', label: '工具调用', icon: <IconTool /> },
  { key: 'usage', path: '/admin/usage', label: '模型用量', icon: <IconStorage /> },
  { key: 'knowledge', path: '/admin/knowledge', label: '知识库', icon: <IconBook /> },
  { key: 'deleted', path: '/admin/deleted', label: '软删除资源', icon: <IconHistory /> }
];

const sectionMeta: Record<AdminSectionKey, { title: string; description: string; tag: string }> = {
  overview: { title: '系统概览', description: '运行、用户、工具、模型和知识库索引状态。当前为 mock 管理视图。', tag: 'Overview' },
  users: { title: '用户管理', description: '查询用户、角色、状态和最近登录；第一版仅 admin 可执行状态变更。', tag: 'RBAC' },
  runs: { title: 'Agent 运行', description: '查看 AgentRun、ToolCall 和 Trace，定位失败任务和异常链路。', tag: 'AgentRun' },
  tools: { title: '工具调用', description: '治理工具注册表、版本、权限范围、风险等级和启停状态。', tag: 'Tools' },
  usage: { title: '模型用量', description: '查看供应商、模型、场景、token、成本和耗时。', tag: 'Model Usage' },
  knowledge: { title: '知识库', description: '管理知识库文档、解析状态、索引进度和下线恢复。', tag: 'Knowledge' },
  deleted: { title: '软删除资源', description: '查看已删除业务资源，并由 admin 执行恢复操作。', tag: 'Recovery' }
};

const auditColumns: TableColumnProps<AuditRow>[] = [
  { title: 'Run', dataIndex: 'runId' },
  { title: '用户', dataIndex: 'user' },
  { title: '意图', dataIndex: 'intent' },
  {
    title: '状态',
    dataIndex: 'status',
    render: (status) => <Tag color={status === 'completed' ? 'green' : 'orange'}>{status}</Tag>
  },
  { title: 'Trace', dataIndex: 'traceId' }
];

const userColumns: TableColumnProps<UserRow>[] = [
  { title: '用户名', dataIndex: 'username' },
  { title: '展示名', dataIndex: 'displayName' },
  {
    title: '角色',
    dataIndex: 'role',
    render: (role) => <Tag color={role === 'admin' ? 'arcoblue' : role === 'operator' ? 'orange' : 'gray'}>{role}</Tag>
  },
  {
    title: '状态',
    dataIndex: 'status',
    render: (status) => <Tag color={status === 'active' ? 'green' : 'red'}>{status}</Tag>
  },
  { title: '最近登录', dataIndex: 'lastLoginAt' },
  {
    title: '操作',
    render: (_, record) => (
      <Button size="mini" disabled={!canManage || record.role === 'admin'} onClick={() => Message.info('用户状态变更为 mock 操作')}>
        锁定
      </Button>
    )
  }
];

const toolColumns: TableColumnProps<ToolRow>[] = [
  { title: '工具名', dataIndex: 'name' },
  { title: '版本', dataIndex: 'version' },
  { title: '范围', dataIndex: 'scope' },
  {
    title: '风险',
    dataIndex: 'risk',
    render: (risk) => <Tag color={risk === 'high' ? 'red' : risk === 'medium' ? 'orange' : 'green'}>{risk}</Tag>
  },
  {
    title: '状态',
    dataIndex: 'status',
    render: (status) => <Tag color={status === 'active' ? 'green' : 'gray'}>{status}</Tag>
  },
  {
    title: '操作',
    render: () => (
      <Button size="mini" disabled={!canManage} onClick={() => Message.info('工具启停为 mock 操作')}>
        启停
      </Button>
    )
  }
];

const modelUsageColumns: TableColumnProps<ModelUsageRow>[] = [
  { title: '供应商', dataIndex: 'provider' },
  { title: '模型', dataIndex: 'model' },
  { title: '场景', dataIndex: 'scene' },
  { title: 'Tokens', dataIndex: 'tokens' },
  { title: '成本', dataIndex: 'cost' },
  { title: '耗时 ms', dataIndex: 'latencyMs' },
  {
    title: '状态',
    dataIndex: 'status',
    render: (status) => <Tag color={status === 'success' ? 'green' : 'orange'}>{status}</Tag>
  }
];

const knowledgeColumns: TableColumnProps<KnowledgeRow>[] = [
  { title: '文档', dataIndex: 'title' },
  {
    title: '状态',
    dataIndex: 'status',
    render: (status) => <Tag color={status === 'indexed' ? 'green' : status === 'failed' ? 'red' : 'orange'}>{status}</Tag>
  },
  { title: 'Chunks', dataIndex: 'chunks' },
  { title: '负责人', dataIndex: 'owner' },
  { title: '更新时间', dataIndex: 'updatedAt' },
  {
    title: '操作',
    render: () => (
      <Button size="mini" disabled={!canManage} onClick={() => Message.info('文档状态变更为 mock 操作')}>
        下线
      </Button>
    )
  }
];

const deletedColumns: TableColumnProps<DeletedRow>[] = [
  { title: '资源类型', dataIndex: 'resourceType' },
  { title: '资源 ID', dataIndex: 'resourceId' },
  { title: '归属', dataIndex: 'owner' },
  { title: '删除时间', dataIndex: 'deletedAt' },
  { title: '原因', dataIndex: 'reason' },
  {
    title: '操作',
    render: () => (
      <Button size="mini" disabled={!canManage} onClick={() => Message.info('恢复资源为 mock 操作')}>
        恢复
      </Button>
    )
  }
];

function getSectionKey(pathname: string): AdminSectionKey {
  if (pathname.endsWith('/users')) return 'users';
  if (pathname.endsWith('/runs')) return 'runs';
  if (pathname.endsWith('/tools')) return 'tools';
  if (pathname.endsWith('/usage')) return 'usage';
  if (pathname.endsWith('/knowledge')) return 'knowledge';
  if (pathname.endsWith('/deleted')) return 'deleted';
  return 'overview';
}

function AdminHeader({ sectionKey }: { sectionKey: AdminSectionKey }) {
  const meta = sectionMeta[sectionKey];

  return (
    <section className={styles.header}>
      <div>
        <h1>{meta.title}</h1>
        <p>{meta.description}</p>
      </div>
      <Tag color="arcoblue">{meta.tag}</Tag>
    </section>
  );
}

function AdminFilters({ placeholder = 'traceId / userId' }: { placeholder?: string }) {
  return (
    <section className={styles.filters}>
      <strong>筛选</strong>
      <Select className={styles.filterControl} size="small" defaultValue="all" triggerProps={{ autoAlignPopupWidth: false }}>
        <Option value="all">全部状态</Option>
        <Option value="completed">completed</Option>
        <Option value="failed">failed</Option>
      </Select>
      <Select className={styles.filterControl} size="small" defaultValue="24h" triggerProps={{ autoAlignPopupWidth: false }}>
        <Option value="24h">近 24h</Option>
        <Option value="7d">近 7 天</Option>
        <Option value="30d">近 30 天</Option>
      </Select>
      <Input className={styles.filterInput} size="small" placeholder={placeholder} allowClear />
      <Button type="primary" onClick={() => Message.info('筛选为 mock 操作')}>
        查询
      </Button>
    </section>
  );
}

function OverviewSection() {
  return (
    <>
      <section className={styles.metrics}>
        {adminOverviewMetrics.map((metric) => (
          <article className={`${styles.metric} ${styles[metric.tone]}`} key={metric.label}>
            <span>{metric.label}</span>
            <strong>{metric.value}</strong>
            <em>{metric.hint}</em>
          </article>
        ))}
      </section>

      <AdminFilters />

      <section className={styles.body}>
        <Card className={styles.auditCard} bordered={false}>
          <div className={styles.cardHead}>
            <strong>运行审计</strong>
            <Tag color="arcoblue">AgentRun / ToolCall / Trace</Tag>
          </div>
          <Table columns={auditColumns} data={adminAuditRows} pagination={{ pageSize: 5, total: adminAuditRows.length }} size="small" />
        </Card>

        <aside className={styles.side}>
          <AdminActionsCard />
          <GovernanceResourceCard />
        </aside>
      </section>

      <Card className={styles.userCard} bordered={false}>
        <div className={styles.cardHead}>
          <strong>用户管理</strong>
          <Tag color="orange">仅 admin 可修改状态</Tag>
        </div>
        <Table columns={userColumns} data={adminUserRows} pagination={{ pageSize: 5, total: adminUserRows.length }} size="small" />
      </Card>

      <section className={styles.moduleGrid}>
        <Link to="/admin/knowledge">
          <IconFile />
          <strong>知识库文档</strong>
          <span>上传、下线、恢复和索引状态将在真实接口接入后开放。</span>
        </Link>
        <Link to="/admin/tools">
          <IconTool />
          <strong>工具注册表</strong>
          <span>工具版本、风险等级、权限范围和启停状态统一在后台治理。</span>
        </Link>
        <Link to="/admin/usage">
          <IconStorage />
          <strong>模型用量</strong>
          <span>查看供应商、模型、token、耗时和成本趋势。</span>
        </Link>
      </section>
    </>
  );
}

function UsersSection() {
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
          <Card className={styles.card} bordered={false}>
            <strong>账号治理策略</strong>
            <div className={styles.noteList}>
              <span>禁用或锁定用户不能登录。</span>
              <span>管理员不能在个人资料页修改自己的 role/status。</span>
              <span>重置会话会撤销 Refresh Token。</span>
            </div>
          </Card>
          <AdminActionsCard />
        </aside>
      </section>
    </>
  );
}

function RunsSection() {
  return (
    <>
      <section className={styles.sectionCards}>
        <MiniStat label="今日运行" value="1,284" hint="+12%" />
        <MiniStat label="失败率" value="2.1%" hint="近 24h" tone="danger" />
        <MiniStat label="平均耗时" value="860ms" hint="p50" tone="blue" />
      </section>
      <AdminFilters />
      <Card className={styles.wideCard} bordered={false}>
        <div className={styles.cardHead}>
          <strong>运行审计</strong>
          <Tag color="arcoblue">支持 status / traceId / userId</Tag>
        </div>
        <Table columns={auditColumns} data={adminAuditRows} pagination={{ pageSize: 5, total: adminAuditRows.length }} size="small" />
      </Card>
    </>
  );
}

function ToolsSection() {
  return (
    <>
      <AdminFilters placeholder="toolName / risk / scope" />
      <Card className={styles.wideCard} bordered={false}>
        <div className={styles.cardHead}>
          <strong>工具注册表</strong>
          <Tag color="red">高风险工具仅 admin 可停用</Tag>
        </div>
        <Table columns={toolColumns} data={adminToolRows} pagination={{ pageSize: 6, total: adminToolRows.length }} size="small" />
      </Card>
    </>
  );
}

function UsageSection() {
  return (
    <>
      <section className={styles.sectionCards}>
        <MiniStat label="今日成本" value="¥86.4" hint="+8%" tone="orange" />
        <MiniStat label="Tokens" value="514k" hint="近 24h" />
        <MiniStat label="Fallback" value="3" hint="供应商切换" tone="danger" />
      </section>
      <AdminFilters placeholder="provider / model / scene" />
      <Card className={styles.wideCard} bordered={false}>
        <div className={styles.cardHead}>
          <strong>模型调用明细</strong>
          <Tag color="arcoblue">成本和延迟治理</Tag>
        </div>
        <Table columns={modelUsageColumns} data={adminModelUsageRows} pagination={{ pageSize: 5, total: adminModelUsageRows.length }} size="small" />
      </Card>
    </>
  );
}

function KnowledgeSection() {
  return (
    <>
      <AdminFilters placeholder="documentId / title / owner" />
      <Card className={styles.wideCard} bordered={false}>
        <div className={styles.cardHead}>
          <strong>知识库文档</strong>
          <Button type="primary" disabled={!canManage} onClick={() => Message.info('上传文档为 mock 操作')}>
            上传文档
          </Button>
        </div>
        <Table columns={knowledgeColumns} data={adminKnowledgeRows} pagination={{ pageSize: 5, total: adminKnowledgeRows.length }} size="small" />
      </Card>
    </>
  );
}

function DeletedSection() {
  return (
    <>
      <AdminFilters placeholder="resourceType / resourceId / userId" />
      <Card className={styles.wideCard} bordered={false}>
        <div className={styles.cardHead}>
          <strong>软删除资源</strong>
          <Tag color="red">恢复仅 admin 可用</Tag>
        </div>
        <Table columns={deletedColumns} data={adminDeletedRows} pagination={{ pageSize: 5, total: adminDeletedRows.length }} size="small" />
      </Card>
    </>
  );
}

function MiniStat({ label, value, hint, tone = 'green' }: { label: string; value: string; hint: string; tone?: string }) {
  return (
    <article className={`${styles.metric} ${styles[tone]}`}>
      <span>{label}</span>
      <strong>{value}</strong>
      <em>{hint}</em>
    </article>
  );
}

function AdminActionsCard() {
  return (
    <Card className={styles.card} bordered={false}>
      <strong>管理操作</strong>
      <div className={styles.actionGrid}>
        <Button disabled={!canManage} onClick={() => Message.info('禁用用户为 mock 操作')}>
          禁用用户
        </Button>
        <Button disabled={!canManage} onClick={() => Message.info('重置会话为 mock 操作')}>
          重置会话
        </Button>
        <Button disabled={!canManage} onClick={() => Message.info('工具启停为 mock 操作')}>
          工具启停
        </Button>
        <Button disabled={!canManage} onClick={() => Message.info('恢复资源为 mock 操作')}>
          恢复资源
        </Button>
      </div>
      <p>高风险操作必须二次确认并写审计。operator 只能查看，不可执行。</p>
    </Card>
  );
}

function GovernanceResourceCard() {
  return (
    <Card className={styles.card} bordered={false}>
      <strong>治理资源</strong>
      <div className={styles.resourceList}>
        {adminResourceCards.map((item) => (
          <article key={item.title}>
            <span>{item.title}</span>
            <strong>{item.value}</strong>
            <em>{item.detail}</em>
          </article>
        ))}
      </div>
    </Card>
  );
}

function renderSection(sectionKey: AdminSectionKey) {
  if (sectionKey === 'users') return <UsersSection />;
  if (sectionKey === 'runs') return <RunsSection />;
  if (sectionKey === 'tools') return <ToolsSection />;
  if (sectionKey === 'usage') return <UsageSection />;
  if (sectionKey === 'knowledge') return <KnowledgeSection />;
  if (sectionKey === 'deleted') return <DeletedSection />;
  return <OverviewSection />;
}

export function AdminPage() {
  const { pathname } = useLocation();
  const sectionKey = getSectionKey(pathname);

  if (!canAccessAdmin) {
    return (
      <div className={styles.authShell}>
        <Card className={styles.noAccessCard} bordered={false}>
          <Tag color="red">AUTH_FORBIDDEN</Tag>
          <h1>无权访问管理后台</h1>
          <p>管理后台仅对 admin/operator 开放。普通用户不会看到入口。</p>
          <Link to="/">
            <Button icon={<IconLeft />}>返回工作台</Button>
          </Link>
        </Card>
      </div>
    );
  }

  return (
    <div className={styles.adminShell}>
      <aside className={styles.adminSidebar}>
        <div className={styles.brandBlock}>
          <BrandLogo />
          <Tag color="green">Admin Console</Tag>
        </div>

        <Link className={styles.workspaceLink} to="/">
          <IconHome />
          <span>返回 Agent 工作台</span>
        </Link>

        <nav className={styles.adminNav} aria-label="管理后台导航">
          {adminNavItems.map((item) => (
            <NavLink className={({ isActive }) => `${styles.navButton} ${isActive ? styles.navButtonActive : ''}`} end={item.path === '/admin'} key={item.key} to={item.path}>
              {item.icon}
              <span>{item.label}</span>
            </NavLink>
          ))}
        </nav>

        <div className={styles.policyBox}>
          <IconSafe />
          <strong>{canManage ? 'admin 可操作' : 'operator 只读'}</strong>
          <span>高风险操作需要二次确认并写入审计。</span>
        </div>
      </aside>

      <main className={styles.adminMain}>
        <header className={styles.topbar}>
          <div className={styles.topbarTitle}>
            <IconApps />
            <span>FoodMate 管理后台</span>
          </div>
          <div className={styles.topbarActions}>
            <Tag color={canManage ? 'green' : 'orange'}>{canManage ? 'admin 可操作' : 'operator 只读'}</Tag>
            <Link to="/profile">
              <Button icon={<IconUser />}>{mockAuthUser.displayName}</Button>
            </Link>
          </div>
        </header>

        <div className={`${styles.page} fm-enter`}>
          <AdminHeader sectionKey={sectionKey} />
          {renderSection(sectionKey)}
        </div>
      </main>
    </div>
  );
}
