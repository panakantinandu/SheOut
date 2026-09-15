import { Camera } from 'lucide-react';
import { useRef, useState, type FormEvent, type ReactNode } from 'react';
import { dateOfBirthProblem, emailProblem, latestAdultBirthDate } from '../lib/profile';
import { Avatar } from './Avatar';
import { Button } from './Button';
import { TextField } from './TextField';

export interface ProfileBasics {
  name: string;
  /** YYYY-MM-DD, or empty. */
  dateOfBirth: string;
  email: string;
}

export interface ProfileCompletionFormProps {
  initial: ProfileBasics;
  photoUrl: string | null;
  hasPhoto: boolean;
  /** Uploads straight away, so the photo is on file before the profile is saved. Rejects with a message. */
  onUploadPhoto: (file: File) => Promise<void>;
  /** Saves; rejects with a message the form shows. */
  onSubmit: (values: ProfileBasics) => Promise<void>;
  submitLabel: string;
  /** Why the photo matters, in this app's words. */
  photoReason: string;
  /** Anything else this app asks for, e.g. a partner's vehicle. */
  children?: ReactNode;
  /** True when the extra fields in children are filled in. */
  extraFieldsValid?: boolean;
}

/**
 * Name, date of birth, photo and optional email - what both apps need before
 * someone can use SheOut. Used on the first-run completion screen and on the
 * profile edit screens, so the rules cannot differ between them.
 */
export function ProfileCompletionForm({
  initial,
  photoUrl,
  hasPhoto,
  onUploadPhoto,
  onSubmit,
  submitLabel,
  photoReason,
  children,
  extraFieldsValid = true,
}: ProfileCompletionFormProps) {
  const [values, setValues] = useState<ProfileBasics>(initial);
  const [touched, setTouched] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [photoError, setPhotoError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [preview, setPreview] = useState<string | null>(null);
  const fileInput = useRef<HTMLInputElement>(null);

  const nameProblem = values.name.trim() ? null : 'Enter your name.';
  const dobProblem = dateOfBirthProblem(values.dateOfBirth);
  const mailProblem = emailProblem(values.email);
  const photoOnFile = hasPhoto || preview !== null;

  async function choosePhoto(file: File | undefined) {
    if (!file) return;
    setPhotoError(null);
    if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type)) {
      setPhotoError('Choose a JPEG, PNG or WebP photo.');
      return;
    }
    setUploading(true);
    const localUrl = URL.createObjectURL(file);
    try {
      await onUploadPhoto(file);
      setPreview(localUrl);
    } catch (err) {
      URL.revokeObjectURL(localUrl);
      setPhotoError(err instanceof Error ? err.message : 'Could not upload that photo. Please try again.');
    } finally {
      setUploading(false);
      if (fileInput.current) fileInput.current.value = '';
    }
  }

  async function submit(e: FormEvent) {
    e.preventDefault();
    setTouched(true);
    setError(null);
    if (!photoOnFile) {
      setPhotoError('Add a profile photo to continue.');
      return;
    }
    if (nameProblem || dobProblem || mailProblem || !extraFieldsValid) return;
    setSaving(true);
    try {
      await onSubmit({ name: values.name.trim(), dateOfBirth: values.dateOfBirth, email: values.email.trim() });
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not save your profile. Please try again.');
    } finally {
      setSaving(false);
    }
  }

  return (
    <form onSubmit={submit} className="space-y-4" noValidate data-testid="profile-completion-form">
      <div className="flex flex-col items-center gap-2">
        <Avatar url={preview ?? photoUrl} name={values.name} size="xl" />
        <input
          ref={fileInput}
          type="file"
          accept="image/jpeg,image/png,image/webp"
          className="hidden"
          aria-label="Profile photo"
          data-testid="photo-input"
          onChange={(e) => choosePhoto(e.target.files?.[0])}
        />
        <Button
          type="button"
          size="md"
          variant="secondary"
          icon={<Camera className="h-4 w-4" />}
          disabled={uploading}
          onClick={() => fileInput.current?.click()}
        >
          {uploading ? 'Uploading...' : photoOnFile ? 'Change photo' : 'Add photo'}
        </Button>
        <p className="text-center text-xs text-text-secondary">{photoReason}</p>
        {photoError && <p className="text-center text-sm text-danger">{photoError}</p>}
      </div>

      <TextField
        label="Your name"
        name="name"
        autoComplete="name"
        maxLength={150}
        value={values.name}
        onChange={(e) => setValues({ ...values, name: e.target.value })}
        error={touched && nameProblem ? nameProblem : undefined}
      />
      <TextField
        label="Date of birth"
        name="dateOfBirth"
        type="date"
        autoComplete="bday"
        max={latestAdultBirthDate()}
        value={values.dateOfBirth}
        onChange={(e) => setValues({ ...values, dateOfBirth: e.target.value })}
        error={touched && dobProblem ? dobProblem : undefined}
      />
      <TextField
        label="Email (optional)"
        name="email"
        type="email"
        autoComplete="email"
        maxLength={254}
        placeholder="For receipts and support replies"
        value={values.email}
        onChange={(e) => setValues({ ...values, email: e.target.value })}
        error={touched && mailProblem ? mailProblem : undefined}
      />
      {children}
      <p className="text-xs text-text-secondary">SheOut is only for people aged 18 and over.</p>
      {error && <p className="text-sm text-danger" role="alert">{error}</p>}
      <Button type="submit" fullWidth disabled={saving || uploading}>
        {saving ? 'Saving...' : submitLabel}
      </Button>
    </form>
  );
}
