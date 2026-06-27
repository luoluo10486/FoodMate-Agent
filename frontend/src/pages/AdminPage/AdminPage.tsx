import { Button, Card, Input, Message, Select, Table, Tag } from '@arco-design/web-react';
import type { TableColumnProps } from '@arco-design/web-react';
import { WorkspaceLayout } from '../../layouts/WorkspaceLayout/WorkspaceLayout';
import { adminAuditRows, adminOverviewMetrics, adminResourceCards, adminUserRows } from '../../mock/admin';
import { mockAuthStatus, mockAuthUser } from '../../mock/auth';
import styles from './AdminPage.module.css';

type AuditRow = (typeof adminAuditRows)[number];
type UserRow = (typeof adminUserRows)[number];

const canAccessAdmin = mockAuthStatus === 'authenticated' && (mockAuthUser.role === 'admin' || mockAuthUser.role === 'operator');
const canManage = mockAuthStatus === 'authenticated' && mockAuthUser.role === 'admin';
const Option = Select.Option;

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
      <WorkspaceLayout activeModule="admin" moduleLabel={<Tag color="red">403</Tag>}>
        <div className={`${styles.page} ${styles.noAccess} fm-enter`}>
          <Card className={styles.card} bordered={false}>
            <Tag color="red">AUTH_FORBIDDEN</Tag>
            <h1>无权访问管理后台</h1>
            <p>管理后台仅对 admin/operator 开放。普通用户不会看到顶部入口。</p>
          </Card>
        </div>
      </WorkspaceLayout>
    );
  }

  return (
    <WorkspaceLayout activeModule="admin" moduleLabel={<Tag color="green">管理后台</Tag>}>
      <div className={`${styles.page} fm-enter`}>
        <section className={styles.header}>
          <div>
            <h1>系统概览</h1>
            <p>运行、用户、工具、模型和知识库索引状态。当前为 mock 管理视图。</p>
          </div>
          <Tag color={canManage ? 'green' : 'orange'}>{canManage ? 'admin 可操作' : 'operator 只读'}</Tag>
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
      </div>
    </WorkspaceLayout>
  );
}
