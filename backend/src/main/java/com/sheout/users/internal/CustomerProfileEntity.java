package com.sheout.users.internal;

import com.sheout.sharedkernel.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * accountId is a plain UUID column, not a JPA relationship into auth's
 * table - same reasoning as every other module: no cross-module foreign
 * keys, only auth's public API and events.
 */
@Entity
@Table(name = "customer_profiles")
public class CustomerProfileEntity extends BaseEntity {

    @Column(nullable = false, unique = true)
    private UUID accountId;

    @Column(length = 150)
    private String name;

    @Column(length = 500)
    private String homeAddress;

    @Column(length = 500)
    private String workAddress;

    @Column(nullable = false)
    private boolean verified = false;

    protected CustomerProfileEntity() {
        // JPA
    }

    /** Created empty on AccountRegistered - a name isn't known at signup time, filled in later by the client. */
    public CustomerProfileEntity(UUID accountId) {
        this.accountId = accountId;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getHomeAddress() {
        return homeAddress;
    }

    public void setHomeAddress(String homeAddress) {
        this.homeAddress = homeAddress;
    }

    public String getWorkAddress() {
        return workAddress;
    }

    public void setWorkAddress(String workAddress) {
        this.workAddress = workAddress;
    }

    public boolean isVerified() {
        return verified;
    }

    public void setVerified(boolean verified) {
        this.verified = verified;
    }
}
