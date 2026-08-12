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

export function loadMemories(): Promise<MemoryRecord[]> {
  return apiRequest<MemoryRecord[]>('/api/memories');
}

export function confirmMemory(memoryId: number): Promise<MemoryRecord> {
  return apiRequest<MemoryRecord>(`/api/memories/${memoryId}/confirm`, { method: 'POST' });
}

export function updateMemory(memoryId: number, memoryValue: string, scope?: string): Promise<MemoryRecord> {
  return apiRequest<MemoryRecord>(`/api/memories/${memoryId}`, {
    method: 'PATCH',
    body: JSON.stringify({ memoryValue: JSON.stringify(memoryValue), scope }),
  });
}

export function deleteMemory(memoryId: number): Promise<void> {
  return apiRequest<void>(`/api/memories/${memoryId}`, { method: 'DELETE' });
}
