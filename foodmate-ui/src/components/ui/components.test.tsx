import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { Button } from './button';
import { Dialog, DialogContent, DialogTitle, DialogTrigger } from './dialog';
import { Input } from './input';
import { Tabs, TabsContent, TabsList, TabsTrigger } from './tabs';
import { Textarea } from './textarea';

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

    fireEvent.click(screen.getByRole('button', { name: '打开确认' }));
    const dialog = await screen.findByRole('dialog');
    expect(dialog).toBeVisible();
    fireEvent.keyDown(dialog, { key: 'Escape' });
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
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
});
