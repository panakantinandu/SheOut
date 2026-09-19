import { useState, type ReactNode } from 'react';
import { Button } from './Button';
import { Card } from './Card';
import { SelectField, type SelectOption } from './SelectField';
import { TextField } from './TextField';
import {
  SUPPORT_SUBJECT_MAX,
  SUPPORT_TEXT_MAX,
  supportCategoryOptions,
  type SupportTicketCategory,
} from '../lib/support';
import { useTranslation } from 'react-i18next';

export interface RaiseIssueValues {
  category: SupportTicketCategory;
  subject: string;
  description: string;
  linkedBookingId?: string;
}

export interface RaiseIssueFormProps {
  audience: 'customer' | 'driver';
  /** The person's own recent trips, already labelled. Empty hides the picker. */
  bookingOptions: SelectOption[];
  /**
   * Shown when Safety concern is chosen. Each app says what "right now" means
   * for its own users - the rider app has an SOS screen, the partner app
   * does not - so the words come from the app, not from here.
   */
  safetyNotice: ReactNode;
  submitting?: boolean;
  error?: string | null;
  onSubmit: (values: RaiseIssueValues) => void;
}

/**
 * Raising an issue: what it is about, a subject, what happened, and
 * optionally which trip.
 * <p>
 * No priority field. The server sets it from the category, so a safety
 * concern is always urgent and nothing else can make itself so.
 * <p>
 * The safety notice appears the moment Safety concern is picked, before
 * anything is typed. A ticket is read when someone reaches it; somebody who
 * is unsafe now must not spend a minute writing into a queue first.
 */
export function RaiseIssueForm({
  audience,
  bookingOptions,
  safetyNotice,
  submitting = false,
  error = null,
  onSubmit,
}: RaiseIssueFormProps) {
  const { t } = useTranslation('ds');
  const [category, setCategory] = useState<SupportTicketCategory | ''>('');
  const [subject, setSubject] = useState('');
  const [description, setDescription] = useState('');
  const [bookingId, setBookingId] = useState('');
  const [touched, setTouched] = useState(false);

  const missing = !category ? t('raiseIssue.missingCategory')
    : !subject.trim() ? t('raiseIssue.missingSubject')
    : !description.trim() ? t('raiseIssue.missingDescription')
    : null;

  return (
    <form
      className="space-y-4"
      onSubmit={(e) => {
        e.preventDefault();
        setTouched(true);
        if (missing || !category) return;
        onSubmit({
          category,
          subject: subject.trim(),
          description: description.trim(),
          linkedBookingId: bookingId || undefined,
        });
      }}
    >
      <SelectField
        label={t('raiseIssue.category')}
        name="category"
        placeholder={t('raiseIssue.choose')}
        value={category}
        onChange={(e) => setCategory(e.target.value as SupportTicketCategory | '')}
        options={supportCategoryOptions(audience)}
      />

      {category === 'SAFETY_CONCERN' && (
        <Card tone="brand" className="text-sm text-text-primary">
          {safetyNotice}
        </Card>
      )}

      <TextField
        label={t('raiseIssue.subject')}
        name="subject"
        placeholder={t('raiseIssue.subjectPlaceholder')}
        maxLength={SUPPORT_SUBJECT_MAX}
        value={subject}
        onChange={(e) => setSubject(e.target.value)}
      />

      <label className="block" htmlFor="description">
        <span className="mb-1.5 block text-sm font-medium text-text-primary">{t('raiseIssue.description')}</span>
        <textarea
          id="description"
          name="description"
          rows={5}
          maxLength={SUPPORT_TEXT_MAX}
          placeholder={t('raiseIssue.descriptionPlaceholder')}
          className="w-full resize-none rounded-input border border-border bg-surface px-4 py-3 text-sm text-text-primary outline-none transition-colors placeholder:text-text-secondary focus:border-primary"
          value={description}
          onChange={(e) => setDescription(e.target.value)}
        />
        <span className="mt-1 block text-right text-xs text-text-secondary">
          {description.length}/{SUPPORT_TEXT_MAX}
        </span>
      </label>

      {bookingOptions.length > 0 && (
        <SelectField
          label={t('raiseIssue.trip')}
          name="linkedBookingId"
          placeholder={t('raiseIssue.noTrip')}
          value={bookingId}
          onChange={(e) => setBookingId(e.target.value)}
          options={bookingOptions}
        />
      )}

      {touched && missing && <p className="text-sm text-danger">{missing}</p>}
      {error && <p className="text-sm text-danger">{error}</p>}

      <Button type="submit" fullWidth disabled={submitting}>
        {submitting ? t('common.sending') : t('raiseIssue.submit')}
      </Button>
    </form>
  );
}
