import { ChevronRight } from 'lucide-react';
import { AssistantAvatar } from './AssistantAvatar';

export interface AssistantEntryCardProps {
  title: string;
  body: string;
  onOpen: () => void;
  /** The avatar follows a finger and hops when tapped. Only on the rider's Home card. */
  interactive?: boolean;
  /** The avatar never moves (the partner app). */
  still?: boolean;
  testId?: string;
}

/**
 * The way into the assistant: a compact card, not a floating button. A
 * floating button would sit over the bottom bar, and the bottom bar is where
 * SOS is; nothing may compete with SOS for that thumb.
 * <p>
 * The whole card is the button. The avatar is a separate, decorative canvas
 * that takes its own taps (a hop) when interactive, so a tap on it does not
 * also open the chat.
 */
export function AssistantEntryCard({ title, body, onOpen, interactive = false, still = false, testId = 'assistant-entry' }: AssistantEntryCardProps) {
  return (
    <div className="relative flex items-center gap-3 rounded-card bg-surface p-4 shadow-card" data-testid={testId}>
      <span
        className="relative z-10 flex shrink-0"
        onClick={interactive ? (e) => e.stopPropagation() : undefined}
      >
        <AssistantAvatar size={52} interactive={interactive} still={still} />
      </span>
      <button type="button" onClick={onOpen} className="flex min-w-0 flex-1 items-center gap-2 text-left after:absolute after:inset-0 after:content-['']">
        <span className="min-w-0 flex-1">
          <span className="block font-heading text-card-title text-text-primary">{title}</span>
          <span className="mt-0.5 block text-sm text-text-secondary">{body}</span>
        </span>
        <ChevronRight className="h-5 w-5 shrink-0 text-text-secondary" aria-hidden="true" />
      </button>
    </div>
  );
}
