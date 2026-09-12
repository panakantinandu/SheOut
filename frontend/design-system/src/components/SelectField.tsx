import { ChevronDown } from 'lucide-react';
import { forwardRef, type SelectHTMLAttributes } from 'react';
import { cn } from '../lib/cn';

export interface SelectOption {
  value: string;
  label: string;
}

export interface SelectFieldProps extends Omit<SelectHTMLAttributes<HTMLSelectElement>, 'children'> {
  label?: string;
  options: SelectOption[];
  /** Shown as the first option and meaning "no filter". Omit for a required choice. */
  placeholder?: string;
}

/**
 * A native select, styled to match TextField.
 * <p>
 * Native rather than a custom dropdown on purpose. On a phone the OS picker
 * is a better control than anything rebuilt in a div - it is searchable,
 * it does not fight the keyboard, and it is what every other app uses - and
 * it is keyboard and screen-reader accessible without any work. The only
 * thing it costs is control over the closed state's appearance, which is
 * what the wrapper below handles.
 */
export const SelectField = forwardRef<HTMLSelectElement, SelectFieldProps>(
  ({ label, options, placeholder, className, id, ...props }, ref) => {
    const selectId = id ?? props.name;
    return (
      <label className="block" htmlFor={selectId}>
        {label && <span className="mb-1.5 block text-sm font-medium text-text-primary">{label}</span>}
        <span className="relative flex items-center">
          <select
            ref={ref}
            id={selectId}
            className={cn(
              'h-11 w-full appearance-none rounded-input border border-border bg-surface pl-3 pr-9',
              'text-sm text-text-primary outline-none transition-colors focus:border-primary',
              className
            )}
            {...props}
          >
            {placeholder && <option value="">{placeholder}</option>}
            {options.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
          <ChevronDown
            className="pointer-events-none absolute right-3 h-4 w-4 text-text-secondary"
            aria-hidden="true"
          />
        </span>
      </label>
    );
  }
);
SelectField.displayName = 'SelectField';
