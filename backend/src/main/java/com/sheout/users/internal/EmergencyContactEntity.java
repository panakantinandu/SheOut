package com.sheout.users.internal;

import com.sheout.sharedkernel.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Owned by a {@link CustomerProfileEntity} - a real JPA relationship is
 * fine here (unlike accountId elsewhere in this module), since both
 * entities belong to this module. Nothing outside users ever sees this
 * class; other modules read contacts through
 * {@link com.sheout.users.EmergencyContactsApi} only.
 */
@Entity
@Table(name = "emergency_contacts")
public class EmergencyContactEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_profile_id", nullable = false)
    private CustomerProfileEntity customerProfile;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, length = 20)
    private String phoneNumber;

    @Column(nullable = false, length = 50)
    private String relationship;

    protected EmergencyContactEntity() {
        // JPA
    }

    public EmergencyContactEntity(CustomerProfileEntity customerProfile, String name, String phoneNumber, String relationship) {
        this.customerProfile = customerProfile;
        this.name = name;
        this.phoneNumber = phoneNumber;
        this.relationship = relationship;
    }

    public CustomerProfileEntity getCustomerProfile() {
        return customerProfile;
    }

    public String getName() {
        return name;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public String getRelationship() {
        return relationship;
    }
}
