package com.sheout.marketplace;

import java.util.Set;

/**
 * The practical facts about a product that a buyer asks before she calls:
 * can I have it, how soon, what is the price per, is there a minimum, what
 * sizes or colours, how does it reach me, can it go back.
 * <p>
 * All of it is the seller's own statement, shown as she gave it. SheOut is
 * not party to the sale (see the product notice), so none of it is a promise
 * SheOut enforces - it is what saves both women a phone call that ends in
 * "sorry, sold out".
 *
 * @param availability      in stock, made to order, or out of stock
 * @param quantityAvailable how many she has, when in stock and she wants to say; null if not said
 * @param readyInDays       for made to order, how long it takes; null otherwise
 * @param priceUnit         what the price is for
 * @param minOrderQuantity  the fewest she sells at once; null for no minimum
 * @param options           sizes, colours or variants, comma separated, in her words
 * @param fulfilment        how it reaches the buyer
 * @param deliveryNote      anything about delivery in her words - areas, charges
 * @param returnPolicy      whether it can go back; null if she has not said
 */
public record ProductTerms(Availability availability,
                           Integer quantityAvailable,
                           Integer readyInDays,
                           PriceUnit priceUnit,
                           Integer minOrderQuantity,
                           String options,
                           Set<Fulfilment> fulfilment,
                           String deliveryNote,
                           ReturnPolicy returnPolicy) {

    /** What every product was before these existed: in stock, per piece, nothing else said. */
    public static ProductTerms defaults() {
        return new ProductTerms(Availability.IN_STOCK, null, null, PriceUnit.PIECE, null, null, Set.of(), null, null);
    }

    public enum Availability { IN_STOCK, MADE_TO_ORDER, OUT_OF_STOCK }

    /** Names are stored; add new ones rather than renaming. */
    public enum PriceUnit { PIECE, SET, PAIR, METRE, KG, HOUR, SESSION }

    /** Names are stored; add new ones rather than renaming. */
    public enum Fulfilment {
        /** She sends it to the buyer. */
        HOME_DELIVERY,
        /** The buyer collects it. */
        PICKUP,
        /** A service done at the buyer's home - mehandi, beauty, tailoring fittings. */
        AT_YOUR_HOME,
        /** A service at the seller's place. */
        AT_SELLER_PLACE
    }

    public enum ReturnPolicy { NO_RETURNS, EXCHANGE_ONLY, RETURNS_ACCEPTED }
}
