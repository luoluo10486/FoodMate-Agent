export type AdminSectionKey = 'overview' | 'users' | 'runs' | 'tools' | 'usage' | 'knowledge' | 'deleted';

export type AdminOperationState = 'idle' | 'no-permission' | 'confirm' | 'submitting' | 'success' | 'failed';

export type AdminOperationError = {
  code: string;
  requestId: string;
  message: string;
};

export type AdminActionPayload = {
  action: string;
  targetLabel: string;
  targetType: string;
  targetId: string;
  onApply?: () => void;
  execute?: () => Promise<void>;
};
