export type PageState = 'empty' | 'loading' | 'running' | 'asking' | 'error' | 'complete';

export type UiComponentState = 'normal' | 'loading' | 'disabled' | 'error';

export type TaskCardData = {
  id: string;
  title: string;
  description: string;
  prompt: string;
  accent: 'green' | 'orange' | 'blue';
};
