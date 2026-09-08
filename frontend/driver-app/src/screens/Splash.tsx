import { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';

const SPLASH_DELAY_MS = 1500;

/** Auto-advances to /home or /login after a short delay - same pattern as customer-app's Splash. */
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
    <div className="flex min-h-screen flex-col items-center justify-center gap-4 bg-background px-screen">
      <img src="/Logo.jpeg" alt="SheOut" className="h-28 w-28 rounded-card object-cover shadow-card" />
      <h1 className="font-heading text-3xl font-extrabold text-primary">SHEOUT DRIVER</h1>
      <p className="text-sm font-medium text-text-secondary">Drive with confidence</p>
    </div>
  );
}
