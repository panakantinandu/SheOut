package com.sheout.marketplace.internal;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Buyers asking about a product - a tap on Call or WhatsApp - counted once
 * per buyer, product and day (product_enquiries, V69). Only counts ever
 * leave here: who asked is never shown to the seller.
 */
@Repository
class ProductEnquiries {

    private final JdbcTemplate jdbc;

    ProductEnquiries(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Once a day per buyer and product; a second tap is the same enquiry. */
    void record(UUID productId, UUID accountId, LocalDate day) {
        jdbc.update("insert into product_enquiries (product_id, account_id, day) values (?, ?, ?) on conflict do nothing",
                productId, accountId, Date.valueOf(day));
    }

    /** Enquiries on the day, by product, for these products. */
    Map<UUID, Integer> countsOn(Collection<UUID> productIds, LocalDate day) {
        Map<UUID, Integer> counts = new HashMap<>();
        if (productIds.isEmpty()) {
            return counts;
        }
        String in = String.join(",", productIds.stream().map(id -> "?").toList());
        Object[] args = new Object[productIds.size() + 1];
        args[0] = Date.valueOf(day);
        int i = 1;
        for (UUID id : productIds) {
            args[i++] = id;
        }
        jdbc.query("select product_id, count(*) from product_enquiries where day = ? and product_id in (" + in + ") group by product_id",
                rs -> {
                    counts.put(rs.getObject(1, UUID.class), rs.getInt(2));
                }, args);
        return counts;
    }

    /** Every product with an enquiry on the day. */
    List<UUID> productsAskedAbout(LocalDate day) {
        return jdbc.queryForList("select distinct product_id from product_enquiries where day = ?", UUID.class, Date.valueOf(day));
    }
}
