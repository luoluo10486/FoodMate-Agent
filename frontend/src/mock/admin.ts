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
