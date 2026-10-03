package com.sheout.payments.internal.tax;

/**
 * The swap point for how a tax invoice becomes a file. The default,
 * SimplePdfInvoiceRenderer, writes a plain one-page PDF with no library; a
 * branded template, an e-invoicing (IRN) provider's PDF, or a CA-approved
 * layout is a new implementation here and nothing else changes.
 */
public interface InvoiceRenderer {

    byte[] render(TaxInvoiceEntity invoice);

    default String contentType() {
        return "application/pdf";
    }
}
