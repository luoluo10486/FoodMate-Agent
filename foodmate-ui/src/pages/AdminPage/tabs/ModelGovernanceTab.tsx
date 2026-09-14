import { useCallback, useEffect, useRef, useState } from 'react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { LoaderCircle, Plus, RefreshCw } from 'lucide-react';
import {
  createModelBudget,
  createModelPrice,
  loadModelGovernance,
  updateModelCatalogStatus,
  updateModelProviderStatus,
  updateModelRoute,
  type CreateModelBudgetRequest,
  type CreateModelPriceRequest,
  type ModelGovernanceModel,
  type ModelGovernanceProvider,
  type ModelGovernanceRoute,
  type ModelGovernanceView,
} from '../../../services/adminService';
import { getAuthUser } from '../../../services/authService';
import { isAbortError } from '../../../services/apiClient';
import type { AdminActionPayload } from './types';
import styles from '../AdminPage.module.css';

type ModelGovernanceSectionProps = {
  onAction: (payload: AdminActionPayload) => void;
  refreshNonce: number;
};

type GovernanceFormKind = 'price' | 'budget';

type PriceFormState = {
  providerCode: string;
  modelName: string;
  priceVersion: string;
  inputPricePerMillion: string;
  outputPricePerMillion: string;
  currency: string;
  effectiveAt: string;
  revision: string;
};

type BudgetFormState = {
  policyKey: string;
  scene: string;
  scopeType: string;
  maxTotalTokens: string;
  maxCostCny: string;
  maxModelCalls: string;
  maxStepRetries: string;
  windowType: string;
  policyVersion: string;
  revision: string;
};

function initialPriceForm(): PriceFormState {
  return {
    providerCode: '',
    modelName: '',
    priceVersion: '',
    inputPricePerMillion: '',
    outputPricePerMillion: '',
    currency: 'CNY',
    effectiveAt: '',
    revision: '1',
  };
}

function initialBudgetForm(): BudgetFormState {
  return {
    policyKey: '',
    scene: 'chat',
    scopeType: 'global',
    maxTotalTokens: '50000',
    maxCostCny: '0',
    maxModelCalls: '10',
    maxStepRetries: '2',
    windowType: 'run',
    policyVersion: '',
    revision: '1',
  };
}

function requiredValue(value: string, label: string) {
  return value.trim() ? undefined : `${label}不能为空`;
}

function nonNegativeNumber(value: string, label: string) {
  if (!value.trim()) return `${label}不能为空`;
  const parsed = Number(value);
  return Number.isFinite(parsed) && parsed >= 0 ? undefined : `${label}必须是大于等于 0 的数字`;
}

function positiveInteger(value: string, label: string) {
  if (!value.trim()) return `${label}不能为空`;
  const parsed = Number(value);
  return Number.isInteger(parsed) && parsed >= 1 ? undefined : `${label}必须是大于等于 1 的整数`;
}

function nonNegativeInteger(value: string, label: string) {
  if (!value.trim()) return `${label}不能为空`;
  const parsed = Number(value);
  return Number.isInteger(parsed) && parsed >= 0 ? undefined : `${label}必须是大于等于 0 的整数`;
}

function toInstant(value: string) {
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? undefined : parsed.toISOString();
}

function formatNumber(value: number | string | null | undefined) {
  if (value == null || value === '') return '-';
  return Number(value).toLocaleString('zh-CN');
}

function statusBadge(status: string) {
  return (
    <Badge variant={status === 'active' ? 'default' : status === 'disabled' ? 'destructive' : 'warning'}>
      {status}
    </Badge>
  );
}

function ToggleButton({ status, disabled, onClick }: { status: string; disabled: boolean; onClick: () => void }) {
  return (
    <Button size="sm" variant="outline" disabled={disabled} onClick={onClick}>
      {status === 'active' ? '停用' : '启用'}
    </Button>
  );
}

