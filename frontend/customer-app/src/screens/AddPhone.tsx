import { useState, type FormEvent } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { BrandHeader, Button, Card, OtpCodeField, PhoneField, ResendCode, isCompletePhone, toE164, useOtpSender } from '@sheout/design-system';
import { useTranslation } from '@sheout/design-system';
import { Phone } from 'lucide-react';
import { ApiError, authApi, usersApi } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { markPhoneAdded } from '../auth/ProtectedRoute';

/**
 * Required once, straight after signing up with Google, before anything
 * else: a real phone number, proved by a code sent to it.
 * <p>
 * A Google account gives an email and nothing else, and every way SheOut
 * reaches a rider goes through her number - her partner calling from the
 * pickup, the SMS that goes to her contacts when she presses SOS, support
 * ringing her back. So a Google signup is not an account until it has one.
 * <p>
 * The same pieces as the sign-in screen - PhoneField, the code sender with
 * its resend countdown, the six code boxes - and the same code, store and
 * limits on the server. The one difference is what a right code does: it
 * gives this account the number instead of signing in.
 * <p>
 * There is no skip. The route guard sends every other screen here until the
 * number is on file, and the server refuses to book without one.
 */
export function AddPhone() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { accountId, logout } = useAuth();
  const notice = (useLocation().state as { notice?: string } | null)?.notice ?? null;

  const [step, setStep] = useState<'phone' | 'code'>('phone');
  const [phoneDigits, setPhoneDigits] = useState('');
  const [code, setCode] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [info, setInfo] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const phoneNumber = toE164(phoneDigits);
  const otp = useOtpSender((phone) => authApi.requestAddedPhone(phone));

  async function handleSend(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setInfo(null);
    if (!isCompletePhone(phoneDigits)) {
      setError(t('login.invalidPhone'));
      return;
    }
    setSubmitting(true);
    try {
      const outcome = await otp.sendCode(phoneNumber);
      if (outcome === 'reused') setInfo(t('otp.reused', { ns: 'ds', phone: phoneNumber }));
      setCode('');
      setStep('code');
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('login.sendError'));
    } finally {
      setSubmitting(false);
    }
  }

  async function handleResend() {
    setError(null);
    setInfo(null);
    setSubmitting(true);
    try {
      const outcome = await otp.sendCode(phoneNumber, { resend: true });
      setInfo(t(outcome === 'sent' ? 'otp.resent' : 'otp.reused', { ns: 'ds', phone: phoneNumber }));
      setCode('');
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('login.sendError'));
    } finally {
      setSubmitting(false);
    }
  }

  /** The argument matters: when the boxes complete themselves, state is one digit behind. */
  async function submitCode(entered: string = code) {
    setError(null);
    setSubmitting(true);
    try {
      await authApi.verifyAddedPhone(phoneNumber, entered);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('login.verifyError'));
      setSubmitting(false);
      return;
    }
    // Where next: the profile step if it is not done yet, otherwise in.
    let profileComplete = false;
    try {
      profileComplete = (await usersApi.getMyProfile()).profileComplete;
    } catch {
      // Unknown - the profile step checks for itself.
    }
    if (accountId) markPhoneAdded(accountId, profileComplete);
    navigate(profileComplete ? '/home' : '/complete-profile', { replace: true });
  }

  return (
    <div className="space-y-6 py-4">
      <BrandHeader size="md" />
      <div>
        <h1 className="font-heading text-2xl font-bold text-text-primary">{t('addPhone.title')}</h1>
        <p className="mt-1 text-sm text-text-secondary">{t('addPhone.subtitle')}</p>
        {notice && (
          <p className="mt-3 rounded-input bg-primary-light px-4 py-3 text-sm font-medium text-primary" data-testid="new-account-notice">
            {notice}
          </p>
        )}
      </div>

      <Card className="space-y-4 p-5">
        <div className="flex items-start gap-3 rounded-input bg-primary-light px-4 py-3 text-sm text-primary">
          <Phone className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
          <span>{t('addPhone.why')}</span>
        </div>

        {info && <p className="text-sm font-medium text-primary">{info}</p>}

        {step === 'phone' ? (
          <form onSubmit={handleSend} className="space-y-4" data-testid="add-phone-form">
            <PhoneField value={phoneDigits} onChange={setPhoneDigits} error={error ?? undefined} placeholder={t('login.phonePlaceholder')} />
            <Button type="submit" fullWidth disabled={submitting}>
              {submitting ? t('common.sending') : t('login.sendOtp')}
            </Button>
          </form>
        ) : (
          <form
            onSubmit={(e) => {
              e.preventDefault();
              void submitCode();
            }}
            className="space-y-4"
            data-testid="add-phone-code"
          >
            <p className="text-center text-sm text-text-secondary">{t('login.codeSentTo', { phone: phoneNumber })}</p>
            <OtpCodeField
              value={code}
              onChange={(next) => {
                setCode(next);
                setError(null);
              }}
              error={error ?? undefined}
              disabled={submitting}
              onComplete={(entered) => void submitCode(entered)}
            />
            <Button type="submit" fullWidth disabled={submitting}>
              {submitting ? t('login.verifying') : t('addPhone.verify')}
            </Button>
            <ResendCode secondsUntilResend={otp.secondsUntilResend} busy={submitting} onResend={handleResend} />
            <button
              type="button"
              onClick={() => {
                setStep('phone');
                setError(null);
                setInfo(null);
              }}
              className="w-full text-center text-sm text-text-secondary underline"
            >
              {t('login.changeNumber')}
            </button>
          </form>
        )}
      </Card>

      {/* The only way past this screen other than a verified number: leaving.
          Signed in with the wrong Google account is a real case. */}
      <button
        type="button"
        onClick={() => {
          logout();
          navigate('/login', { replace: true });
        }}
        className="w-full text-center text-sm text-text-secondary underline"
        data-testid="add-phone-sign-out"
      >
        {t('addPhone.signOut')}
      </button>
    </div>
  );
}
