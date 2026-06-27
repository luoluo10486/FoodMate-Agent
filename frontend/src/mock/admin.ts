export const adminOverviewMetrics = [
  { label: 'AgentRun 今日', value: '1,284', hint: '+12%', tone: 'green' },
  { label: '失败率', value: '2.1%', hint: '近 24h', tone: 'danger' },
  { label: '模型用量', value: '¥86.4', hint: '今日成本', tone: 'orange' },
  { label: '知识库索引', value: '97%', hint: 'indexed', tone: 'blue' }
];

export const adminAuditRows = [
  { key: 'run-1024', runId: 'run_1024', user: '梁同学', intent: 'planning', status: 'completed', traceId: 'trace_plan_1024' },
  { key: 'run-1025', runId: 'run_1025', user: '王同学', intent: 'analysis', status: 'failed', traceId: 'trace_analysis_1025' },
  { key: 'run-1026', runId: 'run_1026', user: '运营账号', intent: 'knowledge_qna', status: 'completed', traceId: 'trace_kb_1026' }
];

export const adminUserRows = [
  { key: 'user-10001', username: 'liang', displayName: '梁同学', role: 'admin', status: 'active', lastLoginAt: '2026-06-25 15:40' },
  { key: 'user-10002', username: 'wang', displayName: '王同学', role: 'user', status: 'active', lastLoginAt: '2026-06-24 20:16' },
  { key: 'user-10003', username: 'ops', displayName: '运营账号', role: 'operator', status: 'locked', lastLoginAt: '2026-06-23 09:04' }
];

export const adminResourceCards = [
  { title: '知识库文档', value: '146', detail: '3 个解析中，1 个索引失败' },
  { title: '工具注册', value: '6', detail: 'P0 工具均已启用' },
  { title: '软删除资源', value: '18', detail: '仅 admin 可恢复' }
];

export const adminToolRows = [
  { key: 'tool-knowledge', name: 'knowledge_search', version: '1.0.0', risk: 'medium', status: 'active', scope: 'user/operator/admin' },
  { key: 'tool-db', name: 'database_query', version: '1.0.0', risk: 'medium', status: 'active', scope: 'internal' },
  { key: 'tool-food', name: 'food_log_writer', version: '1.0.0', risk: 'high', status: 'active', scope: 'user' },
  { key: 'tool-plan', name: 'plan_validator', version: '1.0.0', risk: 'low', status: 'active', scope: 'user' }
];

export const adminModelUsageRows = [
  { key: 'usage-1', provider: 'openai', model: 'gpt-4.1-mini', scene: 'planning', tokens: '128k', cost: '¥42.8', latencyMs: 860, status: 'success' },
  { key: 'usage-2', provider: 'deepseek', model: 'deepseek-chat', scene: 'knowledge_qna', tokens: '76k', cost: '¥18.2', latencyMs: 720, status: 'success' },
  { key: 'usage-3', provider: 'openai', model: 'text-embedding-3-small', scene: 'embedding', tokens: '310k', cost: '¥25.4', latencyMs: 420, status: 'fallback' }
];

export const adminKnowledgeRows = [
  { key: 'doc-1', title: '营养与烹饪指南', status: 'indexed', chunks: 428, owner: '运营账号', updatedAt: '2026-06-25 18:12' },
  { key: 'doc-2', title: '高蛋白食材 FAQ', status: 'indexing', chunks: 116, owner: '运营账号', updatedAt: '2026-06-25 16:40' },
  { key: 'doc-3', title: '家庭备餐规则', status: 'failed', chunks: 0, owner: 'admin', updatedAt: '2026-06-24 09:30' }
];

export const adminDeletedRows = [
  { key: 'del-1', resourceType: 'food_log', resourceId: 'food_log_1024', owner: '王同学', deletedAt: '2026-06-25 11:08', reason: '用户删除' },
  { key: 'del-2', resourceType: 'knowledge_document', resourceId: 'doc_88', owner: '运营账号', deletedAt: '2026-06-24 17:22', reason: '下线旧版本' },
  { key: 'del-3', resourceType: 'meal_plan', resourceId: 'meal_plan_73', owner: '梁同学', deletedAt: '2026-06-23 20:04', reason: '计划废弃' }
];
