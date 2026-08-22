import { Badge } from '@/components/ui/badge';
import type { Citation } from '../../types/agent';
import styles from './CitationBlock.module.css';

type CitationBlockProps = {
  citation: Citation;
};

export function CitationBlock({ citation }: CitationBlockProps) {
  return (
    <details className={styles.block}>
      <summary className={styles.titleRow}>
        <strong>{citation.title}</strong>
        {citation.score ? <Badge variant="outline">{citation.score.toFixed(2)}</Badge> : null}
      </summary>
      <div className={styles.details}>
        <p>{citation.snippet}</p>
        <span>{citation.source}</span>
      </div>
    </details>
  );
}
