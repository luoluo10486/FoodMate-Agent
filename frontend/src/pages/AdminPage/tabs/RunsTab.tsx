import { Card, Table, Tabs, Tag } from '@arco-design/web-react';
import { useEffect, useState } from 'react';
import styles from '../AdminPage.module.css';
import { AdminFilters, MiniStat } from './AdminComponents';
import {
  adminAuditRows,
  adminSqlAuditRows,
  adminToolCallRows,
  adminTraceRows,
  auditColumns,
  sqlAuditColumns,
  toolCallColumns,
  traceColumns,
} from './AdminShared';
import { loadAdminDashboard } from '../../../services/adminService';

const TabPane = Tabs.TabPane;

export function RunsSection() {
  const [dashboard, setDashboard] = useState(import.meta.env.VITE_AGENT_MODE === 'real' ? { runs: [], toolCalls: [], sqlAudits: [], traces: [] } : { runs: adminAuditRows, toolCalls: adminToolCallRows, sqlAudits: adminSqlAuditRows, traces: adminTraceRows });
  useEffect(() => { if (import.meta.env.VITE_AGENT_MODE === 'real') loadAdminDashboard().then((d) => setDashboard({ runs: d.runs as typeof adminAuditRows, toolCalls: d.tool_calls as typeof adminToolCallRows, sqlAudits: d.sql_audits as typeof adminSqlAuditRows, traces: [] })).catch(() => setDashboard({ runs: [], toolCalls: [], sqlAudits: [], traces: [] })); }, []);
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
            <Table
              columns={auditColumns}
              data={dashboard.runs}
              pagination={{ pageSize: 5, total: dashboard.runs.length }}
              size="small"
            />
          </TabPane>
          <TabPane key="tool-calls" title="ToolCall">
            <Table
              columns={toolCallColumns}
              data={dashboard.toolCalls}
              pagination={{ pageSize: 5, total: dashboard.toolCalls.length }}
              size="small"
            />
          </TabPane>
          <TabPane key="sql-audits" title="SQLAudit">
            <Table
              columns={sqlAuditColumns}
              data={dashboard.sqlAudits}
              pagination={{ pageSize: 5, total: dashboard.sqlAudits.length }}
              size="small"
            />
          </TabPane>
          <TabPane key="traces" title="Trace">
            <Table
              columns={traceColumns}
              data={dashboard.traces}
              pagination={{ pageSize: 5, total: dashboard.traces.length }}
              size="small"
            />
          </TabPane>
        </Tabs>
      </Card>
    </>
  );
}
