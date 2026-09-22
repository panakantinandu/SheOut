import { MessageCircle } from 'lucide-react';
import { IconCircle } from './IconCircle';
import { useEffect, useRef, useState } from 'react';
import { Send } from 'lucide-react';
import { useTranslation } from 'react-i18next';

export interface ChatThreadMessage {
  id: string;
  senderAccountId: string;
  senderRole: string;
  body: string;
  sentAt: string;
}

export interface ChatThreadProps {
  messages: ChatThreadMessage[];
  /** Used to decide which side of the thread a message sits on. */
  myAccountId: string | null;
  /** False once the trip has ended - the composer is replaced by a read-only note. */
  open: boolean;
  /** What to call the other person, e.g. "your partner" or "your rider". */
  counterpartLabel: string;
  loading?: boolean;
  sending?: boolean;
  /** A send that was refused, shown above the composer without clearing the box. */
  error?: string | null;
  /**
   * Sends the message. Resolve true when it actually went, false when it did
   * not - the box is only emptied on true.
   * <p>
   * The box used to be cleared the instant the button was pressed, which
   * meant a refused message was also a deleted one: somebody who pasted a
   * number, or who was offline for a second, lost what they had typed and
   * had to write it again to find out whether it would be accepted this
   * time. Nothing a person typed should disappear because the send failed.
   */
  onSend: (body: string) => boolean | Promise<boolean>;
  /** Called when the person edits the box, so a stale refusal can be cleared. */
  onDismissError?: () => void;
}

const MAX_BODY = 1000;

/**
 * The conversation between the two people on a trip.
 * <p>
 * This is what replaced showing them each other's phone number. Handing a
 * stranger a woman's real number is the exact risk this product exists to
 * reduce, and unlike a chat thread it cannot be taken back, closed when the
 * trip ends, or read later by anyone settling a dispute.
 * <p>
 * The composer disappears once the trip is over, but that is a courtesy to
 * the eye, not the rule. The server refuses a message on a finished booking
 * regardless of what this component renders - hiding an input has never
 * stopped anyone who can reach the endpoint.
 * <p>
 * The thread stays readable forever afterwards, on purpose: when a rider
 * and a partner disagree about what was agreed, this is the only account of
 * it either of them has.
 */
export function ChatThread({
  messages,
  myAccountId,
  open,
  counterpartLabel,
  loading = false,
  sending = false,
  error = null,
  onSend,
  onDismissError,
}: ChatThreadProps) {
  const { t } = useTranslation('ds');
  const [draft, setDraft] = useState('');
  const endRef = useRef<HTMLDivElement>(null);

  // Follow the conversation down as it grows. Only on a change in count, so
  // a poll that returns the same thread does not yank the view back while
  // somebody is reading further up.
  useEffect(() => {
    endRef.current?.scrollIntoView({ block: 'nearest' });
  }, [messages.length]);

  async function submit() {
    const body = draft.trim();
    if (!body || sending) return;
    const sent = await onSend(body);
    if (sent) setDraft('');
  }

  return (
    <div className="flex flex-col gap-3">
      <div className="min-h-[160px] space-y-2 rounded-card bg-background p-3">
        {loading && messages.length === 0 ? (
          <p className="py-6 text-center text-sm text-text-secondary">{t('chat.loading')}</p>
        ) : messages.length === 0 ? (
          <div className="flex flex-col items-center gap-2 py-6 text-center">
          <IconCircle tone="soft" icon={<MessageCircle />} />
          <p className="text-sm text-text-secondary">
            {open
              ? t('chat.emptyOpen', { who: counterpartLabel })
              : t('chat.emptyClosed')}
          </p>
          </div>
        ) : (
          messages.map((message) => {
            const mine = myAccountId !== null && message.senderAccountId === myAccountId;
            return (
              <div key={message.id} className={mine ? 'flex justify-end' : 'flex justify-start'}>
                <div
                  className={[
                    'max-w-[80%] rounded-card px-3 py-2',
                    mine ? 'bg-primary text-text-inverse' : 'bg-surface text-text-primary',
                  ].join(' ')}
                >
                  <p className="whitespace-pre-wrap break-words text-sm">{message.body}</p>
                  <p className={['mt-1 text-[10px]', mine ? 'text-text-inverse/70' : 'text-text-secondary'].join(' ')}>
                    {timeOf(message.sentAt)}
                  </p>
                </div>
              </div>
            );
          })
        )}
        <div ref={endRef} />
      </div>

      {error && <p className="text-sm text-danger">{error}</p>}

      {open ? (
        <div className="flex items-end gap-2">
          <textarea
            className="min-h-[44px] flex-1 resize-none rounded-input border border-border bg-surface px-4 py-3 text-sm text-text-primary outline-none transition-colors focus:border-primary"
            rows={1}
            maxLength={MAX_BODY}
            placeholder={t('chat.placeholder')}
            value={draft}
            disabled={sending}
            onChange={(e) => {
              setDraft(e.target.value);
              if (error) onDismissError?.();
            }}
            onKeyDown={(e) => {
              // Enter sends, Shift+Enter breaks the line - what a person
              // expects from a message box on a phone-shaped screen.
              if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault();
                void submit();
              }
            }}
          />
          <button
            type="button"
            aria-label={t('chat.send')}
            className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full bg-primary text-text-inverse transition-colors hover:bg-primary-dark disabled:opacity-50"
            disabled={sending || draft.trim().length === 0}
            onClick={() => void submit()}
          >
            <Send className="h-5 w-5" />
          </button>
        </div>
      ) : (
        <p className="rounded-input border border-border bg-background px-4 py-3 text-center text-sm text-text-secondary">
          {t('chat.closedNote')}
        </p>
      )}
    </div>
  );
}

/** Local wall-clock time, which is the only part of a timestamp a chat needs. */
function timeOf(iso: string): string {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return '';
  return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
}
