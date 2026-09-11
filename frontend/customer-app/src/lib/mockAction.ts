import { showToast } from '@sheout/design-system';

/**
 * Consistent feedback for a button that's visually real but has nothing
 * behind it yet (no backend module, no sub-screen built). An unwired
 * onClick with no feedback at all reads as a broken button when tapped -
 * this at least confirms the tap registered and says why nothing happened.
 * <p>
 * Uses an in-app toast rather than window.alert: an alert is modal, cannot
 * be styled, announces itself as coming from the page's host, and is
 * suppressed outright by some in-app browsers - which would leave the tap
 * with no feedback at all, the exact thing this exists to prevent.
 */
export function mockAction(action: string, detail?: string) {
  showToast(`${action} - not implemented yet${detail ? ` (${detail})` : ''}.`);
}
