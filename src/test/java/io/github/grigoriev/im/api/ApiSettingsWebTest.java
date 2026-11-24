package io.github.grigoriev.im.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.grigoriev.im.customer.CustomerService;
import io.github.grigoriev.im.customer.CustomerTestData;
import io.github.grigoriev.im.einvoice.InvoiceFormat;
import io.github.grigoriev.im.einvoice.TaxCategory;
import io.github.grigoriev.im.invoice.InvoiceException;
import io.github.grigoriev.im.invoice.InvoiceTemplate;
import io.github.grigoriev.im.invoice.TemplateLine;
import io.github.grigoriev.im.invoice.TemplateService;
import io.github.grigoriev.im.mail.MailText;
import io.github.grigoriev.im.mail.MailTextService;
import io.github.grigoriev.im.numbering.NumberPattern;
import io.github.grigoriev.im.numbering.NumberSequence;
import io.github.grigoriev.im.numbering.NumberService;
import io.github.grigoriev.im.seller.ProfilePart;
import io.github.grigoriev.im.seller.SellerService;
import io.github.grigoriev.im.seller.SellerTestData;
import io.github.grigoriev.im.seller.SettingsException;
import io.github.grigoriev.im.seller.TaxNoteService;
import io.github.grigoriev.im.user.Role;
import io.github.grigoriev.im.user.UserTestData;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** The settings and templates in the API with their access rules, the services mocked. */
@WebMvcTest(controllers = {ApiSettingsController.class, ApiTemplateController.class},
        excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX, pattern = "io\\.github\\.grigoriev\\.im\\.web\\..*"))
@Import({ApiSecurityConfig.class, ApiExceptionHandler.class})
class ApiSettingsWebTest {

    private static final Instant AT = Instant.parse("2027-03-18T08:12:00Z");
    private static final String USER_WRITES = "Bearer im_nutzer";
    private static final String ADMIN_READS = "Bearer im_admin_liest";
    private static final String ADMIN_WRITES = "Bearer im_admin";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ApiTokenService tokens;

    @MockitoBean
    private SellerService sellers;

    @MockitoBean
    private MailTextService mailTexts;

    @MockitoBean
    private TaxNoteService taxNotes;

    @MockitoBean
    private NumberService numbers;

    @MockitoBean
    private TemplateService templates;

    @MockitoBean
    private CustomerService customers;

    private static ApiTokenLogin login(Role role, ApiTokenScope scope) {
        return new ApiTokenLogin(UserTestData.user(2L, "elena", role),
                new ApiToken(7L, 2L, "Claude", "h", scope, AT, AT.plusSeconds(60), null));
    }

    private static InvoiceTemplate template() {
        return new InvoiceTemplate(7L, 4L, "Lesung", InvoiceFormat.ZUGFERD_EN16931, 14, "Hallo", "Danke", null,
                "Rechnung", "Anbei", List.of(new TemplateLine("Lesung", true, BigDecimal.ONE, new BigDecimal("400"),
                        TaxCategory.KLEINUNTERNEHMER)), AT, AT, 3L, null, null, null);
    }

    @BeforeEach
    void knownTokens() {
        when(tokens.authenticate("im_nutzer")).thenReturn(Optional.of(login(Role.USER, ApiTokenScope.WRITE)));
        when(tokens.authenticate("im_admin_liest")).thenReturn(Optional.of(login(Role.ADMIN, ApiTokenScope.READ)));
        when(tokens.authenticate("im_admin")).thenReturn(Optional.of(login(Role.ADMIN, ApiTokenScope.WRITE)));
    }

