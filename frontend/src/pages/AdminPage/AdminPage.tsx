import { Button, Card, Input, Message, Select, Table, Tag } from '@arco-design/web-react';
import type { TableColumnProps } from '@arco-design/web-react';
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
import { Link } from 'react-router-dom';
import { BrandLogo } from '../../components/brand/BrandLogo';
import { adminAuditRows, adminOverviewMetrics, adminResourceCards, adminUserRows } from '../../mock/admin';
import { mockAuthStatus, mockAuthUser } from '../../mock/auth';
import styles from './AdminPage.module.css';

type AuditRow = (typeof adminAuditRows)[number];
type UserRow = (typeof adminUserRows)[number];

const canAccessAdmin = mockAuthStatus === 'authenticated' && (mockAuthUser.role === 'admin' || mockAuthUser.role === 'operator');
const canManage = mockAuthStatus === 'authenticated' && mockAuthUser.role === 'admin';
const Option = Select.Option;

const adminNavItems = [
  { key: 'overview', label: '概览', icon: <IconDashboard /> },
  { key: 'users', label: '用户管理', icon: <IconUserGroup /> },
  { key: 'runs', label: 'Agent 运行', icon: <IconThunderbolt /> },
  { key: 'tools', label: '工具调用', icon: <IconTool /> },
  { key: 'usage', label: '模型用量', icon: <IconStorage /> },
  { key: 'knowledge', label: '知识库', icon: <IconBook /> },
  { key: 'deleted', label: '软删除资源', icon: <IconHistory /> }
];

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

export function AdminPage() {
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
          {adminNavItems.map((item, index) => (
            <button className={`${styles.navButton} ${index === 0 ? styles.navButtonActive : ''}`} key={item.key} type="button">
              {item.icon}
              <span>{item.label}</span>
            </button>
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
          <section className={styles.header}>
            <div>
              <h1>系统概览</h1>
              <p>运行、用户、工具、模型和知识库索引状态。当前为 mock 管理视图。</p>
            </div>
            <Tag color="arcoblue">独立后台页面</Tag>
          </section>

          <section className={styles.metrics}>
            {adminOverviewMetrics.map((metric) => (
              <article className={`${styles.metric} ${styles[metric.tone]}`} key={metric.label}>
                <span>{metric.label}</span>
                <strong>{metric.value}</strong>
                <em>{metric.hint}</em>
              </article>
            ))}
          </section>

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
            <Input className={styles.filterInput} size="small" placeholder="traceId / userId" allowClear />
            <Button type="primary" onClick={() => Message.info('筛选为 mock 操作')}>
              查询
            </Button>
          </section>

          <section className={styles.body}>
            <Card className={styles.auditCard} bordered={false}>
              <div className={styles.cardHead}>
                <strong>运行审计</strong>
                <Tag color="arcoblue">AgentRun / ToolCall / Trace</Tag>
              </div>
              <Table columns={auditColumns} data={adminAuditRows} pagination={{ pageSize: 5, total: adminAuditRows.length }} size="small" />
            </Card>

            <aside className={styles.side}>
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
            <article>
              <IconFile />
              <strong>知识库文档</strong>
              <span>上传、下线、恢复和索引状态将在真实接口接入后开放。</span>
            </article>
            <article>
              <IconTool />
              <strong>工具注册表</strong>
              <span>工具版本、风险等级、权限范围和启停状态统一在后台治理。</span>
            </article>
            <article>
              <IconStorage />
              <strong>模型用量</strong>
              <span>查看供应商、模型、token、耗时和成本趋势。</span>
            </article>
          </section>
        </div>
      </main>
    </div>
  );
}
