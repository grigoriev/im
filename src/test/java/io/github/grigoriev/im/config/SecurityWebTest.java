package io.github.grigoriev.im.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
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
import io.github.grigoriev.im.einvoice.InvoiceFormat;
import io.github.grigoriev.im.invoice.DraftForm;
import io.github.grigoriev.im.invoice.DraftLine;
import io.github.grigoriev.im.invoice.Invoice;
import io.github.grigoriev.im.invoice.InvoiceException;
import io.github.grigoriev.im.invoice.InvoiceService;
import io.github.grigoriev.im.invoice.InvoiceStatus;
import io.github.grigoriev.im.invoice.InvoiceTemplate;
import io.github.grigoriev.im.invoice.TemplateForm;
import io.github.grigoriev.im.invoice.TemplateLine;
import io.github.grigoriev.im.invoice.TemplateService;
import io.github.grigoriev.im.web.AccountController;
import io.github.grigoriev.im.web.TemplateController;
import io.github.grigoriev.im.web.InvoiceController;
import io.github.grigoriev.im.web.InvoiceRecordsController;
import io.github.grigoriev.im.web.BackupController;
import io.github.grigoriev.im.web.BaselineController;
import io.github.grigoriev.im.web.ReportController;
import io.github.grigoriev.im.web.MailTextController;
import io.github.grigoriev.im.web.IssuedInvoicePage;
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
        AuditController.class, InvoiceController.class, InvoiceRecordsController.class, TemplateController.class,
        MailTextController.class, BaselineController.class, ReportController.class, BackupController.class})
