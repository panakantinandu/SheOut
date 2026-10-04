import { useEffect, useState } from 'react';
import { marketplaceApi } from '../api/client';
import type { SellerShop } from '../api/types';

let cached: { at: number; shop: SellerShop | null } | null = null;

/**
 * Whether she has a shop - for the menu and Profile to offer "My shop" and
 * "My products" instead of "Sell on SheOut". Undefined until known; null
 * when she has never applied. Kept for a minute so opening the menu twice
 * does not ask twice.
 */
export function useMyShop(enabled = true): SellerShop | null | undefined {
  const fresh = cached && Date.now() - cached.at < 60_000 ? cached.shop : undefined;
  const [shop, setShop] = useState<SellerShop | null | undefined>(fresh);
  useEffect(() => {
    if (!enabled || fresh !== undefined) return;
    let cancelled = false;
    marketplaceApi
      .myShop()
      .then((s) => {
        cached = { at: Date.now(), shop: s };
        if (!cancelled) setShop(s);
      })
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, [enabled, fresh]);
  return shop;
}
