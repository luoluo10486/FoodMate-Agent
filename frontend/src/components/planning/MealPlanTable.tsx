import { Table } from '@arco-design/web-react';

export type MealPlanRow = {
  day: string;
  lunch: string;
  dinner: string;
};

type MealPlanTableProps = {
  rows: MealPlanRow[];
};

export function MealPlanTable({ rows }: MealPlanTableProps) {
  return (
    <Table
      pagination={false}
      data={rows}
      rowKey="day"
      columns={[
        { title: '日期', dataIndex: 'day' },
        { title: '午餐', dataIndex: 'lunch' },
        { title: '晚餐', dataIndex: 'dinner' },
      ]}
    />
  );
}
