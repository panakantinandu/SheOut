/**
 * Three stylised women for the "Women Supporting Women" card.
 * <p>
 * FLAGGED AS AN ADAPTATION, NOT A MATCH: the mockup shows a detailed
 * illustration of three women. No such asset exists in this project - the
 * only illustration available is the single rider used by the brand
 * lockup - so rather than leave the card empty or ship a stock image that
 * does not match the brand, this draws the same idea as flat shapes in the
 * existing palette. It reads as three figures at card size; it is not a
 * reproduction of the mockup's artwork. Swap it for the real asset when
 * one exists, without touching the card around it.
 */
export function ThreeWomen({ className }: { className?: string }) {
  // Back, front-left, front-right. Drawn back-to-front so the overlap
  // reads as depth rather than as flat cut-outs.
  const figures = [
    { cx: 50, skin: '#F1C9A5', hair: '#3B2A2F', top: '#E8734A', scale: 0.94, y: 6 },
    { cx: 26, skin: '#EFC29C', hair: '#2B1F24', top: '#7B3FE4', scale: 1, y: 10 },
    { cx: 74, skin: '#E8B58C', hair: '#2B1F24', top: '#E8734A', scale: 1, y: 10 },
  ];

  return (
    <svg viewBox="0 0 100 64" className={className} role="img" aria-label="Three women standing together">
      {figures.map((f, i) => (
        <g key={i} transform={`translate(${f.cx} ${f.y}) scale(${f.scale})`}>
          {/* shoulders / torso */}
          <path d="M-15 46 C-15 32 -8 26 0 26 C8 26 15 32 15 46 Z" fill={f.top} />
          {/* neck */}
          <rect x="-4" y="18" width="8" height="10" rx="4" fill={f.skin} />
          {/* head */}
          <circle cx="0" cy="10" r="10" fill={f.skin} />
          {/* hair */}
          <path d="M-11 10 A11 11 0 0 1 11 10 L11 14 C11 6 -11 6 -11 14 Z" fill={f.hair} />
          <path d="M-11 10 C-14 20 -12 26 -10 28 L-8 16 Z" fill={f.hair} />
          <path d="M11 10 C14 20 12 26 10 28 L8 16 Z" fill={f.hair} />
        </g>
      ))}
    </svg>
  );
}
