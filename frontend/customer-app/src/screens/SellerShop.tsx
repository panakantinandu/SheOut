import {
  AlertTriangle,
  BadgeCheck,
  CheckCircle2,
  ChevronRight,
  Circle,
  Clock,
  ImageOff,
  IndianRupee,
  Globe,
  Info,
  MapPin,
  PackagePlus,
  PauseCircle,
  Pencil,
  Store,
  Wallet,
} from 'lucide-react';
import { useEffect, useMemo, useState, type ReactNode } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import {
  Button,
  Card,
  SelectField,
  SkeletonCard,
  StatusBadge,
  Stepper,
  SuccessCheck,
  TextField,
  TopHeader,
  showToast,
  useTranslation,
} from '@sheout/design-system';
import { ApiError, marketplaceApi, usersApi, walletApi } from '../api/client';
import type { SellerCategory, SellerDetailsInput, SellerShop as Shop } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { apiErrorText } from '../lib/apiErrors';
import { openRazorpayCheckout } from '../lib/razorpayCheckout';
import { SELLER_CATEGORIES, SELLER_STATUS_TONE, categoryKey, priceText, websiteLabel } from '../lib/seller';
import { clearWizardDraft, readWizardDraft, writeWizardDraft, type WizardDraft } from '../lib/sellerWizardDraft';

/**
 * Sell on SheOut: her own shop, from applying to live.
 * <p>
 * Until it is sent for review - a new application, a draft, or one sent
 * back with changes to make - this is the four-step Seller Registration
 * wizard. Once sent, it is a plain status screen that says what happens
 * next and offers the one action that fits: wait, pay the listing fee, run
 * the live shop, or put right what was sent back. Review is free; the fee is
 * asked for only after a person has approved the shop.
 */
export function SellerShop() {
  const { t } = useTranslation();
  const [shop, setShop] = useState<Shop | null | undefined>(undefined);
  const [loadError, setLoadError] = useState<string | null>(null);
  /** A shop sent back (REJECTED) is shown as a status first; "Make changes" opens the wizard on it. */
  const [fixing, setFixing] = useState(false);
  const goBack = useGoBack();

  const load = () =>
    marketplaceApi
      .myShop()
      .then(setShop)
      .catch(() => setLoadError(t('seller.shop.loadError')));
  useEffect(() => {
    load();
  }, []);

  if (shop === undefined) {
    return (
      <div className="space-y-6 pb-6">
        <TopHeader variant="back" title={t('seller.wizard.title')} onBack={goBack} />
        {loadError ? <p className="text-sm text-danger">{loadError}</p> : <SkeletonCard lines={4} label={t('seller.shop.loading')} />}
      </div>
    );
  }

  const inWizard = shop === null || shop.status === 'DRAFT' || (shop.status === 'REJECTED' && fixing);
  if (inWizard) {
    return <SellerWizard shop={shop} onShop={setShop} onSubmitted={() => setFixing(false)} />;
  }
  return <ShopStatusScreen shop={shop} onShop={setShop} reload={load} onFix={() => setFixing(true)} />;
}

/** Back to wherever she came from - Home if this page was opened directly. */
function useGoBack() {
  const navigate = useNavigate();
  const location = useLocation();
  return () => (location.key === 'default' ? navigate('/home') : navigate(-1));
}

// ================================================================ the wizard

const STEP_COUNT = 4;
const digits = (v: string) => v.replace(/\D/g, '').replace(/^91(?=\d{10}$)/, '').replace(/^0(?=\d{10}$)/, '');
const mobileOk = (v: string) => /^[6-9]\d{9}$/.test(digits(v));

function hasPhotographedProduct(shop: Shop | null): boolean {
  return !!shop && shop.products.some((p) => p.active && p.images.length > 0);
}

/**
 * The furthest step her saved shop lets her stand on. No shop yet: the
 * details step (the category is picked on this phone before anything is
 * saved). A shop with no photographed product: the products step, since
 * review needs one. Otherwise: review.
 */
function furthestStep(shop: Shop | null, category: SellerCategory | undefined): number {
  if (!shop) return category ? 1 : 0;
  return hasPhotographedProduct(shop) ? 3 : 2;
}

/**
 * Seller Registration: category, business details, products, review.
 * <p>
 * Nothing here is new data - it is the same shop, fields, product editor,
 * photo caps and review rule as before, laid out one decision at a time.
 * The details are saved (her shop created, the first time) when she leaves
 * Step 2; products save as she adds them; Step 4 sends it for review.
 * <p>
 * Going back never loses anything: changing the category on Step 1 keeps
 * the details and products, and Step 2's fields are only written to the
 * server together, on Continue. Whatever is on screen is also kept on this
 * phone (see sellerWizardDraft), so a refresh, a trip to the product editor,
 * or coming back tomorrow resumes on the right step with her words in it.
 */
