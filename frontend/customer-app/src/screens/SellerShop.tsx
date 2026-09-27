import { AlertTriangle, BadgeCheck, CheckCircle2, Circle, Clock, ImageOff, IndianRupee, Info, PackagePlus, PauseCircle, Pencil, Store } from 'lucide-react';
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Button,
  Card,
  SelectField,
  SkeletonCard,
  StatusBadge,
  SuccessCheck,
  TextField,
  TopHeader,
  showToast,
  useTranslation,
} from '@sheout/design-system';
import { ApiError, marketplaceApi, usersApi } from '../api/client';
import type { SellerCategory, SellerDetailsInput, SellerShop as Shop } from '../api/types';
import { apiErrorText } from '../lib/apiErrors';
import { openRazorpayCheckout } from '../lib/razorpayCheckout';
import { SELLER_CATEGORIES, SELLER_STATUS_TONE, categoryKey, priceText } from '../lib/seller';

/**
 * Sell on SheOut: her own shop, from applying to live.
 * <p>
 * She fills in her shop and products, sends them for review (free), and is
 * asked for the one-time listing fee only once a person has approved them.
 * Every state says what happens next, and the one action that fits it.
 * The application opens with what SheOut is and is not: a place to be
 * found, not a party to her sales.
 */
export function SellerShop() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [shop, setShop] = useState<Shop | null | undefined>(undefined);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [editing, setEditing] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [justWentLive, setJustWentLive] = useState(false);

  const load = () =>
    marketplaceApi
      .myShop()
      .then(setShop)
      .catch(() => setLoadError(t('seller.shop.loadError')));
  useEffect(() => {
    load();
  }, []);

  async function act(run: () => Promise<Shop>, done?: string) {
    setBusy(true);
    setError(null);
    try {
      setShop(await run());
      if (done) showToast(done);
      return true;
    } catch (err) {
      setError(apiErrorText(err, 'seller.shop.actionError'));
      return false;
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
      setShop(updated);
      if (updated.status === 'ACTIVE') setJustWentLive(true);
    } catch (err) {
      if (err instanceof ApiError && err.body?.error === 'ALREADY_PAID') {
        await load();
        return;
      }
      setError(apiErrorText(err, 'seller.fee.error'));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="space-y-6 pb-6">
      <TopHeader variant="back" title={t('seller.shop.title')} onBack={() => navigate('/seller')} />

      {shop === undefined && !loadError && <SkeletonCard lines={4} label={t('seller.shop.loading')} />}
      {loadError && <p className="text-sm text-danger">{loadError}</p>}

      {shop === null && (
        <ApplyForm
          busy={busy}
          error={error}
          onSubmit={(details) => act(() => marketplaceApi.apply(details), t('seller.shop.applied'))}
        />
      )}

      {shop && (
        <>
          <StatusCard shop={shop} busy={busy} onPay={payFee} justWentLive={justWentLive} />
          {error && <p className="text-sm text-danger" role="alert">{error}</p>}

          <section className="space-y-3">
            <div className="flex items-center justify-between">
              <h2 className="font-heading text-section text-text-primary">{t('seller.shop.detailsTitle')}</h2>
              {shop.canEdit && !editing && (
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
                  submitLabel={t('seller.shop.save')}
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
            {shop.products.length === 0 && (
              <Card className="text-center text-sm text-text-secondary">{t('seller.shop.noProducts')}</Card>
            )}
            <div className="space-y-3">
              {shop.products.map((p) => (
                <button
                  key={p.id}
                  type="button"
                  disabled={!shop.canEdit}
                  onClick={() => navigate(`/seller/manage/products/${p.id}`)}
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
                    <span className="block text-sm text-primary">{priceText(p.displayPrice)}</span>
                    <span className="block text-caption text-text-secondary">
                      {t('seller.shop.productPhotos', { count: p.images.length })}
                      {!p.active && ` · ${t('seller.shop.hidden')}`}
                    </span>
                  </span>
                </button>
              ))}
            </div>
            {shop.canEdit && (
              <Button
                variant="secondary"
                className="w-full"
                icon={<PackagePlus className="h-4 w-4" />}
                disabled={shop.products.length >= shop.maxProducts}
                onClick={() => navigate('/seller/manage/products/new')}
                data-testid="add-product"
              >
                {t('seller.shop.addProduct')}
              </Button>
            )}
          </section>

          {(shop.status === 'DRAFT' || shop.status === 'REJECTED') && (
            <SubmitCard shop={shop} busy={busy} onSubmit={() => act(() => marketplaceApi.submit(), t('seller.shop.submitted'))} />
          )}

          <p className="text-center text-caption text-text-secondary">{t('seller.legal.applicationNotice')}</p>
        </>
      )}
    </div>
  );
}

// ---------------------------------------------------------------- applying

function ApplyForm({ busy, error, onSubmit }: { busy: boolean; error: string | null; onSubmit: (d: SellerDetailsInput) => void }) {
  const { t } = useTranslation();
  const [understood, setUnderstood] = useState(false);
  return (
    <div className="space-y-5">
      <div>
        <h1 className="font-heading text-title text-text-primary">{t('seller.apply.title')}</h1>
        <p className="mt-1 text-sm text-text-secondary">{t('seller.apply.subtitle')}</p>
      </div>
      <ol className="space-y-2 text-sm text-text-primary">
        {(['one', 'two', 'three'] as const).map((step, i) => (
          <li key={step} className="flex gap-3">
            <span className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-primary text-micro text-text-inverse">{i + 1}</span>
            {t(`seller.apply.steps.${step}`)}
          </li>
        ))}
      </ol>
      {/* The one thing she must understand before anything else. */}
      <div className="flex gap-3 rounded-input bg-accent-orange-tint px-4 py-3" role="note" data-testid="seller-legal-notice">
        <Info className="mt-0.5 h-5 w-5 shrink-0 text-accent-orange-strong" aria-hidden="true" />
        <p className="text-sm text-text-primary">{t('seller.legal.applicationNotice')}</p>
      </div>
      <Card>
        <ShopFields
          busy={busy}
          submitLabel={t('seller.apply.submit')}
          extraValid={understood}
          extra={
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
          }
          onSubmit={onSubmit}
        />
      </Card>
      {error && <p className="text-sm text-danger" role="alert">{error}</p>}
    </div>
  );
}

function ShopFields({
  initial,
  busy,
  submitLabel,
  onSubmit,
  onCancel,
  extra,
  extraValid = true,
}: {
  initial?: Shop;
  busy: boolean;
  submitLabel: string;
  onSubmit: (d: SellerDetailsInput) => void;
  onCancel?: () => void;
  extra?: React.ReactNode;
  extraValid?: boolean;
}) {
  const { t } = useTranslation();
  const [name, setName] = useState(initial?.businessName ?? '');
  const [category, setCategory] = useState<SellerCategory | ''>(initial?.category ?? '');
  const [phone, setPhone] = useState(initial?.contactPhone ?? '');
  const [whatsapp, setWhatsapp] = useState(initial?.whatsappNumber ?? '');
  const [showErrors, setShowErrors] = useState(false);
  const digits = (v: string) => v.replace(/\D/g, '').replace(/^91(?=\d{10}$)/, '').replace(/^0(?=\d{10}$)/, '');
  const phoneOk = /^[6-9]\d{9}$/.test(digits(phone));
  const whatsappOk = !whatsapp.trim() || /^[6-9]\d{9}$/.test(digits(whatsapp));
  const valid = name.trim().length > 0 && !!category && phoneOk && whatsappOk && extraValid;

  return (
    <form
      className="space-y-4"
      noValidate
      onSubmit={(e) => {
        e.preventDefault();
        setShowErrors(true);
        if (!valid || !category) return;
        onSubmit({
          businessName: name.trim(),
          category,
          contactPhone: digits(phone),
          whatsappNumber: whatsapp.trim() ? digits(whatsapp) : undefined,
        });
      }}
      data-testid="shop-form"
    >
      <TextField
        label={t('seller.form.businessName')}
        value={name}
        maxLength={80}
        onChange={(e) => setName(e.target.value)}
        placeholder={t('seller.form.businessNamePlaceholder')}
        error={showErrors && !name.trim() ? t('seller.form.required') : undefined}
        name="businessName"
      />
      <SelectField
        label={t('seller.form.category')}
        placeholder={t('seller.form.categoryPlaceholder')}
        value={category}
        onChange={(e) => setCategory(e.target.value as SellerCategory | '')}
        options={SELLER_CATEGORIES.map((c) => ({ value: c.value, label: t(`seller.categories.${c.key}`) }))}
        name="category"
      />
      {showErrors && !category && <p className="-mt-2 text-sm text-danger">{t('seller.form.required')}</p>}
      <TextField
        label={t('seller.form.contactPhone')}
        value={phone}
        inputMode="tel"
        maxLength={16}
        onChange={(e) => setPhone(e.target.value)}
        placeholder="98765 43210"
        error={showErrors && !phoneOk ? t('seller.form.phoneInvalid') : undefined}
        name="contactPhone"
      />
      <div className="space-y-1">
        <TextField
          label={t('seller.form.whatsapp')}
          value={whatsapp}
          inputMode="tel"
          maxLength={16}
          onChange={(e) => setWhatsapp(e.target.value)}
          placeholder={t('seller.form.whatsappPlaceholder')}
          error={showErrors && !whatsappOk ? t('seller.form.phoneInvalid') : undefined}
          name="whatsappNumber"
        />
        {phoneOk && !whatsapp.trim() && (
          <button type="button" className="text-sm font-semibold text-primary" onClick={() => setWhatsapp(digits(phone))}>
            {t('seller.form.sameAsPhone')}
          </button>
        )}
        <p className="text-caption text-text-secondary">{t('seller.form.whatsappHelp')}</p>
      </div>
      {extra}
      <div className="flex gap-3">
        {onCancel && (
          <Button type="button" variant="secondary" className="flex-1" onClick={onCancel} disabled={busy}>
            {t('common.cancel')}
          </Button>
        )}
        <Button type="submit" className="flex-1" disabled={busy || (showErrors && !valid)} data-testid="shop-form-submit">
          {busy ? t('seller.form.saving') : submitLabel}
        </Button>
      </div>
    </form>
  );
}

// ---------------------------------------------------------------- where she is

function StatusCard({ shop, busy, onPay, justWentLive }: { shop: Shop; busy: boolean; onPay: () => void; justWentLive: boolean }) {
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
      {shop.status === 'SUSPENDED' && shop.suspensionReason && (
        <p className="rounded-input bg-accent-red-tint px-4 py-3 text-sm text-text-primary">
          <span className="font-semibold">{t('seller.status.reasonLabel')}</span> {shop.suspensionReason}
        </p>
      )}
      {shop.status === 'APPROVED_AWAITING_PAYMENT' && (
        <>
          <Button className="w-full" size="lg" icon={<IndianRupee className="h-4 w-4" />} onClick={onPay} disabled={busy} data-testid="pay-listing-fee">
            {busy ? t('seller.fee.opening') : t('seller.fee.pay', { amount: priceText(shop.listingFee.amount) })}
          </Button>
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

function SubmitCard({ shop, busy, onSubmit }: { shop: Shop; busy: boolean; onSubmit: () => void }) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const hasProduct = shop.products.some((p) => p.active && p.images.length > 0);
  const checks = [
    { ok: shop.accountVerified, label: t('seller.submit.verified'), fix: () => navigate('/verification') },
    { ok: hasProduct, label: t('seller.submit.product') },
  ];
  return (
    <Card className="space-y-3">
      <p className="font-heading text-card-title text-text-primary">{t('seller.submit.title')}</p>
      <ul className="space-y-2">
        {checks.map((c) => (
          <li key={c.label} className="flex items-center gap-2 text-sm text-text-primary">
            {c.ok ? <CheckCircle2 className="h-5 w-5 text-accent-green" aria-hidden="true" /> : <Circle className="h-5 w-5 text-text-secondary" aria-hidden="true" />}
            <span className="flex-1">{c.label}</span>
            {!c.ok && c.fix && (
              <button type="button" className="text-sm font-semibold text-primary" onClick={c.fix}>
                {t('seller.submit.verifyNow')}
              </button>
            )}
          </li>
        ))}
      </ul>
      <Button className="w-full" disabled={busy || !shop.canSubmit} onClick={onSubmit} data-testid="submit-for-review">
        {shop.status === 'REJECTED' ? t('seller.submit.resend') : t('seller.submit.send')}
      </Button>
      <p className="text-caption text-text-secondary">{t('seller.submit.free', { amount: priceText(shop.listingFee.amount) })}</p>
    </Card>
  );
}
