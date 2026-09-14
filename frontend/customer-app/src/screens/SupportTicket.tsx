import { useCallback, useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  Card,
  StatusBadge,
  SupportTicketThread,
  TopHeader,
  supportCategoryLabel,
  supportStatusLabel,
  supportStatusTone,
} from '@sheout/design-system';
import { ApiError, supportApi } from '../api/client';
import type { SupportTicketThreadResponse } from '../api/types';

/**
 * One ticket and its conversation with support.
 * <p>
 * Refetched after a send rather than appended locally, so what is on screen
 * is always what the server holds - including a status change, when writing
 * back to a resolved ticket reopens it.
 */
export function SupportTicket() {
  const navigate = useNavigate();
  const { ticketId = '' } = useParams();
  const [thread, setThread] = useState<SupportTicketThreadResponse | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [sending, setSending] = useState(false);
  const [sendError, setSendError] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      setThread(await supportApi.getTicket(ticketId));
      setLoadError(null);
    } catch (err) {
      setLoadError(err instanceof ApiError && err.status === 404
        ? 'This ticket could not be found.'
        : 'Could not load this ticket. Check your connection and try again.');
    }
  }, [ticketId]);

  useEffect(() => {
    void load();
  }, [load]);

  async function send(message: string): Promise<boolean> {
    setSending(true);
    setSendError(null);
    try {
      await supportApi.replyToTicket(ticketId, message);
      await load();
      return true;
    } catch (err) {
      setSendError(err instanceof ApiError ? err.message : 'Could not send that. Try again.');
      if (err instanceof ApiError && err.status === 409) await load();
      return false;
    } finally {
      setSending(false);
    }
  }

  const ticket = thread?.ticket;
  return (
    <div className="space-y-4">
      <TopHeader variant="back" title="Your ticket" onBack={() => navigate('/help')} />
      {loadError && <p className="text-sm text-danger">{loadError}</p>}
      {!thread && !loadError && <p className="text-center text-sm text-text-secondary">Loading...</p>}
      {ticket && thread && (
        <>
          <Card className="space-y-1">
            <p className="font-heading font-semibold text-text-primary">{ticket.subject}</p>
            <p className="text-xs text-text-secondary">
              {supportCategoryLabel(ticket.category, 'customer')} &middot; raised{' '}
              {new Date(ticket.createdAt).toLocaleDateString([], { day: 'numeric', month: 'short', year: 'numeric' })}
            </p>
            <StatusBadge tone={supportStatusTone(ticket.status)}>{supportStatusLabel(ticket.status)}</StatusBadge>
          </Card>
          <SupportTicketThread
            description={ticket.description}
            raisedAt={ticket.createdAt}
            messages={thread.messages}
            open={thread.open}
            sending={sending}
            error={sendError}
            onSend={send}
            onDismissError={() => setSendError(null)}
          />
        </>
      )}
    </div>
  );
}
