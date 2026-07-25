import { csrfToken } from './authService';

const baseUrl = (import.meta.env.VITE_API_BASE_URL as string | undefined) ?? '';
type ApiResponse<T> = { success: boolean; data: T; error?: { message: string } };

async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);
  if (init.body && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json');
  const token = csrfToken();
  if (token && init.method && init.method !== 'GET') headers.set('X-CSRF-Token', token);
  const response = await fetch(`${baseUrl}${path}`, { ...init, credentials: 'include', headers });
  const body = (await response.json()) as ApiResponse<T>;
  if (!response.ok || !body.success) throw new Error(body.error?.message ?? '请求失败');
  return body.data;
}

export type Profile = { user_id: number; display_name?: string; gender?: string; height_cm?: number; weight_kg?: number; activity_level?: string; diet_goal?: string; calorie_target?: number; protein_target?: number; allergens?: string; dislikes?: string; preferred_units?: string };
export type AuthSession = { auth_session_id: number; device_id?: string; user_agent?: string; ip_address?: string; expires_at: string; last_seen_at?: string; created_at?: string; revoked_at?: string };

export const getProfile = () => api<Profile>('/api/users/me/profile');
export const updateProfile = (profile: Record<string, unknown>) => api<Profile>('/api/users/me/profile', { method: 'PUT', body: JSON.stringify(profile) });
export const changePassword = (currentPassword: string, newPassword: string) => api<void>('/api/users/me/password', { method: 'POST', body: JSON.stringify({ current_password: currentPassword, new_password: newPassword }) });
export const getAuthSessions = () => api<AuthSession[]>('/api/users/me/sessions');
export const revokeAuthSession = (id: number) => api<void>(`/api/users/me/sessions/${id}`, { method: 'DELETE' });
export const revokeAllAuthSessions = () => api<void>('/api/users/me/sessions/revoke-all', { method: 'POST' });
export const uploadAvatar = (file: File) => { const form = new FormData(); form.append('file', file); return api<{ storage_key: string }>('/api/users/me/avatar', { method: 'POST', body: form }); };
export const deleteAvatar = () => api<void>('/api/users/me/avatar', { method: 'DELETE' });
export const requestDataExport = () => api<{ export_job_id: number }>('/api/users/me/export', { method: 'POST' });
export const getDataExport = (id: number) => api<{ export_job_id: number; status: string; expires_at?: string; completed_at?: string; download_consumed_at?: string; failure_code?: string }>(`/api/users/me/export/${id}`);
export const downloadDataExport = (id: number) => api<{ download_url: string }>(`/api/users/me/export/${id}/download`, { method: 'POST' });
export const requestAccountDeletion = (confirmation: string, currentPassword: string) => api<{ deletion_job_id: number }>('/api/users/me/deletion', { method: 'POST', body: JSON.stringify({ confirmation, current_password: currentPassword }) });
