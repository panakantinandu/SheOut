import type { ReactNode } from 'react';
import { Button } from './Button';
import { Card } from './Card';
import { IconCircle } from './IconCircle';

export interface ListEmptyStateProps {
  icon: ReactNode;
  title: string;
  message: string;
  /** Offered only for the filtered-empty case, where there is something to undo. */
  action?: { label: string; onClick: () => void };
}

/**
 * An empty list, saying which kind of empty it is.
 * <p>
 * The two mean completely different things and must never share copy. "No
 * bookings yet" tells a new customer the app is working and they have not
 * booked; "No results match your filters" tells someone with ten years of
 * history that their history is intact and their filters are too narrow.
 * Showing the first message to the second person reads as data loss, and is
 * the kind of thing that makes someone stop trusting an app entirely.
 * <p>
 * Only the filtered case gets an action, because only it has something to
 * undo. Offering "clear filters" on a genuinely empty list would be a
 * button that changes nothing.
 */
export function ListEmptyState({ icon, title, message, action }: ListEmptyStateProps) {
  return (
    <Card className="flex flex-col items-center gap-3 py-8 text-center">
      <IconCircle size="lg" tone="soft" icon={icon} />
      <div>
        <p className="font-heading font-semibold text-text-primary">{title}</p>
        <p className="mt-1 text-sm text-text-secondary">{message}</p>
      </div>
      {action && (
        <Button variant="secondary" size="md" onClick={action.onClick}>
          {action.label}
        </Button>
      )}
    </Card>
  );
}
