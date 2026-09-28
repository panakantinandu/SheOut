import { Lock, LifeBuoy, PhoneCall, Send, ShieldAlert } from 'lucide-react';
import { useEffect, useRef, useState, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { SafetyText } from '../i18n/SafetyText';
import { soundsLikeEmergency } from '../lib/distress';
import { AssistantAvatar, ASSISTANT_NAME } from './AssistantAvatar';
import { Button } from './Button';
import { Card } from './Card';
import { ThinkingIndicator } from './ThinkingIndicator';

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
  /**
   * Opens Raise an issue, pre-filled; the user reads it and sends it herself.
   * `conversation` is the chat so far, as plain text, for the description:
   * the person who picks it up sees what was already said.
   */
  onRaiseTicket: (draft: AssistantTicketDraft, conversation: string) => void;
  /**
   * The avatar never moves (the partner app: a partner opening help is
   * often mid-shift, and nothing there animates).
   */
  stillAvatar?: boolean;
}

/** Room left in a ticket's description after the summary and a footer line. */
const CONVERSATION_MAX_CHARS = 1200;

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
export function HelpAssistantChat({ ask, sosAction, emergencyNumber, onRaiseTicket, stillAvatar = false }: HelpAssistantChatProps) {
  const { t } = useTranslation('ds');
  const [entries, setEntries] = useState<ChatEntry[]>([]);
  const [draft, setDraft] = useState('');
  const [busy, setBusy] = useState(false);
  const [emergency, setEmergency] = useState(false);
  const [error, setError] = useState<string | null>(null);
  /** Today's cap is spent: the avatar sleeps and the way on is a ticket. */
  const [asleep, setAsleep] = useState(false);
  /**
   * She is typing. The header avatar holds still meanwhile: measured at 6x
   * CPU throttle, its animation doubled the time a keystroke took to show
   * (median 34 ms static, 70 ms animated). It moves again when she stops.
   */
  const [typing, setTyping] = useState(false);
  const bottom = useRef<HTMLDivElement>(null);
  const name = ASSISTANT_NAME;

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
      if (reply.kind === 'LIMIT_REACHED') setAsleep(true);
      setEntries([
        ...next,
        {
          role: 'assistant',
          kind: reply.kind,
          ticket: reply.ticket,
          text:
            reply.text ??
            (reply.kind === 'LIMIT_REACHED'
              ? t('assistant.limitReached', { name })
              : reply.kind === 'UNAVAILABLE'
                ? t('assistant.unavailable', { name })
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
      {/* Who is talking. Calm and still while the SOS panel is up: nothing
          on screen should be moving then except her own thumb. */}
      <Card className="space-y-3" data-testid="assistant-header">
        <div className="flex items-center gap-3">
          <AssistantAvatar size={48} state={asleep ? 'sleeping' : 'idle'} still={stillAvatar || emergency || typing || busy} />
          <div className="min-w-0">
            <p className="font-heading text-card-title text-text-primary">{name}</p>
            <p className="text-xs text-text-secondary">{asleep ? t('assistant.asleepStatus') : t('assistant.status')}</p>
          </div>
        </div>
        <p className="text-sm text-text-secondary">{t('assistant.intro', { name })}</p>
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
                <div className="flex items-end gap-2">
                  <AssistantAvatar size={24} state={entry.kind === 'LIMIT_REACHED' ? 'sleeping' : 'idle'} staticOnly />
                  <div className="min-w-0 flex-1 whitespace-pre-line rounded-card rounded-bl-sm bg-surface px-4 py-3 text-sm text-text-primary shadow-card">
                    {entry.text}
                  </div>
                </div>
              )}
              {entry.ticket && (
                <Card className="ml-8 space-y-2" data-testid="assistant-ticket">
                  <p className="text-sm text-text-secondary">{t('assistant.ticketExplain')}</p>
                  <Button
                    fullWidth
                    size="md"
                    icon={<LifeBuoy className="h-4 w-4" />}
                    onClick={() => onRaiseTicket(entry.ticket!, conversationText(entries, name, t('assistant.you')))}
                  >
                    {t('assistant.ticketButton')}
                  </Button>
                </Card>
              )}
            </div>
          )
        )}
        {busy && (
          <p className="flex items-center gap-2 text-sm text-text-secondary" data-testid="assistant-thinking">
            <ThinkingIndicator mode="composing" size={20} paused={stillAvatar} label={t('assistant.thinking', { name })} />
            <span aria-hidden="true">{t('assistant.thinking', { name })}</span>
          </p>
        )}
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
        className="sticky bottom-0 bg-background pb-4 pt-2"
        onSubmit={(e) => {
          e.preventDefault();
          void send();
        }}
      >
        <div className="flex items-end gap-2">
        <textarea
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          onFocus={() => setTyping(true)}
          onBlur={() => setTyping(false)}
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
        </div>
        {/* Said where she types, not buried in a policy. */}
        <p className="mt-2 flex items-start gap-1.5 text-xs text-text-secondary" data-testid="assistant-privacy-note">
          <Lock className="mt-0.5 h-3 w-3 shrink-0" aria-hidden="true" />
          <SafetyText k="assistant.privacyNote" />
        </p>
      </form>
    </div>
  );
}

/**
 * The chat as plain text for a support ticket, newest last, trimmed from the
 * start to fit. Only what was said: no SOS panel, no ticket cards.
 */
function conversationText(entries: ChatEntry[], name: string, you: string): string {
  const lines = entries.filter((e) => e.text).map((e) => `${e.role === 'user' ? you : name}: ${e.text}`);
  let text = lines.join('\n');
  while (text.length > CONVERSATION_MAX_CHARS && lines.length > 1) {
    lines.shift();
    text = `...\n${lines.join('\n')}`;
  }
  return text.length > CONVERSATION_MAX_CHARS ? text.slice(text.length - CONVERSATION_MAX_CHARS) : text;
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

/**
 * A support ticket's description from an assistant hand-off: the summary,
 * then the conversation, then a line saying a person will review it - cut to
 * fit the ticket's limit, from the conversation's oldest end, never the
 * summary or the footer.
 */
export function assistantTicketDescription(summary: string, conversation: string, heading: string, footer: string, max = 2000): string {
  const fixed = `${summary}\n\n${heading}\n\n\n${footer}`.length;
  const room = Math.max(0, max - fixed);
  const convo = conversation.length > room ? conversation.slice(conversation.length - room) : conversation;
  const text = convo ? `${summary}\n\n${heading}\n${convo}\n\n${footer}` : `${summary}\n\n${footer}`;
  return text.slice(0, max);
}
