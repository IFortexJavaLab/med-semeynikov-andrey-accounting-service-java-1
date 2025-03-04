package com.ifortex.internship.authservice.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class TemporaryPassword {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonBackReference
    @OneToOne
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    private String temporaryPasswordHash;

    private Instant expirationDate;

    public TemporaryPassword(Account account, String temporaryPasswordHash, Instant expirationDate) {
        this.account = account;
        this.temporaryPasswordHash = temporaryPasswordHash;
        this.expirationDate = expirationDate;
    }
}
