import { Bell, CheckCircle2, ChevronRight, MapPin, MessageSquareText, Phone, Share2, Users } from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { Button, Card, IconCircle, ListRow, SafetyText, TopHeader, i18next, useSafetyString } from '@sheout/design-system';
import { ApiError, notificationsApi, usersApi } from '../api/client';
import type { EmergencyContact, SosResponse } from '../api/types';
import { localEmergencyNumber, mapsLink, openSmsComposer, shareViaDevice } from '../lib/emergency';
import { useTranslation } from '@sheout/design-system';

const SAFETY_FEATURES = ['liveLocation', 'support', 'verifiedPartners', 'emergencyContacts'] as const;

/**
 * The mockup's three circular actions, in its order and colours. Filled
 * circles with white glyphs rather than the tinted IconCircle used in
 * lists, since here the circle is the button itself.
 */
const ACTIONS = [
  { key: 'share', bg: 'bg-primary', icon: <MapPin className="h-6 w-6" /> },
  { key: 'call', bg: 'bg-danger', icon: <Phone className="h-6 w-6" /> },
  { key: 'contact', bg: 'bg-accent-orange', icon: <Users className="h-6 w-6" /> },
] as const;

interface Position {
  lat: number;
  lng: number;
}

function getCurrentPosition(): Promise<Position> {
  return new Promise((resolve, reject) => {
    if (!navigator.geolocation) {
      reject(new Error(i18next.t('sos.noGeolocation', { ns: 'safety' })));
      return;
    }
    navigator.geolocation.getCurrentPosition(
      (p) => resolve({ lat: p.coords.latitude, lng: p.coords.longitude }),
      () => reject(new Error(i18next.t('sos.locationDenied', { ns: 'safety' }))),
      // A slightly stale fix now beats a perfect one in ten seconds.
      { timeout: 8000, maximumAge: 30000, enableHighAccuracy: true }
    );
  });
}

/**
 * The text her contacts receive. In her app language, and - when that is not
 * English - with the English after it, because the person she is texting may
 * not read the language she uses the app in.
 */
