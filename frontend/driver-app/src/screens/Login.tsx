import { useState, type FormEvent } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { BrandHeader, Button, LANGUAGES, LanguagePicker, LegalConsentNotice, PhoneField, TextField, isCompletePhone, setAppLanguage, toE164, useAppLanguage, ResendCode, useOtpSender, i18next } from '@sheout/design-system';
import { Languages } from 'lucide-react';
import { ApiError, authApi, takeSessionEndedReason, usersApi } from '../api/client';
import type { AuthSession } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { useTranslation } from '@sheout/design-system';


/**
 * Login and Register are the same mechanism - phone + OTP, with the account
 * created on first successful verification. The toggle exists because a new
 * partner looks for a way to register, and a screen headed "Partner Login"
 * reads as the wrong place to be; the mockup's tile carries a Register link
 * for the same reason.
 * <p>
 * The tabs change copy only. There is one OTP code path, the tab never
 * reaches the backend, and where a partner lands after verifying is decided
 * by whether their profile already has a name - not by which tab they
 * picked. See afterSignIn.
 */
type AuthMode = 'login' | 'register';

const AUTH_MODES: AuthMode[] = ['login', 'register'];

/**
 * Same phone -> OTP flow as customer-app's Login, wired to role: DRIVER.
 * <p>
 * NEW: verifying OTP for a brand-new phone number used to drop straight
 * into /home with an empty profile (no name/vehicle info at all) - Home's
 * verification gate meant nothing looked broken, but the driver had no
 * way to actually fill in their vehicle details short of finding Profile's
 * edit form themselves. Now checks the just-created/found profile after
 * sign-in: no name yet means a genuinely new account, so a "Complete Your
 * Profile" step collects name + vehicle type + registration number before
 * /home - same self-service PUT /users/driver/me Profile.tsx already uses,
 * no new endpoint. A returning driver (name already set) skips straight to
 * /home, unchanged.
 */
