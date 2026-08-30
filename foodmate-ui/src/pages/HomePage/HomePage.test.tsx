import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { HomePage } from './HomePage';

function LocationProbe() {
  return <output data-testid="location">{useLocation().pathname}</output>;
}

describe('HomePage session cards', () => {
  it('renders active sessions through shadcn buttons and keeps navigation intact', async () => {
    const user = userEvent.setup();

    render(
      <MemoryRouter initialEntries={['/']}>
        <Routes>
          <Route path="/" element={<HomePage />} />
          <Route path="/chat/:sessionId" element={<LocationProbe />} />
        </Routes>
      </MemoryRouter>,
    );

    const sessionCard = screen.getByRole('button', { name: /每周宏量调整/ });
    expect(sessionCard).toHaveClass('inline-flex');

    await user.click(sessionCard);
    expect(screen.getByTestId('location')).toHaveTextContent('/chat/week-plan');
  });

  it('renders the Figma workspace shell with its Chat session list', () => {
    render(
      <MemoryRouter initialEntries={['/?state=figma-v2']}>
        <Routes>
          <Route path="/" element={<HomePage />} />
        </Routes>
      </MemoryRouter>,
    );

    expect(screen.getByText('Anddy')).toBeInTheDocument();
    expect(screen.getByText('每周饮食微调')).toBeInTheDocument();
    expect(screen.getByPlaceholderText('搜索会话...')).toBeInTheDocument();
    expect(screen.getByLabelText('会话分页')).toBeInTheDocument();
    expect(
      within(screen.getByRole('navigation', { name: '主导航' })).queryByRole('link', { name: '知识库' }),
    ).toBeNull();
  });

  it('keeps the Figma pending queue as a compact panel instead of stretching to the activity panel height', () => {
    render(
      <MemoryRouter initialEntries={['/?state=figma-v2']}>
        <Routes>
          <Route path="/" element={<HomePage />} />
        </Routes>
      </MemoryRouter>,
    );

    expect(screen.getByRole('heading', { name: '待确认队列' }).closest('article')).toHaveClass('pendingPanel');
  });

  it('renders the Figma workspace implementation notes panel', () => {
    render(
      <MemoryRouter initialEntries={['/?state=figma-v2']}>
        <Routes>
          <Route path="/" element={<HomePage />} />
        </Routes>
      </MemoryRouter>,
    );

    expect(screen.getByRole('heading', { name: '任务入口与状态' })).toBeInTheDocument();
  });
});
