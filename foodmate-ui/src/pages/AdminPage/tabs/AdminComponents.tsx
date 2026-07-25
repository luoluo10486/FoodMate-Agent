/**
 * AdminPage 共享 UI 组件
 *
 * 与 AdminShared.tsx 分离，以满足 react-refresh 规则：
 * 本文件只导出组件，AdminShared.tsx 只导出纯函数和数据。
 */
import { Button, Card, Input, Message, Select, Table, Tag } from '@arco-design/web-react';
import { IconLeft } from '@arco-design/web-react/icon';
import { Link } from 'react-router-dom';
import { ROUTES } from '../../../constants/routes';
import {
  adminOperationAuditRows,
  adminResourceCards,
  adminUserRows,
  adminUserSessionRows,
  adminToolRows,
  adminDeletedRows,
} from '../../../services/adminService';
import { canManage, operationAuditColumns, sectionMeta } from './AdminShared';
import type { AdminActionPayload } from './types';
import styles from '../AdminPage.module.css';

const Option = Select.Option;

// ── Header ──────────────────────────────────────────
export function AdminHeader({ sectionKey }: { sectionKey: string }) {
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

// ── Filters ─────────────────────────────────────────
export function AdminFilters({ placeholder = 'trace_id / user_id' }: { placeholder?: string }) {
  return (
    <section className={styles.filters}>
      <strong>筛选</strong>
      <Select
        className={styles.filterControl}
        size="small"
        defaultValue="all"
        triggerProps={{ autoAlignPopupWidth: false }}
      >
        <Option value="all">全部状态</Option>
        <Option value="active">active</Option>
        <Option value="completed">completed</Option>
        <Option value="failed">failed</Option>
      </Select>
      <Select
        className={styles.filterControl}
        size="small"
        defaultValue="24h"
        triggerProps={{ autoAlignPopupWidth: false }}
      >
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

// ── Access control ──────────────────────────────────
export function AdminOnlyNotice({ title }: { title: string }) {
  return (
    <Card className={styles.noAccessCard} bordered={false}>
      <Tag color="red">ADMIN_ONLY</Tag>
      <h1>{title}</h1>
      <p>该页面包含用户敏感信息或恢复类高风险能力，按后端接口契约仅 admin 可访问。</p>
      <Link to={ROUTES.ADMIN}>
        <Button icon={<IconLeft />}>返回概览</Button>
      </Link>
    </Card>
  );
}

// ── Mini stat card ──────────────────────────────────
export function MiniStat({
  label,
  value,
  hint,
  tone = 'green',
}: {
  label: string;
  value: string;
  hint: string;
  tone?: string;
}) {
  return (
    <article className={`${styles.metric} ${styles[tone]}`}>
      <span>{label}</span>
      <strong>{value}</strong>
      <em>{hint}</em>
    </article>
  );
}

// ── Operation audit card ────────────────────────────
export function OperationAuditCard() {
  return (
    <Card className={styles.wideCard} bordered={false}>
      <div className={styles.cardHead}>
        <strong>管理操作审计</strong>
        <Tag color="arcoblue">operator_id / target_type / request_id / trace_id</Tag>
      </div>
      <Table
        columns={operationAuditColumns}
        data={adminOperationAuditRows}
        pagination={{ pageSize: 4, total: adminOperationAuditRows.length }}
        size="small"
      />
    </Card>
  );
}

// ── Admin actions card ──────────────────────────────
export function AdminActionsCard({ onAction }: { onAction: (payload: AdminActionPayload) => void }) {
  return (
    <Card className={styles.card} bordered={false}>
      <strong>管理操作</strong>
      <div className={styles.actionGrid}>
        <Button
          disabled={!canManage}
          onClick={() =>
            onAction({
              action: '禁用用户',
              targetLabel: 'user_10002',
              targetType: 'user',
              targetId: 'user_10002',
              onApply: () => {
                const target = adminUserRows.find((item) => item.userId === 'user_10002');
                if (target) target.status = 'disabled';
              },
            })
          }
        >
          禁用用户
        </Button>
        <Button
          disabled={!canManage}
          onClick={() =>
            onAction({
              action: '重置会话',
              targetLabel: 'user_10002',
              targetType: 'user_session',
              targetId: 'user_10002',
              onApply: () => {
                adminUserSessionRows
                  .filter((s) => s.userId === 'user_10002')
                  .forEach((s) => {
                    s.status = 'revoked';
                  });
              },
            })
          }
        >
          重置会话
        </Button>
        <Button
          disabled={!canManage}
          onClick={() =>
            onAction({
              action: '工具启停',
              targetLabel: 'food_log_writer',
              targetType: 'tool',
              targetId: 'food_log_writer',
              onApply: () => {
                const tool = adminToolRows.find((t) => t.name === 'food_log_writer');
                if (tool) tool.status = tool.status === 'active' ? 'disabled' : 'active';
              },
            })
          }
        >
          工具启停
        </Button>
        <Button
          disabled={!canManage}
          onClick={() =>
            onAction({
              action: '恢复资源',
              targetLabel: 'meal_plan_73',
              targetType: 'meal_plan',
              targetId: 'meal_plan_73',
              onApply: () => {
                const rowIndex = adminDeletedRows.findIndex((r) => r.resourceId === 'meal_plan_73');
                if (rowIndex >= 0) adminDeletedRows.splice(rowIndex, 1);
              },
            })
          }
        >
          恢复资源
        </Button>
      </div>
      <p>高风险操作必须二次确认并写审计。operator 只能查看，不可执行。</p>
    </Card>
  );
}

// ── Governance resource card ────────────────────────
export function GovernanceResourceCard() {
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
