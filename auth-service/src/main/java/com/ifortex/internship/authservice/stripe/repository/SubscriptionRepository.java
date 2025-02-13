package com.ifortex.internship.authservice.stripe.repository;

import com.ifortex.internship.authservice.stripe.model.Subscription;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
  Optional<Subscription> findByStripeSubscriptionId(String stripeSubscriptionId);

  @Query("SELECT s FROM Subscription s WHERE s.user.id = :userId AND s.status = 'ACTIVE'")
  Optional<Subscription> findActiveSubscriptionByUserId(@Param("userId") Long userId);
}
