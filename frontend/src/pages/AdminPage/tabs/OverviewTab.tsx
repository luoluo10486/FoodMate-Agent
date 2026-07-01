import { Card, Table, Tag } from '@arco-design/web-react';
import { Link } from 'react-router-dom';
import { IconFile, IconThunderbolt, IconTool } from '@arco-design/web-react/icon';
import styles from '../AdminPage.module.css';
import {
  AdminActionsCard,
  AdminFilters,
  GovernanceResourceCard,
  OperationAuditCard,
  adminAuditRows,
  adminOverviewMetrics,
  auditColumns,
  canManage
} from './AdminShared';
import type { AdminActionPayload } from './types';

export function OverviewSection({ onAction }: { onAction: (payload: AdminActionPayload) => void }) {
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
          <AdminActionsCard onAction={onAction} />
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
