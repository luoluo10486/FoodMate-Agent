import { Badge } from '../ui/badge';
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
          <Badge variant="default">{group.name}</Badge>
          <span>{group.items.join('、')}</span>
        </div>
      ))}
      <p>{estimate}</p>
    </div>
  );
}
