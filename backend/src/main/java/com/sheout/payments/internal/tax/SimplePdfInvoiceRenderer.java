package com.sheout.payments.internal.tax;

import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * A tax invoice as a plain one-page PDF, written by hand rather than with a
 * library: a page of text lines in Helvetica is a few hundred bytes of PDF,
 * and pulling in a PDF toolkit for that is a dependency to keep patched for
 * nothing. The layout is a starting point for the CA to approve, not a
 * format anybody has signed off (see the README).
 * <p>
 * Text is kept to printable ASCII - the standard PDF fonts have no rupee
 * sign or Indic script - so amounts read "Rs" and a name in another script
 * is transliterated to "?" rather than garbled. A renderer with an embedded
 * Unicode font is the fix when that matters.
 */
@Component
public class SimplePdfInvoiceRenderer implements InvoiceRenderer {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a")
            .withZone(ZoneId.of("Asia/Kolkata"));

    @Override
    public byte[] render(TaxInvoiceEntity inv) {
        List<String> lines = new ArrayList<>();
        lines.add("TAX INVOICE");
        lines.add("");
        lines.add(inv.getSupplierName());
        lines.add("GSTIN: " + inv.getSupplierGstin());
        lines.add("");
        lines.add("Invoice number: " + inv.getInvoiceNumber());
        lines.add("Invoice date: " + WHEN.format(inv.getIssuedAt()));
        lines.add("Place of supply: " + inv.getPlaceOfSupply());
        lines.add("Billed to: " + (inv.getRecipientName() == null ? "-" : inv.getRecipientName()));
        if (inv.getBookingId() != null) {
            lines.add("Trip: " + inv.getBookingId());
        }
        lines.add("");
        lines.add("Description: " + inv.getDescription() + "   SAC " + inv.getSacCode());
        lines.add("Taxable value: Rs " + money(inv.getTaxableValue()));
        String half = inv.getTaxRatePercent().divide(new BigDecimal("2")).stripTrailingZeros().toPlainString();
        lines.add("CGST @ " + half + "%: Rs " + money(inv.getCgstAmount()));
        lines.add("SGST @ " + half + "%: Rs " + money(inv.getSgstAmount()));
        lines.add("Total (tax included): Rs " + money(inv.getTotalAmount()));
        lines.add("");
        lines.add("The price paid included the tax shown. This invoice was issued electronically.");
        return pdf(lines);
    }

    private static String money(BigDecimal amount) {
        return amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    /** One page, A4, Helvetica 11pt, one line per string - with a correct cross-reference table. */
    static byte[] pdf(List<String> lines) {
        StringBuilder text = new StringBuilder("BT /F1 11 Tf 56 780 Td 16 TL\n");
        for (String line : lines) {
            text.append('(').append(escape(line)).append(") Tj T*\n");
        }
        text.append("ET");
        byte[] content = text.toString().getBytes(StandardCharsets.US_ASCII);
        List<String> objects = List.of(
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>",
                "<< /Length " + content.length + " >>\nstream\n" + new String(content, StandardCharsets.US_ASCII) + "\nendstream",
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        List<Integer> offsets = new ArrayList<>();
        write(out, "%PDF-1.4\n");
        for (int i = 0; i < objects.size(); i++) {
            offsets.add(out.size());
            write(out, (i + 1) + " 0 obj\n" + objects.get(i) + "\nendobj\n");
        }
        int xref = out.size();
        StringBuilder table = new StringBuilder("xref\n0 " + (objects.size() + 1) + "\n0000000000 65535 f \n");
        for (int offset : offsets) {
            table.append(String.format("%010d 00000 n \n", offset));
        }
        write(out, table + "trailer\n<< /Size " + (objects.size() + 1) + " /Root 1 0 R >>\nstartxref\n" + xref + "\n%%EOF\n");
        return out.toByteArray();
    }

    private static void write(ByteArrayOutputStream out, String s) {
        out.writeBytes(s.getBytes(StandardCharsets.US_ASCII));
    }

    /** PDF string escapes, and anything outside printable ASCII as "?". */
    private static String escape(String s) {
        StringBuilder out = new StringBuilder();
        for (char c : (s == null ? "" : s).toCharArray()) {
            if (c == '(' || c == ')' || c == '\\') {
                out.append('\\').append(c);
            } else if (c < 32 || c > 126) {
                out.append('?');
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }
}
