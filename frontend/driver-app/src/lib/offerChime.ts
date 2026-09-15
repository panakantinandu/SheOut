/**
 * A short, unmistakable chime for a trip offer while the app is in front of
 * her. The system notification carries the alert when the app is in the
 * background; with the app open, browsers show that notification quietly or
 * not at all, so the app sounds its own.
 * <p>
 * Browsers only let a page play sound after the person has interacted with
 * it. Going online is that interaction: unlockChime runs on the Go Online
 * tap, and an offer can only arrive for a partner who has gone online.
 */
let audio: AudioContext | null = null;

export function unlockChime() {
  try {
    audio ??= new AudioContext();
    if (audio.state === 'suspended') void audio.resume();
  } catch {
    // No Web Audio: the notification's own sound still plays.
  }
}

export function playOfferChime() {
  if (!audio || audio.state !== 'running') return;
  const start = audio.currentTime;
  [0, 0.28, 0.56].forEach((offset, i) => {
    const tone = audio!.createOscillator();
    const volume = audio!.createGain();
    tone.frequency.value = i === 2 ? 1175 : 880;
    volume.gain.setValueAtTime(0.0001, start + offset);
    volume.gain.exponentialRampToValueAtTime(0.35, start + offset + 0.02);
    volume.gain.exponentialRampToValueAtTime(0.0001, start + offset + 0.24);
    tone.connect(volume).connect(audio!.destination);
    tone.start(start + offset);
    tone.stop(start + offset + 0.26);
  });
  // Android Chrome only; iOS has no web vibration API - see push-sw.js.
  navigator.vibrate?.([300, 120, 300]);
}
