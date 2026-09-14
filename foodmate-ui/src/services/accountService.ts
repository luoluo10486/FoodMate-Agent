import { apiRequest } from './apiClient';

function requestInit(signal?: AbortSignal): RequestInit {
  return signal ? { signal } : {};
}

export type Profile = {
  user_id: number;
  display_name?: string;
  gender?: string;
  height_cm?: number;
  weight_kg?: number;
  activity_level?: string;
  diet_goal?: string;
  calorie_target?: number;
  protein_target?: number;
  allergens?: string;
  dislikes?: string;
  preferred_units?: string;
};
export type AuthSession = {
  auth_session_id: number;
  device_id?: string;
  user_agent?: string;
  ip_address?: string;
  expires_at: string;
  last_seen_at?: string;
  created_at?: string;
  revoked_at?: string;
};
export type ProfileUpdateRequest = {
  display_name?: string;
  gender?: string;
  height_cm?: number;
  weight_kg?: number;
  activity_level?: string;
  diet_goal?: string;
  calorie_target?: number;
  protein_target?: number;
  allergens?: string[];
  dislikes?: string[];
  preferred_units?: Record<string, string>;
};

type ExportJobResponse = {
  export_job_id?: number;
  exportJobId?: number;
  status?: string;
  expires_at?: string;
  expiresAt?: string;
  completed_at?: string;
  completedAt?: string;
  download_consumed_at?: string;
  downloadConsumedAt?: string;
  failure_code?: string;
  failureCode?: string;
};

export type ExportJob = {
  export_job_id: number;
  status: string;
  expires_at?: string;
  completed_at?: string;
  download_consumed_at?: string;
  failure_code?: string;
};

export const getProfile = (signal?: AbortSignal) =>
  apiRequest<Profile>('/api/users/me/profile', signal ? { signal } : {});
export const updateProfile = (profile: ProfileUpdateRequest, signal?: AbortSignal) =>
  apiRequest<Profile>('/api/users/me/profile', {
    method: 'PUT',
    body: JSON.stringify(profile),
    ...requestInit(signal),
  });
export const changePassword = (currentPassword: string, newPassword: string, signal?: AbortSignal) =>
  apiRequest<void>('/api/users/me/password', {
    method: 'POST',
    body: JSON.stringify({ current_password: currentPassword, new_password: newPassword }),
    ...requestInit(signal),
  });
export const getAuthSessions = (signal?: AbortSignal) =>
  apiRequest<AuthSession[]>('/api/users/me/sessions', signal ? { signal } : {});
export const revokeAuthSession = (id: number, signal?: AbortSignal) =>
  apiRequest<void>(`/api/users/me/sessions/${id}`, { method: 'DELETE', ...requestInit(signal) });
export const revokeAllAuthSessions = (signal?: AbortSignal) =>
  apiRequest<void>('/api/users/me/sessions/revoke-all', { method: 'POST', ...requestInit(signal) });
export const uploadAvatar = (file: File, signal?: AbortSignal) => {
  const form = new FormData();
  form.append('file', file);
  return apiRequest<{
    avatar_asset_id: number;
    avatar_url: string;
    mime_type: string;
    size_bytes: number;
  }>('/api/users/me/avatar', { method: 'POST', body: form, ...requestInit(signal) });
};
// 头像读取接口返回 302，作为图片地址使用，不经过 JSON 响应解析。
export const getAvatarUrl = () => {
  const baseUrl = import.meta.env.DEV ? '' : ((import.meta.env.VITE_API_BASE_URL as string | undefined) ?? '');
  return `${baseUrl}/api/users/me/avatar`;
};
export const deleteAvatar = (signal?: AbortSignal) =>
  apiRequest<void>('/api/users/me/avatar', { method: 'DELETE', ...requestInit(signal) });
export const requestDataExport = (signal?: AbortSignal) =>
  apiRequest<{ export_job_id: number }>('/api/users/me/export', { method: 'POST', ...requestInit(signal) });
export async function getDataExport(id: number, signal?: AbortSignal): Promise<ExportJob> {
  const response = await apiRequest<ExportJobResponse>(`/api/users/me/export/${id}`, requestInit(signal));
  return {
    export_job_id: response.export_job_id ?? response.exportJobId ?? id,
    status: response.status ?? 'unknown',
    expires_at: response.expires_at ?? response.expiresAt ?? undefined,
    completed_at: response.completed_at ?? response.completedAt ?? undefined,
    download_consumed_at: response.download_consumed_at ?? response.downloadConsumedAt ?? undefined,
    failure_code: response.failure_code ?? response.failureCode ?? undefined,
  };
}
export const downloadDataExport = (id: number, signal?: AbortSignal) =>
  apiRequest<{ download_url: string }>(`/api/users/me/export/${id}/download`, {
    method: 'POST',
    ...requestInit(signal),
  });
export const requestAccountDeletion = (confirmation: string, currentPassword: string, signal?: AbortSignal) =>
  apiRequest<{ deletion_job_id: number }>('/api/users/me/deletion', {
    method: 'POST',
    body: JSON.stringify({ confirmation, current_password: currentPassword }),
    ...requestInit(signal),
  });
