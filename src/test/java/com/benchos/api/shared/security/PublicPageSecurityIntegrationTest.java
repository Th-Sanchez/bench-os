package com.benchos.api.shared.security;

import com.benchos.api.TestcontainersConfiguration;
import com.benchos.api.order.internal.config.PublicTrackingProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PublicPageSecurityIntegrationTest {

    private static final Pattern SCRIPT_NONCE = Pattern.compile("script-src 'nonce-([A-Za-z0-9+/=]+)'");

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    void hardensTrackingAndReceiptPagesWithNonceCspNoReferrerNoIndexNoStoreAndHstsOnHttps() throws Exception {
        String token = UUID.randomUUID().toString();

        MvcResult trackFirst = mockMvc.perform(get("/track/{token}", token).secure(true))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("X-Robots-Tag", "noindex, nofollow, noarchive"))
                .andExpect(header().string("Permissions-Policy", "camera=(), microphone=(), geolocation=(), payment=()"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(header().string("Strict-Transport-Security", "max-age=31536000 ; includeSubDomains"))
                .andReturn();

        String csp1 = trackFirst.getResponse().getHeader("Content-Security-Policy");
        String body1 = trackFirst.getResponse().getContentAsString();
        assertThat(csp1)
                .isNotNull()
                .contains("default-src 'none'")
                .contains("frame-ancestors 'none'")
                .contains("object-src 'none'")
                .contains("base-uri 'none'")
                .contains("upgrade-insecure-requests")
                .contains("img-src 'self' data: http://localhost:9000")
                .doesNotContain("script-src 'unsafe-inline'");

        String nonce1 = extractNonce(csp1);
        assertThat(body1)
                .contains("<script nonce=\"" + nonce1 + "\">")
                .doesNotContain("__CSP_NONCE__")
                .contains("<meta name=\"referrer\" content=\"no-referrer\" />")
                .contains("<meta name=\"robots\" content=\"noindex, nofollow, noarchive\" />");

        // Second request must produce a brand-new cryptographic nonce
        MvcResult trackSecond = mockMvc.perform(get("/track/{token}/receipt", token).secure(true))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Robots-Tag", "noindex, nofollow, noarchive"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("Strict-Transport-Security", "max-age=31536000 ; includeSubDomains"))
                .andReturn();

        String csp2 = trackSecond.getResponse().getHeader("Content-Security-Policy");
        String body2 = trackSecond.getResponse().getContentAsString();
        String nonce2 = extractNonce(csp2);
        assertThat(nonce2).isNotEqualTo(nonce1);
        assertThat(body2)
                .contains("<script nonce=\"" + nonce2 + "\">")
                .doesNotContain("__CSP_NONCE__");
    }

    @Test
    void rejectsMalformedPublicTokensWithoutReflectingPathAndWithScriptNoneCsp() throws Exception {
        MvcResult result = mockMvc.perform(get("/track/{token}", "invalid-token-value"))
                .andExpect(status().isNotFound())
                .andExpect(header().string("X-Robots-Tag", "noindex, nofollow, noarchive"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, org.hamcrest.Matchers.containsString("no-store")))
                .andReturn();

        assertThat(result.getResponse().getHeader("Content-Security-Policy"))
                .contains("script-src 'none'");
        assertThat(result.getResponse().getContentAsString())
                .doesNotContain("invalid-token-value");
    }

    @Test
    void publicTrackingPropertiesRequiresHttpsWhenConfigured() {
        assertThatThrownBy(() -> new PublicTrackingProperties("http://api.benchos.app.br/track", true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("https://");

        PublicTrackingProperties valid = new PublicTrackingProperties("https://api.benchos.app.br/track/", true);
        assertThat(valid.baseUrl()).isEqualTo("https://api.benchos.app.br/track");
        assertThat(valid.isRequireHttps()).isTrue();
    }

    @Nested
    @SpringBootTest
    @Import(TestcontainersConfiguration.class)
    @TestPropertySource(properties = {
            "benchos.security.transport.require-https=true",
            "benchos.public-tracking.base-url=https://api.benchos.app.br/track",
            "benchos.public-tracking.require-https=true",
            "springdoc.api-docs.enabled=false",
            "springdoc.swagger-ui.enabled=false"
    })
    class ProductionTransportHardeningTest {

        @Autowired
        private WebApplicationContext prodContext;

        private MockMvc prodMockMvc;

        @BeforeEach
        void setUp() {
            this.prodMockMvc = MockMvcBuilders.webAppContextSetup(prodContext)
                    .apply(springSecurity())
                    .build();
        }

        @Test
        void redirectsPlainHttpToHttpsWhileKeepingHealthProbeAccessibleAndBlockingSwagger() throws Exception {
            String token = UUID.randomUUID().toString();

            // Plain HTTP to public tracking page is redirected to HTTPS
            prodMockMvc.perform(get("http://api.benchos.app.br/track/{token}", token))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(header().string(HttpHeaders.LOCATION, "https://api.benchos.app.br/track/" + token));

            // HTTPS request succeeds with HSTS
            prodMockMvc.perform(get("https://api.benchos.app.br/track/{token}", token))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Strict-Transport-Security", "max-age=31536000 ; includeSubDomains"));

            // Container readiness probe over internal HTTP remains 200 OK
            prodMockMvc.perform(get("http://localhost:8080/actuator/health/readiness"))
                    .andExpect(status().isOk());

            // Disabled Swagger/OpenAPI routes are no longer permitAll (require auth -> 401 over HTTPS)
            prodMockMvc.perform(get("https://api.benchos.app.br/v3/api-docs"))
                    .andExpect(status().isUnauthorized());
            prodMockMvc.perform(get("https://api.benchos.app.br/swagger-ui.html"))
                    .andExpect(status().isUnauthorized());
        }
    }

    private static String extractNonce(String cspHeader) {
        assertThat(cspHeader).isNotNull();
        Matcher matcher = SCRIPT_NONCE.matcher(cspHeader);
        assertThat(matcher.find()).as("Expected script-src nonce in CSP header: %s", cspHeader).isTrue();
        return matcher.group(1);
    }
}