function SellerWizard({ shop, onShop, onSubmitted }: { shop: Shop | null; onShop: (s: Shop) => void; onSubmitted: () => void }) {
  const { t } = useTranslation();
  const { accountId } = useAuth();
  const goBack = useGoBack();

  // Her saved shop first, then anything typed here and not yet saved.
  const initial = useMemo<WizardDraft>(() => {
    const stored = readWizardDraft(accountId);
    const fromShop: WizardDraft = shop
      ? {
          step: 0,
          category: shop.category,
          businessName: shop.businessName,
          contactPhone: shop.contactPhone,
          whatsappNumber: shop.whatsappNumber ?? '',
          area: shop.area ?? '',
          websiteUrl: shop.websiteUrl ?? '',
        }
      : { step: 0 };
    const merged: WizardDraft = !shop || stored?.dirty ? { ...fromShop, ...stored, step: 0 } : { ...fromShop, step: 0 };
    const furthest = furthestStep(shop, merged.category);
    // A sent-back shop opens on review, where every part has its own Edit.
    const wanted = shop?.status === 'REJECTED' && !stored ? 3 : stored?.step ?? furthest;
    return { ...merged, step: Math.min(wanted, furthest), dirty: !!stored?.dirty };
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  const [draft, setDraft] = useState<WizardDraft>(initial);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    writeWizardDraft(accountId, draft);
  }, [accountId, draft]);

  const step = draft.step;
  const update = (patch: Partial<WizardDraft>) => setDraft((d) => ({ ...d, ...patch, dirty: true }));
  const goTo = (next: number) => {
    setError(null);
    setDraft((d) => ({ ...d, step: next }));
    window.scrollTo(0, 0);
  };

  const stepNames = [t('seller.wizard.steps.category'), t('seller.wizard.steps.details'), t('seller.wizard.steps.products'), t('seller.wizard.steps.review')];

  async function saveDetails(details: SellerDetailsInput) {
    setBusy(true);
    setError(null);
    try {
      const saved = shop ? await marketplaceApi.updateShop(details) : await marketplaceApi.apply(details);
      if (!shop) showToast(t('seller.shop.applied'));
      onShop(saved);
      setDraft((d) => ({ ...d, dirty: false, step: 2 }));
      window.scrollTo(0, 0);
    } catch (err) {
      setError(apiErrorText(err, 'seller.shop.actionError'));
    } finally {
      setBusy(false);
    }
  }

  async function submit() {
    setBusy(true);
    setError(null);
    try {
      const sent = await marketplaceApi.submit();
      clearWizardDraft(accountId);
      showToast(t('seller.shop.submitted'));
      onShop(sent);
      onSubmitted();
      window.scrollTo(0, 0);
    } catch (err) {
      setError(apiErrorText(err, 'seller.shop.actionError'));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="space-y-6 pb-6" data-testid="seller-wizard" data-step={step + 1}>
      <div className="space-y-4">
        <TopHeader variant="back" title={t('seller.wizard.title')} onBack={() => (step > 0 ? goTo(step - 1) : goBack())} />
        <div className="space-y-2">
          <Stepper steps={stepNames} current={step} onStepClick={goTo} />
          <p className="text-caption font-semibold text-text-secondary" aria-hidden="true">
            {t('seller.wizard.stepOf', { current: step + 1, total: STEP_COUNT, name: stepNames[step] })}
          </p>
        </div>
      </div>

      {step === 0 && (
        <CategoryStep
          selected={draft.category}
          onPick={(category) => {
            setDraft((d) => ({ ...d, category, dirty: d.dirty || category !== shop?.category, step: 1 }));
            window.scrollTo(0, 0);
          }}
        />
      )}
      {step === 1 && draft.category && (
        <DetailsStep draft={draft} isNew={!shop} busy={busy} onChange={update} onContinue={saveDetails} />
      )}
      {step === 1 && !draft.category && (
        // Only reachable through a stale draft: the category comes first.
        <Button className="w-full" onClick={() => goTo(0)}>
          {t('seller.wizard.category.title')}
        </Button>
      )}
      {step === 2 && shop && <ProductsStep shop={shop} onContinue={() => goTo(3)} />}
      {step === 3 && shop && <ReviewStep shop={shop} busy={busy} onEdit={goTo} onSubmit={submit} />}

      {error && <p className="text-sm text-danger" role="alert">{error}</p>}
      {/* Step 2 of a new shop already shows this notice in full, beside its consent box. */}
      {!(step === 1 && !shop) && <p className="text-center text-caption text-text-secondary">{t('seller.legal.applicationNotice')}</p>}
    </div>
  );
}

function StepIntro({ title, subtitle }: { title: string; subtitle: string }) {
  return (
    <div>
      <h1 className="font-heading text-title text-text-primary">{title}</h1>
      <p className="mt-1 text-sm text-text-secondary">{subtitle}</p>
    </div>
  );
}

// ---------------------------------------------------------------- step 1

/**
 * The six categories as large picture cards, two to a row: the picture
 * fills most of the card, its name and a chevron sit at the bottom left.
 * One tap chooses and moves on - there is nothing else on this step to do.
 */
function CategoryStep({ selected, onPick }: { selected?: SellerCategory; onPick: (c: SellerCategory) => void }) {
  const { t } = useTranslation();
  return (
    <section className="space-y-4">
      <StepIntro title={t('seller.wizard.category.title')} subtitle={t('seller.wizard.category.subtitle')} />
      <div className="grid grid-cols-2 gap-3" role="radiogroup" aria-label={t('seller.wizard.category.title')} data-testid="wizard-categories">
        {SELLER_CATEGORIES.map((c) => {
          const isSelected = selected === c.value;
          return (
            <button
              key={c.value}
              type="button"
              role="radio"
              aria-checked={isSelected}
              onClick={() => onPick(c.value)}
              className={`group relative flex aspect-[4/5] flex-col overflow-hidden rounded-card text-left shadow-card transition-transform duration-100 motion-safe:active:scale-[0.97] ${c.tint} ${
                isSelected ? 'ring-[3px] ring-primary' : 'ring-1 ring-border'
              }`}
              data-testid={`wizard-category-${c.key}`}
            >
              <span className="flex min-h-0 flex-1 items-center justify-center p-3 pb-0">
                <img src={c.art} alt="" aria-hidden="true" className="h-full w-full object-contain drop-shadow-md" />
              </span>
              <span className="flex items-center gap-1 px-3 pb-3 pt-2">
                <span className="text-sm font-semibold leading-tight text-text-primary">{t(`seller.categories.${c.key}`)}</span>
                <ChevronRight className="h-4 w-4 shrink-0 text-text-primary" aria-hidden="true" />
              </span>
              {isSelected && (
                <span className="absolute right-2 top-2 flex h-7 w-7 items-center justify-center rounded-full bg-primary text-text-inverse shadow-lift">
                  <CheckCircle2 className="h-5 w-5" aria-hidden="true" />
                </span>
              )}
            </button>
          );
        })}
      </div>
    </section>
  );
}

// ---------------------------------------------------------------- step 2

function DetailsStep({
  draft,
  isNew,
  busy,
  onChange,
  onContinue,
}: {
  draft: WizardDraft;
  isNew: boolean;
  busy: boolean;
  onChange: (patch: Partial<WizardDraft>) => void;
  onContinue: (details: SellerDetailsInput) => void;
}) {
  const { t } = useTranslation();
  const [showErrors, setShowErrors] = useState(false);
  // The one thing she must agree to before her shop exists, as before.
  const [understood, setUnderstood] = useState(!isNew);
  const values = {
    businessName: draft.businessName ?? '',
    contactPhone: draft.contactPhone ?? '',
    whatsappNumber: draft.whatsappNumber ?? '',
    area: draft.area ?? '',
    websiteUrl: draft.websiteUrl ?? '',
  };
  const valid = businessFieldsValid(values) && understood;

  return (
    <section className="space-y-4">
      <StepIntro title={t('seller.wizard.details.title')} subtitle={t('seller.wizard.details.subtitle')} />
      <form
        className="space-y-4"
        noValidate
        onSubmit={(e) => {
          e.preventDefault();
          setShowErrors(true);
          if (!valid || !draft.category) return;
          onContinue(toDetailsInput(values, draft.category));
        }}
        data-testid="shop-form"
      >
        <Card className="space-y-4">
          <BusinessFields values={values} showErrors={showErrors} onChange={onChange} />
        </Card>
        {isNew && (
          <>
            <div className="flex gap-3 rounded-input bg-accent-orange-tint px-4 py-3" role="note" data-testid="seller-legal-notice">
              <Info className="mt-0.5 h-5 w-5 shrink-0 text-accent-orange-strong" aria-hidden="true" />
              <p className="text-sm text-text-primary">{t('seller.legal.applicationNotice')}</p>
            </div>
            <label className="flex cursor-pointer items-start gap-3 text-sm text-text-primary">
              <input
                type="checkbox"
                checked={understood}
                onChange={(e) => setUnderstood(e.target.checked)}
                className="mt-0.5 h-5 w-5 shrink-0 accent-primary"
                data-testid="seller-understood"
              />
              {t('seller.apply.understood')}
            </label>
          </>
        )}
        <Button type="submit" size="lg" className="w-full" disabled={busy || (showErrors && !valid)} data-testid="wizard-continue">
          {busy ? t('seller.form.saving') : t('seller.wizard.continue')}
        </Button>
      </form>
    </section>
  );
}

interface BusinessValues {
  businessName: string;
  contactPhone: string;
  whatsappNumber: string;
  area: string;
  websiteUrl: string;
}

/**
 * The same shape the server accepts (WebsiteAddress): http or https, a real
 * domain, no spaces. "www.myshop.in" is fine - the server adds https://.
 * The server's check is the one that counts; this only says so sooner.
 */
function websiteOk(v: string): boolean {
  const value = v.trim();
  if (!value) return true;
  if (/\s/.test(value) || value.length > 200) return false;
  try {
    const url = new URL(value.includes('://') ? value : `https://${value}`);
    return (url.protocol === 'https:' || url.protocol === 'http:') && !url.username && /^([a-z0-9-]+\.)+[a-z]{2,63}$/i.test(url.hostname);
  } catch {
    return false;
  }
}

function businessFieldsValid(v: BusinessValues): boolean {
  return v.businessName.trim().length > 0 && mobileOk(v.contactPhone) && (!v.whatsappNumber.trim() || mobileOk(v.whatsappNumber)) && websiteOk(v.websiteUrl);
}

function toDetailsInput(v: BusinessValues, category: SellerCategory): SellerDetailsInput {
  return {
    businessName: v.businessName.trim(),
    category,
    contactPhone: digits(v.contactPhone),
    whatsappNumber: v.whatsappNumber.trim() ? digits(v.whatsappNumber) : undefined,
    area: v.area.trim() || undefined,
    websiteUrl: v.websiteUrl.trim() || undefined,
  };
}

/** Shop name, the numbers customers use, and her area - the fields the wizard and the live shop's Edit share. */
function BusinessFields({
  values,
  showErrors,
  onChange,
}: {
  values: BusinessValues;
  showErrors: boolean;
  onChange: (patch: Partial<BusinessValues>) => void;
}) {
  const { t } = useTranslation();
  const phoneOk = mobileOk(values.contactPhone);
  const whatsappOk = !values.whatsappNumber.trim() || mobileOk(values.whatsappNumber);
  return (
    <>
      <TextField
        label={t('seller.form.businessName')}
        value={values.businessName}
        maxLength={80}
        onChange={(e) => onChange({ businessName: e.target.value })}
        placeholder={t('seller.form.businessNamePlaceholder')}
        error={showErrors && !values.businessName.trim() ? t('seller.form.required') : undefined}
        name="businessName"
      />
      <TextField
        label={t('seller.form.contactPhone')}
        value={values.contactPhone}
        inputMode="tel"
        maxLength={16}
        onChange={(e) => onChange({ contactPhone: e.target.value })}
        placeholder="98765 43210"
        error={showErrors && !phoneOk ? t('seller.form.phoneInvalid') : undefined}
        name="contactPhone"
      />
      <div className="space-y-1">
        <TextField
          label={t('seller.form.whatsapp')}
          value={values.whatsappNumber}
          inputMode="tel"
          maxLength={16}
          onChange={(e) => onChange({ whatsappNumber: e.target.value })}
          placeholder={t('seller.form.whatsappPlaceholder')}
          error={showErrors && !whatsappOk ? t('seller.form.phoneInvalid') : undefined}
          name="whatsappNumber"
        />
        {phoneOk && !values.whatsappNumber.trim() && (
          <button type="button" className="text-sm font-semibold text-primary" onClick={() => onChange({ whatsappNumber: digits(values.contactPhone) })}>
            {t('seller.form.sameAsPhone')}
          </button>
        )}
        <p className="text-caption text-text-secondary">{t('seller.form.whatsappHelp')}</p>
      </div>
      <div className="space-y-1">
        <TextField
          label={t('seller.form.area')}
          icon={<MapPin className="h-4 w-4 shrink-0 text-text-secondary" />}
          value={values.area}
          maxLength={80}
          onChange={(e) => onChange({ area: e.target.value })}
          placeholder={t('seller.form.areaPlaceholder')}
          name="area"
        />
        <p className="text-caption text-text-secondary">{t('seller.form.areaHelp')}</p>
      </div>
      <div className="space-y-1">
        <TextField
          label={t('seller.form.website')}
          icon={<Globe className="h-4 w-4 shrink-0 text-text-secondary" />}
          value={values.websiteUrl}
          inputMode="url"
          autoCapitalize="none"
          maxLength={200}
          onChange={(e) => onChange({ websiteUrl: e.target.value })}
          placeholder={t('seller.form.websitePlaceholder')}
          error={showErrors && !websiteOk(values.websiteUrl) ? t('seller.form.websiteInvalid') : undefined}
          name="websiteUrl"
        />
        <p className="text-caption text-text-secondary">{t('seller.form.websiteHelp')}</p>
      </div>
    </>
  );
}

// ---------------------------------------------------------------- step 3

/**
 * Her products, with the existing editor for adding and changing one (title,
 * description, price, then its photos against the per-product and per-shop
 * caps). Continue waits for what review needs: one shown product with a photo.
 */
function ProductsStep({ shop, onContinue }: { shop: Shop; onContinue: () => void }) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const ready = hasPhotographedProduct(shop);
  const openEditor = (id: string) => navigate(`/seller/manage/products/${id}`, { state: { fromShop: true } });
  return (
    <section className="space-y-4">
      <div className="flex items-start justify-between gap-3">
        <StepIntro title={t('seller.wizard.products.title')} subtitle={t('seller.wizard.products.subtitle')} />
      </div>
      <p className="text-caption text-text-secondary" data-testid="photo-usage">
        {t('seller.shop.photoUsage', { used: shop.imagesUsed, max: shop.maxImagesPerSeller })}
      </p>
      <p className="flex gap-2 rounded-input bg-accent-orange-tint px-3 py-2 text-caption text-text-primary" role="note" data-testid="genuine-price-note">
        <Info className="mt-0.5 h-4 w-4 shrink-0 text-accent-orange-strong" aria-hidden="true" />
        <span>{t('seller.legal.genuinePrice')}</span>
      </p>
      {shop.products.length === 0 && (
        <Card className="flex flex-col items-center gap-2 py-8 text-center">
          <span className="flex h-14 w-14 items-center justify-center rounded-full bg-primary-light text-primary">
            <PackagePlus className="h-6 w-6" aria-hidden="true" />
          </span>
          <p className="text-sm text-text-secondary">{t('seller.shop.noProducts')}</p>
        </Card>
      )}
      <ProductRows shop={shop} onOpen={openEditor} />
      <Button
        variant="secondary"
        className="w-full"
        icon={<PackagePlus className="h-4 w-4" />}
        disabled={shop.products.length >= shop.maxProducts}
        onClick={() => openEditor('new')}
        data-testid="add-product"
      >
        {t('seller.shop.addProduct')}
      </Button>
      {!ready && (
        <p className="flex items-start gap-2 text-sm text-text-secondary" data-testid="needs-photo">
          <Info className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
          {t('seller.wizard.products.needPhoto')}
        </p>
      )}
      <Button size="lg" className="w-full" disabled={!ready} onClick={onContinue} data-testid="wizard-continue">
        {t('seller.wizard.continue')}
      </Button>
    </section>
  );
}

