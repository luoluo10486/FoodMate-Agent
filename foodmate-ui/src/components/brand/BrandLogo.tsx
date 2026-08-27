import styles from './BrandLogo.module.css';

type BrandLogoProps = {
  size?: 'small' | 'compact' | 'hero';
  showWordmark?: boolean;
  showTagline?: boolean;
  showMarkLetter?: boolean;
};

export function FoodMateMark({ className, showLetter = true }: { className?: string; showLetter?: boolean }) {
  return <span className={`${styles.mark} ${className ?? ''}`}>{showLetter ? 'F' : null}</span>;
}

export function BrandLogo({
  size = 'small',
  showWordmark = true,
  showTagline = false,
  showMarkLetter = true,
}: BrandLogoProps) {
  return (
    <div className={`${styles.brand} ${styles[size]}`}>
      <FoodMateMark className={styles.mark} showLetter={showMarkLetter} />
      {showWordmark ? (
        <div className={styles.copy}>
          <div className={styles.wordmark}>FoodMate</div>
          {showTagline ? <p>Agent 工作站</p> : null}
        </div>
      ) : null}
    </div>
  );
}
