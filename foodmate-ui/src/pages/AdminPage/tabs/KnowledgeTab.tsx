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
import { loadAdminDashboard, updateKnowledgeStatus, uploadKnowledgeDocument } from '../../../services/adminService';

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
  const [uploadFile, setUploadFile] = useState<File | null>(null);
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
  const selectFile = (file: File | null) => {
    setUploadFile(file);
    if (file) setUploadVisible(true);
  };
  const submitUpload = async () => {
    if (isRealMode) {
      if (!uploadFile) return notify('请选择文件', 'warning');
      await uploadKnowledgeDocument(uploadFile);
    }
    setUploadVisible(false);
    setUploadFile(null);
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
        await updateKnowledgeStatus(selectedDoc.documentId, selectedDoc.status === 'indexed' ? 'disabled' : 'indexed');
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
            selectFile(event.dataTransfer.files[0] ?? null);
          }}
        >
          <UploadCloud aria-hidden="true" />
          <strong>拖入多个文件，后台异步建索引</strong>
          <span>Max file size: 50MB. Allowed formats: PDF, CSV, XLSX, TXT</span>
          <input
            id={fileInputId}
            aria-label="选择知识库文件"
            type="file"
            multiple
            accept=".pdf,.csv,.xlsx,.txt"
            onChange={(event: ChangeEvent<HTMLInputElement>) => selectFile(event.target.files?.[0] ?? null)}
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
            <strong>{uploadFile?.name ?? '选择文件'}</strong>
            <Textarea placeholder="索引备注 / 标签" />
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setUploadVisible(false)}>
              取消
            </Button>
            <Button onClick={() => void submitUpload()}>提交上传</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </section>
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
