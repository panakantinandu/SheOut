/**
 * A sentence a screen reader reads out when it changes, without moving focus
 * or showing anything: "Searching for a nearby partner", then "Your partner is
 * 3 minutes away", then "On the way to your drop". Polite, so it waits for the
 * reader to finish what it was saying.
 */
export function LiveAnnouncement({
  message,
  testId = 'live-announcement',
  urgent = false,
}: {
  message: string;
  testId?: string;
  /** Interrupts the reader - only for something that cannot wait, like a ride offer with a clock on it. */
  urgent?: boolean;
}) {
  return (
    <p
      role={urgent ? 'alert' : 'status'}
      aria-live={urgent ? 'assertive' : 'polite'}
      aria-atomic="true"
      className="sr-only"
      data-testid={testId}
    >
      {message}
    </p>
  );
}
