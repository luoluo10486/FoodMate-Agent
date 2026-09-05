import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { AdminOperationStatus } from './AdminOperationStatus';
import type { AdminActionPayload } from './types';

const action: AdminActionPayload = {
  action: '停用工具',
  targetLabel: 'nutrition_lookup',
  targetType: 'tool',
  targetId: 'nutrition_lookup',
};

const noop = vi.fn();

function renderStatus(status: Parameters<typeof AdminOperationStatus>[0]['status']) {
  return render(
    <AdminOperationStatus
      status={status}
      action={action}
      error={{
        code: 'REGISTRY_TIMEOUT_504',
        requestId: 'req-foodmate-9082ac918',
        message: '管理服务未能在规定时间内完成请求，请检查服务状态后重试。',
      }}
      onConfirm={noop}
      onCancel={noop}
      onRetry={noop}
      onDismiss={noop}
    />,
  );
}

describe('AdminOperationStatus', () => {
  it('renders the Figma confirmation, submitting and failure contracts', async () => {
    const user = userEvent.setup();
    const { rerender } = renderStatus('confirm');

    expect(screen.getByRole('dialog', { name: '确认停用工具' })).toBeInTheDocument();
    expect(screen.getByText(/3 个正在活跃调用的 Agent 任务/)).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '确认停用' }));
    expect(noop).toHaveBeenCalled();

    rerender(
      <AdminOperationStatus
        status="submitting"
        action={action}
        onConfirm={noop}
        onCancel={noop}
        onRetry={noop}
        onDismiss={noop}
      />,
    );
    expect(screen.getByRole('dialog', { name: '确认停用工具' })).toBeInTheDocument();
    expect(screen.getByLabelText('操作提交进度')).toBeInTheDocument();
    expect(screen.getByText(/您正在尝试停用工具/)).toHaveTextContent('nutrition_lookup');
    expect(screen.getByText('停用后，所有关联的 Agent 运行将无法在调用流中激活此工具。')).toBeInTheDocument();
    expect(screen.getByText('正在通知关联的服务集群同步状态...')).toBeInTheDocument();

    rerender(
      <AdminOperationStatus
        status="failed"
        action={action}
        error={{
          code: 'REGISTRY_TIMEOUT_504',
          requestId: 'req-foodmate-9082ac918',
          message: '管理服务未能在规定时间内完成请求，请检查服务状态后重试。',
        }}
        onConfirm={noop}
        onCancel={noop}
        onRetry={noop}
        onDismiss={noop}
      />,
    );
    expect(screen.getByRole('dialog', { name: '操作失败' })).toBeInTheDocument();
    expect(screen.getByText('管理服务未能在规定时间内完成请求，请检查服务状态后重试。')).toBeInTheDocument();
    expect(screen.getByText(/当前配置未改变/)).toBeInTheDocument();
    expect(screen.getByText(/healthy-cluster-0 节点未能及时返回响应/)).toBeInTheDocument();
    expect(screen.getByText('ERROR_CODE: REGISTRY_TIMEOUT_504')).toBeInTheDocument();
    expect(screen.getByText('REQUEST_ID: req-foodmate-9082ac918')).toBeInTheDocument();
    expect(document.querySelector('[class*="operationErrorIcon"]')).toBeInTheDocument();
    const operationActions = document.querySelector('[class*="operationDialogActions"]');
    expect(operationActions).not.toBeNull();
    expect(within(operationActions as HTMLElement).getByRole('button', { name: '关闭' })).toBeInTheDocument();
  });

  it('shows the operator no-permission banner and a dismissible success banner', async () => {
    const { rerender } = renderStatus('no-permission');

    expect(screen.getByRole('alert')).toHaveTextContent('Operator');
    rerender(
      <AdminOperationStatus
        status="success"
        action={action}
        onConfirm={noop}
        onCancel={noop}
        onRetry={noop}
        onDismiss={noop}
      />,
    );
    expect(screen.getByRole('alert')).toHaveTextContent('操作成功');
    expect(screen.getByRole('alert')).toHaveTextContent('操作成功：工具 nutrition_lookup 已成功停用');
  });
});
