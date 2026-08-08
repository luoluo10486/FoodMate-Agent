import { ChevronDown, Paperclip, Send, Square } from 'lucide-react';
import { useState } from 'react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import styles from './Composer.module.css';

type ComposerProps = {
  value?: string;
  placeholder?: string;
  running?: boolean;
  disabled?: boolean;
  toolsUsed: number;
  toolsTotal: number;
  agentsUsed: number;
  agentsTotal: number;
  onChange?: (value: string) => void;
  onSend?: () => void;
  onStop?: () => void;
};

export function Composer({
  value,
  placeholder = '让 FoodMate 计算、分析、记录或规划...',
  running = false,
  disabled = false,
  toolsUsed,
  toolsTotal,
  agentsUsed,
  agentsTotal,
  onChange,
  onSend,
  onStop,
}: ComposerProps) {
  const [notice, setNotice] = useState('');

  const announce = (message: string) => setNotice(message);
  const handleSubmit = () => {
    if (running) {
      onStop?.();
      return;
    }

    onSend?.();
  };

  return (
    <footer className={styles.composer}>
      <div className={styles.statusRow}>
        <button
          className={styles.toolPill}
          type="button"
          aria-expanded="false"
          onClick={() => announce('工具选择和切换在真实接入后可用，当前为 mock 阶段。')}
        >
          Tools（{toolsUsed}/{toolsTotal}）
          <ChevronDown aria-hidden="true" />
        </button>
        <button
          className={styles.agentPill}
          type="button"
          aria-expanded="false"
          onClick={() => announce('Agent 选择和切换在真实接入后可用，当前为 mock 阶段。')}
        >
          Agents（{agentsUsed}/{agentsTotal}）
          <ChevronDown aria-hidden="true" />
        </button>
        {notice ? (
          <span className={styles.notice} role="status">
            {notice}
          </span>
        ) : null}
      </div>
      <div className={styles.inputRow}>
        <Button
          aria-label="上传附件"
          className={styles.iconButton}
          variant="ghost"
          size="icon"
          disabled={disabled}
          onClick={() => announce('附件上传在真实接入 MinIO 后可用，当前为 mock 阶段。')}
        >
          <Paperclip />
        </Button>
        <Input
          className={styles.input}
          disabled={disabled}
          placeholder={placeholder}
          value={value}
          onChange={(event) => onChange?.(event.target.value)}
          onKeyDown={(event) => {
            if (event.key === 'Enter' && !event.shiftKey) {
              event.preventDefault();
              handleSubmit();
            }
          }}
        />
        <Button
          aria-label={running ? '停止生成' : '发送消息'}
          className={styles.iconButton}
          disabled={!running && disabled}
          variant={running ? 'destructive' : 'default'}
          size="icon"
          onClick={handleSubmit}
        >
          {running ? <Square /> : <Send />}
        </Button>
      </div>
    </footer>
  );
}
