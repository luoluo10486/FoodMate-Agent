import { Alert } from '@arco-design/web-react';

type ErrorStateProps = {
  message: string;
};

export function ErrorState({ message }: ErrorStateProps) {
  return <Alert type="error" title="任务执行失败" content={message} />;
}
