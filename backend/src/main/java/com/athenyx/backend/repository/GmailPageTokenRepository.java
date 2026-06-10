package com.athenyx.backend.repository;

import com.athenyx.backend.entity.GmailPageToken;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for the Gmail pagination cache.
 */
public interface GmailPageTokenRepository extends JpaRepository<GmailPageToken, Long> {
    /**
     * Looks up a cached next-page token for the user.
     */
    Optional<GmailPageToken> findByUserIdAndPage(Long userId, int page);

    /**
     * Wipes the cache for a user. Invoked when the upstream
     * {@code historyId} changes.
     *
     * @param userId user id
     */
    @Transactional
    void deleteAllByUserId(Long userId);

    /**
     * Inserts the token only if no row exists for {@code (userId, page)}.
     * Implemented as a default method on the interface to keep the call
     * site short.
     */
    default void saveIfAbsent(GmailPageToken token) {
        findByUserIdAndPage(token.getUserId(), token.getPage())
            .orElseGet(() -> save(token));
    }
}
