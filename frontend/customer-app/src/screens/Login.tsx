import { Bike, Phone } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button, IconCircle, TextField } from '@sheout/design-system';
import { ApiError, authApi } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { mockAction } from '../lib/mockAction';

const PHONE_REGEX = /^\+[1-9]\d{7,14}$/;

/**
 * Official Google "G" logomark (4-color, standard OAuth-button asset) -
 * inlined rather than pulled from an icon font, since lucide-react has no
 * real multi-color Google glyph. Local to this file - nothing else needs it.
 */
function GoogleIcon({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 24 24" className={className} aria-hidden="true">
      <path fill="#4285F4" d="M23.52 12.27c0-.79-.07-1.54-.19-2.27H12v4.51h6.44c-.29 1.48-1.14 2.73-2.4 3.58v3h3.86c2.26-2.08 3.62-5.15 3.62-8.82z" />
      <path fill="#34A853" d="M12 24c3.24 0 5.95-1.08 7.93-2.91l-3.86-3c-1.07.72-2.45 1.15-4.07 1.15-3.13 0-5.78-2.11-6.73-4.96H1.29v3.09C3.26 21.3 7.31 24 12 24z" />
      <path fill="#FBBC05" d="M5.27 14.28A7.2 7.2 0 0 1 4.86 12c0-.79.14-1.56.38-2.28V6.63H1.29A11.98 11.98 0 0 0 0 12c0 1.94.46 3.77 1.29 5.37l3.98-3.09z" />
      <path fill="#EA4335" d="M12 4.75c1.77 0 3.35.61 4.6 1.8l3.42-3.42C17.95 1.19 15.24 0 12 0 7.31 0 3.26 2.7 1.29 6.63l3.98 3.09C6.22 6.86 8.87 4.75 12 4.75z" />
    </svg>
  );
}

/**
 * FLAGGED DEVIATION FROM THE MOCKUP: the mockup shows phone number +
 * password fields. The backend (auth module) has no password/credential
 * concept at all - only phone + OTP. Rather than build a password field
 * that does nothing real, this is a two-step phone -> OTP flow, wired to
 * the actual live endpoints. There's no separate Sign Up screen/endpoint
 * either - verifying an OTP for a new number creates the account, so
 * "Sign Up" below is descriptive text, not a dead link.
 * <p>
 * "Continue with Google" is still non-functional (no Google OAuth wired
 * up) - now uses mockAction on click instead of the disabled HTML
 * attribute, so it reads as "coming soon" (full-opacity, real icon) rather
 * than a greyed-out dead button, per the same convention used elsewhere
 * in this app for unbuilt actions.
 * <p>
 * FLAGGED FOR VISUAL DOUBLE-CHECK: the mockup's bike/scooter graphic is a
 * custom illustration (woman on a scooter with a location pin) - no such
 * SVG asset exists in this project, so it's approximated here with
 * lucide's Bike icon in a primary IconCircle, the same stand-in already
 * used for "Bike Taxi" elsewhere in this app. Heading/subtitle sizes and
 * the gap between them are eyeballed from the mockup image, not measured -
 * worth a pixel check once this is live. The tagline's italic now uses an
 * actual italic Inter font file (added to index.html's Google Fonts
 * request), not browser-synthesized oblique.
 */
export function Login() {
  const navigate = useNavigate();
  const { login } = useAuth();

  const [step, setStep] = useState<'phone' | 'otp'>('phone');
  const [phoneDigits, setPhoneDigits] = useState('');
  const [code, setCode] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const phoneNumber = `+91${phoneDigits}`;

  async function handleSendOtp(e: FormEvent) {
    e.preventDefault();
    setError(null);
    if (!PHONE_REGEX.test(phoneNumber)) {
      setError('Enter a valid 10-digit mobile number');
      return;
    }
    setSubmitting(true);
    try {
      await authApi.requestOtp(phoneNumber);
      setStep('otp');
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not send OTP - is the backend running?');
    } finally {
      setSubmitting(false);
    }
  }

  async function handleVerifyOtp(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      const session = await authApi.verifyOtp(phoneNumber, code);
      login(session);
      navigate('/home', { replace: true });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not verify code');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="flex min-h-screen flex-col justify-center bg-background px-screen py-10">
      <div className="mx-auto mb-6 flex flex-col items-center gap-2">
        <IconCircle size="lg" icon={<Bike className="h-7 w-7" />} />
        <p className="font-heading text-3xl font-extrabold tracking-tight text-text-primary">
          SHE<span className="text-accent-orange">O</span>UT
        </p>
        <p className="text-sm italic text-text-secondary">Your Delivery, Our Priority</p>
      </div>

      <h1 className="text-center font-heading text-2xl font-bold text-text-primary">Welcome Back!</h1>
      <p className="mb-6 text-center text-sm text-text-secondary">Sign in to continue</p>

      {step === 'phone' ? (
        <form onSubmit={handleSendOtp} className="space-y-4">
          <TextField
            icon={
              <span className="flex items-center gap-2 text-text-secondary">
                <Phone className="h-4 w-4" />
                <span className="h-4 w-px bg-border" />
                <span className="text-sm font-medium text-text-primary">+91</span>
              </span>
            }
            type="tel"
            inputMode="numeric"
            placeholder="Mobile Number"
            value={phoneDigits}
            onChange={(e) => setPhoneDigits(e.target.value.replace(/\D/g, '').slice(0, 10))}
            error={error ?? undefined}
          />
          <Button type="submit" fullWidth disabled={submitting}>
            {submitting ? 'Sending...' : 'Send OTP'}
          </Button>
        </form>
      ) : (
        <form onSubmit={handleVerifyOtp} className="space-y-4">
          <p className="text-center text-sm text-text-secondary">Enter the code sent to {phoneNumber}</p>
          <TextField
            type="text"
            inputMode="numeric"
            placeholder="6-digit code"
            value={code}
            onChange={(e) => setCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
            error={error ?? undefined}
          />
          <Button type="submit" fullWidth disabled={submitting}>
            {submitting ? 'Verifying...' : 'Login'}
          </Button>
          <button
            type="button"
            onClick={() => setStep('phone')}
            className="w-full text-center text-sm text-text-secondary underline"
          >
            Change number
          </button>
        </form>
      )}

      <div className="my-6 text-center text-xs font-medium text-text-secondary">or</div>

      <Button
        type="button"
        variant="secondary"
        fullWidth
        icon={<GoogleIcon className="h-4 w-4" />}
        title="Not implemented yet"
        onClick={() => mockAction('Continue with Google', 'Google OAuth not implemented yet')}
      >
        Continue with Google
      </Button>

      <p className="mt-6 text-center text-sm text-text-secondary">
        Don't have an account? <span className="font-semibold text-primary">Sign Up</span> - entering a new number
        above creates one automatically.
      </p>
    </div>
  );
}
