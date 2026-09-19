import { i18next } from '@sheout/design-system';
import type { CheckoutDetails, CheckoutResult } from '../api/types';

const CHECKOUT_SCRIPT_URL = 'https://checkout.razorpay.com/v1/checkout.js';

/** The slice of Razorpay's Checkout API this app uses. */
interface RazorpayCheckoutOptions {
  key: string;
  order_id: string;
  amount: number;
  currency: string;
  name: string;
  description: string;
  theme?: { color: string };
  prefill?: { contact?: string };
  handler: (response: { razorpay_order_id: string; razorpay_payment_id: string; razorpay_signature: string }) => void;
  modal?: { ondismiss?: () => void };
}

interface RazorpayInstance {
  open(): void;
  on(event: 'payment.failed', handler: (response: { error?: { description?: string } }) => void): void;
}

declare global {
  interface Window {
    Razorpay?: new (options: RazorpayCheckoutOptions) => RazorpayInstance;
  }
}

let scriptLoad: Promise<void> | null = null;

/**
 * Loads Checkout.js once, on first use rather than with the app - most
 * sessions never pay online, and a third-party script that is never needed
 * should not be fetched. A failed load is forgotten so the next tap retries.
 */
function loadCheckoutScript(): Promise<void> {
  if (window.Razorpay) return Promise.resolve();
  if (!scriptLoad) {
    scriptLoad = new Promise<void>((resolve, reject) => {
      const script = document.createElement('script');
      script.src = CHECKOUT_SCRIPT_URL;
      script.async = true;
      script.onload = () => resolve();
      script.onerror = () => {
        script.remove();
        scriptLoad = null;
        reject(new Error(i18next.t('payment.windowLoadError')));
      };
      document.body.appendChild(script);
    });
  }
  return scriptLoad;
}

export type CheckoutOutcome =
  | { kind: 'paid'; result: CheckoutResult }
  | { kind: 'dismissed' }
  | { kind: 'failed'; message: string };

/**
 * Opens Razorpay Checkout for an order the server created and resolves with
 * what happened. 'paid' is only Checkout's claim: the caller must hand the
 * result to the server to verify before showing the trip as paid.
 * <p>
 * A failed attempt does not close Checkout - Razorpay lets the rider retry
 * another method inside it - so 'failed' is remembered and reported only if
 * she then closes the window without a later success.
 */
export async function openRazorpayCheckout(
  details: CheckoutDetails,
  description: string,
  /** The rider's own number, so Checkout does not make her type it again. */
  contact?: string
): Promise<CheckoutOutcome> {
  await loadCheckoutScript();
  const Razorpay = window.Razorpay;
  if (!Razorpay) throw new Error(i18next.t('payment.windowLoadError'));

  return new Promise<CheckoutOutcome>((resolve) => {
    let lastFailure: string | null = null;
    const checkout = new Razorpay({
      key: details.keyId,
      order_id: details.orderId,
      amount: details.amountPaise,
      currency: details.currency,
      name: 'SheOut',
      description,
      // The brand primary from design-system tokens.js.
      theme: { color: '#4A1A9E' },
      prefill: contact ? { contact } : undefined,
      handler: (response) =>
        resolve({
          kind: 'paid',
          result: {
            razorpayOrderId: response.razorpay_order_id,
            razorpayPaymentId: response.razorpay_payment_id,
            razorpaySignature: response.razorpay_signature,
          },
        }),
      modal: {
        ondismiss: () => resolve(lastFailure ? { kind: 'failed', message: lastFailure } : { kind: 'dismissed' }),
      },
    });
    checkout.on('payment.failed', (response) => {
      lastFailure = response.error?.description ?? i18next.t('payment.didNotGoThrough');
    });
    checkout.open();
  });
}
