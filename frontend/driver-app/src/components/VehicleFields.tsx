import { Bike } from 'lucide-react';
import { TextField, vehicleLabel } from '@sheout/design-system';
import type { VehicleType } from '../api/types';
import { useTranslation } from '@sheout/design-system';
import { isPlate } from '../lib/documentRules';

/**
 * Vehicle and registration - asked on first-run completion and on the
 * profile edit form alike.
 * <p>
 * Two-wheelers only while SheOut starts out: the server takes partners on
 * BIKE alone (PARTNER_VEHICLE_TYPES), so there is no choice to offer - just
 * what she rides, and that autos and cabs come later. A partner already on
 * another type still sees hers, and can move to a two-wheeler.
 * <p>
 * The plate is checked as she types against the shapes Indian plates have
 * (the server checks again), so "TS09AB12345" is caught here, not by a
 * reviewer comparing it to her RC.
 */
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
  const options: VehicleType[] = vehicleType === 'BIKE' ? ['BIKE'] : ['BIKE', vehicleType];
  const plateWrong = registration.trim() !== '' && !isPlate(registration);
  return (
    <>
      <div>
        <span className="mb-2 block text-sm font-medium text-text-primary">{t('vehicle.type')}</span>
        <div className="flex gap-2">
          {options.map((key) => (
            <button
              key={key}
              type="button"
              onClick={() => onChange({ vehicleType: key, registration })}
              className={
                vehicleType === key
                  ? 'flex flex-1 items-center justify-center gap-2 rounded-full bg-primary py-2 text-sm font-semibold text-text-inverse'
                  : 'flex flex-1 items-center justify-center gap-2 rounded-full border border-border py-2 text-sm font-medium text-text-secondary'
              }
            >
              {key === 'BIKE' ? <Bike className="h-4 w-4" /> : null} {key === 'BIKE' ? t('vehicle.twoWheeler') : vehicleLabel(key)}
            </button>
          ))}
        </div>
        <p className="mt-2 text-caption text-text-secondary">{t('vehicle.twoWheelerOnly')}</p>
      </div>
      <TextField
        label={t('vehicle.registration')}
        placeholder={t('vehicle.registrationPlaceholder')}
        value={registration}
        maxLength={14}
        autoCapitalize="characters"
        autoComplete="off"
        spellCheck={false}
        onChange={(e) => onChange({ vehicleType, registration: e.target.value.toUpperCase() })}
        error={
          showErrors && !registration.trim()
            ? t('vehicle.registrationRequired')
            : plateWrong && (showErrors || registration.replace(/[\s-]/g, '').length >= 9)
              ? t('vehicle.registrationFormat')
              : undefined
        }
      />
    </>
  );
}
