import { FileText, UploadCloud } from 'lucide-react';
import { ChangeEvent, DragEvent, useEffect, useId, useState } from 'react';
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
import styles from '../AdminPage.module.css';
import { type KnowledgeRow, canManage } from './AdminShared';
import type { AdminActionPayload } from './types';
import {
  changeKnowledgeVisibility,
  loadAdminDashboard,
  loadKnowledgeBatch,
  retryKnowledgeItem,
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

function documentStatus(document: KnowledgeRow) {
  const label = document.status === 'indexed' ? '已索引' : document.status === 'indexing' ? '索引中' : '失败';
  return <span className={`${styles.knowledgeStatus} ${styles[`knowledgeStatus${document.status}`]}`}>{label}</span>;
}

export function KnowledgeSection({ onAction }: { onAction: (payload: AdminActionPayload) => void }) {
  const isRealMode = import.meta.env.VITE_AGENT_MODE === 'real';
  const [documents, setDocuments] = useState<KnowledgeRow[]>(isRealMode ? [] : figmaKnowledgeRows);
  const [selectedDoc, setSelectedDoc] = useState<KnowledgeRow | undefined>(documents[0]);
  const [uploadVisible, setUploadVisible] = useState(false);
  const [uploadFiles, setUploadFiles] = useState<File[]>([]);
  const [batchId, setBatchId] = useState<string>();
  const [sourceName, setSourceName] = useState('管理员导入');
  const [sourceVersion, setSourceVersion] = useState('1');
  const [licenseNotice, setLicenseNotice] = useState('管理员确认具备发布授权');
  const fileInputId = useId();

  useEffect(() => {
    if (!isRealMode) return;
    loadAdminDashboard()
      .then((dashboard) => {
        const rows = dashboard.knowledge as KnowledgeRow[];
        setDocuments(rows);
        setSelectedDoc(rows[0]);
      })
      .catch(() => setDocuments([]));
  }, [isRealMode]);

  const notify = (message: string, tone: 'warning' | 'success') => {
    window.dispatchEvent(new CustomEvent('foodmate:admin-notice', { detail: { message, tone } }));
  };
  const selectFiles = (files: FileList | File[]) => {
    const selected = Array.from(files);
    const valid = selected.length <= 20 && selected.every((file) => file.size <= 20 * 1024 * 1024 && /\.(pdf|docx|md|txt)$/i.test(file.name));
    if (!valid) return notify('仅支持至多 20 个 PDF、DOCX、Markdown 或 TXT 文件，单个不超过 20 MB。', 'warning');
    setUploadFiles(selected);
    if (selected.length) setUploadVisible(true);
  };
  const submitUpload = async () => {
    if (isRealMode) {
      if (!uploadFiles.length || !sourceName.trim() || !sourceVersion.trim() || !licenseNotice.trim()) return notify('请完整填写来源、版本和授权说明。', 'warning');
      const uploaded = await uploadKnowledgeBatch({
        files: uploadFiles, sourceType: 'admin_upload', sourceName, sourceVersion, licenseNotice,
        idempotencyKey: crypto.randomUUID(),
      });
      setBatchId(uploaded.batch_id);
    }
    setUploadVisible(false);
    setUploadFiles([]);
    notify('文档上传已提交', 'success');
  };
  const requestStatusChange = () => {
    if (!selectedDoc) return;
    onAction({
      action: selectedDoc.status === 'indexed' ? '下线文档' : '恢复文档',
      targetLabel: selectedDoc.documentId,
      targetType: 'knowledge_document',
      targetId: selectedDoc.documentId,
      execute: async () => {
        if (isRealMode)
          await changeKnowledgeVisibility(selectedDoc.documentId, selectedDoc.status === 'indexed' ? 'disabled' : 'published');
        else await updateKnowledgeStatus(selectedDoc.documentId, selectedDoc.status === 'indexed' ? 'disabled' : 'indexed');
      },
      onApply: () =>
        setDocuments((current) =>
          current.map((document) =>
            document.documentId === selectedDoc.documentId
              ? {
                  ...document,
                  status: document.status === 'indexed' ? 'disabled' : 'indexed',
                  indexProgress: document.status === 'indexed' ? '0%' : '100%',
                }
              : document,
          ),
        ),
    });
  };

  return (
    <section className={styles.knowledgeWorkspace} aria-label="知识库文档管理">
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
          <span>最多 20 个文件，单个不超过 20 MB。支持 PDF、DOCX、Markdown、TXT。</span>
          <input
            id={fileInputId}
            aria-label="选择知识库文件"
            type="file"
            multiple
            accept=".pdf,.docx,.md,.txt"
            onChange={(event: ChangeEvent<HTMLInputElement>) => event.target.files && selectFiles(event.target.files)}
          />
        </label>
        <Card className={styles.knowledgeTableCard}>
          <div className={styles.knowledgeTableHeader}>
            <span>文档 ID</span>
            <span>标题</span>
            <span>大小</span>
            <span>上传 / 索引状态</span>
            <span>分块数</span>
          </div>
          {documents.length ? (
            documents.map((document) => (
              <button
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
                <span>{documentSize(document)}</span>
                <span>{documentStatus(document)}</span>
                <span>{document.chunks} chunks</span>
              </button>
            ))
          ) : (
            <div className={styles.knowledgeTableEmpty}>暂无可展示的知识库文档</div>
          )}
        </Card>
      </div>
      <Card className={styles.knowledgeInsights}>
        <strong className={styles.knowledgeInsightsTitle}>文档向量洞察</strong>
        <div className={styles.knowledgeVectorStats}>
          <strong>索引向量统计</strong>
          <span>Dimensions: 1536 (text-embedding-ada-002)</span>
          <span>Total Chunks indexed: {selectedDoc?.chunks ?? 0}</span>
        </div>
        <strong className={styles.knowledgeChunksTitle}>分块预览</strong>
        <div className={styles.knowledgeChunkList}>
          <ChunkPreview id="chunk_01" score="0.912" text="牛油果富含单不饱和脂肪，对维持治疗性酮症非常有效..." />
          <ChunkPreview id="chunk_02" score="0.884" text="避免食用酸面包，除非标明为低碳水高纤维小麦淀粉替代品..." />
        </div>
        {selectedDoc ? (
          <Button
            className={styles.knowledgeManageButton}
            disabled={!canManage}
            variant="outline"
            onClick={requestStatusChange}
          >
            {selectedDoc.status === 'indexed' ? '下线文档' : '恢复文档'}
          </Button>
        ) : null}
      </Card>
      <Dialog open={uploadVisible} onOpenChange={setUploadVisible}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>上传知识库文档</DialogTitle>
            <DialogDescription>上传后将在后台完成解析和向量索引。</DialogDescription>
          </DialogHeader>
          <div className={styles.uploadMock}>
            <strong>{uploadFiles.length ? `已选择 ${uploadFiles.length} 个文件` : '选择文件'}</strong>
            <Textarea aria-label="来源名称" value={sourceName} onChange={(event) => setSourceName(event.target.value)} placeholder="来源名称" />
            <Textarea aria-label="来源版本" value={sourceVersion} onChange={(event) => setSourceVersion(event.target.value)} placeholder="来源版本" />
            <Textarea aria-label="授权说明" value={licenseNotice} onChange={(event) => setLicenseNotice(event.target.value)} placeholder="授权说明" />
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setUploadVisible(false)}>
              取消
            </Button>
            <Button onClick={() => void submitUpload()}>提交上传</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
      {isRealMode && batchId ? <BatchProgress batchId={batchId} onRetry={(itemId) => retryKnowledgeItem(batchId, itemId)} /> : null}
    </section>
  );
}

function BatchProgress({ batchId, onRetry }: { batchId: string; onRetry: (itemId: string) => Promise<unknown> }) {
  const [detail, setDetail] = useState<Awaited<ReturnType<typeof loadKnowledgeBatch>>>();
  useEffect(() => {
    let active = true;
    const refresh = () => loadKnowledgeBatch(batchId).then((value) => active && setDetail(value)).catch(() => undefined);
    refresh();
    const timer = window.setInterval(refresh, 1000);
    return () => { active = false; window.clearInterval(timer); };
  }, [batchId]);
  return <Card className={styles.knowledgeInsights} aria-label="批次进度">
    <strong>批次 {batchId}</strong>
    <span>{detail?.batch.job.status ?? '上传已提交'}</span>
    {detail?.batch.items.map((item) => <div key={item.item_id}>
      <span>{item.filename}: {item.index_status}{item.error_code ? ` (${item.error_code})` : ''}</span>
      {item.index_status === 'index_failed' ? <Button variant="outline" onClick={() => void onRetry(item.item_id)}>重试</Button> : null}
    </div>)}
  </Card>;
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
