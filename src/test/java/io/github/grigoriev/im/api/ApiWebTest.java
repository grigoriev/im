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

import io.github.grigoriev.im.customer.Customer;
import io.github.grigoriev.im.customer.CustomerException;
import io.github.grigoriev.im.customer.CustomerService;
import io.github.grigoriev.im.customer.CustomerTestData;
import io.github.grigoriev.im.user.Role;
import io.github.grigoriev.im.user.UserTestData;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.List;
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

/** The customer API with its access rules and answers, the services mocked. */
@WebMvcTest(controllers = ApiCustomerController.class, excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX,
        pattern = "io\\.github\\.grigoriev\\.im\\.web\\..*"))
@Import({ApiSecurityConfig.class, ApiExceptionHandler.class})
class ApiWebTest {

    private static final Instant AT = Instant.parse("2027-03-18T08:12:00Z");
    private static final String READER = "Bearer im_leser";
    private static final String WRITER = "Bearer im_schreiber";
    private static final String BODY = """
            {"name": "Volkshochschule Musterstadt", "street": "Am Markt 1", "postcode": "12345", "city": "Musterstadt",
             "countryCode": "DE", "format": "ZUGFERD_EN16931"}
            """;

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ApiTokenService tokens;

    @MockitoBean
    private CustomerService customers;

    private static ApiTokenLogin login(ApiTokenScope scope) {
        return new ApiTokenLogin(UserTestData.user(2L, "elena", Role.USER),
                new ApiToken(7L, 2L, "Claude", "h", scope, AT, AT.plusSeconds(60), null));
    }

    @BeforeEach
    void knownTokens() {
        when(tokens.authenticate("im_leser")).thenReturn(Optional.of(login(ApiTokenScope.READ)));
        when(tokens.authenticate("im_schreiber")).thenReturn(Optional.of(login(ApiTokenScope.WRITE)));
    }

    @Test
    void listsAndReadsCustomers() throws Exception {
        Customer vhs = CustomerTestData.vhs().withId(4L);
        when(customers.list()).thenReturn(List.of(vhs));
        when(customers.find(4L)).thenReturn(Optional.of(vhs));
        mvc.perform(get("/api/v1/customers").header("Authorization", READER)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(4)).andExpect(jsonPath("$[0].name").value(vhs.name()));
        mvc.perform(get("/api/v1/customers/4").header("Authorization", READER)).andExpect(status().isOk())
                .andExpect(jsonPath("$.city").value(vhs.city()));
        mvc.perform(head("/api/v1/customers/4").header("Authorization", READER)).andExpect(status().isOk());
        // A cookie does not hinder a read.
        mvc.perform(get("/api/v1/customers/4").header("Authorization", READER).cookie(new Cookie("JSESSIONID", "x")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/customers/5").header("Authorization", READER)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Diesen Kunden gibt es nicht."));
    }

    @Test
    void createsAndChangesCustomersWithAWritingTokenOnly() throws Exception {
        Customer saved = CustomerTestData.vhs().withId(9L);
        when(customers.create(any())).thenReturn(saved);
        mvc.perform(post("/api/v1/customers").header("Authorization", READER).contentType(MediaType.APPLICATION_JSON)
                .content(BODY)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Nicht erlaubt: Der API-Token darf nur lesen, oder eine Anfrage mit Cookies hat kein CSRF-Token."));
        // A browser with cookies needs the CSRF token for a change.
        mvc.perform(post("/api/v1/customers").header("Authorization", WRITER).cookie(new Cookie("JSESSIONID", "x"))
                .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/customers").header("Authorization", WRITER).contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isCreated()).andExpect(header().string("Location", "/api/v1/customers/9"))
                .andExpect(jsonPath("$.id").value(9));

        when(customers.find(9L)).thenReturn(Optional.of(saved));
        when(customers.update(eq(9L), any())).thenReturn(saved);
        mvc.perform(put("/api/v1/customers/9").header("Authorization", WRITER).contentType(MediaType.APPLICATION_JSON)
                .content(BODY)).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(9));
        mvc.perform(put("/api/v1/customers/8").header("Authorization", WRITER).contentType(MediaType.APPLICATION_JSON)
                .content(BODY)).andExpect(status().isNotFound());
        verify(customers, never()).update(eq(8L), any());
    }

    @Test
    void answersEveryProblemOfARefusedCustomer() throws Exception {
        when(customers.create(any())).thenThrow(new CustomerException(List.of("Name fehlt.", "Ort fehlt.")));
        mvc.perform(post("/api/v1/customers").header("Authorization", WRITER).contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Name fehlt. Ort fehlt."))
                .andExpect(jsonPath("$.problems[0]").value("Name fehlt."))
                .andExpect(jsonPath("$.problems[1]").value("Ort fehlt."));
    }

    @Test
    void refusesARequestWithoutAValidToken() throws Exception {
        mvc.perform(get("/api/v1/customers")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Bitte einen gültigen API-Token senden."));
        mvc.perform(get("/api/v1/customers").header("Authorization", "Bearer im_unbekannt"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/customers").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
        verify(customers, never()).create(any());
    }
}
