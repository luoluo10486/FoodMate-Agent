import { apiRequest } from './apiClient';

export type KnowledgeCitation = {
  document_id: number;
  citation_id: string;
  title: string;
  version: string;
  section_path: string;
  snippet: string;
};

type KnowledgeSearchResponse = {
  citations: KnowledgeCitation[];
};

/** Searches only the Java-authorized public published knowledge scope. */
export async function searchKnowledge(query: string): Promise<KnowledgeCitation[]> {
  const normalizedQuery = query.trim();
  if (!normalizedQuery) return [];
  const response = await apiRequest<KnowledgeSearchResponse>('/api/knowledge-base/search', {
    method: 'POST',
    body: JSON.stringify({ query: normalizedQuery }),
  });
  return response.citations ?? [];
}
