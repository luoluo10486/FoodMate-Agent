import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react';
import { CheckCircle2, FileWarning, LoaderCircle, LockKeyhole, RefreshCw, Search, ShieldAlert } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import {
  approveRetentionPurge,
  loadRetentionPurge,
  loadRetentionPurgePreflight,
  placeRetentionHold,
  releaseRetentionHold,
  requestRetentionPurge,
  type RetentionHoldResult,
  type RetentionPurgePreflight,
  type RetentionPurgeResult,
} from '../../../services/adminService';
import { getAuthUser } from '../../../services/authService';
import type { AdminActionPayload } from './types';
import styles from '../AdminPage.module.css';

type RetentionSectionProps = {
  onAction: (payload: AdminActionPayload) => void;
  refreshNonce: number;
};

type ResourceType = 'knowledge_document' | 'admin_export_job';

type PurgeFormState = {
  resourceType: ResourceType;
  resourceId: string;
};

type HoldFormState = {
  resourceType: ResourceType;
  resourceId: string;
  reasonCode: string;
};

const resourceOptions: Array<{ value: ResourceType; label: string }> = [
  { value: 'knowledge_document', label: '知识库文档' },
  { value: 'admin_export_job', label: '管理端导出任务' },
];

const reasonOptions = [
  { value: 'legal_request', label: '法律请求' },
  { value: 'litigation', label: '诉讼保全' },
  { value: 'compliance_review', label: '合规复核' },
];

const fixturePurge: RetentionPurgeResult = {
  request_id: 901,
  status: 'requested',
  resource_type: 'knowledge_document',
  resource_id: 42,
  eligible_at: '2026-08-22T00:00:00Z',
  task_count: 0,
};

const fixturePreflight: RetentionPurgePreflight = {
  request_id: 901,
  status: 'requested',
  resource_type: 'knowledge_document',
  resource_id: 42,
  policy_found: true,
  hard_delete_enabled: false,
  resource_soft_deleted: true,
  retention_elapsed: true,
  legal_hold_clear: true,
  task_contract_valid: true,
  ready_to_execute: false,
  tasks: [{ task_type: 'database', status: 'pending', attempt_count: 0, last_error_code: null }],
  blockers: ['RETENTION_HARD_DELETE_DISABLED'],
};

const fixtureHold: RetentionHoldResult = {
  hold_id: 88,
  status: 'active',
  resource_type: 'knowledge_document',
  resource_id: 42,
  reason_code: 'legal_request',
};

function initialPurgeForm(): PurgeFormState {
  return { resourceType: 'knowledge_document', resourceId: '' };
}

function initialHoldForm(): HoldFormState {
  return { resourceType: 'knowledge_document', resourceId: '', reasonCode: 'legal_request' };
}

function positiveInteger(value: string) {
  const parsed = Number(value.trim());
  return Number.isInteger(parsed) && parsed > 0 ? parsed : undefined;
}

function statusBadge(status: string) {
  const normalized = status.toLowerCase();
  const variant =
    normalized === 'approved' || normalized === 'active' || normalized === 'released'
      ? 'default'
      : normalized === 'failed' || normalized === 'rejected'
        ? 'destructive'
        : 'warning';
  return <Badge variant={variant}>{status || '-'}</Badge>;
}

function flagLabel(value: boolean) {
  return value ? '是' : '否';
}

function flagClass(value: boolean) {
  return value ? styles.retentionFlagYes : styles.retentionFlagNo;
}

function formatDate(value: string) {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString('zh-CN', { hour12: false });
}

function resourceLabel(value: string) {
  return resourceOptions.find((item) => item.value === value)?.label ?? value;
}

function errorMessage(cause: unknown, fallback: string) {
  return cause instanceof Error ? cause.message : fallback;
}

function Flag({ label, value }: { label: string; value: boolean }) {
  return (
    <div className={styles.retentionFlag}>
      <span>{label}</span>
      <strong className={flagClass(value)}>{flagLabel(value)}</strong>
    </div>
  );
}

