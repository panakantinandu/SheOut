import { useEffect, useState } from 'react';
import { usePrefersReducedMotion } from '../lib/motion';

/**
 * A line that changes every few seconds, each new one rising softly into
 * place - for the greeting's helpful line under "Good morning". Under
 * reduced motion, or with a single line, it simply shows the first.
 * Announced once, not on every change.
 */
export function RotatingText({ lines, intervalMs = 5000, className = '' }: { lines: string[]; intervalMs?: number; className?: string }) {
  const reduced = usePrefersReducedMotion();
  const [index, setIndex] = useState(0);

  useEffect(() => {
    setIndex(0);
    if (reduced || lines.length < 2) return;
    const timer = window.setInterval(() => setIndex((i) => (i + 1) % lines.length), intervalMs);
    return () => window.clearInterval(timer);
  }, [reduced, lines.length, intervalMs]);

  const line = lines[index] ?? lines[0] ?? '';
  return (
    <span className={`block ${className}`} aria-live="off" data-testid="rotating-text">
      <span key={index} className="block motion-safe:animate-fade-slide-in">
        {line}
      </span>
    </span>
  );
}
