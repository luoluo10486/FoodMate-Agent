import { FileText, Search, UploadCloud } from 'lucide-react';
import { ChangeEvent, DragEvent, useEffect, useId, useRef, useState } from 'react';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Textarea } from '@/components/ui/textarea';
import { Input } from '@/components/ui/input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import styles from '../AdminPage.module.css';
import { type KnowledgeRow, canManage } from './AdminShared';
import type { AdminActionPayload } from './types';
import type { AgentStreamConnection } from '../../../types/agent';
import {
  changeKnowledgeVisibility,
  loadAdminKnowledge,
  loadKnowledgeBatch,
  reindexKnowledgeItem,
  retryKnowledgeItem,
  streamKnowledgeBatch,
  updateKnowledgeStatus,
  uploadKnowledgeBatch,
  uploadKnowledgeDocument,
} from '../../../services/adminService';

const figmaKnowledgeRows: KnowledgeRow[] = [
  {
    key: 'doc-118a9',
    documentId: 'doc_118a9',
    title: 'USDA_Keto_Ingredient_Guidelines.pdf',
    status: 'indexed',
    visibility: 'published',
    chunks: 148,
    owner: 'Anddy',
    source: 'knowledge/USDA_Keto_Ingredient_Guidelines.pdf',
    indexProgress: '100%',
    updatedAt: '2026-07-31 10:24',
  },
  {
    key: 'doc-552b1',
    documentId: 'doc_552b1',
    title: 'FoodMate_Custom_Recipes_v3.csv',
    status: 'indexing',
    visibility: 'draft',
    chunks: 890,
    owner: 'Anddy',
    source: 'knowledge/FoodMate_Custom_Recipes_v3.csv',
    indexProgress: '64%',
    updatedAt: '2026-07-31 10:12',
  },
  {
    key: 'doc-990c4',
    documentId: 'doc_990c4',
    title: 'Allergen_Safety_Manual.xlsx',
    status: 'failed',
    visibility: 'draft',
    chunks: 0,
    owner: 'Anddy',
    source: 'knowledge/Allergen_Safety_Manual.xlsx',
    indexProgress: '0%',
    updatedAt: '2026-07-31 09:48',
  },
];

function documentSize(document: KnowledgeRow) {
  if (document.documentId === 'doc_118a9') return '4.2 MB';
  if (document.documentId === 'doc_552b1') return '12.8 MB';
  if (document.documentId === 'doc_990c4') return '890 KB';
  return document.chunks > 500 ? '12.8 MB' : document.chunks ? '4.2 MB' : '890 KB';
}

function documentStatus(document: KnowledgeRow, figmaFixture: boolean) {
  if (figmaFixture) {
    const fixtureLabel = document.status === 'indexed' ? '已索引' : document.status === 'indexing' ? '索引中' : '失败';
    return (
      <span className={`${styles.knowledgeStatus} ${styles[`knowledgeStatus${document.status}`] ?? ''}`}>
        {fixtureLabel}
      </span>
    );
  }
  const visibility = document.visibility;
  const label =
    visibility === 'published'
      ? '已发布'
      : visibility === 'disabled'
        ? '已下线'
        : visibility === 'draft'
          ? '草稿'
          : document.status === 'indexed'
            ? '已索引'
            : document.status === 'indexing'
              ? '索引中'
              : '失败';
  const styleKey =
    visibility === 'published' || visibility === 'disabled' || visibility === 'draft' ? visibility : document.status;
  return <span className={`${styles.knowledgeStatus} ${styles[`knowledgeStatus${styleKey}`] ?? ''}`}>{label}</span>;
}

