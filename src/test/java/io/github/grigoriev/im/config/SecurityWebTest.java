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

import io.github.grigoriev.im.audit.AuditAction;
import io.github.grigoriev.im.audit.AuditEvent;
import io.github.grigoriev.im.audit.AuditService;
import io.github.grigoriev.im.backup.BackupRun;
import io.github.grigoriev.im.backup.BackupRunService;
import io.github.grigoriev.im.backup.BackupService;
import io.github.grigoriev.im.backup.BackupStatus;
import io.github.grigoriev.im.country.Country;
import io.github.grigoriev.im.country.CountryService;
import io.github.grigoriev.im.customer.Customer;
import io.github.grigoriev.im.customer.CustomerException;
import io.github.grigoriev.im.customer.CustomerService;
import io.github.grigoriev.im.customer.CustomerTestData;
import io.github.grigoriev.im.document.BusinessDocument;
import io.github.grigoriev.im.document.DocumentCategory;
import io.github.grigoriev.im.document.DocumentException;
import io.github.grigoriev.im.document.DocumentFile;
import io.github.grigoriev.im.document.DocumentForm;
import io.github.grigoriev.im.document.DocumentService;
import io.github.grigoriev.im.document.ProcedureForm;
import io.github.grigoriev.im.document.ProcedureService;
import io.github.grigoriev.im.einvoice.EInvoiceFacts;
import io.github.grigoriev.im.expense.Expense;
import io.github.grigoriev.im.expense.ExpenseCategory;
import io.github.grigoriev.im.expense.ExpenseException;
import io.github.grigoriev.im.expense.ExpenseService;
import io.github.grigoriev.im.einvoice.InvoiceFormat;
import io.github.grigoriev.im.einvoice.InvoicePdfRenderer;
import io.github.grigoriev.im.einvoice.SampleInvoices;
import io.github.grigoriev.im.einvoice.TaxCategory;
import io.github.grigoriev.im.integrity.Damage;
import io.github.grigoriev.im.integrity.IntegrityMonitor;
import io.github.grigoriev.im.invoice.ArchiveService;
import io.github.grigoriev.im.invoice.DispatchChannel;
import io.github.grigoriev.im.invoice.DispatchForm;
import io.github.grigoriev.im.invoice.DispatchService;
import io.github.grigoriev.im.invoice.DocumentKind;
import io.github.grigoriev.im.invoice.DocumentStore;
import io.github.grigoriev.im.invoice.DraftForm;
import io.github.grigoriev.im.invoice.DraftLine;
import io.github.grigoriev.im.invoice.ExternalInvoiceService;
import io.github.grigoriev.im.invoice.Invoice;
import io.github.grigoriev.im.invoice.InvoiceException;
import io.github.grigoriev.im.invoice.InvoiceIntegrity;
import io.github.grigoriev.im.invoice.InvoiceOriginal;
import io.github.grigoriev.im.invoice.InvoiceService;
import io.github.grigoriev.im.invoice.InvoiceStatus;
import io.github.grigoriev.im.invoice.InvoiceTemplate;
import io.github.grigoriev.im.invoice.IssueService;
import io.github.grigoriev.im.invoice.LineForm;
import io.github.grigoriev.im.invoice.OriginalStore;
import io.github.grigoriev.im.invoice.Payment;
import io.github.grigoriev.im.invoice.PaymentForm;
import io.github.grigoriev.im.invoice.PaymentService;
import io.github.grigoriev.im.invoice.ReminderService;
import io.github.grigoriev.im.invoice.ReminderForm;
import io.github.grigoriev.im.invoice.ReminderChannel;
import io.github.grigoriev.im.invoice.Reminder;
import io.github.grigoriev.im.invoice.StoredDocument;
import io.github.grigoriev.im.invoice.TemplateForm;
import io.github.grigoriev.im.invoice.TemplateLine;
import io.github.grigoriev.im.invoice.TemplateService;
import io.github.grigoriev.im.mail.MailService;
import io.github.grigoriev.im.mail.MailText;
import io.github.grigoriev.im.mail.MailTextService;
import io.github.grigoriev.im.mail.SendForm;
import io.github.grigoriev.im.numbering.NumberPattern;
import io.github.grigoriev.im.numbering.NumberSequence;
import io.github.grigoriev.im.numbering.NumberService;
import io.github.grigoriev.im.seller.Activity;
import io.github.grigoriev.im.seller.ProfilePart;
import io.github.grigoriev.im.seller.SellerProfile;
import io.github.grigoriev.im.seller.SellerService;
import io.github.grigoriev.im.seller.SellerTestData;
import io.github.grigoriev.im.seller.SettingsException;
import io.github.grigoriev.im.seller.TaxNoteService;
import io.github.grigoriev.im.seller.TaxStatus;
import io.github.grigoriev.im.threshold.RevenueRow;
import io.github.grigoriev.im.threshold.Threshold;
import io.github.grigoriev.im.threshold.ThresholdService;
import io.github.grigoriev.im.user.AppUserDetailsService;
import io.github.grigoriev.im.user.AppUserRepository;
import io.github.grigoriev.im.user.Passkey;
import io.github.grigoriev.im.user.PasskeyService;
import io.github.grigoriev.im.user.Role;
import io.github.grigoriev.im.user.UserException;
import io.github.grigoriev.im.user.UserService;
import io.github.grigoriev.im.user.UserTestData;
import io.github.grigoriev.im.web.AccountController;
import io.github.grigoriev.im.web.AdminUserController;
import io.github.grigoriev.im.web.AuditController;
import io.github.grigoriev.im.web.BackupController;
import io.github.grigoriev.im.web.BaselineController;
import io.github.grigoriev.im.web.CountryController;
import io.github.grigoriev.im.web.CurrentUserAdvice;
import io.github.grigoriev.im.web.CustomerController;
import io.github.grigoriev.im.web.DocumentController;
import io.github.grigoriev.im.web.ExpenseController;
import io.github.grigoriev.im.web.HomeController;
import io.github.grigoriev.im.web.InvoiceController;
import io.github.grigoriev.im.web.InvoiceRecordsController;
import io.github.grigoriev.im.web.IssuedInvoicePage;
import io.github.grigoriev.im.web.LoginController;
import io.github.grigoriev.im.web.MailTextController;
import io.github.grigoriev.im.web.NumberController;
import io.github.grigoriev.im.web.ReportController;
import io.github.grigoriev.im.web.SettingsController;
import io.github.grigoriev.im.web.TaxNoteController;
import io.github.grigoriev.im.web.TemplateController;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.assertj.core.api.Assertions;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import tools.jackson.core.exc.StreamReadException;

/** The access rules of {@link SecurityConfig} with the real controllers and templates. */
@WebMvcTest(controllers = {HomeController.class, LoginController.class, AdminUserController.class, AccountController.class,
        SettingsController.class, TaxNoteController.class, NumberController.class,
        CustomerController.class, CountryController.class,
        AuditController.class, InvoiceController.class, InvoiceRecordsController.class, TemplateController.class,
        MailTextController.class, BaselineController.class, ReportController.class, BackupController.class,
        DocumentController.class, ExpenseController.class})
@Import({SecurityConfig.class, StaleFormHandler.class, CurrentUserAdvice.class, IssuedInvoicePage.class, ClockConfig.class,
        SessionEpoch.class, RecentLogin.class, UploadLimit.class})
@EnableConfigurationProperties(PasskeyProperties.class)
class SecurityWebTest {

    private static final String PASSWORD = "lang-genug-123";
    private static final String NO_PROFILE = "Bitte zuerst das Profil mit Name, Anschrift und Steuernummer ausfüllen.";

    @Autowired
    private SessionEpoch sessionEpoch;

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
    private InvoicePdfRenderer pdf;

    @MockitoBean
    private IssueService issues;

    @MockitoBean
    private ExternalInvoiceService externals;

    @MockitoBean
    private OriginalStore originals;

    @MockitoBean
    private DocumentStore documents;

    @MockitoBean
    private DispatchService dispatches;

    @MockitoBean
    private PaymentService payments;

    @MockitoBean
    private MailTextService mailTexts;

    @MockitoBean
    private MailService mail;

    @MockitoBean
    private ReminderService reminders;

    @MockitoBean
    private ThresholdService threshold;

    @MockitoBean
    private ArchiveService archive;

    @MockitoBean
    private BackupService backups;

    @MockitoBean
    private BackupRunService backupRuns;

    @MockitoBean
    private DocumentService businessDocuments;

    @MockitoBean
    private ProcedureService procedure;

    @MockitoBean
    private InvoiceIntegrity integrity;

    @MockitoBean
    private ExpenseService expenses;

    @MockitoBean
    private IntegrityMonitor monitor;

    @BeforeEach
    void noRevenueNoPayments() {
        when(integrity.check(any())).thenReturn(new InvoiceIntegrity.Report(List.of(), List.of()));
        when(threshold.today()).thenReturn(LocalDate.of(2027, 3, 18));
        when(threshold.current()).thenReturn(new Threshold(2027,
                new BigDecimal("18250.00"), true, new BigDecimal("101000.00"),
                new BigDecimal("984.00"), new BigDecimal("101984.00"),
                new BigDecimal("30000.00")));
        when(payments.summary(any())).thenReturn(new PaymentService.Payments(List.of(),
                BigDecimal.ZERO, new BigDecimal("480.00")));
    }

    private static InvoiceTemplate template() {
        return new InvoiceTemplate(7L, 4L, "Lesung mit Gespräch", InvoiceFormat.ZUGFERD_EN16931, 14, "Hallo", "Danke",
                null, "Rechnung {Rechnungsnummer}", "Anbei", List.of(new TemplateLine("Lesung", true, BigDecimal.ONE,
                        new BigDecimal("400"), TaxCategory.KLEINUNTERNEHMER)), Instant.EPOCH,
                Instant.EPOCH, 0L, null);
    }

    private static Invoice invoice(long id, InvoiceStatus status) {
        LocalDate day = LocalDate.of(2027, 3, 18);
        return new Invoice(id, status, 3L, status == InvoiceStatus.DRAFT ? null : "2027-01", InvoiceFormat.ZUGFERD_EN16931,
                day, day, day, 14, null, "Hallo", "Danke", null,
                List.of(new DraftLine("Lesung", true, BigDecimal.ONE, new BigDecimal("400"),
                        TaxCategory.KLEINUNTERNEHMER)), Instant.EPOCH, Instant.EPOCH, 0L,
                status == InvoiceStatus.DRAFT ? null : Instant.parse("2027-03-18T09:30:00Z"),
                status == InvoiceStatus.DRAFT ? null : "{snapshot}", null);
    }

    private void knownUsers() {
        when(repository.findByUsername("admin")).thenReturn(Optional.of(UserTestData.user(1, "admin", Role.ADMIN)));
        when(repository.findByUsername("elena")).thenReturn(Optional.of(UserTestData.user(2, "elena", Role.USER)));
    }

    @Test
    void sendsAnonymousVisitorsToTheLogin() throws Exception {
        mvc.perform(get("/")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
        mvc.perform(get("/admin/users")).andExpect(status().is3xxRedirection());
    }

    @Test
    void showsTheLoginPageWithItsMessagesAndTheCsp() throws Exception {
        mvc.perform(get("/login")).andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Anmelden")))
                .andExpect(header().string("Content-Security-Policy", SecurityConfig.CSP));
        mvc.perform(get("/login").param("error", ""))
                .andExpect(content().string(Matchers.containsString("Benutzername oder Passwort ist falsch.")));
        mvc.perform(get("/login").param("logout", ""))
                .andExpect(content().string(Matchers.containsString("Sie sind abgemeldet.")));
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
                        .param("remember-me", "on").with(csrf()))
                .andExpect(cookie().exists("remember-me"))
                .andExpect(cookie().maxAge("remember-me", (int) SecurityConfig.REMEMBER_ME.toSeconds()));
        verify(tokens).createNewToken(any());
        mvc.perform(post("/login").param("username", "elena").param("password", "falsch-falsch").with(csrf()))
                .andExpect(redirectedUrl("/login?error")).andExpect(unauthenticated());
    }

    @Test
    void handlesAFormWithoutValidCsrfToken() throws Exception {
        mvc.perform(post("/login").param("username", "elena").param("password", PASSWORD))
                .andExpect(redirectedUrl("/login?expired"));
        mvc.perform(post("/logout").with(user("elena")).with(csrf().useInvalidToken()).header("Origin", "http://localhost"))
                .andExpect(redirectedUrl("/login?logout"));
        mvc.perform(post("/logout").with(user("elena")).with(csrf().useInvalidToken())
                        .header("Origin", "https://boese.example"))
                .andExpect(redirectedUrl("/login?expired"));
        when(tokens.getTokenForSeries("serie")).thenReturn(new org.springframework.security.web.authentication
                .rememberme.PersistentRememberMeToken("elena", "serie", "wert", new Date()));
        String cookie = Base64.getEncoder().withoutPadding().encodeToString("serie:wert".getBytes());
        mvc.perform(post("/logout").with(csrf().useInvalidToken()).header("Sec-Fetch-Site", "same-origin")
                        .cookie(new Cookie("remember-me", cookie)))
                .andExpect(redirectedUrl("/login?logout"))
                .andExpect(cookie().maxAge("remember-me", 0));
        verify(tokens).removeUserTokens("elena");
        mvc.perform(get("/login").param("expired", ""))
                .andExpect(content().string(Matchers.containsString("Die Sitzung ist abgelaufen.")));
    }

    @Test
    void logsOut() throws Exception {
        mvc.perform(post("/logout").with(user("elena")).with(csrf())).andExpect(redirectedUrl("/login?logout"));
    }

    @Test
    void greetsTheUserAndHidesTheAdminMenu() throws Exception {
        knownUsers();
        mvc.perform(get("/").with(user("elena").roles("USER"))).andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Guten Tag, Name elena")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("/admin/users"))));
    }

    @Test
    void keepsTheAdminAreaForAdmins() throws Exception {
        knownUsers();
        mvc.perform(get("/admin/users").with(user("elena").roles("USER"))).andExpect(status().isForbidden());
        when(users.list()).thenReturn(List.of(UserTestData.user(1, "admin", Role.ADMIN),
                UserTestData.user(2, "elena", Role.USER)));
        when(passkeys.count("elena")).thenReturn(1);
        mvc.perform(get("/admin/users").with(user("admin").roles("ADMIN"))).andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Name elena")))
                .andExpect(content().string(Matchers.containsString("Neuer Benutzer")))
                .andExpect(content().string(Matchers.containsString("1 Passkey(s)")))
                .andExpect(content().string(Matchers.containsString("Passkeys entfernen")));
    }

    @Test
    void createsAUserAndReportsAProblem() throws Exception {
        knownUsers();
        mvc.perform(post("/admin/users").with(user("admin").roles("ADMIN")).with(csrf())
                        .param("username", "neu").param("displayName", "Neu").param("password", PASSWORD)
                        .param("confirm", PASSWORD).param("role", "USER"))
                .andExpect(redirectedUrl("/admin/users"))
                .andExpect(flash().attribute("success", "Benutzer „neu“ angelegt."));
        verify(users).create("neu", "Neu", PASSWORD, Role.USER);
        when(users.create(eq("x"), any(), any(), any())).thenThrow(new UserException("Kaputt."));
        mvc.perform(post("/admin/users").with(user("admin").roles("ADMIN")).with(csrf())
                        .param("username", "x").param("displayName", "X").param("password", "p").param("confirm", "p")
                        .param("role", "ADMIN"))
                .andExpect(flash().attribute("error", "Kaputt."))
                .andExpect(flash().attribute("username", "x"))
                .andExpect(flash().attribute("displayName", "X"));
        mvc.perform(post("/admin/users").with(user("admin").roles("ADMIN")).with(csrf())
                        .param("username", "y").param("displayName", "Y").param("password", PASSWORD)
                        .param("confirm", "anders").param("role", "USER"))
                .andExpect(flash().attribute("error", "Die beiden Passwörter stimmen nicht überein."))
                .andExpect(flash().attribute("username", "y"))
                .andExpect(flash().attribute("displayName", "Y"));
        verify(users, never()).create(eq("y"), any(), any(), any());
    }

    @Test
    void greetsAUserWithoutAccountRecordByTheLoginName() throws Exception {
        when(repository.findByUsername("ohne-datensatz")).thenReturn(Optional.empty());
        mvc.perform(get("/").with(user("ohne-datensatz").roles("USER"))).andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Guten Tag, ohne-datensatz")));
    }

