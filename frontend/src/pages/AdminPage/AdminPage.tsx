import { Button, Card, Input, Message, Modal, Select, Table, Tabs, Tag } from '@arco-design/web-react';
import type { TableColumnProps } from '@arco-design/web-react';
import type { ReactNode } from 'react';
import { useState } from 'react';
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
  adminOperationAuditRows,
  adminOverviewMetrics,
  adminResourceCards,
  adminSqlAuditRows,
  adminToolCallRows,
  adminToolRows,
  adminTraceRows,
  adminUserRows,
  adminUserSessionRows
} from '../../mock/admin';
import { mockAuthStatus, mockAuthUser } from '../../mock/auth';
import styles from './AdminPage.module.css';

type AuditRow = (typeof adminAuditRows)[number];
type ToolCallRow = (typeof adminToolCallRows)[number];
type SqlAuditRow = (typeof adminSqlAuditRows)[number];
type TraceRow = (typeof adminTraceRows)[number];
type UserRow = (typeof adminUserRows)[number];
type UserSessionRow = (typeof adminUserSessionRows)[number];
type ToolRow = (typeof adminToolRows)[number];
type ModelUsageRow = (typeof adminModelUsageRows)[number];
type KnowledgeRow = (typeof adminKnowledgeRows)[number];
type DeletedRow = (typeof adminDeletedRows)[number];
type OperationAuditRow = (typeof adminOperationAuditRows)[number];
type AdminSectionKey = 'overview' | 'users' | 'runs' | 'tools' | 'usage' | 'knowledge' | 'deleted';

const canAccessAdmin = mockAuthStatus === 'authenticated' && (mockAuthUser.role === 'admin' || mockAuthUser.role === 'operator');
const canManage = mockAuthStatus === 'authenticated' && mockAuthUser.role === 'admin';
const Option = Select.Option;
const TabPane = Tabs.TabPane;

const adminNavItems: Array<{ key: AdminSectionKey; path: string; label: string; icon: ReactNode; adminOnly?: boolean }> = [
  { key: 'overview', path: '/admin', label: '概览', icon: <IconDashboard /> },
  { key: 'users', path: '/admin/users', label: '用户管理', icon: <IconUserGroup />, adminOnly: true },
  { key: 'runs', path: '/admin/runs', label: 'Agent 运行', icon: <IconThunderbolt /> },
  { key: 'tools', path: '/admin/tools', label: '工具调用', icon: <IconTool /> },
  { key: 'usage', path: '/admin/usage', label: '模型用量', icon: <IconStorage /> },
  { key: 'knowledge', path: '/admin/knowledge', label: '知识库', icon: <IconBook /> },
  { key: 'deleted', path: '/admin/deleted', label: '软删除资源', icon: <IconHistory />, adminOnly: true }
];

const sectionMeta: Record<AdminSectionKey, { title: string; description: string; tag: string }> = {
  overview: { title: '系统概览', description: '运行、用户、工具、模型和知识库索引状态。当前为 mock 管理视图。', tag: 'Overview' },
  users: { title: '用户管理', description: '查询用户详情、登录会话、角色和状态；状态变更与会话重置仅 admin 可执行。', tag: 'RBAC' },
  runs: { title: 'Agent 运行', description: '查看 AgentRun、ToolCall、SQLAudit 和 Trace，定位失败任务和异常链路。', tag: 'AgentRun' },
  tools: { title: '工具调用', description: '治理工具注册表、版本、权限范围、风险等级和启停状态。', tag: 'Tools' },
  usage: { title: '模型用量', description: '查看供应商、模型、场景、token、成本和耗时。', tag: 'Model Usage' },
  knowledge: { title: '知识库', description: '管理知识库文档、解析状态、索引进度和下线恢复。', tag: 'Knowledge' },
  deleted: { title: '软删除资源', description: '查看已删除业务资源，并由 admin 执行恢复操作。', tag: 'Recovery' }
};

function statusTag(status: string) {
  const color = status === 'active' || status === 'success' || status === 'completed' || status === 'indexed' ? 'green' : status === 'failed' || status === 'disabled' ? 'red' : 'orange';
  return <Tag color={color}>{status}</Tag>;
}

function roleTag(role: string) {
  return <Tag color={role === 'admin' ? 'arcoblue' : role === 'operator' ? 'orange' : 'gray'}>{role}</Tag>;
}

function riskTag(risk: string) {
  return <Tag color={risk === 'high' ? 'red' : risk === 'medium' ? 'orange' : 'green'}>{risk}</Tag>;
}