export function ModelGovernanceSection({ onAction, refreshNonce }: ModelGovernanceSectionProps) {
  const isReal = import.meta.env.VITE_AGENT_MODE === 'real';
  const isSuperadmin = getAuthUser().role === 'superadmin';
  const [data, setData] = useState<ModelGovernanceView>();
  const [loading, setLoading] = useState(isReal);
  const [error, setError] = useState('');
  const [formKind, setFormKind] = useState<GovernanceFormKind>();
  const [formError, setFormError] = useState('');
  const [priceForm, setPriceForm] = useState<PriceFormState>(initialPriceForm);
  const [budgetForm, setBudgetForm] = useState<BudgetFormState>(initialBudgetForm);
  const requestVersion = useRef(0);
  const requestController = useRef<AbortController>();
  const mountedRef = useRef(true);

  const refresh = useCallback(
    async (signal?: AbortSignal) => {
      if (!isReal) return;
      requestController.current?.abort();
      const controller = new AbortController();
      requestController.current = controller;
      const version = ++requestVersion.current;
      const abortFromAction = () => controller.abort();
      signal?.addEventListener('abort', abortFromAction, { once: true });
      if (signal?.aborted) controller.abort();
      setLoading(true);
      setError('');
      setData(undefined);
      try {
        const nextData = await loadModelGovernance({}, controller.signal);
        if (controller.signal.aborted || version !== requestVersion.current) return;
        setData(nextData);
      } catch (cause) {
        if (controller.signal.aborted || version !== requestVersion.current || isAbortError(cause)) return;
        setData(undefined);
        setError(cause instanceof Error ? cause.message : '模型治理数据加载失败');
      } finally {
        signal?.removeEventListener('abort', abortFromAction);
        if (requestController.current === controller) requestController.current = undefined;
        if (mountedRef.current && version === requestVersion.current) setLoading(false);
      }
    },
    [isReal],
  );

  useEffect(() => {
    // 读取请求属于页面生命周期，切换刷新批次或卸载页面时必须取消旧请求。
    mountedRef.current = true;
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void refresh();
    return () => {
      mountedRef.current = false;
      requestController.current?.abort();
      requestVersion.current += 1;
    };
  }, [refresh, refreshNonce]);

  const requestProviderStatus = (provider: ModelGovernanceProvider) => {
    const status = provider.status === 'active' ? 'disabled' : 'active';
    onAction({
      action: status === 'disabled' ? '停用模型供应商' : '启用模型供应商',
      targetLabel: provider.provider_code,
      targetType: 'model_provider',
      targetId: provider.provider_code,
      execute: async (signal) => {
        await updateModelProviderStatus(provider, status, signal);
        await refresh(signal);
      },
    });
  };

  const requestModelStatus = (model: ModelGovernanceModel) => {
    const status = model.status === 'active' ? 'disabled' : 'active';
    onAction({
      action: status === 'disabled' ? '停用模型' : '启用模型',
      targetLabel: `${model.provider_code}/${model.model_name}`,
      targetType: 'model_catalog',
      targetId: String(model.model_id),
      execute: async (signal) => {
        await updateModelCatalogStatus(model, status, signal);
        await refresh(signal);
      },
    });
  };

  const requestRouteStatus = (route: ModelGovernanceRoute) => {
    const status = route.status === 'active' ? 'disabled' : 'active';
    onAction({
      action: status === 'disabled' ? '停用模型路由' : '启用模型路由',
      targetLabel: `${route.scene}/${route.model_type}`,
      targetType: 'model_route_rule',
      targetId: String(route.route_id),
      execute: async (signal) => {
        await updateModelRoute(route, status, signal);
        await refresh(signal);
      },
    });
  };

  const closeForm = () => {
    setFormKind(undefined);
    setFormError('');
  };

  const openPriceForm = () => {
    setPriceForm(initialPriceForm());
    setFormError('');
    setFormKind('price');
  };

  const openBudgetForm = () => {
    setBudgetForm(initialBudgetForm());
    setFormError('');
    setFormKind('budget');
  };

  const submitPrice = () => {
    const validationError =
      requiredValue(priceForm.providerCode, '供应商代码') ??
      requiredValue(priceForm.modelName, '模型名称') ??
      requiredValue(priceForm.priceVersion, '价格版本') ??
      nonNegativeNumber(priceForm.inputPricePerMillion, '输入价格') ??
      nonNegativeNumber(priceForm.outputPricePerMillion, '输出价格') ??
      requiredValue(priceForm.currency, '币种') ??
      requiredValue(priceForm.effectiveAt, '生效时间') ??
      positiveInteger(priceForm.revision, 'revision');
    const effectiveAt = toInstant(priceForm.effectiveAt);
    if (validationError || !effectiveAt) {
      setFormError(validationError ?? '生效时间格式无效');
      return;
    }

    const request: CreateModelPriceRequest = {
      providerCode: priceForm.providerCode.trim(),
      modelName: priceForm.modelName.trim(),
      priceVersion: priceForm.priceVersion.trim(),
      inputPricePerMillion: priceForm.inputPricePerMillion.trim(),
      outputPricePerMillion: priceForm.outputPricePerMillion.trim(),
      currency: priceForm.currency.trim(),
      effectiveAt,
      revision: Number(priceForm.revision),
    };
    const targetLabel = `${request.providerCode}/${request.modelName} · ${request.priceVersion}`;
    closeForm();
    onAction({
      action: '新增模型价格',
      targetLabel,
      targetType: 'model_price_version',
      targetId: targetLabel,
      execute: async (signal) => {
        await createModelPrice(request, signal);
        await refresh(signal);
      },
    });
  };

  const submitBudget = () => {
    const validationError =
      requiredValue(budgetForm.policyKey, '策略键') ??
      requiredValue(budgetForm.scene, '场景') ??
      requiredValue(budgetForm.scopeType, '作用域') ??
      positiveInteger(budgetForm.maxTotalTokens, '最大 Token 数') ??
      nonNegativeNumber(budgetForm.maxCostCny, '最大费用') ??
      positiveInteger(budgetForm.maxModelCalls, '最大模型调用次数') ??
      nonNegativeInteger(budgetForm.maxStepRetries, '最大步骤重试次数') ??
      requiredValue(budgetForm.windowType, '窗口') ??
      requiredValue(budgetForm.policyVersion, '策略版本') ??
      positiveInteger(budgetForm.revision, 'revision');
    if (validationError) {
      setFormError(validationError);
      return;
    }

    const request: CreateModelBudgetRequest = {
      policyKey: budgetForm.policyKey.trim(),
      scene: budgetForm.scene.trim(),
      scopeType: budgetForm.scopeType,
      maxTotalTokens: Number(budgetForm.maxTotalTokens),
      maxCostCny: budgetForm.maxCostCny.trim(),
      maxModelCalls: Number(budgetForm.maxModelCalls),
      maxStepRetries: Number(budgetForm.maxStepRetries),
      windowType: budgetForm.windowType,
      policyVersion: budgetForm.policyVersion.trim(),
      revision: Number(budgetForm.revision),
    };
    const targetLabel = `${request.policyKey} · ${request.policyVersion}`;
    closeForm();
    onAction({
      action: '新增模型预算',
      targetLabel,
      targetType: 'model_budget_policy',
      targetId: targetLabel,
      execute: async (signal) => {
        await createModelBudget(request, signal);
        await refresh(signal);
      },
    });
  };

  if (!isReal) {
    return (
      <section className={styles.modelGovernanceSurface} aria-label="模型治理样例">
        <Card className={styles.wideCard}>
          <div className={styles.cardHead}>
            <strong>模型治理</strong>
            <Badge variant="outline">fixture</Badge>
          </div>
          <p className={styles.modelGovernanceMuted}>
            fixture 模式仅用于页面预览；切换 VITE_AGENT_MODE=real 后读取服务端治理数据。
          </p>
        </Card>
      </section>
    );
  }

  if (loading && !data) {
    return (
      <Card className={styles.modelGovernanceState} role="status">
        <LoaderCircle className={styles.operationSpinner} aria-hidden="true" />
        正在加载模型治理数据
      </Card>
    );
  }

  if (error && !data) {
    return (
      <Card className={styles.modelGovernanceState} role="alert">
        <strong>{error}</strong>
        <Button variant="outline" onClick={() => void refresh()}>
          <RefreshCw aria-hidden="true" />
          重试
        </Button>
      </Card>
    );
  }

  const governance = data ?? { providers: [], models: [], routes: [], prices: [], budgets: [], usage: [] };
  return (
    <section className={styles.modelGovernanceSurface} aria-label="模型治理">
      {error ? (
        <div className={styles.notice} role="alert">
          {error}
        </div>
      ) : null}
      {!isSuperadmin ? (
        <div className={styles.modelGovernanceReadOnly} role="status">
          当前角色仅可查看模型治理；状态变更需要 superadmin。
        </div>
      ) : null}
      <div className={styles.sectionCards}>
        <article className={`${styles.metric} ${styles.green}`}>
          <span>供应商</span>
          <strong>{governance.providers.length}</strong>
          <em>配置状态已脱敏</em>
        </article>
        <article className={`${styles.metric} ${styles.orange}`}>
          <span>模型</span>
          <strong>{governance.models.length}</strong>
          <em>目录状态</em>
        </article>
        <article className={`${styles.metric} ${styles.blue}`}>
          <span>路由</span>
          <strong>{governance.routes.length}</strong>
          <em>当前版本</em>
        </article>
        <article className={`${styles.metric} ${styles.purple}`}>
          <span>调用记录</span>
          <strong>{governance.usage.reduce((total, row) => total + row.calls, 0)}</strong>
          <em>按服务端汇总</em>
        </article>
      </div>
      <Card className={styles.wideCard}>
        <div className={styles.cardHead}>
          <strong>供应商与模型</strong>
          <Badge variant="outline">API Key 仅显示配置状态和指纹</Badge>
        </div>
        <div className={styles.modelGovernanceGrid}>
          <div className={styles.modelGovernanceTable}>
            <h2>供应商</h2>
            <div className={styles.modelGovernanceTableHeader}>
              <span>代码</span>
              <span>状态</span>
              <span>凭据</span>
              <span>操作</span>
            </div>
            {governance.providers.length === 0 ? (
              <p className={styles.modelGovernanceEmpty}>暂无供应商</p>
            ) : (
              governance.providers.map((provider) => (
                <div className={styles.modelGovernanceTableRow} key={provider.provider_id}>
                  <strong>{provider.provider_code}</strong>
                  <span>{statusBadge(provider.status)}</span>
                  <span>{provider.configured ? provider.fingerprint : '未配置'}</span>
                  <ToggleButton
                    status={provider.status}
                    disabled={!isSuperadmin}
                    onClick={() => requestProviderStatus(provider)}
                  />
                </div>
              ))
            )}
          </div>
          <div className={styles.modelGovernanceTable}>
            <h2>模型目录</h2>
            <div className={styles.modelGovernanceTableHeader}>
              <span>模型</span>
              <span>类型</span>
              <span>超时</span>
              <span>操作</span>
            </div>
            {governance.models.length === 0 ? (
              <p className={styles.modelGovernanceEmpty}>暂无模型</p>
            ) : (
              governance.models.map((model) => (
                <div className={styles.modelGovernanceTableRow} key={model.model_id}>
                  <strong>
                    {model.provider_code}/{model.model_name}
                  </strong>
                  <span>{model.model_type}</span>
                  <span>{formatNumber(model.timeout_ms)} ms</span>
                  <ToggleButton
                    status={model.status}
                    disabled={!isSuperadmin}
                    onClick={() => requestModelStatus(model)}
                  />
                </div>
              ))
            )}
          </div>
        </div>
      </Card>
      <Card className={styles.wideCard}>
        <div className={styles.cardHead}>
          <strong>模型路由</strong>
          <Badge variant="outline">revision 乐观锁</Badge>
        </div>
        <div className={styles.modelGovernanceTableWide}>
          <div className={styles.modelGovernanceTableHeader}>
            <span>场景</span>
            <span>主模型</span>
            <span>Fallback</span>
            <span>版本</span>
            <span>状态</span>
            <span>操作</span>
          </div>
          {governance.routes.length === 0 ? (
            <p className={styles.modelGovernanceEmpty}>暂无路由</p>
          ) : (
            governance.routes.map((route) => (
              <div className={styles.modelGovernanceTableRow} key={route.route_id}>
                <strong>
                  {route.scene}/{route.model_type}
                </strong>
                <span>
                  {route.provider_code}/{route.model_name}
                </span>
                <span>
                  {route.fallback_provider_code ? `${route.fallback_provider_code}/${route.fallback_model_name}` : '-'}
                </span>
                <span>{route.route_version}</span>
                <span>{statusBadge(route.status)}</span>
                <ToggleButton
                  status={route.status}
                  disabled={!isSuperadmin}
                  onClick={() => requestRouteStatus(route)}
                />
              </div>
            ))
          )}
        </div>
      </Card>
      <Card className={styles.wideCard}>
        <div className={styles.cardHead}>
          <strong>价格、预算与调用汇总</strong>
          <div className={styles.modelGovernanceHeaderActions}>
            {isSuperadmin ? (
              <>
                <Button size="sm" variant="outline" onClick={openPriceForm}>
                  <Plus aria-hidden="true" />
                  新增价格
                </Button>
                <Button size="sm" variant="outline" onClick={openBudgetForm}>
                  <Plus aria-hidden="true" />
                  新增预算
                </Button>
              </>
            ) : null}
            <Button size="sm" variant="outline" onClick={() => void refresh()}>
              <RefreshCw aria-hidden="true" />
              刷新
            </Button>
          </div>
        </div>
        <div className={styles.modelGovernanceSummaryGrid}>
          <div>
            <h2>价格版本</h2>
            {governance.prices.length === 0 ? (
              <p className={styles.modelGovernanceEmpty}>暂无价格版本</p>
            ) : (
              governance.prices.map((price) => (
                <p key={price.price_version_id}>
                  <strong>
                    {price.provider_code}/{price.model_name}
                  </strong>
                  <span>
                    {price.price_version} · {price.currency} · {price.status}
                  </span>
                </p>
              ))
            )}
          </div>
          <div>
            <h2>预算策略</h2>
            {governance.budgets.length === 0 ? (
              <p className={styles.modelGovernanceEmpty}>暂无预算策略</p>
            ) : (
              governance.budgets.map((budget) => (
                <p key={budget.budget_policy_id}>
                  <strong>{budget.policy_key}</strong>
                  <span>
                    {budget.policy_version} · {formatNumber(budget.max_total_tokens)} tokens
                  </span>
                </p>
              ))
            )}
          </div>
          <div>
            <h2>用量汇总</h2>
            {governance.usage.length === 0 ? (
              <p className={styles.modelGovernanceEmpty}>暂无调用记录</p>
            ) : (
              governance.usage.map((usage) => (
                <p key={`${usage.provider_code}-${usage.model_name}-${usage.scene}`}>
                  <strong>
                    {usage.provider_code}/{usage.model_name}
                  </strong>
                  <span>
                    {usage.scene} · {formatNumber(usage.total_tokens)} tokens · {formatNumber(usage.total_cost)} CNY
                  </span>
                </p>
              ))
            )}
          </div>
        </div>
      </Card>
      <Dialog open={Boolean(formKind)} onOpenChange={(open) => !open && closeForm()}>
        <DialogContent className={styles.modelGovernanceDialog}>
          <DialogHeader>
            <DialogTitle>{formKind === 'price' ? '新增价格版本' : '新增预算策略'}</DialogTitle>
            <DialogDescription>
              {formKind === 'price'
                ? '新增价格会写入模型治理历史，生效时间和价格版本提交后不可在本表单内修改。'
                : '新增预算策略会影响后续 Agent Run，提交前请核对作用域、窗口和上限。'}
            </DialogDescription>
          </DialogHeader>
          {formKind === 'price' ? (
            <div className={styles.modelGovernanceForm}>
              <label className={styles.modelGovernanceField}>
                <span>供应商代码</span>
                <Input
                  value={priceForm.providerCode}
                  aria-label="供应商代码"
                  onChange={(event) => setPriceForm((current) => ({ ...current, providerCode: event.target.value }))}
                  placeholder="cloud_primary"
                />
              </label>
              <label className={styles.modelGovernanceField}>
                <span>模型名称</span>
                <Input
                  value={priceForm.modelName}
                  aria-label="模型名称"
                  onChange={(event) => setPriceForm((current) => ({ ...current, modelName: event.target.value }))}
                  placeholder="模型名称"
                />
              </label>
              <label className={styles.modelGovernanceField}>
                <span>价格版本</span>
                <Input
                  value={priceForm.priceVersion}
                  aria-label="价格版本"
                  onChange={(event) => setPriceForm((current) => ({ ...current, priceVersion: event.target.value }))}
                  placeholder="price-v1"
                />
              </label>
              <label className={styles.modelGovernanceField}>
                <span>币种</span>
                <Input
                  value={priceForm.currency}
                  aria-label="币种"
                  onChange={(event) => setPriceForm((current) => ({ ...current, currency: event.target.value }))}
                  placeholder="CNY"
                />
              </label>
              <label className={styles.modelGovernanceField}>
                <span>输入价格 / 百万 Token</span>
                <Input
                  type="number"
                  min="0"
                  step="0.000001"
                  value={priceForm.inputPricePerMillion}
                  aria-label="输入价格 / 百万 Token"
                  onChange={(event) =>
                    setPriceForm((current) => ({ ...current, inputPricePerMillion: event.target.value }))
                  }
                  placeholder="0"
                />
              </label>
              <label className={styles.modelGovernanceField}>
                <span>输出价格 / 百万 Token</span>
                <Input
                  type="number"
                  min="0"
                  step="0.000001"
                  value={priceForm.outputPricePerMillion}
                  aria-label="输出价格 / 百万 Token"
                  onChange={(event) =>
                    setPriceForm((current) => ({ ...current, outputPricePerMillion: event.target.value }))
                  }
                  placeholder="0"
                />
              </label>
              <label className={`${styles.modelGovernanceField} ${styles.modelGovernanceFieldWide}`}>
                <span>生效时间</span>
                <Input
                  type="datetime-local"
                  value={priceForm.effectiveAt}
                  aria-label="生效时间"
                  onChange={(event) => setPriceForm((current) => ({ ...current, effectiveAt: event.target.value }))}
                />
              </label>
              <label className={styles.modelGovernanceField}>
                <span>revision</span>
                <Input
                  type="number"
                  min="1"
                  step="1"
                  value={priceForm.revision}
                  aria-label="价格 revision"
                  onChange={(event) => setPriceForm((current) => ({ ...current, revision: event.target.value }))}
                />
              </label>
            </div>
          ) : (
            <div className={styles.modelGovernanceForm}>
              <label className={styles.modelGovernanceField}>
                <span>策略键</span>
                <Input
                  value={budgetForm.policyKey}
                  aria-label="策略键"
                  onChange={(event) => setBudgetForm((current) => ({ ...current, policyKey: event.target.value }))}
                  placeholder="agent-default"
                />
              </label>
              <label className={styles.modelGovernanceField}>
                <span>场景</span>
                <Input
                  value={budgetForm.scene}
                  aria-label="预算场景"
                  onChange={(event) => setBudgetForm((current) => ({ ...current, scene: event.target.value }))}
                  placeholder="chat"
                />
              </label>
              <label className={styles.modelGovernanceField}>
                <span>作用域</span>
                <Select
                  value={budgetForm.scopeType}
                  onValueChange={(value) => setBudgetForm((current) => ({ ...current, scopeType: value }))}
                >
                  <SelectTrigger aria-label="预算作用域">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="global">global · 全局</SelectItem>
                    <SelectItem value="scene">scene · 场景</SelectItem>
                    <SelectItem value="user">user · 用户</SelectItem>
                  </SelectContent>
                </Select>
              </label>
              <label className={styles.modelGovernanceField}>
                <span>窗口</span>
                <Select
                  value={budgetForm.windowType}
                  onValueChange={(value) => setBudgetForm((current) => ({ ...current, windowType: value }))}
                >
                  <SelectTrigger aria-label="预算窗口">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="run">run · 单次运行</SelectItem>
                    <SelectItem value="day">day · 每日</SelectItem>
                  </SelectContent>
                </Select>
              </label>
              <label className={styles.modelGovernanceField}>
                <span>最大 Token 数</span>
                <Input
                  type="number"
                  min="1"
                  step="1"
                  value={budgetForm.maxTotalTokens}
                  aria-label="最大 Token 数"
                  onChange={(event) => setBudgetForm((current) => ({ ...current, maxTotalTokens: event.target.value }))}
                />
              </label>
              <label className={styles.modelGovernanceField}>
                <span>最大费用（CNY）</span>
                <Input
                  type="number"
                  min="0"
                  step="0.01"
                  value={budgetForm.maxCostCny}
                  aria-label="最大费用（CNY）"
                  onChange={(event) => setBudgetForm((current) => ({ ...current, maxCostCny: event.target.value }))}
                />
              </label>
              <label className={styles.modelGovernanceField}>
                <span>最大模型调用次数</span>
                <Input
                  type="number"
                  min="1"
                  step="1"
                  value={budgetForm.maxModelCalls}
                  aria-label="最大模型调用次数"
                  onChange={(event) => setBudgetForm((current) => ({ ...current, maxModelCalls: event.target.value }))}
                />
              </label>
              <label className={styles.modelGovernanceField}>
                <span>最大步骤重试次数</span>
                <Input
                  type="number"
                  min="0"
                  step="1"
                  value={budgetForm.maxStepRetries}
                  aria-label="最大步骤重试次数"
                  onChange={(event) => setBudgetForm((current) => ({ ...current, maxStepRetries: event.target.value }))}
                />
              </label>
              <label className={styles.modelGovernanceField}>
                <span>策略版本</span>
                <Input
                  value={budgetForm.policyVersion}
                  aria-label="策略版本"
                  onChange={(event) => setBudgetForm((current) => ({ ...current, policyVersion: event.target.value }))}
                  placeholder="budget-v1"
                />
              </label>
              <label className={styles.modelGovernanceField}>
                <span>revision</span>
                <Input
                  type="number"
                  min="1"
                  step="1"
                  value={budgetForm.revision}
                  aria-label="预算 revision"
                  onChange={(event) => setBudgetForm((current) => ({ ...current, revision: event.target.value }))}
                />
              </label>
            </div>
          )}
          {formError ? (
            <div className={styles.modelGovernanceFormError} role="alert">
              {formError}
            </div>
          ) : null}
          <DialogFooter>
            <Button type="button" variant="outline" onClick={closeForm}>
              取消
            </Button>
            <Button type="button" onClick={formKind === 'price' ? submitPrice : submitBudget}>
              {formKind === 'price' ? '提交新增价格' : '提交新增预算'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </section>
  );
}
