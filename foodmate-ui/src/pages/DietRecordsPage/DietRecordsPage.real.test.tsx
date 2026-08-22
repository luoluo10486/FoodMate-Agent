import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { DietRecordsPage } from './DietRecordsPage';
import { createFoodLog, deleteFoodLog, loadFoodLogs } from '../../services/foodLogService';

vi.mock('../../services/foodLogService', () => ({
  createFoodLog: vi.fn(),
  deleteFoodLog: vi.fn(),
  loadFoodLogs: vi.fn(),
}));

const log = {
  food_log_id: '11',
  meal_time: '2026-08-22T08:30:00Z',
  meal_type: 'breakfast',
  notes: null,
  source: 'manual',
  revision: 2,
  deleted: false,
  items: [
    {
      food_log_item_id: '101',
      item_order: 0,
      raw_name: '服务端燕麦',
      amount: 100,
      unit: 'g',
      nutrition_status: 'matched',
      calories_kcal: 380,
      protein_g: 13,
      fat_g: 7,
      carbs_g: 68,
    },
  ],
};

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/analysis?view=records']}>
      <DietRecordsPage />
    </MemoryRouter>,
  );
}

describe('DietRecordsPage real mode', () => {
  beforeEach(() => {
    vi.stubEnv('VITE_AGENT_MODE', 'real');
    localStorage.setItem(
      'foodmate_auth_user',
      JSON.stringify({ id: '7', username: 'tester', displayName: 'Tester', role: 'user', status: 'active' }),
    );
  });

  afterEach(() => {
    vi.unstubAllEnvs();
    localStorage.clear();
    vi.clearAllMocks();
  });

  it('renders server records and does not fall back to fixture foods', async () => {
    vi.mocked(loadFoodLogs).mockResolvedValue([log]);
    renderPage();

    await waitFor(() => expect(screen.getByText('服务端燕麦')).toBeInTheDocument());
    expect(screen.queryByText('蓝莓燕麦粥')).not.toBeInTheDocument();
    expect(screen.getByText('C: 68g')).toBeInTheDocument();
    expect(screen.getByText('能量合计')).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: '周视图' })).toBeDisabled();
  });

  it('creates a real food log from the add-food dialog', async () => {
    vi.mocked(loadFoodLogs).mockResolvedValue([]);
    vi.mocked(createFoodLog).mockResolvedValue({
      ...log,
      food_log_id: '12',
      items: [{ ...log.items[0], raw_name: '新食物' }],
    });
    const user = userEvent.setup();
    renderPage();

    await waitFor(() => expect(screen.getByRole('button', { name: '记录一餐' })).toBeInTheDocument());
    await user.click(screen.getByRole('button', { name: '记录一餐' }));
    await user.type(screen.getByPlaceholderText('例如：煮鸡蛋 2 个'), '新食物');
    await user.click(screen.getByRole('button', { name: /^添加$/ }));

    await waitFor(() =>
      expect(createFoodLog).toHaveBeenCalledWith(expect.objectContaining({ meal_type: 'breakfast' })),
    );
    expect(screen.getByText('新食物')).toBeInTheDocument();
  });

  it('deletes a real log with the server revision', async () => {
    vi.mocked(loadFoodLogs).mockResolvedValue([log]);
    vi.mocked(deleteFoodLog).mockResolvedValue();
    const user = userEvent.setup();
    renderPage();

    await waitFor(() => expect(screen.getByRole('button', { name: '删除服务端燕麦所在记录' })).toBeInTheDocument());
    await user.click(screen.getByRole('button', { name: '删除服务端燕麦所在记录' }));

    await waitFor(() => expect(deleteFoodLog).toHaveBeenCalledWith('11', 2));
    expect(screen.queryByText('服务端燕麦')).not.toBeInTheDocument();
  });

  it('shows an explicit empty state for no server records', async () => {
    vi.mocked(loadFoodLogs).mockResolvedValue([]);
    renderPage();

    await waitFor(() => expect(screen.getByText('今天还没有饮食记录')).toBeInTheDocument());
    expect(screen.queryByText('蓝莓燕麦粥')).not.toBeInTheDocument();
  });
});
