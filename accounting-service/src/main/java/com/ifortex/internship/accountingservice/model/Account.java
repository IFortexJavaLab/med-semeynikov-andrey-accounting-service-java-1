package com.ifortex.internship.accountingservice.model;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.ifortex.internship.accountingservice.model.constant.Provider;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@FilterDef(name = Account.FILTER_ACTIVE,
    autoEnabled = true,
    applyToLoadByKey = true,
    defaultCondition = "is_soft_deleted = 'false'")

@Filter(name = Account.FILTER_ACTIVE)
@Entity
@Table(name = "account")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class Account {

    public static final String FILTER_ACTIVE = "Active";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private UUID accountId = UUID.randomUUID();

    @Column(nullable = false)
    private String email;

    private String passwordHash;

    @JsonManagedReference
    @OneToOne(mappedBy = "account", cascade = CascadeType.ALL, orphanRemoval = true)
    private TemporaryPassword temporaryPassword;

    @Column(nullable = false)
    private boolean isSoftDeleted = false;

    @Column(nullable = false)
    private boolean isTwoFactorEnabled = false;

    private Instant blockedUntil;

    @Column(length = 50)
    private String firstName;

    @Column(length = 50)
    private String lastName;

    @Column(length = 15)
    private String phoneNumber;

    @CreationTimestamp
    @Column(nullable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Provider provider = Provider.INTERNAL;

    @JsonManagedReference
    @OneToOne(mappedBy = "account", cascade = CascadeType.REMOVE, orphanRemoval = true)
    private RefreshToken refreshToken;

    @ManyToOne()
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;
}