export function RetentionSection({ onAction, refreshNonce }: RetentionSectionProps) {
  const isReal = import.meta.env.VITE_AGENT_MODE === 'real';
  const role = getAuthUser().role;
  const canManageRetention = role === 'admin' || role === 'superadmin';
  const isSuperadmin = role === 'superadmin';
  const [purgeForm, setPurgeForm] = useState<PurgeFormState>(initialPurgeForm);
  const [holdForm, setHoldForm] = useState<HoldFormState>(initialHoldForm);
  const [requestIdInput, setRequestIdInput] = useState(isReal ? '' : String(fixturePurge.request_id));
  const [purge, setPurge] = useState<RetentionPurgeResult | undefined>(isReal ? undefined : fixturePurge);
  const [preflight, setPreflight] = useState<RetentionPurgePreflight | undefined>(
    isReal ? undefined : fixturePreflight,
  );
  const [hold, setHold] = useState<RetentionHoldResult | undefined>(isReal ? undefined : fixtureHold);
  const [purgeLoading, setPurgeLoading] = useState(false);
  const [purgeError, setPurgeError] = useState('');
  const [purgeFormError, setPurgeFormError] = useState('');
  const [holdFormError, setHoldFormError] = useState('');
  const requestIdInputRef = useRef(requestIdInput);

  useEffect(() => {
    requestIdInputRef.current = requestIdInput;
  }, [requestIdInput]);

  const readPurgeSnapshot = useCallback(async (requestId: number) => {
    setPurgeLoading(true);
    setPurgeError('');
    setPurge(undefined);
    setPreflight(undefined);
    try {
      const [detail, nextPreflight] = await Promise.all([
        loadRetentionPurge(requestId),
        loadRetentionPurgePreflight(requestId),
      ]);
      setPurge(detail);
      setPreflight(nextPreflight);
    } catch (cause) {
      setPurgeError(errorMessage(cause, '清理请求状态加载失败'));
    } finally {
      setPurgeLoading(false);
    }
  }, []);

  useEffect(() => {
    if (!isReal || refreshNonce === 0) return;
    const requestId = positiveInteger(requestIdInputRef.current);
    if (requestId === undefined) return;
    const timer = window.setTimeout(() => {
      void readPurgeSnapshot(requestId);
    }, 0);
    return () => window.clearTimeout(timer);
  }, [isReal, readPurgeSnapshot, refreshNonce]);

  const readSubmittedPurge = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const requestId = positiveInteger(requestIdInput);
    if (requestId === undefined) {
      setPurgeError('清理请求 ID 必须是大于 0 的整数');
      return;
    }
    void readPurgeSnapshot(requestId);
  };

  const submitPurge = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setPurgeFormError('');
    if (!canManageRetention) {
      setPurgeFormError('当前角色没有创建清理请求的权限');
      return;
    }
    const resourceId = positiveInteger(purgeForm.resourceId);
    if (resourceId === undefined) {
      setPurgeFormError('资源 ID 必须是大于 0 的整数');
      return;
    }
    const targetLabel = `${resourceLabel(purgeForm.resourceType)}:${resourceId}`;
    onAction({
      action: '创建清理请求',
      targetLabel,
      targetType: 'retention_purge_request',
      targetId: String(resourceId),
      execute: async () => {
        if (!isReal) {
          setPurge(fixturePurge);
          setPreflight(fixturePreflight);
          setRequestIdInput(String(fixturePurge.request_id));
          return;
        }
        const result = await requestRetentionPurge(purgeForm.resourceType, resourceId);
        setPurge(result);
        setRequestIdInput(String(result.request_id));
        await readPurgeSnapshot(result.request_id);
        setPurgeForm(initialPurgeForm());
      },
    });
  };

  const approvePurge = () => {
    if (!purge || !isSuperadmin) return;
    const requestId = purge.request_id;
    onAction({
      action: '审批清理请求',
      targetLabel: `清理请求:${requestId}`,
      targetType: 'retention_purge_request',
      targetId: String(requestId),
      execute: async () => {
        if (!isReal) {
          setPurge({ ...purge, status: 'approved', task_count: 3 });
          setPreflight({ ...fixturePreflight, status: 'approved', request_id: requestId });
          return;
        }
        const result = await approveRetentionPurge(requestId);
        setPurge(result);
        await readPurgeSnapshot(requestId);
      },
    });
  };

  const submitHold = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setHoldFormError('');
    if (!canManageRetention) {
      setHoldFormError('当前角色没有创建法律保留的权限');
      return;
    }
    const resourceId = positiveInteger(holdForm.resourceId);
    if (resourceId === undefined) {
      setHoldFormError('资源 ID 必须是大于 0 的整数');
      return;
    }
    const targetLabel = `${resourceLabel(holdForm.resourceType)}:${resourceId}`;
    onAction({
      action: '创建法律保留',
      targetLabel,
      targetType: 'retention_hold',
      targetId: String(resourceId),
      execute: async () => {
        if (!isReal) {
          setHold(fixtureHold);
          return;
        }
        const result = await placeRetentionHold(holdForm.resourceType, resourceId, holdForm.reasonCode);
        setHold(result);
        setHoldForm(initialHoldForm());
      },
    });
  };

  const releaseHold = () => {
    if (!hold || !isSuperadmin) return;
    const holdId = hold.hold_id;
    onAction({
      action: '释放法律保留',
      targetLabel: `法律保留:${holdId}`,
      targetType: 'retention_hold',
      targetId: String(holdId),
      execute: async () => {
        if (!isReal) {
          setHold({ ...hold, status: 'released' });
          return;
        }
        setHold(await releaseRetentionHold(holdId));
      },
    });
  };

  return (
    <section className={styles.retentionSurface} aria-label="数据保留治理">
      <div className={styles.retentionIntro}>
        <div>
          <div className={styles.retentionIntroTitle}>
            <ShieldAlert aria-hidden="true" />
            <h2>数据保留治理</h2>
          </div>
          <p>清理申请、前置检查和法律保留均由服务端裁决；页面只展示安全摘要，不执行直接删除。</p>
        </div>
        <Badge variant="outline">{isReal ? 'real API' : 'fixture 预览'}</Badge>
      </div>

      {!canManageRetention ? (
        <div className={styles.retentionReadOnly} role="status">
          <LockKeyhole aria-hidden="true" />
          当前角色为 {role}，仅可读取清理请求详情和 preflight；创建、审批和释放操作受后端权限控制。
        </div>
      ) : null}

      <div className={styles.retentionGrid}>
        <Card className={styles.retentionCard}>
          <div className={styles.cardHead}>
            <div>
              <strong>创建清理请求</strong>
              <p className={styles.retentionCardHint}>资源必须已经软删除且满足服务端保留策略。</p>
            </div>
            <FileWarning aria-hidden="true" className={styles.retentionCardIcon} />
          </div>
          <form className={styles.retentionForm} onSubmit={submitPurge}>
            <label className={styles.retentionField}>
              <span>资源类型</span>
              <Select
                value={purgeForm.resourceType}
                onValueChange={(value: ResourceType) =>
                  setPurgeForm((current) => ({ ...current, resourceType: value }))
                }
                disabled={!canManageRetention}
              >
                <SelectTrigger aria-label="清理资源类型">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {resourceOptions.map((option) => (
                    <SelectItem key={option.value} value={option.value}>
                      {option.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </label>
            <label className={styles.retentionField}>
              <span>资源 ID</span>
              <Input
                aria-label="清理资源 ID"
                inputMode="numeric"
                min="1"
                type="number"
                value={purgeForm.resourceId}
                onChange={(event) => setPurgeForm((current) => ({ ...current, resourceId: event.target.value }))}
                placeholder="例如 42"
                disabled={!canManageRetention}
              />
            </label>
            {purgeFormError ? (
              <p className={styles.retentionFormError} role="alert">
                {purgeFormError}
              </p>
            ) : null}
            <div className={styles.retentionFormActions}>
              <Button type="submit" disabled={!canManageRetention}>
                <FileWarning aria-hidden="true" />
                提交清理申请
              </Button>
            </div>
          </form>
        </Card>

        <Card className={styles.retentionCard}>
          <div className={styles.cardHead}>
            <div>
              <strong>读取清理请求</strong>
              <p className={styles.retentionCardHint}>详情和 preflight 均为只读查询，支持 operator。</p>
            </div>
            <Search aria-hidden="true" className={styles.retentionCardIcon} />
          </div>
          <form className={styles.retentionQueryForm} onSubmit={readSubmittedPurge}>
            <label className={styles.retentionField}>
              <span>清理请求 ID</span>
              <Input
                aria-label="清理请求 ID"
                inputMode="numeric"
                min="1"
                type="number"
                value={requestIdInput}
                onChange={(event) => setRequestIdInput(event.target.value)}
                placeholder="例如 901"
              />
            </label>
            <Button type="submit" variant="outline" disabled={purgeLoading}>
              {purgeLoading ? (
                <LoaderCircle className={styles.operationSpinner} aria-hidden="true" />
              ) : (
                <Search aria-hidden="true" />
              )}
              读取详情与预检
            </Button>
          </form>
          {purgeError ? (
            <div className={styles.retentionError} role="alert">
              <span>{purgeError}</span>
              <Button
                size="sm"
                variant="outline"
                onClick={() => {
                  const requestId = positiveInteger(requestIdInput);
                  if (requestId !== undefined) void readPurgeSnapshot(requestId);
                }}
              >
                <RefreshCw aria-hidden="true" />
                重试
              </Button>
            </div>
          ) : null}
        </Card>
      </div>

      {purge ? (
        <Card className={styles.retentionCard}>
          <div className={styles.cardHead}>
            <div>
              <strong>清理请求状态</strong>
              <p className={styles.retentionCardHint}>
                请求 #{purge.request_id} · {resourceLabel(purge.resource_type)}:{purge.resource_id}
              </p>
            </div>
            <div className={styles.retentionHeaderActions}>
              {statusBadge(purge.status)}
              {purge.status === 'requested' && isSuperadmin ? (
                <Button size="sm" onClick={approvePurge}>
                  <CheckCircle2 aria-hidden="true" />
                  审批清理请求
                </Button>
              ) : null}
            </div>
          </div>
          <dl className={styles.retentionDetailGrid}>
            <div>
              <dt>资源类型</dt>
              <dd>{resourceLabel(purge.resource_type)}</dd>
            </div>
            <div>
              <dt>资源 ID</dt>
              <dd>{purge.resource_id}</dd>
            </div>
            <div>
              <dt>可清理时间</dt>
              <dd>{formatDate(purge.eligible_at)}</dd>
            </div>
            <div>
              <dt>已规划任务数</dt>
              <dd>{purge.task_count}</dd>
            </div>
          </dl>
        </Card>
      ) : null}

      {preflight ? (
        <Card className={styles.retentionCard}>
          <div className={styles.cardHead}>
            <div>
              <strong>执行前置检查</strong>
              <p className={styles.retentionCardHint}>只返回策略、保留期、保全和任务状态，不返回对象键或原始内容。</p>
            </div>
            <Badge variant={preflight.ready_to_execute ? 'default' : 'warning'}>
              {preflight.ready_to_execute ? '可执行' : '存在阻断'}
            </Badge>
          </div>
          <div className={styles.retentionFlags}>
            <Flag label="策略存在" value={preflight.policy_found} />
            <Flag label="硬删除已开启" value={preflight.hard_delete_enabled} />
            <Flag label="资源已软删除" value={preflight.resource_soft_deleted} />
            <Flag label="保留期已到" value={preflight.retention_elapsed} />
            <Flag label="无 active legal hold" value={preflight.legal_hold_clear} />
            <Flag label="任务契约有效" value={preflight.task_contract_valid} />
          </div>
          <div className={styles.retentionPreflightColumns}>
            <section>
              <h3>阻断码</h3>
              {preflight.blockers.length === 0 ? (
                <p className={styles.retentionEmpty}>无稳定阻断码</p>
              ) : (
                <ul className={styles.retentionCodeList}>
                  {preflight.blockers.map((blocker) => (
                    <li key={blocker}>{blocker}</li>
                  ))}
                </ul>
              )}
            </section>
            <section>
              <h3>清理任务</h3>
              {preflight.tasks.length === 0 ? (
                <p className={styles.retentionEmpty}>尚未生成任务</p>
              ) : (
                <div className={styles.retentionTaskTable} role="table" aria-label="清理任务状态">
                  <div className={styles.retentionTaskHeader} role="row">
                    <span>任务</span>
                    <span>状态</span>
                    <span>尝试次数</span>
                    <span>错误码</span>
                  </div>
                  {preflight.tasks.map((task) => (
                    <div className={styles.retentionTaskRow} role="row" key={task.task_type}>
                      <strong>{task.task_type}</strong>
                      <span>{statusBadge(task.status)}</span>
                      <span>{task.attempt_count}</span>
                      <code>{task.last_error_code ?? '-'}</code>
                    </div>
                  ))}
                </div>
              )}
            </section>
          </div>
        </Card>
      ) : null}

      <Card className={styles.retentionCard}>
        <div className={styles.cardHead}>
          <div>
            <strong>法律保留</strong>
            <p className={styles.retentionCardHint}>创建后会阻止相关清理审批和任务领取；释放仅限 superadmin。</p>
          </div>
          <LockKeyhole aria-hidden="true" className={styles.retentionCardIcon} />
        </div>
        <form className={styles.retentionForm} onSubmit={submitHold}>
          <label className={styles.retentionField}>
            <span>资源类型</span>
            <Select
              value={holdForm.resourceType}
              onValueChange={(value: ResourceType) => setHoldForm((current) => ({ ...current, resourceType: value }))}
              disabled={!canManageRetention}
            >
              <SelectTrigger aria-label="保留资源类型">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {resourceOptions.map((option) => (
                  <SelectItem key={option.value} value={option.value}>
                    {option.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </label>
          <label className={styles.retentionField}>
            <span>资源 ID</span>
            <Input
              aria-label="保留资源 ID"
              inputMode="numeric"
              min="1"
              type="number"
              value={holdForm.resourceId}
              onChange={(event) => setHoldForm((current) => ({ ...current, resourceId: event.target.value }))}
              placeholder="例如 42"
              disabled={!canManageRetention}
            />
          </label>
          <label className={styles.retentionField}>
            <span>保留原因</span>
            <Select
              value={holdForm.reasonCode}
              onValueChange={(value) => setHoldForm((current) => ({ ...current, reasonCode: value }))}
              disabled={!canManageRetention}
            >
              <SelectTrigger aria-label="保留原因">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {reasonOptions.map((option) => (
                  <SelectItem key={option.value} value={option.value}>
                    {option.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </label>
          {holdFormError ? (
            <p className={styles.retentionFormError} role="alert">
              {holdFormError}
            </p>
          ) : null}
          <div className={styles.retentionFormActions}>
            <Button type="submit" variant="outline" disabled={!canManageRetention}>
              <LockKeyhole aria-hidden="true" />
              创建法律保留
            </Button>
          </div>
        </form>
        {!isReal ? (
          <p className={styles.retentionFixtureNote}>Fixture 只展示示例保留状态，真实模式以服务端响应为准。</p>
        ) : null}
        {hold ? (
          <div className={styles.retentionHoldResult}>
            <div>
              <strong>法律保留 #{hold.hold_id}</strong>
              <span>
                {resourceLabel(hold.resource_type)}:{hold.resource_id} · {hold.reason_code}
              </span>
            </div>
            <div className={styles.retentionHeaderActions}>
              {statusBadge(hold.status)}
              {hold.status === 'active' && isSuperadmin ? (
                <Button size="sm" variant="destructive" onClick={releaseHold}>
                  <RefreshCw aria-hidden="true" />
                  释放保留
                </Button>
              ) : null}
            </div>
          </div>
        ) : (
          <p className={styles.retentionEmpty}>当前页面没有可展示的法律保留响应；后端暂未提供保留列表查询接口。</p>
        )}
      </Card>
    </section>
  );
}
