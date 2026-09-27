import type { SellerCategory, SellerStatus } from '../api/types';
import fashionArt from '../assets/seller/fashion-saree.webp';
import beautyArt from '../assets/seller/beauty.webp';
import tailoringArt from '../assets/seller/tailoring.webp';
import mehandiArt from '../assets/seller/mehandi.webp';
import giftsArt from '../assets/seller/gifts.webp';
import ornamentsArt from '../assets/seller/ornaments.webp';

/**
 * The six SheOut Seller categories, each with its own illustration and a
 * tint from the colour depth scale behind it. Order is the order a woman
 * browsing would expect: what to wear first, then what to have done, then
 * what to give. `key` is the i18n key under seller.categories.
 */
export const SELLER_CATEGORIES: { value: SellerCategory; key: string; art: string; tint: string }[] = [
  { value: 'FASHION_SAREE', key: 'fashion', art: fashionArt, tint: 'bg-primary-light' },
  { value: 'BEAUTY_SERVICES', key: 'beauty', art: beautyArt, tint: 'bg-accent-orange-tint' },
  { value: 'TAILORING', key: 'tailoring', art: tailoringArt, tint: 'bg-accent-green-tint' },
  { value: 'MEHANDI', key: 'mehandi', art: mehandiArt, tint: 'bg-accent-orange-tint' },
  { value: 'GIFTS', key: 'gifts', art: giftsArt, tint: 'bg-primary-light' },
  { value: 'ORNAMENTS', key: 'ornaments', art: ornamentsArt, tint: 'bg-accent-blue-tint' },
];

export function categoryKey(value: SellerCategory): string {
  return SELLER_CATEGORIES.find((c) => c.value === value)?.key ?? 'fashion';
}

/** "₹1,250" - a price as a seller would write it; paise only when there are any. */
export function priceText(amount: number): string {
  return `₹${amount.toLocaleString('en-IN', { minimumFractionDigits: 0, maximumFractionDigits: 2 })}`;
}

/**
 * How a customer reaches the seller: WhatsApp when she gave a number for
 * it, a phone call otherwise. The numbers are stored as ten digits; both
 * links need the country code. The WhatsApp message is only a starting
 * point - she can change it before sending.
 */
export function contactLink(seller: { whatsappNumber: string | null; contactPhone: string }, message: string): {
  kind: 'whatsapp' | 'call';
  href: string;
} {
  if (seller.whatsappNumber) {
    return { kind: 'whatsapp', href: `https://wa.me/91${seller.whatsappNumber}?text=${encodeURIComponent(message)}` };
  }
  return { kind: 'call', href: `tel:+91${seller.contactPhone}` };
}

/** The statuses as a tone for StatusBadge. */
export const SELLER_STATUS_TONE: Record<SellerStatus, 'success' | 'warning' | 'danger' | 'neutral' | 'primary'> = {
  DRAFT: 'neutral',
  SUBMITTED_FOR_REVIEW: 'warning',
  APPROVED_AWAITING_PAYMENT: 'primary',
  ACTIVE: 'success',
  REJECTED: 'danger',
  SUSPENDED: 'danger',
};
