import { TextField } from './TextField';

export interface DateRangeValue {
  /** yyyy-mm-dd, as a native date input gives it. Empty string means unset. */
  from: string;
  to: string;
}

export interface DateRangeFieldsProps {
  value: DateRangeValue;
  onChange: (value: DateRangeValue) => void;
  label?: string;
}

/**
 * A from/to pair of native date inputs.
 * <p>
 * Native inputs rather than a calendar widget, for the same reason
 * SelectField uses a native select: the OS date picker is the control
 * people already know, it is accessible for free, and it costs one
 * dependency less. A bespoke range calendar would be a better fit for
 * booking a hotel; this is narrowing a list.
 * <p>
 * `max` and `min` keep the pair honest - you cannot set a "from" after the
 * "to", which would silently produce an empty list and look like lost
 * history.
 */
export function DateRangeFields({ value, onChange, label = 'Date range' }: DateRangeFieldsProps) {
  return (
    <div>
      <span className="mb-1.5 block text-sm font-medium text-text-primary">{label}</span>
      <div className="flex items-center gap-2">
        <div className="min-w-0 flex-1">
          <TextField
            type="date"
            aria-label="From date"
            value={value.from}
            max={value.to || undefined}
            onChange={(e) => onChange({ ...value, from: e.target.value })}
          />
        </div>
        <span className="shrink-0 text-sm text-text-secondary">to</span>
        <div className="min-w-0 flex-1">
          <TextField
            type="date"
            aria-label="To date"
            value={value.to}
            min={value.from || undefined}
            onChange={(e) => onChange({ ...value, to: e.target.value })}
          />
        </div>
      </div>
    </div>
  );
}

/** Start of the given local day, as the ISO instant the API expects. */
export function startOfDayIso(date: string): string | undefined {
  if (!date) return undefined;
  return new Date(`${date}T00:00:00`).toISOString();
}

/**
 * End of the given local day. Inclusive on purpose: someone picking
 * "to: today" means through the end of today, not up to midnight this
 * morning, and the off-by-one there hides a whole day's trips.
 */
export function endOfDayIso(date: string): string | undefined {
  if (!date) return undefined;
  return new Date(`${date}T23:59:59.999`).toISOString();
}
