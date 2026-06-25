import styles from './BrandLogo.module.css';

type BrandLogoProps = {
  size?: 'small' | 'hero';
  showWordmark?: boolean;
  showTagline?: boolean;
};

export function FoodMateMark({ className }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 88 88" role="img" aria-label="FoodMate 图标">
      <defs>
        <linearGradient id="fm-bowl-mark" x1="21" y1="64" x2="68" y2="83" gradientUnits="userSpaceOnUse">
          <stop stopColor="var(--fm-green)" />
          <stop offset="1" stopColor="#245940" />
        </linearGradient>
        <linearGradient id="fm-leaf-mark" x1="30" y1="18" x2="72" y2="55" gradientUnits="userSpaceOnUse">
          <stop stopColor="#f3a348" />
          <stop offset="1" stopColor="var(--fm-orange)" />
        </linearGradient>
      </defs>
      <circle cx="44" cy="34" r="31" fill="var(--fm-orange-soft)" />
      <path
        d="M24 52.8C37.5 56.6 54.5 54.1 65.5 44.7C74.2 37.3 77 26.9 72.7 18.3C66 28.7 52.4 33.3 39 29.2C42 36.9 37 47.6 24 52.8Z"
        fill="url(#fm-leaf-mark)"
      />
      <path d="M39 29.2C50.9 31.9 62.4 27.4 68.5 18.7C61.5 15.9 52.1 17.1 44.4 22.9C42.2 24.5 40.4 26.6 39 29.2Z" fill="#f8c36e" />
      <path d="M22 59H66C65.2 73.1 57.8 81 44 81C30.2 81 22.8 73.1 22 59Z" fill="url(#fm-bowl-mark)" />
      <path d="M22 59H66C66 64.5 61.5 68 44 68C26.5 68 22 64.5 22 59Z" fill="#367b58" />
      <path d="M31 61.5C37.8 64 50.2 64 57 61.5" fill="none" stroke="#cfe9d7" strokeWidth="3" strokeLinecap="round" opacity=".65" />
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
