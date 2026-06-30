export const adminOverviewMetrics = [
  { label: 'AgentRun 今日', value: '1,284', hint: '+12%', tone: 'green' },
  { label: '失败率', value: '2.1%', hint: '近 24h', tone: 'danger' },
  { label: '模型用量', value: '¥86.4', hint: '今日成本', tone: 'orange' },
  { label: '知识库索引', value: '97%', hint: 'indexed', tone: 'blue' }
];

export const adminAuditRows = [
  { key: 'run-1024', runId: 'run_1024', userId: 'user_10001', user: '梁同学', intent: 'planning', status: 'completed', durationMs: 942, toolCalls: 4, traceId: 'trace_plan_1024' },
  { key: 'run-1025', runId: 'run_1025', userId: 'user_10002', user: '王同学', intent: 'analysis', status: 'failed', durationMs: 1510, toolCalls: 2, traceId: 'trace_analysis_1025' },
  { key: 'run-1026', runId: 'run_1026', userId: 'user_10003', user: '运营账号', intent: 'knowledge_qna', status: 'completed', durationMs: 684, toolCalls: 1, traceId: 'trace_kb_1026' }
];

export const adminUserRows = [
  {
    key: 'user-10001',
    userId: 'user_10001',
    username: 'liang',
    email: 'liang@example.com',
    displayName: '梁同学',
    role: 'admin',
    status: 'active',
    avatarUrl: '',
    phone: '138****8801',
    dietGoal: '增肌控糖',
    calorieTarget: 2100,
    loginFailedCount: 0,
    lockedUntil: '-',
    lastLoginAt: '2026-06-25 15:40',
    createdAt: '2026-05-18 10:12'
  },
  {
    key: 'user-10002',
    userId: 'user_10002',
    username: 'wang',
    email: 'wang@example.com',
    displayName: '王同学',
    role: 'user',
    status: 'active',
    avatarUrl: '',
    phone: '139****7216',
    dietGoal: '减脂',
    calorieTarget: 1680,
    loginFailedCount: 1,
    lockedUntil: '-',
    lastLoginAt: '2026-06-24 20:16',
    createdAt: '2026-05-22 14:24'
  },
  {
    key: 'user-10003',
    userId: 'user_10003',
    username: 'ops',
    email: 'ops@example.com',
    displayName: '运营账号',
    role: 'operator',
    status: 'locked',
    avatarUrl: '',
    phone: '137****1104',
    dietGoal: '运营测试',
    calorieTarget: 1900,
    loginFailedCount: 5,
    lockedUntil: '2026-06-27 18:00',
    lastLoginAt: '2026-06-23 09:04',
    createdAt: '2026-04-30 09:00'
  }
];

export const adminUserSessionRows = [
  { key: 'session-1', userId: 'user_10001', device: 'Chrome / Windows', ip: '10.0.8.12', expiresAt: '2026-07-04 15:40', status: 'active' },
  { key: 'session-2', userId: 'user_10002', device: 'Safari / iOS', ip: '10.0.8.33', expiresAt: '2026-07-01 20:16', status: 'active' },
  { key: 'session-3', userId: 'user_10003', device: 'Chrome / macOS', ip: '10.0.8.77', expiresAt: '2026-06-28 09:04', status: 'revoked' }
];

export const adminResourceCards = [
  { title: '知识库文档', value: '146', detail: '3 个解析中，1 个索引失败' },
  { title: '工具注册', value: '6', detail: 'P0 工具均已启用' },
  { title: '软删除资源', value: '18', detail: '仅 admin 可恢复' }
];

export const adminToolRows = [
  { key: 'tool-knowledge', name: 'knowledge_search', version: '1.0.0', risk: 'medium', status: 'active', scope: 'user/operator/admin', owner: 'knowledge', schema: 'query, topK, filters', lastCalledAt: '2026-06-25 18:22' },
  { key: 'tool-db', name: 'database_query', version: '1.0.0', risk: 'medium', status: 'active', scope: 'internal', owner: 'platform', schema: 'sqlTemplateId, params, traceId', lastCalledAt: '2026-06-25 18:10' },
  { key: 'tool-food', name: 'food_log_writer', version: '1.0.0', risk: 'high', status: 'active', scope: 'user', owner: 'food-log', schema: 'mealType, items, idempotencyKey', lastCalledAt: '2026-06-25 17:51' },
  { key: 'tool-plan', name: 'plan_validator', version: '1.0.0', risk: 'low', status: 'active', scope: 'user', owner: 'planning', schema: 'planId, nutritionTargets', lastCalledAt: '2026-06-25 16:40' }
];

