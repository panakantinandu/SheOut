import { SampleLabel } from './SampleLabel';

/** A phone with one of the apps' real screens in it. */
export function Device({ screen, alt, className = '', eager = false }: { screen: string; alt: string; className?: string; eager?: boolean }) {
  return (
    <div className={`device relative ${className}`}>
      <SampleLabel className="left-1/2 top-4 -translate-x-1/2" />
      <div className="device-screen">
        <img src={`/screens/${screen}.webp`} alt={alt} width={618} height={1259} loading={eager ? 'eager' : 'lazy'} decoding="async" />
      </div>
    </div>
  );
}
