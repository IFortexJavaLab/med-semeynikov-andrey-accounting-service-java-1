package com.ifortex.internship.accountingservice.repository;

import com.ifortex.internship.accountingservice.model.Admin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AdminRepository extends JpaRepository<Admin, Long> {

    @Query("""
            SELECT adm FROM Admin adm
            JOIN Account acc ON adm.account.id = acc.id
            WHERE acc.accountId = :accountId
        """)
    Optional<Admin> findByAccountId(@Param("accountId") UUID accountId);

}
