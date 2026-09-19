import { Plus, Trash2, UserRound } from 'lucide-react';
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Button,
  Card,
  ConfirmDialog,
  IconCircle,
  PhoneField,
  TextField,
  TopHeader,
  isCompletePhone,
  toE164,
} from '@sheout/design-system';
import { ApiError, usersApi } from '../api/client';
import type { EmergencyContact } from '../api/types';
import { SafetyText, useSafetyString, useTranslation } from '@sheout/design-system';

const RELATIONSHIPS = ['Mother', 'Father', 'Sister', 'Brother', 'Partner', 'Friend'];

/**
 * Add, see and remove the people an SOS alerts.
 * <p>
 * This screen did not exist. The backend had all three endpoints - list,
 * add, remove - and the app only ever called list, so there was no way for
 * a customer to add a contact at all. The SOS screen told them "add one in
 * your profile", pointing at a screen that was not there, and the SOS
 * feature that the whole product is built around could never be set up by
 * the person relying on it. This is the gap that mattered most of anything
 * found in this pass.
 * <p>
 * Phone numbers go through the shared PhoneField, so a contact's number is
 * validated the same way a login number is - a mistyped digit here means an
 * alert going to a stranger, or to nobody.
 */
export function EmergencyContacts() {
  const { t } = useTranslation();
  const safety = useSafetyString();
  const navigate = useNavigate();
  const [contacts, setContacts] = useState<EmergencyContact[] | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [adding, setAdding] = useState(false);
  const [name, setName] = useState('');
  const [digits, setDigits] = useState('');
  const [relationship, setRelationship] = useState(RELATIONSHIPS[0]);
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [removing, setRemoving] = useState<EmergencyContact | null>(null);

  function load() {
    setLoadError(null);
    usersApi
      .getMyEmergencyContacts()
      .then(setContacts)
      .catch((err) => setLoadError(err instanceof ApiError ? err.message : t('contacts.loadError')));
  }

  useEffect(load, []);

  async function handleAdd() {
    setFormError(null);
    if (!name.trim()) {
      setFormError(t('contacts.nameRequired'));
      return;
    }
    if (!isCompletePhone(digits)) {
      setFormError(t('contacts.phoneRequired'));
      return;
    }
    setSaving(true);
    try {
      const created = await usersApi.addEmergencyContact({
        name: name.trim(),
        phoneNumber: toE164(digits),
        relationship,
      });
      setContacts((prev) => [...(prev ?? []), created]);
      setName('');
      setDigits('');
      setRelationship(RELATIONSHIPS[0]);
      setAdding(false);
    } catch (err) {
      setFormError(err instanceof ApiError ? err.message : t('contacts.saveError'));
    } finally {
      setSaving(false);
    }
  }

  async function handleRemove() {
    if (!removing) return;
    const target = removing;
    setRemoving(null);
    try {
      await usersApi.removeEmergencyContact(target.id);
      setContacts((prev) => (prev ?? []).filter((c) => c.id !== target.id));
    } catch (err) {
      setLoadError(err instanceof ApiError ? err.message : t('contacts.removeError'));
    }
  }

  return (
    <div className="space-y-6">
      <TopHeader variant="back" title={t('profile.emergencyContacts')} onBack={() => navigate(-1)} />

      <Card className="space-y-2">
        <p className="text-sm text-text-primary">
          <SafetyText k="contacts.whatSosDoes" />
        </p>
        <p className="text-xs text-text-secondary">
          <SafetyText k="contacts.tellThem" />
        </p>
      </Card>

      {loadError && <p className="text-sm text-danger">{loadError}</p>}

      {contacts === null && !loadError ? (
        <p className="text-center text-sm text-text-secondary">{t('contacts.loading')}</p>
      ) : contacts && contacts.length === 0 ? (
        <Card tone="warning" className="space-y-1 text-center">
          <p className="font-heading font-semibold text-text-primary">{t('contacts.emptyTitle')}</p>
          <p className="text-sm text-text-secondary">
            <SafetyText k="contacts.emptyWarning" />
          </p>
        </Card>
      ) : (
        <Card className="divide-y divide-border p-0">
          {(contacts ?? []).map((contact) => (
            <div key={contact.id} className="flex items-center gap-3 p-4">
              <IconCircle tone="soft" size="sm" icon={<UserRound />} />
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm font-medium text-text-primary">{contact.name}</p>
                <p className="truncate text-xs text-text-secondary">
                  {t(`contacts.rel.${contact.relationship}`, { defaultValue: contact.relationship })} &middot; {contact.phoneNumber}
                </p>
              </div>
              <button
                type="button"
                aria-label={t('contacts.removeAria', { name: contact.name })}
                onClick={() => setRemoving(contact)}
                className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full text-danger hover:bg-danger/10"
              >
                <Trash2 className="h-4 w-4" />
              </button>
            </div>
          ))}
        </Card>
      )}

      {adding ? (
        <Card className="space-y-4">
          <p className="font-heading font-semibold text-text-primary">{t('contacts.add')}</p>
          <TextField
            label={t('contacts.theirName')}
            placeholder={t('contacts.namePlaceholder')}
            value={name}
            onChange={(e) => setName(e.target.value)}
          />
          <div>
            <span className="mb-1.5 block text-sm font-medium text-text-primary">{t('contacts.theirPhone')}</span>
            <PhoneField value={digits} onChange={setDigits} placeholder={t('login.phonePlaceholder')} />
          </div>
          <div>
            <span className="mb-1.5 block text-sm font-medium text-text-primary">{t('contacts.relationship')}</span>
            <div className="flex flex-wrap gap-2">
              {RELATIONSHIPS.map((option) => (
                <button
                  key={option}
                  type="button"
                  onClick={() => setRelationship(option)}
                  className={
                    relationship === option
                      ? 'rounded-full bg-primary px-3 py-1.5 text-sm font-semibold text-text-inverse'
                      : 'rounded-full border border-border px-3 py-1.5 text-sm font-medium text-text-secondary'
                  }
                >
                  {t(`contacts.rel.${option}`)}
                </button>
              ))}
            </div>
          </div>
          {formError && <p className="text-sm text-danger">{formError}</p>}
          <div className="flex gap-3">
            <Button
              variant="secondary"
              fullWidth
              disabled={saving}
              onClick={() => {
                setAdding(false);
                setFormError(null);
              }}
            >
              {t('common.cancel')}
            </Button>
            <Button fullWidth disabled={saving} onClick={handleAdd}>
              {saving ? t('common.saving') : t('contacts.save')}
            </Button>
          </div>
        </Card>
      ) : (
        <Button fullWidth icon={<Plus className="h-4 w-4" />} onClick={() => setAdding(true)}>
          {t('contacts.add')}
        </Button>
      )}

      <ConfirmDialog
        open={removing !== null}
        title={t('contacts.removeTitle')}
        message={
          removing
            ? safety('contacts.removeWarning', { name: removing.name })
            : ''
        }
        confirmLabel={t('contacts.remove')}
        destructive
        onConfirm={handleRemove}
        onCancel={() => setRemoving(null)}
      />
    </div>
  );
}