export function KnowledgeSection({
  onAction,
  figmaFixture = false,
  openUploadRequest = 0,
  refreshNonce = 0,
  canManageAccess = canManage,
}: {
  onAction: (payload: AdminActionPayload) => void;
  figmaFixture?: boolean;
  openUploadRequest?: number;
  refreshNonce?: number;
  canManageAccess?: boolean;
}) {
  const isRealMode = import.meta.env.VITE_AGENT_MODE === 'real';
  const [documents, setDocuments] = useState<KnowledgeRow[]>(isRealMode ? [] : figmaKnowledgeRows);
  const [selectedDoc, setSelectedDoc] = useState<KnowledgeRow | undefined>(documents[0]);
  const [uploadVisible, setUploadVisible] = useState(false);
  const [uploadFiles, setUploadFiles] = useState<File[]>([]);
  const [uploadMode, setUploadMode] = useState<'batch' | 'single'>('batch');
  const [batchId, setBatchId] = useState<string | undefined>(() =>
    isRealMode ? (window.localStorage.getItem('foodmate:admin:knowledge:last-batch') ?? undefined) : undefined,
  );
  const [sourceName, setSourceName] = useState('管理员导入');
  const [sourceVersion, setSourceVersion] = useState('1');
  const [licenseNotice, setLicenseNotice] = useState('管理员确认具备发布授权');
  const [loading, setLoading] = useState(isRealMode);
  const [loadError, setLoadError] = useState('');
  const [localRefreshNonce, setLocalRefreshNonce] = useState(0);
  const [page, setPage] = useState(1);
  const [totalDocuments, setTotalDocuments] = useState(isRealMode ? 0 : figmaKnowledgeRows.length);
  const [query, setQuery] = useState('');
  const [statusFilter, setStatusFilter] = useState('all');
  const [visibilityFilter, setVisibilityFilter] = useState('all');
  const pageSize = 20;
  const fileInputId = useId();

  useEffect(() => {
    if (openUploadRequest > 0) {
      // 顶部批量上传按钮通过请求号通知子组件打开受控 Dialog。
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setUploadVisible(true);
    }
  }, [openUploadRequest]);

  useEffect(() => {
    if (!isRealMode) return;
    let active = true;
    // The effect owns the request lifecycle, so loading/error reset belongs to this subscription boundary.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setLoading(true);
    setLoadError('');
    loadAdminKnowledge({
      page,
      size: pageSize,
      query: query.trim() || undefined,
      status: statusFilter,
      visibility: visibilityFilter,
    })
      .then((result) => {
        if (!active) return;
        const rows = result.items as KnowledgeRow[];
        setDocuments(rows);
        setTotalDocuments(result.total);
        setSelectedDoc(rows[0]);
      })
      .catch((cause) => {
        if (!active) return;
        setDocuments([]);
        setTotalDocuments(0);
        setSelectedDoc(undefined);
        setLoadError(cause instanceof Error ? cause.message : '知识库数据加载失败');
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [isRealMode, localRefreshNonce, page, query, refreshNonce, statusFilter, visibilityFilter]);

  const notify = (message: string, tone: 'warning' | 'success') => {
    window.dispatchEvent(new CustomEvent('foodmate:admin-notice', { detail: { message, tone } }));
  };
  const selectFiles = (files: FileList | File[]) => {
    if (isRealMode && !canManageAccess) {
      return notify('当前角色没有知识库上传权限。', 'warning');
    }
    const selected = Array.from(files);
    // Figma fixture 只替换验收展示和对应的选择提示，真实模式继续遵循后端上传契约。
    const valid = figmaFixture
      ? selected.every((file) => file.size <= 50 * 1024 * 1024 && /\.(pdf|csv|xlsx|txt)$/i.test(file.name))
      : selected.length <= 20 &&
        selected.every((file) => file.size <= 20 * 1024 * 1024 && /\.(pdf|docx|md|txt)$/i.test(file.name));
    if (!valid) {
      return notify(
        figmaFixture
          ? '仅支持 PDF、CSV、XLSX、TXT 文件，单个不超过 50 MB。'
          : '仅支持至多 20 个 PDF、DOCX、Markdown 或 TXT 文件，单个不超过 20 MB。',
        'warning',
      );
    }
    setUploadFiles(selected);
    if (selected.length > 1) setUploadMode('batch');
    if (selected.length) setUploadVisible(true);
  };
  const closeUpload = () => {
    setUploadVisible(false);
    setUploadFiles([]);
    setUploadMode('batch');
  };
  const submitUpload = async () => {
    try {
      if (isRealMode && !canManageAccess) {
        return notify('当前角色没有知识库上传权限。', 'warning');
      }
      if (!uploadFiles.length) {
        return notify('请先选择要上传的文件。', 'warning');
      }
      if (uploadMode === 'single') {
        if (uploadFiles.length !== 1) {
          return notify('单文件上传只能选择一个文件。', 'warning');
        }
        const uploaded = await uploadKnowledgeDocument(uploadFiles[0]);
        setLocalRefreshNonce((current) => current + 1);
        closeUpload();
        notify(`文档 ${uploaded.document_id} 已提交`, 'success');
        return;
      }
      if (isRealMode) {
        if (!sourceName.trim() || !sourceVersion.trim() || !licenseNotice.trim()) {
          return notify('请完整填写来源、版本和授权说明。', 'warning');
        }
        const uploaded = await uploadKnowledgeBatch({
          files: uploadFiles,
          sourceType: 'admin_upload',
          sourceName,
          sourceVersion,
          licenseNotice,
          idempotencyKey: crypto.randomUUID(),
        });
        setBatchId(uploaded.batch_id);
        window.localStorage.setItem('foodmate:admin:knowledge:last-batch', uploaded.batch_id);
        setLocalRefreshNonce((current) => current + 1);
      }
      closeUpload();
      notify('文档上传已提交', 'success');
    } catch (cause) {
      notify(cause instanceof Error ? cause.message : '文档上传失败，请重试。', 'warning');
    }
  };
  const requestVisibilityChange = (visibility: 'published' | 'disabled' | 'draft' | 'deleted', label: string) => {
    if (!selectedDoc) return;
    onAction({
      action: label,
      targetLabel: selectedDoc.documentId,
      targetType: 'knowledge_document',
      targetId: selectedDoc.documentId,
      execute: async () => {
        if (isRealMode) await changeKnowledgeVisibility(selectedDoc.documentId, visibility);
        else await updateKnowledgeStatus(selectedDoc.documentId, visibility === 'disabled' ? 'disabled' : 'indexed');
      },
      onApply: () => {
        setDocuments((current) =>
          visibility === 'deleted'
            ? current.filter((document) => document.documentId !== selectedDoc.documentId)
            : current.map((document) =>
                document.documentId === selectedDoc.documentId
                  ? {
                      ...document,
                      visibility,
                      status:
                        document.status === 'indexed'
                          ? document.status
                          : visibility === 'draft'
                            ? 'parsed'
                            : document.status,
                    }
                  : document,
              ),
        );
        setSelectedDoc((current) =>
          visibility === 'deleted' ? undefined : current ? { ...current, visibility } : current,
        );
        setLocalRefreshNonce((current) => current + 1);
      },
    });
  };
  const selectedVisibility = selectedDoc?.visibility ?? (selectedDoc?.status === 'indexed' ? 'published' : 'draft');

  return (
    <section
      className={`${styles.knowledgeWorkspace} ${figmaFixture ? styles.knowledgeFixtureWorkspace : ''}`}
      aria-label="知识库文档管理"
    >
      <div className={styles.knowledgeMainColumn}>
        <label
          className={styles.knowledgeDropZone}
          htmlFor={fileInputId}
          onDragOver={(event: DragEvent<HTMLLabelElement>) => event.preventDefault()}
          onDrop={(event: DragEvent<HTMLLabelElement>) => {
            event.preventDefault();
            selectFiles(event.dataTransfer.files);
          }}
        >
          <UploadCloud aria-hidden="true" />
          <strong>拖入多个文件，后台异步建索引</strong>
          <span>
            {figmaFixture
              ? 'Max file size: 50MB. Allowed formats: PDF, CSV, XLSX, TXT.'
              : '最多 20 个文件，单个不超过 20 MB。支持 PDF、DOCX、Markdown、TXT。'}
          </span>
          <Input
            id={fileInputId}
            aria-label="选择知识库文件"
            type="file"
            multiple
            accept={figmaFixture ? '.pdf,.csv,.xlsx,.txt' : '.pdf,.docx,.md,.txt'}
            disabled={isRealMode && !canManageAccess}
            onChange={(event: ChangeEvent<HTMLInputElement>) => event.target.files && selectFiles(event.target.files)}
          />
        </label>
        {isRealMode ? (
          <section className={styles.auditFilters} aria-label="知识库筛选">
            <label className={styles.auditSearch}>
              <Search aria-hidden="true" />
              <Input
                value={query}
                onChange={(event) => {
                  setQuery(event.target.value);
                  setPage(1);
                }}
                placeholder="搜索标题或来源"
                aria-label="搜索知识库文档"
              />
            </label>
            <Select
              value={statusFilter}
              onValueChange={(value) => {
                setStatusFilter(value);
                setPage(1);
              }}
            >
              <SelectTrigger aria-label="索引状态筛选">
                <SelectValue placeholder="索引状态" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">全部索引状态</SelectItem>
                <SelectItem value="indexed">已索引</SelectItem>
                <SelectItem value="indexing">索引中</SelectItem>
                <SelectItem value="index_failed">索引失败</SelectItem>
              </SelectContent>
            </Select>
            <Select
              value={visibilityFilter}
              onValueChange={(value) => {
                setVisibilityFilter(value);
                setPage(1);
              }}
            >
              <SelectTrigger aria-label="可见性筛选">
                <SelectValue placeholder="可见性" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">全部可见性</SelectItem>
                <SelectItem value="draft">草稿</SelectItem>
                <SelectItem value="published">已发布</SelectItem>
                <SelectItem value="disabled">已下线</SelectItem>
              </SelectContent>
            </Select>
          </section>
        ) : null}
        <Card className={styles.knowledgeTableCard}>
          <div className={styles.knowledgeTableHeader}>
            <span>文档 ID</span>
            <span>标题</span>
            <span>大小</span>
            <span>上传 / 索引状态</span>
            <span>分块数</span>
          </div>
          {loading ? (
            <div className={styles.knowledgeTableEmpty}>正在加载知识库文档...</div>
          ) : documents.length ? (
            documents.map((document) => (
              <Button
                variant="ghost"
                className={`${styles.knowledgeTableRow} ${selectedDoc?.documentId === document.documentId ? styles.knowledgeTableRowSelected : ''}`}
                key={document.documentId}
                type="button"
                onClick={() => setSelectedDoc(document)}
              >
                <code>{document.documentId}</code>
                <span className={styles.knowledgeDocumentTitle}>
                  <FileText aria-hidden="true" />
                  <strong>{document.title}</strong>
                </span>
                <span>{isRealMode ? '-' : documentSize(document)}</span>
                <span>{documentStatus(document, figmaFixture)}</span>
                <span>{document.chunks} chunks</span>
              </Button>
            ))
          ) : loadError ? (
            <div className={styles.knowledgeTableEmpty} role="alert">
              <span>{loadError}</span>
              <Button variant="outline" onClick={() => setLocalRefreshNonce((current) => current + 1)}>
                重试
              </Button>
            </div>
          ) : (
            <div className={styles.knowledgeTableEmpty}>暂无可展示的知识库文档</div>
          )}
        </Card>
        {isRealMode ? (
          <nav className={styles.deletedPagination} aria-label="知识库文档分页">
            <span>
              显示第 {totalDocuments === 0 ? 0 : (page - 1) * pageSize + 1} 到{' '}
              {Math.min(page * pageSize, totalDocuments)} 条，共 {totalDocuments} 条结果
            </span>
            <div className={styles.deletedPageButtons}>
              <Button variant="outline" size="sm" disabled={page <= 1} onClick={() => setPage((value) => value - 1)}>
                上一页
              </Button>
              <span aria-label={`第 ${page} 页`}>{page}</span>
              <Button
                variant="outline"
                size="sm"
                disabled={page >= Math.max(1, Math.ceil(totalDocuments / pageSize))}
                onClick={() => setPage((value) => value + 1)}
              >
                下一页
              </Button>
            </div>
          </nav>
        ) : null}
      </div>
      <Card className={styles.knowledgeInsights}>
        <strong className={styles.knowledgeInsightsTitle}>文档向量洞察</strong>
        <div className={styles.knowledgeVectorStats}>
          <strong>索引向量统计</strong>
          <span>{isRealMode ? 'Dimensions: 由当前 RAG 模式决定' : 'Dimensions: 1536 (text-embedding-ada-002)'}</span>
          <span>Total Chunks indexed: {selectedDoc?.chunks ?? 0}</span>
        </div>
        <strong className={styles.knowledgeChunksTitle}>分块预览</strong>
        {isRealMode ? (
          <p className={styles.knowledgeChunkEmpty}>当前管理查询不返回原文分块，避免在后台展示未经授权的知识正文。</p>
        ) : (
          <div className={styles.knowledgeChunkList}>
            <ChunkPreview id="chunk_01" score="0.912" text="牛油果富含单不饱和脂肪，对维持治疗性酮症非常有效..." />
            <ChunkPreview id="chunk_02" score="0.884" text="避免食用酸面包，除非标明为低碳水高纤维小麦淀粉替代品..." />
          </div>
        )}
        {selectedDoc && !figmaFixture ? (
          <div className={styles.knowledgeManageActions}>
            {selectedVisibility === 'published' ? (
              <Button
                className={styles.knowledgeManageButton}
                disabled={!canManage}
                variant="outline"
                onClick={() => requestVisibilityChange('disabled', '下线文档')}
              >
                下线文档
              </Button>
            ) : (
              <>
                <Button
                  className={styles.knowledgeManageButton}
                  disabled={!canManage || selectedDoc.status !== 'indexed'}
                  variant="outline"
                  onClick={() => requestVisibilityChange('published', '发布文档')}
                >
                  发布文档
                </Button>
                <Button
                  className={styles.knowledgeManageButton}
                  disabled={!canManage}
                  variant="outline"
                  onClick={() => requestVisibilityChange('draft', '恢复草稿')}
                >
                  恢复草稿
                </Button>
              </>
            )}
            <Button
              className={styles.knowledgeManageButton}
              disabled={!canManage}
              variant="destructive"
              onClick={() => requestVisibilityChange('deleted', '删除文档')}
            >
              删除文档
            </Button>
          </div>
        ) : null}
      </Card>
      <Dialog open={uploadVisible} onOpenChange={setUploadVisible}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>上传知识库文档</DialogTitle>
            <DialogDescription>上传后将在后台完成解析和向量索引。</DialogDescription>
          </DialogHeader>
          {isRealMode ? (
            <div className={styles.uploadModes} role="group" aria-label="上传方式">
              <Button
                type="button"
                variant={uploadMode === 'batch' ? 'secondary' : 'outline'}
                aria-pressed={uploadMode === 'batch'}
                onClick={() => setUploadMode('batch')}
              >
                批量上传
              </Button>
              <Button
                type="button"
                variant={uploadMode === 'single' ? 'secondary' : 'outline'}
                aria-pressed={uploadMode === 'single'}
                onClick={() => setUploadMode('single')}
                disabled={uploadFiles.length > 1}
              >
                单文件上传
              </Button>
            </div>
          ) : null}
          <div className={styles.uploadMock}>
            <strong>{uploadFiles.length ? `已选择 ${uploadFiles.length} 个文件` : '选择文件'}</strong>
            {uploadMode === 'single' ? (
              <p className={styles.uploadModeHint}>单文件接口只提交当前文件，不附带批量来源元数据。</p>
            ) : (
              <>
                <Textarea
                  aria-label="来源名称"
                  value={sourceName}
                  onChange={(event) => setSourceName(event.target.value)}
                  placeholder="来源名称"
                />
                <Textarea
                  aria-label="来源版本"
                  value={sourceVersion}
                  onChange={(event) => setSourceVersion(event.target.value)}
                  placeholder="来源版本"
                />
                <Textarea
                  aria-label="授权说明"
                  value={licenseNotice}
                  onChange={(event) => setLicenseNotice(event.target.value)}
                  placeholder="授权说明"
                />
              </>
            )}
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={closeUpload}>
              取消
            </Button>
            <Button onClick={() => void submitUpload()}>提交上传</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
      {isRealMode && batchId ? (
        <BatchProgress
          batchId={batchId}
          canManageAccess={canManageAccess}
          onRetry={(documentId) => retryKnowledgeItem(batchId, documentId)}
          onReindex={(documentId) => reindexKnowledgeItem(batchId, documentId)}
        />
      ) : null}
    </section>
  );
}

function BatchProgress({
  batchId,
  canManageAccess,
  onRetry,
  onReindex,
}: {
  batchId: string;
  canManageAccess: boolean;
  onRetry: (documentId: string) => Promise<unknown>;
  onReindex: (documentId: string) => Promise<unknown>;
}) {
  const [detail, setDetail] = useState<Awaited<ReturnType<typeof loadKnowledgeBatch>>>();
  const [retryingItemId, setRetryingItemId] = useState<string>();
  const [retryError, setRetryError] = useState('');
  const [reindexingItemId, setReindexingItemId] = useState<string>();
  const [reindexError, setReindexError] = useState('');
  const [streamRetryNonce, setStreamRetryNonce] = useState(0);
  const [streamConnection, setStreamConnection] = useState<AgentStreamConnection>({
    state: 'connecting',
    attempt: 1,
    maxAttempts: 5,
  });
  const streamBatchRef = useRef<string>();
  const streamCursorRef = useRef<string>();
  const refresh = async () => {
    const next = await loadKnowledgeBatch(batchId);
    setDetail(next);
    return next;
  };
  const retry = async (itemId: string, documentId: string) => {
    setRetryingItemId(itemId);
    setRetryError('');
    try {
      await onRetry(documentId);
      await refresh();
      setStreamRetryNonce((value) => value + 1);
    } catch (cause) {
      setRetryError(cause instanceof Error ? cause.message : '索引重试失败，请稍后重试');
    } finally {
      setRetryingItemId(undefined);
    }
  };
  const reindex = async (itemId: string, documentId: string) => {
    setReindexingItemId(itemId);
    setReindexError('');
    try {
      await onReindex(documentId);
      await refresh();
      setStreamRetryNonce((value) => value + 1);
    } catch (cause) {
      setReindexError(cause instanceof Error ? cause.message : '重新索引失败，请稍后重试');
    } finally {
      setReindexingItemId(undefined);
    }
  };
  useEffect(() => {
    let active = true;
    if (streamBatchRef.current !== batchId) {
      streamBatchRef.current = batchId;
      streamCursorRef.current = undefined;
    }
    const load = () =>
      loadKnowledgeBatch(batchId)
        .then((value) => active && setDetail(value))
        .catch(() => undefined);
    load();
    const stream = streamKnowledgeBatch(batchId, load, {
      lastEventId: streamCursorRef.current,
      onStateChange: (connection) => {
        streamCursorRef.current = connection.lastEventId;
        if (active) setStreamConnection(connection);
      },
    });
    return () => {
      active = false;
      stream.close();
    };
  }, [batchId, streamRetryNonce]);
  const streamLabel =
    streamConnection.state === 'connecting'
      ? '正在连接实时进度...'
      : streamConnection.state === 'connected'
        ? '实时进度已连接'
        : streamConnection.state === 'reconnecting'
          ? `实时进度重连中（${streamConnection.attempt}/${streamConnection.maxAttempts}）`
          : streamConnection.state === 'exhausted'
            ? '实时进度连接失败，当前批次详情仍可刷新。'
            : '实时进度已结束';
  return (
    <Card className={styles.knowledgeInsights} aria-label="批次进度">
      <strong>批次 {batchId}</strong>
      <span>{detail?.batch.job.status ?? '上传已提交'}</span>
      <div className={styles.knowledgeStreamStatus} data-stream-state={streamConnection.state} role="status">
        <span>{streamLabel}</span>
        {streamConnection.state === 'exhausted' ? (
          <Button variant="outline" size="sm" onClick={() => setStreamRetryNonce((value) => value + 1)}>
            重连进度
          </Button>
        ) : null}
      </div>
      {retryError ? <span role="alert">{retryError}</span> : null}
      {reindexError ? <span role="alert">{reindexError}</span> : null}
      {detail?.batch.items.map((item) => (
        <div className={styles.knowledgeBatchItem} key={item.item_id}>
          <span>
            {item.filename}: {item.index_status}
            {item.error_code ? ` (${item.error_code})` : ''}
          </span>
          {canManageAccess ? (
            <div className={styles.knowledgeBatchActions}>
              {item.index_status === 'index_failed' ? (
                <Button
                  variant="outline"
                  disabled={retryingItemId === item.item_id || reindexingItemId === item.item_id}
                  onClick={() => void retry(item.item_id, item.document_id)}
                >
                  {retryingItemId === item.item_id ? '重试中...' : '重试'}
                </Button>
              ) : null}
              {item.index_status === 'indexed' || item.index_status === 'index_failed' ? (
                <Button
                  variant="outline"
                  disabled={retryingItemId === item.item_id || reindexingItemId === item.item_id}
                  onClick={() => void reindex(item.item_id, item.document_id)}
                >
                  {reindexingItemId === item.item_id ? '重新索引中...' : '重新索引'}
                </Button>
              ) : null}
            </div>
          ) : null}
        </div>
      ))}
    </Card>
  );
}

function ChunkPreview({ id, score, text }: { id: string; score: string; text: string }) {
  return (
    <article className={styles.knowledgeChunk}>
      <div>
        <code>{id}</code>
        <span>Score: {score}</span>
      </div>
      <p>{text}</p>
    </article>
  );
}
