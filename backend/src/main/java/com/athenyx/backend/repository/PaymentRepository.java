package com.athenyx.backend.repository;

import com.athenyx.backend.entity.Payment;
import com.athenyx.backend.entity.PaymentProvider;
import com.athenyx.backend.entity.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Page<Payment> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    @Query("""
        SELECT p FROM Payment p
        WHERE p.user.id = :userId
        AND p.status = :status
        ORDER BY p.createdAt DESC
        """)
    List<Payment> findByUserIdAndStatus(@Param("userId") Long userId, @Param("status") PaymentStatus status);

    @Query("""
        SELECT p FROM Payment p
        WHERE p.status = 'COMPLETED'
        AND p.expiresAt < :now
        AND p.canceledAt IS NULL
        """)
    List<Payment> findExpiredSubscriptions(@Param("now") LocalDateTime now);

    Optional<Payment> findByProviderAndProviderRef(PaymentProvider provider, String providerRef);

    Optional<Payment> findByProviderAndExternalReference(PaymentProvider provider, String externalReference);

    Optional<Payment> findByClaimToken(String claimToken);

    List<Payment> findByStatusAndCreatedAtBefore(PaymentStatus status, LocalDateTime cutoff);

    @Query("""
        SELECT p FROM Payment p
        WHERE p.user.id = :userId
        AND p.status = 'COMPLETED'
        ORDER BY p.completedAt DESC
        LIMIT 1
        """)
    Optional<Payment> findLatestCompletedByUserId(@Param("userId") Long userId);

    @Query("""
        SELECT COUNT(p) FROM Payment p
        WHERE p.status = 'COMPLETED'
        AND p.expiresAt > :now
        AND p.canceledAt IS NULL
        """)
    long countActiveSubscriptions(@Param("now") LocalDateTime now);

    @Query("""
        SELECT COUNT(p) FROM Payment p
        WHERE p.status = 'COMPLETED'
        AND p.expiresAt > :now
        AND p.canceledAt IS NULL
        AND p.completedAt BETWEEN :from AND :to
        """)
    long countNewActiveSubscriptions(
        @Param("now") LocalDateTime now,
        @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to);

    @Query("SELECT COUNT(p) FROM Payment p WHERE p.cancelRequestedAt IS NOT NULL")
    long countCanceledSubscriptions();

    @Query("""
        SELECT COUNT(p) FROM Payment p
        WHERE p.cancelRequestedAt BETWEEN :from AND :to
        """)
    long countCanceledSubscriptionsBetween(
        @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to);

    @Query("""
        SELECT COUNT(p) FROM Payment p
        WHERE p.status = 'COMPLETED'
        AND p.completedAt BETWEEN :from AND :to
        """)
    long countCompletedPaymentsBetween(
        @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to);
}
