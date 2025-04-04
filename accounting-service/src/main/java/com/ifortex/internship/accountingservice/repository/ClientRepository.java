package com.ifortex.internship.accountingservice.repository;

import com.ifortex.internship.accountingservice.model.Client;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClientRepository extends JpaRepository<Client, Long> {

    Optional<Client> findByStripeId(String stripeCustomerId);

    @Query("""
            SELECT c.stripeId 
            FROM Client c
            JOIN Account a ON c.account.id = a.id
            WHERE a.accountId = :accountId
        """)
    Optional<String> findStripeIdByAccountId(@Param("accountId") UUID accountId);

    @Query("""
            SELECT c FROM Client c
            JOIN Account a ON c.account.id = a.id
            WHERE a.accountId = :accountId
        """)
    Optional<Client> findByAccountId(@Param("accountId") UUID accountId);
}
