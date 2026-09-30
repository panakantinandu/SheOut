import { ImagePlus, Trash2, X } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import { Button, Card, ConfirmDialog, SelectField, SkeletonCard, TextField, TopHeader, showToast, useTranslation } from '@sheout/design-system';
import { marketplaceApi } from '../api/client';
import type { Fulfilment, PriceUnit, ProductAvailability, ReturnPolicy, SellerShop } from '../api/types';
import { apiErrorText } from '../lib/apiErrors';
import { discountPercent } from '../lib/seller';

/**
 * Adding or changing one product: what it is, what it costs, and its
 * photos. A new product is saved first and then takes photos - a photo has
 * to belong to something. The price is shown to customers as she sets it;
 * nothing is ever charged in the app for it.
 * <p>
 * Photos count against two caps, this product's and the whole shop's, and
 * both are shown so the limit is never a surprise.
 */
export function SellerProductEditor() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { productId } = useParams<{ productId: string }>();
  const location = useLocation();
  const isNew = !productId || productId === 'new';
  // Opened from her shop (the registration wizard or the live shop): go back
  // to it, so the wizard is not stacked twice in history. Opened directly: replace.
  const fromShop = !!(location.state as { fromShop?: boolean } | null)?.fromShop;
  const leave = () => (fromShop ? navigate(-1) : navigate('/seller/manage', { replace: true }));
  const [shop, setShop] = useState<SellerShop | null>(null);
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [price, setPrice] = useState('');
  /** The price before a discount - optional, and only if she genuinely charged it before. */
  const [originalPrice, setOriginalPrice] = useState('');
  const [active, setActive] = useState(true);
  // What a buyer asks before she calls - see ProductTerms.
  const [availability, setAvailability] = useState<ProductAvailability>('IN_STOCK');
  const [quantity, setQuantity] = useState('');
  const [readyIn, setReadyIn] = useState('');
  const [priceUnit, setPriceUnit] = useState<PriceUnit>('PIECE');
  const [minOrder, setMinOrder] = useState('');
  const [options, setOptions] = useState('');
  const [fulfilment, setFulfilment] = useState<Fulfilment[]>([]);
  const [deliveryNote, setDeliveryNote] = useState('');
  const [returnPolicy, setReturnPolicy] = useState<ReturnPolicy | ''>('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [showErrors, setShowErrors] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const fileInput = useRef<HTMLInputElement>(null);

  const product = shop && !isNew ? shop.products.find((p) => p.id === productId) ?? null : null;

  useEffect(() => {
    marketplaceApi
      .myShop()
      .then((s) => {
        if (!s) {
          navigate('/seller/manage', { replace: true });
          return;
        }
        setShop(s);
        const p = isNew ? null : s.products.find((x) => x.id === productId);
        if (p) {
          setTitle(p.title);
          setDescription(p.description);
          setPrice(String(p.displayPrice));
          setOriginalPrice(p.originalPrice != null ? String(p.originalPrice) : '');
          setActive(p.active);
          if (p.terms) {
            setAvailability(p.terms.availability);
            setQuantity(p.terms.quantityAvailable != null ? String(p.terms.quantityAvailable) : '');
            setReadyIn(p.terms.readyInDays != null ? String(p.terms.readyInDays) : '');
            setPriceUnit(p.terms.priceUnit);
            setMinOrder(p.terms.minOrderQuantity != null ? String(p.terms.minOrderQuantity) : '');
            setOptions(p.terms.options ?? '');
            setFulfilment(p.terms.fulfilment);
            setDeliveryNote(p.terms.deliveryNote ?? '');
            setReturnPolicy(p.terms.returnPolicy ?? '');
          }
        }
      })
      .catch(() => setError(t('seller.shop.loadError')));
  }, [productId]);

  const priceNumber = Number(price);
  const priceOk = price.trim() !== '' && Number.isFinite(priceNumber) && priceNumber >= 0 && priceNumber <= 9999999;
  const originalNumber = Number(originalPrice);
  const originalOk = originalPrice.trim() === '' || (Number.isFinite(originalNumber) && priceOk && originalNumber > priceNumber && originalNumber <= 9999999);
  const off = originalPrice.trim() && originalOk ? discountPercent(priceNumber, originalNumber) : null;
  const whole = (text: string, min: number, max: number) => text.trim() === '' || (/^\d+$/.test(text.trim()) && Number(text) >= min && Number(text) <= max);
  const quantityOk = availability !== 'IN_STOCK' || whole(quantity, 0, 100000);
  const readyOk = availability !== 'MADE_TO_ORDER' || whole(readyIn, 1, 90);
  const minOk = whole(minOrder, 1, 10000);
  const valid = title.trim().length > 0 && description.trim().length > 0 && priceOk && originalOk && quantityOk && readyOk && minOk;

  async function save() {
    setShowErrors(true);
    if (!valid || !shop) return;
    setBusy(true);
    setError(null);
    const input = {
      title: title.trim(),
      description: description.trim(),
      displayPrice: priceNumber,
      originalPrice: originalPrice.trim() ? originalNumber : null,
      active,
      availability,
      quantityAvailable: availability === 'IN_STOCK' && quantity.trim() ? Number(quantity) : null,
      readyInDays: availability === 'MADE_TO_ORDER' && readyIn.trim() ? Number(readyIn) : null,
      priceUnit,
      minOrderQuantity: minOrder.trim() ? Number(minOrder) : null,
      options: options.trim() || null,
      fulfilment,
      deliveryNote: deliveryNote.trim() || null,
      returnPolicy: returnPolicy || null,
    };
    try {
      if (isNew) {
        const before = new Set(shop.products.map((p) => p.id));
        const updated = await marketplaceApi.addProduct(input);
        const created = updated.products.find((p) => !before.has(p.id));
        showToast(t('seller.editor.added'));
        // Straight on to its photos: a product without one cannot be reviewed.
        if (created) navigate(`/seller/manage/products/${created.id}`, { replace: true, state: location.state });
        else leave();
      } else {
        setShop(await marketplaceApi.updateProduct(productId!, input));
        showToast(t('seller.editor.saved'));
        leave();
      }
    } catch (err) {
      setError(apiErrorText(err, 'seller.editor.saveError'));
    } finally {
      setBusy(false);
    }
  }

  async function addPhotos(files: FileList | null) {
    if (!files || !product) return;
    setBusy(true);
    setError(null);
    try {
      // One at a time: each answer is the shop as it now stands, so a cap
      // reached half way stops the rest with the reason.
      for (const file of Array.from(files)) {
        setShop(await marketplaceApi.addPhoto(product.id, file));
      }
    } catch (err) {
      setError(apiErrorText(err, 'seller.editor.photoError'));
    } finally {
      setBusy(false);
      if (fileInput.current) fileInput.current.value = '';
    }
  }

  async function removePhoto(imageId: string) {
    if (!product) return;
    setBusy(true);
    setError(null);
    try {
      setShop(await marketplaceApi.deletePhoto(product.id, imageId));
    } catch (err) {
      setError(apiErrorText(err, 'seller.editor.photoError'));
    } finally {
      setBusy(false);
    }
  }

  async function remove() {
    if (!product) return;
    setConfirmDelete(false);
    setBusy(true);
    try {
      await marketplaceApi.deleteProduct(product.id);
      showToast(t('seller.editor.deleted'));
      leave();
    } catch (err) {
      setError(apiErrorText(err, 'seller.editor.saveError'));
      setBusy(false);
    }
  }

  const productFull = !!product && !!shop && product.images.length >= shop.maxImagesPerProduct;
  const shopFull = !!shop && shop.imagesUsed >= shop.maxImagesPerSeller;

  return (
    <div className="space-y-5 pb-6">
      <TopHeader
        variant="back"
        title={isNew ? t('seller.editor.newTitle') : t('seller.editor.editTitle')}
        onBack={leave}
      />
      {!shop && !error && <SkeletonCard lines={4} label={t('seller.shop.loading')} />}
      {shop && !shop.canEdit && <p className="text-sm text-text-secondary">{t('seller.editor.locked')}</p>}

      {shop && shop.canEdit && (isNew || product) && (
        <>
          <Card className="space-y-4">
            <TextField
              label={t('seller.editor.productTitle')}
              value={title}
              maxLength={100}
              onChange={(e) => setTitle(e.target.value)}
              placeholder={t('seller.editor.productTitlePlaceholder')}
              error={showErrors && !title.trim() ? t('seller.form.required') : undefined}
              name="title"
            />
            <label className="block">
              <span className="mb-1 block text-sm font-medium text-text-primary">{t('seller.editor.description')}</span>
              <textarea
                value={description}
                maxLength={2000}
                rows={5}
                onChange={(e) => setDescription(e.target.value)}
                placeholder={t('seller.editor.descriptionPlaceholder')}
                className="w-full rounded-input border border-border bg-surface px-4 py-3 text-text-primary placeholder:text-text-secondary"
                name="description"
              />
              {showErrors && !description.trim() && <span className="text-sm text-danger">{t('seller.form.required')}</span>}
            </label>
            <TextField
              label={t('seller.editor.price')}
              value={price}
              inputMode="decimal"
              onChange={(e) => setPrice(e.target.value.replace(/[^\d.]/g, ''))}
              placeholder="1200"
              error={showErrors && !priceOk ? t('seller.editor.priceInvalid') : undefined}
              name="displayPrice"
            />
            <p className="-mt-2 text-caption text-text-secondary">{t('seller.editor.priceHelp')}</p>
            <SelectField
              label={t('seller.editor.unit')}
              value={priceUnit}
              onChange={(e) => setPriceUnit(e.target.value as PriceUnit)}
              options={(['PIECE', 'SET', 'PAIR', 'METRE', 'KG', 'HOUR', 'SESSION'] as PriceUnit[]).map((u) => ({ value: u, label: t(`seller.terms.unit.${u}`) }))}
              name="priceUnit"
            />
            <TextField
              label={t('seller.editor.originalPrice')}
              value={originalPrice}
              inputMode="decimal"
              onChange={(e) => setOriginalPrice(e.target.value.replace(/[^\d.]/g, ''))}
              placeholder={t('seller.editor.originalPricePlaceholder')}
              error={showErrors && !originalOk ? t('seller.editor.originalPriceInvalid') : undefined}
              name="originalPrice"
            />
            {off !== null && (
              <p className="-mt-2 text-caption font-semibold text-accent-green-strong" data-testid="discount-preview">
                {t('seller.editor.discountPreview', { percent: off })}
              </p>
            )}
            {/* The honest-pricing declaration, where the "was" price is typed. */}
            <p className="-mt-1 rounded-input bg-accent-orange-tint px-3 py-2 text-caption text-text-primary" role="note" data-testid="genuine-price-note">
              {t('seller.legal.genuinePrice')}
            </p>
            {/* Stock and ordering: can she have it, how many, how soon, the minimum, the sizes. */}
            <div className="space-y-3 border-t border-border pt-4" data-testid="editor-stock">
              <p className="font-heading text-card-title text-text-primary">{t('seller.editor.stockTitle')}</p>
              <div className="grid grid-cols-3 gap-2" role="radiogroup" aria-label={t('seller.editor.availability')}>
                {(['IN_STOCK', 'MADE_TO_ORDER', 'OUT_OF_STOCK'] as ProductAvailability[]).map((a) => (
                  <button
                    key={a}
                    type="button"
                    role="radio"
                    aria-checked={availability === a}
                    onClick={() => setAvailability(a)}
                    className={`rounded-input border px-2 py-2.5 text-sm font-semibold leading-tight transition-colors ${availability === a ? 'border-primary bg-primary-light text-primary' : 'border-border bg-surface text-text-secondary'}`}
                    data-testid={`availability-${a}`}
                  >
                    {t(`seller.terms.availability.${a}`)}
                  </button>
                ))}
              </div>
              {availability === 'IN_STOCK' && (
                <TextField
                  label={t('seller.editor.quantity')}
                  value={quantity}
                  inputMode="numeric"
                  onChange={(e) => setQuantity(e.target.value.replace(/\D/g, ''))}
                  placeholder={t('seller.editor.quantityPlaceholder')}
                  error={showErrors && !quantityOk ? t('seller.editor.numberInvalid', { min: 0, max: 100000 }) : undefined}
                  name="quantityAvailable"
                />
              )}
              {availability === 'IN_STOCK' && <p className="-mt-2 text-caption text-text-secondary">{t('seller.editor.quantityHelp')}</p>}
              {availability === 'MADE_TO_ORDER' && (
                <TextField
                  label={t('seller.editor.readyIn')}
                  value={readyIn}
                  inputMode="numeric"
                  onChange={(e) => setReadyIn(e.target.value.replace(/\D/g, ''))}
                  placeholder="5"
                  error={showErrors && !readyOk ? t('seller.editor.numberInvalid', { min: 1, max: 90 }) : undefined}
                  name="readyInDays"
                />
              )}
              <TextField
                label={t('seller.editor.minOrder')}
                value={minOrder}
                inputMode="numeric"
                onChange={(e) => setMinOrder(e.target.value.replace(/\D/g, ''))}
                placeholder={t('seller.editor.minOrderPlaceholder')}
                error={showErrors && !minOk ? t('seller.editor.numberInvalid', { min: 1, max: 10000 }) : undefined}
                name="minOrderQuantity"
              />
              <TextField
                label={t('seller.editor.options')}
                value={options}
                maxLength={200}
                onChange={(e) => setOptions(e.target.value)}
                placeholder={t('seller.editor.optionsPlaceholder')}
                name="options"
              />
            </div>

            {/* How it reaches the buyer, and whether it can go back. */}
            <div className="space-y-3 border-t border-border pt-4" data-testid="editor-delivery">
              <p className="font-heading text-card-title text-text-primary">{t('seller.editor.deliveryTitle')}</p>
              <p className="-mt-2 text-sm text-text-secondary">{t('seller.editor.fulfilment')}</p>
              <div className="grid grid-cols-2 gap-2">
                {(['HOME_DELIVERY', 'PICKUP', 'AT_YOUR_HOME', 'AT_SELLER_PLACE'] as Fulfilment[]).map((way) => {
                  const on = fulfilment.includes(way);
                  return (
                    <label
                      key={way}
                      className={`flex cursor-pointer items-center gap-2 rounded-input border px-3 py-2.5 text-sm transition-colors ${on ? 'border-primary bg-primary-light text-primary' : 'border-border bg-surface text-text-primary'}`}
                    >
                      <input
                        type="checkbox"
                        checked={on}
                        onChange={() => setFulfilment((current) => (on ? current.filter((x) => x !== way) : [...current, way]))}
                        className="h-4 w-4 accent-primary"
                        data-testid={`fulfilment-${way}`}
                      />
                      {t(`seller.terms.fulfilment.${way}`)}
                    </label>
                  );
                })}
              </div>
              <label className="block">
                <span className="mb-1 block text-sm font-medium text-text-primary">{t('seller.editor.deliveryNote')}</span>
                <textarea
                  value={deliveryNote}
                  maxLength={300}
                  rows={2}
                  onChange={(e) => setDeliveryNote(e.target.value)}
                  placeholder={t('seller.editor.deliveryNotePlaceholder')}
                  className="w-full rounded-input border border-border bg-surface px-4 py-3 text-text-primary placeholder:text-text-secondary"
                  name="deliveryNote"
                />
              </label>
              <SelectField
                label={t('seller.terms.returnsTitle')}
                value={returnPolicy}
                onChange={(e) => setReturnPolicy(e.target.value as ReturnPolicy | '')}
                placeholder={t('seller.editor.returnsNotSaid')}
                options={(['NO_RETURNS', 'EXCHANGE_ONLY', 'RETURNS_ACCEPTED'] as ReturnPolicy[]).map((r) => ({ value: r, label: t(`seller.terms.returns.${r}`) }))}
                name="returnPolicy"
              />
            </div>

            <label className="flex cursor-pointer items-start gap-3 text-sm text-text-primary">
              <input type="checkbox" checked={active} onChange={(e) => setActive(e.target.checked)} className="mt-0.5 h-5 w-5 accent-primary" />
              <span>
                {t('seller.editor.active')}
                <span className="block text-caption text-text-secondary">{t('seller.editor.activeHelp')}</span>
              </span>
            </label>
            <Button className="w-full" onClick={save} disabled={busy} data-testid="save-product">
              {busy ? t('seller.form.saving') : isNew ? t('seller.editor.saveAndAddPhotos') : t('seller.editor.save')}
            </Button>
          </Card>

          {product && (
            <section className="space-y-3">
              <div className="flex items-center justify-between">
                <h2 className="font-heading text-section text-text-primary">{t('seller.editor.photosTitle')}</h2>
                <span className="text-caption text-text-secondary" data-testid="product-photo-usage">
                  {t('seller.editor.photoCount', { used: product.images.length, max: shop.maxImagesPerProduct })}
                </span>
              </div>
              <div className="grid grid-cols-3 gap-2">
                {product.images.map((img) => (
                  <div key={img.id} className="relative aspect-square overflow-hidden rounded-input bg-primary-light">
                    {img.url && <img src={img.url} alt="" className="h-full w-full object-cover" />}
                    <button
                      type="button"
                      aria-label={t('seller.editor.removePhoto')}
                      onClick={() => removePhoto(img.id)}
                      disabled={busy}
                      className="absolute right-1 top-1 flex h-7 w-7 items-center justify-center rounded-full bg-black/60 text-white"
                    >
                      <X className="h-4 w-4" aria-hidden="true" />
                    </button>
                  </div>
                ))}
              </div>
              <input
                ref={fileInput}
                type="file"
                accept="image/jpeg,image/png,image/webp"
                multiple
                hidden
                onChange={(e) => addPhotos(e.target.files)}
                data-testid="photo-input"
              />
              <Button
                variant="secondary"
                className="w-full"
                icon={<ImagePlus className="h-4 w-4" />}
                disabled={busy || productFull || shopFull}
                onClick={() => fileInput.current?.click()}
                data-testid="add-photo"
              >
                {busy ? t('common.uploading') : t('seller.editor.addPhoto')}
              </Button>
              <p className="text-caption text-text-secondary">
                {productFull
                  ? t('seller.editor.productFull')
                  : shopFull
                    ? t('seller.editor.shopFull', { max: shop.maxImagesPerSeller })
                    : t('seller.editor.shopPhotoCount', { used: shop.imagesUsed, max: shop.maxImagesPerSeller })}
              </p>
            </section>
          )}

          {error && <p className="text-sm text-danger" role="alert">{error}</p>}

          {product && (
            <Button variant="danger" className="w-full" icon={<Trash2 className="h-4 w-4" />} onClick={() => setConfirmDelete(true)} disabled={busy}>
              {t('seller.editor.delete')}
            </Button>
          )}
          <ConfirmDialog
            open={confirmDelete}
            title={t('seller.editor.deleteTitle')}
            message={t('seller.editor.deleteMessage')}
            confirmLabel={t('seller.editor.delete')}
            destructive
            onConfirm={remove}
            onCancel={() => setConfirmDelete(false)}
          />
        </>
      )}
      {shop && shop.canEdit && !isNew && !product && <p className="text-sm text-text-secondary">{t('seller.editor.notFound')}</p>}
      {!shop && error && <p className="text-sm text-danger">{error}</p>}
    </div>
  );
}
