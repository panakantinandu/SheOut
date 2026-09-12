import type { ReactNode } from 'react';

/**
 * The frame for every screen pushed on top of the tab bar rather than
 * living in it - drill-downs from Profile, the booking flow, tracking, the
 * legal documents.
 * <p>
 * These had no frame at all. AppShell gave the four tab screens
 * `max-w-md px-screen pt-6`, and everything routed without it rendered
 * edge to edge: About, Help, Personal Details, Saved Addresses, Payment
 * History, Verification and Notifications all had cards touching both
 * sides of the display while Home and Profile sat in a gutter. On a phone
 * it reads as two different apps depending on which screen you are on, and
 * on a wide window the unframed ones stretched the full width.
 * <p>
 * Same values as AppShell, minus the bottom padding it needs to clear the
 * tab bar these screens do not have.
 */
export function PageShell({ children }: { children: ReactNode }) {
  return (
    <div className="mx-auto min-h-screen max-w-md bg-background px-screen pb-12 pt-6">{children}</div>
  );
}