function ProductRows({ shop, onOpen }: { shop: Shop; onOpen: (id: string) => void }) {
  const { t } = useTranslation();
  return (
    <div className="space-y-3">
      {shop.products.map((p) => (
        <button
          key={p.id}
          type="button"
          disabled={!shop.canEdit}
          onClick={() => onOpen(p.id)}
          className="flex w-full items-center gap-3 rounded-card border border-border bg-surface p-3 text-left disabled:opacity-80"
          data-testid="shop-product"
        >
          <span className="flex h-16 w-16 shrink-0 items-center justify-center overflow-hidden rounded-input bg-primary-light">
            {p.images[0]?.url ? (
              <img src={p.images[0].url} alt="" className="h-full w-full object-cover" />
            ) : (
              <ImageOff className="h-6 w-6 text-text-secondary" aria-hidden="true" />
            )}
          </span>
          <span className="min-w-0 flex-1">
            <span className="block truncate font-semibold text-text-primary">{p.title}</span>
            <span className="block text-sm text-primary">
              {p.originalPrice != null && p.originalPrice > p.displayPrice && (
                <s className="mr-1.5 text-text-secondary">{priceText(p.originalPrice)}</s>
              )}
              {priceText(p.displayPrice)}
              <span className="ml-2 font-mono text-caption tracking-wider text-text-secondary">{p.code}</span>
            </span>
            <span className={`block text-caption ${p.images.length === 0 ? 'font-semibold text-accent-orange-strong' : 'text-text-secondary'}`}>
              {p.images.length === 0 ? t('seller.wizard.products.missingPhoto') : t('seller.shop.productPhotos', { count: p.images.length })}
              {!p.active && ` · ${t('seller.shop.hidden')}`}
            </span>
          </span>
          {shop.canEdit && <ChevronRight className="h-5 w-5 shrink-0 text-text-secondary" aria-hidden="true" />}
        </button>
      ))}
    </div>
  );
}

