export type AdminSectionKey = 'overview' | 'users' | 'runs' | 'tools' | 'usage' | 'knowledge' | 'deleted';

export type AdminActionPayload = {
  action: string;
  targetLabel: string;
  targetType: string;
  targetId: string;
  onApply?: () => void;
  execute?: () => Promise<void>;
};
