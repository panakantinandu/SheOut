import { Bike, Car, Truck } from 'lucide-react';
import { TextField, vehicleLabel } from '@sheout/design-system';
import type { VehicleType } from '../api/types';
import { useTranslation } from '@sheout/design-system';

export const VEHICLE_OPTIONS: { key: VehicleType; label: string; icon: JSX.Element }[] = [
  { key: 'BIKE', label: 'Bike', icon: <Bike className="h-4 w-4" /> },
  { key: 'AUTO', label: 'Auto', icon: <Truck className="h-4 w-4" /> },
  { key: 'CAB', label: 'Cab', icon: <Car className="h-4 w-4" /> },
];

/** Vehicle type and registration - asked on first-run completion and on the profile edit form alike. */
export function VehicleFields({
  vehicleType,
  registration,
  onChange,
  showErrors,
}: {
  vehicleType: VehicleType;
  registration: string;
  onChange: (next: { vehicleType: VehicleType; registration: string }) => void;
  showErrors: boolean;
}) {
  const { t } = useTranslation();
  return (
    <>
      <div>
        <span className="mb-1.5 block text-sm font-medium text-text-primary">{t('vehicle.type')}</span>
        <div className="flex gap-2">
          {VEHICLE_OPTIONS.map((opt) => (
            <button
              key={opt.key}
              type="button"
              onClick={() => onChange({ vehicleType: opt.key, registration })}
              className={
                vehicleType === opt.key
                  ? 'flex flex-1 items-center justify-center gap-1.5 rounded-full bg-primary py-2 text-sm font-semibold text-text-inverse'
                  : 'flex flex-1 items-center justify-center gap-1.5 rounded-full border border-border py-2 text-sm font-medium text-text-secondary'
              }
            >
              {opt.icon} {vehicleLabel(opt.key)}
            </button>
          ))}
        </div>
      </div>
      <TextField
        label={t('vehicle.registration')}
        placeholder={t('vehicle.registrationPlaceholder')}
        value={registration}
        onChange={(e) => onChange({ vehicleType, registration: e.target.value })}
        error={showErrors && !registration.trim() ? t('vehicle.registrationRequired') : undefined}
      />
    </>
  );
}
