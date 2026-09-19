import { useEffect, useRef, useState } from 'react';

/** Matches the server's otp-ttl-seconds default: a code sent this recently can still be used. */
const CODE_VALID_MS = 5 * 60 * 1000;
/** Matches the server's otp-cooldown-seconds default; the server's own answer overrides it. */
const DEFAULT_COOLDOWN_SECONDS = 45;

export type OtpSendOutcome =
  /** A new code was sent. */
  | 'sent'
  /** No new code was asked for: the one sent moments ago to this number still works. */
  | 'reused';

interface ErrorLike {
  status?: number;
  body?: { error?: string; details?: string[] } | null;
}

/** The wait the server named in a refusal, if this is the "code sent moments ago" one. */
function cooldownSeconds(err: unknown): number | null {
  const e = err as ErrorLike;
  if (e?.status !== 429 || e.body?.error !== 'OTP_RESEND_COOLDOWN') return null;
  const detail = e.body.details?.find((d) => d.startsWith('retryAfterSeconds:'));
  const seconds = detail ? Number(detail.split(':')[1]) : NaN;
  return Number.isFinite(seconds) && seconds > 0 ? seconds : DEFAULT_COOLDOWN_SECONDS;
}

/**
 * Sending sign-in codes without ever stranding her on "please wait".
 * <p>
 * There was no way to ask for a second code except "Change number" and
 * submitting the same number again - which asked the server for a new code
 * inside its 45-second cooldown, and was refused with "Please wait before
 * asking for another code". The code she had just been sent was fine; the
 * screen had simply forgotten about it.
 * <p>
 * So: the same number again, while its code is still valid, goes straight
 * back to entering that code. A cooldown refusal means a code went out moments
 * ago - on another tab, or before a reload - so it too leads to the code
 * step rather than an error. A new code is asked for only through Resend,
 * which counts down the cooldown instead of letting her walk into it.
 */
export function useOtpSender(send: (phoneNumber: string) => Promise<void>) {
  const sentRef = useRef<{ phone: string; at: number } | null>(null);
  const [resendAt, setResendAt] = useState(0);
  const [now, setNow] = useState(() => Date.now());

  useEffect(() => {
    if (resendAt <= Date.now()) return;
    const timer = window.setInterval(() => {
      const t = Date.now();
      setNow(t);
      if (t >= resendAt) window.clearInterval(timer);
    }, 500);
    return () => window.clearInterval(timer);
  }, [resendAt]);

  const secondsUntilResend = Math.max(0, Math.ceil((resendAt - now) / 1000));

  async function sendCode(phoneNumber: string, { resend = false } = {}): Promise<OtpSendOutcome> {
    const last = sentRef.current;
    if (!resend && last && last.phone === phoneNumber && Date.now() - last.at < CODE_VALID_MS) {
      return 'reused';
    }
    try {
      await send(phoneNumber);
    } catch (err) {
      const wait = cooldownSeconds(err);
      if (wait == null) throw err;
      // A code went out to this number moments ago. It works - go and use it.
      setResendAt(Date.now() + wait * 1000);
      setNow(Date.now());
      if (last?.phone !== phoneNumber) sentRef.current = { phone: phoneNumber, at: Date.now() };
      return 'reused';
    }
    sentRef.current = { phone: phoneNumber, at: Date.now() };
    setResendAt(Date.now() + DEFAULT_COOLDOWN_SECONDS * 1000);
    setNow(Date.now());
    return 'sent';
  }

  return { sendCode, secondsUntilResend };
}
