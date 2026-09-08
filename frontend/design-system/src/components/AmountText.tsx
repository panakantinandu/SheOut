import { cn } from '../lib/cn';

export type AmountSign = 'positive' | 'negative' | 'neutral';
export type AmountSize = 'sm' | 'md' | 'lg';

export interface AmountTextProps {
  /** Always a positive magnitude - `sign` controls the +/- prefix and color, not the input's own sign. */
  amount: number;
  sign?: AmountSign;
  size?: AmountSize;
  className?: string;
}

const signClasses: Record<AmountSign, string> = {
  positive: 'text-accent-green',
  negative: 'text-danger',
  neutral: 'text-text-primary',
};

const sizeClasses: Record<AmountSize, string> = {
  sm: 'text-sm',
  md: 'text-base',
  lg: 'text-2xl',
};

const prefix: Record<AmountSign, string> = {
  positive: '+',
  negative: '-',
  neutral: '',
};

const formatter = new Intl.NumberFormat('en-IN', { maximumFractionDigits: 0 });

/** Formatted ₹ amount - e.g. "+ ₹1,250" (wallet credit) or "- ₹56" (debit). */
export function AmountText({ amount, sign = 'neutral', size = 'md', className }: AmountTextProps) {
  return (
    <span className={cn('font-heading font-semibold tabular-nums', signClasses[sign], sizeClasses[size], className)}>
      {prefix[sign]} ₹{formatter.format(Math.abs(amount))}
    </span>
  );
}
