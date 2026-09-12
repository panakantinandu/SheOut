import { MapPinOff } from 'lucide-react';
import { Card, IconCircle } from '@sheout/design-system';
import type { GeoAddress } from '../api/types';
import { OUT_OF_AREA_MESSAGE, isInServiceArea } from '../lib/geocode';

export interface ServiceAreaNoticeProps {
  pickup: GeoAddress | null;
  drop: GeoAddress | null;
}

/**
 * Says which end of the trip is out of range, on the booking screen itself.
 * <p>
 * The picker already refuses to hand back an out-of-area place, so reaching
 * this needs a location that arrived some other way - a pin set before the
 * boundary moved, or a device position. It is here because the rule can
 * change under a customer and the screen should still explain itself rather
 * than just greying out the button.
 * <p>
 * Shared by both booking screens. Same reason LocationRow is shared: two
 * copies of this would eventually say two different things.
 */
export function ServiceAreaNotice({ pickup, drop }: ServiceAreaNoticeProps) {
  const badPickup = pickup !== null && !isInServiceArea(pickup);
  const badDrop = drop !== null && !isInServiceArea(drop);
  if (!badPickup && !badDrop) return null;

  const which = badPickup && badDrop ? 'Your pickup and drop are' : badPickup ? 'Your pickup is' : 'Your drop is';

  return (
    <Card tone="danger" className="flex items-start gap-3">
      <IconCircle color="red" tone="soft" size="sm" icon={<MapPinOff />} />
      <div className="min-w-0 flex-1">
        <p className="text-sm font-semibold text-text-primary">{which} outside our service area</p>
        <p className="mt-0.5 text-xs text-text-secondary">{OUT_OF_AREA_MESSAGE}</p>
      </div>
    </Card>
  );
}
