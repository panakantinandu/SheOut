import { Phone } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button, TextField } from '@sheout/design-system';
import { ApiError, authApi } from '../api/client';
import { useAuth } from '../auth/AuthContext';

const PHONE_REGEX = /^\+[1-9]\d{7,14}$/;

/**
 * FLAGGED DEVIATION FROM THE MOCKUP: the mockup shows phone number +
 * password fields. The backend (auth module) has no password/credential
 * concept at all - only phone + OTP. Rather than build a password field
 * that does nothing real, this is a two-step phone -> OTP flow, wired to
 * the actual live endpoints. "Continue with Google" is left as the
 * disabled placeholder the brief asked for. There's no separate Sign Up
 * screen/endpoint either - verifying an OTP for a new number creates the
 * account, so "Sign Up" below is descriptive text, not a dead link.
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
      <img src="/Logo.jpeg" alt="SheOut" className="mx-auto mb-6 h-16 w-16 rounded-card object-cover shadow-card" />
      <h1 className="text-center font-heading text-2xl font-bold text-text-primary">Welcome Back!</h1>
      <p className="mb-8 text-center text-sm text-text-secondary">Sign in to continue</p>

      {step === 'phone' ? (
        <form onSubmit={handleSendOtp} className="space-y-4">
          <TextField
            icon={<Phone className="h-4 w-4 text-text-secondary" />}
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

      <Button variant="secondary" fullWidth disabled title="Not implemented yet">
        Continue with Google
      </Button>

      <p className="mt-6 text-center text-sm text-text-secondary">
        Don't have an account? <span className="font-semibold text-primary">Sign Up</span> - entering a new number
        above creates one automatically.
      </p>
    </div>
  );
}
