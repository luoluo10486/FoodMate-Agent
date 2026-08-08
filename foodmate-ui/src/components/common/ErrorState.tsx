import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert';

type ErrorStateProps = {
  message: string;
};

export function ErrorState({ message }: ErrorStateProps) {
  return (
    <Alert variant="destructive">
      <AlertTitle>任务执行失败</AlertTitle>
      <AlertDescription>{message}</AlertDescription>
    </Alert>
  );
}