@Import({SecurityConfig.class, StaleFormHandler.class, CurrentUserAdvice.class, IssuedInvoicePage.class, ClockConfig.class,
        SessionEpoch.class})
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

    @MockitoBean
    private InvoiceService invoices;

    @MockitoBean
    private TemplateService templates;

    @MockitoBean
    private io.github.grigoriev.im.einvoice.InvoicePdfRenderer pdf;

    @MockitoBean
    private io.github.grigoriev.im.invoice.IssueService issues;

    @MockitoBean
    private io.github.grigoriev.im.invoice.DocumentStore documents;

    @MockitoBean
    private io.github.grigoriev.im.invoice.DispatchService dispatches;

    @MockitoBean
    private io.github.grigoriev.im.invoice.PaymentService payments;

    @MockitoBean
    private io.github.grigoriev.im.mail.MailTextService mailTexts;

    @MockitoBean
    private io.github.grigoriev.im.mail.MailService mail;

    @MockitoBean
    private io.github.grigoriev.im.threshold.ThresholdService threshold;

    @MockitoBean
    private io.github.grigoriev.im.invoice.ArchiveService archive;

    @MockitoBean
    private io.github.grigoriev.im.backup.BackupService backups;

    @MockitoBean
    private io.github.grigoriev.im.backup.BackupRunService backupRuns;

    @org.junit.jupiter.api.BeforeEach
    void noRevenueNoPayments() {
        when(threshold.today()).thenReturn(java.time.LocalDate.of(2027, 3, 18));
        when(threshold.current()).thenReturn(new io.github.grigoriev.im.threshold.Threshold(2027,
                new java.math.BigDecimal("18250.00"), true, new java.math.BigDecimal("101000.00"),
                new java.math.BigDecimal("984.00"), new java.math.BigDecimal("101984.00"),
                new java.math.BigDecimal("30000.00")));
        when(payments.summary(any())).thenReturn(new io.github.grigoriev.im.invoice.PaymentService.Payments(List.of(),
                java.math.BigDecimal.ZERO, new java.math.BigDecimal("480.00")));
    }

    private static InvoiceTemplate template() {
        return new InvoiceTemplate(7L, 4L, "Lesung mit Gespräch", InvoiceFormat.ZUGFERD_EN16931, 14, "Hallo", "Danke",
                null, "Rechnung {Rechnungsnummer}", "Anbei", List.of(new TemplateLine("Lesung", true, java.math.BigDecimal.ONE,
                        new java.math.BigDecimal("400"), TaxCategory.KLEINUNTERNEHMER)), java.time.Instant.EPOCH,
                java.time.Instant.EPOCH, 0L);
    }

    private static Invoice invoice(long id, InvoiceStatus status) {
        java.time.LocalDate day = java.time.LocalDate.of(2027, 3, 18);
        return new Invoice(id, status, 3L, status == InvoiceStatus.DRAFT ? null : "2027-01", InvoiceFormat.ZUGFERD_EN16931,
                day, day, day, 14, null, "Hallo", "Danke", null,
                List.of(new DraftLine("Lesung", true, java.math.BigDecimal.ONE, new java.math.BigDecimal("400"),
                        TaxCategory.KLEINUNTERNEHMER)), java.time.Instant.EPOCH, java.time.Instant.EPOCH, 0L,
                status == InvoiceStatus.DRAFT ? null : java.time.Instant.parse("2027-03-18T09:30:00Z"),
                status == InvoiceStatus.DRAFT ? null : "{snapshot}");
    }

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
                        "Verlag am Fluss GmbH", null),
                new AuditEvent(1L, java.time.Instant.parse("2027-03-18T08:10:00Z"), "elena", AuditAction.TAX_NOTES_SAVED, "",
                        null)));
        mvc.perform(get("/admin/protokoll").with(user("admin").roles("ADMIN")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("18.03.2027 09:12")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Kunde angelegt</span><span>: Verlag am Fluss GmbH")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("<span>Steuerhinweise gespeichert</span>\n")));
        mvc.perform(get("/admin/protokoll").with(user("elena").roles("USER"))).andExpect(status().isForbidden());
    }

    @Test
    void listsInvoicesAndCreatesADraft() throws Exception {
        when(invoices.list()).thenReturn(List.of());
        when(customers.list()).thenReturn(List.of());
        mvc.perform(get("/rechnungen").with(user("elena").roles("USER"))).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Noch keine Rechnungen.")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Legen Sie zuerst einen")));
        Invoice draft = invoice(5, InvoiceStatus.DRAFT);
        Invoice issued = invoice(6, InvoiceStatus.ISSUED);
        when(invoices.list()).thenReturn(List.of(issued, draft));
        when(invoices.customerName(any())).thenReturn("Verlag am Fluss GmbH");
        when(invoices.document(draft)).thenReturn(Optional.empty());
        when(documents.restore("{snapshot}")).thenReturn(io.github.grigoriev.im.einvoice.SampleInvoices.reading(
                InvoiceFormat.ZUGFERD_EN16931));
        when(customers.list()).thenReturn(List.of(CustomerTestData.verlag().withId(3L)));
        mvc.perform(get("/rechnungen").with(user("elena").roles("USER")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("2027-01 · 18.03.2027")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Entwurf · 18.03.2027")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("480,00 €")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Neue Rechnung")));

        when(invoices.createDraft(3L, null)).thenReturn(draft);
        mvc.perform(post("/rechnungen").param("customerId", "3").with(user("elena").roles("USER")).with(csrf()))
                .andExpect(redirectedUrl("/rechnungen/5"));
        when(invoices.createDraft(4L, null)).thenThrow(new InvoiceException(List.of("Bitte zuerst das Profil ausfüllen.")));
        mvc.perform(post("/rechnungen").param("customerId", "4").with(user("elena").roles("USER")).with(csrf()))
                .andExpect(redirectedUrl("/rechnungen")).andExpect(flash().attribute("error", "Bitte zuerst das Profil ausfüllen."));
    }

    @Test
    void recordsADispatch() throws Exception {
        mvc.perform(post("/rechnungen/6/versand").param("sentOn", "2027-03-19").param("channel", "POST")
                        .param("recipient", "Buchhaltung").with(user("elena").roles("USER")).with(csrf()))
                .andExpect(redirectedUrl("/rechnungen/6")).andExpect(flash().attribute("success", "Versand vermerkt."));
        var form = org.mockito.ArgumentCaptor.forClass(io.github.grigoriev.im.invoice.DispatchForm.class);
        verify(dispatches).recordManual(org.mockito.ArgumentMatchers.eq(6L), form.capture());
        org.assertj.core.api.Assertions.assertThat(form.getValue().getSentOn()).isEqualTo(java.time.LocalDate.of(2027, 3, 19));
        org.assertj.core.api.Assertions.assertThat(form.getValue().getChannel())
                .isEqualTo(io.github.grigoriev.im.invoice.DispatchChannel.POST);
        org.assertj.core.api.Assertions.assertThat(form.getValue().getRecipient()).isEqualTo("Buchhaltung");

        when(dispatches.recordManual(org.mockito.ArgumentMatchers.eq(7L), any())).thenThrow(
                new InvoiceException(List.of("Erst ausstellen, dann versenden.")));
        mvc.perform(post("/rechnungen/7/versand").param("sentOn", "2027-03-19").param("channel", "POST")
                        .with(user("elena").roles("USER")).with(csrf()))
                .andExpect(redirectedUrl("/rechnungen/7")).andExpect(flash().attribute("error", "Erst ausstellen, dann versenden."));
        mvc.perform(post("/rechnungen/6/versand").param("sentOn", "kaputt").param("channel", "POST")
                        .with(user("elena").roles("USER")).with(csrf()))
                .andExpect(redirectedUrl("/rechnungen/6"))
                .andExpect(flash().attribute("error", "Bitte gültige Daten eingeben, zum Beispiel ein Datum."));
    }

    @Test
    void sendsAnInvoiceByEmail() throws Exception {
        mvc.perform(post("/rechnungen/6/senden").param("to", " a@b.example ").param("subject", "Rechnung 2027-01")
                        .param("body", "Hallo").param("pdf", "true").with(user("elena").roles("USER")).with(csrf()))
                .andExpect(redirectedUrl("/rechnungen/6"))
                .andExpect(flash().attribute("success", "E-Mail an a@b.example gesendet."));
        var form = org.mockito.ArgumentCaptor.forClass(io.github.grigoriev.im.mail.SendForm.class);
        verify(mail).send(org.mockito.ArgumentMatchers.eq(6L), form.capture());
        org.assertj.core.api.Assertions.assertThat(form.getValue().getSubject()).isEqualTo("Rechnung 2027-01");
        org.assertj.core.api.Assertions.assertThat(form.getValue().getBody()).isEqualTo("Hallo");
        org.assertj.core.api.Assertions.assertThat(form.getValue().isPdf()).isTrue();
        org.assertj.core.api.Assertions.assertThat(form.getValue().isXml()).isFalse();
        doThrow(new InvoiceException(List.of("Die E-Mail ging nicht raus."))).when(mail)
                .send(org.mockito.ArgumentMatchers.eq(7L), any());
        mvc.perform(post("/rechnungen/7/senden").param("to", "a@b.example").param("subject", "Mein Betreff")
                        .with(user("elena").roles("USER")).with(csrf()))
                .andExpect(redirectedUrl("/rechnungen/7")).andExpect(flash().attribute("error", "Die E-Mail ging nicht raus."))
                .andExpect(flash().attribute("mailForm", org.hamcrest.Matchers.hasProperty("subject",
                        org.hamcrest.Matchers.is("Mein Betreff"))));
    }

    @Test
    void recordsAPayment() throws Exception {
        when(payments.add(org.mockito.ArgumentMatchers.eq(6L), any()))
                .thenReturn(io.github.grigoriev.im.invoice.InvoiceStatus.PAID);
        mvc.perform(post("/rechnungen/6/zahlungen").param("paidOn", "2027-03-19").param("amount", "480,00")
                        .param("note", "Überweisung").with(user("elena").roles("USER")).with(csrf()))
                .andExpect(redirectedUrl("/rechnungen/6"))
                .andExpect(flash().attribute("success", "Zahlung erfasst. Die Rechnung ist bezahlt."));
        var form = org.mockito.ArgumentCaptor.forClass(io.github.grigoriev.im.invoice.PaymentForm.class);
        verify(payments).add(org.mockito.ArgumentMatchers.eq(6L), form.capture());
        org.assertj.core.api.Assertions.assertThat(form.getValue().getPaidOn()).isEqualTo(java.time.LocalDate.of(2027, 3, 19));
        org.assertj.core.api.Assertions.assertThat(form.getValue().getAmount()).isEqualTo("480,00");
        org.assertj.core.api.Assertions.assertThat(form.getValue().getNote()).isEqualTo("Überweisung");

        when(payments.add(org.mockito.ArgumentMatchers.eq(7L), any()))
                .thenReturn(io.github.grigoriev.im.invoice.InvoiceStatus.SENT);
        mvc.perform(post("/rechnungen/7/zahlungen").param("paidOn", "2027-03-19").param("amount", "100")
                        .with(user("elena").roles("USER")).with(csrf()))
                .andExpect(flash().attribute("success", "Zahlung erfasst."));
        when(payments.add(org.mockito.ArgumentMatchers.eq(8L), any()))
                .thenThrow(new InvoiceException(List.of("Der Betrag ist 0.")));
        mvc.perform(post("/rechnungen/8/zahlungen").param("paidOn", "2027-03-19").param("amount", "0")
                        .with(user("elena").roles("USER")).with(csrf()))
                .andExpect(redirectedUrl("/rechnungen/8")).andExpect(flash().attribute("error", "Der Betrag ist 0."));
        mvc.perform(post("/rechnungen/6/zahlungen").param("paidOn", "gestern").param("amount", "1")
                        .with(user("elena").roles("USER")).with(csrf()))
                .andExpect(flash().attribute("error", "Bitte gültige Daten eingeben, zum Beispiel ein Datum."));
    }

    @Test
    void theAdminEditsTheDefaultMailText() throws Exception {
        when(mailTexts.defaults()).thenReturn(new io.github.grigoriev.im.mail.MailText("Rechnung {Rechnungsnummer}",
                "Hallo & Tschüss"));
        mvc.perform(get("/admin/e-mail").with(user("admin").roles("ADMIN"))).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("value=\"Rechnung {Rechnungsnummer}\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Hallo &amp; Tschüss</textarea>")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("aria-selected=\"true\">E-Mail")));
        mvc.perform(get("/admin/e-mail").with(user("elena").roles("USER"))).andExpect(status().isForbidden());
        mvc.perform(post("/admin/e-mail").param("subject", "Neu").param("body", "Text")
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(redirectedUrl("/admin/e-mail")).andExpect(flash().attribute("success", "E-Mail-Text gespeichert."));
        verify(mailTexts).save("Neu", "Text");
        doThrow(new io.github.grigoriev.im.seller.SettingsException(
                List.of("Der Betreff hat höchstens 200 Zeichen."))).when(mailTexts).save("zu lang", "Text");
        mvc.perform(post("/admin/e-mail").param("subject", "zu lang").param("body", "Text")
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Der Betreff hat höchstens 200 Zeichen.")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("value=\"zu lang\"")));
    }

    @Test
    void cancelsAnInvoiceThroughAStornorechnung() throws Exception {
        when(invoices.createCancellation(6)).thenReturn(invoice(9, InvoiceStatus.DRAFT));
        mvc.perform(post("/rechnungen/6/stornieren").with(user("elena").roles("USER")).with(csrf()))
                .andExpect(redirectedUrl("/rechnungen/9"))
                .andExpect(flash().attribute("success", "Stornorechnung als Entwurf angelegt. Die Nummer folgt beim Ausstellen."));
        when(invoices.createCancellation(7)).thenThrow(new InvoiceException(List.of("Zu dieser Rechnung gibt es schon eine Stornorechnung.")));
        mvc.perform(post("/rechnungen/7/stornieren").with(user("elena").roles("USER")).with(csrf()))
                .andExpect(redirectedUrl("/rechnungen/7"))
                .andExpect(flash().attribute("error", "Zu dieser Rechnung gibt es schon eine Stornorechnung."));

        Invoice original = invoice(6, InvoiceStatus.SENT);
        Invoice storno = invoice(9, InvoiceStatus.DRAFT);
        when(invoices.find(6)).thenReturn(Optional.of(original));
        when(invoices.find(9)).thenReturn(Optional.of(storno));
        when(invoices.customerName(any())).thenReturn("Verlag am Fluss GmbH");
        when(documents.restore("{snapshot}")).thenReturn(io.github.grigoriev.im.einvoice.SampleInvoices.reading(
                InvoiceFormat.ZUGFERD_EN16931));
        mvc.perform(get("/rechnungen/6").with(user("elena").roles("USER")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Stornieren…")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("action=\"/rechnungen/6/stornieren\"")));
        when(invoices.cancellation(original)).thenReturn(Optional.of(storno));
        mvc.perform(get("/rechnungen/6").with(user("elena").roles("USER")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Eine Stornorechnung ist als")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("href=\"/rechnungen/9\"")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Stornieren…"))));
        Invoice issuedStorno = invoice(9, InvoiceStatus.ISSUED);
        when(invoices.cancellation(original)).thenReturn(Optional.of(issuedStorno));
        mvc.perform(get("/rechnungen/6").with(user("elena").roles("USER")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Storniert durch die")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Stornorechnung 2027-01")));

        when(invoices.cancelled(storno)).thenReturn(Optional.of(original));
        when(invoices.problems(storno)).thenReturn(List.of());
        mvc.perform(get("/rechnungen/9").with(user("elena").roles("USER")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Stornorechnung · Entwurf")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Storniert vollständig die")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"cancel-lines\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("disabled=\"disabled\"")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Position hinzufügen"))));
        when(invoices.document(storno)).thenReturn(Optional.of(io.github.grigoriev.im.einvoice.SampleInvoices.reading(
                InvoiceFormat.ZUGFERD_EN16931)));
        when(threshold.wouldExceed(any())).thenReturn(true);
        mvc.perform(get("/rechnungen/9").with(user("elena").roles("USER")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"cancel-total\"")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("über 100.000 €"))));
        when(threshold.wouldExceed(any())).thenReturn(false);

        Invoice xStorno = new Invoice(9L, InvoiceStatus.DRAFT, 3L, null, InvoiceFormat.XRECHNUNG, storno.issueDate(),
                storno.serviceFrom(), storno.serviceTo(), 0, "04011000-1", null, null, null, storno.lines(),
                java.time.Instant.EPOCH, java.time.Instant.EPOCH, 0L, null, null);
        when(invoices.find(9)).thenReturn(Optional.of(xStorno));
        when(invoices.cancelled(xStorno)).thenReturn(Optional.of(original));
        mvc.perform(post("/rechnungen/9").param("version", "0").param("issueDate", "kaputt")
                        .with(user("elena").roles("USER")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("<option value=\"XRECHNUNG\" selected=\"selected\">")));

        when(invoices.find(9)).thenReturn(Optional.of(issuedStorno));
        when(invoices.cancelled(issuedStorno)).thenReturn(Optional.of(original));
        mvc.perform(get("/rechnungen/9").with(user("elena").roles("USER")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Stornorechnung zur")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("href=\"/rechnungen/6\"")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Zahlung erfassen"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("id=\"invoice-payments\""))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Stornieren…"))));

        when(invoices.list()).thenReturn(List.of(issuedStorno));
        when(customers.list()).thenReturn(List.of());
        mvc.perform(get("/rechnungen").with(user("elena").roles("USER")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Stornorechnung · 2027-01 · 18.03.2027")));
    }

    @Test
    void showsTheThresholdOnTheStartPage() throws Exception {
        mvc.perform(get("/").with(user("elena").roles("USER"))).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Stand 18.03.2027")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("18.250,00 €")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("101.000,00 €")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("≈ 30.000,00 €")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("984,00 €")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("gilt nicht mehr")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Mit dem Vorjahresumsatz")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("im nächsten Jahr nicht mehr")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Nichts überfällig.")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("value=\"100\"")));
    }

    @Test
    void showsTheReportAndItsCsv() throws Exception {
        when(threshold.years()).thenReturn(List.of(2027));
        when(threshold.rows(2027)).thenReturn(List.of(new io.github.grigoriev.im.threshold.RevenueRow(
                java.time.LocalDate.of(2027, 3, 18), "2027-01", "Verlag am Fluss GmbH", new java.math.BigDecimal("560.00"),
                new java.math.BigDecimal("200.00"), new java.math.BigDecimal("360.00"), java.util.Map.of(
                        TaxCategory.KLEINUNTERNEHMER, new java.math.BigDecimal("200.00"), TaxCategory.EXEMPT_4_21,
                        new java.math.BigDecimal("360.00")), null)));
        mvc.perform(get("/berichte").with(user("elena").roles("USER"))).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Anlage EÜR 2027, Betriebseinnahmen")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Kennzahl 111")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(">560,00 €<")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(">360,00 €<")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("§ 4 Nr. 21 UStG, steuerfrei")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("18.03.2027 · 2027-01")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("href=\"/berichte/zahlungen.csv?jahr=2027\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("href=\"/berichte/archiv.zip?jahr=2027\"")));
        when(threshold.rows(2026)).thenReturn(List.of(new io.github.grigoriev.im.threshold.RevenueRow(
                java.time.LocalDate.of(2026, 3, 18), "2026-01", "Schule", new java.math.BigDecimal("119.00"),
                java.math.BigDecimal.ZERO, new java.math.BigDecimal("119.00"), java.util.Map.of(TaxCategory.STANDARD_19,
                        new java.math.BigDecimal("119.00")), null)));
        mvc.perform(get("/berichte").param("jahr", "2026").with(user("elena").roles("USER")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Einnahmen mit Umsatzsteuer von 119,00 €")));
        mvc.perform(get("/berichte").param("jahr", "2020").with(user("elena").roles("USER")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Keine Zahlungen in diesem Jahr.")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("<option value=\"2020\" selected=\"selected\">")));
        mvc.perform(get("/berichte/zahlungen.csv").param("jahr", "2027").with(user("elena").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"Zahlungen_2027.csv\""))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("18.03.2027;\"2027-01\";\"Verlag am Fluss GmbH\";560,00;200,00;360,00;")));
        var started = mvc.perform(get("/berichte/archiv.zip").param("jahr", "2027").with(user("elena").roles("USER")))
                .andExpect(request().asyncStarted()).andReturn();
        mvc.perform(asyncDispatch(started)).andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"Rechnungen_2027.zip\""));
        verify(archive).write(org.mockito.ArgumentMatchers.eq(2027), any());
        mvc.perform(get("/berichte")).andExpect(status().is3xxRedirection());
    }

    @Test
    void showsTheLastBackupAndWarnsWhenOneIsOverdue() throws Exception {
        when(backupRuns.status()).thenReturn(new io.github.grigoriev.im.backup.BackupStatus(null, null, true, true));
        mvc.perform(get("/admin/datensicherung").with(user("admin").roles("ADMIN"))).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Es gibt noch keine automatische Sicherung.")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Es gibt noch keinen Wiederherstellungstest.")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(">noch keine</span></dd>")));

        java.time.OffsetDateTime night = java.time.OffsetDateTime.parse("2027-03-18T01:30:00Z");
        when(backupRuns.status()).thenReturn(new io.github.grigoriev.im.backup.BackupStatus(
                new io.github.grigoriev.im.backup.BackupRun("BACKUP", night, "Snapshot 1a2b3c4d, 1,2 MB"),
                new io.github.grigoriev.im.backup.BackupRun("RESTORE_TEST", night.minusDays(40), ""), false, true));
        mvc.perform(get("/admin/datensicherung").with(user("admin").roles("ADMIN"))).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("18.03.2027, 02:30 · Snapshot 1a2b3c4d, 1,2 MB")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(">06.02.2027, 02:30</span></dd>")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("id=\"backup-overdue\""))))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("älter als 35 Tage")));

        when(backupRuns.status()).thenReturn(new io.github.grigoriev.im.backup.BackupStatus(
                new io.github.grigoriev.im.backup.BackupRun("BACKUP", night.minusDays(2), ""), null, true, true));
        mvc.perform(get("/admin/datensicherung").with(user("admin").roles("ADMIN"))).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("älter als 36 Stunden")));
    }

    @Test
    void theAdminExportsAndImportsEverything() throws Exception {
        when(backupRuns.status()).thenReturn(new io.github.grigoriev.im.backup.BackupStatus(null, null, true, true));
        mvc.perform(get("/admin/datensicherung").with(user("admin").roles("ADMIN"))).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Export herunterladen")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("enctype=\"multipart/form-data\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("aria-selected=\"true\">Datensicherung")));
        mvc.perform(get("/admin/datensicherung").with(user("elena").roles("USER"))).andExpect(status().isForbidden());
        var started = mvc.perform(get("/admin/datensicherung/export.zip").with(user("admin").roles("ADMIN")))
                .andExpect(request().asyncStarted()).andReturn();
        mvc.perform(asyncDispatch(started)).andExpect(status().isOk());
        verify(backups).export(any());
        org.springframework.mock.web.MockMultipartFile file = new org.springframework.mock.web.MockMultipartFile("file",
                "export.zip", "application/zip", new byte[] {1});
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/admin/datensicherung/import")
                        .file(file).with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(redirectedUrl("/login?eingespielt"));
        mvc.perform(get("/login").param("eingespielt", ""))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Datensicherung eingespielt.")));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/admin/datensicherung/import")
                        .file(file).with(user("elena").roles("USER")).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void theAdminKeepsTheBaselines() throws Exception {
        when(threshold.baselines()).thenReturn(java.util.Map.of());
        when(threshold.newestBaselineYear()).thenReturn(2024);
        mvc.perform(get("/admin/vorjahre").with(user("admin").roles("ADMIN"))).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Noch kein Vorjahresumsatz erfasst.")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("max=\"2026\" value=\"2024\"")));
        when(threshold.baselines()).thenReturn(java.util.Map.of(2026, new java.math.BigDecimal("18250.00")));
        mvc.perform(get("/admin/vorjahre").with(user("admin").roles("ADMIN")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("18.250,00 €")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"baseline-remove-2026\"")));
        mvc.perform(get("/admin/vorjahre").with(user("elena").roles("USER"))).andExpect(status().isForbidden());
        mvc.perform(post("/admin/vorjahre").param("year", "2026").param("amount", "18.250,00")
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(redirectedUrl("/admin/vorjahre"))
                .andExpect(flash().attribute("success", "Vorjahresumsatz 2026 gespeichert."));
        verify(threshold).saveBaseline(2026, "18.250,00");
        doThrow(new io.github.grigoriev.im.seller.SettingsException(List.of("Ein Vorjahresumsatz gehört zu einem "
                + "vergangenen Jahr."))).when(threshold).saveBaseline(2027, "1");
        mvc.perform(post("/admin/vorjahre").param("year", "2027").param("amount", "1")
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(flash().attribute("error", "Ein Vorjahresumsatz gehört zu einem vergangenen Jahr."));
    }

    @Test
    void filtersTheListByYearStatusAndText() throws Exception {
        Invoice draft = invoice(5, InvoiceStatus.DRAFT);
        Invoice issued = invoice(6, InvoiceStatus.ISSUED);
        when(invoices.list()).thenReturn(List.of(issued, draft));
        when(invoices.customerName(any())).thenReturn("Volkshochschule Musterstadt");
        when(invoices.document(draft)).thenReturn(Optional.empty());
        when(documents.restore("{snapshot}")).thenReturn(io.github.grigoriev.im.einvoice.SampleInvoices.reading(
                InvoiceFormat.ZUGFERD_EN16931));
        when(customers.list()).thenReturn(List.of());
        String issuedRow = "2027-01 · 18.03.2027";
        String draftRow = "Entwurf · 18.03.2027";
        mvc.perform(get("/rechnungen").with(user("elena").roles("USER")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(issuedRow)))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(draftRow)))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("<option value=\"2027\">2027</option>")));
        mvc.perform(get("/rechnungen").param("status", "DRAFT").with(user("elena").roles("USER")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(issuedRow))))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(draftRow)))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("<option value=\"DRAFT\" selected=\"selected\">")));
        mvc.perform(get("/rechnungen").param("suche", " FLUSS ").with(user("elena").roles("USER")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(issuedRow)))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(draftRow))))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("value=\"FLUSS\"")));
        mvc.perform(get("/rechnungen").param("suche", "2027-0").with(user("elena").roles("USER")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(issuedRow)))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(draftRow))));
        mvc.perform(get("/rechnungen").param("suche", "volkshoch").with(user("elena").roles("USER")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(issuedRow))))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(draftRow)));
        mvc.perform(get("/rechnungen").param("jahr", "2027").param("status", "ISSUED").with(user("elena").roles("USER")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(issuedRow)))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(draftRow))))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("<option value=\"2027\" selected=\"selected\">")));
        mvc.perform(get("/rechnungen").param("jahr", "2026").with(user("elena").roles("USER")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Keine Rechnungen gefunden.")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Noch keine Rechnungen."))));
        mvc.perform(get("/rechnungen").param("status", "NIX").with(user("elena").roles("USER")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void showsTheEditorOfADraftAndTheNoticeOfAnIssuedInvoice() throws Exception {
        Invoice draft = invoice(5, InvoiceStatus.DRAFT);
        when(invoices.find(5)).thenReturn(Optional.of(draft));
        when(invoices.customerName(any())).thenReturn("Verlag am Fluss GmbH");
        when(invoices.problems(draft)).thenReturn(List.of("Position 1: Bitte die Leistung beschreiben."));
        mvc.perform(get("/rechnungen/5").with(user("elena").roles("USER"))).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("value=\"Lesung\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("value=\"400,00\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Position 1: Bitte die Leistung beschreiben.")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("§ 19 UStG, Kleinunternehmer")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Entwurf löschen")));
        when(invoices.problems(draft)).thenReturn(List.of());
        when(invoices.document(draft)).thenReturn(Optional.of(io.github.grigoriev.im.einvoice.SampleInvoices.sale(
                InvoiceFormat.ZUGFERD_EN16931)));
        mvc.perform(get("/rechnungen/5").with(user("elena").roles("USER")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Vollständig.")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Netto 130,50 €")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("USt 19 % 10,60 €")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("146,33 €")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Rechnung ausstellen…")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("über 100.000 €"))))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"version\" value=\"0\"")));
        when(threshold.wouldExceed(any())).thenReturn(true);
        mvc.perform(get("/rechnungen/5").with(user("elena").roles("USER")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("über 100.000 €")));
        Invoice issued = invoice(6, InvoiceStatus.ISSUED);
        when(invoices.find(6)).thenReturn(Optional.of(issued));
        when(documents.restore("{snapshot}")).thenReturn(io.github.grigoriev.im.einvoice.SampleInvoices.reading(
                InvoiceFormat.ZUGFERD_EN16931));
        when(documents.list(6)).thenReturn(List.of(new io.github.grigoriev.im.invoice.StoredDocument(1L, 6L,
                io.github.grigoriev.im.invoice.DocumentKind.PDF, "Rechnung_2027-10.pdf", new byte[] {1}, "ab12", 12345,
                java.time.Instant.EPOCH)));
        when(audit.history(6)).thenReturn(List.of(
                new AuditEvent(1L, java.time.Instant.parse("2027-03-18T08:00:00Z"), "elena", AuditAction.DRAFT_CREATED,
                        "Verlag am Fluss GmbH", 6L),
                new AuditEvent(2L, java.time.Instant.parse("2027-03-18T09:30:00Z"), "admin", AuditAction.INVOICE_ISSUED,
                        "2027-10, Verlag am Fluss GmbH", 6L)));
        mvc.perform(get("/rechnungen/6").with(user("elena").roles("USER")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Unveränderbar seit")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Rechnung 2027-10")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "min-w-0 [overflow-wrap:anywhere]\" id=\"invoice-number\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Verlag am Fluss GmbH · 480,00 € · fällig am")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Entwurf angelegt")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("18.03.2027 09:00")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Rechnung ausgestellt")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("<span>admin</span>")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("ZUGFeRD und PDF/A-3 geprüft")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("2027-10, Verlag am Fluss GmbH")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-lucide=\"stamp\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Als versendet markieren…")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Noch keine Zahlung.")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Das Versenden per E-Mail ist nicht eingerichtet.")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Per E-Mail senden"))))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"amount\" value=\"480,00\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("<option value=\"POST\">Post</option>")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("value=\"EMAIL_APP\""))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("XRechnung geprüft"))))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("480,00 €")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("18.03.2027 10:30")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Rechnung_2027-10.pdf")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("12.345 Byte")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("SHA-256 ab12")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("href=\"/rechnungen/6/dokumente/pdf\"")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Rechnung ausstellen…"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Entwurf löschen"))));
        verify(invoices, never()).problems(issued);
        when(audit.history(6)).thenReturn(List.of(new AuditEvent(3L, java.time.Instant.parse("2027-03-19T09:30:00Z"),
                "elena", AuditAction.INVOICE_SENT, "", 6L)));
        when(dispatches.today()).thenReturn(java.time.LocalDate.of(2027, 3, 19));
        mvc.perform(get("/rechnungen/6").with(user("elena").roles("USER")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-lucide=\"send\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("value=\"2027-03-19\" max=\"2027-03-19\"")));
        when(payments.summary(any())).thenReturn(new io.github.grigoriev.im.invoice.PaymentService.Payments(List.of(
                new io.github.grigoriev.im.invoice.Payment(1L, 6L, java.time.LocalDate.of(2027, 3, 20),
                        new java.math.BigDecimal("500.00"), "zu viel", "elena", java.time.Instant.EPOCH)),
                new java.math.BigDecimal("500.00"), new java.math.BigDecimal("-20.00")));
        mvc.perform(get("/rechnungen/6").with(user("elena").roles("USER")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("20.03.2027")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("zu viel")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Überzahlt")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(">20,00 €<")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"amount\" value=\"\"")));
        io.github.grigoriev.im.mail.SendForm send = new io.github.grigoriev.im.mail.SendForm();
        send.setTo("buchhaltung@verlag.example");
        send.setSubject("Rechnung 2027-01");
        send.setBody("Hallo & Tschüss");
        send.setPdf(true);
        when(mail.configured()).thenReturn(true);
        when(mail.form(org.mockito.ArgumentMatchers.eq(issued), any())).thenReturn(send);
        mvc.perform(get("/rechnungen/6").with(user("elena").roles("USER")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Per E-Mail senden…")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("value=\"buchhaltung@verlag.example\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Hallo &amp; Tschüss</textarea>")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"mail-pdf\" name=\"pdf\" type=\"checkbox\" class=\"checkbox checkbox-sm\"\n              value=\"true\" checked=\"checked\"")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("nicht eingerichtet"))));
        verify(mail).form(issued, "buchhaltung@verlag-am-fluss.example");
        io.github.grigoriev.im.mail.SendForm typed = new io.github.grigoriev.im.mail.SendForm();
        typed.setTo("neu@kunde.example");
        typed.setSubject("Mein Betreff");
        typed.setBody("Mein Text");
        typed.setXml(true);
        mvc.perform(get("/rechnungen/6").flashAttr("mailForm", typed).with(user("elena").roles("USER")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("value=\"neu@kunde.example\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Mein Text</textarea>")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"invoice-mail\" open=\"open\"")));
        verify(mail).form(any(), any());
        when(mail.configured()).thenReturn(false);
        when(audit.history(6)).thenReturn(List.of());
        when(invoices.find(6)).thenReturn(Optional.of(invoice(6, InvoiceStatus.CANCELLED)));
        mvc.perform(get("/rechnungen/6").with(user("elena").roles("USER")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Keine Einträge.")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Als versendet markieren"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("nicht eingerichtet"))));
        when(invoices.find(6)).thenReturn(Optional.of(issued));
        when(documents.restore("{snapshot}")).thenReturn(io.github.grigoriev.im.einvoice.SampleInvoices.reading(
                InvoiceFormat.XRECHNUNG));
        mvc.perform(get("/rechnungen/6").with(user("elena").roles("USER")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("XRechnung geprüft (KoSIT)")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("04011000-12345-67")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("ZUGFeRD und PDF/A-3"))));
        when(invoices.saveDraft(org.mockito.ArgumentMatchers.eq(6L), any())).thenThrow(
                new InvoiceException(List.of("Eine ausgestellte Rechnung ändert sich nicht mehr.")));
        mvc.perform(post("/rechnungen/6").with(user("elena").roles("USER")).with(csrf())).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Eine ausgestellte Rechnung ändert sich nicht mehr.")));
        when(invoices.find(9)).thenReturn(Optional.empty());
        mvc.perform(get("/rechnungen/9").with(user("elena").roles("USER"))).andExpect(status().isNotFound());
        mvc.perform(post("/rechnungen/9").with(user("elena").roles("USER")).with(csrf())).andExpect(status().isNotFound());
    }

    @Test
    void savesADraftAddsAndRemovesLines() throws Exception {
        Invoice draft = invoice(5, InvoiceStatus.DRAFT);
        when(invoices.find(5)).thenReturn(Optional.of(draft));
        when(invoices.customerName(any())).thenReturn("Verlag am Fluss GmbH");
        when(invoices.defaultCategory()).thenReturn(TaxCategory.KLEINUNTERNEHMER);
        var saved = org.mockito.ArgumentCaptor.forClass(DraftForm.class);

        mvc.perform(post("/rechnungen/5").param("aktion", "speichern").param("format", "ZUGFERD_EN16931")
                        .param("lines[0].text", "Lesung").param("lines[0].price", "400")
                        .with(user("elena").roles("USER")).with(csrf()))
                .andExpect(redirectedUrl("/rechnungen/5")).andExpect(flash().attribute("success", "Entwurf gespeichert."));
        mvc.perform(post("/rechnungen/5").param("aktion", "position").param("lines[0].text", "Lesung")
                        .with(user("elena").roles("USER")).with(csrf()))
                .andExpect(redirectedUrl("/rechnungen/5")).andExpect(flash().attributeCount(0));
        mvc.perform(post("/rechnungen/5").param("entfernen", "0").param("lines[0].text", "Lesung")
                        .param("lines[1].text", "Reise").with(user("elena").roles("USER")).with(csrf()))
                .andExpect(redirectedUrl("/rechnungen/5"));
        mvc.perform(post("/rechnungen/5").param("entfernen", "7").param("lines[0].text", "Lesung")
                        .with(user("elena").roles("USER")).with(csrf()))
                .andExpect(redirectedUrl("/rechnungen/5"));
        mvc.perform(post("/rechnungen/5").param("entfernen", "-1").param("lines[0].text", "Lesung")
                        .with(user("elena").roles("USER")).with(csrf()))
                .andExpect(redirectedUrl("/rechnungen/5"));
        verify(invoices, times(5)).saveDraft(eq(5L), saved.capture());
        assertThat(saved.getAllValues().get(0).getLines()).hasSize(1);
        assertThat(saved.getAllValues().get(1).getLines()).hasSize(2);
        assertThat(saved.getAllValues().get(1).getLines().get(1).getCategory()).isEqualTo(TaxCategory.KLEINUNTERNEHMER);
        assertThat(saved.getAllValues().get(2).getLines()).extracting(io.github.grigoriev.im.invoice.LineForm::getText)
                .containsExactly("Reise");
        assertThat(saved.getAllValues().get(3).getLines()).hasSize(1);
        assertThat(saved.getAllValues().get(4).getLines()).hasSize(1);
    }

    @Test
    void showsTheProblemsOfADraftWithTheEnteredValues() throws Exception {
        Invoice draft = invoice(5, InvoiceStatus.DRAFT);
        when(invoices.find(5)).thenReturn(Optional.of(draft));
        when(invoices.customerName(any())).thenReturn("Verlag am Fluss GmbH");
        when(invoices.saveDraft(eq(5L), any())).thenThrow(new InvoiceException(List.of("Position 1: Der Preis ist keine Zahl.")));
        mvc.perform(post("/rechnungen/5").param("aktion", "speichern").param("lines[0].text", "Lesung")
                        .param("lines[0].price", "vierhundert").with(user("elena").roles("USER")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Position 1: Der Preis ist keine Zahl.")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("value=\"vierhundert\"")));
        mvc.perform(post("/rechnungen/5").param("issueDate", "kein-datum").with(user("elena").roles("USER")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Bitte gültige Daten eingeben")));
    }

    @Test
    void showsThePreviewOfACompleteDraftOnly() throws Exception {
        Invoice draft = invoice(5, InvoiceStatus.DRAFT);
        var document = io.github.grigoriev.im.einvoice.SampleInvoices.reading(InvoiceFormat.ZUGFERD_EN16931);
        when(invoices.find(5)).thenReturn(Optional.of(draft));
        when(invoices.document(draft)).thenReturn(Optional.of(document));
        when(pdf.render(document)).thenReturn("%PDF-1.7".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        mvc.perform(get("/rechnungen/5/vorschau").with(user("elena").roles("USER"))).andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", "inline; filename=\"Rechnungsentwurf.pdf\""))
                .andExpect(content().string("%PDF-1.7"));
        when(invoices.document(draft)).thenReturn(Optional.empty());
        var refused = mvc.perform(get("/rechnungen/5/vorschau").with(user("elena").roles("USER")))
                .andExpect(status().isSeeOther()).andExpect(redirectedUrl("/rechnungen/5"))
                .andExpect(flash().attribute("error", "Die Vorschau braucht einen vollständigen Entwurf.")).andReturn();
        when(invoices.customerName(any())).thenReturn("Verlag am Fluss GmbH");
        mvc.perform(get("/rechnungen/5").flashAttrs(refused.getFlashMap()).with(user("elena").roles("USER")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Die Vorschau braucht einen vollständigen Entwurf.")));
        when(invoices.find(9)).thenReturn(Optional.empty());
        mvc.perform(get("/rechnungen/9/vorschau").with(user("elena").roles("USER"))).andExpect(status().isNotFound());
    }

    @Test
    void issuesADraftAndReportsAProblem() throws Exception {
        when(issues.issue(5, 3L)).thenReturn("2027-10");
        mvc.perform(post("/rechnungen/5/ausstellen").param("version", "3").with(user("elena").roles("USER")).with(csrf()))
                .andExpect(redirectedUrl("/rechnungen/5")).andExpect(flash().attribute("success", "Rechnung 2027-10 ausgestellt."));
        when(issues.issue(5, null)).thenThrow(new InvoiceException(List.of("Nicht gültig.")));
        mvc.perform(post("/rechnungen/5/ausstellen").with(user("elena").roles("USER")).with(csrf()))
                .andExpect(redirectedUrl("/rechnungen/5"))
                .andExpect(flash().attribute("error", "Nicht gültig."));
    }

    @Test
    void downloadsTheDocumentsOfAnIssuedInvoice() throws Exception {
        when(documents.find(6, io.github.grigoriev.im.invoice.DocumentKind.PDF)).thenReturn(Optional.of(
                new io.github.grigoriev.im.invoice.StoredDocument(1L, 6L, io.github.grigoriev.im.invoice.DocumentKind.PDF,
                        "Rechnung_2027-10.pdf", "%PDF-1.7".getBytes(java.nio.charset.StandardCharsets.US_ASCII), "ab", 8,
                        java.time.Instant.EPOCH)));
        when(documents.find(6, io.github.grigoriev.im.invoice.DocumentKind.XML)).thenReturn(Optional.of(
                new io.github.grigoriev.im.invoice.StoredDocument(2L, 6L, io.github.grigoriev.im.invoice.DocumentKind.XML,
                        "Rechnung_2027-10.xml", "<x/>".getBytes(java.nio.charset.StandardCharsets.US_ASCII), "cd", 4,
                        java.time.Instant.EPOCH)));
        mvc.perform(get("/rechnungen/6/dokumente/pdf").with(user("elena").roles("USER"))).andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"Rechnung_2027-10.pdf\""))
                .andExpect(content().string("%PDF-1.7"));
        mvc.perform(get("/rechnungen/6/dokumente/xml").with(user("elena").roles("USER"))).andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/xml"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"Rechnung_2027-10.xml\""))
                .andExpect(content().string("<x/>"));
        when(documents.find(5, io.github.grigoriev.im.invoice.DocumentKind.PDF)).thenReturn(Optional.empty());
        mvc.perform(get("/rechnungen/5/dokumente/pdf").with(user("elena").roles("USER"))).andExpect(status().isNotFound());
        mvc.perform(get("/rechnungen/6/dokumente/zip").with(user("elena").roles("USER"))).andExpect(status().isNotFound());

        when(invoices.find(6)).thenReturn(Optional.of(invoice(6, InvoiceStatus.ISSUED)));
        mvc.perform(get("/rechnungen/6/vorschau").with(user("elena").roles("USER"))).andExpect(status().isSeeOther())
                .andExpect(redirectedUrl("/rechnungen/6/dokumente/pdf"));
    }

    @Test
    void deletesADraftAndReportsAProblem() throws Exception {
        mvc.perform(post("/rechnungen/5/loeschen").with(user("elena").roles("USER")).with(csrf()))
                .andExpect(redirectedUrl("/rechnungen")).andExpect(flash().attribute("success", "Entwurf gelöscht."));
        verify(invoices).deleteDraft(5);
        doThrow(new InvoiceException(List.of("Eine ausgestellte Rechnung ändert sich nicht mehr."))).when(invoices).deleteDraft(6);
        mvc.perform(post("/rechnungen/6/loeschen").with(user("elena").roles("USER")).with(csrf()))
                .andExpect(flash().attribute("error", "Eine ausgestellte Rechnung ändert sich nicht mehr."));
    }

    @Test
    void showsTheTemplatesOfACustomerAndStartsADraftFromOne() throws Exception {
        when(customers.list()).thenReturn(List.of());
        when(customers.find(4)).thenReturn(Optional.of(CustomerTestData.vhs().withId(4L)));
        when(templates.list(4)).thenReturn(List.of());
        mvc.perform(get("/kunden/4").with(user("elena").roles("USER")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Noch keine Vorlage.")));
        when(templates.list(4)).thenReturn(List.of(template()));
        mvc.perform(get("/kunden/4").with(user("elena").roles("USER")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Lesung mit Gespräch")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"templateId\" value=\"7\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Rechnung erstellen")));
        mvc.perform(get("/kunden/neu").with(user("elena").roles("USER")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Neue Vorlage"))));

        when(invoices.createDraft(4L, 7L)).thenReturn(invoice(5, InvoiceStatus.DRAFT));
        mvc.perform(post("/rechnungen").param("customerId", "4").param("templateId", "7")
                        .with(user("elena").roles("USER")).with(csrf()))
                .andExpect(redirectedUrl("/rechnungen/5"));
    }

    @Test
    void createsEditsAndDeletesATemplate() throws Exception {
        when(templates.create(4)).thenReturn(template());
        mvc.perform(post("/kunden/4/vorlagen").with(user("elena").roles("USER")).with(csrf()))
                .andExpect(redirectedUrl("/kunden/4/vorlagen/7"));
        when(templates.create(9)).thenThrow(new InvoiceException(List.of("Diesen Kunden gibt es nicht.")));
        mvc.perform(post("/kunden/9/vorlagen").with(user("elena").roles("USER")).with(csrf()))
                .andExpect(redirectedUrl("/kunden")).andExpect(flash().attribute("error", "Diesen Kunden gibt es nicht."));

        when(templates.find(4, 7)).thenReturn(Optional.of(template()));
        when(customers.find(4)).thenReturn(Optional.of(CustomerTestData.vhs().withId(4L)));
        mvc.perform(get("/kunden/4/vorlagen/7").with(user("elena").roles("USER"))).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("value=\"Lesung mit Gespr&auml;ch\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("{Leistungszeitraum}")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Volkshochschule Musterstadt")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("value=\"400,00\"")));
        when(customers.find(4)).thenReturn(Optional.empty());
        mvc.perform(get("/kunden/4/vorlagen/7").with(user("elena").roles("USER")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("← –")));
        when(templates.find(4, 8)).thenReturn(Optional.empty());
        mvc.perform(get("/kunden/4/vorlagen/8").with(user("elena").roles("USER"))).andExpect(status().isNotFound());

        when(invoices.defaultCategory()).thenReturn(TaxCategory.KLEINUNTERNEHMER);
        var saved = org.mockito.ArgumentCaptor.forClass(TemplateForm.class);
        mvc.perform(post("/kunden/4/vorlagen/7").param("aktion", "speichern").param("name", "Lesung")
                        .param("lines[0].text", "Lesung").with(user("elena").roles("USER")).with(csrf()))
                .andExpect(redirectedUrl("/kunden/4/vorlagen/7")).andExpect(flash().attribute("success", "Vorlage gespeichert."));
        mvc.perform(post("/kunden/4/vorlagen/7").param("aktion", "position").param("lines[0].text", "Lesung")
                        .with(user("elena").roles("USER")).with(csrf()))
                .andExpect(flash().attributeCount(0));
        mvc.perform(post("/kunden/4/vorlagen/7").param("entfernen", "0").param("lines[0].text", "Lesung")
                        .param("lines[1].text", "Reise").with(user("elena").roles("USER")).with(csrf()));
        mvc.perform(post("/kunden/4/vorlagen/7").param("entfernen", "5").param("lines[0].text", "Lesung")
                        .with(user("elena").roles("USER")).with(csrf()));
        mvc.perform(post("/kunden/4/vorlagen/7").param("entfernen", "-1").param("lines[0].text", "Lesung")
                        .with(user("elena").roles("USER")).with(csrf()));
        verify(templates, times(5)).save(eq(4L), eq(7L), saved.capture());
        assertThat(saved.getAllValues().get(1).getLines()).hasSize(2);
        assertThat(saved.getAllValues().get(1).getLines().get(1).getCategory()).isEqualTo(TaxCategory.KLEINUNTERNEHMER);
        assertThat(saved.getAllValues().get(2).getLines()).extracting(io.github.grigoriev.im.invoice.LineForm::getText)
                .containsExactly("Reise");
        assertThat(saved.getAllValues().get(3).getLines()).hasSize(1);
        assertThat(saved.getAllValues().get(4).getLines()).hasSize(1);

        when(templates.save(eq(4L), eq(7L), any())).thenThrow(new InvoiceException(List.of("Bitte der Vorlage einen Namen geben.")));
        mvc.perform(post("/kunden/4/vorlagen/7").param("aktion", "speichern").param("name", "")
                        .with(user("elena").roles("USER")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Bitte der Vorlage einen Namen geben.")));
        mvc.perform(post("/kunden/4/vorlagen/8").with(user("elena").roles("USER")).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(post("/kunden/4/vorlagen/7").param("aktion", "position").param("paymentDays", "")
                        .param("lines[0].text", "Lesung").with(user("elena").roles("USER")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Bitte gültige Daten eingeben")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("value=\"Lesung\"")));

        when(templates.delete(4, 7)).thenReturn(template());
        mvc.perform(post("/kunden/4/vorlagen/7/loeschen").with(user("elena").roles("USER")).with(csrf()))
                .andExpect(redirectedUrl("/kunden/4"))
                .andExpect(flash().attribute("success", "Vorlage „Lesung mit Gespräch“ gelöscht."));
        when(templates.delete(4, 8)).thenThrow(new InvoiceException(List.of("Diese Vorlage gibt es nicht.")));
        mvc.perform(post("/kunden/4/vorlagen/8/loeschen").with(user("elena").roles("USER")).with(csrf()))
                .andExpect(flash().attribute("error", "Diese Vorlage gibt es nicht."));
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
