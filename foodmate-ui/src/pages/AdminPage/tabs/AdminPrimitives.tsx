/* Compatibility facade for the existing admin tabs. */
/* eslint-disable react-refresh/only-export-components */
import * as React from 'react';
import type { ReactNode } from 'react';
import { useState } from 'react';
import { Badge as ShadcnBadge } from '@/components/ui/badge';
import { Button as ShadcnButton } from '@/components/ui/button';
import { Card as ShadcnCard } from '@/components/ui/card';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Input as ShadcnInput } from '@/components/ui/input';
import { Table as ShadcnTable, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { TabsContent, TabsList, TabsTrigger, Tabs as ShadcnTabs } from '@/components/ui/tabs';
import { Textarea } from '@/components/ui/textarea';
import {
  Archive,
  BookOpen,
  CircleGauge,
  CirclePlay,
  Database,
  FileText,
  GitBranch,
  House,
  ArrowLeft,
  LayoutDashboard,
  PackageCheck,
  ShieldCheck,
  User,
  UsersRound,
  Wrench,
} from 'lucide-react';
import { cn } from '@/lib/utils';

type AdminButtonProps = Omit<React.ComponentProps<typeof ShadcnButton>, 'size' | 'type'> & {
  icon?: ReactNode;
  status?: 'danger' | 'default';
  color?: string;
  type?: 'button' | 'submit' | 'reset' | 'primary';
  size?: 'mini' | 'small' | 'default' | 'large' | 'sm' | 'lg' | 'icon' | null;
};

export function Button({
  icon,
  status,
  color,
  type = 'button',
  variant,
  size,
  className,
  children,
  ...props
}: AdminButtonProps) {
  const resolvedVariant =
    variant ?? (status === 'danger' || color === 'red' ? 'destructive' : type === 'primary' ? 'default' : 'outline');
  const resolvedSize = size === 'mini' || size === 'small' ? 'sm' : size === 'large' ? 'lg' : size;
  const htmlType = type === 'primary' ? 'button' : type;
  return (
    <ShadcnButton className={className} variant={resolvedVariant} size={resolvedSize} type={htmlType} {...props}>
      {icon}
      {children}
    </ShadcnButton>
  );
}

export function Card({
  bordered: _bordered,
  className,
  ...props
}: React.ComponentProps<typeof ShadcnCard> & { bordered?: boolean }) {
  return <ShadcnCard className={className} {...props} />;
}

const badgeVariant = (color?: string) => {
  if (color === 'red') return 'destructive' as const;
  if (color === 'orange') return 'warning' as const;
  if (color === 'gray') return 'secondary' as const;
  if (color === 'blue') return 'outline' as const;
  return 'default' as const;
};

export function Tag({ color, children, className }: { color?: string; children: ReactNode; className?: string }) {
  return (
    <ShadcnBadge className={className} variant={badgeVariant(color)}>
      {children}
    </ShadcnBadge>
  );
}

type AdminInputProps = React.ComponentProps<typeof ShadcnInput> & {
  allowClear?: boolean;
  size?: 'small' | 'default';
};

function AdminInput({ allowClear: _allowClear, size: _size, ...props }: AdminInputProps) {
  return <ShadcnInput {...props} />;
}

export const Input = Object.assign(AdminInput, { TextArea: Textarea });

type AdminSelectProps = React.SelectHTMLAttributes<HTMLSelectElement> & {
  triggerProps?: unknown;
  size?: 'small' | 'default';
};

function AdminSelect({ className, size: _size, triggerProps: _triggerProps, ...props }: AdminSelectProps) {
  return (
    <select className={cn('h-10 rounded-md border border-input bg-background px-3 text-sm', className)} {...props} />
  );
}

function AdminOption({ value, children }: { value: string; children: ReactNode }) {
  return <option value={value}>{children}</option>;
}

export const Select = Object.assign(AdminSelect, { Option: AdminOption });

export type TableColumnProps<T> = {
  title: ReactNode;
  dataIndex?: keyof T;
  render?: (value: never, record: T, index: number) => ReactNode;
};

type AdminTableProps<T extends { key?: string }> = {
  columns: TableColumnProps<T>[];
  data: T[];
  pagination?: false | { pageSize?: number; total?: number };
  size?: 'mini' | 'small';
  className?: string;
  tableClassName?: string;
};

export function Table<T extends { key?: string }>({
  columns,
  data,
  pagination: _pagination,
  size: _size,
  className,
  tableClassName,
}: AdminTableProps<T>) {
  return (
    <div className={cn('w-full overflow-x-auto', className)}>
      <ShadcnTable className={tableClassName}>
        <TableHeader>
          <TableRow>
            {columns.map((column, index) => (
              <TableHead key={`${String(column.dataIndex ?? column.title)}-${index}`}>{column.title}</TableHead>
            ))}
          </TableRow>
        </TableHeader>
        <TableBody>
          {data.length ? (
            data.map((record, rowIndex) => (
              <TableRow key={record.key ?? rowIndex}>
                {columns.map((column, columnIndex) => {
                  const value = column.dataIndex ? record[column.dataIndex] : undefined;
                  return (
                    <TableCell key={`${String(column.dataIndex ?? column.title)}-${columnIndex}`}>
                      {column.render ? column.render(value as never, record, rowIndex) : String(value ?? '-')}
                    </TableCell>
                  );
                })}
              </TableRow>
            ))
          ) : (
            <TableRow>
              <TableCell colSpan={columns.length} className="h-24 text-center text-muted-foreground">
                暂无数据
              </TableCell>
            </TableRow>
          )}
        </TableBody>
      </ShadcnTable>
    </div>
  );
}

type AdminTabPaneProps = { title: string; children: ReactNode };

function TabPane(_props: AdminTabPaneProps) {
  return null;
}

type AdminTabsProps = { defaultActiveTab: string; children: ReactNode };

function AdminTabs({ defaultActiveTab, children }: AdminTabsProps) {
  const panes = (Array.isArray(children) ? children : [children]).filter(
    Boolean,
  ) as React.ReactElement<AdminTabPaneProps>[];
  const [active, setActive] = useState(defaultActiveTab);
  return (
    <ShadcnTabs value={active} onValueChange={setActive}>
      <TabsList>
        {panes.map((pane) => (
          <TabsTrigger key={String(pane.key)} value={String(pane.key)}>
            {pane.props.title}
          </TabsTrigger>
        ))}
      </TabsList>
      {panes.map((pane) => (
        <TabsContent key={String(pane.key)} value={String(pane.key)}>
          {pane.props.children}
        </TabsContent>
      ))}
    </ShadcnTabs>
  );
}

export const Tabs = Object.assign(AdminTabs, { TabPane });

type AdminModalProps = {
  title: string;
  visible: boolean;
  okText?: string;
  cancelText?: string;
  onCancel: () => void;
  onOk: () => void | Promise<void>;
  children: ReactNode;
};

export function Modal({
  title,
  visible,
  okText = '确定',
  cancelText = '取消',
  onCancel,
  onOk,
  children,
}: AdminModalProps) {
  return (
    <Dialog
      open={visible}
      onOpenChange={(open) => {
        if (!open) onCancel();
      }}
    >
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
          <DialogDescription>请检查内容后确认提交。</DialogDescription>
        </DialogHeader>
        {children}
        <DialogFooter>
          <Button variant="outline" onClick={onCancel}>
            {cancelText}
          </Button>
          <Button onClick={() => void onOk()}>{okText}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

export const Message = {
  info(message: string) {
    window.dispatchEvent(new CustomEvent('foodmate:admin-notice', { detail: { message, tone: 'info' } }));
  },
  warning(message: string) {
    window.dispatchEvent(new CustomEvent('foodmate:admin-notice', { detail: { message, tone: 'warning' } }));
  },
  success(message: string) {
    window.dispatchEvent(new CustomEvent('foodmate:admin-notice', { detail: { message, tone: 'success' } }));
  },
};

export const IconApps = LayoutDashboard;
export const IconHome = House;
export const IconLeft = ArrowLeft;
export const IconSafe = ShieldCheck;
export const IconUser = User;
export const IconFile = FileText;
export const IconThunderbolt = CirclePlay;
export const IconTool = Wrench;
export const IconBook = BookOpen;
export const IconDashboard = CircleGauge;
export const IconHistory = Archive;
export const IconStorage = Database;
export const IconUserGroup = UsersRound;
export const IconSql = Database;
export const IconTrace = GitBranch;
export const IconToolRegistry = PackageCheck;
export const IconAudit = ShieldCheck;
