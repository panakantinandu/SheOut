import { TODO_LAWYER, TODO_LEGAL } from '../legal/content';

/**
 * Makes an unfinished section impossible to mistake for a finished one -
 * both a fact only the company can supply and wording a lawyer has not yet
 * approved. Exported for the partner app's consent step.
 */
export function highlightPlaceholders(text: string) {
  const markers = [TODO_LEGAL, TODO_LAWYER].filter((m) => text.includes(m));
  if (!markers.length) return text;
  const pattern = new RegExp(`(${markers.map((m) => m.replace(/[[\]]/g, '\\$&')).join('|')})`);
  return text.split(pattern).map((part, i) =>
    markers.includes(part) ? (
      <span key={i} className="font-semibold text-danger">
        {part}
      </span>
    ) : (
      part
    )
  );
}
