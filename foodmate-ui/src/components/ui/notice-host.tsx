import { useEffect, useState } from 'react';
import { cn } from '@/lib/utils';

type NoticeTone = 'info' | 'warning' | 'success' | 'error';

type Notice = {
  message: string;
  tone: NoticeTone;
};

const toneClasses: Record<NoticeTone, string> = {
  info: 'border-border bg-card text-card-foreground',
  warning: 'border-accent bg-accent/40 text-accent-foreground',
  success: 'border-primary/30 bg-primary/10 text-foreground',
  error: 'border-destructive/40 bg-destructive/10 text-destructive',
};

export function NoticeHost() {
  const [notice, setNotice] = useState<Notice | null>(null);

  useEffect(() => {
    let timeoutId: number | undefined;
    const handleNotice = (event: Event) => {
      const detail = (event as CustomEvent<Notice>).detail;
      if (!detail?.message) return;
      setNotice(detail);
      if (timeoutId !== undefined) window.clearTimeout(timeoutId);
      timeoutId = window.setTimeout(() => setNotice(null), 3500);
    };

    window.addEventListener('foodmate:notice', handleNotice);
    return () => {
      window.removeEventListener('foodmate:notice', handleNotice);
      if (timeoutId !== undefined) window.clearTimeout(timeoutId);
    };
  }, []);

  if (!notice) return null;

  return (
    <div className="pointer-events-none fixed right-4 top-4 z-[100] w-[min(24rem,calc(100vw-2rem))]" aria-live="polite">
      <div
        className={cn(
          'rounded-[var(--radius-container)] border px-4 py-3 text-sm shadow-[var(--elevation-overlay)]',
          toneClasses[notice.tone],
        )}
        role="status"
      >
        {notice.message}
      </div>
    </div>
  );
}
