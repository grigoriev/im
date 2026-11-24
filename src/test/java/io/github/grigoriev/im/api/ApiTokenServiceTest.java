package io.github.grigoriev.im.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.grigoriev.im.audit.AuditAction;
import io.github.grigoriev.im.audit.AuditService;
import io.github.grigoriev.im.user.AppUser;
import io.github.grigoriev.im.user.AppUserRepository;
import io.github.grigoriev.im.user.Role;
import io.github.grigoriev.im.user.UserTestData;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class ApiTokenServiceTest {

    private static final Instant NOW = Instant.parse("2027-03-18T08:12:00Z");
    private static final AppUser ELENA = UserTestData.user(2L, "elena", Role.USER);

    private final ApiTokenRepository tokens = mock(ApiTokenRepository.class);
    private final AppUserRepository users = mock(AppUserRepository.class);
    private final AuditService audit = mock(AuditService.class);
    private final ApiTokenService service = new ApiTokenService(tokens, users, audit, Clock.fixed(NOW, ZoneOffset.UTC));

    @BeforeEach
    void knownUser() {
        when(users.findByUsername("elena")).thenReturn(Optional.of(ELENA));
        when(users.findById(2L)).thenReturn(Optional.of(ELENA));
        when(tokens.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    private static ApiToken token(Instant expiresAt) {
        return new ApiToken(7L, 2L, "Claude", ApiTokenService.hash("im_" + "a".repeat(43)), ApiTokenScope.WRITE, NOW,
                expiresAt, null);
    }

    @Test
    void createsATokenAndKeepsOnlyItsHash() {
        CreatedToken created = service.create("elena", "  Claude Code ", ApiTokenScope.WRITE, 90);
        assertThat(created.secret()).startsWith("im_").hasSize(ApiTokenService.LENGTH).matches("im_[A-Za-z0-9_-]{43}");
        ApiToken token = created.token();
        assertThat(token.userId()).isEqualTo(2L);
        assertThat(token.name()).isEqualTo("Claude Code");
        assertThat(token.tokenHash()).isEqualTo(ApiTokenService.hash(created.secret())).hasSize(64)
                .doesNotContain(created.secret());
        assertThat(token.createdAt()).isEqualTo(NOW);
        assertThat(token.expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(90)));
        assertThat(token.lastUsedAt()).isNull();
        assertThat(created.toString()).doesNotContain(created.secret());
        verify(audit).add(AuditAction.API_TOKEN_CREATED, "Claude Code, Lesen und schreiben, 90 Tage");
        assertThat(service.create("elena", "Zwei", ApiTokenScope.READ, 1).secret()).isNotEqualTo(created.secret());
    }

    @Test
    void takesNamesUpToTheLimitAndLivesUpToAYear() {
        assertThat(service.create("elena", "n".repeat(64), ApiTokenScope.READ, 365).token().expiresAt())
                .isEqualTo(NOW.plus(Duration.ofDays(365)));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void refusesATokenWithoutAName(String name) {
        assertThatThrownBy(() -> service.create("elena", name, ApiTokenScope.READ, 30))
                .isInstanceOf(ApiTokenException.class).hasMessage("Bitte einen Namen mit höchstens 64 Zeichen angeben.");
    }

    @Test
    void refusesALongNameNoScopeAWrongLifeAndAnUnknownUser() {
        assertThatThrownBy(() -> service.create("elena", "n".repeat(65), ApiTokenScope.READ, 30))
                .hasMessage("Bitte einen Namen mit höchstens 64 Zeichen angeben.");
        assertThatThrownBy(() -> service.create("elena", "Claude", null, 30))
                .hasMessage("Bitte die Rechte des Tokens wählen.");
        assertThatThrownBy(() -> service.create("elena", "Claude", ApiTokenScope.READ, 0))
                .hasMessage("Ein Token gilt einen bis 365 Tage.");
        assertThatThrownBy(() -> service.create("elena", "Claude", ApiTokenScope.READ, 366))
                .hasMessage("Ein Token gilt einen bis 365 Tage.");
        assertThatThrownBy(() -> service.create("niemand", "Claude", ApiTokenScope.READ, 30))
                .hasMessage("Unbekannter Benutzer.");
        verify(tokens, never()).save(any());
    }

    @Test
    void listsTheTokensOfAUser() {
        List<ApiToken> list = List.of(token(NOW.plusSeconds(60)));
        when(tokens.findAllByUserIdOrderByCreatedAtDescIdDesc(2L)).thenReturn(list);
        assertThat(service.list("elena")).isEqualTo(list);
        assertThat(service.list("niemand")).isEmpty();
    }

    @Test
    void deletesOnlyAnOwnToken() {
        ApiToken own = token(NOW.plusSeconds(60));
        when(tokens.findById(7L)).thenReturn(Optional.of(own));
        when(tokens.findById(8L)).thenReturn(Optional.of(new ApiToken(8L, 3L, "Fremd", "h", ApiTokenScope.READ, NOW,
                NOW.plusSeconds(60), null)));
        assertThat(service.delete("elena", 7L)).isEqualTo(own);
        verify(tokens).deleteById(7L);
        verify(audit).add(AuditAction.API_TOKEN_DELETED, "Claude");
        assertThatThrownBy(() -> service.delete("elena", 8L)).isInstanceOf(ApiTokenException.class)
                .hasMessage("Diesen Token gibt es nicht.");
        assertThatThrownBy(() -> service.delete("elena", 9L)).hasMessage("Diesen Token gibt es nicht.");
        assertThatThrownBy(() -> service.delete("niemand", 7L)).hasMessage("Diesen Token gibt es nicht.");
        verify(tokens, never()).deleteById(8L);
    }

    @Test
    void acceptsAValidTokenAndRecordsItsUse() {
        String secret = "im_" + "a".repeat(43);
        when(tokens.findByTokenHash(ApiTokenService.hash(secret))).thenReturn(Optional.of(token(NOW.plusSeconds(1))));
        when(tokens.markUsed(7L, NOW)).thenReturn(1);
        ApiTokenLogin login = service.authenticate(secret).orElseThrow();
        assertThat(login.user()).isEqualTo(ELENA);
        assertThat(login.token().lastUsedAt()).isEqualTo(NOW);
        verify(tokens).markUsed(7L, NOW);
        verify(tokens, never()).save(any());
    }

    @Test
    void refusesATokenDeletedDuringTheRequest() {
        String secret = "im_" + "a".repeat(43);
        when(tokens.findByTokenHash(ApiTokenService.hash(secret))).thenReturn(Optional.of(token(NOW.plusSeconds(1))));
        when(tokens.markUsed(7L, NOW)).thenReturn(0);
        assertThat(service.authenticate(secret)).isEmpty();
    }

    @Test
    void refusesAnExpiredTokenAndATokenOfADisabledOrRemovedUser() {
        String secret = "im_" + "a".repeat(43);
        when(tokens.findByTokenHash(ApiTokenService.hash(secret))).thenReturn(Optional.of(token(NOW)));
        assertThat(service.authenticate(secret)).as("expires now").isEmpty();
        when(tokens.findByTokenHash(ApiTokenService.hash(secret))).thenReturn(Optional.of(token(NOW.plusSeconds(1))));
        when(users.findById(2L)).thenReturn(Optional.of(new AppUser(2L, "elena", "Elena", "{noop}x", Role.USER, false,
                UserTestData.CREATED)));
        assertThat(service.authenticate(secret)).as("disabled").isEmpty();
        when(users.findById(2L)).thenReturn(Optional.empty());
        assertThat(service.authenticate(secret)).as("removed").isEmpty();
        verify(tokens, never()).markUsed(anyLong(), any());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "im_kurz", "xx_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            "im_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "im_bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"})
    void refusesAMalformedOrUnknownToken(String secret) {
        assertThat(service.authenticate(secret)).isEmpty();
    }

    @Test
    void hashesWithSha256() {
        assertThat(ApiTokenService.hash("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }
}
