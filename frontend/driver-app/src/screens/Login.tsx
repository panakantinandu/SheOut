import { Bike, Car, Phone, Truck, User } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { BrandHeader, Button, TextField } from '@sheout/design-system';
import { ApiError, authApi, usersApi } from '../api/client';
import type { AuthSession, VehicleType } from '../api/types';
import { useAuth } from '../auth/AuthContext';

const PHONE_REGEX = /^\+[1-9]\d{7,14}$/;

const VEHICLE_OPTIONS: { key: VehicleType; label: string; icon: JSX.Element }[] = [
  { key: 'BIKE', label: 'Bike', icon: <Bike className="h-4 w-4" /> },
  { key: 'AUTO', label: 'Auto', icon: <Truck className="h-4 w-4" /> },
  { key: 'CAB', label: 'Cab', icon: <Car className="h-4 w-4" /> },
];

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
  const navigate = useNavigate();
  const { login } = useAuth();

  const [step, setStep] = useState<'phone' | 'otp' | 'complete-profile'>('phone');
  const [phoneDigits, setPhoneDigits] = useState('');
  const [code, setCode] = useState('');
  const [name, setName] = useState('');
  const [vehicleType, setVehicleType] = useState<VehicleType>('BIKE');
  const [vehicleReg, setVehicleReg] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const phoneNumber = `+91${phoneDigits}`;

  async function afterSignIn(session: AuthSession) {
    login(session);
    let needsProfile = session.newAccount;
    try {
      const profile = await usersApi.getMyProfile();
      needsProfile = !profile.name;
    } catch {
      // Profile fetch failed - fall back to the session's own signal rather than stranding the driver here.
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
    if (!name.trim() || !vehicleReg.trim()) {
      setError('Enter your name and vehicle registration number');
      return;
    }
    setSubmitting(true);
    try {
      await usersApi.updateMyProfile({ name: name.trim(), vehicleType, vehicleRegistrationNumber: vehicleReg.trim() });
      navigate('/home', { replace: true });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not save your profile');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="flex min-h-screen flex-col justify-center bg-background px-screen py-10">
      {/* Same lockup customer-app's Login uses, from the shared package -
          this screen used to show a 64px Logo.jpeg tile and a plain text
          heading, which read as a different product to the rider app. */}
      <BrandHeader size="md" className="mb-6" />

      {step === 'complete-profile' ? (
        <>
          <h1 className="text-center font-heading text-2xl font-bold text-text-primary">Complete Your Profile</h1>
          <p className="mb-8 text-center text-sm text-text-secondary">Tell us about you and your vehicle</p>
          <form onSubmit={handleCompleteProfile} className="space-y-4">
            <TextField
              icon={<User className="h-4 w-4 text-text-secondary" />}
              type="text"
              placeholder="Your Name"
              value={name}
              onChange={(e) => setName(e.target.value)}
            />
            <div>
              <span className="mb-1.5 block text-sm font-medium text-text-primary">Vehicle Type</span>
              <div className="flex gap-2">
                {VEHICLE_OPTIONS.map((opt) => (
                  <button
                    key={opt.key}
                    type="button"
                    onClick={() => setVehicleType(opt.key)}
                    className={
                      vehicleType === opt.key
                        ? 'flex flex-1 items-center justify-center gap-1.5 rounded-full bg-primary py-2 text-sm font-semibold text-text-inverse'
                        : 'flex flex-1 items-center justify-center gap-1.5 rounded-full border border-border py-2 text-sm font-medium text-text-secondary'
                    }
                  >
                    {opt.icon} {opt.label}
                  </button>
                ))}
              </div>
            </div>
            <TextField
              type="text"
              placeholder="Vehicle Registration Number"
              value={vehicleReg}
              onChange={(e) => setVehicleReg(e.target.value)}
              error={error ?? undefined}
            />
            <Button type="submit" fullWidth disabled={submitting}>
              {submitting ? 'Saving...' : 'Continue'}
            </Button>
          </form>
        </>
      ) : (
        <>
          <h1 className="text-center font-heading text-2xl font-bold text-text-primary">Partner Login</h1>
          <p className="mb-8 text-center text-sm text-text-secondary">Sign in to start driving</p>

          {step === 'phone' ? (
            <>
              <form onSubmit={handleSendOtp} className="space-y-4">
                <TextField
                  // Identical prefix treatment to customer-app's Login: the
                  // country code is fixed, so it is shown rather than typed.
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

              {/* The mockup's Partner Login tile carries this line under the
                  form. No password field and no "Register" link: sign-in is
                  OTP-only, and a first-time number is routed to the profile
                  step after verification, not before it. */}
              <p className="mt-8 text-center text-xs font-medium text-text-secondary">
                Empowering Women Partners
              </p>
            </>
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
        </>
      )}
    </div>
  );
}
