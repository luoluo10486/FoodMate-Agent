import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '../ui/table';

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
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>日期</TableHead>
          <TableHead>午餐</TableHead>
          <TableHead>晚餐</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {rows.map((row) => (
          <TableRow key={row.day}>
            <TableCell>{row.day}</TableCell>
            <TableCell>{row.lunch}</TableCell>
            <TableCell>{row.dinner}</TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}
