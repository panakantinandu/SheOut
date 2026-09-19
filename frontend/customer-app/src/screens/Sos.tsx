import { Bell, CheckCircle2, ChevronRight, MapPin, MessageSquareText, Phone, Share2, Users } from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { Button, Card, IconCircle, ListRow, TopHeader } from '@sheout/design-system';
import { ApiError, notificationsApi, usersApi } from '../api/client';
import type { EmergencyContact, SosResponse } from '../api/types';
import { localEmergencyNumber, mapsLink, openSmsComposer, shareViaDevice } from '../lib/emergency';

const SAFETY_FEATURES = [
  'Live Location Sharing',
  '24/7 Support',
  'Verified Women Partners',
  'Emergency Contacts',
];

/**
 * The mockup's three circular actions, in its order and colours. Filled
 * circles with white glyphs rather than the tinted IconCircle used in
 * lists, since here the circle is the button itself.
 */
const ACTIONS = [
  { key: 'share', label: 'Share Location', bg: 'bg-primary', icon: <MapPin className="h-6 w-6" /> },
  { key: 'call', label: 'Call Emergency', bg: 'bg-danger', icon: <Phone className="h-6 w-6" /> },
  { key: 'contact', label: 'Call Contact', bg: 'bg-accent-orange', icon: <Users className="h-6 w-6" /> },
] as const;

interface Position {
  lat: number;
  lng: number;
}

function getCurrentPosition(): Promise<Position> {
  return new Promise((resolve, reject) => {
    if (!navigator.geolocation) {
      reject(new Error('This browser cannot read your location.'));
      return;
    }
    navigator.geolocation.getCurrentPosition(
      (p) => resolve({ lat: p.coords.latitude, lng: p.coords.longitude }),
      () => reject(new Error('Could not get your location - allow location access and try again.')),
      // A slightly stale fix now beats a perfect one in ten seconds.
      { timeout: 8000, maximumAge: 30000, enableHighAccuracy: true }
    );
  });
}

function alertMessage(name: string | undefined, position: Position): string {
  return `SOS - ${name ?? 'I'} need${name ? 's' : ''} help. My location: ${mapsLink(position.lat, position.lng)} (sent from SheOut)`;
}

/**
 * SOS.
 * <p>
 * Pressing SOS records an alert with her location and asks the server to
 * text her emergency contacts. That text goes through an SMS provider, and
 * it can fail for reasons that have nothing to do with her - which is what
 * "could not reach any of your emergency contacts" meant, with nothing
 * offered next. Now, whenever the server did not reach everyone, the screen
 * offers her own phone's SMS app with every contact and her location already
 * filled in, and her share sheet. Those work from anywhere, on her own plan,
 * with no SheOut server involved.
 * <p>
 * "Share Location" is its own thing now, not a second SOS button: it opens
 * her phone's share sheet (WhatsApp, Messages, anything) with a map link, so
 * she can send it to whoever is relevant - a friend waiting for her, family,
 * a colleague - without raising an emergency.
 * <p>
 * "Call Emergency" dials the number for where the phone is, from its time
 * zone: 112 in India, 911 in the US, 999 in the UK. It had been 112
 * everywhere.
 */
