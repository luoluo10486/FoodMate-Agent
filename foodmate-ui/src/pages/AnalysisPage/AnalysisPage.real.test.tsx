import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AnalysisPage } from './AnalysisPage';
import { loadNutritionAnalysis, type NutritionAnalysis } from '../../services/analysisService';

vi.mock('../../services/analysisService', () => ({
  loadNutritionAnalysis: vi.fn(),
}));

function LocationProbe() {
  const location = useLocation();
  return (
    <output data-testid="location" role="presentation">
      {location.pathname + location.search}
    </output>
  );
}

function renderPage(initialEntry = '/analysis', withNavigationProbe = false) {
  return render(
    <MemoryRouter initialEntries={[initialEntry]}>
      <Routes>
        <Route path="/analysis" element={<AnalysisPage />} />
      </Routes>
      {withNavigationProbe ? <LocationProbe /> : null}
    </MemoryRouter>,
  );
}

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((promiseResolve, promiseReject) => {
    resolve = promiseResolve;
    reject = promiseReject;
  });
  return { promise, resolve, reject };
}

const response = {
  range: '7d' as const,
  from: '2026-08-15T00:00:00Z',
  to: '2026-08-22T00:00:00Z',
  total_items: 4,
  matched_items: 3,
  coverage: 0.75,
  calories_kcal: 4200,
  protein_g: 220,
  fat_g: 130,
  carbs_g: 310,
  calorie_target: 1800,
  protein_target: 120,
  incomplete: true,
  unmatched_names: ['自制酱料'],
  disclaimer: '仅用于饮食记录参考',
};

const todayEmptyResponse = {
  ...response,
  range: 'today' as const,
  total_items: 0,
  matched_items: 0,
  coverage: 0,
  incomplete: false,
  unmatched_names: [],
};

