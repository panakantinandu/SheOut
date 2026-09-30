package com.sheout.marketplace.internal;

import com.sheout.marketplace.ProductTerms;
import com.sheout.marketplace.ProductTerms.Availability;
import com.sheout.marketplace.ProductTerms.Fulfilment;
import com.sheout.marketplace.ProductTerms.PriceUnit;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** A seller's stock, ordering and delivery terms, as they are stored and shown. */
class ProductTermsTest {

    private static ProductTerms terms(Availability availability, Integer quantity, Integer ready, Integer min, String options) {
        return new ProductTerms(availability, quantity, ready, PriceUnit.SET, min, options, Set.of(Fulfilment.PICKUP), "  ", null);
    }

    @Test
    void nothingSaidIsInStockPerPiece() {
        ProductTerms t = ProductService.normalise(null);
        assertThat(t.availability()).isEqualTo(Availability.IN_STOCK);
        assertThat(t.priceUnit()).isEqualTo(PriceUnit.PIECE);
    }

    @Test
    void aStockOfNoneIsOutOfStock() {
        ProductTerms t = ProductService.normalise(terms(Availability.IN_STOCK, 0, null, null, null));
        assertThat(t.availability()).isEqualTo(Availability.OUT_OF_STOCK);
        assertThat(t.quantityAvailable()).isNull();
    }

    @Test
    void eachNumberBelongsOnlyWhereItMeansSomething() {
        ProductTerms made = ProductService.normalise(terms(Availability.MADE_TO_ORDER, 7, 5, null, null));
        assertThat(made.quantityAvailable()).isNull();
        assertThat(made.readyInDays()).isEqualTo(5);
        ProductTerms stocked = ProductService.normalise(terms(Availability.IN_STOCK, 7, 5, null, null));
        assertThat(stocked.readyInDays()).isNull();
        assertThat(stocked.quantityAvailable()).isEqualTo(7);
    }

    @Test
    void outOfRangeIsRefused() {
        assertThat(ProductService.normalise(terms(Availability.MADE_TO_ORDER, null, 0, null, null))).isNull();
        assertThat(ProductService.normalise(terms(Availability.MADE_TO_ORDER, null, 91, null, null))).isNull();
        assertThat(ProductService.normalise(terms(Availability.IN_STOCK, -1, null, null, null))).isNull();
        assertThat(ProductService.normalise(terms(Availability.IN_STOCK, null, null, 0, null))).isNull();
    }

    @Test
    void textIsTidied() {
        ProductTerms t = ProductService.normalise(terms(Availability.IN_STOCK, null, null, 1, " S, M ,L,, M "));
        assertThat(t.options()).isEqualTo("S, M, L");
        assertThat(t.deliveryNote()).isNull();
        // A minimum of one is no minimum.
        assertThat(t.minOrderQuantity()).isNull();
    }
}
