package io.github.grigoriev.im.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.grigoriev.im.user.AppUserDetailsService;
import io.github.grigoriev.im.user.AppUserRepository;
import io.github.grigoriev.im.user.Role;
import io.github.grigoriev.im.user.UserException;
import io.github.grigoriev.im.user.UserService;
import io.github.grigoriev.im.user.UserTestData;
import io.github.grigoriev.im.user.Passkey;
import io.github.grigoriev.im.user.PasskeyService;
import io.github.grigoriev.im.einvoice.TaxCategory;
import io.github.grigoriev.im.seller.SellerProfile;
import io.github.grigoriev.im.seller.SellerService;
import io.github.grigoriev.im.seller.SellerTestData;
import io.github.grigoriev.im.seller.SettingsException;
import io.github.grigoriev.im.seller.TaxNoteService;
import io.github.grigoriev.im.seller.TaxStatus;
import io.github.grigoriev.im.numbering.NumberPattern;
import io.github.grigoriev.im.numbering.NumberSequence;
import io.github.grigoriev.im.numbering.NumberService;
import io.github.grigoriev.im.customer.Customer;
import io.github.grigoriev.im.customer.CustomerException;
import io.github.grigoriev.im.customer.CustomerService;
import io.github.grigoriev.im.customer.CustomerTestData;
import io.github.grigoriev.im.country.Country;
import io.github.grigoriev.im.country.CountryService;
import io.github.grigoriev.im.audit.AuditAction;
import io.github.grigoriev.im.audit.AuditEvent;
import io.github.grigoriev.im.audit.AuditService;
import io.github.grigoriev.im.web.AccountController;
import io.github.grigoriev.im.web.AuditController;
import io.github.grigoriev.im.web.CountryController;
import io.github.grigoriev.im.web.CustomerController;
import io.github.grigoriev.im.web.NumberController;
import io.github.grigoriev.im.web.SettingsController;
import io.github.grigoriev.im.web.TaxNoteController;
import io.github.grigoriev.im.web.AdminUserController;
import io.github.grigoriev.im.web.CurrentUserAdvice;
import io.github.grigoriev.im.web.HomeController;
import io.github.grigoriev.im.web.LoginController;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** The access rules of {@link SecurityConfig} with the real controllers and templates. */
@WebMvcTest(controllers = {HomeController.class, LoginController.class, AdminUserController.class, AccountController.class,
        SettingsController.class, TaxNoteController.class, NumberController.class,
        CustomerController.class, CountryController.class,
        AuditController.class})
@Import({SecurityConfig.class, StaleFormHandler.class, CurrentUserAdvice.class})
@org.springframework.boot.context.properties.EnableConfigurationProperties(PasskeyProperties.class)
class SecurityWebTest {

    private static final String PASSWORD = "lang-genug-123";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private PasswordEncoder encoder;

    @MockitoBean
    private AppUserRepository repository;

    @MockitoBean
    private AppUserDetailsService details;

    @MockitoBean
    private UserService users;

    @MockitoBean
    private PersistentTokenRepository tokens;

    @MockitoBean
    private PasskeyService passkeys;

    @MockitoBean
    private SellerService sellers;

    @MockitoBean
    private TaxNoteService taxNotes;

    @MockitoBean
    private NumberService numbers;

    @MockitoBean
    private CustomerService customers;

    @MockitoBean
    private CountryService countries;

    @MockitoBean
    private AuditService audit;

    private void knownUsers() {
        when(repository.findByUsername("admin")).thenReturn(Optional.of(UserTestData.user(1, "admin", Role.ADMIN)));
        when(repository.findByUsername("elena")).thenReturn(Optional.of(UserTestData.user(2, "elena", Role.USER)));
    }