// ---------------------------------------------------------------- step 4

function ReviewStep({ shop, busy, onEdit, onSubmit }: { shop: Shop; busy: boolean; onEdit: (step: number) => void; onSubmit: () => void }) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const category = SELLER_CATEGORIES.find((c) => c.value === shop.category);
  const photographed = hasPhotographedProduct(shop);
  const checks = [
    { ok: shop.accountVerified, label: t('seller.submit.verified'), fixLabel: t('seller.submit.verifyNow'), fix: () => navigate('/verification') },
    { ok: photographed, label: t('seller.submit.product'), fixLabel: t('seller.wizard.review.fix'), fix: () => onEdit(2) },
  ];
  const photos = shop.products.flatMap((p) => p.images).filter((i) => i.url).slice(0, 4);

  return (
    <section className="space-y-4">
      <StepIntro title={t('seller.wizard.review.title')} subtitle={t('seller.wizard.review.subtitle')} />

      {shop.status === 'REJECTED' && shop.rejectionReason && (
        <p className="rounded-input bg-accent-red-tint px-4 py-3 text-sm text-text-primary" data-testid="rejection-reason">
          <span className="font-semibold">{t('seller.status.reasonLabel')}</span> {shop.rejectionReason}
        </p>
      )}

      <ReviewSection title={t('seller.wizard.review.category')} onEdit={() => onEdit(0)}>
        <div className="flex items-center gap-3">
          {category && (
            <span className={`flex h-12 w-12 shrink-0 items-center justify-center rounded-input ${category.tint}`}>
              <img src={category.art} alt="" aria-hidden="true" className="h-10 w-10 object-contain" />
            </span>
          )}
          <p className="font-semibold text-text-primary" data-testid="review-category">{t(`seller.categories.${categoryKey(shop.category)}`)}</p>
        </div>
      </ReviewSection>

      <ReviewSection title={t('seller.wizard.review.business')} onEdit={() => onEdit(1)}>
        <div className="space-y-1 text-sm">
          <p className="font-heading text-card-title text-text-primary" data-testid="review-business-name">{shop.businessName}</p>
          <p className="text-text-secondary">{t('seller.shop.callOn', { number: `+91 ${shop.contactPhone}` })}</p>
          <p className="text-text-secondary">
            {shop.whatsappNumber ? t('seller.shop.whatsappOn', { number: `+91 ${shop.whatsappNumber}` }) : t('seller.shop.noWhatsapp')}
          </p>
          <p className="text-text-secondary">{shop.area ? t('seller.shop.areaOn', { area: shop.area }) : t('seller.shop.noArea')}</p>
          <p className="text-text-secondary" data-testid="review-website">
            {shop.websiteUrl ? t('seller.shop.websiteOn', { site: websiteLabel(shop.websiteUrl) }) : t('seller.shop.noWebsite')}
          </p>
        </div>
      </ReviewSection>

      <ReviewSection title={t('seller.wizard.review.products')} onEdit={() => onEdit(2)}>
        <p className="text-sm text-text-primary" data-testid="review-product-count">
          {t('seller.wizard.review.productCount', { count: shop.products.length })} ·{' '}
          {t('seller.shop.productPhotos', { count: shop.imagesUsed })}
        </p>
        {photos.length > 0 && (
          <div className="mt-3 flex gap-2">
            {photos.map((img) => (
              <img key={img.id} src={img.url!} alt="" className="h-14 w-14 rounded-input object-cover" />
            ))}
          </div>
        )}
      </ReviewSection>

      <Card className="space-y-3">
        <p className="font-heading text-card-title text-text-primary">{t('seller.submit.title')}</p>
        <ul className="space-y-2">
          {checks.map((c) => (
            <li key={c.label} className="flex items-center gap-2 text-sm text-text-primary" data-ok={c.ok}>
              {c.ok ? <CheckCircle2 className="h-5 w-5 text-accent-green" aria-hidden="true" /> : <Circle className="h-5 w-5 text-text-secondary" aria-hidden="true" />}
              <span className="flex-1">{c.label}</span>
              {!c.ok && (
                <button type="button" className="text-sm font-semibold text-primary" onClick={c.fix}>
                  {c.fixLabel}
                </button>
              )}
            </li>
          ))}
        </ul>
        <Button size="lg" className="w-full" disabled={busy || !shop.canSubmit} onClick={onSubmit} data-testid="submit-for-review">
          {shop.status === 'REJECTED' ? t('seller.submit.resend') : t('seller.wizard.review.submit')}
        </Button>
        <p className="text-caption text-text-secondary">{t('seller.submit.free', { amount: priceText(shop.listingFee.amount) })}</p>
      </Card>
    </section>
  );
}

