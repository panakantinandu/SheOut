import { useEffect, useRef, useState } from 'react';
import { Send } from 'lucide-react';
import { SUPPORT_TEXT_MAX } from '../lib/support';

export interface SupportThreadMessage {
  id: string;
  message: string;
  mine: boolean;
  fromSupport: boolean;
  createdAt: string;
}

export interface SupportTicketThreadProps {
  /** What the person wrote when they raised it - shown first, as the start of the conversation. */
  description: string;
  raisedAt: string;
  messages: SupportThreadMessage[];
  /** False once the ticket is CLOSED; the composer gives way to a note. */
  open: boolean;
  sending?: boolean;
  error?: string | null;
  /** Resolve true when the message went; the box is only cleared then. */
  onSend: (message: string) => boolean | Promise<boolean>;
  onDismissError?: () => void;
}

/**
 * A ticket's conversation, as the person who raised it sees it.
 * <p>
 * Replies are labelled "SheOut Support", never with an operator's name or
 * number: the rider is talking to SheOut, and which person on the team
 * picked it up is not hers to know. Internal notes never reach this
 * component at all - the server leaves them out of her read.
 * <p>
 * The composer follows the same rule ChatThread does: what somebody typed is
 * kept until the server has actually accepted it.
 */
export function SupportTicketThread({
  description,
  raisedAt,
  messages,
  open,
  sending = false,
  error = null,
  onSend,
  onDismissError,
}: SupportTicketThreadProps) {
  const [draft, setDraft] = useState('');
  const endRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    endRef.current?.scrollIntoView({ block: 'nearest' });
  }, [messages.length]);

  async function submit() {
    const body = draft.trim();
    if (!body || sending) return;
    if (await onSend(body)) setDraft('');
  }

  return (
    <div className="flex flex-col gap-3">
      <div className="space-y-2 rounded-card bg-background p-3">
        <Bubble mine label="You" text={description} at={raisedAt} />
        {messages.map((m) => (
          <Bubble
            key={m.id}
            mine={m.mine}
            label={m.fromSupport ? 'SheOut Support' : 'You'}
            text={m.message}
            at={m.createdAt}
          />
        ))}
        {!messages.some((m) => m.fromSupport) && (
          <p className="py-2 text-center text-xs text-text-secondary">
            No reply yet. Support will answer here - check back on this screen.
          </p>
        )}
        <div ref={endRef} />
      </div>

      {error && <p className="text-sm text-danger">{error}</p>}

      {open ? (
        <div className="flex items-end gap-2">
          <textarea
            className="min-h-[44px] flex-1 resize-none rounded-input border border-border bg-surface px-4 py-3 text-sm text-text-primary outline-none transition-colors focus:border-primary"
            rows={2}
            maxLength={SUPPORT_TEXT_MAX}
            placeholder="Add more detail or reply to support"
            value={draft}
            disabled={sending}
            onChange={(e) => {
              setDraft(e.target.value);
              if (error) onDismissError?.();
            }}
          />
          <button
            type="button"
            aria-label="Send message"
            className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full bg-primary text-text-inverse transition-colors hover:bg-primary-dark disabled:opacity-50"
            disabled={sending || draft.trim().length === 0}
            onClick={() => void submit()}
          >
            <Send className="h-5 w-5" />
          </button>
        </div>
      ) : (
        <p className="rounded-input border border-border bg-background px-4 py-3 text-center text-sm text-text-secondary">
          This ticket is closed. If you still need help, raise a new issue.
        </p>
      )}
    </div>
  );
}

function Bubble({ mine, label, text, at }: { mine: boolean; label: string; text: string; at: string }) {
  return (
    <div className={mine ? 'flex justify-end' : 'flex justify-start'}>
      <div
        className={[
          'max-w-[85%] rounded-card px-3 py-2',
          mine ? 'bg-primary text-text-inverse' : 'bg-surface text-text-primary',
        ].join(' ')}
      >
        <p className={['text-[10px] font-semibold', mine ? 'text-text-inverse/80' : 'text-primary'].join(' ')}>{label}</p>
        <p className="whitespace-pre-wrap break-words text-sm">{text}</p>
        <p className={['mt-1 text-[10px]', mine ? 'text-text-inverse/70' : 'text-text-secondary'].join(' ')}>
          {new Date(at).toLocaleString([], { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' })}
        </p>
      </div>
    </div>
  );
}
