import { useCallback, useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { Card, ChatThread, ContactSupportButton, TopHeader } from '@sheout/design-system';
import { ApiError, chatApi } from '../api/client';
import type { ChatMessage } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { useTranslation } from '@sheout/design-system';

// Long enough not to hammer the backend, short enough that a reply during a
// live trip arrives while it still matters. Same polling approach the rest
// of this app uses - there is no push channel in this backend.
const POLL_INTERVAL_MS = 4000;

/**
 * The conversation with the partner on this trip.
 * <p>
 * This is what a rider gets instead of a phone number, and it is the only
 * channel to her partner that exists. No screen in this app has ever shown
 * a partner's real number and none should: it cannot be withdrawn once
 * given, it outlives the trip, and handing it to a stranger is the specific
 * risk this product was built to remove.
 * <p>
 * Writable only while the trip is live, and that is decided by the server,
 * not here. This screen renders whatever `open` comes back as; posting to a
 * finished booking is refused by ChatService regardless of what is on
 * screen.
 * <p>
 * Still reachable after the trip ends, deliberately. If a rider needs to
 * show somebody what was actually said, the thread has to still be there.
 */
export function Chat() {
  const { t } = useTranslation();
  const { bookingId } = useParams<{ bookingId: string }>();
  const navigate = useNavigate();
  const { accountId } = useAuth();

  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [open, setOpen] = useState(false);
  const [supportPhoneNumber, setSupportPhoneNumber] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [sending, setSending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);

  const load = useCallback(async () => {
    if (!bookingId) return;
    try {
      const thread = await chatApi.getThread(bookingId);
      setMessages(thread.messages);
      setOpen(thread.open);
      setSupportPhoneNumber(thread.supportPhoneNumber || null);
      setLoadError(null);
    } catch (err) {
      setLoadError(err instanceof ApiError ? err.message : t('chat.loadError'));
    } finally {
      setLoading(false);
    }
  }, [bookingId]);

  useEffect(() => {
    let stopped = false;
    void load();
    const interval = setInterval(() => {
      if (!stopped) void load();
    }, POLL_INTERVAL_MS);
    return () => {
      stopped = true;
      clearInterval(interval);
    };
  }, [load]);

  /**
   * Returns whether the message actually went, so ChatThread knows whether
   * to empty the box. A refusal must leave what she typed where it is.
   */
  async function handleSend(body: string): Promise<boolean> {
    if (!bookingId) return false;
    setSending(true);
    setError(null);
    try {
      const sent = await chatApi.send(bookingId, body);
      setMessages((current) => [...current, sent]);
      return true;
    } catch (err) {
      // A refusal, not a failure: the message was understood and declined.
      // The server's own wording explains which rule was hit, so it is shown
      // as-is rather than flattened into "something went wrong".
      setError(err instanceof ApiError ? err.message : t('chat.sendError'));
      if (err instanceof ApiError && err.status === 409) {
        // The trip ended while this screen was open. Close the composer
        // rather than letting her keep typing into a thread that will keep
        // refusing her.
        setOpen(false);
      }
      return false;
    } finally {
      setSending(false);
    }
  }

  return (
    <div className="space-y-5">
      <TopHeader variant="back" title={t('chat.title')} onBack={() => navigate(-1)} />

      {loadError && <p className="text-sm text-danger">{loadError}</p>}

      <Card className="space-y-4">
        <ChatThread
          messages={messages}
          myAccountId={accountId}
          open={open}
          counterpartLabel={t('common.yourPartner')}
          loading={loading}
          sending={sending}
          error={error}
          onSend={handleSend}
          onDismissError={() => setError(null)}
        />
      </Card>

      <div className="space-y-2">
        <p className="text-center text-xs text-text-secondary">
          {t('chat.noNumbers')}
        </p>
        <ContactSupportButton phoneNumber={supportPhoneNumber} />
      </div>
    </div>
  );
}
