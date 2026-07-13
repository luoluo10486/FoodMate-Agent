import { Button, Card, Input, Modal, Table } from '@arco-design/web-react';
import type { TableColumnProps } from '@arco-design/web-react';
import { useState } from 'react';
import { Message } from '@arco-design/web-react';
import styles from '../AdminPage.module.css';
import { AdminFilters, OperationAuditCard } from './AdminComponents';
import { type KnowledgeRow, adminKnowledgeRows, canManage, statusTag } from './AdminShared';
import type { AdminActionPayload } from './types';

export function KnowledgeSection({ onAction }: { onAction: (payload: AdminActionPayload) => void }) {
  const [selectedDoc, setSelectedDoc] = useState<KnowledgeRow>(adminKnowledgeRows[0]);
  const [uploadVisible, setUploadVisible] = useState(false);

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
          <Button size="mini" onClick={() => setSelectedDoc(record)}>
            详情
          </Button>
          <Button
            size="mini"
            disabled={!canManage}
            onClick={() =>
              onAction({
                action: record.status === 'indexed' ? '下线文档' : '恢复文档',
                targetLabel: record.documentId,
                targetType: 'knowledge_document',
                targetId: record.documentId,
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
        <Card className={styles.wideCard} bordered={false}>
          <div className={styles.cardHead}>
            <strong>知识库文档</strong>
            <Button type="primary" disabled={!canManage} onClick={() => setUploadVisible(true)}>
              上传文档
            </Button>
          </div>
          <Table
            columns={knowledgeColumns}
            data={adminKnowledgeRows}
            pagination={{ pageSize: 5, total: adminKnowledgeRows.length }}
            size="small"
          />
        </Card>
        <aside className={styles.side}>
          <KnowledgeDetailCard document={selectedDoc} />
          <OperationAuditCard />
        </aside>
      </section>
      <Modal
        title="上传知识库文档"
        visible={uploadVisible}
        okText="提交 mock 上传"
        cancelText="取消"
        onCancel={() => setUploadVisible(false)}
        onOk={() => {
          setUploadVisible(false);
          Message.success('文档上传已提交；真实接入会写 MinIO 对象和索引任务');
        }}
      >
        <div className={styles.uploadMock}>
          <strong>选择文件</strong>
          <span>支持 PDF / Markdown / Excel，真实接入后限制大小、类型并记录上传人。</span>
          <Input placeholder="nutrition-guide.pdf" />
          <Input.TextArea placeholder="索引备注 / 标签" />
        </div>
      </Modal>
    </>
  );
}

function KnowledgeDetailCard({ document }: { document: KnowledgeRow }) {
  return (
    <Card className={styles.card} bordered={false}>
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
