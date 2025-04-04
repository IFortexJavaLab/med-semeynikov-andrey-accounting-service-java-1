package com.ifortex.internship.accountingservice.repository;

import com.ifortex.internship.accountingservice.model.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByToken(String token);

    @Modifying
    @Query(
        "DELETE FROM RefreshToken rt WHERE rt.account.id = (SELECT a.id FROM Account a WHERE a.email = :email)")
    void deleteRefreshTokenByAccountEmail(@Param("email") String email);
}
