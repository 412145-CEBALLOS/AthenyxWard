package com.athenyx.backend.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Cache of Gmail API pagination tokens, one row per {@code (user, page)}.
 *
 * <p>When the upstream {@code historyId} changes (new mail arrived) all
 * rows for the user are deleted by
 * {@code GmailService#fetchEmails}, so the next fetch starts from the
 * latest message.</p>
 */
@Entity
@Table(name = "gmail_page_tokens", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"userId", "page"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GmailPageToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private int page;
    private String token;
}