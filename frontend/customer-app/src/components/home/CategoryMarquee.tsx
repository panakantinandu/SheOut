import { useState } from 'react';
import type { SellerCategory } from '../../api/types';

export type MarqueeItem = { value: SellerCategory; label: string; art: string; tint: string };

/**
 * The marketplace's six categories gliding past on Home, each one a way
 * straight into that category.
 * <p>
 * The items are drawn twice so the strip can scroll by exactly half its
 * width and start over without a visible jump (see the marquee keyframes);
 * the copy is hidden from screen readers and the keyboard, so each category
 * is announced once. A finger on the strip or a pointer over it holds it
 * still, so a moving target is never what she has to tap. Under reduced
 * motion it does not move at all and is an ordinary swipeable row.
 */
export function CategoryMarquee({ items, onPick }: { items: MarqueeItem[]; onPick: (value: SellerCategory) => void }) {
  const [held, setHeld] = useState(false);

  const pill = (item: MarqueeItem, copy: boolean) => (
    <button
      key={`${copy ? 'b' : 'a'}-${item.value}`}
      type="button"
      onClick={() => onPick(item.value)}
      tabIndex={copy ? -1 : undefined}
      aria-hidden={copy || undefined}
      className="mr-2.5 flex shrink-0 items-center gap-2 rounded-full border border-border bg-surface py-1.5 pl-1.5 pr-4 shadow-lift transition-transform duration-100 motion-safe:active:scale-95"
      data-testid={copy ? undefined : `marquee-${item.value}`}
    >
      <span className={`flex h-9 w-9 items-center justify-center rounded-full ${item.tint}`}>
        <img src={item.art} alt="" aria-hidden="true" loading="lazy" draggable={false} className="h-9 w-9 rounded-full object-cover" />
      </span>
      <span className="whitespace-nowrap text-caption font-semibold text-text-primary">{item.label}</span>
    </button>
  );

  return (
    <div
      className="-mx-screen -my-1 overflow-hidden py-2 [mask-image:linear-gradient(90deg,transparent,#000_8%,#000_92%,transparent)] motion-reduce:overflow-x-auto motion-reduce:[mask-image:none]"
      onPointerEnter={() => setHeld(true)}
      onPointerLeave={() => setHeld(false)}
      onPointerDown={() => setHeld(true)}
      onPointerUp={(e) => e.pointerType !== 'mouse' && setHeld(false)}
      onFocus={() => setHeld(true)}
      onBlur={() => setHeld(false)}
      data-testid="home-category-marquee"
    >
      <div
        className="flex w-max motion-safe:animate-marquee motion-reduce:px-screen"
        style={{ animationPlayState: held ? 'paused' : 'running' }}
      >
        {items.map((item) => pill(item, false))}
        {items.map((item) => pill(item, true))}
      </div>
    </div>
  );
}
