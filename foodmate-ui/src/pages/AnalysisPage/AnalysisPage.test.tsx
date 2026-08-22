import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { AnalysisPage } from './AnalysisPage';

function LocationProbe() {
  const location = useLocation();
  return (
    <output data-testid="location" role="presentation">
      {location.pathname + location.search}
    </output>
  );
}

function renderPage(initialEntry = '/analysis') {
  return render(
    <MemoryRouter initialEntries={[initialEntry]}>
      <Routes>
        <Route path="/analysis" element={<AnalysisPage />} />
      </Routes>
      <LocationProbe />
    </MemoryRouter>,
  );
}

describe('AnalysisPage', () => {
  it('updates summary metrics when the range changes', async () => {
    const user = userEvent.setup();
    renderPage();

    const rangeTab = screen.getByRole('tab', { name: '30 天' });
    expect(rangeTab).toHaveClass('inline-flex');
    await user.click(rangeTab);

    expect(screen.getByRole('tab', { name: '30 天' })).toHaveAttribute('aria-selected', 'true');
    expect(screen.getByText('1,896 kcal')).toBeInTheDocument();
    expect(screen.getByText('26 / 30 Days')).toBeInTheDocument();
  });

  it('reports export state without changing the visible analysis', async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByRole('button', { name: '导出 CSV' }));

    expect(screen.getByText('分析报告已排队，完成后可下载 CSV。')).toBeInTheDocument();
    expect(screen.getByText('能量摄入与目标对比')).toBeInTheDocument();
  });

  it('renders loading, empty, and error analysis states with recovery paths', async () => {
    const user = userEvent.setup();
    const { unmount } = renderPage('/analysis?state=loading');
    expect(screen.getByLabelText('分析摘要加载中')).toHaveAttribute('aria-busy', 'true');
    expect(screen.getByLabelText('能量摄入分析加载中')).toBeInTheDocument();
    expect(screen.queryByText('1,940 kcal')).not.toBeInTheDocument();
    unmount();

    renderPage('/analysis?state=empty');
    expect(screen.getByText('数据不足，无法生成分析')).toBeInTheDocument();
    expect(screen.getByText('0 / 7 Days')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '去记录饮食' }));
    expect(screen.getByTestId('location')).toHaveTextContent('/analysis?view=records');
    unmount();

    renderPage('/analysis?state=error');
    expect(screen.getByRole('alert', { name: '分析数据加载失败' })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '重新加载' }));
    expect(screen.getAllByText('1,940 kcal')).not.toHaveLength(0);
  });
});
