import { ChevronRight, X } from 'lucide-react';
import { Overlay, useTranslation } from '@sheout/design-system';
import { useCloseOnBack } from '../../lib/useGoBack';

export type ServiceEntry = { key: string; label: string; body: string; art: string; tint: string; onOpen: () => void };

/**
 * Every SheOut service in one place - Bike Taxi, Parcel Delivery and the
 * Marketplace - opened from "All services" beside the Services heading.
 * Home itself shows only the two ride services; this is where the rest
 * are listed alongside them, and where a new one goes when there is one.
 * <p>
 * A sheet rising from the bottom; the rows arrive one after another. Back,
 * the close button or a tap outside all close it.
 */
export function AllServicesSheet({ open, onClose, services }: { open: boolean; onClose: () => void; services: ServiceEntry[] }) {
  const { t } = useTranslation();
  useCloseOnBack(open, onClose);

  return (
    <Overlay open={open} label={t('home.allServices')} onDismiss={onClose} align="sheet">
      <div
        className="w-full max-w-md rounded-t-[1.75rem] bg-background px-screen pb-[max(1.5rem,env(safe-area-inset-bottom))] pt-3 shadow-overlay motion-safe:animate-sheet-up"
        onClick={(e) => e.stopPropagation()}
        data-testid="all-services-sheet"
      >
        <span className="mx-auto mb-3 block h-1 w-10 rounded-full bg-border" aria-hidden="true" />
        <div className="mb-4 flex items-center justify-between gap-3">
          <h2 className="font-heading text-section text-text-primary">{t('home.allServices')}</h2>
          <button
            type="button"
            onClick={onClose}
            aria-label={t('common.close')}
            className="flex h-9 w-9 items-center justify-center rounded-full bg-surface text-text-primary shadow-lift"
          >
            <X className="h-5 w-5" />
          </button>
        </div>
        <ul className="space-y-3">
          {services.map((service, i) => (
            <li key={service.key} className="motion-safe:animate-fade-slide-in" style={{ animationDelay: `${120 + i * 80}ms` }}>
              <button
                type="button"
                onClick={service.onOpen}
                className={`group flex w-full items-center gap-4 rounded-[1.5rem] ${service.tint} p-3 pr-4 text-left shadow-lift transition-transform duration-100 motion-safe:active:scale-[0.98]`}
                data-testid={`all-services-${service.key}`}
              >
                <img
                  src={service.art}
                  alt=""
                  aria-hidden="true"
                  draggable={false}
                  className="h-16 w-16 shrink-0 select-none object-contain drop-shadow-[0_8px_10px_rgba(74,26,158,0.22)] transition-transform duration-300 group-hover:scale-105"
                />
                <span className="min-w-0 flex-1">
                  <span className="block font-heading text-card-title text-text-primary">{service.label}</span>
                  <span className="mt-0.5 block text-caption text-text-secondary">{service.body}</span>
                </span>
                <ChevronRight className="h-5 w-5 shrink-0 text-text-secondary transition-transform duration-200 group-hover:translate-x-0.5" aria-hidden="true" />
              </button>
            </li>
          ))}
        </ul>
      </div>
    </Overlay>
  );
}
