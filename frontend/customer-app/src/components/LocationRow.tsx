import { MapPinned } from 'lucide-react';
import type { ReactNode } from 'react';
import { ListRow } from '@sheout/design-system';

export interface LocationRowProps {
  icon: ReactNode;
  label: string;
  sublabel: string;
  /** Tapping the row itself - opens the picker on its search tab. */
  onSearch: () => void;
  /** The map button beside the row - opens the picker straight on the map. */
  onMap: () => void;
}

/**
 * One pickup or drop field: tap the row to search for an address, or the
 * map button beside it to go straight to dropping a pin.
 * <p>
 * The map button sits outside ListRow rather than in its rightSlot. ListRow
 * renders as a button once it has an onClick, and a button inside a button
 * is invalid markup that browsers resolve by dropping the inner one - so
 * the shortcut would simply not work on some of them.
 * <p>
 * Shared by both booking screens so the two fields cannot drift apart, the
 * way the phone inputs did.
 */
export function LocationRow({ icon, label, sublabel, onSearch, onMap }: LocationRowProps) {
  return (
    <div className="flex items-center gap-2 p-4">
      <ListRow className="flex-1" icon={icon} label={label} sublabel={sublabel} onClick={onSearch} />
      <button
        type="button"
        aria-label={`Pick ${label.toLowerCase()} on the map`}
        onClick={onMap}
        className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full border border-border text-text-secondary hover:bg-background"
      >
        <MapPinned className="h-4 w-4" />
      </button>
    </div>
  );
}
