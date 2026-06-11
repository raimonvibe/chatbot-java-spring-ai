package com.prayer_chat.chatbot.repository;

import com.prayer_chat.chatbot.model.Subscription;
import com.prayer_chat.chatbot.model.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for managing Subscription entities
 */
@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    Optional<Subscription> findByUser(User user);

    Optional<Subscription> findByUserId(Long userId);

    Optional<Subscription> findByStripeCustomerId(String stripeCustomerId);

    Optional<Subscription> findByStripeSubscriptionId(String stripeSubscriptionId);

    /**
     * Pessimistic lock for read-modify-write webhook handlers (payment failure/success)
     * so concurrent Stripe deliveries can't interleave and corrupt retry counters.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Subscription s WHERE s.stripeSubscriptionId = :stripeSubscriptionId")
    Optional<Subscription> findByStripeSubscriptionIdWithLock(@Param("stripeSubscriptionId") String stripeSubscriptionId);
}
