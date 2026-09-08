import { Phone, User } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button, TextField } from '@sheout/design-system';
import { ApiError, authApi, usersApi } from '../api/client';
import type { AuthSession } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { signInWithGoogle } from '../lib/googleAuth';

const PHONE_REGEX = /^\+[1-9]\d{7,14}$/;
const GOOGLE_CLIENT_ID = import.meta.env.VITE_GOOGLE_CLIENT_ID;

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
 * concept at all - only phone + OTP, or Google Sign-In. There's still no
 * separate up-front Sign Up form - verifying an OTP (or a Google token)
 * for an identity with no account creates one; "Sign Up" below describes
 * that flow rather than linking to a separate form.
 * <p>
 * NEW ACCOUNT vs RETURNING USER: after either sign-in method succeeds,
 * this fetches the just-created/found profile and checks whether it has a
 * name yet - CustomerProfileApi requires one, and neither sign-in method
 * always has one (phone never does; Google does, but only when the
 * account is brand new - see afterSignIn). No name means the "Complete
 * your profile" step runs before Home; a name already present means this
 * is a returning user and skips straight to Home, unchanged from before.
 * If the profile fetch itself fails, this falls back to the session's
 * newAccount flag rather than stranding the user.
 * <p>
 * GOOGLE SIGN-IN: wired to real Google Identity Services (see
 * lib/googleAuth.ts) and the backend's /api/v1/auth/google/verify, which
 * verifies the ID token server-side before trusting anything in it. NOT
 * YET FUNCTIONAL IN PRODUCTION, though: no real Google Cloud OAuth Client
 * ID was given for this build (VITE_GOOGLE_CLIENT_ID is unset) - clicking
 * the button shows a clear "not configured yet" message instead of
 * attempting a broken flow. Set VITE_GOOGLE_CLIENT_ID (this app's Vercel
 * project) and GOOGLE_OAUTH_CLIENT_ID (the backend, on Render) to the same
 * real Client ID and this starts working with no further code changes.
 * ACCOUNT LINKING: see AuthService.verifyGoogleSignIn's Javadoc - phone
 * accounts never collect an email, so there's currently no realistic case
 * where a Google sign-in's email collides with an existing phone account;
 * building real cross-method linking is flagged there as a separate,
 * deliberately-not-built product decision.
 * <p>
 * FLAGGED FOR VISUAL DOUBLE-CHECK: heading/subtitle sizes and the gap
 * between them are eyeballed from the mockup image, not measured - worth
 * a pixel check once live. The Google prompt (GIS's "One Tap" surface,
 * triggered from this custom-styled button rather than Google's own
 * rendered button) can silently decline to appear at all in some
 * browsers/cookie settings - handled as an error rather than a silent
 * hang, but worth knowing this is a real GIS limitation, not a bug here.
 */
export function Login() {
  const navigate = useNavigate();
  const { login } = useAuth();

  const [step, setStep] = useState<'phone' | 'otp' | 'complete-profile'>('phone');
  const [phoneDigits, setPhoneDigits] = useState('');
  const [code, setCode] = useState('');
  const [name, setName] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [googleLoading, setGoogleLoading] = useState(false);

  const phoneNumber = `+91${phoneDigits}`;

  /** Shared by both sign-in methods - see the file header comment. */
  async function afterSignIn(session: AuthSession) {
    login(session);
    let needsProfile = session.newAccount;
    try {
      const profile = await usersApi.getMyProfile();
      needsProfile = !profile.name;
    } catch {
      // Profile fetch failed - fall back to the session's own signal rather than stranding the user here.
    }
    if (needsProfile) {
      setStep('complete-profile');
    } else {
      navigate('/home', { replace: true });
    }
  }

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
      await afterSignIn(session);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not verify code');
    } finally {
      setSubmitting(false);
    }
  }

  async function handleCompleteProfile(e: FormEvent) {
    e.preventDefault();
    setError(null);
    if (!name.trim()) {
      setError('Enter your name');
      return;
    }
    setSubmitting(true);
    try {
      await usersApi.updateMyProfile({ name: name.trim() });
      navigate('/home', { replace: true });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not save your name');
    } finally {
      setSubmitting(false);
    }
  }

  async function handleGoogleSignIn() {
    setError(null);
    if (!GOOGLE_CLIENT_ID) {
      setError('Google sign-in is not configured yet');
      return;
    }
    setGoogleLoading(true);
    try {
      const idToken = await signInWithGoogle(GOOGLE_CLIENT_ID);
      const session = await authApi.googleSignIn(idToken);
      await afterSignIn(session);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : err instanceof Error ? err.message : 'Google sign-in failed');
    } finally {
      setGoogleLoading(false);
    }
  }

  return (
    <div className="flex min-h-screen flex-col justify-center bg-background px-screen py-10">
      <div className="mx-auto mb-6 flex flex-col items-center gap-2">
        <img src="/Logo.jpeg" alt="SheOut" className="h-24 w-24 rounded-card object-cover shadow-card" />
        <p className="font-heading text-3xl font-extrabold tracking-tight text-text-primary">
          SHE<span className="text-accent-orange">O</span>UT
        </p>
        <p className="text-sm italic text-text-secondary">Your Delivery, Our Priority</p>
      </div>

      {step === 'complete-profile' ? (
        <>
          <h1 className="text-center font-heading text-2xl font-bold text-text-primary">Complete Your Profile</h1>
          <p className="mb-6 text-center text-sm text-text-secondary">Just your name, and you're in</p>
          <form onSubmit={handleCompleteProfile} className="space-y-4">
            <TextField
              icon={<User className="h-4 w-4 text-text-secondary" />}
              type="text"
              placeholder="Your Name"
              value={name}
              onChange={(e) => setName(e.target.value)}
              error={error ?? undefined}
            />
            <Button type="submit" fullWidth disabled={submitting}>
              {submitting ? 'Saving...' : 'Continue'}
            </Button>
          </form>
        </>
      ) : (
        <>
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

          {step === 'phone' && (
            <>
              <div className="my-6 text-center text-xs font-medium text-text-secondary">or</div>

              <Button
                type="button"
                variant="secondary"
                fullWidth
                icon={<GoogleIcon className="h-4 w-4" />}
                disabled={googleLoading}
                onClick={handleGoogleSignIn}
              >
                {googleLoading ? 'Signing in...' : 'Continue with Google'}
              </Button>

              <p className="mt-6 text-center text-sm text-text-secondary">
                Don't have an account? <span className="font-semibold text-primary">Sign Up</span> - enter a new
                number above, verify the OTP, then add your name to finish.
              </p>
            </>
          )}
        </>
      )}
    </div>
  );
}
