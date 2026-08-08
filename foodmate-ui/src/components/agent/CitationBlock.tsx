import { Badge } from '@/components/ui/badge';
import type { Citation } from '../../types/agent';
import styles from './CitationBlock.module.css';

type CitationBlockProps = {
  citation: Citation;
};

export function CitationBlock({ citation }: CitationBlockProps) {
  return (
    <article className={styles.block}>
      <div className={styles.titleRow}>
        <strong>{citation.title}</strong>
        {citation.score ? <Badge variant="outline">{citation.score.toFixed(2)}</Badge> : null}
      </div>
      <p>{citation.snippet}</p>
      <span>{citation.source}</span>
    </article>
  );
}