function ReviewSection({ title, onEdit, children }: { title: string; onEdit: () => void; children: ReactNode }) {
  const { t } = useTranslation();
  return (
    <Card>
      <div className="mb-2 flex items-center justify-between">
        <p className="text-caption font-semibold uppercase tracking-wide text-text-secondary">{title}</p>
        <button type="button" onClick={onEdit} className="flex items-center gap-1 text-sm font-semibold text-primary" aria-label={`${t('seller.shop.edit')}: ${title}`}>
          <Pencil className="h-3.5 w-3.5" aria-hidden="true" /> {t('seller.shop.edit')}
        </button>
      </div>
      {children}
    </Card>
  );
}

// ================================================================ after sending

/**
 * Her shop once it has been sent: in review, approved and waiting for the
 * fee, live, sent back, or paused. The status card says where it stands
 * and offers the one action that fits; below it, her details and products -
 * editable while the shop is live, read-only while a person is looking at
 * it or the fee is due.
 */
function ShopStatusScreen({ shop, onShop, reload, onFix }: { shop: Shop; onShop: (s: Shop) => void; reload: () => Promise<void>; onFix: () => void }) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const goBack = useGoBack();
  const [editing, setEditing] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [justWentLive, setJustWentLive] = useState(false);
  /** Her SheOut wallet balance, while the fee is due - null until known, or if it could not be read. */
  const [walletBalance, setWalletBalance] = useState<number | null>(null);
  const feeDue = shop.status === 'APPROVED_AWAITING_PAYMENT';
  useEffect(() => {
    if (!feeDue) return;
    walletApi.get().then((w) => setWalletBalance(w.balance)).catch(() => setWalletBalance(null));
  }, [feeDue]);

  async function act(run: () => Promise<Shop>, done?: string) {
    setBusy(true);
    setError(null);
    try {
      onShop(await run());
      if (done) showToast(done);
      return true;
    } catch (err) {
      setError(apiErrorText(err, 'seller.shop.actionError'));
      return false;
    } finally {
      setBusy(false);
    }
  }

  async function payFeeFromWallet() {
    setBusy(true);
    setError(null);
    try {
      const updated = await marketplaceApi.payListingFeeFromWallet();
      onShop(updated);
      if (updated.status === 'ACTIVE') setJustWentLive(true);
    } catch (err) {
      if (err instanceof ApiError && err.body?.error === 'ALREADY_PAID') {
        await reload();
        return;
      }
      setError(apiErrorText(err, 'seller.fee.error'));
    } finally {
      setBusy(false);
    }
  }

  async function payFee() {
    setBusy(true);
    setError(null);
    try {
      const [checkout, contact] = await Promise.all([
        marketplaceApi.startListingFee(),
        usersApi.getMyProfile().then((p) => p.phoneNumber ?? undefined).catch(() => undefined),
      ]);
      const outcome = await openRazorpayCheckout(checkout, t('seller.fee.checkoutDescription'), contact);
      if (outcome.kind === 'dismissed') return;
      if (outcome.kind === 'failed') {
        setError(t('seller.fee.failed', { reason: outcome.message }));
        return;
      }
      const updated = await marketplaceApi.confirmListingFee(outcome.result);
      onShop(updated);
      if (updated.status === 'ACTIVE') setJustWentLive(true);
    } catch (err) {
      if (err instanceof ApiError && err.body?.error === 'ALREADY_PAID') {
        await reload();
        return;
      }
      setError(apiErrorText(err, 'seller.fee.error'));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="space-y-6 pb-6" data-testid="seller-status-screen">
      <TopHeader variant="back" title={t('seller.shop.title')} onBack={goBack} />

      <StatusCard
        shop={shop}
        busy={busy}
        onPay={payFee}
        onPayFromWallet={payFeeFromWallet}
        onFix={onFix}
        walletBalance={walletBalance}
        justWentLive={justWentLive}
      />
      {error && <p className="text-sm text-danger" role="alert">{error}</p>}

      <section className="space-y-3">
        <div className="flex items-center justify-between">
          <h2 className="font-heading text-section text-text-primary">{t('seller.shop.detailsTitle')}</h2>
          {shop.status === 'ACTIVE' && shop.canEdit && !editing && (
            <button type="button" className="flex items-center gap-1 text-sm font-semibold text-primary" onClick={() => setEditing(true)} data-testid="edit-shop">
              <Pencil className="h-4 w-4" aria-hidden="true" /> {t('seller.shop.edit')}
            </button>
          )}
        </div>
        {editing ? (
          <Card>
            <ShopFields
              initial={shop}
              busy={busy}
              onCancel={() => setEditing(false)}
              onSubmit={async (details) => {
                if (await act(() => marketplaceApi.updateShop(details), t('seller.shop.saved'))) setEditing(false);
              }}
            />
          </Card>
        ) : (
          <Card className="space-y-1 text-sm">
            <p className="font-heading text-card-title text-text-primary">{shop.businessName}</p>
            <p className="text-text-secondary">{t(`seller.categories.${categoryKey(shop.category)}`)}</p>
            <p className="text-text-secondary">{t('seller.shop.callOn', { number: `+91 ${shop.contactPhone}` })}</p>
            <p className="text-text-secondary">
              {shop.whatsappNumber ? t('seller.shop.whatsappOn', { number: `+91 ${shop.whatsappNumber}` }) : t('seller.shop.noWhatsapp')}
            </p>
            {shop.area && <p className="text-text-secondary">{t('seller.shop.areaOn', { area: shop.area })}</p>}
            {shop.websiteUrl && <p className="text-text-secondary">{t('seller.shop.websiteOn', { site: websiteLabel(shop.websiteUrl) })}</p>}
          </Card>
        )}
      </section>

      <section className="space-y-3">
        <div className="flex items-center justify-between">
          <h2 className="font-heading text-section text-text-primary">{t('seller.shop.productsTitle')}</h2>
          <span className="text-caption text-text-secondary" data-testid="photo-usage">
            {t('seller.shop.photoUsage', { used: shop.imagesUsed, max: shop.maxImagesPerSeller })}
          </span>
        </div>
        {/* A sent-back shop is changed through the wizard ("Make changes"), not here. */}
        {shop.status === 'ACTIVE' ? (
          <>
            <ProductRows shop={shop} onOpen={(id) => navigate(`/seller/manage/products/${id}`, { state: { fromShop: true } })} />
            {shop.canEdit && (
              <Button
                variant="secondary"
                className="w-full"
                icon={<PackagePlus className="h-4 w-4" />}
                disabled={shop.products.length >= shop.maxProducts}
                onClick={() => navigate('/seller/manage/products/new', { state: { fromShop: true } })}
                data-testid="add-product"
              >
                {t('seller.shop.addProduct')}
              </Button>
            )}
          </>
        ) : (
          <ProductRows shop={{ ...shop, canEdit: false }} onOpen={() => undefined} />
        )}
      </section>

      <p className="text-center text-caption text-text-secondary">{t('seller.legal.applicationNotice')}</p>
    </div>
  );
}

