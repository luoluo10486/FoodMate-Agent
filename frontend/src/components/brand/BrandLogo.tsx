import styles from './BrandLogo.module.css';

type BrandLogoProps = {
  size?: 'small' | 'hero';
  showWordmark?: boolean;
  showTagline?: boolean;
};

export function FoodMateMark({ className }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 88 88" role="img" aria-label="FoodMate 图标">
      <circle cx="44" cy="32" r="32" fill="var(--fm-orange-soft)" />
      <path d="M25.5 53.4C50.2 59.1 75.1 48.1 78.2 27.3c1.5-10-4.6-16.2-13.1-18.5-8.5 13.9-25.5 15.4-37.8 8.5 6.2 9.2 7.7 18.5-1.8 36.1z" fill="var(--fm-orange)" />
      <rect x="18" y="60" width="52" height="22" rx="9" fill="var(--fm-green)" />
    </svg>
  );
}

export function BrandLogo({ size = 'small', showWordmark = true, showTagline = false }: BrandLogoProps) {
  return (
    <div className={`${styles.brand} ${styles[size]}`}>
      <FoodMateMark className={styles.mark} />
      {showWordmark ? (
        <div className={styles.copy}>
          <div className={styles.wordmark}>
            <span className={styles.food}>Food</span>
            <span className={styles.mate}>Mate</span>
          </div>
          {showTagline ? <p>记录、计算、分析和规划都从一个 Agent 工作台开始</p> : null}
        </div>
      ) : null}
    </div>
  );
}
