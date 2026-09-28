import { Bot, LifeBuoy, PhoneCall, Send, ShieldAlert } from 'lucide-react';
import { useEffect, useRef, useState, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { SafetyText } from '../i18n/SafetyText';
import { soundsLikeEmergency } from '../lib/distress';
import { Button } from './Button';
import { Card } from './Card';

export type AssistantKind = 'ANSWER' | 'ESCALATE' | 'EMERGENCY' | 'LIMIT_REACHED' | 'UNAVAILABLE';

/** A pre-filled support ticket the user reviews and sends - never sent by the assistant itself. */
export interface AssistantTicketDraft {
  category: string;
  subject: string;
  summary: string;
}

export interface AssistantReply {
  kind: AssistantKind;
  text: string | null;
  ticket: AssistantTicketDraft | null;
  remainingToday: number;
}

export interface AssistantTurn {
  role: 'user' | 'assistant';
  text: string;
}

interface ChatEntry {
  role: 'user' | 'assistant';
  text: string;
  kind?: AssistantKind;
  ticket?: AssistantTicketDraft | null;
}

export interface HelpAssistantChatProps {
  ask: (messages: AssistantTurn[]) => Promise<AssistantReply>;
  /** The app's own SOS control - a button to the SOS screen, or the partner app's SOS sheet. */
  sosAction: ReactNode;
  emergencyNumber: string;
  /** Opens Raise an issue, pre-filled; the user reads it and sends it herself. */
  onRaiseTicket: (draft: AssistantTicketDraft) => void;
}

/**
 * SheOut Help: a chat that answers from SheOut's own help content.
 * <p>
 * Three rules shape it:
 * <ol>
 *   <li>Danger comes first. A message that sounds like someone in trouble is
 *       caught here before anything is sent, and the SOS panel - SOS and
 *       112, large - replaces the chat at once. Nobody in an emergency is
 *       left talking to a bot. The server checks again, and the model can
 *       also call it out.</li>
 *   <li>A person takes over what it cannot answer. Anything it is unsure of,
 *       and every complaint, dispute or payment problem, becomes a support
 *       ticket she sends through the ordinary form, pre-filled from the
 *       conversation - and the chat says plainly that a person reads it and
 *       it is not instant.</li>
 *   <li>It says what it is: an assistant working from SheOut's help content,
 *       which can make mistakes.</li>
 * </ol>
 */
export function HelpAssistantChat({ ask, sosAction, emergencyNumber, onRaiseTicket }: HelpAssistantChatProps) {
  const { t } = useTranslation('ds');
  const [entries, setEntries] = useState<ChatEntry[]>([]);
  const [draft, setDraft] = useState('');
  const [busy, setBusy] = useState(false);
  const [emergency, setEmergency] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const bottom = useRef<HTMLDivElement>(null);

  useEffect(() => {
    bottom.current?.scrollIntoView?.({ behavior: 'smooth', block: 'end' });
  }, [entries, busy, emergency]);

  async function send() {
    const text = draft.trim();
    if (!text || busy) return;
    setDraft('');
    setError(null);
    const next: ChatEntry[] = [...entries, { role: 'user', text }];
    setEntries(next);
    if (soundsLikeEmergency(text)) {
      // Before any network: the SOS panel now. The server is still told, so
      // the moment is counted - but nothing waits for it.
      setEmergency(true);
      void ask(toTurns(next)).catch(() => undefined);
      return;
    }
    setBusy(true);
    try {
      const reply = await ask(toTurns(next));
      if (reply.kind === 'EMERGENCY') setEmergency(true);
      setEntries([
        ...next,
        {
          role: 'assistant',
          kind: reply.kind,
          ticket: reply.ticket,
          text:
            reply.text ??
            (reply.kind === 'LIMIT_REACHED'
              ? t('assistant.limitReached')
              : reply.kind === 'UNAVAILABLE'
                ? t('assistant.unavailable')
                : reply.kind === 'EMERGENCY'
                  ? ''
                  : t('assistant.handOff')),
        },
      ]);
    } catch {
      setError(t('assistant.error'));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="flex min-h-[70vh] flex-col gap-4" data-testid="help-assistant">
      <Card className="flex items-start gap-3 text-sm text-text-secondary">
        <Bot className="mt-0.5 h-5 w-5 shrink-0 text-primary" aria-hidden="true" />
        <p>{t('assistant.intro')}</p>
      </Card>

      <div className="flex-1 space-y-3" aria-live="polite">
        {entries.map((entry, i) =>
          entry.role === 'user' ? (
            <div key={i} className="ml-10 rounded-card rounded-br-sm bg-primary px-4 py-3 text-sm text-text-inverse" data-testid="assistant-user-message">
              {entry.text}
            </div>
          ) : (
            <div key={i} className="mr-6 space-y-3" data-testid="assistant-reply" data-kind={entry.kind}>
              {entry.text && (
                <div className="whitespace-pre-line rounded-card rounded-bl-sm bg-surface px-4 py-3 text-sm text-text-primary shadow-card">
                  {entry.text}
                </div>
              )}
              {entry.ticket && (
                <Card className="space-y-2" data-testid="assistant-ticket">
                  <p className="text-sm text-text-secondary">{t('assistant.ticketExplain')}</p>
                  <Button fullWidth size="md" icon={<LifeBuoy className="h-4 w-4" />} onClick={() => onRaiseTicket(entry.ticket!)}>
                    {t('assistant.ticketButton')}
                  </Button>
                </Card>
              )}
            </div>
          )
        )}
        {busy && <p className="text-sm text-text-secondary" data-testid="assistant-thinking">{t('assistant.thinking')}</p>}
        {error && <p className="text-sm text-danger" role="alert">{error}</p>}
        {/* Last, where the chat scrolls to - never above the fold of a long conversation. */}
        {emergency && (
          <Card tone="danger" className="space-y-3" data-testid="assistant-emergency">
            <p className="flex items-center gap-2 font-heading text-card-title text-text-primary">
              <ShieldAlert className="h-5 w-5 text-danger" aria-hidden="true" />
              <SafetyText k="assistant.emergencyTitle" englishClassName="font-normal" />
            </p>
            <p className="text-sm text-text-primary">
              <SafetyText k="assistant.emergencyBody" values={{ number: emergencyNumber }} />
            </p>
            {sosAction}
            <Button
              fullWidth
              variant="danger"
              icon={<PhoneCall className="h-4 w-4" />}
              onClick={() => {
                window.location.href = `tel:${emergencyNumber}`;
              }}
              data-testid="assistant-call-112"
            >
              <SafetyText k="assistant.call" values={{ number: emergencyNumber }} englishClassName="font-normal" />
            </Button>
            <button
              type="button"
              className="w-full py-2 text-sm font-semibold text-text-secondary"
              onClick={() => setEmergency(false)}
              data-testid="assistant-im-safe"
            >
              <SafetyText k="assistant.imSafe" />
            </button>
          </Card>
        )}

        <div ref={bottom} />
      </div>

      <form
        className="sticky bottom-0 flex items-end gap-2 bg-background pb-4 pt-2"
        onSubmit={(e) => {
          e.preventDefault();
          void send();
        }}
      >
        <textarea
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter' && !e.shiftKey) {
              e.preventDefault();
              void send();
            }
          }}
          rows={1}
          maxLength={1000}
          placeholder={t('assistant.placeholder')}
          aria-label={t('assistant.placeholder')}
          className="max-h-32 min-h-[48px] flex-1 resize-none rounded-input border border-border bg-surface px-4 py-3 text-sm text-text-primary"
          data-testid="assistant-input"
        />
        <Button type="submit" size="md" disabled={busy || !draft.trim()} aria-label={t('assistant.send')} data-testid="assistant-send">
          <Send className="h-4 w-4" aria-hidden="true" />
        </Button>
      </form>
    </div>
  );
}

function toTurns(entries: ChatEntry[]): AssistantTurn[] {
  // Only what the server can use: text turns, alternating. The SOS panel and
  // ticket cards are the app's own; a message that got no reply (the SOS
  // panel answered it) is joined to the next one rather than sent twice in a row.
  const turns: AssistantTurn[] = [];
  for (const e of entries) {
    if (!e.text) continue;
    const last = turns[turns.length - 1];
    if (last && last.role === e.role) last.text = `${last.text}\n${e.text}`;
    else turns.push({ role: e.role, text: e.text });
  }
  return turns;
}
