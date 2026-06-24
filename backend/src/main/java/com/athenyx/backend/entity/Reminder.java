package com.athenyx.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * A user-defined reminder attached to a single {@link Email}.
 *
 * <p>One row per (user, email) — enforced by the
 * {@code uk_reminder_user_email} unique constraint. The owning
 * {@link User} is denormalised as a {@code ManyToOne} so reminder
 * queries never need to JOIN through {@code Email} to scope by
 * account.</p>
 */
@Entity
@Table(
    name = "reminders",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_reminder_user_email",
        columnNames = {"user_id", "email_id"}
    )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Reminder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "email_id", nullable = false)
    private Email email;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "reminder_date", nullable = false)
    private LocalDateTime reminderDate;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Column(nullable = false)
    @Builder.Default
    private boolean done = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
