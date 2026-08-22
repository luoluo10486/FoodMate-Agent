import { AlertTriangle, CheckCircle2, FileWarning, LoaderCircle, RefreshCw, ShieldAlert, XCircle } from 'lucide-react';
import { useEffect, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { ArrowLeft } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { ROUTES } from '../../constants/routes';
import { adminOperationAuditRows } from '../../services/adminService';
import { getAuthUser } from '../../services/authService';
import styles from './AdminPage.module.css';
import { AdminHeader } from './tabs/AdminComponents';
import { adminNavItems, canAccessAdmin, canManage, getSectionKey, isAdminNavItemActive } from './tabs/AdminShared';
import { DeletedSection } from './tabs/DeletedResourcesTab';
import { KnowledgeSection } from './tabs/KnowledgeTab';
import { OverviewSection } from './tabs/OverviewTab';
import { RunsSection } from './tabs/RunsTab';
import { ToolsSection } from './tabs/ToolsTab';
import { UsageSection } from './tabs/UsageTab';
import { UsersSection } from './tabs/UsersTab';
import { AdminOperationStatus } from './tabs/AdminOperationStatus';
import { OperationAuditSection } from './tabs/OperationAuditTab';
import type { AdminActionPayload, AdminOperationError, AdminOperationState, AdminSectionKey } from './tabs/types';

const defaultOperationError: AdminOperationError = {
  code: 'REGISTRY_TIMEOUT_504',
  requestId: 'req-foodmate-9082ac918',
  message: '无法停用工具：服务端超时 (GATEWAY_TIMEOUT)',
};

const figmaOperationAction: AdminActionPayload = {
  action: '停用工具',
  targetLabel: 'nutrition_lookup',
  targetType: 'tool',
  targetId: 'nutrition_lookup',
};

type AdminFixtureState =
  | 'overview'
  | 'tool-registry'
  | 'deleted-resources'
  | 'user-detail'
  | 'op-no-permission'
  | 'op-confirm'
  | 'op-submitting'
  | 'op-success'
  | 'op-failed'
  | 'knowledge-uploading'
  | 'knowledge-indexing'
  | 'knowledge-upload-failed'
  | 'knowledge-upload-success'
  | 'knowledge-format-error'
  | 'knowledge-size-error'
  | 'run-detail'
  | 'tool-calls'
  | 'sql-audit'
  | 'trace';

function getAdminFixtureState(value: string | null): AdminFixtureState | undefined {
  const states: AdminFixtureState[] = [
    'overview',
    'tool-registry',
    'deleted-resources',
    'user-detail',
    'op-no-permission',
    'op-confirm',
    'op-submitting',
    'op-success',
    'op-failed',
    'knowledge-uploading',
    'knowledge-indexing',
    'knowledge-upload-failed',
    'knowledge-upload-success',
    'knowledge-format-error',
    'knowledge-size-error',
    'run-detail',
    'tool-calls',
    'sql-audit',
    'trace',
  ];
  return value && states.includes(value as AdminFixtureState) ? (value as AdminFixtureState) : undefined;
}

function getFixtureNavKey(state: AdminFixtureState | undefined): string | undefined {
  if (!state) return undefined;
  if (state === 'tool-registry' || state.startsWith('op-')) return 'registry';
  if (state === 'deleted-resources') return 'deleted';
  if (state === 'user-detail') return 'users';
  if (state.startsWith('knowledge-')) return 'knowledge';
  if (state === 'run-detail') return 'runs';
  if (state === 'tool-calls') return 'tools';
  if (state === 'sql-audit') return 'sql';
  if (state === 'trace') return 'trace';
  if (state === 'overview') return 'overview';
  return undefined;
}

const toolCallsPayload = `{
  "query": "avocado sourdough toast",
  "filters": {
    "usda_ndb_id": "1103982",
    "strict_keto_validation": true
  },
  "caller_context_mask": "SENSITIVE_USER_CREDENTIALS_MASKED_***"
}`;

type GovernanceTabKey = 'tool-calls' | 'sql-audit' | 'trace';

function GovernanceTabs({ active }: { active: GovernanceTabKey }) {
  const tabs: Array<{ key: GovernanceTabKey; label: string; href: string }> = [
    { key: 'tool-calls', label: '工具调用', href: '/admin?state=tool-calls' },
    { key: 'sql-audit', label: 'SQL 审计', href: '/admin?state=sql-audit' },
    { key: 'trace', label: '追踪视图', href: '/admin?state=trace' },
  ];

  return (
    <nav className={styles.governanceTabs} aria-label="治理详情视图">
      {tabs.map((tab) => (
        <Button
          asChild
          key={tab.key}
          size="sm"
          variant={tab.key === active ? 'default' : 'outline'}
          className={`${styles.governanceTab} ${tab.key === active ? styles.governanceTabActive : ''}`}
        >
          <Link to={tab.href}>{tab.label}</Link>
        </Button>
      ))}
    </nav>
  );
}

function ToolCallsFixture() {
  const [search, setSearch] = useState('');
  const hasMatch =
    !search.trim() ||
    ['call_829c', 'run_98218a', 'usda_food_api.query_ingredients'].some((value) =>
      value.toLowerCase().includes(search.trim().toLowerCase()),
    );

  return (
    <section className={styles.governanceSurface} aria-label="工具调用详情 fixture">
      <GovernanceTabs active="tool-calls" />

      <div className={styles.governanceFilters} aria-label="工具调用筛选">
        <span className={styles.governanceStaticFilter}>tool_name: query_usda</span>
        <Select defaultValue="high">
          <SelectTrigger className={styles.governanceRiskFilter} aria-label="风险筛选">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="high">风险：高</SelectItem>
            <SelectItem value="medium">风险：中</SelectItem>
            <SelectItem value="low">风险：低</SelectItem>
          </SelectContent>
        </Select>
        <Input
          aria-label="搜索运行 ID"
          className={styles.governanceSearch}
          placeholder="搜索运行ID..."
          value={search}
          onChange={(event) => setSearch(event.target.value)}
        />
      </div>

      <section className={styles.governanceTable} aria-label="工具调用记录">
        <div className={styles.governanceTableHeader} role="row">
          <span>调用 ID</span>
          <span>运行 ID</span>
          <span>工具组件</span>
          <span>版本</span>
          <span>状态</span>
          <span>耗时</span>
          <span>风险</span>
        </div>
        {hasMatch ? (
          <div className={styles.governanceTableRow} role="row">
            <strong>call_829c</strong>
            <code>run_98218a</code>
            <strong>usda_food_api.query_ingredients</strong>
            <span>v2.1</span>
            <span className={styles.governanceFailure}>失败</span>
            <span>8.5s</span>
            <span className={styles.governanceRiskMedium}>中</span>
          </div>
        ) : (
          <p className={styles.governanceEmpty} role="status">
            没有匹配的工具调用
          </p>
        )}
      </section>

      <section className={styles.governancePayloadCard} aria-label="Arguments & System Schema">
        <div className={styles.governancePayloadHeader}>
          <h2>Arguments &amp; System Schema (call_829c)</h2>
          <span className={styles.governancePolicy}>策略：通过</span>
        </div>
        <div className={styles.governancePayload}>
          <div className={styles.governancePayloadMeta}>
            <strong>payload_arguments.json</strong>
            <code>只读</code>
          </div>
          <pre>{toolCallsPayload}</pre>
        </div>
        <p className={styles.governanceFootnote}>
          * Sensitive fields masked automatically by Foodmate PII filter gateway.
        </p>
      </section>

      <aside className={styles.governanceNotes} aria-label="Tool Calls 筛选与详情">
        <h2>Tool Calls · 筛选与详情</h2>
        <p>筛选：时间范围 · 状态 · 工具名 · 风险等级 · 仅看失败 · 重试次数</p>
        <p>详情字段：call_id · run_id · 创建时间 · 完成时间 · 耗时 · 状态 · 重试次数 · 错误码</p>
        <p className={styles.governanceNoteSuccess}>
          输入 / 输出：结构化摘要 + 脱敏 payload；敏感字段仅显示 [MASKED]，支持复制 call_id 与查看所属 Run。
        </p>
        <p className={styles.governanceNoteMuted}>
          工具策略校验：权限范围、超时、重试策略、风险等级和 SQL Guard 结果均可追踪。
        </p>
      </aside>
    </section>
  );
}

function SqlAuditFixture() {
  const [search, setSearch] = useState('');
  const hasMatch =
    !search.trim() ||
    ['call_829c', 'run_98218a', 'usda_food_api.query_ingredients'].some((value) =>
      value.toLowerCase().includes(search.trim().toLowerCase()),
    );

  return (
    <section className={styles.governanceSurface} aria-label="SQL 审计详情 fixture">
      <GovernanceTabs active="sql-audit" />

      <div className={styles.governanceFilters} aria-label="SQL 审计筛选">
        <span className={styles.governanceStaticFilter}>tool_name: query_usda</span>
        <Select defaultValue="high">
          <SelectTrigger className={styles.governanceRiskFilter} aria-label="SQL 风险筛选">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="high">风险：高</SelectItem>
            <SelectItem value="medium">风险：中</SelectItem>
            <SelectItem value="low">风险：低</SelectItem>
          </SelectContent>
        </Select>
        <Input
          aria-label="搜索 SQL 运行 ID"
          className={styles.governanceSearch}
          placeholder="搜索运行ID..."
          value={search}
          onChange={(event) => setSearch(event.target.value)}
        />
      </div>

      <section className={styles.governanceTable} aria-label="SQL 审计记录">
        <div className={styles.governanceTableHeader} role="row">
          <span>调用 ID</span>
          <span>运行 ID</span>
          <span>工具组件</span>
          <span>版本</span>
          <span>状态</span>
          <span>耗时</span>
          <span>风险</span>
        </div>
        {hasMatch ? (
          <div className={styles.governanceTableRow} role="row">
            <strong>call_829c</strong>
            <code>run_98218a</code>
            <strong>usda_food_api.query_ingredients</strong>
            <span>v2.1</span>
            <span className={styles.governanceFailure}>失败</span>
            <span>8.5s</span>
            <span className={styles.governanceRiskMedium}>中</span>
          </div>
        ) : (
          <p className={styles.governanceEmpty} role="status">
            没有匹配的 SQL 审计记录
          </p>
        )}
      </section>

      <section className={styles.governancePayloadCard} aria-label="SQL 审计参数详情">
        <div className={styles.governancePayloadHeader}>
          <h2>Arguments &amp; System Schema (call_829c)</h2>
          <span className={styles.governancePolicy}>策略：通过</span>
        </div>
        <div className={styles.governancePayload}>
          <div className={styles.governancePayloadMeta}>
            <strong>payload_arguments.json</strong>
            <code>只读</code>
          </div>
          <pre>{toolCallsPayload}</pre>
        </div>
        <p className={styles.governanceFootnote}>
          * Sensitive fields masked automatically by Foodmate PII filter gateway.
        </p>
      </section>

      <aside className={styles.governanceNotes} aria-label="SQL Audit 筛选与详情">
        <h2>SQL Audit · 筛选与详情</h2>
        <p>筛选：时间范围 · 用户 / Run · 工具组件 · 风险等级 · 执行结果 · 仅看失败</p>
        <p>列表字段：audit_id · run_id · tool_name · query_hash · 执行时间 · 耗时 · 状态 · 风险等级</p>
        <p className={styles.governanceNoteSuccess}>
          详情字段：SQL 摘要 · 参数摘要 · 行数 / 结果摘要 · Guard 决策 · 错误码 · request_id / trace_id
        </p>
        <p className={styles.governanceNoteDanger}>
          数据库凭据、令牌和敏感参数统一脱敏；只展示经过权限过滤的审计内容。
        </p>
      </aside>
    </section>
  );
}

function AgentTimelineFixture({ trace }: { trace: boolean }) {
  return (
    <section
      className={`${styles.fixtureSurface} ${styles.fixtureSurfaceInline}`}
      aria-label={trace ? 'Trace 详情 fixture' : 'Run 详情 fixture'}
    >
      <div className={styles.runFixtureToolbar} aria-label="Run 筛选">
        <span className={styles.runFixtureId}>Run ID: run_...</span>
        <span className={styles.runFixtureSearch}>搜索用户...</span>
        <span className={`${styles.runFixtureStatus} ${styles.runFixtureStatusFailed}`}>Failed ×</span>
        <span className={`${styles.runFixtureStatus} ${styles.runFixtureStatusSuccess}`}>Success ✓</span>
        <span className={styles.runFixtureDegraded}>
          仅降级
          <i aria-hidden="true" />
        </span>
      </div>
      <section className={styles.runFixtureTable} aria-label="Run 记录">
        <div className={styles.runFixtureTableHeader} role="row">
          <span>运行 ID</span>
          <span>用户</span>
          <span>上下文</span>
          <span>状态</span>
          <span>阶段</span>
          <span>耗时</span>
          <span>成本</span>
          <span>工具数</span>
        </div>
        <div className={styles.runFixtureTableRow} role="row">
          <strong>run_98218a</strong>
          <strong>anddy_lab</strong>
          <span>Keto Meal Plan Formulation for target 1800kcal</span>
          <b className={styles.runFixtureFailure}>失败</b>
          <span>RAG_RETRIEVE</span>
          <span>12.4s</span>
          <span>$0.045</span>
          <span>12 calls</span>
        </div>
      </section>
      <section className={styles.runFixtureTrace} aria-label="执行事件追踪">
        <div className={styles.runFixtureTraceHeader}>
          <h2>
            执行事件追踪： <code>run_98218a</code>
          </h2>
          <button
            type="button"
            className={styles.runFixtureDownload}
            onClick={() =>
              window.dispatchEvent(
                new CustomEvent('foodmate:admin-notice', { detail: { message: '完整日志下载已加入队列。' } }),
              )
            }
          >
            下载完整日志
          </button>
        </div>
        <div className={styles.runFixtureSteps}>
          <article>
            <header>
              <strong>1. 分发</strong>
              <code>0.2s</code>
            </header>
            <p>Agent queued and initialized on gpt-4-turbo</p>
          </article>
          <article>
            <header>
              <strong>2. RAG 查询</strong>
              <code>3.1s</code>
            </header>
            <p>Semantic query to Vector DB. Index hit: 94%</p>
          </article>
          <article>
            <header>
              <strong>3. 降级</strong>
              <code>8.5s</code>
            </header>
            <p>USDA API 延迟阈值超限警告</p>
          </article>
          <article>
            <header>
              <strong>4. 失败</strong>
              <code>0.6s</code>
            </header>
            <p>进程中止：上下文Token溢出限制</p>
          </article>
        </div>
      </section>
      {trace ? (
        <aside className={`${styles.governanceNotes} ${styles.traceNotes}`} aria-label="Trace 聚合与筛选">
          <h2>Trace 聚合与筛选</h2>
          <p>按 trace_id 聚合：请求 · Run · Tool Calls · 模型调用 · 管理操作</p>
          <p>筛选：时间范围 · 仅看失败 · 状态 · 节点类型；支持展开节点与复制 trace_id</p>
          <p className={styles.governanceNoteSuccess}>
            时间线字段：开始 / 完成时间 · 阶段 · 状态 · 耗时 · 父子关系 · 事件顺序缺口 / 重复 / 乱序
          </p>
          <p className={styles.governanceNoteSuccess}>
            request_id req_7c2e · trace_id tr_88192a · dispatch_id dsp_55aa · 复制后可回到 Run 或 SQL Audit
          </p>
          <p className={styles.governanceNoteMuted}>敏感字段只显示脱敏摘要，不展示数据库凭据和隐藏推理。</p>
        </aside>
      ) : null}
    </section>
  );
}

function AdminFixtureOverlay({ state, onDismiss }: { state: AdminFixtureState; onDismiss: () => void }) {
  if (state === 'overview' || state === 'tool-registry' || state === 'deleted-resources' || state === 'user-detail')
    return null;
  const isOperation = state.startsWith('op-');
  const isDetail = state === 'run-detail' || state === 'tool-calls' || state === 'sql-audit' || state === 'trace';
  if (state === 'op-no-permission') {
    return (
      <div className={`${styles.fixtureBanner} ${styles.fixtureBannerError}`} role="alert">
        <ShieldAlert aria-hidden="true" />
        当前角色为 Operator，无写操作权限
      </div>
    );
  }
  if (state === 'op-success') {
    return (
      <div className={`${styles.fixtureBanner} ${styles.fixtureBannerSuccess}`} role="status">
        <CheckCircle2 aria-hidden="true" />
        操作成功：工具 nutrition_lookup 已成功停用
      </div>
    );
  }
  if (isDetail) {
    if (state === 'run-detail') {
      return <AgentTimelineFixture trace={false} />;
    }
    if (state === 'tool-calls') return <ToolCallsFixture />;
    if (state === 'sql-audit') return <SqlAuditFixture />;
    if (state === 'trace') return <AgentTimelineFixture trace />;
    const title = '工具调用与 SQL 审计';
    return (
      <div className={styles.fixtureSurface}>
        <div className={styles.fixtureSurfaceHeader}>
          <h2>{title}</h2>
          <Button variant="outline" size="sm" onClick={onDismiss}>
            关闭状态
          </Button>
        </div>
        <div className={styles.fixtureSurfaceToolbar}>
          <span>Run ID: run_98218a</span>
          <span>失败 · RAG_RETRIEVE</span>
          <span>12.4s · $0.045 · 12 calls</span>
        </div>
        <div className={styles.fixtureSurfaceCard}>
          <h3>执行事件追踪：run_98218a</h3>
          <pre>
            {JSON.stringify(
              {
                query: 'avocado sourdough toast',
                filters: { usda_ndb_id: '1103982', strict_keto_validation: true },
                caller_context_mask: 'SENSITIVE_USER_CREDENTIALS_MASKED_***',
              },
              null,
              2,
            )}
          </pre>
        </div>
      </div>
    );
  }

  const knowledgeError =
    state === 'knowledge-format-error' || state === 'knowledge-size-error' || state === 'knowledge-upload-failed';
  const errorTitle =
    state === 'knowledge-format-error'
      ? '文件格式校验失败'
      : state === 'knowledge-size-error'
        ? '文件大小超过限制'
        : '知识库上传失败';
  const knowledgeProgressTitle =
    state === 'knowledge-uploading'
      ? '批量任务已提交'
      : state === 'knowledge-indexing'
        ? '正在建立索引'
        : state === 'knowledge-upload-success'
          ? '批量任务已提交'
          : '';
  const operationTitle =
    state === 'op-confirm'
      ? '确认停用工具'
      : state === 'op-submitting'
        ? '正在提交操作'
        : state === 'op-failed'
          ? '操作失败'
          : '';
  const title = isOperation ? operationTitle : knowledgeError ? errorTitle : knowledgeProgressTitle;
  const Icon = isOperation
    ? state === 'op-failed'
      ? XCircle
      : AlertTriangle
    : knowledgeError
      ? FileWarning
      : LoaderCircle;
  const progress =
    state === 'op-submitting'
      ? '正在通知关联的服务集群同步状态...'
      : state === 'knowledge-uploading'
        ? '上传中 · 64% · 可离开页面，完成后自动开始索引'
        : state === 'knowledge-indexing'
          ? '索引中 · 72% · 正在生成向量索引'
          : state === 'knowledge-upload-success'
            ? '批量任务已提交 · 3 个文件正在后台处理'
            : '';
  return (
    <div className={styles.fixtureOverlay} role="presentation">
      <section
        className={`${styles.fixtureModal} ${knowledgeError ? styles.fixtureModalError : ''}`}
        role="alert"
        aria-live="polite"
      >
        <div className={styles.fixtureModalTitle}>
          <span>
            <Icon aria-hidden="true" />
          </span>
          <h2>{title}</h2>
        </div>
        {isOperation ? (
          <p>
            您正在尝试停用工具 <strong>nutrition_lookup</strong>。停用后，所有关联的 Agent
            运行将无法在调用流中激活此工具。
          </p>
        ) : null}
        {knowledgeError ? (
          <>
            <p>
              <strong>
                {state === 'knowledge-format-error'
                  ? '2 个文件不支持 · meal_photo.webp'
                  : state === 'knowledge-size-error'
                    ? '1 个文件超出 50MB · nutrition_archive.pdf'
                    : '上传任务未完成 · 请检查文件并重试'}
              </strong>
            </p>
            <p>仅支持 PDF / CSV / XLSX / TXT；错误不会影响已完成的索引。</p>
            <code>
              错误码: {state === 'knowledge-format-error' ? 'KB_UPLOAD_FORMAT_INVALID' : 'KB_UPLOAD_SIZE_LIMIT'} ·
              request_id: req_kb_20260731_0042
            </code>
          </>
        ) : null}
        {progress ? (
          <>
            <p className={styles.fixtureProgressText}>{progress}</p>
            <span className={styles.fixtureProgress}>
              <i style={{ width: state === 'knowledge-upload-success' ? '100%' : '64%' }} />
            </span>
          </>
        ) : null}
        {state === 'op-failed' ? (
          <code>ERROR_CODE: REGISTRY_TIMEOUT_504{`\n`}REQUEST_ID: req-foodmate-9082ac918</code>
        ) : null}
        {state === 'op-confirm' || state === 'op-failed' || knowledgeError ? (
          <div className={styles.fixtureModalActions}>
            <Button variant="outline" onClick={onDismiss}>
              取消
            </Button>
            <Button variant={state === 'op-failed' || knowledgeError ? 'destructive' : 'default'} onClick={onDismiss}>
              <RefreshCw aria-hidden="true" />
              {state === 'op-failed' ? '重新尝试' : knowledgeError ? '移除并重试' : '确认停用'}
            </Button>
          </div>
        ) : null}
      </section>
    </div>
  );
}

function appendOperationAudit(
  authUser: ReturnType<typeof getAuthUser>,
  action: string,
  targetType: string,
  targetId: string,
  result: 'success' | 'failed' = 'success',
  requestId?: string,
) {
  const stamp = Date.now();
  adminOperationAuditRows.unshift({
    key: `op-${stamp}`,
    operator_id: `user_${authUser.id}`,
    operator: authUser.role,
    action,
    target_type: targetType,
    target_id: targetId,
    result,
    request_id: requestId ?? `req_admin_${stamp}`,
    trace_id: `trace_admin_${stamp}`,
    created_at: new Date(stamp).toLocaleString('zh-CN', { hour12: false }).replace(/\//g, '-'),
    request_summary: `${action} ${targetId}`,
    before_state: '待提交',
    after_state: result === 'success' ? '已完成' : '失败',
    error_code: result === 'success' ? '-' : 'ADMIN_OPERATION_FAILED',
    client_info: 'FoodMate Admin Console',
  });
  if (adminOperationAuditRows.length > 8) adminOperationAuditRows.splice(8);
}

function renderSection(
  sectionKey: AdminSectionKey,
  onAction: (payload: AdminActionPayload) => void,
  refreshNonce: number,
  operationStatus: AdminOperationState,
) {
  switch (sectionKey) {
    case 'users':
      return <UsersSection onAction={onAction} />;
    case 'runs':
      return <RunsSection refreshNonce={refreshNonce} />;
    case 'tools':
      return <ToolsSection onAction={onAction} operationStatus={operationStatus} refreshNonce={refreshNonce} />;
    case 'usage':
      return <UsageSection />;
    case 'knowledge':
      return <KnowledgeSection onAction={onAction} />;
    case 'deleted':
      return <DeletedSection onAction={onAction} />;
    case 'audit':
      return <OperationAuditSection refreshNonce={refreshNonce} />;
    default:
      return <OverviewSection onAction={onAction} refreshNonce={refreshNonce} />;
  }
}

export function AdminPage() {
  const authUser = getAuthUser();
  const { pathname, search } = useLocation();
  const navigate = useNavigate();
  const requestedFixture = getAdminFixtureState(new URLSearchParams(search).get('state'));
  const fixtureNavKey = getFixtureNavKey(requestedFixture);
  const sectionKey = (
    requestedFixture?.startsWith('op-')
      ? 'tools'
      : requestedFixture?.startsWith('knowledge-')
        ? 'knowledge'
        : requestedFixture === 'tool-registry'
          ? 'tools'
          : requestedFixture === 'deleted-resources'
            ? 'deleted'
            : requestedFixture === 'user-detail'
              ? 'users'
              : getSectionKey(pathname, search)
  ) as AdminSectionKey;
  const isRegistryRoute =
    (requestedFixture?.startsWith('op-') ?? false) ||
    requestedFixture === 'tool-registry' ||
    (pathname.endsWith('/tools') && new URLSearchParams(search).get('tab') === 'registry');
  const isDeletedRoute = pathname.endsWith('/deleted') || requestedFixture === 'deleted-resources';
  const isDetailFixture =
    requestedFixture === 'run-detail' ||
    requestedFixture === 'tool-calls' ||
    requestedFixture === 'sql-audit' ||
    requestedFixture === 'trace';
  const detailTitle =
    requestedFixture === 'tool-calls' || requestedFixture === 'sql-audit' ? '工具调用与 SQL 审计' : 'Agent 运行控制台';
  const [pendingAction, setPendingAction] = useState<AdminActionPayload>();
  const [operationStatus, setOperationStatus] = useState<AdminOperationState>('idle');
  const [operationError, setOperationError] = useState<AdminOperationError>();
  const [notice, setNotice] = useState('');
  const [refreshNonce, setRefreshNonce] = useState(0);
  const fixtureUser = requestedFixture
    ? { displayName: 'Anddy', id: '1234567' }
    : { displayName: authUser.displayName, id: authUser.id };
  const fixtureOperationStatus: AdminOperationState | undefined = requestedFixture?.startsWith('op-')
    ? requestedFixture.replace('op-', '') === 'no-permission'
      ? 'no-permission'
      : (requestedFixture.replace('op-', '') as AdminOperationState)
    : undefined;
  const activeOperationStatus = fixtureOperationStatus ?? operationStatus;
  const activeOperationAction = fixtureOperationStatus ? figmaOperationAction : pendingAction;
  const activeOperationError = fixtureOperationStatus === 'failed' ? defaultOperationError : operationError;
  const dismissFixture = () => navigate('/admin?state=tool-registry', { replace: true });

  useEffect(() => {
    const handleNotice = (event: Event) => {
      const detail = (event as CustomEvent<{ message?: string }>).detail;
      if (detail?.message) setNotice(detail.message);
    };
    window.addEventListener('foodmate:admin-notice', handleNotice);
    return () => window.removeEventListener('foodmate:admin-notice', handleNotice);
  }, []);

  const requestAdminAction = (payload: AdminActionPayload) => {
    setOperationError(undefined);
    setNotice('');
    setPendingAction(payload);
    if (!canManage) {
      setOperationStatus('no-permission');
      return;
    }
    setOperationStatus('confirm');
  };

  const executePendingAction = async () => {
    if (!pendingAction) return;
    const { action, targetType, targetId, onApply, execute } = pendingAction;
    setOperationStatus('submitting');
    try {
      if (import.meta.env.VITE_AGENT_MODE === 'real') {
        await execute?.();
      } else {
        await new Promise<void>((resolve) => window.setTimeout(resolve, 280));
      }
      onApply?.();
      appendOperationAudit(authUser, action, targetType, targetId);
      setRefreshNonce((current) => current + 1);
      setOperationStatus('success');
    } catch (error) {
      const candidate = (error ?? {}) as {
        code?: unknown;
        message?: unknown;
        requestId?: unknown;
        request_id?: unknown;
      };
      const fallback =
        targetType === 'tool'
          ? defaultOperationError
          : {
              code: 'ADMIN_OPERATION_FAILED',
              requestId: 'req_admin_operation_failed',
              message: '操作未完成，请检查服务状态后重试。',
            };
      const failedCode = typeof candidate.code === 'string' ? candidate.code : fallback.code;
      const failedRequestId =
        typeof candidate.requestId === 'string'
          ? candidate.requestId
          : typeof candidate.request_id === 'string'
            ? candidate.request_id
            : fallback.requestId;
      setOperationError({
        code: failedCode,
        requestId: failedRequestId,
        message: typeof candidate.message === 'string' ? candidate.message : fallback.message,
      });
      appendOperationAudit(authUser, action, targetType, targetId, 'failed', failedRequestId);
      setOperationStatus('failed');
    }
  };

  const dismissOperation = () => {
    setPendingAction(undefined);
    setOperationError(undefined);
    setOperationStatus('idle');
  };

  const handleRefresh = () => setRefreshNonce((current) => current + 1);

  if (!canAccessAdmin) {
    return (
      <div className={styles.authShell}>
        <Card className={styles.noAccessCard}>
          <Badge variant="destructive">AUTH_FORBIDDEN</Badge>
          <h1>无权访问管理后台</h1>
          <p>管理后台仅对 admin/operator 开放，普通用户不会看到入口。</p>
          <Link to="/">
            <Button variant="outline">
              <ArrowLeft aria-hidden="true" />
              返回工作台
            </Button>
          </Link>
        </Card>
      </div>
    );
  }

  return (
    <div className={styles.adminShell}>
      <aside className={styles.adminSidebar}>
        <div className={styles.brandBlock}>
          <div className={styles.adminBrand}>
            <span className={styles.adminLogoMark}>F</span>
            <span className={styles.adminLogoCopy}>
              <strong>FoodMate</strong>
              <small>管理控制台</small>
            </span>
          </div>
          <span className={styles.adminTag}>FoodMate 管理</span>
        </div>
        <nav className={styles.adminNav} aria-label="管理后台导航">
          {adminNavItems.map((item) => {
            const isActive = fixtureNavKey
              ? item.key === fixtureNavKey
              : isAdminNavItemActive(item.path, pathname, search);
            const isLocked = Boolean(item.adminOnly && !canManage);
            return (
              <Link
                aria-current={isActive ? 'page' : undefined}
                aria-disabled={isLocked ? 'true' : undefined}
                className={`${styles.navButton} ${isLocked ? styles.navButtonLocked : ''} ${isActive ? styles.navButtonActive : ''}`}
                key={item.key}
                to={item.path}
                tabIndex={isLocked ? -1 : undefined}
                onClick={(event) => {
                  if (isLocked) event.preventDefault();
                }}
              >
                {item.icon}
                <span>{item.label}</span>
              </Link>
            );
          })}
        </nav>
        <div className={styles.sidebarFooter}>
          <div className={styles.privilegeBox}>
            <span className={styles.privilegeDot} aria-hidden="true" />
            <strong>{canManage ? '管理员：完全权限' : '操作员：只读'}</strong>
          </div>
          <Link className={styles.workspaceLink} to={ROUTES.HOME}>
            返回 Agent 工作区
          </Link>
          <div className={styles.userSection}>
            <div className={styles.userAvatar}>
              <img alt="" src="/assets/avatars/default-male.svg" />
            </div>
            <div className={styles.userMetadata}>
              <strong>{fixtureUser.displayName}&apos;s Lab</strong>
              <small>ID: {fixtureUser.id}</small>
            </div>
          </div>
        </div>
      </aside>
      <main className={styles.adminMain}>
        <AdminOperationStatus
          status={activeOperationStatus}
          action={activeOperationAction}
          error={activeOperationError}
          onConfirm={fixtureOperationStatus ? dismissFixture : () => void executePendingAction()}
          onCancel={fixtureOperationStatus ? dismissFixture : dismissOperation}
          onRetry={fixtureOperationStatus ? dismissFixture : () => void executePendingAction()}
          onDismiss={fixtureOperationStatus ? dismissFixture : dismissOperation}
        />
        {requestedFixture && !requestedFixture.startsWith('op-') && !isDetailFixture ? (
          <AdminFixtureOverlay state={requestedFixture} onDismiss={() => navigate('/admin', { replace: true })} />
        ) : null}
        <header className={styles.topbar}>
          <div className={styles.topbarTitle}>
            <strong>
              {isDetailFixture
                ? detailTitle
                : sectionKey === 'overview'
                  ? '管理概览'
                  : isRegistryRoute
                    ? '工具注册表'
                    : isDeletedRoute
                      ? '删除资源管理'
                      : sectionKey === 'users'
                        ? '用户管理'
                        : sectionKey === 'knowledge'
                          ? '知识库管理'
                          : sectionKey === 'audit'
                            ? '操作审计'
                            : '管理控制台'}
            </strong>
            {isDetailFixture || sectionKey === 'overview' || sectionKey === 'users' || isRegistryRoute ? (
              <span className={styles.envBadge}>生产环境</span>
            ) : isDeletedRoute ? (
              <span className={styles.securityBadge}>审计存档区</span>
            ) : null}
          </div>
          <div className={styles.topbarActions}>
            <span className={styles.refreshStatus}>
              {isDetailFixture
                ? '刷新时间：刚刚'
                : isRegistryRoute
                  ? '服务节点：healthy-cluster-0'
                  : isDeletedRoute
                    ? '存档保留时长：90天安全窗口'
                    : sectionKey === 'users'
                      ? '刷新时间：刚刚'
                      : sectionKey === 'audit'
                        ? '审计记录只读'
                        : '数据刷新：刚刚'}
            </span>
            <Button
              variant="outline"
              className={styles.topbarRefresh}
              onClick={isDeletedRoute ? () => setNotice('合规性审计记录仅供查看，恢复操作会写入审计。') : handleRefresh}
            >
              {isDetailFixture
                ? '刷新'
                : isRegistryRoute
                  ? '更新状态'
                  : isDeletedRoute
                    ? '合规性审计'
                    : sectionKey === 'users'
                      ? '刷新'
                      : sectionKey === 'audit'
                        ? '刷新审计'
                        : '刷新数据'}
            </Button>
          </div>
        </header>
        <div
          className={`${styles.page} ${sectionKey === 'users' ? styles.usersPage : ''} ${isDetailFixture ? styles.fixtureDetailPage : ''} fm-enter`}
        >
          {notice ? (
            <div className={styles.notice} role="status">
              {notice}
            </div>
          ) : null}
          {sectionKey === 'overview' ||
          sectionKey === 'users' ||
          sectionKey === 'knowledge' ||
          isRegistryRoute ||
          isDeletedRoute ? null : (
            <AdminHeader sectionKey={sectionKey} />
          )}
          {isDetailFixture ? (
            <AdminFixtureOverlay state={requestedFixture} onDismiss={() => navigate('/admin', { replace: true })} />
          ) : (
            renderSection(sectionKey, requestAdminAction, refreshNonce, activeOperationStatus)
          )}
        </div>
      </main>
    </div>
  );
}
