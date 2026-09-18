package com.clearing.netting.adapter.in.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Full-stack acceptance test over real HTTP + JPA (H2): seeding-like flow,
 * multi-currency netting, missing-rate failure, and the acceptance criterion
 * "editing one rate immediately changes the report numbers".
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(locations = "classpath:application-test.properties")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FxReportIntegrationTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String D = "2026-09-17";

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate rest;

    private String base() {
        return "http://localhost:" + port + "/api";
    }

    private HttpHeaders auth(String token) {
        HttpHeaders h = new HttpHeaders();
        h.set("Authorization", "Bearer " + token);
        return h;
    }

    private String login(String user, String pass) throws Exception {
        HttpHeaders h = new HttpHeaders();
        h.set("Content-Type", "application/json");
        ResponseEntity<String> resp = rest.exchange(base() + "/auth/login", HttpMethod.POST,
                new HttpEntity<>("{\"username\":\"" + user + "\",\"password\":\"" + pass + "\"}", h),
                String.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode(), resp.getBody());
        return JSON.readTree(resp.getBody()).get("token").asText();
    }

    private JsonNode post(String path, String body, String token) throws Exception {
        HttpHeaders h = auth(token);
        h.set("Content-Type", "application/json");
        ResponseEntity<String> resp = rest.exchange(base() + path, HttpMethod.POST,
                new HttpEntity<>(body, h), String.class);
        assertTrue(resp.getStatusCode().is2xxSuccessful(),
                () -> path + " -> " + resp.getStatusCode() + " " + resp.getBody());
        return JSON.readTree(resp.getBody());
    }

    @Test
    @Order(1)
    void endToEndMissingRateFailsThenReportFollowsRateEdits() throws Exception {
        String operator = login("operator", "op123456");

        JsonNode m1 = post("/members", "{\"name\":\"Alpha Bank\"}", operator);
        JsonNode m2 = post("/members", "{\"name\":\"Beta Securities\"}", operator);
        JsonNode m3 = post("/members", "{\"name\":\"Gamma Clearing\"}", operator);
        String a = m1.get("memberId").asText();
        String b = m2.get("memberId").asText();
        String c = m3.get("memberId").asText();

        obligation(operator, a, b, "USD", "100000");
        obligation(operator, b, c, "USD", "60000");
        obligation(operator, c, a, "USD", "40000");
        obligation(operator, a, c, "USD", "25000");

        obligation(operator, a, b, "EUR", "80000");
        obligation(operator, b, c, "EUR", "50000");
        obligation(operator, c, a, "EUR", "30000");

        obligation(operator, b, a, "CNY", "300000");
        obligation(operator, a, c, "CNY", "200000");
        obligation(operator, c, b, "CNY", "100000");

        net(operator, "USD");
        net(operator, "EUR");
        net(operator, "CNY");

        // 1) Missing rates must fail and name every missing pair — never default to 1.
        ResponseEntity<String> missing = rest.exchange(
                base() + "/fx-reports/converted-net?settleDate=" + D + "&targetCurrency=JPY",
                HttpMethod.GET, new HttpEntity<>(auth(operator)), String.class);
        assertEquals(HttpStatus.BAD_REQUEST, missing.getStatusCode());
        JsonNode err = JSON.readTree(missing.getBody());
        assertEquals("MISSING_FX_RATE", err.get("code").asText());
        String msg = err.get("message").asText();
        assertTrue(msg.contains("USD/JPY"), msg);
        assertTrue(msg.contains("EUR/JPY"), msg);
        assertTrue(msg.contains("CNY/JPY"), msg);

        // 2) Seed rates and generate the report.
        post("/fx-rates", rateBody("EUR", "USD", "1.10000000"), operator);
        post("/fx-rates", rateBody("USD", "CNY", "7.20000000"), operator);

        JsonNode reportAt110 = getReport(operator, "USD");
        assertEquals(3, reportAt110.get("runCount").asInt());
        BigDecimal eurB110 = findLine(reportAt110, b, "EUR").get("convertedAmount").decimalValue();
        // B net EUR +30000 * 1.10 = 33000 USD
        assertEquals(0, eurB110.compareTo(new BigDecimal("33000.00000000")),
                "30000 EUR @1.10 should be 33000 USD");
        // every currency conserves to zero, so the grand converted total is zero
        assertEquals(0, reportAt110.get("grandTotalConverted").decimalValue()
                .compareTo(BigDecimal.ZERO.setScale(8)));

        // 3) Acceptance: edit ONE rate and re-request — the report number changes immediately.
        String eurUsdRateId = findRateId(operator, "EUR", "USD");
        HttpHeaders h = auth(operator);
        h.set("Content-Type", "application/json");
        ResponseEntity<String> put = rest.exchange(base() + "/fx-rates/" + eurUsdRateId,
                HttpMethod.PUT,
                new HttpEntity<>("{\"rate\":1.20000000,\"effectiveDate\":\"" + D + "\"}", h),
                String.class);
        assertTrue(put.getStatusCode().is2xxSuccessful(), put.getBody());

        JsonNode reportAt120 = getReport(operator, "USD");
        BigDecimal eurB120 = findLine(reportAt120, b, "EUR").get("convertedAmount").decimalValue();
        // 30000 EUR * 1.20 = 36000 USD — the edit is reflected with no extra propagation step
        assertEquals(0, eurB120.compareTo(new BigDecimal("36000.00000000")),
                "30000 EUR @1.20 should be 36000 USD");
        assertTrue(eurB120.compareTo(eurB110) != 0, "report must change after rate edit");

        // 4) Inverse pair resolution: target CNY converts EUR via EUR->USD->... no, direct:
        //    EUR has no EUR/CNY pair, so it must fail (no cross-through assumed).
        ResponseEntity<String> noCnyCross = rest.exchange(
                base() + "/fx-reports/converted-net?settleDate=" + D + "&targetCurrency=CNY",
                HttpMethod.GET, new HttpEntity<>(auth(operator)), String.class);
        assertEquals(HttpStatus.BAD_REQUEST, noCnyCross.getStatusCode());
        assertEquals("MISSING_FX_RATE", JSON.readTree(noCnyCross.getBody()).get("code").asText());
    }

    @Test
    @Order(2)
    void viewerCannotEditRates() throws Exception {
        String viewer = login("viewer", "view123456");
        // read is allowed
        ResponseEntity<String> list = rest.exchange(base() + "/fx-rates",
                HttpMethod.GET, new HttpEntity<>(auth(viewer)), String.class);
        assertEquals(HttpStatus.OK, list.getStatusCode());
        // write is forbidden
        HttpHeaders h = auth(viewer);
        h.set("Content-Type", "application/json");
        ResponseEntity<String> denied = rest.exchange(base() + "/fx-rates",
                HttpMethod.POST,
                new HttpEntity<>(rateBody("GBP", "USD", "1.30"), h), String.class);
        assertEquals(HttpStatus.FORBIDDEN, denied.getStatusCode());
        assertEquals("FORBIDDEN", JSON.readTree(denied.getBody()).get("code").asText());
    }

    private void obligation(String token, String payer, String payee, String ccy, String amount) {
        String body = "{\"payerMemberId\":\"" + payer + "\",\"payeeMemberId\":\"" + payee + "\","
                + "\"currency\":\"" + ccy + "\",\"amount\":" + amount
                + ",\"tradeDate\":\"" + D + "\",\"settleDate\":\"" + D + "\"}";
        try {
            post("/obligations", body, token);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void net(String token, String ccy) {
        try {
            JsonNode run = post("/netting-runs",
                    "{\"settleDate\":\"" + D + "\",\"currency\":\"" + ccy + "\"}", token);
            assertEquals("COMPLETED", run.get("run").get("status").asText(),
                    () -> ccy + " netting should complete: " + run);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String rateBody(String base, String quote, String rate) {
        return "{\"baseCurrency\":\"" + base + "\",\"quoteCurrency\":\"" + quote
                + "\",\"rate\":" + rate + ",\"effectiveDate\":\"" + D + "\"}";
    }

    private JsonNode getReport(String token, String target) throws Exception {
        ResponseEntity<String> resp = rest.exchange(
                base() + "/fx-reports/converted-net?settleDate=" + D + "&targetCurrency=" + target,
                HttpMethod.GET, new HttpEntity<>(auth(token)), String.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode(), resp.getBody());
        return JSON.readTree(resp.getBody());
    }

    private JsonNode findLine(JsonNode report, String memberId, String sourceCurrency) {
        for (JsonNode line : report.get("lines")) {
            if (line.get("memberId").asText().equals(memberId)
                    && line.get("sourceCurrency").asText().equals(sourceCurrency)) {
                return line;
            }
        }
        throw new AssertionError("no report line for member " + memberId + " " + sourceCurrency);
    }

    private String findRateId(String token, String baseCcy, String quoteCcy) throws Exception {
        ResponseEntity<String> resp = rest.exchange(base() + "/fx-rates",
                HttpMethod.GET, new HttpEntity<>(auth(token)), String.class);
        JsonNode arr = JSON.readTree(resp.getBody());
        for (JsonNode rate : arr) {
            if (rate.get("baseCurrency").asText().equals(baseCcy)
                    && rate.get("quoteCurrency").asText().equals(quoteCcy)) {
                String id = rate.get("rateId").asText();
                assertNotNull(id);
                return id;
            }
        }
        throw new AssertionError("rate " + baseCcy + "/" + quoteCcy + " not found");
    }
}
