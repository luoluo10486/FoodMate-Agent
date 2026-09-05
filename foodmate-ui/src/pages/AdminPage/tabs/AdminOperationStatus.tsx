import { Alert } from '@/components/ui/alert';
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
import { AlertTriangle, Info, LoaderCircle, RefreshCw, XCircle } from 'lucide-react';
import styles from '../AdminPage.module.css';
import type { AdminActionPayload, AdminOperationError, AdminOperationState } from './types';

type AdminOperationStatusProps = {
  status: AdminOperationState;
  action?: AdminActionPayload;
  error?: AdminOperationError;
  onConfirm: () => void;
  onCancel: () => void;
  onRetry: () => void;
  onDismiss: () => void;
};

function AffectedResources() {
  return (
    <Card className={styles.operationAffectedBox}>
      <strong>
        <AlertTriangle aria-hidden="true" />
        以下关联资源将受直接影响：
      </strong>
      <span>• 3 个正在活跃调用的 Agent 任务</span>
      <span>• 2 个开发中的工作流模版</span>
    </Card>
  );
}

export function AdminOperationStatus({
  status,
  action,
  error,
  onConfirm,
  onCancel,
  onRetry,
  onDismiss,
}: AdminOperationStatusProps) {
  if (status === 'idle') return null;

  const isToolDisable = action?.action === '停用工具';

  if (status === 'no-permission') {
    return (
      <Alert className={`${styles.operationBanner} ${styles.operationPermissionBanner}`}>
        <Info aria-hidden="true" />
        <span>当前角色为 Operator，无写操作权限</span>
      </Alert>
    );
  }

  if (status === 'success') {
    return (
      <Alert className={`${styles.operationBanner} ${styles.operationSuccessBanner}`}>
        <Info aria-hidden="true" />
        <span>
          操作成功：
          {action?.action === '停用工具'
            ? `工具 ${action.targetLabel} 已成功停用`
            : `${action?.action} ${action?.targetLabel} 已完成`}
        </span>
      </Alert>
    );
  }

  if (status === 'confirm') {
    return (
      <Dialog open onOpenChange={(open) => !open && onCancel()}>
        <DialogContent
          className={styles.operationDialogCard}
          overlayClassName={styles.operationOverlay}
          aria-describedby="operation-confirm-description"
        >
          <DialogHeader className={styles.operationDialogHeader}>
            <span className={`${styles.operationIconWrapper} ${styles.operationWarningIcon}`}>
              <AlertTriangle aria-hidden="true" />
            </span>
            <DialogTitle>确认{action?.action}</DialogTitle>
          </DialogHeader>
          <DialogDescription asChild id="operation-confirm-description" className={styles.operationDialogBody}>
            <div>
              <p>
                您正在尝试{action?.action} <strong>{action?.targetLabel}</strong>。
              </p>
              <p className={styles.operationDialogMuted}>
                {isToolDisable
                  ? '停用后，所有关联的 Agent 运行将无法在调用流中激活此工具。'
                  : '该操作将立即生效并记录操作审计。'}
              </p>
              {isToolDisable ? <AffectedResources /> : null}
            </div>
          </DialogDescription>
          <DialogFooter className={styles.operationDialogActions}>
            <Button variant="outline" onClick={onCancel}>
              取消
            </Button>
            <Button className={styles.operationPrimaryButton} onClick={onConfirm}>
              确认{action?.action.replace('工具', '')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    );
  }

  if (status === 'submitting') {
    return (
      <Dialog open onOpenChange={() => undefined}>
        <DialogContent
          className={`${styles.operationDialogCard} ${styles.operationSubmittingCard}`}
          overlayClassName={styles.operationOverlay}
          aria-describedby="operation-submitting-description"
        >
          <DialogHeader className={styles.operationDialogHeader}>
            <span className={`${styles.operationIconWrapper} ${styles.operationWarningIcon}`}>
              <AlertTriangle aria-hidden="true" />
            </span>
            <DialogTitle>确认{action?.action}</DialogTitle>
          </DialogHeader>
          <DialogDescription asChild id="operation-submitting-description" className={styles.operationDialogBody}>
            <div>
              {isToolDisable ? (
                <>
                  <p>
                    您正在尝试{action?.action} <strong>{action?.targetLabel}</strong>。
                  </p>
                  <p className={styles.operationDialogMuted}>
                    停用后，所有关联的 Agent 运行将无法在调用流中激活此工具。
                  </p>
                </>
              ) : (
                <p>
                  正在{action?.action} <strong>{action?.targetLabel}</strong>，请稍候。
                </p>
              )}
              <div className={styles.operationProgressGroup}>
                <div className={styles.operationProgressTrack} aria-label="操作提交进度">
                  <span className={styles.operationProgressValue} />
                </div>
                <p className={styles.operationProgressLabel}>正在通知关联的服务集群同步状态...</p>
              </div>
            </div>
          </DialogDescription>
          <DialogFooter className={styles.operationDialogActions}>
            <Button variant="outline" disabled>
              取消
            </Button>
            <Button className={styles.operationSubmittingButton} disabled>
              <LoaderCircle className={styles.operationSpinner} aria-hidden="true" />
              提交中...
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    );
  }

  return (
    <Dialog open onOpenChange={(open) => !open && onDismiss()}>
      <DialogContent
        className={`${styles.operationDialogCard} ${styles.operationFailedCard}`}
        overlayClassName={styles.operationOverlay}
        aria-describedby="operation-failed-description"
      >
        <DialogHeader className={styles.operationDialogHeader}>
          <span className={`${styles.operationIconWrapper} ${styles.operationErrorIcon}`}>
            <XCircle aria-hidden="true" />
          </span>
          <DialogTitle>操作失败</DialogTitle>
        </DialogHeader>
        <DialogDescription asChild id="operation-failed-description" className={styles.operationDialogBody}>
          <div>
            <p>
              <strong>{error?.message ?? '操作未完成，请检查服务状态后重试。'}</strong>
            </p>
            <p className={styles.operationDialogMuted}>
              {isToolDisable
                ? '请求发送后，healthy-cluster-0 节点未能及时返回响应。当前配置未改变，请稍后重试。'
                : '服务端未确认本次变更，当前配置未改变。请检查错误码后重试。'}
            </p>
            <div className={styles.operationDebugBox}>
              <span>ERROR_CODE: {error?.code ?? 'REGISTRY_TIMEOUT_504'}</span>
              <span>REQUEST_ID: {error?.requestId ?? 'req-foodmate-9082ac918'}</span>
            </div>
          </div>
        </DialogDescription>
        <DialogFooter className={styles.operationDialogActions}>
          <Button variant="outline" onClick={onDismiss}>
            关闭
          </Button>
          <Button className={styles.operationPrimaryButton} onClick={onRetry}>
            <RefreshCw aria-hidden="true" />
            重新尝试
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
