import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { Alert, AlertDescription, AlertTitle } from './alert';
import { Badge } from './badge';
import { Button } from './button';
import { Dialog, DialogContent, DialogTitle, DialogTrigger } from './dialog';
import { Input } from './input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from './select';
import { Sheet, SheetContent, SheetDescription, SheetTitle, SheetTrigger } from './sheet';
import { Table, TableBody, TableCell, TableCaption, TableHead, TableHeader, TableRow } from './table';
import { Tabs, TabsContent, TabsList, TabsTrigger } from './tabs';
import { Textarea } from './textarea';
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from './tooltip';

describe('FoodMate UI primitives', () => {
  it('supports Button states and click handlers', () => {
    const onClick = vi.fn();
    render(
      <div>
        <Button onClick={onClick}>保存</Button>
        <Button disabled>禁用</Button>
      </div>,
    );

    fireEvent.click(screen.getByRole('button', { name: '保存' }));
    expect(onClick).toHaveBeenCalledOnce();
    expect(screen.getByRole('button', { name: '禁用' })).toBeDisabled();
  });

  it('maps Button variant and size contracts to stable classes', () => {
    render(
      <div>
        <Button variant="destructive" size="lg">
          删除
        </Button>
        <Button variant="ghost" size="icon" aria-label="更多操作">
          ...
        </Button>
      </div>,
    );

    expect(screen.getByRole('button', { name: '删除' })).toHaveClass(
      'bg-destructive',
      'text-destructive-foreground',
      'h-11',
    );
    expect(screen.getByRole('button', { name: '更多操作' })).toHaveClass('hover:bg-accent', 'size-10');
  });

  it('exposes accessible input and textarea controls', () => {
    render(
      <div>
        <label htmlFor="email">邮箱</label>
        <Input id="email" aria-invalid="true" />
        <label htmlFor="note">备注</label>
        <Textarea id="note" />
      </div>,
    );

    expect(screen.getByRole('textbox', { name: '邮箱' })).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getByRole('textbox', { name: '备注' })).toBeEnabled();
  });

  it('opens and closes Dialog with keyboard escape', async () => {
    const user = userEvent.setup();
    render(
      <Dialog>
        <DialogTrigger asChild>
          <Button>打开确认</Button>
        </DialogTrigger>
        <DialogContent>
          <DialogTitle>确认操作</DialogTitle>
          <p>操作说明</p>
        </DialogContent>
      </Dialog>,
    );

    const trigger = screen.getByRole('button', { name: '打开确认' });
    await user.click(trigger);
    const dialog = await screen.findByRole('dialog');
    expect(dialog).toBeVisible();
    await user.keyboard('{Escape}');
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(trigger).toHaveFocus();
  });

  it('opens Sheet content on the requested side and restores trigger focus', async () => {
    const user = userEvent.setup();
    render(
      <Sheet>
        <SheetTrigger asChild>
          <Button>打开侧栏</Button>
        </SheetTrigger>
        <SheetContent side="left">
          <SheetTitle>会话详情</SheetTitle>
          <SheetDescription>查看当前会话的更多信息。</SheetDescription>
        </SheetContent>
      </Sheet>,
    );

    const trigger = screen.getByRole('button', { name: '打开侧栏' });
    await user.click(trigger);
    const sheet = await screen.findByRole('dialog');
    expect(sheet).toHaveClass('left-0', 'border-r');
    expect(sheet).toHaveAccessibleName('会话详情');

    await user.keyboard('{Escape}');
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(trigger).toHaveFocus();
  });

  it('shows Tooltip content through TooltipProvider and hides it after leaving', async () => {
    const user = userEvent.setup();
    render(
      <TooltipProvider delayDuration={0} skipDelayDuration={0} disableHoverableContent>
        <Tooltip>
          <TooltipTrigger asChild>
            <Button variant="ghost" size="icon" aria-label="打开帮助">
              ?
            </Button>
          </TooltipTrigger>
          <TooltipContent>查看帮助信息</TooltipContent>
        </Tooltip>
      </TooltipProvider>,
    );

    const trigger = screen.getByRole('button', { name: '打开帮助' });
    await user.hover(trigger);
    expect(await screen.findByRole('tooltip')).toHaveTextContent('查看帮助信息');

    fireEvent.pointerLeave(trigger);
    await waitFor(() => expect(screen.queryByRole('tooltip')).not.toBeInTheDocument());
  });

  it('maps Badge variants to the semantic visual states', () => {
    render(
      <div>
        <Badge>默认</Badge>
        <Badge variant="secondary">次要</Badge>
        <Badge variant="outline">轮廓</Badge>
        <Badge variant="warning">警告</Badge>
        <Badge variant="destructive">危险</Badge>
      </div>,
    );

    expect(screen.getByText('默认')).toHaveClass('bg-primary', 'text-primary-foreground');
    expect(screen.getByText('次要')).toHaveClass('bg-secondary', 'text-secondary-foreground');
    expect(screen.getByText('轮廓')).toHaveClass('border', 'text-foreground');
    expect(screen.getByText('警告')).toHaveClass('bg-accent', 'text-accent-foreground');
    expect(screen.getByText('危险')).toHaveClass('bg-destructive', 'text-destructive-foreground');
  });

  it('exposes Alert semantics and destructive state styling', () => {
    render(
      <Alert variant="destructive">
        <AlertTitle>操作失败</AlertTitle>
        <AlertDescription>请稍后重试。</AlertDescription>
      </Alert>,
    );

    const alert = screen.getByRole('alert');
    expect(alert).toHaveTextContent('操作失败请稍后重试。');
    expect(alert).toHaveClass('border-destructive/40', 'bg-destructive/10', 'text-destructive');
    expect(screen.getByRole('heading', { name: '操作失败' })).toHaveClass('font-semibold');
  });

  it('preserves native Table semantics and the narrow-screen scroll wrapper', () => {
    render(
      <Table aria-label="营养明细">
        <TableCaption>每餐营养摄入</TableCaption>
        <TableHeader>
          <TableRow>
            <TableHead scope="col">餐次</TableHead>
            <TableHead scope="col">热量</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          <TableRow>
            <TableCell>午餐</TableCell>
            <TableCell>520 kcal</TableCell>
          </TableRow>
        </TableBody>
      </Table>,
    );

    const table = screen.getByRole('table', { name: '营养明细' });
    expect(table).toHaveClass('w-full');
    expect(table.parentElement).toHaveClass('overflow-auto');
    expect(screen.getAllByRole('row')).toHaveLength(2);
    expect(screen.getAllByRole('columnheader')).toHaveLength(2);
    expect(screen.getAllByRole('cell')).toHaveLength(2);
    expect(screen.getByText('每餐营养摄入')).toBeInTheDocument();
  });

  it('changes Tabs with a keyboard-friendly trigger', async () => {
    const user = userEvent.setup();
    render(
      <Tabs defaultValue="overview">
        <TabsList aria-label="视图">
          <TabsTrigger value="overview">概览</TabsTrigger>
          <TabsTrigger value="details">详情</TabsTrigger>
        </TabsList>
        <TabsContent value="overview">概览内容</TabsContent>
        <TabsContent value="details">详情内容</TabsContent>
      </Tabs>,
    );

    expect(screen.getByRole('tabpanel')).toHaveTextContent('概览内容');
    const overviewTab = screen.getByRole('tab', { name: '概览' });
    const detailsTab = screen.getByRole('tab', { name: '详情' });
    overviewTab.focus();
    await user.keyboard('{ArrowRight}');
    await waitFor(() => expect(detailsTab).toHaveAttribute('aria-selected', 'true'));
    expect(screen.getByText('详情内容')).toBeVisible();
  });

  it('opens Select and commits an option', async () => {
    render(
      <Select defaultValue="green">
        <SelectTrigger aria-label="颜色">
          <SelectValue placeholder="选择颜色" />
        </SelectTrigger>
        <SelectContent>
          <SelectItem value="green">绿色</SelectItem>
          <SelectItem value="orange">橙色</SelectItem>
        </SelectContent>
      </Select>,
    );

    const trigger = screen.getByRole('combobox', { name: '颜色' });
    fireEvent.keyDown(trigger, { key: 'ArrowDown' });
    await waitFor(() => expect(screen.getByRole('option', { name: '橙色' })).toBeVisible());
    fireEvent.click(screen.getByRole('option', { name: '橙色' }));
    await waitFor(() => expect(trigger).toHaveTextContent('橙色'));
  });
});
