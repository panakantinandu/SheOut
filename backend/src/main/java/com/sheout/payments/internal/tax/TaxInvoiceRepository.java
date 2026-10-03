package com.sheout.payments.internal.tax;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface TaxInvoiceRepository extends JpaRepository<TaxInvoiceEntity, UUID> {

    Optional<TaxInvoiceEntity> findByPaymentId(UUID paymentId);

    Optional<TaxInvoiceEntity> findFirstByBookingId(UUID bookingId);

    List<TaxInvoiceEntity> findByFinancialYearOrderBySequenceNumberAsc(String financialYear);

    /**
     * Takes the next number in a financial year, inside the caller's
     * transaction: creates the year's counter if it is the first invoice,
     * then increments it under a row lock and returns the number taken. A
     * capture that rolls back rolls the number back with it - which is what
     * keeps the numbering free of gaps. Native SQL because "insert if absent"
     * and "update ... returning" are not JPQL.
     */
    @Modifying
    @Query(value = "insert into tax_invoice_sequences (financial_year, next_value, created_at, updated_at)"
            + " values (:fy, 1, now(), now()) on conflict (financial_year) do nothing", nativeQuery = true)
    void ensureSequence(@Param("fy") String financialYear);

    /** Locks the year's counter until the transaction ends; another capture waits here rather than sharing a number. */
    @Query(value = "select next_value from tax_invoice_sequences where financial_year = :fy for update", nativeQuery = true)
    long lockNext(@Param("fy") String financialYear);

    @Modifying
    @Query(value = "update tax_invoice_sequences set next_value = :next, updated_at = now() where financial_year = :fy",
            nativeQuery = true)
    void setNext(@Param("fy") String financialYear, @Param("next") long next);
}
