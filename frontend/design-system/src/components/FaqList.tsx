import { Card } from './Card';

export interface FaqItem {
  question: string;
  answer: string;
}

export interface FaqListProps {
  items: FaqItem[];
  /** Omit to render the card with no heading above it. */
  heading?: string;
  className?: string;
}

/**
 * A list of questions and answers, separated so they read as a list.
 * <p>
 * Both Help screens had the same five-or-six answers stacked in a single
 * card with nothing between them, which renders as a wall of text: no
 * heading to say what the block is, and no rule to show where one answer
 * ends and the next question begins. A partner scanning for "why can I not
 * go online" had to read the whole thing.
 * <p>
 * Shared rather than written twice, because the two apps have already
 * drifted on exactly this kind of thing before - the status labels and the
 * phone inputs both had to be pulled back together after diverging. The
 * same shape here means a fix to one is a fix to both.
 * <p>
 * Uses the heading-over-divided-card shape the Profile screens already use,
 * so a partner meets one layout convention across the app rather than a new
 * one per screen.
 */
export function FaqList({ items, heading, className }: FaqListProps) {
  if (items.length === 0) return null;

  return (
    <section className={className}>
      {heading && (
        <h2 className="mb-3 font-heading text-base font-semibold text-text-primary">{heading}</h2>
      )}
      <Card className="divide-y divide-border p-0">
        {items.map((item) => (
          <div key={item.question} className="p-4">
            <p className="font-medium text-text-primary">{item.question}</p>
            <p className="mt-1 text-sm leading-relaxed text-text-secondary">{item.answer}</p>
          </div>
        ))}
      </Card>
    </section>
  );
}