    @Test
    void sendsAnonymousVisitorsToTheLogin() throws Exception {
        mvc.perform(get("/")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
        mvc.perform(get("/admin/benutzer")).andExpect(status().is3xxRedirection());
    }

    @Test
    void showsTheLoginPageWithItsMessagesAndTheCsp() throws Exception {
        mvc.perform(get("/login")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Anmelden")))
                .andExpect(header().string("Content-Security-Policy", SecurityConfig.CSP));
        mvc.perform(get("/login").param("error", ""))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Benutzername oder Passwort ist falsch.")));
        mvc.perform(get("/login").param("abgemeldet", ""))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Sie sind abgemeldet.")));
    }

    @Test
    void logsInWithThePasswordAndRemembersOnRequest() throws Exception {
        String hash = encoder.encode(PASSWORD);
        // A fresh user per call: Spring Security erases the password of the object after a login.
        when(details.loadUserByUsername("elena")).thenAnswer(
                call -> User.withUsername("elena").password(hash).authorities("ROLE_USER").build());
        mvc.perform(post("/login").param("username", "elena").param("password", PASSWORD).with(csrf()))
                .andExpect(redirectedUrl("/")).andExpect(authenticated().withUsername("elena"));
        mvc.perform(post("/login").param("username", "elena").param("password", PASSWORD)
                        .param("angemeldet-bleiben", "on").with(csrf()))
                .andExpect(cookie().exists("remember-me"))
                .andExpect(cookie().maxAge("remember-me", (int) SecurityConfig.REMEMBER_ME.toSeconds()));
        verify(tokens).createNewToken(any());
        mvc.perform(post("/login").param("username", "elena").param("password", "falsch-falsch").with(csrf()))
                .andExpect(redirectedUrl("/login?error")).andExpect(unauthenticated());
    }

    @Test
    void handlesAFormWithoutValidCsrfToken() throws Exception {
        mvc.perform(post("/login").param("username", "elena").param("password", PASSWORD))
                .andExpect(redirectedUrl("/login?abgelaufen"));
        mvc.perform(post("/logout").with(user("elena")).with(csrf().useInvalidToken()).header("Origin", "http://localhost"))
                .andExpect(redirectedUrl("/login?abgemeldet"));
        mvc.perform(post("/logout").with(user("elena")).with(csrf().useInvalidToken())
                        .header("Origin", "https://boese.example"))
                .andExpect(redirectedUrl("/login?abgelaufen"));
        when(tokens.getTokenForSeries("serie")).thenReturn(new org.springframework.security.web.authentication
                .rememberme.PersistentRememberMeToken("elena", "serie", "wert", new java.util.Date()));
        String cookie = java.util.Base64.getEncoder().withoutPadding().encodeToString("serie:wert".getBytes());
        mvc.perform(post("/logout").with(csrf().useInvalidToken()).header("Sec-Fetch-Site", "same-origin")
                        .cookie(new jakarta.servlet.http.Cookie("remember-me", cookie)))
                .andExpect(redirectedUrl("/login?abgemeldet"))
                .andExpect(cookie().maxAge("remember-me", 0));
        verify(tokens).removeUserTokens("elena");
        mvc.perform(get("/login").param("abgelaufen", ""))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Die Sitzung ist abgelaufen.")));
    }

    @Test
    void logsOut() throws Exception {
        mvc.perform(post("/logout").with(user("elena")).with(csrf())).andExpect(redirectedUrl("/login?abgemeldet"));
    }

    @Test
    void greetsTheUserAndHidesTheAdminMenu() throws Exception {
        knownUsers();
        mvc.perform(get("/").with(user("elena").roles("USER"))).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Guten Tag, Name elena")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("/admin/benutzer"))));
    }

    @Test
    void keepsTheAdminAreaForAdmins() throws Exception {
        knownUsers();
        mvc.perform(get("/admin/benutzer").with(user("elena").roles("USER"))).andExpect(status().isForbidden());
        when(users.list()).thenReturn(List.of(UserTestData.user(1, "admin", Role.ADMIN),
                UserTestData.user(2, "elena", Role.USER)));
        when(passkeys.count("elena")).thenReturn(1);
        mvc.perform(get("/admin/benutzer").with(user("admin").roles("ADMIN"))).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Name elena")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Neuer Benutzer")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("1 Passkey(s)")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Passkeys entfernen")));
    }

    @Test
    void createsAUserAndReportsAProblem() throws Exception {
        knownUsers();
        mvc.perform(post("/admin/benutzer").with(user("admin").roles("ADMIN")).with(csrf())
                        .param("username", "neu").param("displayName", "Neu").param("password", PASSWORD)
                        .param("role", "USER"))
                .andExpect(redirectedUrl("/admin/benutzer"))
                .andExpect(flash().attribute("success", "Benutzer „neu“ angelegt."));
        verify(users).create("neu", "Neu", PASSWORD, Role.USER);
        when(users.create(eq("x"), any(), any(), any())).thenThrow(new UserException("Kaputt."));
        mvc.perform(post("/admin/benutzer").with(user("admin").roles("ADMIN")).with(csrf())
                        .param("username", "x").param("displayName", "X").param("password", "p").param("role", "ADMIN"))
                .andExpect(flash().attribute("error", "Kaputt."))
                .andExpect(flash().attribute("username", "x"))
                .andExpect(flash().attribute("displayName", "X"));
    }

    @Test
    void greetsAUserWithoutAccountRecordByTheLoginName() throws Exception {
        when(repository.findByUsername("ohne-datensatz")).thenReturn(Optional.empty());
        mvc.perform(get("/").with(user("ohne-datensatz").roles("USER"))).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Guten Tag, ohne-datensatz")));
    }

    @Test
    void offersPasskeyLoginToAnonymousVisitors() throws Exception {
        mvc.perform(get("/login")).andExpect(content().string(org.hamcrest.Matchers.containsString("Mit Passkey anmelden")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"_csrf_header\"")));
        mvc.perform(post("/webauthn/authenticate/options").with(csrf())).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"challenge\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"rpId\":\"localhost\"")));
        mvc.perform(post("/webauthn/register/options").with(csrf())).andExpect(status().is4xxClientError());
    }

    @Test
    void showsTheAccountWithItsPasskeys() throws Exception {
        knownUsers();
        when(passkeys.list("elena")).thenReturn(List.of(new Passkey("id1", "MacBook Air", UserTestData.CREATED, null),
                new Passkey("id2", "iPhone", UserTestData.CREATED, UserTestData.CREATED)));
        mvc.perform(get("/konto").with(user("elena").roles("USER"))).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("MacBook Air")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Hinzugefügt am 02.01.2027 · zuletzt 02.01.2027")));
        when(passkeys.list("admin")).thenReturn(List.of());
        mvc.perform(get("/konto").with(user("admin").roles("ADMIN")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Noch kein Passkey.")));
    }

    @Test
    void renamesAndRemovesOwnPasskeysAndReportsProblems() throws Exception {
        mvc.perform(post("/konto/passkeys/id1/name").param("label", "Neu").with(user("elena")).with(csrf()))
                .andExpect(redirectedUrl("/konto")).andExpect(flash().attribute("success", "Passkey umbenannt."));
        verify(passkeys).rename("elena", "id1", "Neu");
        doThrow(new UserException("Fremd.")).when(passkeys).rename("elena", "x", "Neu");
        mvc.perform(post("/konto/passkeys/x/name").param("label", "Neu").with(user("elena")).with(csrf()))
                .andExpect(flash().attribute("error", "Fremd."));
        mvc.perform(post("/konto/passkeys/id1/loeschen").with(user("elena")).with(csrf()))
                .andExpect(flash().attribute("success", "Passkey entfernt."));
        verify(passkeys).remove("elena", "id1");
        doThrow(new UserException("Fremd.")).when(passkeys).remove("elena", "x");
        mvc.perform(post("/konto/passkeys/x/loeschen").with(user("elena")).with(csrf()))
                .andExpect(flash().attribute("error", "Fremd."));
    }

    @Test
    void changesTheOwnPasswordAndReportsAProblem() throws Exception {
        mvc.perform(post("/konto/passwort").param("current", "alt").param("password", PASSWORD).with(user("elena")).with(csrf()))
                .andExpect(flash().attribute("success", "Passwort geändert."));
        verify(users).changePassword("elena", "alt", PASSWORD);
        doThrow(new UserException("Falsch.")).when(users).changePassword("elena", "x", PASSWORD);
        mvc.perform(post("/konto/passwort").param("current", "x").param("password", PASSWORD).with(user("elena")).with(csrf()))
                .andExpect(flash().attribute("error", "Falsch."));
    }

    @Test
    void theAdminRemovesThePasskeysOfAUser() throws Exception {
        when(users.get(2)).thenReturn(UserTestData.user(2, "elena", Role.USER));
        when(passkeys.removeAll("elena")).thenReturn(2);
        mvc.perform(post("/admin/benutzer/2/passkeys/loeschen").with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(flash().attribute("success", "2 Passkey(s) von „elena“ entfernt."));
        when(users.get(9)).thenThrow(new UserException("Diesen Benutzer gibt es nicht."));
        mvc.perform(post("/admin/benutzer/9/passkeys/loeschen").with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(flash().attribute("error", "Diesen Benutzer gibt es nicht."));
        mvc.perform(post("/admin/benutzer/2/passkeys/loeschen").with(user("elena").roles("USER")).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void showsAnEmptyProfileAndThenTheSavedOne() throws Exception {
        when(sellers.find()).thenReturn(Optional.empty());
        mvc.perform(get("/einstellungen").with(user("elena").roles("USER"))).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Kleinunternehmer nach § 19 UStG")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Steuerhinweise"))));
        when(sellers.find()).thenReturn(Optional.of(SellerTestData.profile()));
        mvc.perform(get("/einstellungen").with(user("admin").roles("ADMIN")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("value=\"Erika Mustermann\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("value=\"DE89 3704 0044 0532 0130 00\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Steuerhinweise")));
    }

    @Test
    void savesTheProfileOrShowsTheFormWithTheProblems() throws Exception {
        mvc.perform(post("/einstellungen").param("name", "Erika Mustermann").param("iban", "DE89 3704")
                        .param("taxStatus", "REGULAR").with(user("elena").roles("USER")).with(csrf()))
                .andExpect(redirectedUrl("/einstellungen")).andExpect(flash().attribute("success", "Profil gespeichert."));
        verify(sellers).save(argThat(profile -> profile.name().equals("Erika Mustermann")
                && profile.taxStatus() == TaxStatus.REGULAR));
        when(sellers.save(any())).thenThrow(new SettingsException(List.of("Die IBAN ist ungültig.", "Bitte Ort angeben.")));
        mvc.perform(post("/einstellungen").param("name", "Erika Mustermann").param("iban", "DE00").param("countryCode", "JP")
                        .with(user("elena").roles("USER")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Die IBAN ist ungültig.")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Bitte Ort angeben.")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("value=\"Erika Mustermann\"")));
        verify(countries).options(null, "JP");
        mvc.perform(post("/einstellungen").param("name", "x").with(csrf())).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
        verify(sellers, times(2)).save(any());
    }

    @Test
    void keepsTheTaxNotesForTheAdmin() throws Exception {
        when(taxNotes.notes()).thenReturn(Map.of(TaxCategory.EXEMPT_4_21, "Steuerfrei gemäß § 4 Nr. 21 UStG."));
        mvc.perform(get("/admin/steuerhinweise").with(user("admin").roles("ADMIN"))).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Steuerfreie Bildungsleistung (§ 4 Nr. 21 UStG)")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"note-EXEMPT_4_21\"")));
        mvc.perform(get("/admin/steuerhinweise").with(user("elena").roles("USER"))).andExpect(status().isForbidden());
        mvc.perform(post("/admin/steuerhinweise").param("note-EXEMPT_4_21", "Neu.").param("note-STANDARD_19", "x")
                        .param("other", "y").with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(redirectedUrl("/admin/steuerhinweise"))
                .andExpect(flash().attribute("success", "Steuerhinweise gespeichert."));
        verify(taxNotes).save(Map.of(TaxCategory.EXEMPT_4_21, "Neu."));
        doThrow(new SettingsException(List.of("Zu lang."))).when(taxNotes).save(Map.of());
        mvc.perform(post("/admin/steuerhinweise").with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(flash().attribute("error", "Zu lang."));
    }

    @Test
    void showsThePatternAndTheCountersToTheAdmin() throws Exception {
        NumberSequence used = new NumberSequence(2027, 8, true);
        NumberSequence open = new NumberSequence(2028, 1, false);
        when(numbers.pattern()).thenReturn(NumberPattern.DEFAULT);
        when(numbers.years()).thenReturn(List.of(used, open));
        when(numbers.preview(used)).thenReturn("2027-08");
        when(numbers.preview(open)).thenReturn("2028-01");
        mvc.perform(get("/admin/nummernkreis").with(user("admin").roles("ADMIN"))).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("value=\"{YYYY}-{NN}\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("2027-08")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Der Zähler ist gesperrt.")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"start-2028\"")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("id=\"start-2027\""))));
        mvc.perform(get("/admin/nummernkreis").with(user("elena").roles("USER"))).andExpect(status().isForbidden());
    }

    @Test
    void savesThePatternAndTheStartAndReportsProblems() throws Exception {
        mvc.perform(post("/admin/nummernkreis/muster").param("pattern", "RE-{YYYY}-{NNN}")
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(redirectedUrl("/admin/nummernkreis")).andExpect(flash().attribute("success", "Muster gespeichert."));
        verify(numbers).savePattern("RE-{YYYY}-{NNN}");
        when(numbers.savePattern("{NN}")).thenThrow(new SettingsException(List.of("Jahr fehlt.")));
        mvc.perform(post("/admin/nummernkreis/muster").param("pattern", "{NN}").with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(flash().attribute("error", "Jahr fehlt."));

        mvc.perform(post("/admin/nummernkreis/start").param("year", "2028").param("start", "10")
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(flash().attribute("success", "Startnummer für 2028 gespeichert."));
        verify(numbers).setStart(2028, 10);
        doThrow(new SettingsException(List.of("Gesperrt."))).when(numbers).setStart(2027, 5);
        mvc.perform(post("/admin/nummernkreis/start").param("year", "2027").param("start", "5")
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(flash().attribute("error", "Gesperrt."));
        mvc.perform(post("/admin/nummernkreis/start").param("year", "2028").param("start", "1")
                        .with(user("elena").roles("USER")).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void listsCustomersAndShowsOne() throws Exception {
        when(customers.list()).thenReturn(List.of());
        mvc.perform(get("/kunden").with(user("elena").roles("USER"))).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Noch keine Kunden.")));
        when(customers.list()).thenReturn(List.of(CustomerTestData.verlag().withId(3L), CustomerTestData.vhs().withId(4L)));
        when(customers.find(4)).thenReturn(Optional.of(CustomerTestData.vhs().withId(4L)));
        mvc.perform(get("/kunden/4").with(user("elena").roles("USER"))).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Verlag am Fluss GmbH")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("value=\"09162000-VHS01-42\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("XRechnung: nur XML</option>")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("aria-current=\"page\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Endgültig löschen")));
        mvc.perform(get("/kunden/neu").with(user("elena").roles("USER"))).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Neuer Kunde")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Endgültig löschen"))));
        when(customers.find(9)).thenReturn(Optional.empty());
        mvc.perform(get("/kunden/9").with(user("elena").roles("USER"))).andExpect(status().isNotFound());
    }

    @Test
    void createsChangesAndDeletesCustomersAndReportsProblems() throws Exception {
        when(customers.create(any())).thenReturn(CustomerTestData.verlag().withId(3L));
        mvc.perform(post("/kunden").param("name", "Verlag am Fluss GmbH").param("format", "ZUGFERD_EN16931")
                        .with(user("elena").roles("USER")).with(csrf()))
                .andExpect(redirectedUrl("/kunden/3"))
                .andExpect(flash().attribute("success", "Kunde „Verlag am Fluss GmbH“ angelegt."));
        when(customers.create(any())).thenThrow(new CustomerException(List.of("Bitte Ort angeben.")));
        mvc.perform(post("/kunden").param("id", "77").param("name", "Neu GmbH").with(user("elena").roles("USER")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Bitte Ort angeben.")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("value=\"Neu GmbH\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("action=\"/kunden\"")));

        mvc.perform(post("/kunden/3").param("name", "Verlag").with(user("elena").roles("USER")).with(csrf()))
                .andExpect(redirectedUrl("/kunden/3")).andExpect(flash().attribute("success", "Änderungen gespeichert."));
        verify(customers).update(eq(3L), argThat(customer -> customer.name().equals("Verlag")));
        when(customers.update(eq(4L), any())).thenThrow(new CustomerException(List.of("Diesen Kunden gibt es nicht.")));
        mvc.perform(post("/kunden/4").param("name", "Verlag").with(user("elena").roles("USER")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Diesen Kunden gibt es nicht.")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("action=\"/kunden/4\"")));

        when(customers.delete(3)).thenReturn(CustomerTestData.verlag().withId(3L));
        mvc.perform(post("/kunden/3/loeschen").with(user("elena").roles("USER")).with(csrf()))
                .andExpect(redirectedUrl("/kunden"))
                .andExpect(flash().attribute("success", "Kunde „Verlag am Fluss GmbH“ gelöscht."));
        when(customers.delete(4)).thenThrow(new CustomerException(List.of("Diesen Kunden gibt es nicht.")));
        mvc.perform(post("/kunden/4/loeschen").with(user("elena").roles("USER")).with(csrf()))
                .andExpect(flash().attribute("error", "Diesen Kunden gibt es nicht."));
    }

    @Test
    void offersTheListToNewRecordsAndKeepsTheStoredCountry() throws Exception {
        List<Country> list = List.of(new Country("DE", "Deutschland"), new Country("CH", "Schweiz"));
        when(countries.options(null, null)).thenReturn(list);
        when(sellers.find()).thenReturn(Optional.empty());
        mvc.perform(get("/einstellungen").with(user("elena").roles("USER")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("<option value=\"DE\" selected=\"selected\">Deutschland</option>")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("<option value=\"CH\">Schweiz</option>")));
        mvc.perform(get("/kunden/neu").with(user("elena").roles("USER")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("<option value=\"DE\" selected=\"selected\">Deutschland</option>")));

        SellerProfile p = SellerTestData.profile();
        SellerProfile kosovo = new SellerProfile(p.name(), p.street(), p.postcode(), p.city(), "XK", null, p.email(),
                p.phone(), p.taxNumber(), null, p.iban(), p.bic(), p.taxStatus(), null, null, null, null);
        when(sellers.find()).thenReturn(Optional.of(kosovo));
        when(countries.options("XK", null)).thenReturn(List.of(new Country("DE", "Deutschland"), new Country("XK", "XK")));
        mvc.perform(get("/einstellungen").with(user("elena").roles("USER")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("<option value=\"XK\" selected=\"selected\">XK</option>")));

        Customer swiss = CustomerTestData.verlag();
        swiss = new Customer(5L, swiss.name(), swiss.street(), swiss.postcode(), swiss.city(), "CH", null, null, null,
                null, swiss.format());
        when(customers.find(5)).thenReturn(Optional.of(swiss));
        when(countries.options("CH", null)).thenReturn(list);
        mvc.perform(get("/kunden/5").with(user("elena").roles("USER")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("<option value=\"CH\" selected=\"selected\">Schweiz</option>")));
        when(customers.update(eq(5L), any())).thenThrow(new CustomerException(List.of("Bitte ein Land aus der Liste wählen.")));
        mvc.perform(post("/kunden/5").param("countryCode", "JP").with(user("elena").roles("USER")).with(csrf()))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Bitte ein Land aus der Liste wählen.")));
        verify(countries).options("CH", "JP");
        mvc.perform(get("/kunden").with(user("elena").roles("USER"))).andExpect(status().isOk());
    }

    @Test
    void theAdminKeepsTheShortListOfCountries() throws Exception {
        when(countries.enabled()).thenReturn(List.of(new Country("DE", "Deutschland")));
        when(countries.others()).thenReturn(List.of(new Country("JP", "Japan")));
        mvc.perform(get("/admin/laender").with(user("admin").roles("ADMIN"))).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("aria-label=\"Deutschland entfernen\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("<option value=\"JP\">Japan</option>")));
        mvc.perform(get("/admin/laender").with(user("elena").roles("USER"))).andExpect(status().isForbidden());

        when(countries.add("JP")).thenReturn(new Country("JP", "Japan"));
        mvc.perform(post("/admin/laender").param("code", "JP").with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(redirectedUrl("/admin/laender")).andExpect(flash().attribute("success", "Japan hinzugefügt."));
        when(countries.add("ZZ")).thenThrow(new IllegalArgumentException("Unknown country ZZ"));
        mvc.perform(post("/admin/laender").param("code", "ZZ").with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(flash().attribute("error", "Dieses Land gibt es nicht."));
        when(countries.remove("CH")).thenReturn(new Country("CH", "Schweiz"));
        mvc.perform(post("/admin/laender/CH/entfernen").with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(flash().attribute("success", "Schweiz entfernt."));
        mvc.perform(post("/admin/laender/CH/entfernen").with(user("elena").roles("USER")).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void showsTheAuditLogToTheAdminOnly() throws Exception {
        when(audit.latest()).thenReturn(List.of());
        mvc.perform(get("/admin/protokoll").with(user("admin").roles("ADMIN"))).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Noch keine Einträge.")));
        when(audit.latest()).thenReturn(List.of(
                new AuditEvent(2L, java.time.Instant.parse("2027-03-18T08:12:00Z"), "elena", AuditAction.CUSTOMER_CREATED,
                        "Verlag am Fluss GmbH"),
                new AuditEvent(1L, java.time.Instant.parse("2027-03-18T08:10:00Z"), "elena", AuditAction.TAX_NOTES_SAVED, "")));
        mvc.perform(get("/admin/protokoll").with(user("admin").roles("ADMIN")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("18.03.2027 09:12")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Kunde angelegt</span><span>: Verlag am Fluss GmbH")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("<span>Steuerhinweise gespeichert</span>\n")));
        mvc.perform(get("/admin/protokoll").with(user("elena").roles("USER"))).andExpect(status().isForbidden());
    }

    @Test
    void resetsAPasswordAndReportsAProblem() throws Exception {
        when(users.resetPassword(2, PASSWORD)).thenReturn(UserTestData.user(2, "elena", Role.USER));
        mvc.perform(post("/admin/benutzer/2/passwort").with(user("admin").roles("ADMIN")).with(csrf())
                        .param("password", PASSWORD))
                .andExpect(flash().attribute("success", "Neues Passwort für „elena“ gesetzt."));
        when(users.resetPassword(anyLong(), eq("kurz"))).thenThrow(new UserException("Zu kurz."));
        mvc.perform(post("/admin/benutzer/2/passwort").with(user("admin").roles("ADMIN")).with(csrf())
                        .param("password", "kurz"))
                .andExpect(flash().attribute("error", "Zu kurz."));
    }
}
