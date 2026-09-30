import { Copy, ExternalLink, Globe, Info, MessageCircle, Phone, Store } from 'lucide-react';
import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { Card, ListEmptyState, SkeletonCard, TopHeader, showToast, useTranslation } from '@sheout/design-system';
import { ApiError, marketplaceApi } from '../api/client';
import type { ProductDetail } from '../api/types';
import { PriceTag } from '../components/PriceTag';
import { categoryName, contactLink, websiteLabel } from '../lib/seller';
import { ListingTile } from './Seller';
import { useGoBack } from '../lib/useGoBack';

/**
 * One product in the directory: its photos, price and description, who is
 * selling it, and a way to reach her.
 * <p>
 * "Contact Seller" opens WhatsApp when she gave a number for it, otherwise
 * a phone call. Nothing is bought here - the notice under the price says
 * plainly that the sale is between the customer and the seller, and that
 * SheOut has no part in paying for or delivering it.
 */
export function SellerProduct() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const goBack = useGoBack('/seller');
  const { productId } = useParams<{ productId: string }>();
  const [product, setProduct] = useState<ProductDetail | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [gone, setGone] = useState(false);

  useEffect(() => {
    if (!productId) return;
    setProduct(null);
    setGone(false);
    setError(null);
    marketplaceApi
      .product(productId)
      .then(setProduct)
      .catch((err) => {
        if (err instanceof ApiError && err.status === 404) setGone(true);
        else setError(t('seller.product.loadError'));
      });
    window.scrollTo(0, 0);
  }, [productId, t]);

  const contact = product
    ? contactLink(product, t('seller.product.whatsappMessage', { title: product.title, code: product.code }))
    : null;

  async function copyCode(code: string) {
    try {
      await navigator.clipboard.writeText(code);
      showToast(t('seller.product.codeCopied'));
    } catch {
      // Clipboard blocked: the code is on screen to read out.
    }
  }

  return (
    <div className="space-y-5 pb-4">
      <TopHeader variant="back" title={t('seller.marketplaceTitle')} onBack={goBack} />

      {!product && !gone && !error && <SkeletonCard lines={5} label={t('seller.product.loading')} />}
      {error && <p className="text-sm text-danger">{error}</p>}
      {gone && (
        <ListEmptyState
          icon={<Store />}
          title={t('seller.product.goneTitle')}
          message={t('seller.product.gone')}
          action={{ label: t('seller.product.backToDirectory'), onClick: () => navigate('/seller') }}
        />
      )}

      {product && contact && (
        <>
          {product.imageUrls.length > 0 && (
            <div
              className="-mx-4 flex snap-x snap-mandatory gap-3 overflow-x-auto px-4 pb-1"
              aria-label={t('seller.product.photos', { count: product.imageUrls.length })}
              data-testid="product-photos"
            >
              {product.imageUrls.map((url, i) => (
                <img
                  key={url}
                  src={url}
                  alt={i === 0 ? product.title : ''}
                  className="aspect-square w-[85%] shrink-0 snap-center rounded-card bg-primary-light object-cover"
                />
              ))}
            </div>
          )}

          <div className="space-y-1">
            <h1 className="font-heading text-title text-text-primary" data-testid="product-title">{product.title}</h1>
            <PriceTag price={product.displayPrice} originalPrice={product.originalPrice} size="lg" />
            <p className="text-caption text-text-secondary">{t('seller.product.priceNote')}</p>
            {/* The code she quotes to the seller, so both know exactly which product. */}
            <button
              type="button"
              onClick={() => copyCode(product.code)}
              className="mt-2 inline-flex items-center gap-2 rounded-full border border-border bg-surface px-3 py-1.5 text-sm text-text-primary"
              aria-label={t('seller.product.copyCode', { code: product.code })}
              data-testid="product-code"
            >
              {t('seller.product.code')} <span className="font-mono font-bold tracking-wider">{product.code}</span>
              <Copy className="h-4 w-4 text-text-secondary" aria-hidden="true" />
            </button>
          </div>

          <Card className="space-y-3">
            <div className="flex items-center gap-3">
              <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full bg-primary-light text-primary">
                <Store className="h-5 w-5" aria-hidden="true" />
              </span>
              <div className="min-w-0">
                <p className="truncate font-semibold text-text-primary" data-testid="product-seller">{product.businessName}</p>
                <p className="text-caption text-text-secondary">
                  {categoryName(product, t)}
                  {product.area ? ` · ${product.area}` : ''}
                </p>
              </div>
            </div>
            {/* Her own website, in the phone's browser - never inside SheOut. */}
            {product.websiteUrl && (
              <a
                href={product.websiteUrl}
                target="_blank"
                rel="noopener noreferrer nofollow"
                className="flex items-center gap-2 rounded-input bg-background px-3 py-2.5 text-sm font-semibold text-primary"
                data-testid="seller-website"
              >
                <Globe className="h-4 w-4 shrink-0" aria-hidden="true" />
                <span className="min-w-0 flex-1 truncate">{t('seller.product.website', { site: websiteLabel(product.websiteUrl) })}</span>
                <ExternalLink className="h-4 w-4 shrink-0" aria-hidden="true" />
              </a>
            )}
          </Card>

          <p className="whitespace-pre-wrap text-body text-text-primary">{product.description}</p>

          {/* The notice every product carries: the sale is theirs, not SheOut's. */}
          <div className="flex gap-3 rounded-input bg-accent-orange-tint px-4 py-3" role="note" data-testid="direct-sale-notice">
            <Info className="mt-0.5 h-5 w-5 shrink-0 text-accent-orange-strong" aria-hidden="true" />
            <p className="text-sm text-text-primary">{t('seller.legal.productNotice')}</p>
          </div>

          <a
            href={contact.href}
            target={contact.kind === 'whatsapp' ? '_blank' : undefined}
            rel={contact.kind === 'whatsapp' ? 'noopener noreferrer' : undefined}
            className="flex w-full items-center justify-center gap-2 rounded-full bg-primary px-6 py-4 font-heading text-card-title text-text-inverse shadow-lift"
            data-testid="contact-seller"
            data-contact-kind={contact.kind}
          >
            {contact.kind === 'whatsapp' ? <MessageCircle className="h-5 w-5" aria-hidden="true" /> : <Phone className="h-5 w-5" aria-hidden="true" />}
            {contact.kind === 'whatsapp' ? t('seller.product.contactWhatsapp') : t('seller.product.contactCall')}
          </a>
          <p className="-mt-2 text-center text-caption text-text-secondary">
            {contact.kind === 'whatsapp' ? t('seller.product.codeInMessage') : t('seller.product.codeOnCall', { code: product.code })}
          </p>

          {product.moreFromSeller.length > 0 && (
            <section className="space-y-3">
              <h2 className="font-heading text-section text-text-primary">{t('seller.product.moreFrom', { name: product.businessName })}</h2>
              <div className="grid grid-cols-2 gap-3">
                {product.moreFromSeller.map((item) => (
                  <ListingTile key={item.productId} item={item} onOpen={() => navigate(`/seller/products/${item.productId}`)} />
                ))}
              </div>
            </section>
          )}
        </>
      )}
    </div>
  );
}