    @Test
    void readsAndSavesTheProfileByTab() throws Exception {
        mvc.perform(get("/api/v1/profile").header("Authorization", USER_WRITES)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Noch kein Profil."));
        when(sellers.find()).thenReturn(Optional.of(SellerTestData.profile()));
        mvc.perform(get("/api/v1/profile").header("Authorization", USER_WRITES)).andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Erika Mustermann"));
        when(sellers.save(eq(ProfilePart.BANK), any())).thenReturn(SellerTestData.profile());
        mvc.perform(put("/api/v1/profile/bank").header("Authorization", USER_WRITES)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"iban\": \"DE89370400440532013000\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.iban").value(SellerTestData.IBAN));
        mvc.perform(put("/api/v1/profile/foto").header("Authorization", USER_WRITES)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
        when(sellers.save(eq(ProfilePart.TAX), any())).thenThrow(new SettingsException(
                Map.of("taxNumber", "Bitte die Steuernummer prüfen.")));
        mvc.perform(put("/api/v1/profile/tax").header("Authorization", USER_WRITES)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.problems[0]").value("Bitte die Steuernummer prüfen."))
                .andExpect(jsonPath("$.fields.taxNumber").value("Bitte die Steuernummer prüfen."));
    }

    @Test
    void readsAndSavesTheMailTexts() throws Exception {
        when(mailTexts.defaults()).thenReturn(new MailText("Rechnung", "Anbei"));
        when(mailTexts.reminderDefaults()).thenReturn(new MailText("Erinnerung", "Offen"));
        mvc.perform(get("/api/v1/mail-texts").header("Authorization", USER_WRITES)).andExpect(status().isOk())
                .andExpect(jsonPath("$.invoice.subject").value("Rechnung"))
                .andExpect(jsonPath("$.reminder.body").value("Offen"));
        mvc.perform(put("/api/v1/mail-texts/invoice").header("Authorization", USER_WRITES)
                .contentType(MediaType.APPLICATION_JSON).content("{\"subject\": \"S\", \"body\": \"B\"}"))
                .andExpect(status().isOk());
        verify(mailTexts).save("S", "B");
        mvc.perform(put("/api/v1/mail-texts/reminder").header("Authorization", USER_WRITES)
                .contentType(MediaType.APPLICATION_JSON).content("{\"subject\": \"E\", \"body\": \"O\"}"))
                .andExpect(status().isOk());
        verify(mailTexts).saveReminder("E", "O");
        when(mailTexts.defaults()).thenThrow(new SettingsException(List.of("Zu lang.")));
        mvc.perform(get("/api/v1/mail-texts").header("Authorization", USER_WRITES)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields").doesNotExist());
    }

    @Test
    void theTaxNotesAndTheNumberPatternNeedTheAdmin() throws Exception {
        Map<TaxCategory, String> notes = new EnumMap<>(TaxCategory.class);
        notes.put(TaxCategory.KLEINUNTERNEHMER, "§ 19");
        when(taxNotes.notes()).thenReturn(notes);
        mvc.perform(get("/api/v1/admin/tax-notes").header("Authorization", USER_WRITES)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/tax-notes").header("Authorization", ADMIN_READS)).andExpect(status().isOk())
                .andExpect(jsonPath("$.KLEINUNTERNEHMER").value("§ 19"));
        mvc.perform(head("/api/v1/admin/tax-notes").header("Authorization", ADMIN_READS)).andExpect(status().isOk());
        String change = "{\"KLEINUNTERNEHMER\": \"Neu\"}";
        mvc.perform(put("/api/v1/admin/tax-notes").header("Authorization", ADMIN_READS)
                .contentType(MediaType.APPLICATION_JSON).content(change)).andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/admin/tax-notes").header("Authorization", USER_WRITES)
                .contentType(MediaType.APPLICATION_JSON).content(change)).andExpect(status().isForbidden());
        verify(taxNotes, never()).save(any());
        mvc.perform(put("/api/v1/admin/tax-notes").header("Authorization", ADMIN_WRITES)
                .contentType(MediaType.APPLICATION_JSON).content(change)).andExpect(status().isOk());
        verify(taxNotes).save(Map.of(TaxCategory.KLEINUNTERNEHMER, "Neu"));

        when(numbers.pattern()).thenReturn(new NumberPattern("{YYYY}-{NN}"));
        when(numbers.years()).thenReturn(List.of(new NumberSequence(2027, 5, true), new NumberSequence(2028, 1, false)));
        when(numbers.preview(new NumberSequence(2027, 5, true))).thenReturn("2027-05");
        when(numbers.preview(new NumberSequence(2028, 1, false))).thenReturn("2028-01");
        mvc.perform(get("/api/v1/admin/number-pattern").header("Authorization", USER_WRITES))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/number-pattern").header("Authorization", ADMIN_READS)).andExpect(status().isOk())
                .andExpect(jsonPath("$.pattern").value("{YYYY}-{NN}"))
                .andExpect(jsonPath("$.next.2027").value("2027-05"))
                .andExpect(jsonPath("$.next.2028").value("2028-01"));
        mvc.perform(put("/api/v1/admin/number-pattern").header("Authorization", ADMIN_WRITES)
                .contentType(MediaType.APPLICATION_JSON).content("{\"pattern\": \"RE-{YYYY}-{NNN}\"}"))
                .andExpect(status().isOk());
        verify(numbers).savePattern("RE-{YYYY}-{NNN}");
    }

    @Test
    void listsCreatesReadsAndSavesTemplates() throws Exception {
        mvc.perform(get("/api/v1/customers/4/templates").header("Authorization", USER_WRITES))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.detail").value("Diesen Kunden gibt es nicht."));
        when(customers.find(4L)).thenReturn(Optional.of(CustomerTestData.vhs().withId(4L)));
        when(templates.list(4L)).thenReturn(List.of(template()));
        mvc.perform(get("/api/v1/customers/4/templates").header("Authorization", USER_WRITES)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(7)).andExpect(jsonPath("$[0].name").value("Lesung"));
        when(templates.create(4L)).thenReturn(template());
        mvc.perform(post("/api/v1/customers/4/templates").header("Authorization", USER_WRITES))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/customers/4/templates/7"));
        mvc.perform(get("/api/v1/customers/4/templates/8").header("Authorization", USER_WRITES))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.detail").value("Diese Vorlage gibt es nicht."));
        when(templates.find(4L, 7L)).thenReturn(Optional.of(template()));
        mvc.perform(get("/api/v1/customers/4/templates/7").header("Authorization", USER_WRITES))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(3))
                .andExpect(jsonPath("$.lines[0].text").value("Lesung"))
                .andExpect(jsonPath("$.lines[0].amount").value("400,00"));
        when(templates.save(eq(4L), eq(7L), any())).thenReturn(template());
        mvc.perform(put("/api/v1/customers/4/templates/7").header("Authorization", USER_WRITES)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\": 3, \"name\": \"Lesung\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Lesung"));
        when(templates.save(eq(4L), eq(8L), any())).thenThrow(new InvoiceException(List.of("Diese Vorlage gibt es nicht.")));
        mvc.perform(put("/api/v1/customers/4/templates/8").header("Authorization", USER_WRITES)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.problems[0]").value("Diese Vorlage gibt es nicht."));
    }
}
