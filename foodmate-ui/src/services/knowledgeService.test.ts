import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { searchKnowledge } from './knowledgeService';

describe('knowledgeService', () => {
  beforeEach(() => {
    vi.stubEnv('VITE_AGENT_MODE', 'real');
  });

  afterEach(() => {
    vi.unstubAllEnvs();
    vi.unstubAllGlobals();
  });

  it('posts the normalized query to the public knowledge search endpoint', async () => {
    const response = {
      citations: [
        {
          document_id: 7,
          citation_id: 'cit-7-1',
          title: '公共饮食指南',
          version: 'v1',
          section_path: '基础',
          snippet: '安全片段',
        },
      ],
    };
    const fetchMock = vi
      .fn()
      .mockResolvedValue(new Response(JSON.stringify({ success: true, data: response }), { status: 200 }));
    vi.stubGlobal('fetch', fetchMock);

    await expect(searchKnowledge('  公共饮食指南  ')).resolves.toEqual(response.citations);
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/knowledge-base/search',
      expect.objectContaining({ method: 'POST', credentials: 'include' }),
    );
    const [, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(JSON.parse(String(init.body))).toEqual({ query: '公共饮食指南' });
  });

  it('does not call the API for a blank query', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);

    await expect(searchKnowledge('   ')).resolves.toEqual([]);
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
