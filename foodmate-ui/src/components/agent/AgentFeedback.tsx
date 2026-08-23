import { ThumbsDown, ThumbsUp } from 'lucide-react';
import { useState } from 'react';
import { Button } from '../ui/button';
import { Checkbox } from '../ui/checkbox';
import { Textarea } from '../ui/textarea';
import { apiRequest } from '../../services/apiClient';
import styles from './AgentFeedback.module.css';

type AgentFeedbackProps = {
  runId: string;
  messageId: string;
};

const reasons = [
  ['incorrect', '内容不准确'],
  ['incomplete', '内容不完整'],
  ['irrelevant', '没有解决问题'],
  ['missing_citation', '缺少引用'],
  ['fabricated_execution', '虚构了执行结果'],
  ['unsafe_or_privacy', '存在安全或隐私问题'],
] as const;

export function AgentFeedback({ runId, messageId }: AgentFeedbackProps) {
  const [helpful, setHelpful] = useState<boolean>();
  const [selectedReasons, setSelectedReasons] = useState<string[]>([]);
  const [comment, setComment] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [submitted, setSubmitted] = useState(false);
  const [error, setError] = useState('');

  if (submitted) return <div className={styles.submitted}>感谢反馈</div>;

  const submit = async (value: boolean, reasonCodes = selectedReasons) => {
    if (!value && reasonCodes.length === 0) {
      setError('请选择至少一个原因');
      return;
    }
    setSubmitting(true);
    setError('');
    try {
      await apiRequest(
        `/api/agent-runs/${encodeURIComponent(runId)}/messages/${encodeURIComponent(messageId)}/feedback`,
        {
          method: 'POST',
          headers: { 'Idempotency-Key': `agent-feedback-${runId}-${messageId}` },
          body: JSON.stringify({ helpful: value, reason_codes: reasonCodes, comment: comment || undefined }),
        },
      );
      setHelpful(value);
      setSubmitted(true);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '反馈提交失败，请稍后重试');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <section className={styles.root} aria-label="回答反馈">
      <div className={styles.actions}>
        <span>这条回答有帮助吗？</span>
        <Button
          type="button"
          size="sm"
          variant={helpful === true ? 'secondary' : 'ghost'}
          disabled={submitting}
          aria-label="有帮助"
          onClick={() => void submit(true, [])}
        >
          <ThumbsUp aria-hidden="true" />
          有帮助
        </Button>
        <Button
          type="button"
          size="sm"
          variant={helpful === false ? 'secondary' : 'ghost'}
          disabled={submitting}
          aria-label="没帮助"
          onClick={() => setHelpful(false)}
        >
          <ThumbsDown aria-hidden="true" />
          没帮助
        </Button>
      </div>
      {helpful === false ? (
        <div className={styles.details}>
          <div className={styles.reasonList}>
            {reasons.map(([code, label]) => (
              <label className={styles.reason} key={code}>
                <Checkbox
                  checked={selectedReasons.includes(code)}
                  onCheckedChange={(checked) =>
                    setSelectedReasons((current) =>
                      checked ? [...new Set([...current, code])] : current.filter((item) => item !== code),
                    )
                  }
                />
                <span>{label}</span>
              </label>
            ))}
          </div>
          <Textarea
            value={comment}
            maxLength={1000}
            onChange={(event) => setComment(event.target.value)}
            placeholder="补充说明（可选）"
            aria-label="反馈补充说明"
          />
          <Button type="button" size="sm" disabled={submitting} onClick={() => void submit(false)}>
            提交反馈
          </Button>
        </div>
      ) : null}
      {error ? (
        <span className={styles.error} role="alert">
          {error}
        </span>
      ) : null}
    </section>
  );
}
