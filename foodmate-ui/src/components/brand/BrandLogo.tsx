import styles from './BrandLogo.module.css';

type BrandLogoProps = {
  size?: 'small' | 'compact' | 'hero';
  showWordmark?: boolean;
  showTagline?: boolean;
};

export function FoodMateMark({ className }: { className?: string }) {
  return <span className={`${styles.mark} ${className ?? ''}`}>F</span>;
}

export function BrandLogo({ size = 'small', showWordmark = true, showTagline = false }: BrandLogoProps) {
  return (
    <div className={`${styles.brand} ${styles[size]}`}>
      <FoodMateMark className={styles.mark} />
      {showWordmark ? (
        <div className={styles.copy}>
          <div className={styles.wordmark}>FoodMate</div>
          {showTagline ? <p>Agent 工作站</p> : null}
        </div>
      ) : null}
    </div>
  );
}
