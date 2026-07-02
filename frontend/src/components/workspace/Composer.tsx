import { Button, Input } from '@arco-design/web-react';
import { IconAttachment, IconSend, IconStop } from '@arco-design/web-react/icon';
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
        <button className={styles.toolPill}>
          Tools（{toolsUsed}/{toolsTotal}）⌄
        </button>
        <button className={styles.agentPill}>
          Agents（{agentsUsed}/{agentsTotal}）⌄
        </button>
      </div>
      <div className={styles.inputRow}>
        <Button aria-label="上传附件" shape="circle" disabled={disabled} icon={<IconAttachment />} />
        <Input.TextArea
          autoSize={false}
          className={styles.input}
          disabled={disabled}
          placeholder={placeholder}
          value={value}
          onChange={onChange}
          onPressEnter={(event) => {
            if (!event.shiftKey) {
              event.preventDefault();
              handleSubmit();
            }
          }}
        />
        <Button
          aria-label={running ? '停止生成' : '发送消息'}
          disabled={!running && disabled}
          onClick={handleSubmit}
          shape="circle"
          type={running ? 'secondary' : 'primary'}
          status={running ? 'danger' : 'default'}
          icon={running ? <IconStop /> : <IconSend />}
        />
      </div>
    </footer>
  );
}
