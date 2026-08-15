import { useMemo, useState } from 'react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Skeleton } from '@/components/ui/skeleton';
import type { UiComponentState } from '../../types/ui';
import styles from './ClarificationCard.module.css';

export type ClarificationField = {
  key: string;
  label: string;
  placeholder: string;
  defaultValue?: string;
  quickOptions?: string[];
};

type ClarificationCardProps = {
  title?: string;
  fields?: ClarificationField[];
  options?: string[];
  presentation?: 'default' | 'figma-compact';
  question?: string;
  state?: UiComponentState;
  errorText?: string;
  onSelect?: (value: string) => void;
  onSubmit?: (values: Record<string, string>) => void;
  submitLabel?: string;
};

export function ClarificationCard({
  title = '为了让计划更可执行，我还需要 3 个信息',
  fields = [],
  options = ['预算 300 元以内', '不吃猪肉', '目标高蛋白'],
  presentation = 'default',
  question = '你的午餐具体包含哪些食物？',
  state = 'normal',
  errorText = '追问选项加载失败，请直接在输入框补充。',
  onSelect,
  onSubmit,
  submitLabel = '继续生成计划',
}: ClarificationCardProps) {
  const defaultValues = useMemo(() => {
    if (!fields.length) return {};
    return fields.reduce<Record<string, string>>((current, field) => {
      current[field.key] = field.defaultValue ?? '';
      return current;
    }, {});
  }, [fields]);
  const [values, setValues] = useState(defaultValues);
  const fieldsKey = fields.map((field) => field.key).join(',');
  const [prevFieldsKey, setPrevFieldsKey] = useState(fieldsKey);
  const [selectedOption, setSelectedOption] = useState(options[0]);

  if (fieldsKey !== prevFieldsKey) {
    setPrevFieldsKey(fieldsKey);
    setValues(defaultValues);
  }

  if (state === 'loading') {
    return (
      <Card className={`${styles.card} ${styles.loading}`}>
        <div className="grid gap-3">
          <Skeleton className="h-4 w-1/4" />
          <Skeleton className="h-5 w-3/4" />
          <Skeleton className="h-10 w-full" />
          <Skeleton className="h-10 w-32" />
        </div>
      </Card>
    );
  }

  if (presentation === 'figma-compact') {
    return (
      <Card aria-label={title} className={`${styles.card} ${styles.figmaCard}`}>
        <h3>{title}</h3>
        <div className={styles.figmaQuestion}>
          <strong>{question}</strong>
          <div className={styles.figmaOptions}>
            {options.map((option) => {
              const selected = selectedOption === option;
              return (
                <Button
                  aria-pressed={selected}
                  className={styles.figmaOption}
                  key={option}
                  type="button"
                  variant="ghost"
                  onClick={() => {
                    setSelectedOption(option);
                    onSelect?.(option);
                  }}
                >
                  <span
                    className={`${styles.figmaRadio} ${selected ? styles.figmaRadioSelected : ''}`}
                    aria-hidden="true"
                  />
                  <span>{option}</span>
                </Button>
              );
            })}
          </div>
        </div>
      </Card>
    );
  }

  const disabled = state === 'disabled' || state === 'error';

  return (
    <Card className={`${styles.card} ${styles[state]}`}>
      <Badge variant={state === 'error' ? 'destructive' : 'warning'}>
        {state === 'error' ? '追问失败' : '需要补充'}
      </Badge>
      <h3>{state === 'error' ? errorText : title}</h3>
      {fields.length ? (
        <div className={styles.form}>
          {fields.map((field) => (
            <div className={styles.field} key={field.key}>
              <div className={styles.fieldHead}>
                <strong>{field.label}</strong>
                {field.quickOptions?.length ? (
                  <div className={styles.options}>
                    {field.quickOptions.map((option) => (
                      <Button
                        variant="outline"
                        size="sm"
                        disabled={disabled}
                        key={option}
                        onClick={() => setValues((current) => ({ ...current, [field.key]: option }))}
                      >
                        {option}
                      </Button>
                    ))}
                  </div>
                ) : null}
              </div>
              <Input
                disabled={disabled}
                placeholder={field.placeholder}
                value={values[field.key] ?? ''}
                onChange={(event) => setValues((current) => ({ ...current, [field.key]: event.target.value }))}
              />
            </div>
          ))}
          <div className={styles.actions}>
            <Button
              disabled={disabled || fields.some((field) => !(values[field.key] ?? '').trim())}
              onClick={() => onSubmit?.(values)}
            >
              {submitLabel}
            </Button>
          </div>
        </div>
      ) : (
        <div className={styles.options}>
          {options.map((option) => (
            <Button variant="outline" disabled={disabled} onClick={() => onSelect?.(option)} key={option}>
              {option}
            </Button>
          ))}
        </div>
      )}
    </Card>
  );
}
