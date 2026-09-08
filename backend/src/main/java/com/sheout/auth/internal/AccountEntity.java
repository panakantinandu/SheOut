package com.sheout.auth.internal;

import com.sheout.auth.AccountRole;
import com.sheout.sharedkernel.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "accounts")
public class AccountEntity extends BaseEntity {

    @Column(nullable = false, unique = true, length = 20)
    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountRole role;

    protected AccountEntity() {
        // JPA
    }

    public AccountEntity(String phoneNumber, AccountRole role) {
        this.phoneNumber = phoneNumber;
        this.role = role;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public AccountRole getRole() {
        return role;
    }
}
