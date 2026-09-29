import { useEffect, useState } from 'react';

/** The part of the day, for the greeting at the top of Home in both apps. */
export type DayPart = 'morning' | 'afternoon' | 'evening' | 'night';

/** 5-12 morning, 12-17 afternoon, 17-21 evening, the rest night - by the phone's own clock. */
export function dayPartOf(date: Date): DayPart {
  const h = date.getHours();
  if (h >= 5 && h < 12) return 'morning';
  if (h >= 12 && h < 17) return 'afternoon';
  if (h >= 17 && h < 21) return 'evening';
  return 'night';
}

/** The part of the day now, kept current if the app stays open across a boundary. */
export function useDayPart(): DayPart {
  const [part, setPart] = useState<DayPart>(() => dayPartOf(new Date()));
  useEffect(() => {
    const timer = window.setInterval(() => setPart(dayPartOf(new Date())), 60_000);
    return () => window.clearInterval(timer);
  }, []);
  return part;
}
