import type { HTMLAttributes, ReactNode } from 'react';
import { cn } from '../lib/cn';

export type IconCircleColor = 'primary' | 'orange' | 'green' | 'red' | 'neutral';
export type IconCircleTone = 'solid' | 'soft';
export type IconCircleSize = 'sm' | 'md' | 'lg';

export interface IconCircleProps extends HTMLAttributes<HTMLDivElement> {
  color?: IconCircleColor;
  tone?: IconCircleTone;
  size?: IconCircleSize;
  icon: ReactNode;
}

const solidClasses: Record<IconCircleColor, string> = {
  primary: 'bg-primary text-text-inverse',
  orange: 'bg-accent-orange text-text-inverse',
  green: 'bg-accent-green text-text-inverse',
  red: 'bg-danger text-text-inverse',
  neutral: 'bg-text-secondary text-text-inverse',
};

const softClasses: Record<IconCircleColor, string> = {
  primary: 'bg-primary-light text-primary',
  orange: 'bg-accent-orange/15 text-accent-orange',
  green: 'bg-accent-green/15 text-accent-green',
  red: 'bg-danger/15 text-danger',
  neutral: 'bg-border text-text-secondary',
};

const sizeClasses: Record<IconCircleSize, string> = {
  sm: 'w-9 h-9 [&_svg]:w-4 [&_svg]:h-4',
  md: 'w-12 h-12 [&_svg]:w-5 [&_svg]:h-5',
  lg: 'w-16 h-16 [&_svg]:w-7 [&_svg]:h-7',
};

/**
 * The colored circular icon used for ride/delivery categories (solid tone -
 * colored fill, white icon) and for lighter contexts like wallet quick
 * actions or safety checkmarks (soft tone - tinted fill, colored icon).
 */
export function IconCircle({
  color = 'primary',
  tone = 'solid',
  size = 'md',
  icon,
  className,
  ...props
}: IconCircleProps) {
  return (
    <div
      className={cn(
        'inline-flex items-center justify-center rounded-full shrink-0',
        (tone === 'solid' ? solidClasses : softClasses)[color],
        sizeClasses[size],
        className
      )}
      {...props}
    >
      {icon}
    </div>
  );
}
