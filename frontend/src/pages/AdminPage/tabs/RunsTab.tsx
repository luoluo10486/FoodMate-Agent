import { Card, Table, Tabs, Tag } from '@arco-design/web-react';
import styles from '../AdminPage.module.css';
import {
  AdminFilters,
  MiniStat,
  adminAuditRows,
  adminSqlAuditRows,
  adminToolCallRows,
  adminTraceRows,
  auditColumns,
  sqlAuditColumns,
  toolCallColumns,
  traceColumns
} from './AdminShared';

const TabPane = Tabs.TabPane;

export function RunsSection() {
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
