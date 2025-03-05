package com.ifortex.internship.authservice.repository;

import com.ifortex.internship.authservice.model.stripe.StripeSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface SubscriptionRepository extends JpaRepository<StripeSubscription, Long> {
    Optional<StripeSubscription> findByStripeSubscriptionId(String stripeSubscriptionId);

    List<StripeSubscription> findAllByStripeSubscriptionIdIn(Collection<String> stripeSubscriptionId);

    @Query("SELECT s FROM StripeSubscription s WHERE s.client.id = :userId AND s.status = 'ACTIVE'")
    Optional<StripeSubscription> findActiveSubscriptionByUserId(@Param("userId") Long userId);
}
