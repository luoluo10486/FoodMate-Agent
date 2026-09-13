import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { HomePage } from './HomePage';

const { loadSessionSummariesPage, loadNutritionAnalysis } = vi.hoisted(() => ({
  loadSessionSummariesPage: vi.fn(),
  loadNutritionAnalysis: vi.fn(),
}));

vi.mock('../../services/sessionService', async () => {
  const actual = await vi.importActual<typeof import('../../services/sessionService')>('../../services/sessionService');
  return { ...actual, loadSessionSummariesPage };
});

vi.mock('../../services/analysisService', async () => {
  const actual = await vi.importActual<typeof import('../../services/analysisService')>(
    '../../services/analysisService',
  );
  return { ...actual, loadNutritionAnalysis };
});

vi.mock('../../services/authService', async () => {
  const actual = await vi.importActual<typeof import('../../services/authService')>('../../services/authService');
  const user = {
    id: '7',
    username: 'user@foodmate.local',
    displayName: '真实用户',
    email: 'user@foodmate.local',
    role: 'user',
    status: 'active',
    gender: '男',
  };
  return {
    ...actual,
    getAuthStatus: () => 'authenticated',
    getAuthUser: () => user,
    loadCurrentUser: async () => user,
  };
});

const analysis = {
  range: 'today' as const,
  from: '2026-09-14',
  to: '2026-09-14',
  total_items: 2,
  matched_items: 2,
  coverage: 1,
  calories_kcal: 1850,
  protein_g: 120,
  fat_g: 58,
  carbs_g: 210,
  calorie_target: 2000,
  protein_target: 150,
  incomplete: false,
  unmatched_names: [],
  disclaimer: '',
};

function renderRealHome() {
  return render(
    <MemoryRouter initialEntries={['/']}>
      <Routes>
        <Route path="/" element={<HomePage />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('HomePage 真实模式', () => {
  beforeEach(() => {
    vi.stubEnv('VITE_AGENT_MODE', 'real');
    loadSessionSummariesPage.mockReset();
    loadNutritionAnalysis.mockReset();
    loadSessionSummariesPage.mockResolvedValue({
      items: [{ id: 'session-1', title: '真实会话', subtitle: 'agent', active: true }],
      total: 1,
      page: 1,
      size: 5,
    });
    loadNutritionAnalysis.mockResolvedValue(analysis);
  });

  afterEach(() => {
    vi.unstubAllEnvs();
  });

  it('使用真实会话和营养分析，不渲染 Fixture 待确认队列', async () => {
    renderRealHome();

    expect(await screen.findByText('真实会话')).toBeInTheDocument();
    expect(screen.getByText('1,850')).toBeInTheDocument();
    expect(screen.getByText('93%')).toBeInTheDocument();
    expect(screen.getByText('80%')).toBeInTheDocument();
    expect(screen.getByText('暂无待确认事项')).toBeInTheDocument();
    expect(screen.queryByText('牛油果酸面包吐司')).not.toBeInTheDocument();
    expect(loadNutritionAnalysis).toHaveBeenCalledWith('today');
  });

  it('营养摘要失败时保留已加载的会话并提供重试入口', async () => {
    loadNutritionAnalysis.mockRejectedValue(new Error('营养摘要服务不可用'));

    renderRealHome();

    expect(await screen.findByText('真实会话')).toBeInTheDocument();
    expect(await screen.findByText('营养摘要服务不可用')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '重新加载' })).toBeInTheDocument();
    expect(screen.queryByText('1,850')).not.toBeInTheDocument();
    expect(screen.getByText('暂无待确认事项')).toBeInTheDocument();
  });

  it('后端未返回营养目标时不推算百分比', async () => {
    loadNutritionAnalysis.mockResolvedValue({ ...analysis, calorie_target: null, protein_target: null });

    renderRealHome();

    await waitFor(() => expect(screen.getByText('1,850')).toBeInTheDocument());
    expect(screen.getAllByText('--')).toHaveLength(4);
  });
});
