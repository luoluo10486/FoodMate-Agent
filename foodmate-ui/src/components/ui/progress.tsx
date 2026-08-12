import * as React from 'react';
import { cn } from '@/lib/utils';

type ProgressProps = React.HTMLAttributes<HTMLDivElement> & {
  value?: number;
  max?: number;
};

const Progress = React.forwardRef<HTMLDivElement, ProgressProps>(
  ({ className, value = 0, max = 100, ...props }, ref) => {
    const percent = Math.max(0, Math.min(100, (value / max) * 100));

    return (
      <div
        ref={ref}
        role="progressbar"
        aria-valuemin={0}
        aria-valuemax={max}
        aria-valuenow={value}
        className={cn('relative h-2 w-full overflow-hidden rounded-[var(--radius-pill)] bg-secondary', className)}
        {...props}
      >
        <div className="h-full bg-primary transition-[width]" style={{ width: `${percent}%` }} />
      </div>
    );
  },
);
Progress.displayName = 'Progress';

export { Progress };
