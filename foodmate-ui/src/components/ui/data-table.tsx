import * as React from 'react';
import { cn } from '@/lib/utils';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from './table';

export type TableColumnProps<T> = {
  title: React.ReactNode;
  dataIndex?: keyof T;
  render?: (value: unknown, record: T, index: number) => React.ReactNode;
};

type DataTableProps<T extends { key?: string }> = {
  columns: TableColumnProps<T>[];
  data: T[];
  className?: string;
  tableClassName?: string;
  emptyLabel?: string;
};

export function DataTable<T extends { key?: string }>({
  columns,
  data,
  className,
  tableClassName,
  emptyLabel = '暂无数据',
}: DataTableProps<T>) {
  return (
    <div className={cn('w-full overflow-x-auto', className)}>
      <Table className={tableClassName}>
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
                      {column.render ? column.render(value, record, rowIndex) : String(value ?? '-')}
                    </TableCell>
                  );
                })}
              </TableRow>
            ))
          ) : (
            <TableRow>
              <TableCell colSpan={columns.length} className="h-24 text-center text-muted-foreground">
                {emptyLabel}
              </TableCell>
            </TableRow>
          )}
        </TableBody>
      </Table>
    </div>
  );
}
