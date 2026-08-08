import * as React from 'react';
import { useContext } from 'react';
import { Badge as ShadcnBadge } from './badge';
import { Button as ShadcnButton } from './button';
import { Card as ShadcnCard } from './card';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from './dialog';
import { Input as ShadcnInput } from './input';
import { Progress as ShadcnProgress } from './progress';
import { Skeleton as ShadcnSkeleton } from './skeleton';
import { Textarea } from './textarea';
import { cn } from '@/lib/utils';

type ButtonProps = Omit<React.ComponentProps<typeof ShadcnButton>, 'type' | 'size'> & {
  type?: 'primary' | 'default' | 'text';
  status?: 'danger' | 'default';
  htmlType?: 'button' | 'submit' | 'reset';
  long?: boolean;
  size?: 'mini' | 'small' | 'default' | 'large';
  icon?: React.ReactNode;
  loading?: boolean;
  color?: string;
};

export function Button({
  type = 'default',
  status,
  htmlType = 'button',
  long,
  size = 'default',
  icon,
  loading,
  color: _color,
  disabled,
  className,
  children,
  variant,
  ...props
}: ButtonProps) {
  const resolvedVariant = variant ?? (status === 'danger' ? 'destructive' : type === 'primary' ? 'default' : type === 'text' ? 'ghost' : 'outline');
  const resolvedSize = size === 'mini' || size === 'small' ? 'sm' : size === 'large' ? 'lg' : 'default';
  return (
    <ShadcnButton
      className={cn(long && 'w-full', className)}
      disabled={loading || disabled}
      variant={resolvedVariant}
      size={resolvedSize}
      type={htmlType}
      {...props}
    >
      {icon}
      {loading ? '处理中...' : children}
    </ShadcnButton>
  );
}

export function Card({ bordered: _bordered, ...props }: React.ComponentProps<typeof ShadcnCard> & { bordered?: boolean }) {
  return <ShadcnCard {...props} />;
}

function resolveBadgeVariant(color?: string) {
  if (color === 'red') return 'destructive' as const;
  if (color === 'orange') return 'warning' as const;
  if (color === 'gray') return 'secondary' as const;
  if (color === 'blue') return 'outline' as const;
  return 'default' as const;
}

export function Tag({ color, children, className }: { color?: string; children: React.ReactNode; className?: string }) {
  return <ShadcnBadge className={className} variant={resolveBadgeVariant(color)}>{children}</ShadcnBadge>;
}

type InputProps = Omit<React.ComponentProps<typeof ShadcnInput>, 'onChange' | 'prefix'> & {
  onChange?: (value: string) => void;
  allowClear?: boolean;
  prefix?: React.ReactNode;
};

function InputBase({ onChange, allowClear: _allowClear, prefix, className, ...props }: InputProps) {
  const input = <ShadcnInput className={cn(prefix && 'pl-9', className)} onChange={(event) => onChange?.(event.target.value)} {...props} />;
  return prefix ? <span className="relative block"><span className="absolute left-3 top-1/2 z-10 -translate-y-1/2">{prefix}</span>{input}</span> : input;
}

function PasswordInput(props: InputProps) {
  return <InputBase type="password" {...props} />;
}

function SearchInput({ searchButton, ...props }: InputProps & { searchButton?: React.ReactNode }) {
  return (
    <div className="flex items-center gap-2">
      <InputBase {...props} />
      <Button type="primary">{searchButton ?? '搜索'}</Button>
    </div>
  );
}

export const Input = Object.assign(InputBase, {
  Password: PasswordInput,
  Search: SearchInput,
  TextArea: Textarea,
});

type InputNumberProps = Omit<InputProps, 'type'> & { min?: number; max?: number; precision?: number };

export function InputNumber({ min, max, precision: _precision, ...props }: InputNumberProps) {
  return <InputBase type="number" min={min} max={max} {...props} />;
}

type SelectProps = React.SelectHTMLAttributes<HTMLSelectElement> & { onChange?: (value: string) => void };