describe('AnalysisPage real mode', () => {
  beforeEach(() => {
    vi.stubEnv('VITE_AGENT_MODE', 'real');
    localStorage.setItem(
      'foodmate_auth_user',
      JSON.stringify({ id: '7', username: 'tester', displayName: 'Tester', role: 'user', status: 'active' }),
    );
    vi.stubGlobal(
      'fetch',
      vi.fn().mockImplementation((path: string) => {
        const data =
          path === '/api/users/me'
            ? { user_id: 7, username: 'tester', email: 'tester@example.com', role: 'user' }
            : { items: [] };
        return Promise.resolve(new Response(JSON.stringify({ success: true, data }), { status: 200 }));
      }),
    );
  });

  afterEach(() => {
    vi.unstubAllEnvs();
    localStorage.clear();
    vi.clearAllMocks();
    vi.unstubAllGlobals();
  });

  it('renders backend aggregate data and does not render fixture-only trend insights', async () => {
    vi.mocked(loadNutritionAnalysis).mockResolvedValue(response);
    renderPage();

    await screen.findByRole('tab', { name: '7 天' });
    await waitFor(() => expect(screen.getByText('4,200 kcal')).toBeInTheDocument());
    expect(loadNutritionAnalysis).toHaveBeenCalledWith('7d', expect.any(AbortSignal));
    expect(screen.getByText('220 g')).toBeInTheDocument();
    expect(screen.getByText('75%')).toBeInTheDocument();
    expect(screen.getByText(/有 1 项记录未匹配营养目录/)).toBeInTheDocument();
    expect(screen.getByText('未匹配项：自制酱料')).toBeInTheDocument();
    expect(screen.queryByText(/Protein distribution is heavily skewed/)).not.toBeInTheDocument();
    expect(screen.getByRole('tab', { name: '今天' })).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: '30 天' })).toBeInTheDocument();
    expect(screen.queryByRole('tab', { name: '90 天' })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: '导出 CSV' })).toBeDisabled();
  });

  it('exposes real Agent interpretation and planning routes from a completed analysis', async () => {
    vi.mocked(loadNutritionAnalysis).mockResolvedValue(response);
    const user = userEvent.setup();
    const interpretationView = renderPage('/analysis', true);

    await screen.findByText('4,200 kcal');
    await user.click(screen.getByRole('button', { name: '让 Agent 解读' }));
    const interpretationLocation = decodeURIComponent(screen.getByTestId('location').textContent ?? '');
    expect(interpretationLocation).toContain('/chat?prompt=请解读我最近 7 天的饮食摄入分析。');
    expect(interpretationLocation).toContain('未匹配项：自制酱料');

    interpretationView.unmount();
    vi.mocked(loadNutritionAnalysis).mockResolvedValue(response);
    renderPage('/analysis', true);

    await screen.findByText('4,200 kcal');
    await user.click(screen.getByRole('button', { name: '基于分析制定计划' }));
    expect(screen.getByTestId('location')).toHaveTextContent('/planning');
  });

  it('requests today and 30d independently in real mode', async () => {
    vi.mocked(loadNutritionAnalysis)
      .mockResolvedValueOnce(response)
      .mockResolvedValueOnce(todayEmptyResponse)
      .mockResolvedValueOnce({ ...response, range: '30d' });
    const user = userEvent.setup();
    renderPage();

    await waitFor(() => expect(loadNutritionAnalysis).toHaveBeenCalledWith('7d', expect.any(AbortSignal)));
    await user.click(screen.getByRole('tab', { name: '今天' }));
    await waitFor(() => expect(loadNutritionAnalysis).toHaveBeenCalledWith('today', expect.any(AbortSignal)));
    await waitFor(() => expect(screen.getByText('0 / 1 Days')).toBeInTheDocument());

    await user.click(screen.getByRole('tab', { name: '30 天' }));
    await waitFor(() => expect(loadNutritionAnalysis).toHaveBeenCalledWith('30d', expect.any(AbortSignal)));
    expect(loadNutritionAnalysis).not.toHaveBeenCalledWith('90d');
  });

  it('cancels the previous range request and ignores its late response', async () => {
    const first = deferred<NutritionAnalysis>();
    const second = deferred<NutritionAnalysis>();
    vi.mocked(loadNutritionAnalysis).mockImplementation((requestedRange) =>
      requestedRange === '7d' ? first.promise : second.promise,
    );
    const user = userEvent.setup();
    renderPage();

    await waitFor(() => expect(loadNutritionAnalysis).toHaveBeenCalledWith('7d', expect.any(AbortSignal)));
    const firstSignal = vi.mocked(loadNutritionAnalysis).mock.calls[0][1];
    await user.click(screen.getByRole('tab', { name: '今天' }));
    await waitFor(() => expect(loadNutritionAnalysis).toHaveBeenCalledWith('today', expect.any(AbortSignal)));

    expect(firstSignal?.aborted).toBe(true);
    first.resolve({ ...response, calories_kcal: 9999 });
    second.resolve({ ...response, range: 'today', calories_kcal: 900 });

    await waitFor(() => expect(screen.getByText('900 kcal')).toBeInTheDocument());
    expect(screen.queryByText('9,999 kcal')).not.toBeInTheDocument();
  });

  it('shows the empty state for a range with no backend records', async () => {
    vi.mocked(loadNutritionAnalysis).mockResolvedValue({
      ...response,
      total_items: 0,
      matched_items: 0,
      coverage: 0,
      incomplete: false,
      unmatched_names: [],
    });
    renderPage();

    await waitFor(() => expect(screen.getByText('当前范围暂无饮食记录')).toBeInTheDocument());
    expect(screen.getByText('0 / 7 Days')).toBeInTheDocument();
    expect(screen.queryByText('4,200 kcal')).not.toBeInTheDocument();
  });

  it('shows the backend error and retries the current range', async () => {
    vi.mocked(loadNutritionAnalysis).mockRejectedValue(new Error('NUTRITION_ANALYSIS_UNAVAILABLE'));
    const user = userEvent.setup();
    renderPage();

    await waitFor(() => expect(screen.getByRole('alert', { name: '分析数据加载失败' })).toBeInTheDocument());
    expect(screen.getByText('NUTRITION_ANALYSIS_UNAVAILABLE')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '重新加载' }));
    await waitFor(() => expect(loadNutritionAnalysis).toHaveBeenCalledTimes(2));
  });
});