export function Login() {
  const { t } = useTranslation();
  const [pickingLanguage, setPickingLanguage] = useState(false);
  const lng = useAppLanguage();
  const navigate = useNavigate();
  const { login } = useAuth();

  const [step, setStep] = useState<'phone' | 'otp'>('phone');
  const [mode, setMode] = useState<AuthMode>('login');
  /** Non-error feedback, e.g. 'that number is already registered'. */
  const location = useLocation();
  // Set by another screen that sent her here with something to say - account deletion does.
  const [notice, setNotice] = useState<string | null>(() => {
    // Why she is here, when she did not choose to be: signed in elsewhere,
    // blocked, or signed out from another device. Read once - see
    // takeSessionEndedReason.
    const ended = takeSessionEndedReason();
    if (ended && ended !== 'SIGNED_OUT') return i18next.t(`login.ended.${ended}`);
    return (location.state as { notice?: string } | null)?.notice ?? null;
  });
  const [phoneDigits, setPhoneDigits] = useState('');
  const [code, setCode] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const phoneNumber = toE164(phoneDigits);
  // Remembers the code it sent, and counts down to when another may be
  // asked for - see useOtpSender for the "please wait" this replaces.
  const otp = useOtpSender((phone) => authApi.requestOtp(phone));

  /**
   * Tapping Register with a number that already has an account used to drop
   * straight into the dashboard with no acknowledgement, as though the app
   * had ignored what was asked. It says so now.
   * <p>
   * The check runs AFTER the OTP is verified, deliberately not before
   * sending it: reporting whether a number is registered before the caller
   * proves they control it would let anyone probe numbers to learn which
   * belong to SheOut partners. Once verified there is nothing left to
   * disclose.
   */
  async function afterSignIn(session: AuthSession) {
    login(session);

    if (mode === 'register' && !session.newAccount) {
      setNotice(t('login.alreadyRegistered'));
    } else if (mode === 'login' && session.newAccount) {
      setNotice(t('login.createdForYou'));
    }
    let needsProfile = session.newAccount;
    try {
      const profile = await usersApi.getMyProfile();
      needsProfile = !profile.profileComplete;
    } catch {
      // Profile fetch failed - fall back to the session's own signal rather than stranding the driver here.
    }
    if (needsProfile) {
      // Said on the screen she lands on - set here and navigated away from,
      // the notice was never seen. A number from the other app becomes a new
      // account in this one, and this is where she learns that.
      navigate('/complete-profile', {
        replace: true,
        state: session.newAccount ? { notice: t('login.createdForYou') } : undefined,
      });
    } else {
      // Long enough to read the notice before the screen changes; skipped
      // entirely when there is nothing to say.
      const delay = mode === 'register' && !session.newAccount ? 1400 : 0;
      setTimeout(() => navigate('/home', { replace: true }), delay);
    }
  }

  async function handleSendOtp(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setNotice(null);
    if (!isCompletePhone(phoneDigits)) {
      setError(t('login.invalidPhone'));
      return;
    }
    setSubmitting(true);
    try {
      const outcome = await otp.sendCode(phoneNumber);
      if (outcome === 'reused') setNotice(t('otp.reused', { ns: 'ds', phone: phoneNumber }));
      setCode('');
      setStep('otp');
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('login.sendError'));
    } finally {
      setSubmitting(false);
    }
  }

  async function handleResend() {
    setError(null);
    setNotice(null);
    setSubmitting(true);
    try {
      const outcome = await otp.sendCode(phoneNumber, { resend: true });
      setNotice(t(outcome === 'sent' ? 'otp.resent' : 'otp.reused', { ns: 'ds', phone: phoneNumber }));
      setCode('');
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('login.sendError'));
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
      setError(err instanceof ApiError ? err.message : t('login.verifyError'));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="flex min-h-screen flex-col justify-center bg-gradient-to-br from-[#FEF8F8] via-[#FBF1F6] to-[#E9DEF5] px-screen py-10">
      {/* Same lockup customer-app's Login uses, from the shared package -
          this screen used to show a 64px Logo.jpeg tile and a plain text
          heading, which read as a different product to the rider app. */}
      {/* Language, before anything else - she may not read English, and
          the drawer where it otherwise lives is only there once signed in. */}
      <div className="-mt-4 mb-2 flex justify-end">
        <button
          type="button"
          onClick={() => setPickingLanguage(true)}
          className="flex items-center gap-1.5 rounded-full border border-border bg-surface/80 px-3 py-1.5 text-xs font-semibold text-primary shadow-sm"
          data-testid="login-language"
        >
          <Languages className="h-4 w-4" aria-hidden="true" />
          {LANGUAGES.find((l) => l.code === lng)?.nativeName}
        </button>
      </div>
      <LanguagePicker
        open={pickingLanguage}
        onClose={() => setPickingLanguage(false)}
        onSelect={(code) => {
          setAppLanguage(code);
          setPickingLanguage(false);
        }}
      />

      <BrandHeader size="md" className="mb-6" />

      {(
        <>
          {step === 'phone' && (
            <div className="mb-5 flex gap-2" role="tablist" aria-label={t('login.modeAria')}>
              {AUTH_MODES.map((m) => (
                <button
                  key={m}
                  type="button"
                  role="tab"
                  aria-selected={mode === m}
                  onClick={() => setMode(m)}
                  className={
                    mode === m
                      ? 'flex-1 rounded-full bg-primary px-4 py-2 text-sm font-semibold text-text-inverse'
                      : 'flex-1 rounded-full border border-border px-4 py-2 text-sm font-medium text-text-secondary'
                  }
                >
                  {t(`login.${m}.tab`)}
                </button>
              ))}
            </div>
          )}

          <h1 className="text-center font-heading text-2xl font-bold text-text-primary">{t(`login.${mode}.heading`)}</h1>
          <p className="mb-8 text-center text-sm text-text-secondary">{t(`login.${mode}.subtitle`)}</p>

          {notice && (
            <p className="mb-4 rounded-input bg-primary-light px-4 py-3 text-center text-sm font-medium text-primary">{notice}</p>
          )}

          {step === 'phone' ? (
            <>
              <form onSubmit={handleSendOtp} className="space-y-4">
                <PhoneField value={phoneDigits} onChange={setPhoneDigits} error={error ?? undefined} placeholder={t('login.phonePlaceholder')} />
                {/* Above the button, so it is read before the decision
                    rather than after it - see LegalConsentNotice. */}
                <LegalConsentNotice
                  actionLabel={t('login.sendOtp')}
                  onOpenTerms={() => navigate('/terms')}
                  onOpenPrivacy={() => navigate('/privacy')}
                />
                <Button type="submit" fullWidth disabled={submitting}>
                  {submitting ? t('common.sending') : t('login.sendOtp')}
                </Button>
              </form>

              {/* The mockup's Partner Login tile carries this line under the
                  form. No password field and no "Register" link: sign-in is
                  OTP-only, and a first-time number is routed to the profile
                  step after verification, not before it. */}
              <p className="mt-8 text-center text-xs font-medium text-text-secondary">
                {t('splash.footer')}
              </p>
            </>
          ) : (
            <form onSubmit={handleVerifyOtp} className="space-y-4">
              <p className="text-center text-sm text-text-secondary">{t('login.codeSentTo', { phone: phoneNumber })}</p>
              <TextField
                type="text"
                inputMode="numeric"
                placeholder={t('login.codePlaceholder')}
                value={code}
                onChange={(e) => setCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
                error={error ?? undefined}
              />
              <Button type="submit" fullWidth disabled={submitting}>
                {submitting ? t('login.verifying') : t('login.verify')}
              </Button>
              <ResendCode secondsUntilResend={otp.secondsUntilResend} busy={submitting} onResend={handleResend} />
              <button
                type="button"
                onClick={() => { setStep('phone'); setError(null); setNotice(null); }}
                className="w-full text-center text-sm text-text-secondary underline"
              >
                {t('login.changeNumber')}
              </button>
            </form>
          )}
        </>
      )}
    </div>
  );
}
