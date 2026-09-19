import { Headphones } from 'lucide-react';
import { Button } from './Button';
import { useTranslation } from 'react-i18next';

export interface ContactSupportButtonProps {
  /** Comes from the backend, which reads SUPPORT_PHONE_NUMBER. Null or blank renders nothing. */
  phoneNumber: string | null | undefined;
  label?: string;
  className?: string;
}

/**
 * Calls a real person at SheOut. Never the other party to the trip.
 * <p>
 * This is the whole replacement for direct rider-to-partner calling. There
 * is no version of this product where one of them gets the other's number:
 * once it is out it cannot be recalled, and it outlives the trip it was
 * given for. Anything that genuinely needs a voice goes through somebody
 * here, who can hear both sides and act.
 * <p>
 * The number is served by the backend rather than compiled into either app.
 * It used to be hardcoded separately in each, with two different values, so
 * support answered on one number from the rider's app and a different one
 * from the partner's, and changing either meant shipping a frontend.
 * <p>
 * Renders nothing when no number is configured. A support button that dials
 * an empty string is worse than no button - it looks like a way out and is
 * not one.
 */
export function ContactSupportButton({
  phoneNumber,
  label: labelProp,
  className,
}: ContactSupportButtonProps) {
  const { t } = useTranslation('ds');
  const label = labelProp ?? t('support.contact');
  const trimmed = phoneNumber?.trim();
  if (!trimmed) return null;

  return (
    <Button
      variant="secondary"
      fullWidth
      size="md"
      className={className}
      icon={<Headphones className="h-4 w-4" />}
      onClick={() => {
        window.location.href = `tel:${trimmed}`;
      }}
    >
      {label}
    </Button>
  );
}
