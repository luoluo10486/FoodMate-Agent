import { Button, Card, Table, Tag } from '@arco-design/web-react';
import type { TableColumnProps } from '@arco-design/web-react';
import { useEffect, useState } from 'react';
import styles from '../AdminPage.module.css';
import { AdminFilters, OperationAuditCard } from './AdminComponents';
import { type ToolRow, adminToolRows, canManage, riskTag, statusTag } from './AdminShared';
import type { AdminActionPayload } from './types';
import { loadAdminDashboard, updateAdminToolStatus } from '../../../services/adminService';

export function ToolsSection({ onAction }: { onAction: (payload: AdminActionPayload) => void }) {
  const [tools, setTools] = useState<ToolRow[]>(import.meta.env.VITE_AGENT_MODE === 'real' ? [] : adminToolRows);
  const [selectedTool, setSelectedTool] = useState<ToolRow | undefined>(tools[0]);
  useEffect(() => { if (import.meta.env.VITE_AGENT_MODE === 'real') loadAdminDashboard().then((d) => { const rows = d.tools as ToolRow[]; setTools(rows); setSelectedTool(rows[0]); }).catch(() => setTools([])); }, []);

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
          <Button
            size="mini"
            disabled={!canManage}
            onClick={() =>
              onAction({
                action: record.status === 'active' ? '停用工具' : '启用工具',
                targetLabel: record.name,
                targetType: 'tool',
                targetId: record.name,
                execute: async () => { await updateAdminToolStatus(record.name, record.status === 'active' ? 'disabled' : 'active'); },
                onApply: () => {
                  record.status = record.status === 'active' ? 'disabled' : 'active';
                },
              })
            }
          >
            启停
          </Button>
        </div>
      ),
    },
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
          <Table
            columns={toolColumns}
            data={tools}
            pagination={{ pageSize: 6, total: tools.length }}
            size="small"
          />
        </Card>
        <aside className={styles.side}>
          {selectedTool ? <ToolDetailCard tool={selectedTool} /> : null}
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