function confirmAdminAction(action: string, target: string) {
  if (!canManage) {
    Message.warning('operator 只读，不能执行管理写操作');
    return;
  }

  Modal.confirm({
    title: action,
    content: `确认对 ${target} 执行该管理操作？真实接入后会携带 requestId / traceId 并写入审计表。`,
    okText: '确认执行',
    cancelText: '取消',
    onOk: () => {
      Message.success(`${action} 已提交，mock 审计记录已更新`);
    }
  });
}

const auditColumns: TableColumnProps<AuditRow>[] = [
  { title: 'Run', dataIndex: 'runId' },
  { title: '用户', dataIndex: 'user' },
  { title: '意图', dataIndex: 'intent' },
  { title: '状态', dataIndex: 'status', render: statusTag },
  { title: '耗时 ms', dataIndex: 'durationMs' },
  { title: 'Trace', dataIndex: 'traceId' }
];

const toolCallColumns: TableColumnProps<ToolCallRow>[] = [
  { title: 'Call ID', dataIndex: 'callId' },
  { title: 'Run', dataIndex: 'runId' },
  { title: '工具', dataIndex: 'toolName' },
  { title: '状态', dataIndex: 'status', render: statusTag },
  { title: '耗时 ms', dataIndex: 'latencyMs' },
  { title: 'Trace', dataIndex: 'traceId' }
];

const sqlAuditColumns: TableColumnProps<SqlAuditRow>[] = [
  { title: 'Audit ID', dataIndex: 'auditId' },
  { title: '执行方', dataIndex: 'actor' },
  { title: '语句摘要', dataIndex: 'statement' },
  { title: '风险', dataIndex: 'risk', render: riskTag },
  { title: '结果', dataIndex: 'result' },
  { title: 'Trace', dataIndex: 'traceId' }
];

const traceColumns: TableColumnProps<TraceRow>[] = [
  { title: 'Trace', dataIndex: 'traceId' },
  { title: '链路', dataIndex: 'entry' },
  { title: '状态', dataIndex: 'status', render: statusTag },
  { title: '开始时间', dataIndex: 'startedAt' }
];

const modelUsageColumns: TableColumnProps<ModelUsageRow>[] = [
  { title: '供应商', dataIndex: 'provider' },
  { title: '模型', dataIndex: 'model' },
  { title: '场景', dataIndex: 'scene' },
  { title: 'Tokens', dataIndex: 'tokens' },
  { title: '成本', dataIndex: 'cost' },
  { title: '耗时 ms', dataIndex: 'latencyMs' },
  { title: '状态', dataIndex: 'status', render: statusTag }
];

const operationAuditColumns: TableColumnProps<OperationAuditRow>[] = [
  { title: '操作人', dataIndex: 'operator' },
  { title: '动作', dataIndex: 'action' },
  { title: '目标', render: (_, record) => `${record.targetType}:${record.targetId}` },
  { title: '结果', dataIndex: 'result', render: statusTag },
  { title: 'Request', dataIndex: 'requestId' },
  { title: 'Trace', dataIndex: 'traceId' }
];

const sessionColumns: TableColumnProps<UserSessionRow>[] = [
  { title: '设备', dataIndex: 'device' },
  { title: 'IP', dataIndex: 'ip' },
  { title: '过期时间', dataIndex: 'expiresAt' },
  { title: '状态', dataIndex: 'status', render: statusTag }
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
        <Option value="active">active</Option>
        <Option value="completed">completed</Option>
        <Option value="failed">failed</Option>
      </Select>
      <Select className={styles.filterControl} size="small" defaultValue="24h" triggerProps={{ autoAlignPopupWidth: false }}>
        <Option value="24h">近 24h</Option>
        <Option value="7d">近 7 天</Option>
        <Option value="30d">近 30 天</Option>
      </Select>
      <Input className={styles.filterInput} size="small" placeholder={placeholder} allowClear />
      <Button type="primary" onClick={() => Message.info('筛选为 mock 操作；真实接入会映射 status / createdAt / traceId / userId')}>
        查询
      </Button>
    </section>
  );
}

function AdminOnlyNotice({ title }: { title: string }) {
  return (
    <Card className={styles.noAccessCard} bordered={false}>
      <Tag color="red">ADMIN_ONLY</Tag>
      <h1>{title}</h1>
      <p>该页面包含用户敏感信息或恢复类高风险能力，按后端接口契约仅 admin 可访问。</p>
      <Link to="/admin">
        <Button icon={<IconLeft />}>返回概览</Button>
      </Link>
    </Card>
  );
}

