import { useCallback, useEffect, useState } from 'react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { LoaderCircle, RefreshCw } from 'lucide-react';
import {
  loadModelGovernance,
  updateModelCatalogStatus,
  updateModelProviderStatus,
  updateModelRoute,
  type ModelGovernanceModel,
  type ModelGovernanceProvider,
  type ModelGovernanceRoute,
  type ModelGovernanceView,
} from '../../../services/adminService';
import { getAuthUser } from '../../../services/authService';
import type { AdminActionPayload } from './types';
import styles from '../AdminPage.module.css';

type ModelGovernanceSectionProps = {
  onAction: (payload: AdminActionPayload) => void;
  refreshNonce: number;
};

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

  const refresh = useCallback(async () => {
    if (!isReal) return;
    setLoading(true);
    setError('');
    try {
      setData(await loadModelGovernance());
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : '模型治理数据加载失败');
    } finally {
      setLoading(false);
    }
  }, [isReal]);

  useEffect(() => {
    void refresh();
  }, [refresh, refreshNonce]);

  const requestProviderStatus = (provider: ModelGovernanceProvider) => {
    const status = provider.status === 'active' ? 'disabled' : 'active';
    onAction({
      action: status === 'disabled' ? '停用模型供应商' : '启用模型供应商',
      targetLabel: provider.provider_code,
      targetType: 'model_provider',
      targetId: provider.provider_code,
      execute: async () => {
        await updateModelProviderStatus(provider, status);
        await refresh();
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
      execute: async () => {
        await updateModelCatalogStatus(model, status);
        await refresh();
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
      execute: async () => {
        await updateModelRoute(route, status);
        await refresh();
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
          <Button size="sm" variant="outline" onClick={() => void refresh()}>
            <RefreshCw aria-hidden="true" />
            刷新
          </Button>
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
    </section>
  );
}
