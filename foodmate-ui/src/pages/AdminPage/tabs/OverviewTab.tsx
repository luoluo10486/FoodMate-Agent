import { Card, Table, Tag } from '@arco-design/web-react';
import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { ROUTES } from '../../../constants/routes';
import { IconFile, IconThunderbolt, IconTool } from '@arco-design/web-react/icon';
import styles from '../AdminPage.module.css';
import { AdminActionsCard, AdminFilters, GovernanceResourceCard, OperationAuditCard } from './AdminComponents';
import { adminAuditRows, adminOverviewMetrics, auditColumns, canManage } from './AdminShared';
import type { AdminActionPayload } from './types';
import { loadAdminDashboard } from '../../../services/adminService';

export function OverviewSection({ onAction }: { onAction: (payload: AdminActionPayload) => void }) {
  const [metrics, setMetrics] = useState(import.meta.env.VITE_AGENT_MODE === 'real' ? [] : adminOverviewMetrics);
  const [rows, setRows] = useState(import.meta.env.VITE_AGENT_MODE === 'real' ? [] : adminAuditRows);
  useEffect(() => { if (import.meta.env.VITE_AGENT_MODE === 'real') loadAdminDashboard().then((d) => { setMetrics(d.overview_metrics as typeof adminOverviewMetrics); setRows(d.runs as typeof adminAuditRows); }).catch(() => { setMetrics([]); setRows([]); }); }, []);
  return (
    <>
      <section className={styles.metrics}>
        {metrics.map((metric) => (
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
          <Table
            columns={auditColumns}
            data={rows}
            pagination={{ pageSize: 5, total: rows.length }}
            size="small"
          />
        </Card>
        <aside className={styles.side}>
          <AdminActionsCard onAction={onAction} />
          <GovernanceResourceCard />
        </aside>
      </section>
      {canManage ? <OperationAuditCard /> : null}
      <section className={styles.moduleGrid}>
        <Link to={`${ROUTES.ADMIN}/runs`}>
          <IconThunderbolt />
          <strong>运行链路</strong>
          <span>查看 AgentRun、ToolCall、SQLAudit 和 Trace。</span>
        </Link>
        <Link to={`${ROUTES.ADMIN}/knowledge`}>
          <IconFile />
          <strong>知识库文档</strong>
          <span>上传、下线、恢复和索引状态将在真实接口接入后开放。</span>
        </Link>
        <Link to={`${ROUTES.ADMIN}/tools`}>
          <IconTool />
          <strong>工具注册表</strong>
          <span>工具版本、风险等级、权限范围和启停状态统一在后台治理。</span>
        </Link>
      </section>
    </>
  );
}