function OperationAuditCard() {
  return (
    <Card className={styles.wideCard} bordered={false}>
      <div className={styles.cardHead}>
        <strong>管理操作审计</strong>
        <Tag color="arcoblue">operator / target / requestId / traceId</Tag>
      </div>
      <Table columns={operationAuditColumns} data={adminOperationAuditRows} pagination={{ pageSize: 4, total: adminOperationAuditRows.length }} size="small" />
    </Card>
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

      {canManage ? <OperationAuditCard /> : null}

      <section className={styles.moduleGrid}>
        <Link to="/admin/runs">
          <IconThunderbolt />
          <strong>运行链路</strong>
          <span>查看 AgentRun、ToolCall、SQLAudit 和 Trace。</span>
        </Link>
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
      </section>
    </>
  );
}

function UsersSection() {
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
          <Button size="mini" onClick={() => setSelectedUser(record)}>
            查看
          </Button>
          <Button size="mini" disabled={record.role === 'admin'} onClick={() => confirmAdminAction('锁定用户', record.userId)}>
            锁定
          </Button>
          <Button size="mini" disabled={record.role === 'admin'} onClick={() => confirmAdminAction('禁用用户', record.userId)}>
            禁用
          </Button>
          <Button size="mini" disabled={record.role === 'admin'} onClick={() => confirmAdminAction('重置会话', record.userId)}>
            重置会话
          </Button>
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
          <AdminActionsCard />
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
          <strong>运行治理</strong>
          <Tag color="arcoblue">AgentRun / ToolCall / SQLAudit / Trace</Tag>
        </div>
        <Tabs defaultActiveTab="agent-runs">
          <TabPane key="agent-runs" title="AgentRun">
            <Table columns={auditColumns} data={adminAuditRows} pagination={{ pageSize: 5, total: adminAuditRows.length }} size="small" />
          </TabPane>
          <TabPane key="tool-calls" title="ToolCall">
            <Table columns={toolCallColumns} data={adminToolCallRows} pagination={{ pageSize: 5, total: adminToolCallRows.length }} size="small" />
          </TabPane>
          <TabPane key="sql-audits" title="SQLAudit">
            <Table columns={sqlAuditColumns} data={adminSqlAuditRows} pagination={{ pageSize: 5, total: adminSqlAuditRows.length }} size="small" />
          </TabPane>
          <TabPane key="traces" title="Trace">
            <Table columns={traceColumns} data={adminTraceRows} pagination={{ pageSize: 5, total: adminTraceRows.length }} size="small" />
          </TabPane>
        </Tabs>
      </Card>
    </>
  );
}

function ToolsSection() {
  const [selectedTool, setSelectedTool] = useState<ToolRow>(adminToolRows[0]);

  const toolColumns: TableColumnProps<ToolRow>[] = [
    { title: '工具名', dataIndex: 'name' },
    { title: '版本', dataIndex: 'version' },
    { title: '范围', dataIndex: 'scope' },
    { title: '风险', dataIndex: 'risk', render: riskTag },
    { title: '状态', dataIndex: 'status', render: statusTag },
    {
      title: '操作',
      render: (_, record) => (
        <div className={styles.rowActions}>
          <Button size="mini" onClick={() => setSelectedTool(record)}>
            详情
          </Button>
          <Button size="mini" disabled={!canManage} onClick={() => confirmAdminAction(record.status === 'active' ? '停用工具' : '启用工具', record.name)}>
            启停
          </Button>
        </div>
      )
    }
  ];

  return (
    <>
      <AdminFilters placeholder="toolName / risk / scope" />
      <section className={styles.sectionLayout}>
        <Card className={styles.wideCard} bordered={false}>
          <div className={styles.cardHead}>
            <strong>工具注册表</strong>
            <Tag color="red">高风险工具仅 admin 可停用</Tag>
          </div>
          <Table columns={toolColumns} data={adminToolRows} pagination={{ pageSize: 6, total: adminToolRows.length }} size="small" />
        </Card>
        <aside className={styles.side}>
          <ToolDetailCard tool={selectedTool} />
          <OperationAuditCard />
        </aside>
      </section>
    </>
  );
}

