import type { LucideIcon } from 'lucide-react';
import type { ComponentProps } from 'react';
import { cn } from '@/lib/utils';

type IconProps = ComponentProps<'svg'> & {
  icon: LucideIcon;
  size?: number;
};

export function Icon({ icon: IconComponent, size = 16, className, ...props }: IconProps) {
  return <IconComponent aria-hidden="true" className={cn('shrink-0', className)} size={size} {...props} />;
}