/** A live shop's details, edited in place - the same fields as the wizard's Step 2, with its category beside them. */
function ShopFields({
  initial,
  busy,
  onSubmit,
  onCancel,
}: {
  initial: Shop;
  busy: boolean;
  onSubmit: (d: SellerDetailsInput) => void;
  onCancel: () => void;
}) {
  const { t } = useTranslation();
  const [category, setCategory] = useState<SellerCategory>(initial.category);
  const [values, setValues] = useState<BusinessValues>({
    businessName: initial.businessName,
    contactPhone: initial.contactPhone,
    whatsappNumber: initial.whatsappNumber ?? '',
    area: initial.area ?? '',
    websiteUrl: initial.websiteUrl ?? '',
  });
  const [showErrors, setShowErrors] = useState(false);
  const valid = businessFieldsValid(values);

  return (
    <form
      className="space-y-4"
      noValidate
      onSubmit={(e) => {
        e.preventDefault();
        setShowErrors(true);
        if (!valid) return;
        onSubmit(toDetailsInput(values, category));
      }}
      data-testid="shop-form"
    >
      <BusinessFields values={values} showErrors={showErrors} onChange={(patch) => setValues((v) => ({ ...v, ...patch }))} />
      <SelectField
        label={t('seller.form.category')}
        value={category}
        onChange={(e) => setCategory(e.target.value as SellerCategory)}
        options={SELLER_CATEGORIES.map((c) => ({ value: c.value, label: t(`seller.categories.${c.key}`) }))}
        name="category"
      />
      <div className="flex gap-3">
        <Button type="button" variant="secondary" className="flex-1" onClick={onCancel} disabled={busy}>
          {t('common.cancel')}
        </Button>
        <Button type="submit" className="flex-1" disabled={busy || (showErrors && !valid)} data-testid="shop-form-submit">
          {busy ? t('seller.form.saving') : t('seller.shop.save')}
        </Button>
      </div>
    </form>
  );
}