function alertMessage(name: string | undefined, position: Position, lng: string): string {
  const link = mapsLink(position.lat, position.lng);
  const key = name ? 'sos.smsNamed' : 'sos.smsUnnamed';
  const own = i18next.t(key, { ns: 'safety', name, link, lng });
  if (lng === 'en') return own;
  return `${own}\n\n${i18next.t(key, { ns: 'safety', name, link, lng: 'en' })}`;
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
  const { t } = useTranslation();
  // Safety copy: an unreviewed translation always carries the English
  // beneath it - see SafetyText.
  const s = useSafetyString();
  const { i18n } = useTranslation();
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
      setError(`${err instanceof Error ? err.message : s('sos.noLocation')}\n${s('sos.ifInDanger', { number: emergency.number })}`);
      return;
    }
    try {
      setResult(await notificationsApi.triggerSos({ ...position, bookingId }));
    } catch (err) {
      // The alert did not reach SheOut at all. Her phone can still text.
      setError(
        err instanceof ApiError
          ? err.message
          : s('sos.cannotReachSheout', { number: emergency.number })
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
      setError(err instanceof Error ? err.message : s('sos.noLocation'));
    } finally {
      setSharing(false);
    }
  }

  async function share(position: Position) {
    const url = mapsLink(position.lat, position.lng);
    const outcome = await shareViaDevice(t('sos.shareTitle'), myName ? t('sos.shareTextNamed', { name: myName }) : t('sos.shareText'), url);
    if (outcome === 'shared') {
      setNotice(t('sos.shared'));
      return;
    }
    if (outcome === 'cancelled') return;
    // No share sheet from here (desktop browser, or the tap went stale while
    // the location was found). Offer the direct routes instead.
    setShareFallback(true);
    try {
      await navigator.clipboard?.writeText(url);
      setNotice(t('sos.linkCopied'));
    } catch {
      setNotice(t('sos.useButtons'));
    }
  }

  function textContacts() {
    if (!lastPosition || contactNumbers.length === 0) return;
    openSmsComposer(contactNumbers, alertMessage(myName, lastPosition, i18n.language));
  }

  function handleCallContact() {
    if (contacts === null) return;
    if (contacts.length === 0) {
      setError(s('sos.noContactsSaved'));
      return;
    }
    window.location.href = `tel:${contacts[0].phoneNumber}`;
  }

  function resultMessage(response: SosResponse): { tone: 'success' | 'danger'; text: string } {
    if (response.reason === 'NO_EMERGENCY_CONTACTS') {
      return {
        tone: 'danger',
        text: s('sos.resultNoContacts', { number: emergency.number }),
      };
    }
    if (response.reason === 'CONTACTS_RECENTLY_ALERTED') {
      return {
        tone: 'success',
        text: s('sos.resultRecent', { number: emergency.number }),
      };
    }
    if (response.contactsNotified === 0) {
      return {
        tone: 'danger',
        text: s('sos.resultNoneReached', { count: response.contactsTotal }),
      };
    }
    if (response.contactsNotified < response.contactsTotal) {
      return {
        tone: 'danger',
        text: s('sos.resultSomeReached', { notified: response.contactsNotified, total: response.contactsTotal }),
      };
    }
    return {
      tone: 'success',
      text: s('sos.resultAllReached', { count: response.contactsNotified }),
    };
  }

  const message = result ? resultMessage(result) : null;

  return (
    <div className="space-y-6">
      <TopHeader variant="back" title={t('home.sos')} onBack={() => navigate('/home')} />

      {/* The circle IS the trigger, as in the mockup - there is no separate
          button beneath it. */}
      <div className="flex flex-col items-center gap-3 text-center">
        <button
          type="button"
          onClick={handleSendSos}
          disabled={sending}
          aria-label={sending ? t('sos.sendingAria') : t('sos.sendAria')}
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

        <p className="font-heading text-lg font-semibold text-text-primary">
          <SafetyText k="sos.inEmergency" />
        </p>
        <p className="text-sm text-text-secondary">
          {sending ? <SafetyText k="sos.sending" /> : <SafetyText k="sos.pressToAlert" />}
        </p>

        {message && (
          <p className={`whitespace-pre-line text-sm font-medium ${message.tone === 'success' ? 'text-success' : 'text-danger'}`} data-testid="sos-result">
            {message.text}
          </p>
        )}
        {error && <p className="whitespace-pre-line text-sm font-medium text-danger">{error}</p>}
        {notice && <p className="text-sm font-medium text-success">{notice}</p>}
      </div>

      {/* The fallback that does not depend on SheOut's SMS provider. Shown
          whenever anyone was not reached, and whenever sharing could not
          open the share sheet by itself. */}
      {((someoneUnreached && lastPosition) || (shareFallback && lastPosition)) && (
        <Card tone="brand" className="space-y-3" data-testid="sos-fallback">
          {contactNumbers.length > 0 && (
            <Button fullWidth icon={<MessageSquareText className="h-4 w-4" />} onClick={textContacts} data-testid="sos-text-from-phone">
              {contactNumbers.length === 1
                ? <SafetyText k="sos.textOneFromPhone" values={{ name: contacts![0].name }} englishClassName="font-normal" />
                : <SafetyText k="sos.textAllFromPhone" values={{ count: contactNumbers.length }} englishClassName="font-normal" />}
            </Button>
          )}
          <Button
            fullWidth
            variant="secondary"
            icon={<Share2 className="h-4 w-4" />}
            onClick={() => lastPosition && void share(lastPosition)}
          >
            <SafetyText k="sos.shareAnotherWay" englishClassName="font-normal" />
          </Button>
          <Button
            fullWidth
            variant="danger"
            icon={<Phone className="h-4 w-4" />}
            onClick={() => {
              window.location.href = `tel:${emergency.number}`;
            }}
          >
            <SafetyText k="sos.callNumber" values={{ number: emergency.number }} englishClassName="font-normal" />
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
                  ? <SafetyText k="sos.gettingLocation" />
                  : action.key === 'call'
                    ? <SafetyText k="sos.callNumber" values={{ number: emergency.number }} />
                    : <SafetyText k={action.key === 'share' ? 'sos.shareLocation' : 'sos.callContact'} />}
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
          <h2 className="font-heading text-base font-semibold text-text-primary">{t('sos.safetyFeatures')}</h2>
          <ChevronRight className="h-5 w-5 text-text-secondary" aria-hidden="true" />
        </button>
        <Card className="space-y-3">
          {SAFETY_FEATURES.map((feature) => (
            <ListRow key={feature} padded={false} icon={<IconCircle tone="soft" color="green" size="sm" icon={<CheckCircle2 />} />} label={t(`sos.features.${feature}`)} chevron={false} />
          ))}
          {contacts !== null && (
            <p className="text-xs text-text-secondary">
              {contacts.length === 0
                ? <SafetyText k="sos.noContactsYet" />
                : <SafetyText k="sos.alertsGoTo" values={{ names: contacts.map((c) => c.name).join(', ') }} />}
            </p>
          )}
        </Card>
      </div>
    </div>
  );
}