    @Test
    void offersPasskeyLoginToAnonymousVisitors() throws Exception {
        mvc.perform(get("/login")).andExpect(content().string(Matchers.containsString("Mit Passkey anmelden")))
                .andExpect(content().string(Matchers.containsString("name=\"_csrf_header\"")));
        mvc.perform(post("/webauthn/authenticate/options").with(csrf())).andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("\"challenge\"")))
                .andExpect(content().string(Matchers.containsString("\"rpId\":\"localhost\"")));
        mvc.perform(post("/webauthn/register/options").with(csrf())).andExpect(status().is4xxClientError());
    }

    @Test
    void showsTheAccountWithItsPasskeys() throws Exception {
        knownUsers();
        when(passkeys.list("elena")).thenReturn(List.of(new Passkey("id1", "MacBook Air", UserTestData.CREATED, null),
                new Passkey("id2", "iPhone", UserTestData.CREATED, UserTestData.CREATED)));
        mvc.perform(get("/account").with(user("elena").roles("USER"))).andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("MacBook Air")))
                .andExpect(content().string(Matchers.containsString("Hinzugefügt am 02.01.2027 · zuletzt 02.01.2027")));
        when(passkeys.list("admin")).thenReturn(List.of());
        mvc.perform(get("/account").with(user("admin").roles("ADMIN")))
                .andExpect(content().string(Matchers.containsString("Noch kein Passkey.")));
    }

    @Test
    void aNewPasskeyNeedsARecentLoginOrTheConfirmedPassword() throws Exception {
        knownUsers();
        when(passkeys.list("elena")).thenReturn(List.of());
        mvc.perform(get("/account").with(user("elena").roles("USER"))).andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("id=\"passkey-confirm\"")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("data-passkey-register"))));
        mvc.perform(post("/webauthn/register/options").with(user("elena").roles("USER")).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post(URI.create("/webauthn/%72egister/options")).with(user("elena").roles("USER")).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/account").with(user("elena").authorities(new SimpleGrantedAuthority("ROLE_USER"),
                        FactorGrantedAuthority.withAuthority(FactorGrantedAuthority.PASSWORD_AUTHORITY)
                                .issuedAt(Instant.now()).build())))
                .andExpect(content().string(Matchers.containsString("data-passkey-register")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("id=\"passkey-confirm\""))));

        doThrow(new UserException("Das Passwort stimmt nicht.")).when(users).confirmPassword("elena", "falsch");
        mvc.perform(post("/account/confirm").param("password", "falsch").with(user("elena")).with(csrf()))
                .andExpect(redirectedUrl("/account")).andExpect(flash().attribute("error", "Das Passwort stimmt nicht."))
                .andExpect(request().sessionAttribute(RecentLogin.CONFIRMED, Matchers.nullValue()));
        mvc.perform(post("/account/confirm").param("password", "richtig").with(user("elena")).with(csrf()))
                .andExpect(redirectedUrl("/account"))
                .andExpect(request().sessionAttribute(RecentLogin.CONFIRMED, Matchers.notNullValue()));
        verify(users).confirmPassword("elena", "richtig");
    }

    @Test
    void renamesAndRemovesOwnPasskeysAndReportsProblems() throws Exception {
        mvc.perform(post("/account/passkeys/id1/rename").param("label", "Neu").with(user("elena")).with(csrf()))
                .andExpect(redirectedUrl("/account")).andExpect(flash().attribute("success", "Passkey umbenannt."));
        verify(passkeys).rename("elena", "id1", "Neu");
        doThrow(new UserException("Fremd.")).when(passkeys).rename("elena", "x", "Neu");
        mvc.perform(post("/account/passkeys/x/rename").param("label", "Neu").with(user("elena")).with(csrf()))
                .andExpect(flash().attribute("error", "Fremd."));
        mvc.perform(post("/account/passkeys/id1/delete").with(user("elena")).with(csrf()))
                .andExpect(flash().attribute("success", "Passkey entfernt."));
        verify(passkeys).remove("elena", "id1");
        doThrow(new UserException("Fremd.")).when(passkeys).remove("elena", "x");
        mvc.perform(post("/account/passkeys/x/delete").with(user("elena")).with(csrf()))
                .andExpect(flash().attribute("error", "Fremd."));
    }

    @Test
    void changesTheOwnPasswordAndReportsAProblem() throws Exception {
        doThrow(new UserException("Falsch.")).when(users).changePassword("paula", "x", PASSWORD);
        mvc.perform(post("/account/password").param("current", "x").param("password", PASSWORD).param("confirm", PASSWORD)
                        .with(user("paula")).with(csrf()))
                .andExpect(flash().attribute("error", "Falsch."));
        mvc.perform(post("/account/password").param("current", "alt").param("password", PASSWORD).param("confirm", "anders")
                        .with(user("paula")).with(csrf()))
                .andExpect(flash().attribute("error", "Die beiden Passwörter stimmen nicht überein."));
        Assertions.assertThat(sessionEpoch.ended("paula", 0)).as("a failed change ends no session").isFalse();
        mvc.perform(post("/account/password").param("current", "alt").param("password", PASSWORD).param("confirm", PASSWORD)
                        .with(user("paula")).with(csrf()))
                .andExpect(flash().attribute("success", "Passwort geändert."))
                .andExpect(request().sessionAttribute(SessionEpoch.KEPT, Matchers.notNullValue()));
        verify(users, times(1)).changePassword("paula", "alt", PASSWORD);
        Assertions.assertThat(sessionEpoch.ended("paula", 0)).as("the other sessions of paula end").isTrue();
    }

    @Test
    void theAdminRemovesThePasskeysOfAUser() throws Exception {
        when(users.get(2)).thenReturn(UserTestData.user(2, "elena", Role.USER));
        when(passkeys.removeAll("elena")).thenReturn(2);
        mvc.perform(post("/admin/users/2/passkeys/delete").with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(flash().attribute("success", "2 Passkey(s) von „elena“ entfernt."));
        when(users.get(9)).thenThrow(new UserException("Diesen Benutzer gibt es nicht."));
        mvc.perform(post("/admin/users/9/passkeys/delete").with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(flash().attribute("error", "Diesen Benutzer gibt es nicht."));
        mvc.perform(post("/admin/users/2/passkeys/delete").with(user("elena").roles("USER")).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void theAdminDeletesAnotherUserAndEndsItsSessions() throws Exception {
        when(users.list()).thenReturn(List.of(UserTestData.user(1, "admin", Role.ADMIN),
                UserTestData.user(2, "elena", Role.USER)));
        mvc.perform(get("/admin/users").with(user("admin").roles("ADMIN"))).andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("id=\"delete-dialog-2\"")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("id=\"delete-dialog-1\""))))
                .andExpect(content().string(Matchers.containsString("Benutzer elena löschen?")));
        when(users.delete(2)).thenReturn(UserTestData.user(2, "elena", Role.USER));
        mvc.perform(post("/admin/users/2/delete").with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(redirectedUrl("/admin/users"))
                .andExpect(flash().attribute("success", "Benutzer „elena“ gelöscht."));
        Assertions.assertThat(sessionEpoch.ended("elena", 0)).as("the sessions of elena end").isTrue();
        Assertions.assertThat(sessionEpoch.ended("admin", 0)).isFalse();
        when(users.delete(1)).thenThrow(new UserException("Das eigene Konto lässt sich nicht löschen."));
        mvc.perform(post("/admin/users/1/delete").with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(flash().attribute("error", "Das eigene Konto lässt sich nicht löschen."));
        Assertions.assertThat(sessionEpoch.ended("admin", 0)).isFalse();
        mvc.perform(post("/admin/users/2/delete").with(user("elena").roles("USER")).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void showsAnEmptyProfileAndThenTheSavedOneTabByTab() throws Exception {
        when(sellers.find()).thenReturn(Optional.empty());
        mvc.perform(get("/settings").with(user("elena").roles("USER"))).andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("id=\"seller-name\"")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("id=\"seller-iban\""))))
                .andExpect(content().string(Matchers.containsString("<span class=\"sr-only\">unvollständig</span>")))
                .andExpect(content().string(Matchers.containsString("Für Rechnungen nötig.")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("Steuerhinweise"))));
        mvc.perform(get("/settings").param("part", "tax").with(user("elena").roles("USER")))
                .andExpect(content().string(Matchers.containsString("Kleinunternehmer nach § 19 UStG")));
        when(sellers.find()).thenReturn(Optional.of(SellerTestData.profile()));
        mvc.perform(get("/settings").param("part", "bank").with(user("admin").roles("ADMIN")))
                .andExpect(content().string(Matchers.containsString("value=\"DE89 3704 0044 0532 0130 00\"")))
                .andExpect(content().string(Matchers.containsString("name=\"accountHolder\" value=\"Erika Mustermann\"")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("<span class=\"sr-only\">unvollständig</span>"))))
                .andExpect(content().string(Matchers.containsString("Steuerhinweise")));
        mvc.perform(get("/settings").param("part", "unknown").with(user("admin").roles("ADMIN")))
                .andExpect(content().string(Matchers.containsString("value=\"Erika Mustermann\"")));
    }

    @Test
    void savesATabOrShowsItWithTheProblemAtTheField() throws Exception {
        SellerProfile saved = SellerTestData.profile();
        when(sellers.save(any(), any())).thenReturn(saved);
        mvc.perform(post("/settings").param("part", "tax").param("taxStatus", "REGULAR")
                        .with(user("elena").roles("USER")).with(csrf()))
                .andExpect(redirectedUrl("/settings?part=tax"))
                .andExpect(flash().attribute("success", "Steuer gespeichert."));
        verify(sellers).save(eq(ProfilePart.TAX),
                argThat(profile -> profile.taxStatus() == TaxStatus.REGULAR));

        when(sellers.save(any(), any())).thenReturn(new SellerProfile("Erika", null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, false));
        mvc.perform(post("/settings").param("part", "address").param("name", "Erika")
                        .with(user("elena").roles("USER")).with(csrf()))
                .andExpect(flash().attribute("success", "Anschrift gespeichert. Für Rechnungen fehlt noch: Straße, PLZ, "
                        + "Ort, Land, Steuerstatus, Steuernummer oder USt-IdNr., IBAN, BIC."));

        when(sellers.find()).thenReturn(Optional.of(saved));
        when(sellers.save(any(), any())).thenThrow(new SettingsException(Map.of("iban", "Die IBAN ist ungültig.")));
        mvc.perform(post("/settings").param("part", "bank").param("iban", "DE00").with(user("elena").roles("USER")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Das Profil ist nicht gespeichert.")))
                .andExpect(content().string(Matchers.containsString("<span id=\"iban-hint\" class=\"text-xs text-error\">Die IBAN ist ungültig.</span>")))
                .andExpect(content().string(Matchers.containsString("aria-invalid=\"true\"")));
        when(sellers.find()).thenReturn(Optional.empty());
        mvc.perform(post("/settings").param("part", "address").param("countryCode", "JP").with(user("elena").roles("USER")).with(csrf()))
                .andExpect(status().isOk());
        verify(countries).options(null, "JP");
        mvc.perform(post("/settings").param("part", "x").param("name", "x").with(csrf())).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void keepsTheTaxNotesForTheAdmin() throws Exception {
        when(taxNotes.notes()).thenReturn(Map.of(TaxCategory.EXEMPT_4_21, "Steuerfreie Unterrichtsleistung gemäß § 4 Nr. 21 UStG."));
        mvc.perform(get("/admin/tax-notes").with(user("admin").roles("ADMIN"))).andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Steuerfreie Bildungsleistung (§ 4 Nr. 21 UStG)")))
                .andExpect(content().string(Matchers.containsString("name=\"note-EXEMPT_4_21\"")));
        mvc.perform(get("/admin/tax-notes").with(user("elena").roles("USER"))).andExpect(status().isForbidden());
        mvc.perform(post("/admin/tax-notes").param("note-EXEMPT_4_21", "Neu.").param("note-STANDARD_19", "x")
                        .param("other", "y").with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(redirectedUrl("/admin/tax-notes"))
                .andExpect(flash().attribute("success", "Steuerhinweise gespeichert."));
        verify(taxNotes).save(Map.of(TaxCategory.EXEMPT_4_21, "Neu."));
        doThrow(new SettingsException(List.of("Zu lang."))).when(taxNotes).save(Map.of());
        mvc.perform(post("/admin/tax-notes").with(user("admin").roles("ADMIN")).with(csrf()))
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
        mvc.perform(get("/admin/numbers").with(user("admin").roles("ADMIN"))).andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("value=\"{YYYY}-{NN}\"")))
                .andExpect(content().string(Matchers.containsString("2027-08")))
                .andExpect(content().string(Matchers.containsString("Der Zähler ist gesperrt.")))
                .andExpect(content().string(Matchers.containsString("id=\"start-2028\"")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("id=\"start-2027\""))));
        mvc.perform(get("/admin/numbers").with(user("elena").roles("USER"))).andExpect(status().isForbidden());
    }

    @Test
    void savesThePatternAndTheStartAndReportsProblems() throws Exception {
        mvc.perform(post("/admin/numbers/pattern").param("pattern", "RE-{YYYY}-{NNN}")
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(redirectedUrl("/admin/numbers")).andExpect(flash().attribute("success", "Muster gespeichert."));
        verify(numbers).savePattern("RE-{YYYY}-{NNN}");
        when(numbers.savePattern("{NN}")).thenThrow(new SettingsException(List.of("Jahr fehlt.")));
        mvc.perform(post("/admin/numbers/pattern").param("pattern", "{NN}").with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(flash().attribute("error", "Jahr fehlt."));

        mvc.perform(post("/admin/numbers/start").param("year", "2028").param("start", "10")
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(flash().attribute("success", "Startnummer für 2028 gespeichert."));
        verify(numbers).setStart(2028, 10);
        doThrow(new SettingsException(List.of("Gesperrt."))).when(numbers).setStart(2027, 5);
        mvc.perform(post("/admin/numbers/start").param("year", "2027").param("start", "5")
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(flash().attribute("error", "Gesperrt."));
        mvc.perform(post("/admin/numbers/start").param("year", "2028").param("start", "1")
                        .with(user("elena").roles("USER")).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void listsCustomersAndShowsOne() throws Exception {
        when(customers.list()).thenReturn(List.of());
        mvc.perform(get("/customers").with(user("elena").roles("USER"))).andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Noch keine Kunden.")));
        when(customers.list()).thenReturn(List.of(CustomerTestData.verlag().withId(3L), CustomerTestData.vhs().withId(4L)));
        when(customers.find(4)).thenReturn(Optional.of(CustomerTestData.vhs().withId(4L)));
        mvc.perform(get("/customers/4").with(user("elena").roles("USER"))).andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Verlag am Fluss GmbH")))
                .andExpect(content().string(Matchers.containsString("value=\"09162000-VHS01-42\"")))
                .andExpect(content().string(Matchers.containsString("XRechnung: nur XML</option>")))
                .andExpect(content().string(Matchers.containsString("aria-current=\"page\"")))
                .andExpect(content().string(Matchers.containsString("Endgültig löschen")));
        mvc.perform(get("/customers/new").with(user("elena").roles("USER"))).andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Neuer Kunde")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("Endgültig löschen"))));
        when(customers.find(9)).thenReturn(Optional.empty());
        mvc.perform(get("/customers/9").with(user("elena").roles("USER"))).andExpect(status().isNotFound());
        mvc.perform(get("/customers/new").param("from", "9").with(user("elena").roles("USER"))).andExpect(status().isNotFound());
    }

    @Test
    void tellsTwoContactsOfOneOrganizationApartAndCopiesOne() throws Exception {
        Customer weber = new Customer(5L, "Sprachschule am See", "Seeweg 5", "86150", "Musterstadt", "DE",
                "Frau Weber", "weber@sprachschule.example", null, null, InvoiceFormat.ZUGFERD_EN16931, null);
        Customer klein = new Customer(6L, "Sprachschule am See", "Seeweg 5", "86150", "Musterstadt", "DE",
                "Herr Klein", "klein@sprachschule.example", null, null, InvoiceFormat.ZUGFERD_EN16931, null);
        when(customers.list()).thenReturn(List.of(weber, klein));
        when(customers.find(5)).thenReturn(Optional.of(weber));
        mvc.perform(get("/customers/5").with(user("elena").roles("USER"))).andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString(">Sprachschule am See · Frau Weber</h2>")))
                .andExpect(content().string(Matchers.containsString(">Herr Klein</span>")))
                .andExpect(content().string(Matchers.containsString("href=\"/customers/new?from=5\"")));
        mvc.perform(get("/customers/new").param("from", "5").with(user("elena").roles("USER"))).andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("value=\"Seeweg 5\"")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("value=\"weber@sprachschule.example\""))));
        when(invoices.list()).thenReturn(List.of());
        mvc.perform(get("/invoices").with(user("elena").roles("USER")))
                .andExpect(content().string(Matchers.containsString(">Sprachschule am See · Frau Weber</option>")))
                .andExpect(content().string(Matchers.containsString(">Sprachschule am See · Herr Klein</option>")));
    }

    @Test
    void createsChangesAndDeletesCustomersAndReportsProblems() throws Exception {
        when(customers.create(any())).thenReturn(CustomerTestData.verlag().withId(3L));
        mvc.perform(post("/customers").param("name", "Verlag am Fluss GmbH").param("format", "ZUGFERD_EN16931")
                        .with(user("elena").roles("USER")).with(csrf()))
                .andExpect(redirectedUrl("/customers/3"))
                .andExpect(flash().attribute("success", "Kunde „Verlag am Fluss GmbH“ angelegt."));
        when(customers.create(any())).thenThrow(new CustomerException(List.of("Bitte Ort angeben.")));
        mvc.perform(post("/customers").param("id", "77").param("name", "Neu GmbH").with(user("elena").roles("USER")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Bitte Ort angeben.")))
                .andExpect(content().string(Matchers.containsString("value=\"Neu GmbH\"")))
                .andExpect(content().string(Matchers.containsString("action=\"/customers\"")));

        mvc.perform(post("/customers/3").param("name", "Verlag").with(user("elena").roles("USER")).with(csrf()))
                .andExpect(redirectedUrl("/customers/3")).andExpect(flash().attribute("success", "Änderungen gespeichert."));
        verify(customers).update(eq(3L), argThat(customer -> customer.name().equals("Verlag")));
        when(customers.update(eq(4L), any())).thenThrow(new CustomerException(List.of("Diesen Kunden gibt es nicht.")));
        mvc.perform(post("/customers/4").param("name", "Verlag").with(user("elena").roles("USER")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Diesen Kunden gibt es nicht.")))
                .andExpect(content().string(Matchers.containsString("action=\"/customers/4\"")));

        when(customers.delete(3)).thenReturn(CustomerTestData.verlag().withId(3L));
        mvc.perform(post("/customers/3/delete").with(user("elena").roles("USER")).with(csrf()))
                .andExpect(redirectedUrl("/customers"))
                .andExpect(flash().attribute("success", "Kunde „Verlag am Fluss GmbH“ gelöscht."));
        when(customers.delete(4)).thenThrow(new CustomerException(List.of("Diesen Kunden gibt es nicht.")));
        mvc.perform(post("/customers/4/delete").with(user("elena").roles("USER")).with(csrf()))
                .andExpect(flash().attribute("error", "Diesen Kunden gibt es nicht."));
    }

    @Test
    void offersTheListToNewRecordsAndKeepsTheStoredCountry() throws Exception {
        List<Country> list = List.of(new Country("DE", "Deutschland"), new Country("CH", "Schweiz"));
        when(countries.options(null, null)).thenReturn(list);
        when(sellers.find()).thenReturn(Optional.empty());
        mvc.perform(get("/settings").with(user("elena").roles("USER")))
                .andExpect(content().string(Matchers.containsString("<option value=\"DE\" selected=\"selected\">Deutschland</option>")))
                .andExpect(content().string(Matchers.containsString("<option value=\"CH\">Schweiz</option>")));
        mvc.perform(get("/customers/new").with(user("elena").roles("USER")))
                .andExpect(content().string(Matchers.containsString("<option value=\"DE\" selected=\"selected\">Deutschland</option>")));
        Customer kosovar = CustomerTestData.verlag();
        when(customers.find(7)).thenReturn(Optional.of(new Customer(7L, kosovar.name(), kosovar.street(),
                kosovar.postcode(), kosovar.city(), "XK", "Frau Krasniqi", null, null, null, kosovar.format(), null)));
        when(countries.options(null, "XK")).thenReturn(List.of(new Country("DE", "Deutschland"), new Country("XK", "XK")));
        mvc.perform(get("/customers/new").param("from", "7").with(user("elena").roles("USER")))
                .andExpect(content().string(Matchers.containsString("<option value=\"XK\" selected=\"selected\">XK</option>")));

        SellerProfile p = SellerTestData.profile();
        SellerProfile kosovo = new SellerProfile(p.name(), p.street(), p.postcode(), p.city(), "XK", null, p.email(),
                p.phone(), p.taxNumber(), null, p.iban(), p.bic(), p.taxStatus(), null, null, null, null, null, false);
        when(sellers.find()).thenReturn(Optional.of(kosovo));
        when(countries.options("XK", null)).thenReturn(List.of(new Country("DE", "Deutschland"), new Country("XK", "XK")));
        mvc.perform(get("/settings").with(user("elena").roles("USER")))
                .andExpect(content().string(Matchers.containsString("<option value=\"XK\" selected=\"selected\">XK</option>")));

        Customer swiss = CustomerTestData.verlag();
        swiss = new Customer(5L, swiss.name(), swiss.street(), swiss.postcode(), swiss.city(), "CH", null, null, null,
                null, swiss.format(), null);
        when(customers.find(5)).thenReturn(Optional.of(swiss));
        when(countries.options("CH", null)).thenReturn(list);
        mvc.perform(get("/customers/5").with(user("elena").roles("USER")))
                .andExpect(content().string(Matchers.containsString("<option value=\"CH\" selected=\"selected\">Schweiz</option>")));
        when(customers.update(eq(5L), any())).thenThrow(new CustomerException(List.of("Bitte ein Land aus der Liste wählen.")));
        mvc.perform(post("/customers/5").param("countryCode", "JP").with(user("elena").roles("USER")).with(csrf()))
                .andExpect(content().string(Matchers.containsString("Bitte ein Land aus der Liste wählen.")));
        verify(countries).options("CH", "JP");
        mvc.perform(get("/customers").with(user("elena").roles("USER"))).andExpect(status().isOk());
    }

    @Test
    void theAdminKeepsTheShortListOfCountries() throws Exception {
        when(countries.enabled()).thenReturn(List.of(new Country("DE", "Deutschland")));
        when(countries.others()).thenReturn(List.of(new Country("JP", "Japan")));
        mvc.perform(get("/admin/countries").with(user("admin").roles("ADMIN"))).andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("aria-label=\"Deutschland entfernen\"")))
                .andExpect(content().string(Matchers.containsString("<option value=\"JP\">Japan</option>")));
        mvc.perform(get("/admin/countries").with(user("elena").roles("USER"))).andExpect(status().isForbidden());

        when(countries.add("JP")).thenReturn(new Country("JP", "Japan"));
        mvc.perform(post("/admin/countries").param("code", "JP").with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(redirectedUrl("/admin/countries")).andExpect(flash().attribute("success", "Japan hinzugefügt."));
        when(countries.add("ZZ")).thenThrow(new IllegalArgumentException("Unknown country ZZ"));
        mvc.perform(post("/admin/countries").param("code", "ZZ").with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(flash().attribute("error", "Dieses Land gibt es nicht."));
        when(countries.remove("CH")).thenReturn(new Country("CH", "Schweiz"));
        mvc.perform(post("/admin/countries/CH/remove").with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(flash().attribute("success", "Schweiz entfernt."));
        mvc.perform(post("/admin/countries/CH/remove").with(user("elena").roles("USER")).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void showsTheAuditLogToTheAdminOnly() throws Exception {
        when(audit.latest()).thenReturn(List.of());
        mvc.perform(get("/admin/audit").with(user("admin").roles("ADMIN"))).andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Noch keine Einträge.")));
        when(audit.latest()).thenReturn(List.of(
                new AuditEvent(2L, Instant.parse("2027-03-18T08:12:00Z"), "elena", AuditAction.CUSTOMER_CREATED,
                        "Verlag am Fluss GmbH", null),
                new AuditEvent(1L, Instant.parse("2027-03-18T08:10:00Z"), "elena", AuditAction.TAX_NOTES_SAVED, "",
                        null)));
        mvc.perform(get("/admin/audit").with(user("admin").roles("ADMIN")))
                .andExpect(content().string(Matchers.containsString("18.03.2027, 09:12")))
                .andExpect(content().string(Matchers.containsString("Kunde angelegt</span><span\n                class=\"text-base-content/75\">: Verlag am Fluss GmbH")))
                .andExpect(content().string(Matchers.containsString(">Steuerhinweise gespeichert</span></span>")));
        mvc.perform(get("/admin/audit").with(user("elena").roles("USER"))).andExpect(status().isForbidden());
    }

    @Test
    void listsInvoicesAndCreatesADraft() throws Exception {
        when(invoices.list()).thenReturn(List.of());
        when(customers.list()).thenReturn(List.of());
        mvc.perform(get("/invoices").with(user("elena").roles("USER"))).andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Noch keine Rechnungen.")))
                .andExpect(content().string(Matchers.containsString("Legen Sie zuerst einen")));
        Invoice draft = invoice(5, InvoiceStatus.DRAFT);
        Invoice issued = invoice(6, InvoiceStatus.ISSUED);
        when(invoices.list()).thenReturn(List.of(issued, draft));
        when(invoices.customerName(any())).thenReturn("Verlag am Fluss GmbH");
        when(invoices.document(draft)).thenReturn(Optional.empty());
        when(documents.restore("{snapshot}")).thenReturn(SampleInvoices.reading(
                InvoiceFormat.ZUGFERD_EN16931));
        when(customers.list()).thenReturn(List.of(CustomerTestData.verlag().withId(3L)));
        mvc.perform(get("/invoices").with(user("elena").roles("USER")))
                .andExpect(content().string(Matchers.containsString("2027-01 · 18.03.2027")))
                .andExpect(content().string(Matchers.containsString("Entwurf · 18.03.2027")))
                .andExpect(content().string(Matchers.containsString("480,00 €")))
                .andExpect(content().string(Matchers.containsString("Neue Rechnung")));

        when(invoices.createDraft(3L, null)).thenReturn(draft);
        mvc.perform(post("/invoices").param("customerId", "3").with(user("elena").roles("USER")).with(csrf()))
                .andExpect(redirectedUrl("/invoices/5"));
        when(invoices.createDraft(4L, null)).thenThrow(new InvoiceException(List.of("Bitte zuerst das Profil ausfüllen.")));
        mvc.perform(post("/invoices").param("customerId", "4").with(user("elena").roles("USER")).with(csrf()))
                .andExpect(redirectedUrl("/invoices")).andExpect(flash().attribute("error", "Bitte zuerst das Profil ausfüllen."));
    }

    @Test
    void recordsADispatch() throws Exception {
        mvc.perform(post("/invoices/6/dispatches").param("sentOn", "2027-03-19").param("channel", "POST")
                        .param("recipient", "Buchhaltung").with(user("elena").roles("USER")).with(csrf()))
                .andExpect(redirectedUrl("/invoices/6")).andExpect(flash().attribute("success", "Versand vermerkt."));
        var form = ArgumentCaptor.forClass(DispatchForm.class);
        verify(dispatches).recordManual(ArgumentMatchers.eq(6L), form.capture());
        Assertions.assertThat(form.getValue().getSentOn()).isEqualTo(LocalDate.of(2027, 3, 19));
        Assertions.assertThat(form.getValue().getChannel())
                .isEqualTo(DispatchChannel.POST);
        Assertions.assertThat(form.getValue().getRecipient()).isEqualTo("Buchhaltung");

        when(dispatches.recordManual(ArgumentMatchers.eq(7L), any())).thenThrow(
                new InvoiceException(List.of("Erst ausstellen, dann versenden.")));
        mvc.perform(post("/invoices/7/dispatches").param("sentOn", "2027-03-19").param("channel", "POST")
                        .with(user("elena").roles("USER")).with(csrf()))
                .andExpect(redirectedUrl("/invoices/7")).andExpect(flash().attribute("error", "Erst ausstellen, dann versenden."));
        mvc.perform(post("/invoices/6/dispatches").param("sentOn", "kaputt").param("channel", "POST")
                        .with(user("elena").roles("USER")).with(csrf()))
                .andExpect(redirectedUrl("/invoices/6"))
                .andExpect(flash().attribute("error", "Bitte gültige Daten eingeben, zum Beispiel ein Datum."));
    }

    @Test
    void sendsAnInvoiceByEmail() throws Exception {
        mvc.perform(post("/invoices/6/send").param("to", " a@b.example ").param("subject", "Rechnung 2027-01")
                        .param("body", "Hallo").param("pdf", "true").with(user("elena").roles("USER")).with(csrf()))
                .andExpect(redirectedUrl("/invoices/6"))
                .andExpect(flash().attribute("success", "E-Mail an a@b.example gesendet."));
        var form = ArgumentCaptor.forClass(SendForm.class);
        verify(mail).send(ArgumentMatchers.eq(6L), form.capture());
        Assertions.assertThat(form.getValue().getSubject()).isEqualTo("Rechnung 2027-01");
        Assertions.assertThat(form.getValue().getBody()).isEqualTo("Hallo");
        Assertions.assertThat(form.getValue().isPdf()).isTrue();
        Assertions.assertThat(form.getValue().isXml()).isFalse();
        doThrow(new InvoiceException(List.of("Die E-Mail ging nicht raus."))).when(mail)
                .send(ArgumentMatchers.eq(7L), any());
        mvc.perform(post("/invoices/7/send").param("to", "a@b.example").param("subject", "Mein Betreff")
                        .with(user("elena").roles("USER")).with(csrf()))
                .andExpect(redirectedUrl("/invoices/7")).andExpect(flash().attribute("error", "Die E-Mail ging nicht raus."))
                .andExpect(flash().attribute("mailForm", Matchers.hasProperty("subject",
                        Matchers.is("Mein Betreff"))));
    }

    @Test
    void previewsTheEmailWithAPolicyOfItsOwn() throws Exception {
        when(mail.preview(ArgumentMatchers.eq(6L), any())).thenReturn("<p style=\"margin:0\">Hallo</p>");
        mvc.perform(post("/invoices/6/send/preview").param("subject", "Rechnung 2027-01").param("body", "Hallo")
                        .with(user("elena").roles("USER")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/html;charset=UTF-8"))
                .andExpect(header().string("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'; "
                        + "base-uri 'none'; form-action 'none'; frame-ancestors 'none'"))
                .andExpect(content().string("<p style=\"margin:0\">Hallo</p>"));
        var form = ArgumentCaptor.forClass(SendForm.class);
        verify(mail).preview(ArgumentMatchers.eq(6L), form.capture());
        Assertions.assertThat(form.getValue().getBody()).isEqualTo("Hallo");
        verify(mail, never()).send(ArgumentMatchers.anyLong(), any());
        when(mail.preview(ArgumentMatchers.eq(7L), any())).thenThrow(new InvoiceException(List.of("Storniert.")));
        mvc.perform(post("/invoices/7/send/preview").with(user("elena").roles("USER")).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(post("/invoices/6/send/preview").with(user("elena").roles("USER")))
                .andExpect(status().is3xxRedirection());
        verify(mail, times(2)).preview(ArgumentMatchers.anyLong(), any());
    }

    @Test
    void remindsByEmailOrByHand() throws Exception {
        mvc.perform(post("/invoices/6/remind").param("to", " a@b.example ").param("subject", "Zahlungserinnerung")
                        .param("body", "Bitte zahlen").param("pdf", "true").with(user("elena").roles("USER")).with(csrf()))
                .andExpect(redirectedUrl("/invoices/6"))
                .andExpect(flash().attribute("success", "Zahlungserinnerung an a@b.example gesendet."));
        var form = ArgumentCaptor.forClass(SendForm.class);
        verify(mail).sendReminder(ArgumentMatchers.eq(6L), form.capture());
        Assertions.assertThat(form.getValue().getBody()).isEqualTo("Bitte zahlen");
        doThrow(new InvoiceException(List.of("Für diese Rechnung ist nichts offen."))).when(mail)
                .sendReminder(ArgumentMatchers.eq(7L), any());
        mvc.perform(post("/invoices/7/remind").param("to", "a@b.example").param("subject", "Mein Betreff")
                        .with(user("elena").roles("USER")).with(csrf()))
                .andExpect(redirectedUrl("/invoices/7"))
                .andExpect(flash().attribute("error", "Für diese Rechnung ist nichts offen."))
                .andExpect(flash().attribute("reminderForm", Matchers.hasProperty("subject", Matchers.is("Mein Betreff"))));

        when(mail.previewReminder(ArgumentMatchers.eq(6L), any())).thenReturn("<p style=\"margin:0\">Bitte</p>");
        mvc.perform(post("/invoices/6/remind/preview").param("body", "Bitte").with(user("elena").roles("USER")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'; "
                        + "base-uri 'none'; form-action 'none'; frame-ancestors 'none'"))
                .andExpect(content().string("<p style=\"margin:0\">Bitte</p>"));
        when(mail.previewReminder(ArgumentMatchers.eq(7L), any())).thenThrow(new InvoiceException(List.of("Bezahlt.")));
        mvc.perform(post("/invoices/7/remind/preview").with(user("elena").roles("USER")).with(csrf()))
                .andExpect(status().isNotFound());

        mvc.perform(post("/invoices/6/reminders").param("remindedOn", "2027-04-05").param("note", "telefonisch")
                        .with(user("elena").roles("USER")).with(csrf()))
                .andExpect(redirectedUrl("/invoices/6")).andExpect(flash().attribute("success", "Erinnerung vermerkt."));
        var manual = ArgumentCaptor.forClass(ReminderForm.class);
        verify(reminders).recordManual(ArgumentMatchers.eq(6L), manual.capture());
        Assertions.assertThat(manual.getValue().getRemindedOn()).isEqualTo(LocalDate.of(2027, 4, 5));
        Assertions.assertThat(manual.getValue().getNote()).isEqualTo("telefonisch");
        when(reminders.recordManual(ArgumentMatchers.eq(7L), any())).thenThrow(
                new InvoiceException(List.of("Für diese Rechnung ist nichts offen.")));
        mvc.perform(post("/invoices/7/reminders").param("remindedOn", "2027-04-05").with(user("elena").roles("USER"))
                        .with(csrf()))
                .andExpect(redirectedUrl("/invoices/7"))
                .andExpect(flash().attribute("error", "Für diese Rechnung ist nichts offen."));
        mvc.perform(post("/invoices/6/reminders").param("remindedOn", "kaputt").with(user("elena").roles("USER"))
                        .with(csrf()))
                .andExpect(redirectedUrl("/invoices/6"))
                .andExpect(flash().attribute("error", "Bitte gültige Daten eingeben, zum Beispiel ein Datum."));
    }

    @Test
    void showsTheRemindersOfAnOpenInvoice() throws Exception {
        Invoice issued = invoice(6, InvoiceStatus.SENT);
        when(invoices.find(6)).thenReturn(Optional.of(issued));
        when(documents.restore("{snapshot}")).thenReturn(SampleInvoices.reading(InvoiceFormat.ZUGFERD_EN16931));
        when(payments.summary(any())).thenReturn(new PaymentService.Payments(List.of(), BigDecimal.ZERO,
                new BigDecimal("480.00")));
        mvc.perform(get("/invoices/6").with(user("elena").roles("USER")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("Zahlungserinnerung…"))))
                .andExpect(content().string(Matchers.not(Matchers.containsString("id=\"invoice-reminders\""))));
        when(reminders.isRemindable(6)).thenReturn(true);
        when(reminders.overdue(issued)).thenReturn(true);
        when(reminders.list(6)).thenReturn(List.of(
                new Reminder(1L, 6L, LocalDate.of(2027, 4, 5), ReminderChannel.EMAIL_APP, "a@b.example", null, "elena",
                        Instant.EPOCH),
                new Reminder(2L, 6L, LocalDate.of(2027, 4, 12), ReminderChannel.MANUAL, null, "telefonisch", "elena",
                        Instant.EPOCH),
                new Reminder(3L, 6L, LocalDate.of(2027, 4, 13), ReminderChannel.MANUAL, null, null, "elena",
                        Instant.EPOCH)));
        mvc.perform(get("/invoices/6").with(user("elena").roles("USER")))
                .andExpect(content().string(Matchers.containsString("Zahlungserinnerung…")))
                .andExpect(content().string(Matchers.containsString("btn-warning")))
                .andExpect(content().string(Matchers.containsString("Die Rechnung war am 01.04.2027 fällig.")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("Per E-Mail erinnern"))))
                .andExpect(content().string(Matchers.containsString("Erinnerung vermerken")))
                .andExpect(content().string(Matchers.containsString("E-Mail aus der App an a@b.example")))
                .andExpect(content().string(Matchers.containsString("telefonisch")))
                .andExpect(content().string(Matchers.containsString("13.04.2027")))
                .andExpect(content().string(Matchers.containsString("Von Hand vermerkt")));
        SendForm send = new SendForm();
        send.setTo("buchhaltung@verlag.example");
        send.setSubject("Zahlungserinnerung zur Rechnung 2027-01");
        send.setBody("Bitte zahlen");
        send.setPdf(true);
        when(mail.configured()).thenReturn(true);
        when(mail.form(ArgumentMatchers.eq(issued), any())).thenReturn(send);
        when(mail.reminderForm(ArgumentMatchers.eq(issued), any())).thenReturn(send);
        when(reminders.overdue(issued)).thenReturn(false);
        mvc.perform(get("/invoices/6").with(user("elena").roles("USER")))
                .andExpect(content().string(Matchers.containsString("Per E-Mail erinnern")))
                .andExpect(content().string(Matchers.containsString("Die Rechnung ist am 01.04.2027 fällig.")))
                .andExpect(content().string(Matchers.containsString("value=\"Zahlungserinnerung zur Rechnung 2027-01\"")))
                .andExpect(content().string(Matchers.containsString("id=\"invoice-reminder\">")));
        SendForm typed = new SendForm();
        typed.setTo("neu@kunde.example");
        typed.setSubject("Erinnerung");
        typed.setBody("Mein Text");
        mvc.perform(get("/invoices/6").flashAttr("reminderForm", typed).with(user("elena").roles("USER")))
                .andExpect(content().string(Matchers.containsString("id=\"invoice-reminder\" open=\"open\"")))
                .andExpect(content().string(Matchers.containsString("value=\"neu@kunde.example\"")));
        verify(mail).reminderForm(any(), any());
    }

    @Test
    void recordsAPayment() throws Exception {
        when(payments.add(ArgumentMatchers.eq(6L), any()))
                .thenReturn(InvoiceStatus.PAID);
        mvc.perform(post("/invoices/6/payments").param("paidOn", "2027-03-19").param("amount", "480,00")
                        .param("note", "Überweisung").with(user("elena").roles("USER")).with(csrf()))
                .andExpect(redirectedUrl("/invoices/6"))
                .andExpect(flash().attribute("success", "Zahlung erfasst. Die Rechnung ist bezahlt."));
        var form = ArgumentCaptor.forClass(PaymentForm.class);
        verify(payments).add(ArgumentMatchers.eq(6L), form.capture());
        Assertions.assertThat(form.getValue().getPaidOn()).isEqualTo(LocalDate.of(2027, 3, 19));
        Assertions.assertThat(form.getValue().getAmount()).isEqualTo("480,00");
        Assertions.assertThat(form.getValue().getNote()).isEqualTo("Überweisung");

        when(payments.add(ArgumentMatchers.eq(7L), any()))
                .thenReturn(InvoiceStatus.SENT);
        mvc.perform(post("/invoices/7/payments").param("paidOn", "2027-03-19").param("amount", "100")
                        .with(user("elena").roles("USER")).with(csrf()))
                .andExpect(flash().attribute("success", "Zahlung erfasst."));
        when(payments.add(ArgumentMatchers.eq(8L), any()))
                .thenThrow(new InvoiceException(List.of("Der Betrag ist 0.")));
        mvc.perform(post("/invoices/8/payments").param("paidOn", "2027-03-19").param("amount", "0")
                        .with(user("elena").roles("USER")).with(csrf()))
                .andExpect(redirectedUrl("/invoices/8")).andExpect(flash().attribute("error", "Der Betrag ist 0."));
        mvc.perform(post("/invoices/6/payments").param("paidOn", "gestern").param("amount", "1")
                        .with(user("elena").roles("USER")).with(csrf()))
                .andExpect(flash().attribute("error", "Bitte gültige Daten eingeben, zum Beispiel ein Datum."));
    }

    @Test
    void theOwnerEditsTheDefaultMailText() throws Exception {
        when(mailTexts.defaults()).thenReturn(new MailText("Rechnung {Rechnungsnummer}",
                "Hallo & Tschüss"));
        when(mailTexts.reminderDefaults()).thenReturn(new MailText("Zahlungserinnerung {Rechnungsnummer}",
                "Offen: {Offener Betrag}"));
        mvc.perform(get("/settings/mail").with(user("admin").roles("ADMIN"))).andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("value=\"Zahlungserinnerung {Rechnungsnummer}\"")))
                .andExpect(content().string(Matchers.containsString("Offen: {Offener Betrag}</textarea>")))
                .andExpect(content().string(Matchers.containsString("value=\"Rechnung {Rechnungsnummer}\"")))
                .andExpect(content().string(Matchers.containsString("Hallo &amp; Tschüss</textarea>")))
                .andExpect(content().string(Matchers.containsString("aria-selected=\"true\">E-Mail")));
        mvc.perform(get("/settings/mail").with(user("elena").roles("USER"))).andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("aria-selected=\"true\">E-Mail")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("Steuerhinweise"))));
        mvc.perform(post("/settings/mail").param("subject", "Neu").param("body", "Text")
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(redirectedUrl("/settings/mail")).andExpect(flash().attribute("success", "E-Mail-Text gespeichert."));
        verify(mailTexts).save("Neu", "Text");
        doThrow(new SettingsException(
                List.of("Der Betreff hat höchstens 200 Zeichen."))).when(mailTexts).save("zu lang", "Text");
        mvc.perform(post("/settings/mail").param("subject", "zu lang").param("body", "Text")
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Der Betreff hat höchstens 200 Zeichen.")))
                .andExpect(content().string(Matchers.containsString("value=\"zu lang\"")))
                .andExpect(content().string(Matchers.containsString("Offen: {Offener Betrag}</textarea>")));
        mvc.perform(post("/settings/mail/reminder").param("subject", "Erinnerung").param("body", "Bitte")
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(redirectedUrl("/settings/mail"))
                .andExpect(flash().attribute("success", "Text der Zahlungserinnerung gespeichert."));
        verify(mailTexts).saveReminder("Erinnerung", "Bitte");
        doThrow(new SettingsException(List.of("Der Text hat höchstens 4000 Zeichen."))).when(mailTexts)
                .saveReminder("Erinnerung", "zu lang");
        mvc.perform(post("/settings/mail/reminder").param("subject", "Erinnerung").param("body", "zu lang")
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Der Text hat höchstens 4000 Zeichen.")))
                .andExpect(content().string(Matchers.containsString("zu lang</textarea>")))
                .andExpect(content().string(Matchers.containsString("Hallo &amp; Tschüss</textarea>")));
        mvc.perform(post("/settings/mail/reminder").param("subject", "Von ihr").param("body", "Text")
                        .with(user("elena").roles("USER")).with(csrf()))
                .andExpect(redirectedUrl("/settings/mail"));
        verify(mailTexts).saveReminder("Von ihr", "Text");
        mvc.perform(post("/settings/mail").param("subject", "x").with(user("elena").roles("USER")))
                .andExpect(redirectedUrl("/login?expired"));
        verify(mailTexts, never()).save(eq("x"), any());
    }

    @Test
    void cancelsAnInvoiceThroughAStornorechnung() throws Exception {
        when(invoices.createCancellation(6)).thenReturn(invoice(9, InvoiceStatus.DRAFT));
        mvc.perform(post("/invoices/6/cancel").with(user("elena").roles("USER")).with(csrf()))
                .andExpect(redirectedUrl("/invoices/9"))
                .andExpect(flash().attribute("success", "Stornorechnung als Entwurf angelegt. Die Nummer folgt beim Ausstellen."));
        when(invoices.createCancellation(7)).thenThrow(new InvoiceException(List.of("Zu dieser Rechnung gibt es schon eine Stornorechnung.")));
        mvc.perform(post("/invoices/7/cancel").with(user("elena").roles("USER")).with(csrf()))
                .andExpect(redirectedUrl("/invoices/7"))
                .andExpect(flash().attribute("error", "Zu dieser Rechnung gibt es schon eine Stornorechnung."));

        Invoice original = invoice(6, InvoiceStatus.SENT);
        Invoice storno = invoice(9, InvoiceStatus.DRAFT);
        when(invoices.find(6)).thenReturn(Optional.of(original));
        when(invoices.find(9)).thenReturn(Optional.of(storno));
        when(invoices.customerName(any())).thenReturn("Verlag am Fluss GmbH");
        when(documents.restore("{snapshot}")).thenReturn(SampleInvoices.reading(
                InvoiceFormat.ZUGFERD_EN16931));
        mvc.perform(get("/invoices/6").with(user("elena").roles("USER")))
                .andExpect(content().string(Matchers.containsString("Stornieren…")))
                .andExpect(content().string(Matchers.containsString("action=\"/invoices/6/cancel\"")));
        when(invoices.cancellation(original)).thenReturn(Optional.of(storno));
        mvc.perform(get("/invoices/6").with(user("elena").roles("USER")))
                .andExpect(content().string(Matchers.containsString("Eine Stornorechnung ist als")))
                .andExpect(content().string(Matchers.containsString("href=\"/invoices/9\"")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("Stornieren…"))));
        Invoice issuedStorno = invoice(9, InvoiceStatus.ISSUED);
        when(invoices.cancellation(original)).thenReturn(Optional.of(issuedStorno));
        mvc.perform(get("/invoices/6").with(user("elena").roles("USER")))
                .andExpect(content().string(Matchers.containsString("Storniert durch die")))
                .andExpect(content().string(Matchers.containsString("Stornorechnung 2027-01")));

        when(invoices.cancelled(storno)).thenReturn(Optional.of(original));
        when(invoices.problems(storno)).thenReturn(List.of());
        mvc.perform(get("/invoices/9").with(user("elena").roles("USER")))
                .andExpect(content().string(Matchers.containsString("Stornorechnung · Entwurf")))
                .andExpect(content().string(Matchers.containsString("Storniert vollständig die")))
                .andExpect(content().string(Matchers.containsString("id=\"cancel-lines\"")))
                .andExpect(content().string(Matchers.containsString("disabled=\"disabled\"")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("Position hinzufügen"))));
        when(invoices.document(storno)).thenReturn(Optional.of(SampleInvoices.reading(
                InvoiceFormat.ZUGFERD_EN16931)));
        when(threshold.wouldExceed(any())).thenReturn(true);
        mvc.perform(get("/invoices/9").with(user("elena").roles("USER")))
                .andExpect(content().string(Matchers.containsString("id=\"cancel-total\"")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("über 100.000 €"))));
        when(threshold.wouldExceed(any())).thenReturn(false);

        Invoice xStorno = new Invoice(9L, InvoiceStatus.DRAFT, 3L, null, InvoiceFormat.XRECHNUNG, storno.issueDate(),
                storno.serviceFrom(), storno.serviceTo(), 0, "04011000-1", null, null, null, storno.lines(),
                Instant.EPOCH, Instant.EPOCH, 0L, null, null, null);
        when(invoices.find(9)).thenReturn(Optional.of(xStorno));
        when(invoices.cancelled(xStorno)).thenReturn(Optional.of(original));
        mvc.perform(post("/invoices/9").param("version", "0").param("issueDate", "kaputt")
                        .with(user("elena").roles("USER")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("<option value=\"XRECHNUNG\" selected=\"selected\">")));

        when(invoices.find(9)).thenReturn(Optional.of(issuedStorno));
        when(invoices.cancelled(issuedStorno)).thenReturn(Optional.of(original));
        mvc.perform(get("/invoices/9").with(user("elena").roles("USER")))
                .andExpect(content().string(Matchers.containsString("Stornorechnung zur")))
                .andExpect(content().string(Matchers.containsString("href=\"/invoices/6\"")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("Zahlung erfassen"))))
                .andExpect(content().string(Matchers.not(Matchers.containsString("id=\"invoice-payments\""))))
                .andExpect(content().string(Matchers.not(Matchers.containsString("Stornieren…"))));

        when(invoices.list()).thenReturn(List.of(issuedStorno));
        when(customers.list()).thenReturn(List.of());
        mvc.perform(get("/invoices").with(user("elena").roles("USER")))
                .andExpect(content().string(Matchers.containsString("Stornorechnung · 2027-01 · 18.03.2027")));
    }

    @Test
    void showsTheThresholdOnTheStartPage() throws Exception {
        mvc.perform(get("/").with(user("elena").roles("USER"))).andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Stand 18.03.2027")))
                .andExpect(content().string(Matchers.containsString("18.250,00 €")))
                .andExpect(content().string(Matchers.containsString("101.000,00 €")))
                .andExpect(content().string(Matchers.containsString("≈ 30.000,00 €")))
                .andExpect(content().string(Matchers.containsString("984,00 €")))
                .andExpect(content().string(Matchers.containsString("gilt nicht mehr")))
                .andExpect(content().string(Matchers.containsString("Mit dem Vorjahresumsatz")))
                .andExpect(content().string(Matchers.containsString("im nächsten Jahr nicht mehr")))
                .andExpect(content().string(Matchers.containsString("Nichts überfällig.")))
                .andExpect(content().string(Matchers.containsString("value=\"100\"")));
    }

    @Test
    void showsTheReportAndItsCsv() throws Exception {
        when(threshold.years()).thenReturn(List.of(2027));
        when(threshold.rows(2027)).thenReturn(List.of(new RevenueRow(
                LocalDate.of(2027, 3, 18), "2027-01", "Verlag am Fluss GmbH", new BigDecimal("560.00"),
                new BigDecimal("200.00"), new BigDecimal("360.00"), Map.of(
                        TaxCategory.KLEINUNTERNEHMER, new BigDecimal("200.00"), TaxCategory.EXEMPT_4_21,
                        new BigDecimal("360.00")), null, null)));
        mvc.perform(get("/reports").with(user("elena").roles("USER"))).andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Anlage EÜR 2027, Betriebseinnahmen")))
                .andExpect(content().string(Matchers.containsString("Kz 111")))
                .andExpect(content().string(Matchers.containsString(">560,00 €<")))
                .andExpect(content().string(Matchers.containsString(">360,00 €<")))
                .andExpect(content().string(Matchers.containsString("§ 4 Nr. 21 UStG, steuerfrei")))
                .andExpect(content().string(Matchers.containsString("18.03.2027 · 2027-01")))
                .andExpect(content().string(Matchers.containsString("href=\"/reports/payments.csv?year=2027\"")))
                .andExpect(content().string(Matchers.containsString("href=\"/reports/archive.zip?year=2027\"")));
        when(threshold.rows(2026)).thenReturn(List.of(new RevenueRow(
                LocalDate.of(2026, 3, 18), "2026-01", "Schule", new BigDecimal("119.00"),
                BigDecimal.ZERO, new BigDecimal("119.00"), Map.of(TaxCategory.STANDARD_19,
                        new BigDecimal("119.00")), null, null)));
        mvc.perform(get("/reports").param("year", "2026").with(user("elena").roles("USER")))
                .andExpect(content().string(Matchers.containsString("Einnahmen mit Umsatzsteuer von 119,00 €")));
        SellerProfile p = SellerTestData.profile();
        when(sellers.find()).thenReturn(Optional.of(new SellerProfile(p.name(), p.street(), p.postcode(), p.city(),
                p.countryCode(), null, p.email(), p.phone(), p.taxNumber(), null, p.iban(), p.bic(), p.taxStatus(), null,
                null, null, null, "98/765/43210", false)));
        mvc.perform(get("/reports").param("year", "2027").with(user("elena").roles("USER")))
                .andExpect(content().string(Matchers.containsString("Betriebseinnahmen: Unterricht")))
                .andExpect(content().string(Matchers.containsString("Betriebseinnahmen: Übrige Leistungen")))
                .andExpect(content().string(Matchers.containsString(">98/765/43210</span>")))
                .andExpect(content().string(Matchers.containsString("id=\"income-111-teaching\"")))
                .andExpect(content().string(Matchers.containsString("id=\"income-111-other\"")));
        mvc.perform(get("/settings").param("part", "tax").with(user("elena").roles("USER")))
                .andExpect(content().string(Matchers.containsString("value=\"98/765/43210\"")))
                .andExpect(content().string(Matchers.containsString("Steuernummer für Unterricht (§ 4 Nr. 21)")));
        mvc.perform(get("/reports").param("year", "2020").with(user("elena").roles("USER")))
                .andExpect(content().string(Matchers.containsString("Keine Zahlungen in diesem Jahr.")))
                .andExpect(content().string(Matchers.containsString("<option value=\"2020\" selected=\"selected\">")));
        mvc.perform(get("/reports/payments.csv").param("year", "2027").with(user("elena").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"Zahlungen_2027.csv\""))
                .andExpect(content().string(Matchers.containsString("18.03.2027;\"2027-01\";\"Verlag am Fluss GmbH\";560,00;200,00;360,00;")));
        var started = mvc.perform(get("/reports/archive.zip").param("year", "2027").with(user("elena").roles("USER")))
                .andExpect(request().asyncStarted()).andReturn();
        mvc.perform(asyncDispatch(started)).andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"Rechnungen_2027.zip\""));
        verify(archive).write(ArgumentMatchers.eq(2027), any());
        mvc.perform(get("/reports")).andExpect(status().is3xxRedirection());
    }

    @Test
    void showsTheLastBackupAndWarnsWhenOneIsOverdue() throws Exception {
        when(backupRuns.status()).thenReturn(new BackupStatus(null, null, true, true));
        mvc.perform(get("/admin/backup").with(user("admin").roles("ADMIN"))).andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Es gibt noch keine automatische Sicherung.")))
                .andExpect(content().string(Matchers.containsString("Es gibt noch keinen Wiederherstellungstest.")))
                .andExpect(content().string(Matchers.containsString(">noch keine</span></dd>")));

        OffsetDateTime night = OffsetDateTime.parse("2027-03-18T01:30:00Z");
        when(backupRuns.status()).thenReturn(new BackupStatus(
                new BackupRun("BACKUP", night, "Snapshot 1a2b3c4d, 1,2 MB"),
                new BackupRun("RESTORE_TEST", night.minusDays(40), ""), false, true));
        mvc.perform(get("/admin/backup").with(user("admin").roles("ADMIN"))).andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("18.03.2027, 02:30 · Snapshot 1a2b3c4d, 1,2 MB")))
                .andExpect(content().string(Matchers.containsString(">06.02.2027, 02:30</span></dd>")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("id=\"backup-overdue\""))))
                .andExpect(content().string(Matchers.containsString("älter als 35 Tage")));

        when(backupRuns.status()).thenReturn(new BackupStatus(
                new BackupRun("BACKUP", night.minusDays(2), ""), null, true, true));
        mvc.perform(get("/admin/backup").with(user("admin").roles("ADMIN"))).andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("älter als 36 Stunden")));
    }

    @Test
    void theAdminExportsAndImportsEverything() throws Exception {
        when(backupRuns.status()).thenReturn(new BackupStatus(null, null, true, true));
        mvc.perform(get("/admin/backup").with(user("admin").roles("ADMIN"))).andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Export herunterladen")))
                .andExpect(content().string(Matchers.containsString("enctype=\"multipart/form-data\"")))
                .andExpect(content().string(Matchers.containsString("aria-selected=\"true\">Datensicherung")));
        mvc.perform(get("/admin/backup").with(user("elena").roles("USER"))).andExpect(status().isForbidden());
        var started = mvc.perform(get("/admin/backup/export.zip").with(user("admin").roles("ADMIN")))
                .andExpect(request().asyncStarted()).andReturn();
        mvc.perform(asyncDispatch(started)).andExpect(status().isOk());
        verify(backups).export(any());
        MockMultipartFile file = new MockMultipartFile("file",
                "export.zip", "application/zip", new byte[] {1});
        mvc.perform(upload("/admin/backup/import")
                        .file(file).with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(redirectedUrl("/login?restored"));
        mvc.perform(get("/login").param("restored", ""))
                .andExpect(content().string(Matchers.containsString("Datensicherung eingespielt.")));
        mvc.perform(upload("/admin/backup/import")
                        .file(file).with(user("elena").roles("USER")).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void theAdminKeepsTheBaselines() throws Exception {
        when(threshold.baselines()).thenReturn(Map.of());
        when(threshold.newestBaselineYear()).thenReturn(2024);
        mvc.perform(get("/admin/baselines").with(user("admin").roles("ADMIN"))).andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Noch kein Vorjahresumsatz erfasst.")))
                .andExpect(content().string(Matchers.containsString("max=\"2026\" value=\"2024\"")));
        when(threshold.baselines()).thenReturn(Map.of(2026, new BigDecimal("18250.00")));
        mvc.perform(get("/admin/baselines").with(user("admin").roles("ADMIN")))
                .andExpect(content().string(Matchers.containsString("18.250,00 €")))
                .andExpect(content().string(Matchers.containsString("id=\"baseline-remove-2026\"")));
        mvc.perform(get("/admin/baselines").with(user("elena").roles("USER"))).andExpect(status().isForbidden());
        mvc.perform(post("/admin/baselines").param("year", "2026").param("amount", "18.250,00")
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(redirectedUrl("/admin/baselines"))
                .andExpect(flash().attribute("success", "Vorjahresumsatz 2026 gespeichert."));
        verify(threshold).saveBaseline(2026, "18.250,00");
        doThrow(new SettingsException(List.of("Ein Vorjahresumsatz gehört zu einem "
                + "vergangenen Jahr."))).when(threshold).saveBaseline(2027, "1");
        mvc.perform(post("/admin/baselines").param("year", "2027").param("amount", "1")
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
        when(documents.restore("{snapshot}")).thenReturn(SampleInvoices.reading(
                InvoiceFormat.ZUGFERD_EN16931));
        when(customers.list()).thenReturn(List.of());
        String issuedRow = "2027-01 · 18.03.2027";
        String draftRow = "Entwurf · 18.03.2027";
        mvc.perform(get("/invoices").with(user("elena").roles("USER")))
                .andExpect(content().string(Matchers.containsString(issuedRow)))
                .andExpect(content().string(Matchers.containsString(draftRow)))
                .andExpect(content().string(Matchers.containsString("<option value=\"2027\">2027</option>")));
        mvc.perform(get("/invoices").param("status", "DRAFT").with(user("elena").roles("USER")))
                .andExpect(content().string(Matchers.not(Matchers.containsString(issuedRow))))
                .andExpect(content().string(Matchers.containsString(draftRow)))
                .andExpect(content().string(Matchers.containsString("<option value=\"DRAFT\" selected=\"selected\">")));
        mvc.perform(get("/invoices").param("q", " FLUSS ").with(user("elena").roles("USER")))
                .andExpect(content().string(Matchers.containsString(issuedRow)))
                .andExpect(content().string(Matchers.not(Matchers.containsString(draftRow))))
                .andExpect(content().string(Matchers.containsString("value=\"FLUSS\"")));
        mvc.perform(get("/invoices").param("q", "2027-0").with(user("elena").roles("USER")))
                .andExpect(content().string(Matchers.containsString(issuedRow)))
                .andExpect(content().string(Matchers.not(Matchers.containsString(draftRow))));
        mvc.perform(get("/invoices").param("q", "volkshoch").with(user("elena").roles("USER")))
                .andExpect(content().string(Matchers.not(Matchers.containsString(issuedRow))))
                .andExpect(content().string(Matchers.containsString(draftRow)));
        mvc.perform(get("/invoices").param("year", "2027").param("status", "ISSUED").with(user("elena").roles("USER")))
                .andExpect(content().string(Matchers.containsString(issuedRow)))
                .andExpect(content().string(Matchers.not(Matchers.containsString(draftRow))))
                .andExpect(content().string(Matchers.containsString("<option value=\"2027\" selected=\"selected\">")));
        mvc.perform(get("/invoices").param("year", "2026").with(user("elena").roles("USER")))
                .andExpect(content().string(Matchers.containsString("Keine Rechnungen gefunden.")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("Noch keine Rechnungen."))));
        mvc.perform(get("/invoices").param("status", "NIX").with(user("elena").roles("USER")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void showsTheEditorOfADraftAndTheNoticeOfAnIssuedInvoice() throws Exception {
        Invoice draft = invoice(5, InvoiceStatus.DRAFT);
        when(invoices.find(5)).thenReturn(Optional.of(draft));
        when(invoices.customerName(any())).thenReturn("Verlag am Fluss GmbH");
        when(invoices.problems(draft)).thenReturn(List.of("Position 1: Bitte die Leistung beschreiben."));
        mvc.perform(get("/invoices/5").with(user("elena").roles("USER"))).andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("value=\"Lesung\"")))
                .andExpect(content().string(Matchers.containsString("value=\"400,00\"")))
                .andExpect(content().string(Matchers.containsString("Position 1: Bitte die Leistung beschreiben.")))
                .andExpect(content().string(Matchers.containsString("§ 19 UStG, Kleinunternehmer")))
                .andExpect(content().string(Matchers.containsString("Entwurf löschen")));
        when(invoices.problems(draft)).thenReturn(List.of());
        when(invoices.document(draft)).thenReturn(Optional.of(SampleInvoices.sale(
                InvoiceFormat.ZUGFERD_EN16931)));
        mvc.perform(get("/invoices/5").with(user("elena").roles("USER")))
                .andExpect(content().string(Matchers.containsString("Vollständig.")))
                .andExpect(content().string(Matchers.containsString("Netto 130,50 €")))
                .andExpect(content().string(Matchers.containsString("USt 19 % 10,60 €")))
                .andExpect(content().string(Matchers.containsString("146,33 €")))
                .andExpect(content().string(Matchers.containsString("Rechnung ausstellen…")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("über 100.000 €"))))
                .andExpect(content().string(Matchers.containsString("name=\"version\" value=\"0\"")));
        when(threshold.wouldExceed(any())).thenReturn(true);
        mvc.perform(get("/invoices/5").with(user("elena").roles("USER")))
                .andExpect(content().string(Matchers.containsString("über 100.000 €")));
        Invoice issued = invoice(6, InvoiceStatus.ISSUED);
        when(invoices.find(6)).thenReturn(Optional.of(issued));
        when(documents.restore("{snapshot}")).thenReturn(SampleInvoices.reading(
                InvoiceFormat.ZUGFERD_EN16931));
        when(documents.list(6)).thenReturn(List.of(new StoredDocument(1L, 6L,
                DocumentKind.PDF, "Rechnung_2027-10.pdf", new byte[] {1}, "ab12", 12345,
                Instant.EPOCH, "seal")));
        when(audit.history(6)).thenReturn(List.of(
                new AuditEvent(1L, Instant.parse("2027-03-18T08:00:00Z"), "elena", AuditAction.DRAFT_CREATED,
                        "Verlag am Fluss GmbH", 6L),
                new AuditEvent(2L, Instant.parse("2027-03-18T09:30:00Z"), "admin", AuditAction.INVOICE_ISSUED,
                        "2027-10, Verlag am Fluss GmbH", 6L)));
        mvc.perform(get("/invoices/6").with(user("elena").roles("USER")))
                .andExpect(content().string(Matchers.containsString("Unveränderbar seit")))
                .andExpect(content().string(Matchers.containsString("Rechnung 2027-10")))
                .andExpect(content().string(Matchers.containsString(
                        "min-w-0 [overflow-wrap:anywhere]\" id=\"invoice-number\"")))
                .andExpect(content().string(Matchers.containsString("Verlag am Fluss GmbH · 480,00 € · fällig am")))
                .andExpect(content().string(Matchers.containsString("Entwurf angelegt")))
                .andExpect(content().string(Matchers.containsString("18.03.2027 09:00")))
                .andExpect(content().string(Matchers.containsString("Rechnung ausgestellt")))
                .andExpect(content().string(Matchers.containsString("<span>admin</span>")))
                .andExpect(content().string(Matchers.containsString("ZUGFeRD und PDF/A-3 geprüft")))
                .andExpect(content().string(Matchers.containsString("2027-10, Verlag am Fluss GmbH")))
                .andExpect(content().string(Matchers.containsString("data-lucide=\"stamp\"")))
                .andExpect(content().string(Matchers.containsString("Als versendet markieren…")))
                .andExpect(content().string(Matchers.containsString("Noch keine Zahlung.")))
                .andExpect(content().string(Matchers.containsString("Das Versenden per E-Mail ist nicht eingerichtet.")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("Per E-Mail senden"))))
                .andExpect(content().string(Matchers.containsString("name=\"amount\" value=\"480,00\"")))
                .andExpect(content().string(Matchers.containsString("<option value=\"POST\">Post</option>")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("value=\"EMAIL_APP\""))))
                .andExpect(content().string(Matchers.not(Matchers.containsString("XRechnung geprüft"))))
                .andExpect(content().string(Matchers.containsString("480,00 €")))
                .andExpect(content().string(Matchers.containsString("18.03.2027 10:30")))
                .andExpect(content().string(Matchers.containsString("Rechnung_2027-10.pdf")))
                .andExpect(content().string(Matchers.containsString("12.345 Byte")))
                .andExpect(content().string(Matchers.containsString("SHA-256 ab12")))
                .andExpect(content().string(Matchers.containsString("href=\"/invoices/6/documents/pdf\"")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("Rechnung ausstellen…"))))
                .andExpect(content().string(Matchers.not(Matchers.containsString("Entwurf löschen"))));
        verify(invoices, never()).problems(issued);
        when(audit.history(6)).thenReturn(List.of(new AuditEvent(3L, Instant.parse("2027-03-19T09:30:00Z"),
                "elena", AuditAction.INVOICE_SENT, "", 6L)));
        when(dispatches.today()).thenReturn(LocalDate.of(2027, 3, 19));
        mvc.perform(get("/invoices/6").with(user("elena").roles("USER")))
                .andExpect(content().string(Matchers.containsString("data-lucide=\"send\"")))
                .andExpect(content().string(Matchers.containsString("value=\"2027-03-19\" max=\"2027-03-19\"")));
        when(payments.summary(any())).thenReturn(new PaymentService.Payments(List.of(
                new Payment(1L, 6L, LocalDate.of(2027, 3, 20),
                        new BigDecimal("500.00"), "zu viel", "elena", Instant.EPOCH)),
                new BigDecimal("500.00"), new BigDecimal("-20.00")));
        mvc.perform(get("/invoices/6").with(user("elena").roles("USER")))
                .andExpect(content().string(Matchers.containsString("20.03.2027")))
                .andExpect(content().string(Matchers.containsString("zu viel")))
                .andExpect(content().string(Matchers.containsString("Überzahlt")))
                .andExpect(content().string(Matchers.containsString(">20,00 €<")))
                .andExpect(content().string(Matchers.containsString("name=\"amount\" value=\"\"")));
        SendForm send = new SendForm();
        send.setTo("buchhaltung@verlag.example");
        send.setSubject("Rechnung 2027-01");
        send.setBody("Hallo & Tschüss");
        send.setPdf(true);
        when(mail.configured()).thenReturn(true);
        when(mail.form(ArgumentMatchers.eq(issued), any())).thenReturn(send);
        mvc.perform(get("/invoices/6").with(user("elena").roles("USER")))
                .andExpect(content().string(Matchers.containsString("Per E-Mail senden…")))
                .andExpect(content().string(Matchers.containsString("value=\"buchhaltung@verlag.example\"")))
                .andExpect(content().string(Matchers.containsString("Hallo &amp; Tschüss</textarea>")))
                .andExpect(content().string(Matchers.containsString("id=\"mail-pdf\" name=\"pdf\" type=\"checkbox\" class=\"checkbox checkbox-sm\"\n              value=\"true\" checked=\"checked\"")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("nicht eingerichtet"))));
        verify(mail).form(issued, "buchhaltung@verlag-am-fluss.example");
        SendForm typed = new SendForm();
        typed.setTo("neu@kunde.example");
        typed.setSubject("Mein Betreff");
        typed.setBody("Mein Text");
        typed.setXml(true);
        mvc.perform(get("/invoices/6").flashAttr("mailForm", typed).with(user("elena").roles("USER")))
                .andExpect(content().string(Matchers.containsString("value=\"neu@kunde.example\"")))
                .andExpect(content().string(Matchers.containsString("Mein Text</textarea>")))
                .andExpect(content().string(Matchers.containsString("id=\"invoice-mail\" open=\"open\"")));
        verify(mail).form(any(), any());
        when(mail.configured()).thenReturn(false);
        when(audit.history(6)).thenReturn(List.of());
        when(invoices.find(6)).thenReturn(Optional.of(invoice(6, InvoiceStatus.CANCELLED)));
        mvc.perform(get("/invoices/6").with(user("elena").roles("USER")))
                .andExpect(content().string(Matchers.containsString("Keine Einträge.")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("Als versendet markieren"))))
                .andExpect(content().string(Matchers.not(Matchers.containsString("nicht eingerichtet"))));
        when(invoices.find(6)).thenReturn(Optional.of(issued));
        when(documents.restore("{snapshot}")).thenReturn(SampleInvoices.reading(
                InvoiceFormat.XRECHNUNG));
        mvc.perform(get("/invoices/6").with(user("elena").roles("USER")))
                .andExpect(content().string(Matchers.containsString("XRechnung geprüft (KoSIT)")))
                .andExpect(content().string(Matchers.containsString("04011000-12345-67")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("ZUGFeRD und PDF/A-3"))));
        when(invoices.saveDraft(ArgumentMatchers.eq(6L), any())).thenThrow(
                new InvoiceException(List.of("Eine ausgestellte Rechnung ändert sich nicht mehr.")));
        mvc.perform(post("/invoices/6").with(user("elena").roles("USER")).with(csrf())).andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Eine ausgestellte Rechnung ändert sich nicht mehr.")));
        when(invoices.find(9)).thenReturn(Optional.empty());
        mvc.perform(get("/invoices/9").with(user("elena").roles("USER"))).andExpect(status().isNotFound());
        mvc.perform(post("/invoices/9").with(user("elena").roles("USER")).with(csrf())).andExpect(status().isNotFound());
    }

    @Test
    void offersTheBusinessWithTwoTaxNumbersOnly() throws Exception {
        when(customers.list()).thenReturn(List.of());
        mvc.perform(get("/customers/new").with(user("elena").roles("USER")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("customer-activity"))))
                .andExpect(content().string(Matchers.containsString("<input type=\"hidden\" id=\"activity\" name=\"activity\"")));
        SellerProfile p = SellerTestData.profile();
        when(sellers.find()).thenReturn(Optional.of(new SellerProfile(p.name(), p.street(), p.postcode(), p.city(),
                p.countryCode(), null, p.email(), p.phone(), p.taxNumber(), null, p.iban(), p.bic(), p.taxStatus(), null,
                null, null, null, "98/765/43210", false)));
        mvc.perform(get("/customers/new").with(user("elena").roles("USER")))
                .andExpect(content().string(Matchers.containsString("id=\"customer-activity\"")))
                .andExpect(content().string(Matchers.containsString(">Übrige Leistungen</option>")));
        when(customers.create(any())).thenReturn(CustomerTestData.verlag().withId(3L));
        mvc.perform(post("/customers").param("name", "Goethe-Zentrum").param("activity", "TEACHING")
                        .with(user("elena").roles("USER")).with(csrf()))
                .andExpect(redirectedUrl("/customers/3"));
        verify(customers).create(argThat(customer -> customer.activity() == Activity.TEACHING));

        Invoice draft = invoice(5, InvoiceStatus.DRAFT);
        when(invoices.find(5)).thenReturn(Optional.of(draft));
        when(invoices.customerName(any())).thenReturn("Goethe-Zentrum");
        mvc.perform(get("/invoices/5").with(user("elena").roles("USER")))
                .andExpect(content().string(Matchers.containsString("id=\"invoice-activity\"")));
        var saved = ArgumentCaptor.forClass(DraftForm.class);
        mvc.perform(post("/invoices/5").param("action", "save").param("format", "ZUGFERD_EN16931").param("activity", "TEACHING")
                        .param("lines[0].text", "Kurs").param("lines[0].price", "300")
                        .with(user("elena").roles("USER")).with(csrf()))
                .andExpect(redirectedUrl("/invoices/5"));
        verify(invoices).saveDraft(eq(5L), saved.capture());
        assertThat(saved.getValue().getActivity()).isEqualTo(Activity.TEACHING);

        when(templates.find(4, 7)).thenReturn(Optional.of(new InvoiceTemplate(7L, 4L, "Kurs", InvoiceFormat.ZUGFERD_EN16931,
                14, null, null, null, null, null, List.of(), Instant.EPOCH, Instant.EPOCH, 0L, Activity.TEACHING)));
        mvc.perform(get("/customers/4/templates/7").with(user("elena").roles("USER")))
                .andExpect(content().string(Matchers.containsString("id=\"template-activity\"")))
                .andExpect(content().string(Matchers.containsString("<option value=\"TEACHING\" selected=\"selected\">Unterricht</option>")));
    }

    @Test
    void savesADraftAddsAndRemovesLines() throws Exception {
        Invoice draft = invoice(5, InvoiceStatus.DRAFT);
        when(invoices.find(5)).thenReturn(Optional.of(draft));
        when(invoices.customerName(any())).thenReturn("Verlag am Fluss GmbH");
        when(invoices.defaultCategory()).thenReturn(TaxCategory.KLEINUNTERNEHMER);
        var saved = ArgumentCaptor.forClass(DraftForm.class);

        mvc.perform(post("/invoices/5").param("action", "save").param("format", "ZUGFERD_EN16931")
                        .param("lines[0].text", "Lesung").param("lines[0].price", "400")
                        .with(user("elena").roles("USER")).with(csrf()))
                .andExpect(redirectedUrl("/invoices/5")).andExpect(flash().attribute("success", "Entwurf gespeichert."));
        mvc.perform(post("/invoices/5").param("action", "add-line").param("lines[0].text", "Lesung")
                        .with(user("elena").roles("USER")).with(csrf()))
                .andExpect(redirectedUrl("/invoices/5")).andExpect(flash().attributeCount(0));
        mvc.perform(post("/invoices/5").param("remove", "0").param("lines[0].text", "Lesung")
                        .param("lines[1].text", "Reise").with(user("elena").roles("USER")).with(csrf()))
                .andExpect(redirectedUrl("/invoices/5"));
        mvc.perform(post("/invoices/5").param("remove", "7").param("lines[0].text", "Lesung")
                        .with(user("elena").roles("USER")).with(csrf()))
                .andExpect(redirectedUrl("/invoices/5"));
        mvc.perform(post("/invoices/5").param("remove", "-1").param("lines[0].text", "Lesung")
                        .with(user("elena").roles("USER")).with(csrf()))
                .andExpect(redirectedUrl("/invoices/5"));
        verify(invoices, times(5)).saveDraft(eq(5L), saved.capture());
        assertThat(saved.getAllValues().get(0).getLines()).hasSize(1);
        assertThat(saved.getAllValues().get(1).getLines()).hasSize(2);
        assertThat(saved.getAllValues().get(1).getLines().get(1).getCategory()).isEqualTo(TaxCategory.KLEINUNTERNEHMER);
        assertThat(saved.getAllValues().get(2).getLines()).extracting(LineForm::getText)
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
        mvc.perform(post("/invoices/5").param("action", "save").param("lines[0].text", "Lesung")
                        .param("lines[0].price", "vierhundert").with(user("elena").roles("USER")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Position 1: Der Preis ist keine Zahl.")))
                .andExpect(content().string(Matchers.containsString("value=\"vierhundert\"")));
        mvc.perform(post("/invoices/5").param("issueDate", "kein-datum").with(user("elena").roles("USER")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Bitte gültige Daten eingeben")));
    }

    @Test
    void showsThePreviewOfACompleteDraftOnly() throws Exception {
        Invoice draft = invoice(5, InvoiceStatus.DRAFT);
        var document = SampleInvoices.reading(InvoiceFormat.ZUGFERD_EN16931);
        when(invoices.find(5)).thenReturn(Optional.of(draft));
        when(invoices.document(draft)).thenReturn(Optional.of(document));
        when(pdf.render(document)).thenReturn("%PDF-1.7".getBytes(StandardCharsets.US_ASCII));
        mvc.perform(get("/invoices/5/preview").with(user("elena").roles("USER"))).andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", "inline; filename=\"Rechnungsentwurf.pdf\""))
                .andExpect(content().string("%PDF-1.7"));
        when(invoices.document(draft)).thenReturn(Optional.empty());
        var refused = mvc.perform(get("/invoices/5/preview").with(user("elena").roles("USER")))
                .andExpect(status().isSeeOther()).andExpect(redirectedUrl("/invoices/5"))
                .andExpect(flash().attribute("error", "Die Vorschau braucht einen vollständigen Entwurf.")).andReturn();
        when(invoices.customerName(any())).thenReturn("Verlag am Fluss GmbH");
        mvc.perform(get("/invoices/5").flashAttrs(refused.getFlashMap()).with(user("elena").roles("USER")))
                .andExpect(content().string(Matchers.containsString("Die Vorschau braucht einen vollständigen Entwurf.")));
        when(invoices.find(9)).thenReturn(Optional.empty());
        mvc.perform(get("/invoices/9/preview").with(user("elena").roles("USER"))).andExpect(status().isNotFound());
    }

    @Test
    void issuesADraftAndReportsAProblem() throws Exception {
        when(issues.issue(5, 3L)).thenReturn("2027-10");
        mvc.perform(post("/invoices/5/issue").param("version", "3").with(user("elena").roles("USER")).with(csrf()))
                .andExpect(redirectedUrl("/invoices/5")).andExpect(flash().attribute("success", "Rechnung 2027-10 ausgestellt."));
        when(issues.issue(5, null)).thenThrow(new InvoiceException(List.of("Nicht gültig.")));
        mvc.perform(post("/invoices/5/issue").with(user("elena").roles("USER")).with(csrf()))
                .andExpect(redirectedUrl("/invoices/5"))
                .andExpect(flash().attribute("error", "Nicht gültig."));
    }

    private static Invoice external(long id, InvoiceStatus status) {
        Invoice app = invoice(id, status);
        return new Invoice(id, status, 3L, app.number(), InvoiceFormat.EXTERNAL, app.issueDate(), app.serviceFrom(),
                app.serviceTo(), 14, "04011000-1", "Hallo", "Danke", null, app.lines(), Instant.EPOCH, Instant.EPOCH, 3L,
                app.issuedAt(), app.snapshot(), null);
    }

    @Test
    void registersAnExternalInvoiceWithItsFile() throws Exception {
        when(invoices.find(8)).thenReturn(Optional.of(external(8, InvoiceStatus.DRAFT)));
        when(invoices.customerName(any())).thenReturn("Verlag am Fluss GmbH");
        when(externals.nextNumber(any())).thenReturn("2026-04");
        mvc.perform(get("/invoices/8").with(user("elena").roles("USER"))).andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("id=\"invoice-register\"")))
                .andExpect(content().string(Matchers.containsString("id=\"register-next\">2026-04<")))
                .andExpect(content().string(Matchers.containsString("id=\"external-hint\"")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("id=\"invoice-preview\""))))
                .andExpect(content().string(Matchers.not(Matchers.containsString("id=\"invoice-intro\""))))
                .andExpect(content().string(Matchers.not(Matchers.containsString("id=\"invoice-issue\""))))
                .andExpect(content().string(Matchers.containsString("type=\"hidden\" id=\"intro\" name=\"intro\"")))
                .andExpect(content().string(Matchers.containsString("type=\"hidden\" id=\"closing\" name=\"closing\"")))
                .andExpect(content().string(Matchers.containsString(
                        "type=\"hidden\" id=\"buyerReference\" name=\"buyerReference\"")));
        mvc.perform(get("/invoices/8/preview").with(user("elena").roles("USER"))).andExpect(status().isSeeOther())
                .andExpect(redirectedUrl("/invoices/8"))
                .andExpect(flash().attribute("error", "Eine externe Rechnung hat keine Vorschau. Ihre Datei ist die Rechnung."));

        byte[] xlsx = "PK-Tabelle".getBytes(StandardCharsets.US_ASCII);
        when(externals.register(8, 3L, "2026-04", "April.xlsx", xlsx)).thenReturn("2026-04");
        MockMultipartFile file = new MockMultipartFile("file", "April.xlsx", "application/octet-stream", xlsx);
        mvc.perform(upload("/invoices/8/register").file(file).param("version", "3").param("number", "2026-04")
                        .with(user("elena").roles("USER")).with(csrf()))
                .andExpect(redirectedUrl("/invoices/8"))
                .andExpect(flash().attribute("success", "Externe Rechnung 2026-04 erfasst."));
        when(externals.register(8, null, null, null, new byte[0]))
                .thenThrow(new InvoiceException(List.of("Bitte eine Datei wählen.")));
        mvc.perform(upload("/invoices/8/register").with(user("elena").roles("USER")).with(csrf()))
                .andExpect(redirectedUrl("/invoices/8")).andExpect(flash().attribute("error", "Bitte eine Datei wählen."));
        MockMultipartFile huge = new MockMultipartFile("file", "x.pdf", "application/pdf",
                new byte[DocumentService.MAX_BYTES + 1]);
        mvc.perform(upload("/invoices/8/register").file(huge).with(user("elena").roles("USER")).with(csrf()))
                .andExpect(flash().attribute("error", DocumentService.TOO_LARGE));
    }

    @Test
    void showsAnExternalInvoiceWithItsOriginalFiles() throws Exception {
        Invoice registered = external(9, InvoiceStatus.ISSUED);
        when(invoices.find(9)).thenReturn(Optional.of(registered));
        when(documents.restore("{snapshot}")).thenReturn(SampleInvoices.reading(InvoiceFormat.EXTERNAL));
        InvoiceOriginal original = new InvoiceOriginal(1, 9, "Rechnung März.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "PK".getBytes(StandardCharsets.US_ASCII), "ab12", 2, "seal", Instant.EPOCH, "elena");
        when(originals.list(9)).thenReturn(List.of(original));
        when(mail.configured()).thenReturn(true);
        when(reminders.isRemindable(9)).thenReturn(true);
        mvc.perform(get("/invoices/9").with(user("elena").roles("USER"))).andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("id=\"invoice-originals\"")))
                .andExpect(content().string(Matchers.containsString("Rechnung März.xlsx")))
                .andExpect(content().string(Matchers.containsString("href=\"/invoices/9/originals/1\"")))
                .andExpect(content().string(Matchers.containsString("id=\"mail-external\"")))
                .andExpect(content().string(Matchers.containsString("Externe Rechnung: eigene Datei, von Hand erfasst")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("id=\"invoice-mail\""))))
                .andExpect(content().string(Matchers.not(Matchers.containsString("id=\"reminder-to\""))))
                .andExpect(content().string(Matchers.not(Matchers.containsString("id=\"invoice-documents\""))));
        verify(mail, never()).form(any(), any());
        verify(mail, never()).reminderForm(any(), any());

        when(originals.find(9, 1)).thenReturn(Optional.of(original));
        mvc.perform(get("/invoices/9/originals/1").with(user("elena").roles("USER"))).andExpect(status().isOk())
                .andExpect(header().string("Content-Type",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().string("Content-Disposition", Matchers.containsString("filename*=UTF-8''Rechnung%20M%C3%A4rz.xlsx")))
                .andExpect(content().string("PK"));
        when(originals.find(9, 2)).thenReturn(Optional.empty());
        mvc.perform(get("/invoices/9/originals/2").with(user("elena").roles("USER"))).andExpect(status().isNotFound());
        when(originals.find(9, 3)).thenThrow(new InvoiceException(List.of("kaputt")));
        mvc.perform(get("/invoices/9/originals/3").with(user("elena").roles("USER"))).andExpect(status().isSeeOther())
                .andExpect(redirectedUrl("/invoices/9"));

        byte[] copy = "%PDF-1.7".getBytes(StandardCharsets.US_ASCII);
        when(externals.addOriginal(9, "März.pdf", copy)).thenReturn(new InvoiceOriginal(2, 9, "März.pdf",
                "application/pdf", copy, "cd", 8, "seal", Instant.EPOCH, "elena"));
        mvc.perform(upload("/invoices/9/originals").file(new MockMultipartFile("file", "März.pdf", "application/pdf", copy))
                        .with(user("elena").roles("USER")).with(csrf()))
                .andExpect(redirectedUrl("/invoices/9")).andExpect(flash().attribute("success", "Datei „März.pdf“ hinzugefügt."));
        when(externals.addOriginal(9, null, new byte[0])).thenThrow(new InvoiceException(List.of("Bitte eine Datei wählen.")));
        mvc.perform(upload("/invoices/9/originals").with(user("elena").roles("USER")).with(csrf()))
                .andExpect(redirectedUrl("/invoices/9")).andExpect(flash().attribute("error", "Bitte eine Datei wählen."));
    }

    @Test
    void downloadsTheDocumentsOfAnIssuedInvoice() throws Exception {
        when(documents.find(6, DocumentKind.PDF)).thenReturn(Optional.of(
                new StoredDocument(1L, 6L, DocumentKind.PDF,
                        "Rechnung_2027-10.pdf", "%PDF-1.7".getBytes(StandardCharsets.US_ASCII), "ab", 8,
                        Instant.EPOCH, "seal")));
        when(documents.find(6, DocumentKind.XML)).thenReturn(Optional.of(
                new StoredDocument(2L, 6L, DocumentKind.XML,
                        "Rechnung_2027-10.xml", "<x/>".getBytes(StandardCharsets.US_ASCII), "cd", 4,
                        Instant.EPOCH, "seal")));
        mvc.perform(get("/invoices/6/documents/pdf").with(user("elena").roles("USER"))).andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"Rechnung_2027-10.pdf\""))
                .andExpect(content().string("%PDF-1.7"));
        mvc.perform(get("/invoices/6/documents/xml").with(user("elena").roles("USER"))).andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/xml"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"Rechnung_2027-10.xml\""))
                .andExpect(content().string("<x/>"));
        when(documents.find(5, DocumentKind.PDF)).thenReturn(Optional.empty());
        mvc.perform(get("/invoices/5/documents/pdf").with(user("elena").roles("USER"))).andExpect(status().isNotFound());
        mvc.perform(get("/invoices/6/documents/zip").with(user("elena").roles("USER"))).andExpect(status().isNotFound());

        when(invoices.find(6)).thenReturn(Optional.of(invoice(6, InvoiceStatus.ISSUED)));
        mvc.perform(get("/invoices/6/preview").with(user("elena").roles("USER"))).andExpect(status().isSeeOther())
                .andExpect(redirectedUrl("/invoices/6/documents/pdf"));
    }

    @Test
    void showsAChangedInvoiceAndKeepsItsFilesBack() throws Exception {
        Invoice issued = invoice(6, InvoiceStatus.ISSUED);
        when(invoices.find(6)).thenReturn(Optional.of(issued));
        when(documents.restore("{snapshot}")).thenReturn(SampleInvoices.reading(InvoiceFormat.ZUGFERD_EN16931));
        mvc.perform(get("/invoices/6").with(user("elena").roles("USER")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("invoice-integrity"))));
        when(integrity.check(issued)).thenReturn(new InvoiceIntegrity.Report(
                List.of("Die XML-Datei weicht vom Stand der Ausstellung ab."),
                List.of(new EInvoiceFacts.Difference("Gesamtbetrag", "480,00 €", "4.800,00 €"))));
        mvc.perform(get("/invoices/6").with(user("elena").roles("USER")))
                .andExpect(content().string(Matchers.containsString("id=\"invoice-integrity\"")))
                .andExpect(content().string(Matchers.containsString("Prüfung fehlgeschlagen")))
                .andExpect(content().string(Matchers.containsString("Die XML-Datei weicht vom Stand der Ausstellung ab.")))
                .andExpect(content().string(Matchers.containsString("4.800,00 €")));
        when(integrity.check(issued)).thenReturn(new InvoiceIntegrity.Report(List.of("Die Datei fehlt."), List.of()));
        mvc.perform(get("/invoices/6").with(user("elena").roles("USER")))
                .andExpect(content().string(Matchers.containsString("Die Datei fehlt.")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("invoice-differences"))));
        when(documents.find(6, DocumentKind.PDF)).thenThrow(new InvoiceException(List.of("kaputt")));
        mvc.perform(get("/invoices/6/documents/pdf").with(user("elena").roles("USER")))
                .andExpect(status().isSeeOther()).andExpect(redirectedUrl("/invoices/6"));
    }

    private static Expense expense(long id, boolean cancelled) {
        return new Expense(id, LocalDate.of(2027, 3, 2), new BigDecimal("49.90"), ExpenseCategory.LITERATURE,
                "Buchhandlung am Markt", "Wörterbuch", id == 2, cancelled ? Instant.parse("2027-03-05T10:00:00Z") : null,
                cancelled ? "doppelt erfasst" : null, "Quittung.pdf", "application/pdf", "ab12", 2048,
                Instant.parse("2027-03-02T09:00:00Z"), "elena");
    }

    @Test
    void listsAndEntersExpenses() throws Exception {
        when(expenses.today()).thenReturn(LocalDate.of(2027, 3, 18));
        when(expenses.years()).thenReturn(Set.of(2026));
        mvc.perform(get("/expenses").with(user("elena").roles("USER"))).andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Noch keine Ausgaben in diesem Jahr.")))
                .andExpect(content().string(Matchers.containsString("<option value=\"2026\">2026</option>")));
        when(expenses.ofYear(2026)).thenReturn(List.of(expense(1, false), expense(2, false), expense(3, true)));
        mvc.perform(get("/expenses").param("year", "2026").with(user("elena").roles("USER")))
                .andExpect(content().string(Matchers.containsString("Buchhandlung am Markt: Wörterbuch")))
                .andExpect(content().string(Matchers.containsString("02.03.2027 · Fachliteratur · Anlagegut")))
                .andExpect(content().string(Matchers.containsString("· storniert")))
                .andExpect(content().string(Matchers.containsString("id=\"expense-total\">49,90 €")))
                .andExpect(content().string(Matchers.containsString("Anlagegüter, nicht in der Summe")));
        mvc.perform(get("/expenses/new").with(user("elena").roles("USER"))).andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("value=\"2027-03-18\"")))
                .andExpect(content().string(Matchers.containsString("enctype=\"multipart/form-data\"")));
        when(expenses.add(any(), eq("Quittung.pdf"), any())).thenReturn(expense(7, false));
        MockMultipartFile receipt = new MockMultipartFile("file", "Quittung.pdf", "application/pdf", new byte[] {'%'});
        mvc.perform(upload("/expenses").file(receipt).param("amount", "49,90")
                .with(user("elena").roles("USER")).with(csrf()))
                .andExpect(redirectedUrl("/expenses/7")).andExpect(flash().attribute("success", "Ausgabe erfasst."));
        when(expenses.add(any(), any(), eq(new byte[0]))).thenThrow(new ExpenseException(List.of("Bitte eine Datei wählen.")));
        mvc.perform(upload("/expenses").with(user("elena").roles("USER")).with(csrf()))
                .andExpect(status().isOk()).andExpect(content().string(Matchers.containsString("Bitte eine Datei wählen.")));
        MockMultipartFile huge = new MockMultipartFile("file", "x.pdf", "application/pdf",
                new byte[DocumentService.MAX_BYTES + 1]);
        mvc.perform(upload("/expenses").file(huge).with(user("elena").roles("USER")).with(csrf()))
                .andExpect(status().isOk()).andExpect(content().string(Matchers.containsString(DocumentService.TOO_LARGE)));
        mvc.perform(get("/expenses")).andExpect(status().is3xxRedirection());
    }

    @Test
    void theReportListsAssetsAndSaysNothingIsMissing() throws Exception {
        when(threshold.today()).thenReturn(LocalDate.of(2027, 3, 18));
        mvc.perform(get("/reports").with(user("elena").roles("USER")))
                .andExpect(content().string(Matchers.containsString("Keine Ausgaben in diesem Jahr.")));
        when(expenses.ofYear(2027)).thenReturn(List.of(expense(2, false)));
        mvc.perform(get("/reports").with(user("elena").roles("USER")))
                .andExpect(content().string(Matchers.containsString("Anlagegüter, nicht in der Summe")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("Keine Ausgaben in diesem Jahr."))));
    }

    @Test
    void showsChangesAndCancelsAnExpense() throws Exception {
        when(expenses.find(1)).thenReturn(Optional.of(expense(1, false)));
        mvc.perform(get("/expenses/1").with(user("elena").roles("USER"))).andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("value=\"49,90\"")))
                .andExpect(content().string(Matchers.containsString("Stornieren…")))
                .andExpect(content().string(Matchers.containsString("href=\"/expenses?year=2027\"")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("expense-integrity"))));
        mvc.perform(post("/expenses/1").param("amount", "59,90").with(user("elena").roles("USER")).with(csrf()))
                .andExpect(redirectedUrl("/expenses/1")).andExpect(flash().attribute("success", "Änderungen gespeichert."));
        when(expenses.change(eq(1L), any())).thenThrow(new ExpenseException(List.of("Bitte die Art wählen.")));
        mvc.perform(post("/expenses/1").with(user("elena").roles("USER")).with(csrf()))
                .andExpect(status().isOk()).andExpect(content().string(Matchers.containsString("Bitte die Art wählen.")));
        mvc.perform(post("/expenses/1/cancel").param("reason", "doppelt").with(user("elena").roles("USER")).with(csrf()))
                .andExpect(redirectedUrl("/expenses/1"))
                .andExpect(flash().attribute("success", "Ausgabe storniert. Sie zählt nicht mehr."));
        when(expenses.cancel(1, null)).thenThrow(new ExpenseException(List.of("Bitte den Grund angeben.")));
        mvc.perform(post("/expenses/1/cancel").with(user("elena").roles("USER")).with(csrf()))
                .andExpect(redirectedUrl("/expenses/1")).andExpect(flash().attribute("error", "Bitte den Grund angeben."));
        when(expenses.find(3)).thenReturn(Optional.of(expense(3, true)));
        when(expenses.problems(3)).thenReturn(List.of("Quittung.pdf: Das Siegel passt nicht."));
        mvc.perform(get("/expenses/3").with(user("elena").roles("USER")))
                .andExpect(content().string(Matchers.containsString("Storniert am 05.03.2027: doppelt erfasst.")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("Stornieren…"))))
                .andExpect(content().string(Matchers.containsString("id=\"expense-integrity\"")));
        mvc.perform(get("/expenses/9").with(user("elena").roles("USER"))).andExpect(status().isNotFound());
    }

    @Test
    void downloadsAReceipt() throws Exception {
        when(expenses.file(1)).thenReturn(Optional.of(new DocumentFile("Quittung März.pdf", "application/pdf",
                new byte[] {'%', 'P'})));
        mvc.perform(get("/expenses/1/file").with(user("elena").roles("USER"))).andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", Matchers.containsString("Quittung%20M%C3%A4rz.pdf")))
                .andExpect(content().bytes(new byte[] {'%', 'P'}));
        mvc.perform(get("/expenses/9/file").with(user("elena").roles("USER"))).andExpect(status().isNotFound());
        when(expenses.file(3)).thenThrow(new ExpenseException(List.of("kaputt")));
        mvc.perform(get("/expenses/3/file").with(user("elena").roles("USER"))).andExpect(status().isSeeOther())
                .andExpect(redirectedUrl("/expenses/3"));
    }

    @Test
    void anUnreadableSnapshotShowsTheDamageInsteadOfAnError() throws Exception {
        when(invoices.find(6)).thenReturn(Optional.of(invoice(6, InvoiceStatus.ISSUED)));
        when(documents.restore("{snapshot}")).thenThrow(new StreamReadException(null, "kaputt"));
        when(monitor.check()).thenReturn(new IntegrityMonitor.Result(Instant.EPOCH, List.of(new Damage(6L,
                "Rechnung 2027-10", "/invoices/6", List.of("Der Stand der Ausstellung lässt sich nicht lesen.")))));
        mvc.perform(get("/invoices/6").with(user("elena").roles("USER")))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string(Matchers.containsString("Gespeicherte Daten lassen sich nicht lesen")))
                .andExpect(content().string(Matchers.containsString("Rechnung 2027-10")))
                .andExpect(content().string(Matchers.containsString("Der Stand der Ausstellung lässt sich nicht lesen.")));
        when(monitor.check()).thenReturn(new IntegrityMonitor.Result(Instant.EPOCH, List.of()));
        mvc.perform(get("/invoices/6").with(user("elena").roles("USER")))
                .andExpect(content().string(Matchers.containsString("Die Prüfung der Dokumente fand nichts.")));
    }

    @Test
    void warnsOnTheOverviewAndChecksOnRequest() throws Exception {
        when(monitor.last()).thenReturn(Optional.of(new IntegrityMonitor.Result(Instant.EPOCH,
                List.of(new Damage(6L, "Rechnung 2027-10", "/invoices/6", List.of("x"))))));
        mvc.perform(get("/").with(user("elena").roles("USER")))
                .andExpect(content().string(Matchers.containsString("id=\"integrity-alert\"")))
                .andExpect(content().string(Matchers.containsString("href=\"/invoices/6\">Rechnung 2027-10</a>")));
        when(backupRuns.status()).thenReturn(new BackupStatus(null, null, true, true));
        mvc.perform(get("/admin/backup").with(user("admin").roles("ADMIN")))
                .andExpect(content().string(Matchers.containsString("Abweichungen: 1, siehe Übersicht")));
        when(monitor.last()).thenReturn(Optional.of(new IntegrityMonitor.Result(Instant.EPOCH, List.of())));
        mvc.perform(get("/admin/backup").with(user("admin").roles("ADMIN")))
                .andExpect(content().string(Matchers.containsString("Alles in Ordnung")))
                .andExpect(content().string(Matchers.containsString("01.01.1970, 01:00")));
        when(monitor.last()).thenReturn(Optional.empty());
        mvc.perform(get("/admin/backup").with(user("admin").roles("ADMIN")))
                .andExpect(content().string(Matchers.containsString("noch keine")));
        when(monitor.check()).thenReturn(new IntegrityMonitor.Result(Instant.EPOCH, List.of()));
        mvc.perform(post("/admin/backup/check").with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(redirectedUrl("/admin/backup"));
        mvc.perform(post("/admin/backup/check").with(user("elena").roles("USER")).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/admin/backup/check").with(user("admin").roles("ADMIN")))
                .andExpect(status().is3xxRedirection());
        verify(monitor, times(1)).check();
    }

    @Test
    void deletesADraftAndReportsAProblem() throws Exception {
        mvc.perform(post("/invoices/5/delete").with(user("elena").roles("USER")).with(csrf()))
                .andExpect(redirectedUrl("/invoices")).andExpect(flash().attribute("success", "Entwurf gelöscht."));
        verify(invoices).deleteDraft(5);
        doThrow(new InvoiceException(List.of("Eine ausgestellte Rechnung ändert sich nicht mehr."))).when(invoices).deleteDraft(6);
        mvc.perform(post("/invoices/6/delete").with(user("elena").roles("USER")).with(csrf()))
                .andExpect(flash().attribute("error", "Eine ausgestellte Rechnung ändert sich nicht mehr."));
    }

    @Test
    void showsTheTemplatesOfACustomerAndStartsADraftFromOne() throws Exception {
        when(customers.list()).thenReturn(List.of());
        when(customers.find(4)).thenReturn(Optional.of(CustomerTestData.vhs().withId(4L)));
        when(templates.list(4)).thenReturn(List.of());
        mvc.perform(get("/customers/4").with(user("elena").roles("USER")))
                .andExpect(content().string(Matchers.containsString("Noch keine Vorlage.")));
        when(templates.list(4)).thenReturn(List.of(template()));
        mvc.perform(get("/customers/4").with(user("elena").roles("USER")))
                .andExpect(content().string(Matchers.containsString("Lesung mit Gespräch")))
                .andExpect(content().string(Matchers.containsString("name=\"templateId\" value=\"7\"")))
                .andExpect(content().string(Matchers.containsString("Rechnung erstellen")));
        mvc.perform(get("/customers/new").with(user("elena").roles("USER")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("Neue Vorlage"))));

        when(invoices.createDraft(4L, 7L)).thenReturn(invoice(5, InvoiceStatus.DRAFT));
        mvc.perform(post("/invoices").param("customerId", "4").param("templateId", "7")
                        .with(user("elena").roles("USER")).with(csrf()))
                .andExpect(redirectedUrl("/invoices/5"));
    }

    @Test
    void createsEditsAndDeletesATemplate() throws Exception {
        when(templates.create(4)).thenReturn(template());
        mvc.perform(post("/customers/4/templates").with(user("elena").roles("USER")).with(csrf()))
                .andExpect(redirectedUrl("/customers/4/templates/7"));
        when(templates.create(9)).thenThrow(new InvoiceException(List.of("Diesen Kunden gibt es nicht.")));
        mvc.perform(post("/customers/9/templates").with(user("elena").roles("USER")).with(csrf()))
                .andExpect(redirectedUrl("/customers")).andExpect(flash().attribute("error", "Diesen Kunden gibt es nicht."));

        when(templates.find(4, 7)).thenReturn(Optional.of(template()));
        when(customers.find(4)).thenReturn(Optional.of(CustomerTestData.vhs().withId(4L)));
        mvc.perform(get("/customers/4/templates/7").with(user("elena").roles("USER"))).andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("value=\"Lesung mit Gespr&auml;ch\"")))
                .andExpect(content().string(Matchers.containsString("{Leistungszeitraum}")))
                .andExpect(content().string(Matchers.containsString("Volkshochschule Musterstadt")))
                .andExpect(content().string(Matchers.containsString("value=\"400,00\"")));
        when(customers.find(4)).thenReturn(Optional.empty());
        mvc.perform(get("/customers/4/templates/7").with(user("elena").roles("USER")))
                .andExpect(content().string(Matchers.containsString("← –")));
        when(templates.find(4, 8)).thenReturn(Optional.empty());
        mvc.perform(get("/customers/4/templates/8").with(user("elena").roles("USER"))).andExpect(status().isNotFound());

        when(invoices.defaultCategory()).thenReturn(TaxCategory.KLEINUNTERNEHMER);
        var saved = ArgumentCaptor.forClass(TemplateForm.class);
        mvc.perform(post("/customers/4/templates/7").param("action", "save").param("name", "Lesung")
                        .param("lines[0].text", "Lesung").with(user("elena").roles("USER")).with(csrf()))
                .andExpect(redirectedUrl("/customers/4/templates/7")).andExpect(flash().attribute("success", "Vorlage gespeichert."));
        mvc.perform(post("/customers/4/templates/7").param("action", "add-line").param("lines[0].text", "Lesung")
                        .with(user("elena").roles("USER")).with(csrf()))
                .andExpect(flash().attributeCount(0));
        mvc.perform(post("/customers/4/templates/7").param("remove", "0").param("lines[0].text", "Lesung")
                        .param("lines[1].text", "Reise").with(user("elena").roles("USER")).with(csrf()));
        mvc.perform(post("/customers/4/templates/7").param("remove", "5").param("lines[0].text", "Lesung")
                        .with(user("elena").roles("USER")).with(csrf()));
        mvc.perform(post("/customers/4/templates/7").param("remove", "-1").param("lines[0].text", "Lesung")
                        .with(user("elena").roles("USER")).with(csrf()));
        verify(templates, times(5)).save(eq(4L), eq(7L), saved.capture());
        assertThat(saved.getAllValues().get(1).getLines()).hasSize(2);
        assertThat(saved.getAllValues().get(1).getLines().get(1).getCategory()).isEqualTo(TaxCategory.KLEINUNTERNEHMER);
        assertThat(saved.getAllValues().get(2).getLines()).extracting(LineForm::getText)
                .containsExactly("Reise");
        assertThat(saved.getAllValues().get(3).getLines()).hasSize(1);
        assertThat(saved.getAllValues().get(4).getLines()).hasSize(1);

        when(templates.save(eq(4L), eq(7L), any())).thenThrow(new InvoiceException(List.of("Bitte der Vorlage einen Namen geben.")));
        mvc.perform(post("/customers/4/templates/7").param("action", "save").param("name", "")
                        .with(user("elena").roles("USER")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Bitte der Vorlage einen Namen geben.")));
        mvc.perform(post("/customers/4/templates/8").with(user("elena").roles("USER")).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(post("/customers/4/templates/7").param("action", "add-line").param("paymentDays", "")
                        .param("lines[0].text", "Lesung").with(user("elena").roles("USER")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Bitte gültige Daten eingeben")))
                .andExpect(content().string(Matchers.containsString("value=\"Lesung\"")));

        when(templates.delete(4, 7)).thenReturn(template());
        mvc.perform(post("/customers/4/templates/7/delete").with(user("elena").roles("USER")).with(csrf()))
                .andExpect(redirectedUrl("/customers/4"))
                .andExpect(flash().attribute("success", "Vorlage „Lesung mit Gespräch“ gelöscht."));
        when(templates.delete(4, 8)).thenThrow(new InvoiceException(List.of("Diese Vorlage gibt es nicht.")));
        mvc.perform(post("/customers/4/templates/8/delete").with(user("elena").roles("USER")).with(csrf()))
                .andExpect(flash().attribute("error", "Diese Vorlage gibt es nicht."));
    }

    @Test
    void resetsAPasswordAndReportsAProblem() throws Exception {
        when(users.resetPassword(anyLong(), eq("kurz"))).thenThrow(new UserException("Zu kurz."));
        mvc.perform(post("/admin/users/3/password").with(user("admin").roles("ADMIN")).with(csrf())
                        .param("password", "kurz").param("confirm", "kurz"))
                .andExpect(flash().attribute("error", "Zu kurz."));
        mvc.perform(post("/admin/users/3/password").with(user("admin").roles("ADMIN")).with(csrf())
                        .param("password", PASSWORD).param("confirm", "anders"))
                .andExpect(flash().attribute("error", "Die beiden Passwörter stimmen nicht überein."));
        Assertions.assertThat(sessionEpoch.ended("otto", 0)).as("a failed reset ends no session").isFalse();
        when(users.resetPassword(3, PASSWORD)).thenReturn(UserTestData.user(3, "otto", Role.USER));
        mvc.perform(post("/admin/users/3/password").with(user("admin").roles("ADMIN")).with(csrf())
                        .param("password", PASSWORD).param("confirm", PASSWORD))
                .andExpect(flash().attribute("success", "Neues Passwort für „otto“ gesetzt."))
                .andExpect(request().sessionAttribute(SessionEpoch.KEPT, Matchers.nullValue()));
        Assertions.assertThat(sessionEpoch.ended("otto", 0)).as("every session of otto ends").isTrue();
        Assertions.assertThat(sessionEpoch.ended("admin", 0)).isFalse();
        when(users.resetPassword(1, PASSWORD)).thenReturn(UserTestData.user(1, "admin", Role.ADMIN));
        mvc.perform(post("/admin/users/1/password").with(user("admin").roles("ADMIN")).with(csrf())
                        .param("password", PASSWORD).param("confirm", PASSWORD))
                .andExpect(request().sessionAttribute(SessionEpoch.KEPT, Matchers.notNullValue()));
        Assertions.assertThat(sessionEpoch.ended("admin", 0)).as("the other sessions of the admin end").isTrue();
    }

    /** A multipart request as a browser sends it, with its length: the upload limit refuses one without. */
    private static MockMultipartHttpServletRequestBuilder upload(String path) {
        MockMultipartHttpServletRequestBuilder builder = MockMvcRequestBuilders.multipart(path);
        builder.with(request -> {
            request.setContent(new byte[1]);
            return request;
        });
        return builder;
    }

    private static BusinessDocument businessDocument(long id, Long replacesId, Long replacedBy) {
        return new BusinessDocument(id, DocumentCategory.TAX_ASSESSMENT, "Einkommensteuerbescheid 2026",
                LocalDate.of(2027, 5, 10), 2026, "Ohne Nachzahlung", replacesId, replacedBy, "Bescheid-" + id + ".pdf",
                "application/pdf", "ab".repeat(32), 2048, Instant.parse("2027-05-12T08:00:00Z"), "elena");
    }

    @Test
    void listsTheCurrentDocumentsOfAKind() throws Exception {
        when(businessDocuments.list()).thenReturn(List.of());
        mvc.perform(get("/documents").with(user("elena").roles("USER"))).andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Noch keine Dokumente.")))
                .andExpect(content().string(Matchers.containsString("Dokument ablegen")));
        when(businessDocuments.list()).thenReturn(List.of(businessDocument(2, 1L, null)));
        mvc.perform(get("/documents").with(user("elena").roles("USER")))
                .andExpect(content().string(Matchers.containsString("Einkommensteuerbescheid 2026")))
                .andExpect(content().string(Matchers.containsString("Steuerbescheid · 10.05.2027 · für 2026 · neue Fassung")));
        mvc.perform(get("/documents").param("category", "CONTRACT").with(user("elena").roles("USER")))
                .andExpect(content().string(Matchers.containsString("Keine Dokumente dieser Art.")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("Einkommensteuerbescheid 2026"))));
        mvc.perform(get("/documents").param("category", "TAX_ASSESSMENT").with(user("elena").roles("USER")))
                .andExpect(content().string(Matchers.containsString("Einkommensteuerbescheid 2026")));
    }

    @Test
    void storesANewDocumentAndShowsItsProblems() throws Exception {
        mvc.perform(get("/documents/new").with(user("elena").roles("USER"))).andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Neues Dokument")))
                .andExpect(content().string(Matchers.containsString("enctype=\"multipart/form-data\"")))
                .andExpect(content().string(Matchers.containsString("Datum des Dokuments")));
        MockMultipartFile file = new MockMultipartFile("file", "Bescheid.pdf", "application/pdf", new byte[] {'%'});
        when(businessDocuments.add(any(), eq("Bescheid.pdf"), eq(new byte[] {'%'})))
                .thenReturn(businessDocument(1, null, null));
        mvc.perform(upload("/documents").file(file).param("category", "TAX_ASSESSMENT")
                        .param("title", "Einkommensteuerbescheid 2026").param("documentDate", "2027-05-10")
                        .with(user("elena").roles("USER")).with(csrf()))
                .andExpect(redirectedUrl("/documents/1"))
                .andExpect(flash().attribute("success", "Dokument „Einkommensteuerbescheid 2026“ abgelegt."));
        ArgumentCaptor<DocumentForm> form = ArgumentCaptor.forClass(DocumentForm.class);
        verify(businessDocuments).add(form.capture(), eq("Bescheid.pdf"), any());
        Assertions.assertThat(form.getValue().getDocumentDate()).isEqualTo(LocalDate.of(2027, 5, 10));
        when(businessDocuments.add(any(), ArgumentMatchers.isNull(), eq(new byte[0])))
                .thenThrow(new DocumentException(List.of("Bitte eine Datei wählen.")));
        mvc.perform(upload("/documents").param("title", "Ohne Datei")
                        .with(user("elena").roles("USER")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Bitte eine Datei wählen.")))
                .andExpect(content().string(Matchers.containsString("value=\"Ohne Datei\"")));
        mvc.perform(upload("/documents").file(new MockMultipartFile("file", "Riesig.pdf",
                        "application/pdf", new byte[DocumentService.MAX_BYTES + 1])).param("title", "Riesig")
                        .with(user("elena").roles("USER")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Die Datei ist größer als 25 MB.")));
        verify(businessDocuments, never()).add(any(), eq("Riesig.pdf"), any());
    }

    @Test
    void showsADocumentWithItsVersions() throws Exception {
        BusinessDocument current = businessDocument(2, 1L, null);
        BusinessDocument old = businessDocument(1, null, 2L);
        when(businessDocuments.find(2)).thenReturn(Optional.of(current));
        when(businessDocuments.find(1)).thenReturn(Optional.of(old));
        when(businessDocuments.versions(2)).thenReturn(List.of(current, old));
        when(businessDocuments.versions(1)).thenReturn(List.of(current, old));
        mvc.perform(get("/documents/2").with(user("elena").roles("USER"))).andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Neue Fassung ablegen")))
                .andExpect(content().string(Matchers.containsString("Herunterladen")))
                .andExpect(content().string(Matchers.containsString("2 KB")))
                .andExpect(content().string(Matchers.containsString("12.05.2027, 10:00 · elena")))
                .andExpect(content().string(Matchers.containsString("Fassungen")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("Eine neuere Fassung ersetzt"))));
        mvc.perform(get("/documents/1").with(user("elena").roles("USER"))).andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Eine neuere Fassung ersetzt dieses Dokument.")))
                .andExpect(content().string(Matchers.containsString("href=\"/documents/2\"")))
                .andExpect(content().string(Matchers.containsString("<fieldset class=\"grid gap-4\" disabled=\"disabled\">")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("Neue Fassung ablegen"))));
        when(businessDocuments.find(3)).thenReturn(Optional.of(new BusinessDocument(3, DocumentCategory.OTHER, "Groß",
                LocalDate.of(2027, 5, 10), null, "", null, null, "scan.png", "image/png", "cd".repeat(32), 3_250_000,
                Instant.EPOCH, "elena")));
        when(businessDocuments.versions(3)).thenReturn(List.of());
        mvc.perform(get("/documents/3").with(user("elena").roles("USER")))
                .andExpect(content().string(Matchers.containsString("3,1 MB")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("Fassungen"))));
        mvc.perform(get("/documents/9").with(user("elena").roles("USER"))).andExpect(status().isNotFound());
    }

    @Test
    void changesADocumentAndStoresANewVersion() throws Exception {
        BusinessDocument current = businessDocument(2, null, null);
        when(businessDocuments.find(2)).thenReturn(Optional.of(current));
        when(businessDocuments.versions(2)).thenReturn(List.of(current));
        mvc.perform(post("/documents/2").param("category", "TAX_ASSESSMENT").param("title", "Neu")
                        .param("documentDate", "2027-05-10").with(user("elena").roles("USER")).with(csrf()))
                .andExpect(redirectedUrl("/documents/2")).andExpect(flash().attribute("success", "Änderungen gespeichert."));
        verify(businessDocuments).change(eq(2L), any());
        when(businessDocuments.change(eq(2L), any())).thenThrow(new DocumentException(List.of("Bitte Titel angeben.")));
        mvc.perform(post("/documents/2").param("title", "").with(user("elena").roles("USER")).with(csrf()))
                .andExpect(status().isOk()).andExpect(content().string(Matchers.containsString("Bitte Titel angeben.")));
        MockMultipartFile file = new MockMultipartFile("file", "Neu.pdf", "application/pdf", new byte[] {'%'});
        when(businessDocuments.replace(eq(2L), any(), eq("Neu.pdf"), any())).thenReturn(businessDocument(3, 2L, null));
        mvc.perform(upload("/documents/2/versions").file(file)
                        .with(user("elena").roles("USER")).with(csrf()))
                .andExpect(redirectedUrl("/documents/3")).andExpect(flash().attribute("success", "Neue Fassung abgelegt."));
        when(businessDocuments.replace(eq(2L), any(), ArgumentMatchers.isNull(), any()))
                .thenThrow(new DocumentException(List.of("Bitte eine Datei wählen.")));
        mvc.perform(upload("/documents/2/versions").with(user("elena").roles("USER")).with(csrf()))
                .andExpect(status().isOk()).andExpect(content().string(Matchers.containsString("Bitte eine Datei wählen.")));
        mvc.perform(post("/documents/9").param("title", "x").with(user("elena").roles("USER")).with(csrf()))
                .andExpect(redirectedUrl("/documents/9"));
    }

    @Test
    void downloadsTheStoredFile() throws Exception {
        when(businessDocuments.file(2)).thenReturn(Optional.of(new DocumentFile("Bescheid 2026 geändert.pdf",
                "application/pdf", new byte[] {'%', 'P'})));
        mvc.perform(get("/documents/2/file").with(user("elena").roles("USER"))).andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", Matchers.containsString(
                        "filename*=UTF-8''Bescheid%202026%20ge%C3%A4ndert.pdf")))
                .andExpect(content().bytes(new byte[] {'%', 'P'}));
        mvc.perform(get("/documents/9/file").with(user("elena").roles("USER"))).andExpect(status().isNotFound());
        mvc.perform(get("/documents")).andExpect(status().is3xxRedirection());
        when(businessDocuments.file(3)).thenThrow(new DocumentException(List.of("kaputt")));
        mvc.perform(get("/documents/3/file").with(user("elena").roles("USER"))).andExpect(status().isSeeOther())
                .andExpect(redirectedUrl("/documents/3"));
    }

    @Test
    void showsAChangedDocument() throws Exception {
        when(businessDocuments.find(2)).thenReturn(Optional.of(businessDocument(2, null, null)));
        mvc.perform(get("/documents/2").with(user("elena").roles("USER")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("document-integrity"))));
        when(businessDocuments.problems(2)).thenReturn(List.of("Bescheid.pdf: Das Siegel passt nicht."));
        mvc.perform(get("/documents/2").with(user("elena").roles("USER")))
                .andExpect(content().string(Matchers.containsString("id=\"document-integrity\"")))
                .andExpect(content().string(Matchers.containsString("Bescheid.pdf: Das Siegel passt nicht.")));
    }

    @Test
    void writesTheVerfahrensdokumentation() throws Exception {
        ProcedureForm form = new ProcedureForm();
        form.setChange("Erstfassung");
        when(procedure.form()).thenReturn(form);
        mvc.perform(get("/documents/procedure").with(user("elena").roles("USER"))).andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("value=\"Erstfassung\"")))
                .andExpect(content().string(Matchers.containsString("Vorschau (PDF)")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("Aktuelle Fassung"))));
        when(procedure.current()).thenReturn(Optional.of(businessDocument(5, null, null)));
        mvc.perform(get("/documents/procedure").with(user("elena").roles("USER")))
                .andExpect(content().string(Matchers.containsString("Aktuelle Fassung")))
                .andExpect(content().string(Matchers.containsString("href=\"/documents/5\"")));
        when(procedure.save(any())).thenReturn(businessDocument(6, 5L, null));
        mvc.perform(post("/documents/procedure").param("validFrom", "2027-01-01").param("expenses", "WISO Steuern")
                        .param("archive", "in einem Dropbox-Ordner").param("change", "Erstfassung")
                        .with(user("elena").roles("USER")).with(csrf()))
                .andExpect(redirectedUrl("/documents/6"))
                .andExpect(flash().attribute("success", Matchers.startsWith("Verfahrensdokumentation als neue Fassung")));
        ArgumentCaptor<ProcedureForm> saved = ArgumentCaptor.forClass(ProcedureForm.class);
        verify(procedure).save(saved.capture());
        Assertions.assertThat(saved.getValue().getValidFrom()).isEqualTo(LocalDate.of(2027, 1, 1));
        Assertions.assertThat(saved.getValue().getExpenses()).isEqualTo("WISO Steuern");
        when(procedure.save(any())).thenThrow(new DocumentException(List.of("Bitte die Änderung angeben.")));
        mvc.perform(post("/documents/procedure").param("expenses", "WISO").with(user("elena").roles("USER")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Bitte die Änderung angeben.")))
                .andExpect(content().string(Matchers.containsString("value=\"WISO\"")));
    }

    @Test
    void previewsTheVerfahrensdokumentationAsAPdf() throws Exception {
        when(procedure.preview(any())).thenReturn(new byte[] {'%', 'P'});
        mvc.perform(post("/documents/procedure/preview").param("change", "Erstfassung")
                        .with(user("elena").roles("USER")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"Verfahrensdokumentation_Entwurf.pdf\""))
                .andExpect(content().bytes(new byte[] {'%', 'P'}));
        when(procedure.preview(any())).thenThrow(new DocumentException(List.of(NO_PROFILE)));
        mvc.perform(post("/documents/procedure/preview").with(user("elena").roles("USER")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString(NO_PROFILE)));
    }
}
