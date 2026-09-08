/**
 * Consistent feedback for a button that's visually real but has nothing
 * behind it yet (no backend module, no sub-screen built). An unwired
 * onClick with no feedback at all reads as a broken button when tapped -
 * this at least confirms the tap registered and says why nothing happened.
 */
export function mockAction(action: string, detail?: string) {
  window.alert(`${action} - not implemented yet${detail ? ` (${detail})` : ''}.`);
}
