import { useEffect, useState } from 'react';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { DataTable, type TableColumnProps } from '@/components/ui/data-table';
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
import { AdminFilters, OperationAuditCard } from './AdminComponents';
import { type KnowledgeRow, adminKnowledgeRows, canManage, statusTag } from './AdminShared';
import type { AdminActionPayload } from './types';
import { loadAdminDashboard, updateKnowledgeStatus, uploadKnowledgeDocument } from '../../../services/adminService';

export function KnowledgeSection({ onAction }: { onAction: (payload: AdminActionPayload) => void }) {
  const [documents, setDocuments] = useState<KnowledgeRow[]>(
    import.meta.env.VITE_AGENT_MODE === 'real' ? [] : adminKnowledgeRows,
  );
  const [selectedDoc, setSelectedDoc] = useState<KnowledgeRow | undefined>(documents[0]);
  const [uploadVisible, setUploadVisible] = useState(false);
  const [uploadFile, setUploadFile] = useState<File | null>(null);
  useEffect(() => {
    if (import.meta.env.VITE_AGENT_MODE === 'real')
      loadAdminDashboard()
        .then((d) => {
          const rows = d.knowledge as KnowledgeRow[];
          setDocuments(rows);
          setSelectedDoc(rows[0]);
        })
        .catch(() => setDocuments([]));
  }, []);

  const notify = (message: string, tone: 'warning' | 'success') => {
    window.dispatchEvent(new CustomEvent('foodmate:admin-notice', { detail: { message, tone } }));
  };

  const submitUpload = async () => {
    if (import.meta.env.VITE_AGENT_MODE === 'real') {
      if (!uploadFile) {
        notify('请选择文件', 'warning');
        return;
      }
      await uploadKnowledgeDocument(uploadFile);
    }
    setUploadVisible(false);
    notify('文档上传已提交', 'success');
  };

  const knowledgeColumns: TableColumnProps<KnowledgeRow>[] = [
    { title: '文档 ID', dataIndex: 'documentId' },
    { title: '文档', dataIndex: 'title' },
    { title: '状态', dataIndex: 'status', render: statusTag },
    { title: 'Chunks', dataIndex: 'chunks' },
    { title: '索引进度', dataIndex: 'indexProgress' },
    { title: '负责人', dataIndex: 'owner' },
    { title: '更新时间', dataIndex: 'updatedAt' },
    {
      title: '操作',
      render: (_, record) => (
        <div className={styles.rowActions}>
          <Button variant="outline" size="sm" onClick={() => setSelectedDoc(record)}>
            详情
          </Button>
          <Button
            variant="outline"
            size="sm"
            disabled={!canManage}
            onClick={() =>
              onAction({
                action: record.status === 'indexed' ? '下线文档' : '恢复文档',
                targetLabel: record.documentId,
                targetType: 'knowledge_document',
                targetId: record.documentId,
                execute: async () => {
                  await updateKnowledgeStatus(record.documentId, record.status === 'indexed' ? 'disabled' : 'indexed');
                },
                onApply: () => {
                  record.status = record.status === 'indexed' ? 'disabled' : 'indexed';
                  record.indexProgress = record.status === 'indexed' ? '100%' : '0%';
                },
              })
            }
          >
            {record.status === 'indexed' ? '下线' : '恢复'}
          </Button>
        </div>
      ),
    },
  ];

  return (
    <>
      <AdminFilters placeholder="documentId / title / owner" />
      <section className={styles.sectionLayout}>
        <Card className={styles.wideCard}>
          <div className={styles.cardHead}>
            <strong>知识库文档</strong>
            <Button disabled={!canManage} onClick={() => setUploadVisible(true)}>
              上传文档
            </Button>
          </div>
          <DataTable columns={knowledgeColumns} data={documents} />
        </Card>
        <aside className={styles.side}>
          {selectedDoc ? <KnowledgeDetailCard document={selectedDoc} /> : null}
          <OperationAuditCard />
        </aside>
      </section>
      <Dialog open={uploadVisible} onOpenChange={setUploadVisible}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>上传知识库文档</DialogTitle>
            <DialogDescription>支持 PDF / Markdown / Excel，真实接入后限制大小、类型并记录上传人。</DialogDescription>
          </DialogHeader>
          <div className={styles.uploadMock}>
            <strong>选择文件</strong>
            <input type="file" onChange={(event) => setUploadFile(event.target.files?.[0] ?? null)} />
            <Textarea placeholder="索引备注 / 标签" />
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setUploadVisible(false)}>
              取消
            </Button>
            <Button onClick={() => void submitUpload()}>提交 mock 上传</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
}

function KnowledgeDetailCard({ document }: { document: KnowledgeRow }) {
  return (
    <Card className={styles.card}>
      <div className={styles.cardHead}>
        <strong>文档详情</strong>
        {statusTag(document.status)}
      </div>
      <div className={styles.detailGrid}>
        <span>文档 ID</span>
        <strong>{document.documentId}</strong>
        <span>来源</span>
        <strong>{document.source}</strong>
        <span>索引进度</span>
        <strong>{document.indexProgress}</strong>
        <span>切片数</span>
        <strong>{document.chunks}</strong>
      </div>
    </Card>
  );
}
