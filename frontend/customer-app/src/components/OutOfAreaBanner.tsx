import { Globe2 } from 'lucide-react';
import { useEffect, useState } from 'react';
import { Card, IconCircle } from '@sheout/design-system';
import { SERVICE_CENTRE_NAME, SERVICE_RADIUS_KM, currentPosition, isInServiceArea } from '../lib/geocode';
import { useTranslation } from '@sheout/design-system';

/**
 * Tells a customer who is physically outside the service area, up front,
 * before she picks anything.
 * <p>
 * DELIBERATELY A NOTICE AND NOT A BLOCK, and the distinction matters. A
 * customer's device position is not what decides whether a trip is allowed -
 * the PICKUP and DROP are, and the backend gates on those. Somebody in
 * another country booking a ride for her mother in Hyderabad is a real
 * customer with a real trip, and refusing her because of where her phone is
 * would be refusing business for no reason.
 * <p>
 * Contrast the driver app, where the phone's position IS the rule: a partner
 * is the vehicle, so one standing in another country cannot collect anybody
 * here, and going online is refused outright.
 * <p>
 * What this fixes is the silence. Opening the app from abroad used to look
 * completely normal, and the first hint was an out-of-area warning after
 * choosing a pickup - by which point she had done work for nothing.
 * <p>
 * Says nothing at all when the position is unknown or permission is refused.
 * Not knowing where somebody is is not grounds for telling her she is in the
 * wrong place.
 */
export function OutOfAreaBanner() {
  const { t } = useTranslation();
  const [outside, setOutside] = useState(false);

  useEffect(() => {
    let cancelled = false;
    currentPosition()
      .then((here) => {
        if (!cancelled) setOutside(!isInServiceArea(here));
      })
      .catch(() => {
        // Permission refused, unavailable, or timed out. Stay quiet.
      });
    return () => {
      cancelled = true;
    };
  }, []);

  if (!outside) return null;

  return (
    <Card tone="warning" className="flex items-start gap-3">
      <IconCircle color="orange" tone="soft" icon={<Globe2 />} />
      <div className="min-w-0 flex-1">
        <p className="font-heading font-semibold text-text-primary">
          {t('serviceArea.youOutside')}
        </p>
        <p className="mt-0.5 text-sm text-text-secondary">
          {t('serviceArea.youOutsideBody', { city: SERVICE_CENTRE_NAME, km: SERVICE_RADIUS_KM })}
        </p>
      </div>
    </Card>
  );
}
