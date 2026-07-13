import { useMemo, useState } from 'react';
import { Button, Card, Input, Skeleton, Tag } from '@arco-design/web-react';
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
  state = 'normal',
  errorText = '追问选项加载失败，请直接在输入框补充。',
  onSelect,
  onSubmit,
  submitLabel = '继续生成计划',
}: ClarificationCardProps) {
  // Derive default values from fields; resets when fields identity changes
  const defaultValues = useMemo(() => {
    if (!fields.length) return {};
    return fields.reduce<Record<string, string>>(
      (current, field) => {
        current[field.key] = field.defaultValue ?? '';
        return current;
      },
      {} as Record<string, string>,
    );
  }, [fields]);
  const [values, setValues] = useState(defaultValues);

  // Sync internal state when fields definition changes
  const fieldsKey = fields.map((f) => f.key).join(',');
  const [prevFieldsKey, setPrevFieldsKey] = useState(fieldsKey);
  if (fieldsKey !== prevFieldsKey) {
    setPrevFieldsKey(fieldsKey);
    setValues(defaultValues);
  }

  if (state === 'loading') {
    return (
      <Card className={`${styles.card} ${styles.loading}`} bordered={false}>
        <Skeleton text={{ rows: 2 }} animation />
      </Card>
    );
  }

  return (
    <Card className={`${styles.card} ${styles[state]}`} bordered={false}>
      <Tag color={state === 'error' ? 'red' : 'orange'}>{state === 'error' ? '追问失败' : '需要补充'}</Tag>
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
                        disabled={state === 'disabled' || state === 'error'}
                        key={option}
                        size="mini"
                        onClick={() => {
                          setValues((current) => ({ ...current, [field.key]: option }));
                        }}
                      >
                        {option}
                      </Button>
                    ))}
                  </div>
                ) : null}
              </div>
              <Input
                disabled={state === 'disabled' || state === 'error'}
                placeholder={field.placeholder}
                value={values[field.key] ?? ''}
                onChange={(value) => {
                  setValues((current) => ({ ...current, [field.key]: value }));
                }}
              />
            </div>
          ))}
          <div className={styles.actions}>
            <Button
              disabled={
                state === 'disabled' || state === 'error' || fields.some((field) => !(values[field.key] ?? '').trim())
              }
              type="primary"
              onClick={() => onSubmit?.(values)}
            >
              {submitLabel}
            </Button>
          </div>
        </div>
      ) : (
        <div className={styles.options}>
          {options.map((option) => (
            <Button
              disabled={state === 'disabled' || state === 'error'}
              onClick={() => onSelect?.(option)}
              key={option}
            >
              {option}
            </Button>
          ))}
        </div>
      )}
    </Card>
  );
}
