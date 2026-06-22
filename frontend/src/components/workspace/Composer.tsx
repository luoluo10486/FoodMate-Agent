import { Button, Input } from '@arco-design/web-react';
import { IconAttachment, IconSend, IconStop } from '@arco-design/web-react/icon';
import styles from './Composer.module.css';

type ComposerProps = {
  placeholder?: string;
  running?: boolean;
  toolsUsed: number;
  toolsTotal: number;
  agentsUsed: number;
  agentsTotal: number;
};

export function Composer({
  placeholder = '让 FoodMate 计算、分析、记录或规划...',
  running = false,
  toolsUsed,
  toolsTotal,
  agentsUsed,
  agentsTotal
}: ComposerProps) {
  return (
    <footer className={styles.composer}>
      <div className={styles.statusRow}>
        <button className={styles.toolPill}>Tools（{toolsUsed}/{toolsTotal}）⌄</button>
        <button className={styles.agentPill}>Agents（{agentsUsed}/{agentsTotal}）⌄</button>
      </div>
      <div className={styles.inputRow}>
        <Button aria-label="上传附件" shape="circle" icon={<IconAttachment />} />
        <Input.TextArea autoSize={false} className={styles.input} placeholder={placeholder} />
        <Button
          aria-label={running ? '停止生成' : '发送消息'}
          shape="circle"
          type={running ? 'secondary' : 'primary'}
          status={running ? 'danger' : 'default'}
          icon={running ? <IconStop /> : <IconSend />}
        />
      </div>
    </footer>
  );
}
