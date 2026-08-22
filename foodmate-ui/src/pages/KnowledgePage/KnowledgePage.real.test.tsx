import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { KnowledgePage } from './KnowledgePage';
import { searchKnowledge } from '../../services/knowledgeService';

vi.mock('../../services/knowledgeService', () => ({
  searchKnowledge: vi.fn(),
}));

function renderPage(initialEntry = '/knowledge') {
  return render(
    <MemoryRouter initialEntries={[initialEntry]}>
      <Routes>
        <Route path="/knowledge" element={<KnowledgePage />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('KnowledgePage real mode', () => {
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

  it('renders only returned citation fields after a real search', async () => {
    vi.mocked(searchKnowledge).mockResolvedValue([
      {
        document_id: 42,
        citation_id: 'cit-42-1',
        title: '低 GI 早餐指南',
        version: '2026.08',
        section_path: '早餐/谷物',
        snippet: '燕麦与坚果搭配可以提高早餐的膳食纤维密度。',
      },
    ]);
    const user = userEvent.setup();
    renderPage();
    await screen.findByRole('heading', { name: '知识库' });

    const search = screen.getByRole('textbox', { name: '搜索食物知识、食材、烹饪技巧' });
    await user.type(search, '低 GI');
    await user.keyboard('{Enter}');

    await waitFor(() => expect(screen.getByRole('heading', { name: '低 GI 早餐指南' })).toBeInTheDocument());
    expect(searchKnowledge).toHaveBeenCalledWith('低 GI');
    expect(screen.getByText('cit-42-1')).toBeInTheDocument();
    expect(screen.getByText('版本 2026.08')).toBeInTheDocument();
    expect(screen.getByText('章节 早餐/谷物')).toBeInTheDocument();
    expect(screen.getByText('DOC ID: 42')).toBeInTheDocument();
    expect(screen.queryByText('98% Match')).not.toBeInTheDocument();
    expect(screen.queryByText('NIH 研究实验室文献库')).not.toBeInTheDocument();
  });

  it('shows an explicit empty state without fixture results', async () => {
    vi.mocked(searchKnowledge).mockResolvedValue([]);
    const user = userEvent.setup();
    renderPage();
    await screen.findByRole('heading', { name: '知识库' });

    const search = screen.getByRole('textbox', { name: '搜索食物知识、食材、烹饪技巧' });
    await user.type(search, '没有这篇文档');
    await user.keyboard('{Enter}');

    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('没有找到相关内容'));
    expect(screen.queryByText('烹饪温度对牛油果健康脂肪的影响')).not.toBeInTheDocument();
    expect(screen.getByText('显示 0 条结果')).toBeInTheDocument();
  });

  it('shows the backend error and retries the same query without fixture fallback', async () => {
    vi.mocked(searchKnowledge).mockRejectedValue(new Error('RAG_UNAVAILABLE'));
    const user = userEvent.setup();
    renderPage();
    await screen.findByRole('heading', { name: '知识库' });

    const search = screen.getByRole('textbox', { name: '搜索食物知识、食材、烹饪技巧' });
    await user.type(search, '营养检索');
    await user.keyboard('{Enter}');

    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('检索失败'));
    expect(screen.getByText('RAG_UNAVAILABLE')).toBeInTheDocument();
    expect(screen.queryByText('烹饪温度对牛油果健康脂肪的影响')).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: '重新检索' }));
    await waitFor(() => expect(searchKnowledge).toHaveBeenCalledTimes(2));
  });
});
