import { useNavigate } from 'react-router-dom';
import { DELETE_ACCOUNT_STEP, DeleteAccountInfo } from '@sheout/design-system';
import { useAuth } from '../auth/AuthContext';

/** The public Delete your account page: what deleting does, then sign in (the identity check) and the delete step. */
export function DeleteAccountPage() {
  const navigate = useNavigate();
  const { isAuthenticated } = useAuth();
  return (
    <DeleteAccountInfo
      audience="customer"
      signedIn={isAuthenticated}
      onContinue={() =>
        isAuthenticated ? navigate(DELETE_ACCOUNT_STEP) : navigate('/login', { state: { next: DELETE_ACCOUNT_STEP } })
      }
    />
  );
}