function SelectBase({ onChange, className, children, ...props }: SelectProps) {
  return <select className={cn('h-10 rounded-md border border-input bg-background px-3 text-sm', className)} onChange={(event) => onChange?.(event.target.value)} {...props}>{children}</select>;
}

function SelectOption({ value, children }: { value: string; children: React.ReactNode }) {
  return <option value={value}>{children}</option>;
}

export const Select = Object.assign(SelectBase, { Option: SelectOption });

type FormContextValue = { initialValues?: Record<string, unknown> };
const FormContext = React.createContext<FormContextValue>({});

type FormRule = {
  required?: boolean;
  minLength?: number;
  message?: string;
  validator?: (value: string, callback: (error?: string) => void) => void;
};

type FormProps<T extends Record<string, string> = Record<string, string>> = {
  className?: string;
  layout?: 'vertical' | 'horizontal';
  initialValues?: Record<string, unknown>;
  onSubmit?: (values: T) => void | Promise<void>;
  children: React.ReactNode;
};

function FormBase<T extends Record<string, string>>({ className, initialValues, onSubmit, children }: FormProps<T>) {
  return (
    <FormContext.Provider value={{ initialValues }}>
      <form
        className={className}
        onSubmit={(event) => {
          event.preventDefault();
          const formData = new FormData(event.currentTarget);
          const values = Object.fromEntries(
            Array.from(formData.entries(), ([key, value]) => [key, typeof value === 'string' ? value : value.name]),
          ) as T;
          void onSubmit?.(values);
        }}
      >
        {children}
      </form>
    </FormContext.Provider>
  );
}

function FormItem({ label, field, rules: _rules, children }: { label?: React.ReactNode; field: string; rules?: FormRule[]; children: React.ReactElement }) {
  const { initialValues } = useContext(FormContext);
  const child = React.cloneElement(children, {
    name: field,
    defaultValue: children.props.defaultValue ?? initialValues?.[field],
  });
  return <label className="grid gap-2 text-sm text-muted-foreground">{label}<span>{child}</span></label>;
}

export const Form = Object.assign(FormBase, { Item: FormItem });

type ModalProps = {
  title: string;
  visible: boolean;
  okText?: string;
  cancelText?: string;
  onCancel: () => void;
  onOk: () => void | Promise<void>;
  children: React.ReactNode;
};

export function Modal({ title, visible, okText = '确定', cancelText = '取消', onCancel, onOk, children }: ModalProps) {
  return (
    <Dialog open={visible} onOpenChange={(open) => { if (!open) onCancel(); }}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
          <DialogDescription>请检查内容后确认提交。</DialogDescription>
        </DialogHeader>
        {children}
        <DialogFooter>
          <Button onClick={onCancel}>{cancelText}</Button>
          <Button type="primary" onClick={() => void onOk()}>{okText}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

export function Progress({
  percent = 0,
  size,
  showText: _showText,
  className,
}: {
  percent?: number;
  size?: 'small' | 'default' | string;
  showText?: boolean;
  className?: string;
}) {
  return <ShadcnProgress className={cn(size === 'small' && 'h-1.5', className)} value={percent} />;
}

export function Skeleton({ loading: _loading, text: _text, animation: _animation, ...props }: React.ComponentProps<typeof ShadcnSkeleton> & { loading?: boolean; text?: unknown; animation?: boolean }) {
  return <ShadcnSkeleton {...props} />;
}

export const Message = {
  info(message: string) { window.dispatchEvent(new CustomEvent('foodmate:notice', { detail: { message, tone: 'info' } })); },
  warning(message: string) { window.dispatchEvent(new CustomEvent('foodmate:notice', { detail: { message, tone: 'warning' } })); },
  success(message: string) { window.dispatchEvent(new CustomEvent('foodmate:notice', { detail: { message, tone: 'success' } })); },
  error(message: string) { window.dispatchEvent(new CustomEvent('foodmate:notice', { detail: { message, tone: 'error' } })); },
};
