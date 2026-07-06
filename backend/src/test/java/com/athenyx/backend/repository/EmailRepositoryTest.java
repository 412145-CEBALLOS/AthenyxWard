package com.athenyx.backend.repository;

import com.athenyx.backend.entity.Email;
import com.athenyx.backend.entity.Role;
import com.athenyx.backend.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for {@link EmailRepository#searchByUserAndTerm}
 * (US 3.7 — search bar). Boots the full Spring context against the
 * H2/MySQL-mode test database and exercises the {@code @Query} LIKE
 * against real persisted rows. Verifies:
 *
 * <ul>
 *   <li>Case-insensitive matching (the {@code LOWER()} wrapping
 *       works under H2's MySQL mode, which is case-sensitive by
 *       default).</li>
 *   <li>Match across all four searched columns: subject, sender,
 *       senderName, snippet.</li>
 *   <li>User scoping: a query for user A never returns user B's
 *       rows.</li>
 *   <li>Newest-first ordering.</li>
 *   <li>Pagination metadata (page number, size, hasNext).</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EmailRepositoryTest {

    @Autowired private EmailRepository emailRepository;
    @Autowired private UserRepository userRepository;

    private User alice;
    private User bob;

    @BeforeEach
    void setUp() {
        emailRepository.deleteAll();
        userRepository.deleteAll();
        alice = userRepository.save(User.builder()
            .googleId("g-alice").email("alice@example.com").name("Alice")
            .role(Role.PREMIUM).build());
        bob = userRepository.save(User.builder()
            .googleId("g-bob").email("bob@example.com").name("Bob")
            .role(Role.PREMIUM).build());

        save(alice, "PayPal receipt", "noreply@paypal.com", "PayPal", "you got money", 1);
        save(alice, "Your invoice", "billing@acme.com", "Acme Corp", "thanks for your business", 2);
        save(alice, "Newsletter", "news@techcrunch.com", "TechCrunch", "weekly digest", 3);
        save(alice, "PAYPAL security alert", "alert@paypal.com", "PayPal", "verify your account", 4);
        save(alice, "Random note", "juan@example.com", "Juan Pérez", "Hola, ¿qué tal?", 5);
        save(bob,   "PayPal payment", "noreply@paypal.com", "PayPal", "Bob got money", 6);
    }

    private void save(User owner, String subject, String sender, String senderName,
                      String snippet, int daysAgo) {
        emailRepository.save(Email.builder()
            .gmailId("g-" + daysAgo + "-" + subject.hashCode())
            .sender(sender)
            .senderName(senderName)
            .subject(subject)
            .snippet(snippet)
            .contentForAnalysis("body " + subject)
            .receivedAt(LocalDateTime.now().minusDays(daysAgo))
            .originalDateHeader("now")
            .isRead(false)
            .isImportant(false)
            .user(owner)
            .build());
    }

    private Pageable firstPage() {
        return PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "receivedAt"));
    }

    @Test
    void searchByUserAndTerm_isCaseInsensitive() {
        Page<Email> result = emailRepository.searchByUserAndTerm(alice.getId(), "paypal", firstPage());

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent())
            .extracting(Email::getSubject)
            .containsExactlyInAnyOrder("PayPal receipt", "PAYPAL security alert");
    }

    @Test
    void searchByUserAndTerm_matchesLowercaseQueryAgainstCapitalisedContent() {
        Page<Email> result = emailRepository.searchByUserAndTerm(alice.getId(), "PAYPAL", firstPage());

        assertThat(result.getTotalElements()).isEqualTo(2);
    }

    @Test
    void searchByUserAndTerm_matchesSubjectColumn() {
        Page<Email> result = emailRepository.searchByUserAndTerm(alice.getId(), "invoice", firstPage());

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getSubject()).isEqualTo("Your invoice");
    }

    @Test
    void searchByUserAndTerm_matchesSenderColumn() {
        Page<Email> result = emailRepository.searchByUserAndTerm(alice.getId(), "acme.com", firstPage());

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getSender()).isEqualTo("billing@acme.com");
    }

    @Test
    void searchByUserAndTerm_matchesSenderNameColumn() {
        Page<Email> result = emailRepository.searchByUserAndTerm(alice.getId(), "juan", firstPage());

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getSenderName()).isEqualTo("Juan Pérez");
    }

    @Test
    void searchByUserAndTerm_matchesSnippetColumn() {
        Page<Email> result = emailRepository.searchByUserAndTerm(alice.getId(), "weekly", firstPage());

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getSnippet()).isEqualTo("weekly digest");
    }

    @Test
    void searchByUserAndTerm_scopesResultsToOwningUser() {
        // 'paypal' would match Bob's row too, but searching as Alice
        // must NOT return Bob's emails.
        Page<Email> result = emailRepository.searchByUserAndTerm(alice.getId(), "paypal", firstPage());

        assertThat(result.getContent())
            .allMatch(e -> e.getUser().getId().equals(alice.getId()));
    }

    @Test
    void searchByUserAndTerm_returnsEmptyPageWhenNoMatch() {
        Page<Email> result = emailRepository.searchByUserAndTerm(alice.getId(), "no-such-text", firstPage());

        assertThat(result.getTotalElements()).isZero();
        assertThat(result.getContent()).isEmpty();
        assertThat(result.hasNext()).isFalse();
    }

    @Test
    void searchByUserAndTerm_ordersByReceivedAtDesc() {
        Page<Email> result = emailRepository.searchByUserAndTerm(alice.getId(), "paypal", firstPage());

        // Alice's two paypal rows: 'PayPal receipt' (1 day ago, newest)
        // and 'PAYPAL security alert' (4 days ago, older). Newest first.
        assertThat(result.getContent())
            .extracting(Email::getSubject)
            .containsExactly("PayPal receipt", "PAYPAL security alert");
    }

    @Test
    void searchByUserAndTerm_paginatesResults() {
        // Page size 1, ordered newest first, so page 0 is the
        // 1-day-ago row, page 1 is the 4-days-ago row.
        Pageable page0 = PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "receivedAt"));
        Pageable page1 = PageRequest.of(1, 1, Sort.by(Sort.Direction.DESC, "receivedAt"));

        Page<Email> p0 = emailRepository.searchByUserAndTerm(alice.getId(), "paypal", page0);
        Page<Email> p1 = emailRepository.searchByUserAndTerm(alice.getId(), "paypal", page1);

        assertThat(p0.getTotalElements()).isEqualTo(2);
        assertThat(p0.getNumberOfElements()).isEqualTo(1);
        assertThat(p0.hasNext()).isTrue();
        assertThat(p0.getContent().get(0).getSubject()).isEqualTo("PayPal receipt");

        assertThat(p1.getTotalElements()).isEqualTo(2);
        assertThat(p1.getNumberOfElements()).isEqualTo(1);
        assertThat(p1.hasNext()).isFalse();
        assertThat(p1.getContent().get(0).getSubject()).isEqualTo("PAYPAL security alert");
    }
}
