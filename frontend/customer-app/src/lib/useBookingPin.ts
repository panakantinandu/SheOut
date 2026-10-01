import { useState } from 'react';
import type { GeoAddress } from '../api/types';
import type { PinMode } from '../components/BookingMapLayout';
import { handOffSheet } from './useGoBack';

type Field = 'pickup' | 'drop';

/**
 * Choosing pickup or drop on the booking screen's own map - see PinMode. The
 * same for every booking screen, so it lives here rather than in each.
 * <p>
 * A drop with nothing chosen yet starts at the pickup: where she is going is
 * far more often near where she is than at the middle of the city.
 */
export function useBookingPin({
  pickup,
  drop,
  setPickup,
  setDrop,
  openSearch,
}: {
  pickup: GeoAddress | null;
  drop: GeoAddress | null;
  setPickup: (a: GeoAddress) => void;
  setDrop: (a: GeoAddress) => void;
  openSearch: (field: Field) => void;
}) {
  const [pinFor, setPinFor] = useState<Field | null>(null);
  const [seed, setSeed] = useState<{ start: GeoAddress | null; areaHint: boolean }>({ start: null, areaHint: false });

  function startPin(field: Field, from: GeoAddress | null = null, areaHint = false) {
    setSeed({ start: from ?? (field === 'pickup' ? pickup : drop), areaHint });
    setPinFor(field);
  }

  const pin: PinMode | null = pinFor
    ? {
        field: pinFor,
        start: seed.start,
        areaHint: seed.areaHint,
        fallbackCentre: pinFor === 'drop' ? pickup : drop,
        onConfirm: (address) => {
          if (pinFor === 'pickup') setPickup(address);
          else setDrop(address);
          setPinFor(null);
        },
        onCancel: () => setPinFor(null),
        onSearch: () => {
          const field = pinFor;
          handOffSheet();
          setPinFor(null);
          openSearch(field);
        },
      }
    : null;

  return { pin, startPin };
}