function ToolDetailCard({ tool }: { tool: ToolRow }) {
  return (
    <Card className={styles.card} bordered={false}>
      <div className={styles.cardHead}>
        <strong>工具详情</strong>
        {riskTag(tool.risk)}
      </div>
      <div className={styles.detailGrid}>
        <span>名称</span>
        <strong>{tool.name}</strong>
        <span>版本</span>
        <strong>{tool.version}</strong>
        <span>负责人域</span>
        <strong>{tool.owner}</strong>
        <span>可用范围</span>
        <strong>{tool.scope}</strong>
        <span>入参 schema</span>
        <strong>{tool.schema}</strong>
        <span>最近调用</span>
        <strong>{tool.lastCalledAt}</strong>
      </div>
    </Card>
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
  const [selectedDoc, setSelectedDoc] = useState<KnowledgeRow>(adminKnowledgeRows[0]);
  const [uploadVisible, setUploadVisible] = useState(false);

  const knowledgeColumns: TableColumnProps<KnowledgeRow>[] = [
    { title: '文档 ID', dataIndex: 'documentId' },
    { title: '文档', dataIndex: 'title' },
    { title: '状态', dataIndex: 'status', render: statusTag },
    { title: 'Chunks', dataIndex: 'chunks' },
    { title: '索引进度', dataIndex: 'indexProgress' },
    { title: '负责人', dataIndex: 'owner' },
    { title: '更新时间', dataIndex: 'updatedAt' },
    {
      title: '操作',
      render: (_, record) => (
        <div className={styles.rowActions}>
          <Button size="mini" onClick={() => setSelectedDoc(record)}>
            详情
          </Button>
          <Button size="mini" disabled={!canManage} onClick={() => confirmAdminAction(record.status === 'indexed' ? '下线文档' : '恢复文档', record.documentId)}>
            {record.status === 'indexed' ? '下线' : '恢复'}
          </Button>
        </div>
      )
    }
  ];

  return (
    <>
      <AdminFilters placeholder="documentId / title / owner" />
      <section className={styles.sectionLayout}>
        <Card className={styles.wideCard} bordered={false}>
          <div className={styles.cardHead}>
            <strong>知识库文档</strong>
            <Button type="primary" disabled={!canManage} onClick={() => setUploadVisible(true)}>
              上传文档
            </Button>
          </div>
          <Table columns={knowledgeColumns} data={adminKnowledgeRows} pagination={{ pageSize: 5, total: adminKnowledgeRows.length }} size="small" />
        </Card>
        <aside className={styles.side}>
          <KnowledgeDetailCard document={selectedDoc} />
          <OperationAuditCard />
        </aside>
      </section>
      <Modal
        title="上传知识库文档"
        visible={uploadVisible}
        okText="提交 mock 上传"
        cancelText="取消"
        onCancel={() => setUploadVisible(false)}
        onOk={() => {
          setUploadVisible(false);
          Message.success('文档上传已提交；真实接入会写 MinIO 对象和索引任务');
        }}
      >
        <div className={styles.uploadMock}>
          <strong>选择文件</strong>
          <span>支持 PDF / Markdown / Excel，真实接入后限制大小、类型并记录上传人。</span>
          <Input placeholder="nutrition-guide.pdf" />
          <Input.TextArea placeholder="索引备注 / 标签" />
        </div>
      </Modal>
    </>
  );
}

function KnowledgeDetailCard({ document }: { document: KnowledgeRow }) {
  return (
    <Card className={styles.card} bordered={false}>
      <div className={styles.cardHead}>
        <strong>文档详情</strong>
        {statusTag(document.status)}
      </div>
      <div className={styles.detailGrid}>
        <span>文档 ID</span>
        <strong>{document.documentId}</strong>
        <span>来源</span>
        <strong>{document.source}</strong>
        <span>索引进度</span>
        <strong>{document.indexProgress}</strong>
        <span>切片数</span>
        <strong>{document.chunks}</strong>
      </div>
    </Card>
  );
}

function DeletedSection() {
  if (!canManage) return <AdminOnlyNotice title="无权访问软删除资源" />;

  const deletedColumns: TableColumnProps<DeletedRow>[] = [
    { title: '资源类型', dataIndex: 'resourceType' },
    { title: '资源 ID', dataIndex: 'resourceId' },
    { title: '归属', dataIndex: 'owner' },
    { title: '删除时间', dataIndex: 'deletedAt' },
    { title: '原因', dataIndex: 'reason' },
    {
      title: '操作',
      render: (_, record) => (
        <Button size="mini" onClick={() => confirmAdminAction('恢复软删除资源', `${record.resourceType}:${record.resourceId}`)}>
          恢复
        </Button>
      )
    }
  ];

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
      <OperationAuditCard />
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
        <Button disabled={!canManage} onClick={() => confirmAdminAction('禁用用户', 'user_10002')}>
          禁用用户
        </Button>
        <Button disabled={!canManage} onClick={() => confirmAdminAction('重置会话', 'user_10002')}>
          重置会话
        </Button>
        <Button disabled={!canManage} onClick={() => confirmAdminAction('工具启停', 'food_log_writer')}>
          工具启停
        </Button>
        <Button disabled={!canManage} onClick={() => confirmAdminAction('恢复资源', 'meal_plan_73')}>
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
            <NavLink className={({ isActive }) => `${styles.navButton} ${item.adminOnly && !canManage ? styles.navButtonLocked : ''} ${isActive ? styles.navButtonActive : ''}`} end={item.path === '/admin'} key={item.key} to={item.path}>
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
