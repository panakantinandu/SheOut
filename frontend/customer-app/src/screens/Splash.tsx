import { Bike, Package, UtensilsCrossed } from 'lucide-react';
import { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { IconCircle, ListRow } from '@sheout/design-system';
import { useAuth } from '../auth/AuthContext';

const SPLASH_DELAY_MS = 1500;

/**
 * Static per the brief - no interactive elements. Auto-advances to
 * /home or /login after a short delay, standard splash-screen behavior
 * (not asked for explicitly, but a splash screen that never proceeds
 * isn't useful - flagging the timing choice as mine, not from the spec).
 */
export function Splash() {
  const navigate = useNavigate();
  const { isAuthenticated } = useAuth();

  useEffect(() => {
    const timer = setTimeout(() => {
      navigate(isAuthenticated ? '/home' : '/login', { replace: true });
    }, SPLASH_DELAY_MS);
    return () => clearTimeout(timer);
  }, [navigate, isAuthenticated]);

  return (
    <div className="flex min-h-screen flex-col items-center justify-between bg-background px-screen py-16">
      <div className="flex flex-1 flex-col items-center justify-center gap-4">
        <img src="/Logo.jpeg" alt="SheOut" className="h-28 w-28 rounded-card object-cover shadow-card" />
        <h1 className="font-heading text-3xl font-extrabold text-primary">SHEOUT</h1>
        <p className="text-sm font-medium text-text-secondary">Your Delivery, Our Priority</p>
      </div>

      <div className="flex w-full justify-around">
        <ListRow layout="stacked" icon={<IconCircle icon={<Bike />} />} label="Bike Taxi" />
        <ListRow layout="stacked" icon={<IconCircle color="orange" icon={<Package />} />} label="Parcel Delivery" />
        <ListRow layout="stacked" icon={<IconCircle color="green" icon={<UtensilsCrossed />} />} label="Lunch Box Delivery" />
      </div>

      <p className="mt-8 text-xs font-medium uppercase tracking-wide text-text-secondary">
        Safe &middot; Fast &middot; Women Focused
      </p>
    </div>
  );
}