export const adminToolCallRows = [
  { key: 'call-1', callId: 'tool_call_8921', runId: 'run_1024', toolName: 'food_log_writer', status: 'success', latencyMs: 320, traceId: 'trace_plan_1024' },
  { key: 'call-2', callId: 'tool_call_8922', runId: 'run_1025', toolName: 'database_query', status: 'failed', latencyMs: 1180, traceId: 'trace_analysis_1025' },
  { key: 'call-3', callId: 'tool_call_8923', runId: 'run_1026', toolName: 'knowledge_search', status: 'success', latencyMs: 260, traceId: 'trace_kb_1026' }
];

export const adminSqlAuditRows = [
  { key: 'sql-1', auditId: 'sql_audit_301', actor: 'system', statement: 'select meal summary by user_id', risk: 'low', result: 'allowed', traceId: 'trace_plan_1024' },
  { key: 'sql-2', auditId: 'sql_audit_302', actor: 'system', statement: 'select tool failure by trace_id', risk: 'medium', result: 'allowed', traceId: 'trace_analysis_1025' },
  { key: 'sql-3', auditId: 'sql_audit_303', actor: 'admin', statement: 'restore soft deleted resource', risk: 'high', result: 'pending approval', traceId: 'trace_admin_7788' }
];

export const adminTraceRows = [
  { key: 'trace-1', traceId: 'trace_plan_1024', entry: 'planner -> food_log_writer -> plan_validator', status: 'completed', startedAt: '2026-06-25 15:41' },
  { key: 'trace-2', traceId: 'trace_analysis_1025', entry: 'analyzer -> database_query -> fallback', status: 'failed', startedAt: '2026-06-25 15:45' },
  { key: 'trace-3', traceId: 'trace_kb_1026', entry: 'retriever -> knowledge_search -> answer', status: 'completed', startedAt: '2026-06-25 16:02' }
];

export const adminModelUsageRows = [
  { key: 'usage-1', provider: 'openai', model: 'gpt-4.1-mini', scene: 'planning', tokens: '128k', cost: '¥42.8', latencyMs: 860, status: 'success' },
  { key: 'usage-2', provider: 'deepseek', model: 'deepseek-chat', scene: 'knowledge_qna', tokens: '76k', cost: '¥18.2', latencyMs: 720, status: 'success' },
  { key: 'usage-3', provider: 'openai', model: 'text-embedding-3-small', scene: 'embedding', tokens: '310k', cost: '¥25.4', latencyMs: 420, status: 'fallback' }
];

export const adminKnowledgeRows = [
  { key: 'doc-1', documentId: 'doc_146', title: '营养与烹饪指南', status: 'indexed', chunks: 428, owner: '运营账号', source: 'MinIO / knowledge/nutrition-guide.pdf', indexProgress: '100%', updatedAt: '2026-06-25 18:12' },
  { key: 'doc-2', documentId: 'doc_147', title: '高蛋白食材 FAQ', status: 'indexing', chunks: 116, owner: '运营账号', source: 'MinIO / knowledge/protein-faq.md', indexProgress: '64%', updatedAt: '2026-06-25 16:40' },
  { key: 'doc-3', documentId: 'doc_148', title: '家庭备餐规则', status: 'failed', chunks: 0, owner: 'admin', source: 'MinIO / knowledge/meal-prep.xlsx', indexProgress: '0%', updatedAt: '2026-06-24 09:30' }
];

export const adminDeletedRows = [
  { key: 'del-1', resourceType: 'food_log', resourceId: 'food_log_1024', owner: '王同学', deletedAt: '2026-06-25 11:08', reason: '用户删除' },
  { key: 'del-2', resourceType: 'knowledge_document', resourceId: 'doc_88', owner: '运营账号', deletedAt: '2026-06-24 17:22', reason: '下线旧版本' },
  { key: 'del-3', resourceType: 'meal_plan', resourceId: 'meal_plan_73', owner: '梁同学', deletedAt: '2026-06-23 20:04', reason: '计划废弃' }
];

export const adminOperationAuditRows = [
  { key: 'op-1', operator_id: 'user_10001', operator: 'admin', action: 'PATCH_USER_STATUS', target_type: 'user', target_id: 'user_10003', result: 'success', request_id: 'req_admin_7781', trace_id: 'trace_admin_7781', created_at: '2026-06-25 18:18' },
  { key: 'op-2', operator_id: 'user_10001', operator: 'admin', action: 'PATCH_TOOL_STATUS', target_type: 'tool', target_id: 'food_log_writer', result: 'success', request_id: 'req_admin_7782', trace_id: 'trace_admin_7782', created_at: '2026-06-25 17:40' },
  { key: 'op-3', operator_id: 'user_10001', operator: 'admin', action: 'RESTORE_RESOURCE', target_type: 'meal_plan', target_id: 'meal_plan_73', result: 'pending', request_id: 'req_admin_7783', trace_id: 'trace_admin_7783', created_at: '2026-06-25 16:52' }
];