function StatusCard({
  shop,
  busy,
  onPay,
  onPayFromWallet,
  onFix,
  walletBalance,
  justWentLive,
}: {
  shop: Shop;
  busy: boolean;
  onPay: () => void;
  onPayFromWallet: () => void;
  onFix: () => void;
  walletBalance: number | null;
  justWentLive: boolean;
}) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const icon = {
    DRAFT: <Pencil />,
    SUBMITTED_FOR_REVIEW: <Clock />,
    APPROVED_AWAITING_PAYMENT: <IndianRupee />,
    ACTIVE: <BadgeCheck />,
    REJECTED: <AlertTriangle />,
    SUSPENDED: <PauseCircle />,
  }[shop.status];
  return (
    <Card className="space-y-3" data-testid="shop-status" data-status={shop.status}>
      <div className="flex items-center gap-3">
        {justWentLive ? (
          <SuccessCheck size={44} label={t('seller.status.ACTIVE.title')} />
        ) : (
          <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full bg-primary-light text-primary [&_svg]:h-5 [&_svg]:w-5">{icon}</span>
        )}
        <div className="min-w-0 flex-1">
          <p className="font-heading text-card-title text-text-primary">{t(`seller.status.${shop.status}.title`)}</p>
          <StatusBadge tone={SELLER_STATUS_TONE[shop.status]}>{t(`seller.status.${shop.status}.badge`)}</StatusBadge>
        </div>
      </div>
      <p className="text-sm text-text-secondary">
        {t(`seller.status.${shop.status}.body`, { amount: priceText(shop.listingFee.amount) })}
      </p>
      {shop.status === 'REJECTED' && shop.rejectionReason && (
        <p className="rounded-input bg-accent-red-tint px-4 py-3 text-sm text-text-primary" data-testid="rejection-reason">
          <span className="font-semibold">{t('seller.status.reasonLabel')}</span> {shop.rejectionReason}
        </p>
      )}
      {shop.status === 'REJECTED' && (
        <Button className="w-full" icon={<Pencil className="h-4 w-4" />} onClick={onFix} data-testid="make-changes">
          {t('seller.wizard.makeChanges')}
        </Button>
      )}
      {shop.status === 'SUSPENDED' && shop.suspensionReason && (
        <p className="rounded-input bg-accent-red-tint px-4 py-3 text-sm text-text-primary">
          <span className="font-semibold">{t('seller.status.reasonLabel')}</span> {shop.suspensionReason}
        </p>
      )}
      {shop.status === 'APPROVED_AWAITING_PAYMENT' && (
        <>
          {/* Her SheOut wallet first when it covers the fee: one tap, no app to open. */}
          {walletBalance != null && walletBalance >= shop.listingFee.amount && (
            <Button className="w-full" size="lg" icon={<Wallet className="h-4 w-4" />} onClick={onPayFromWallet} disabled={busy} data-testid="pay-fee-wallet">
              {busy ? t('seller.fee.paying') : t('seller.fee.payWallet', { amount: priceText(shop.listingFee.amount), balance: priceText(walletBalance) })}
            </Button>
          )}
          <Button
            className="w-full"
            size="lg"
            variant={walletBalance != null && walletBalance >= shop.listingFee.amount ? 'secondary' : 'primary'}
            icon={<IndianRupee className="h-4 w-4" />}
            onClick={onPay}
            disabled={busy}
            data-testid="pay-listing-fee"
          >
            {busy ? t('seller.fee.opening') : t('seller.fee.pay', { amount: priceText(shop.listingFee.amount) })}
          </Button>
          {walletBalance != null && walletBalance < shop.listingFee.amount && (
            <p className="text-caption text-text-secondary" data-testid="fee-wallet-short">
              {t('seller.fee.walletShort', { balance: priceText(walletBalance) })}{' '}
              <button type="button" className="font-semibold text-primary" onClick={() => navigate('/wallet', { state: { returnTo: '/seller/manage' } })}>
                {t('seller.fee.addMoney')}
              </button>
            </p>
          )}
          <p className="text-caption text-text-secondary">{t('seller.fee.oneTime')}</p>
        </>
      )}
      {shop.status === 'ACTIVE' && (
        <Button variant="secondary" className="w-full" icon={<Store className="h-4 w-4" />} onClick={() => navigate('/seller')}>
          {t('seller.status.ACTIVE.view')}
        </Button>
      )}
      {shop.status === 'SUSPENDED' && (
        <Button variant="secondary" className="w-full" onClick={() => navigate('/help')}>
          {t('seller.status.SUSPENDED.contact')}
        </Button>
      )}
    </Card>
  );
}
