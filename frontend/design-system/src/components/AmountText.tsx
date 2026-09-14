import { cn } from '../lib/cn';

export type AmountSign = 'positive' | 'negative' | 'neutral';
export type AmountSize = 'sm' | 'md' | 'lg';

export interface AmountTextProps {
  /** Always a positive magnitude - `sign` controls the +/- prefix and color, not the input's own sign. */
  amount: number;
  sign?: AmountSign;
  size?: AmountSize;
  /**
   * 'inverse' for an amount sitting on a filled brand card, where the sign
   * colors are unreadable.
   * <p>
   * This exists because passing `className="text-text-inverse"` did not
   * work: cn() is plain clsx with no Tailwind-aware merging, so both color
   * utilities landed in the class list and stylesheet order decided the
   * winner. The customer wallet's balance was rendering near-black on dark
   * purple as a result, while driver Earnings had quietly worked around the
   * same thing with an `!important` modifier. One explicit prop, rather
   * than two screens each discovering this separately.
   */
  tone?: 'default' | 'inverse';
  /**
   * Show paise. Off by default, where a rounded figure reads better. On
   * wherever the number is what money actually moves by: a rider's card said
   * ₹190 while Razorpay Checkout, beside it, charged ₹189.67.
   */
  exact?: boolean;
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
const exactFormatter = new Intl.NumberFormat('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });

/** Formatted ₹ amount - e.g. "+ ₹1,250" (wallet credit) or "- ₹56" (debit). */
export function AmountText({ amount, sign = 'neutral', size = 'md', tone = 'default', exact = false, className }: AmountTextProps) {
  return (
    <span
      className={cn(
        'font-heading font-semibold tabular-nums',
        tone === 'inverse' ? 'text-text-inverse' : signClasses[sign],
        sizeClasses[size],
        className
      )}
    >
      {prefix[sign]} ₹{(exact ? exactFormatter : formatter).format(Math.abs(amount))}
    </span>
  );
}
