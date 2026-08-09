export type NoticeTone = 'info' | 'warning' | 'success' | 'error';

export function notify(message: string, tone: NoticeTone = 'info') {
  if (typeof window === 'undefined') return;
  window.dispatchEvent(new CustomEvent('foodmate:notice', { detail: { message, tone } }));
}