export function Sos() {
  const navigate = useNavigate();
  const location = useLocation();
  const bookingId = (location.state as { bookingId?: string } | null)?.bookingId;
  const emergency = useMemo(localEmergencyNumber, []);

  const [sending, setSending] = useState(false);
  const [sharing, setSharing] = useState(false);
  const [result, setResult] = useState<SosResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [lastPosition, setLastPosition] = useState<Position | null>(null);
  const [contacts, setContacts] = useState<EmergencyContact[] | null>(null);
  const [myName, setMyName] = useState<string | undefined>(undefined);
  /** Set when the share sheet could not open straight from the tap, so a direct button is offered. */
  const [shareFallback, setShareFallback] = useState(false);

  // Fetched up front so the fallbacks work the instant they are needed,
  // including if the connection drops right after the alert.
  useEffect(() => {
    usersApi.getMyEmergencyContacts().then(setContacts).catch(() => setContacts([]));
    usersApi
      .getMyProfile()
      .then((p) => setMyName(p.name?.split(' ')[0] || undefined))
      .catch(() => undefined);
  }, []);

  const contactNumbers = (contacts ?? []).map((c) => c.phoneNumber);
  const someoneUnreached = result !== null && result.contactsTotal > 0 && result.contactsNotified < result.contactsTotal
    && result.reason !== 'CONTACTS_RECENTLY_ALERTED';

  async function handleSendSos() {
    setSending(true);
    setError(null);
    setNotice(null);
    setResult(null);
    let position: Position;
    try {
      position = await getCurrentPosition();
      setLastPosition(position);
    } catch (err) {
      setSending(false);
      setError(`${err instanceof Error ? err.message : 'Could not get your location.'} If you are in danger, call ${emergency.number}.`);
      return;
    }
    try {
      setResult(await notificationsApi.triggerSos({ ...position, bookingId }));
    } catch (err) {
      // The alert did not reach SheOut at all. Her phone can still text.
      setError(
        err instanceof ApiError
          ? err.message
          : `Could not reach SheOut. Text your contacts from your phone below, or call ${emergency.number}.`
      );
      setResult({
        alertId: '',
        contactsTotal: contacts?.length ?? 0,
        contactsNotified: 0,
        contactsFailed: contacts?.length ?? 0,
        contacts: [],
        success: false,
        reason: (contacts?.length ?? 0) === 0 ? 'NO_EMERGENCY_CONTACTS' : 'ALL_SENDS_FAILED',
      });
    } finally {
      setSending(false);
    }
  }

  async function handleShareLocation() {
    setSharing(true);
    setError(null);
    setNotice(null);
    setShareFallback(false);
    try {
      const position = await getCurrentPosition();
      setLastPosition(position);
      await share(position);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not get your location.');
    } finally {
      setSharing(false);
    }
  }

  async function share(position: Position) {
    const url = mapsLink(position.lat, position.lng);
    const outcome = await shareViaDevice('My location', `${myName ?? 'I'} ${myName ? 'is' : 'am'} here:`, url);
    if (outcome === 'shared') {
      setNotice('Location shared.');
      return;
    }
    if (outcome === 'cancelled') return;
    // No share sheet from here (desktop browser, or the tap went stale while
    // the location was found). Offer the direct routes instead.
    setShareFallback(true);
    try {
      await navigator.clipboard?.writeText(url);
      setNotice('Your location link is copied - paste it anywhere, or use the buttons below.');
    } catch {
      setNotice('Use the buttons below to send your location.');
    }
  }

  function textContacts() {
    if (!lastPosition || contactNumbers.length === 0) return;
    openSmsComposer(contactNumbers, alertMessage(myName, lastPosition));
  }

  function handleCallContact() {
    if (contacts === null) return;
    if (contacts.length === 0) {
      setError('No emergency contacts saved yet. Tap Safety Features below to add one.');
      return;
    }
    window.location.href = `tel:${contacts[0].phoneNumber}`;
  }

  function resultMessage(response: SosResponse): { tone: 'success' | 'danger'; text: string } {
    if (response.reason === 'NO_EMERGENCY_CONTACTS') {
      return {
        tone: 'danger',
        text: `Your alert is recorded with SheOut, but you have no emergency contacts saved, so nobody was texted. Share your location below or call ${emergency.number}.`,
      };
    }
    if (response.reason === 'CONTACTS_RECENTLY_ALERTED') {
      return {
        tone: 'success',
        text: `Alert recorded with your latest location. Your contacts were texted less than a minute ago. Call ${emergency.number} if you need help right now.`,
      };
    }
    if (response.contactsNotified === 0) {
      return {
        tone: 'danger',
        text: `Your alert is recorded with SheOut, but our text to your ${response.contactsTotal === 1 ? 'contact' : `${response.contactsTotal} contacts`} didn't go through. Send it from your own phone now - it takes one tap.`,
      };
    }
    if (response.contactsNotified < response.contactsTotal) {
      return {
        tone: 'danger',
        text: `Texted ${response.contactsNotified} of ${response.contactsTotal} contacts. Send it from your phone to reach everyone.`,
      };
    }
    return {
      tone: 'success',
      text: `Your location was texted to ${response.contactsNotified === 1 ? 'your emergency contact' : `all ${response.contactsNotified} emergency contacts`}.`,
    };
  }

  const message = result ? resultMessage(result) : null;

  return (
    <div className="space-y-6">
      <TopHeader variant="back" title="SOS" onBack={() => navigate('/home')} />

      {/* The circle IS the trigger, as in the mockup - there is no separate
          button beneath it. */}
      <div className="flex flex-col items-center gap-3 text-center">
        <button
          type="button"
          onClick={handleSendSos}
          disabled={sending}
          aria-label={sending ? 'Sending SOS alert' : 'Send SOS alert'}
          className="relative flex h-44 w-44 items-center justify-center rounded-full transition-transform active:scale-95 disabled:opacity-70"
          data-testid="sos-button"
        >
          <span className="absolute inset-0 rounded-full bg-danger/15" aria-hidden="true" />
          <span className="absolute inset-3 rounded-full bg-danger/25" aria-hidden="true" />
          <span className="relative flex h-32 w-32 flex-col items-center justify-center gap-0.5 rounded-full bg-danger text-text-inverse shadow-card">
            <Bell className="h-9 w-9" />
            <span className="font-heading text-2xl font-extrabold tracking-wide">SOS</span>
          </span>
        </button>

        <p className="font-heading text-lg font-semibold text-text-primary">In Emergency?</p>
        <p className="text-sm text-text-secondary">
          {sending ? 'Sending SOS alert...' : 'Press SOS to alert your emergency contacts with your location'}
        </p>

        {message && (
          <p className={`text-sm font-medium ${message.tone === 'success' ? 'text-success' : 'text-danger'}`} data-testid="sos-result">
            {message.text}
          </p>
        )}
        {error && <p className="text-sm font-medium text-danger">{error}</p>}
        {notice && <p className="text-sm font-medium text-success">{notice}</p>}
      </div>

      {/* The fallback that does not depend on SheOut's SMS provider. Shown
          whenever anyone was not reached, and whenever sharing could not
          open the share sheet by itself. */}
      {((someoneUnreached && lastPosition) || (shareFallback && lastPosition)) && (
        <Card tone="brand" className="space-y-3" data-testid="sos-fallback">
          {contactNumbers.length > 0 && (
            <Button fullWidth icon={<MessageSquareText className="h-4 w-4" />} onClick={textContacts} data-testid="sos-text-from-phone">
              Text {contactNumbers.length === 1 ? contacts![0].name : `all ${contactNumbers.length} contacts`} from your phone
            </Button>
          )}
          <Button
            fullWidth
            variant="secondary"
            icon={<Share2 className="h-4 w-4" />}
            onClick={() => lastPosition && void share(lastPosition)}
          >
            Share location another way
          </Button>
          <Button
            fullWidth
            variant="danger"
            icon={<Phone className="h-4 w-4" />}
            onClick={() => {
              window.location.href = `tel:${emergency.number}`;
            }}
          >
            Call {emergency.number}
          </Button>
        </Card>
      )}

      <div>
        <div className="flex items-start justify-around">
          {ACTIONS.map((action) => (
            <button
              key={action.key}
              type="button"
              onClick={
                action.key === 'share'
                  ? handleShareLocation
                  : action.key === 'call'
                    ? () => {
                        window.location.href = `tel:${emergency.number}`;
                      }
                    : handleCallContact
              }
              disabled={action.key === 'share' && sharing}
              className="flex w-24 flex-col items-center gap-2 disabled:opacity-70"
              data-testid={`sos-action-${action.key}`}
            >
              <span className={`flex h-14 w-14 items-center justify-center rounded-full text-text-inverse ${action.bg}`}>
                {action.icon}
              </span>
              <span className="text-center text-xs font-semibold leading-tight text-text-primary">
                {action.key === 'share' && sharing
                  ? 'Getting location...'
                  : action.key === 'call'
                    ? `Call ${emergency.number}`
                    : action.label}
              </span>
            </button>
          ))}
        </div>
      </div>

      <div>
        {/* Managing who an SOS reaches is the one safety setting a customer
            actually has, so the heading is a real control that opens it. */}
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
            <ListRow key={feature} padded={false} icon={<IconCircle tone="soft" color="green" size="sm" icon={<CheckCircle2 />} />} label={feature} chevron={false} />
          ))}
          {contacts !== null && (
            <p className="text-xs text-text-secondary">
              {contacts.length === 0
                ? 'You have no emergency contacts yet - add at least one.'
                : `SOS alerts go to ${contacts.map((c) => c.name).join(', ')}.`}
            </p>
          )}
        </Card>
      </div>
    </div>
  );
}
