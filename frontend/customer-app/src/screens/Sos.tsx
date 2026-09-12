import { Bell, CheckCircle2, ChevronRight, MapPin, Phone, Users } from 'lucide-react';
import { useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { Card, IconCircle, ListRow, TopHeader } from '@sheout/design-system';
import { ApiError, notificationsApi, usersApi } from '../api/client';
import type { SosResponse } from '../api/types';

const SAFETY_FEATURES = [
  'Live Location Sharing',
  '24/7 Support',
  'Verified Women Partners',
  'Emergency Contacts',
];

// India's unified emergency number - a real tel: dial, not a backend call
// (there is no "call" concept on the backend, calling is inherently
// device-native).
const EMERGENCY_TEL = '112';

/**
 * The mockup's three circular actions, in its order and colours. Filled
 * circles with white glyphs rather than the tinted IconCircle used in
 * lists, since here the circle is the button itself.
 */
const ACTIONS = [
  { key: 'share', label: 'Share Location', bg: 'bg-primary', icon: <MapPin className="h-6 w-6" /> },
  { key: 'call', label: 'Call Emergency', bg: 'bg-danger', icon: <Phone className="h-6 w-6" /> },
  { key: 'contact', label: 'Contact', bg: 'bg-accent-orange', icon: <Users className="h-6 w-6" /> },
] as const;

function getCurrentPosition(): Promise<GeolocationPosition> {
  return new Promise((resolve, reject) => {
    if (!navigator.geolocation) {
      reject(new Error('Geolocation not supported by this browser'));
      return;
    }
    navigator.geolocation.getCurrentPosition(resolve, () => reject(new Error('Could not get your location - allow location access and retry')), {
      timeout: 8000,
    });
  });
}

function reasonMessage(response: SosResponse): string {
  if (response.reason === 'NO_EMERGENCY_CONTACTS') {
    return 'You have no emergency contacts saved yet, so nobody was told. Add one under Profile, Emergency Contacts, or call for help directly.';
  }
  if (response.reason === 'ALL_SENDS_FAILED') {
    return `We recorded your alert but could not reach any of your ${response.contactsTotal} emergency contact(s). Please call for help directly.`;
  }
  return `Help is on the way - notified ${response.contactsNotified} of ${response.contactsTotal} emergency contact(s).`;
}

/**
 * REAL: "Send SOS Alert" and "Share Location" both call the real
 * POST /api/v1/notifications/sos - sharing your location IS what the SOS
 * alert does (it fans out your live location to your emergency contacts),
 * so both actions are wired to the same endpoint rather than inventing a
 * separate, lighter "just share, don't alert" backend capability that
 * doesn't exist. "Contact" fetches real emergency contacts and dials the
 * first one; "Call Emergency" dials India's real emergency number (112) -
 * both are real device actions, not mock, even though neither one hits our
 * backend (there's no "place a call" concept on the backend to wire to).
 * <p>
 * bookingId (if this screen was reached from Tracking's SOS button) comes
 * through router state - see Tracking.tsx's onClick.
 */
export function Sos() {
  const navigate = useNavigate();
  const location = useLocation();
  const bookingId = (location.state as { bookingId?: string } | null)?.bookingId;

  const [sending, setSending] = useState(false);
  const [result, setResult] = useState<SosResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [contactLookupError, setContactLookupError] = useState<string | null>(null);

  async function handleSendSos() {
    setSending(true);
    setError(null);
    setResult(null);
    try {
      const position = await getCurrentPosition();
      const response = await notificationsApi.triggerSos({
        lat: position.coords.latitude,
        lng: position.coords.longitude,
        bookingId,
      });
      setResult(response);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : err instanceof Error ? err.message : 'Could not send SOS alert - check your connection and try again, or call for help directly.');
    } finally {
      setSending(false);
    }
  }

  async function handleContact() {
    setContactLookupError(null);
    try {
      const contacts = await usersApi.getMyEmergencyContacts();
      if (contacts.length === 0) {
        setContactLookupError('No emergency contacts saved yet. Tap Safety Features below to add one.');
        return;
      }
      window.location.href = `tel:${contacts[0].phoneNumber}`;
    } catch (err) {
      setContactLookupError(err instanceof ApiError ? err.message : 'Could not load your emergency contacts.');
    }
  }

  return (
    <div className="space-y-6">
      <TopHeader variant="back" title="SOS" onBack={() => navigate('/home')} />

      {/* The circle IS the trigger, as in the mockup - there is no separate
          button beneath it. Same handleSendSos, same disabled-while-sending
          behaviour and same result/error copy as before; only the tap
          target's shape changed. */}
      <div className="flex flex-col items-center gap-3 text-center">
        <button
          type="button"
          onClick={handleSendSos}
          disabled={sending}
          aria-label={sending ? 'Sending SOS alert' : 'Send SOS alert'}
          className="relative flex h-44 w-44 items-center justify-center rounded-full transition-transform active:scale-95 disabled:opacity-70"
        >
          {/* Two soft rings, not a shadow - the mockup's glow reads as a
              lighter halo of the same red rather than a drop shadow. */}
          <span className="absolute inset-0 rounded-full bg-danger/15" aria-hidden="true" />
          <span className="absolute inset-3 rounded-full bg-danger/25" aria-hidden="true" />
          <span className="relative flex h-32 w-32 flex-col items-center justify-center gap-0.5 rounded-full bg-danger text-text-inverse shadow-card">
            <Bell className="h-9 w-9" />
            <span className="font-heading text-2xl font-extrabold tracking-wide">SOS</span>
          </span>
        </button>

        <p className="font-heading text-lg font-semibold text-text-primary">In Emergency?</p>
        <p className="text-sm text-text-secondary">
          {sending ? 'Sending SOS alert...' : 'Press SOS for immediate help'}
        </p>

        {result && (
          <p className={`text-sm font-medium ${result.success ? 'text-success' : 'text-danger'}`}>{reasonMessage(result)}</p>
        )}
        {error && <p className="text-sm font-medium text-danger">{error}</p>}
      </div>

      {/* A row of circular icon buttons with labels beneath, per the mockup,
          instead of a vertical chevron list. Each one keeps the handler it
          already had. */}
      <div>
        <div className="flex items-start justify-around">
          {ACTIONS.map((action) => (
            <button
              key={action.key}
              type="button"
              onClick={
                action.key === 'share' ? handleSendSos
                  : action.key === 'call' ? () => { window.location.href = `tel:${EMERGENCY_TEL}`; }
                    : handleContact
              }
              disabled={action.key === 'share' && sending}
              className="flex w-24 flex-col items-center gap-2 disabled:opacity-70"
            >
              <span className={`flex h-14 w-14 items-center justify-center rounded-full text-text-inverse ${action.bg}`}>
                {action.icon}
              </span>
              <span className="text-center text-xs font-semibold leading-tight text-text-primary">
                {action.key === 'share' && sending ? 'Sharing...' : action.label}
              </span>
            </button>
          ))}
        </div>
        {contactLookupError && <p className="mt-3 text-center text-sm text-danger">{contactLookupError}</p>}
      </div>

      <div>
        {/* The chevron used to be decorative, because there was nowhere to
            go. There is now: managing who an SOS reaches is the one safety
            setting a customer actually has, so the heading is a real
            control that opens it. */}
        <button
          type="button"
          onClick={() => navigate('/profile/emergency-contacts')}
          className="mb-3 flex w-full items-center justify-between text-left"
        >
          <h2 className="font-heading text-base font-semibold text-text-primary">Safety Features</h2>
          <ChevronRight className="h-5 w-5 text-text-secondary" aria-hidden="true" />
        </button>
        <Card className="space-y-3">
          {SAFETY_FEATURES.map((feature) => (
            <ListRow key={feature} icon={<IconCircle tone="soft" color="green" size="sm" icon={<CheckCircle2 />} />} label={feature} chevron={false} />
          ))}
        </Card>
      </div>
    </div>
  );
}
