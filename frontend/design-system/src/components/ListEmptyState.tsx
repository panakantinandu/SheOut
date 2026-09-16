import type { ReactNode } from 'react';
import brandIllustration from '../assets/sheout-illustration.png';
import { Button } from './Button';
import { Card } from './Card';
import { IconCircle } from './IconCircle';

export interface ListEmptyStateProps {
  icon: ReactNode;
  title: string;
  message: string;
  /** Offered only for the filtered-empty case, where there is something to undo. */
  action?: { label: string; onClick: () => void };
  /**
   * Draw the brand illustration above the words, with the icon as a badge on
   * it.
   * <p>
   * For the "you have not done this yet" empties - a first-time rider with no
   * trips, an inbox with nothing in it. A small picture makes an empty screen
   * read as a beginning rather than as something that failed to load.
   * <p>
   * Off for the filtered-empty case. Somebody who has narrowed a list of two
   * hundred trips down to none is not looking at a friendly beginning, she is
   * looking for her trips, and a cheerful illustration in front of that is
   * noise between her and the filter she needs to widen.
   */
  illustrated?: boolean;
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
export function ListEmptyState({ icon, title, message, action, illustrated = false }: ListEmptyStateProps) {
  return (
    <Card className="flex flex-col items-center gap-3 py-8 text-center">
      {illustrated ? (
        // The existing brand illustration, not a new drawing: one asset both
        // apps already download, tinted circle behind it, and the section's
        // own icon as a badge so an empty inbox still reads as an inbox.
        <span className="relative inline-flex">
          <span className="flex h-24 w-24 items-center justify-center rounded-full bg-primary-light">
            <img src={brandIllustration} alt="" aria-hidden="true" className="h-16 w-16 object-contain" />
          </span>
          <IconCircle
            size="sm"
            tone="solid"
            icon={icon}
            className="absolute -bottom-0.5 -right-0.5 ring-4 ring-surface"
          />
        </span>
      ) : (
        <IconCircle size="lg" tone="soft" icon={icon} />
      )}
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
