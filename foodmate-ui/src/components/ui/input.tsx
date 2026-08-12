import * as React from 'react';
import { cn } from '@/lib/utils';

type InputProps = React.ComponentProps<'input'> & {
  leadingIcon?: React.ReactNode;
  trailingAction?: React.ReactNode;
};

const Input = React.forwardRef<HTMLInputElement, InputProps>(
  ({ className, type, leadingIcon, trailingAction, ...props }, ref) => {
    const input = (
      <input
        type={type}
        className={cn(
          'flex h-10 w-full rounded-[var(--radius-control)] border border-input bg-background px-3 py-2 text-sm text-foreground shadow-sm outline-none placeholder:text-muted-foreground focus-visible:border-ring focus-visible:ring-2 focus-visible:ring-ring/25 disabled:cursor-not-allowed disabled:opacity-50',
          leadingIcon ? 'pl-11' : '',
          trailingAction ? 'pr-11' : '',
          className,
        )}
        ref={ref}
        {...props}
      />
    );
    if (!leadingIcon && !trailingAction) return input;
    return (
      <span className="relative block w-full">
        {leadingIcon ? (
          <span className="pointer-events-none absolute left-4 top-1/2 z-10 -translate-y-1/2 [&>svg]:size-[18px] [&>svg]:text-[#6f7e89]">
            {leadingIcon}
          </span>
        ) : null}
        {input}
        {trailingAction ? (
          <span className="absolute right-3 top-1/2 z-10 -translate-y-1/2">{trailingAction}</span>
        ) : null}
      </span>
    );
  },
);
Input.displayName = 'Input';

export { Input };
