import { apiRequest } from './apiClient';

export type MemoryRecord = {
  memory_id?: number;
  memoryId?: number;
  memory_type?: string;
  memoryType?: string;
  memory_key?: string;
  memoryKey?: string;
  memory_value?: string;
  memoryValue?: string;
  confidence?: number;
  source?: string;
  scope?: string;
  confirmation_status?: string;
  confirmationStatus?: string;
  expires_at?: string;
  expiresAt?: string;
  updated_at?: string;
  updatedAt?: string;
};

export function loadMemories(signal?: AbortSignal): Promise<MemoryRecord[]> {
  return apiRequest<MemoryRecord[]>('/api/memories', signal ? { signal } : {});
}

export function confirmMemory(memoryId: number, signal?: AbortSignal): Promise<MemoryRecord> {
  return apiRequest<MemoryRecord>(`/api/memories/${memoryId}/confirm`, {
    method: 'POST',
    ...(signal ? { signal } : {}),
  });
}

export function updateMemory(
  memoryId: number,
  memoryValue: string,
  scope?: string,
  signal?: AbortSignal,
): Promise<MemoryRecord> {
  return apiRequest<MemoryRecord>(`/api/memories/${memoryId}`, {
    method: 'PATCH',
    body: JSON.stringify({ memoryValue: serializeMemoryValue(memoryValue), scope }),
    ...(signal ? { signal } : {}),
  });
}

export function deleteMemory(memoryId: number, signal?: AbortSignal): Promise<void> {
  return apiRequest<void>(`/api/memories/${memoryId}`, {
    method: 'DELETE',
    ...(signal ? { signal } : {}),
  });
}

function serializeMemoryValue(memoryValue: string): string {
  const normalized = memoryValue.trim();
  if (!normalized) return '{}';

  try {
    const parsed = JSON.parse(normalized) as unknown;
    if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) {
      return JSON.stringify(parsed);
    }
  } catch {
    // 页面文本编辑通常是普通文字，需要继续按统一对象结构提交。
  }

  // 后端将 memory_value 作为 JSON 对象校验和保存，纯文本统一放入 value 字段。
  return JSON.stringify({ value: normalized });
}
