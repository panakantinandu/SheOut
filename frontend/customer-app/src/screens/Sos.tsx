import { CheckCircle2, MapPin, Phone, ShieldAlert, Users } from 'lucide-react';
import { useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { Button, Card, IconCircle, ListRow, TopHeader } from '@sheout/design-system';
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
    return 'You have no emergency contacts saved yet - add one in your profile, or call for help directly.';
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
        setContactLookupError('No emergency contacts saved yet - add one in your profile first.');
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

      <Card className="flex flex-col items-center gap-3 text-center">
        <IconCircle size="lg" color="red" icon={<ShieldAlert />} />
        <p className="font-heading text-lg font-semibold text-text-primary">In Emergency?</p>
        <p className="text-sm text-text-secondary">Press SOS for immediate help</p>
        <Button variant="danger" fullWidth disabled={sending} onClick={handleSendSos}>
          {sending ? 'Sending SOS Alert...' : 'Send SOS Alert'}
        </Button>

        {result && (
          <p className={`text-sm font-medium ${result.success ? 'text-success' : 'text-danger'}`}>{reasonMessage(result)}</p>
        )}
        {error && <p className="text-sm font-medium text-danger">{error}</p>}
      </Card>

      <Card className="divide-y divide-border p-0">
        <div className="p-4">
          <ListRow
            icon={<IconCircle tone="soft" icon={<MapPin />} />}
            label={sending ? 'Sharing...' : 'Share Location'}
            onClick={handleSendSos}
          />
        </div>
        <div className="p-4">
          <ListRow
            icon={<IconCircle tone="soft" color="orange" icon={<Phone />} />}
            label="Call Emergency"
            onClick={() => {
              window.location.href = `tel:${EMERGENCY_TEL}`;
            }}
          />
        </div>
        <div className="p-4">
          <ListRow
            icon={<IconCircle tone="soft" color="green" icon={<Users />} />}
            label="Contact"
            onClick={handleContact}
          />
        </div>
        {contactLookupError && <p className="px-4 pb-4 text-sm text-danger">{contactLookupError}</p>}
      </Card>

      <div>
        <h2 className="mb-3 font-heading text-base font-semibold text-text-primary">Safety Features</h2>
        <Card className="space-y-3">
          {SAFETY_FEATURES.map((feature) => (
            <ListRow key={feature} icon={<IconCircle tone="soft" color="green" size="sm" icon={<CheckCircle2 />} />} label={feature} chevron={false} />
          ))}
        </Card>
      </div>
    </div>
  );
}
