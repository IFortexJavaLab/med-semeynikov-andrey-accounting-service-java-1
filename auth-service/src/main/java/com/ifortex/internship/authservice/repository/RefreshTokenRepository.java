package com.ifortex.internship.authservice.repository;

import com.ifortex.internship.authservice.model.RefreshToken;
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
            "DELETE FROM RefreshToken rt WHERE rt.user.id = (SELECT u.id FROM User u WHERE u.email = :email)")
    void deleteRefreshTokenByUserEmail(@Param("email") String email);
}
