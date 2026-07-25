import { Tag } from '@arco-design/web-react';
import styles from './ShoppingList.module.css';

export type ShoppingGroup = {
  name: string;
  items: string[];
};

type ShoppingListProps = {
  groups: ShoppingGroup[];
  estimate: string;
};

export function ShoppingList({ groups, estimate }: ShoppingListProps) {
  return (
    <div className={styles.list}>
      {groups.map((group) => (
        <div className={styles.group} key={group.name}>
          <Tag color="green">{group.name}</Tag>
          <span>{group.items.join('、')}</span>
        </div>
      ))}
      <p>{estimate}</p>
    </div>
  );
}
