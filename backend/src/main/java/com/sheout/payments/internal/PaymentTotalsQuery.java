package com.sheout.payments.internal;

import com.sheout.payments.PaymentTotalsApi;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;

/**
 * Sums straight from the ledger tables. payments.captured_at is a plain
 * timestamp written through the JVM's zone, so the bounds go in the same way
 * (Timestamp.from) and line up whatever zone the server runs in.
 */
@Service
class PaymentTotalsQuery implements PaymentTotalsApi {

    private final JdbcTemplate jdbc;

    PaymentTotalsQuery(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public Totals between(Instant from, Instant to) {
        Timestamp start = Timestamp.from(from);
        Timestamp end = Timestamp.from(to);
        Object[] fares = jdbc.queryForObject("""
                select count(*), coalesce(sum(amount), 0), coalesce(sum(amount - coalesce(driver_payout, amount)), 0)
                from payments where status = 'CAPTURED' and purpose = 'RIDE_FARE' and captured_at >= ? and captured_at < ?""",
                (rs, n) -> new Object[]{rs.getLong(1), rs.getBigDecimal(2), rs.getBigDecimal(3)}, start, end);
        BigDecimal listing = jdbc.queryForObject("""
                select coalesce(sum(amount), 0) from payments
                where status = 'CAPTURED' and purpose = 'SELLER_LISTING_FEE' and captured_at >= ? and captured_at < ?""",
                BigDecimal.class, start, end);
        Object[] refunds = jdbc.queryForObject("""
                select count(*), coalesce(sum(amount), 0) from rider_wallet_entries
                where entry_type = 'REFUND' and created_at >= ? and created_at < ?""",
                (rs, n) -> new Object[]{rs.getLong(1), rs.getBigDecimal(2)}, start, end);
        return new Totals((Long) fares[0], (BigDecimal) fares[1], (BigDecimal) fares[2], listing,
                (Long) refunds[0], ((BigDecimal) refunds[1]).abs());
    }
}
