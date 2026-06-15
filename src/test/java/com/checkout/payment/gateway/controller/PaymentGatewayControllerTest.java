package com.checkout.payment.gateway.controller;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import com.checkout.payment.gateway.repository.PaymentsRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.client.RestTemplate;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;

@SpringBootTest
@AutoConfigureMockMvc
class PaymentGatewayControllerTest {

  @Autowired
  private MockMvc mvc;

  @Autowired
  private PaymentsRepository paymentsRepository;

  @Autowired
  private RestTemplate restTemplate;

  private MockRestServiceServer mockBankServer;

  private static final String BANK_URL = "http://localhost:8080/payments";
  private static final String PAYMENTS_URL = "/payments";
  private static final String PAYMENT_URL = "/payment";

  @BeforeEach
  void setUp() {
    mockBankServer = MockRestServiceServer.createServer(restTemplate);
  }

  // -------------------------------------------------------------------------
  // GET /payment/{id}
  // -------------------------------------------------------------------------

  @Test
  void whenPaymentWithIdExistThenCorrectPaymentIsReturned() throws Exception {
    PostPaymentResponse payment = buildStoredPayment(PaymentStatus.AUTHORIZED, 4321);
    paymentsRepository.add(payment);

    mvc.perform(MockMvcRequestBuilders.get(PAYMENT_URL + "/" + payment.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(payment.getId().toString()))
        .andExpect(jsonPath("$.status").value("Authorized"))
        .andExpect(jsonPath("$.cardNumberLastFour").value(4321))
        .andExpect(jsonPath("$.expiryMonth").value(12))
        .andExpect(jsonPath("$.expiryYear").value(2026))
        .andExpect(jsonPath("$.currency").value("USD"))
        .andExpect(jsonPath("$.amount").value(100));
  }

  @Test
  void whenPaymentWithIdDoesNotExistThen404IsReturned() throws Exception {
    mvc.perform(MockMvcRequestBuilders.get(PAYMENT_URL + "/" + UUID.randomUUID()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("Page not found"));
  }

  // -------------------------------------------------------------------------
  // POST /payments — authorized
  // -------------------------------------------------------------------------

  @Test
  void whenValidPaymentWithOddLastDigitThenAuthorizedIsReturned() throws Exception {
    mockBankServer.expect(requestTo(BANK_URL))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withSuccess(
            "{\"authorized\":true,\"authorization_code\":\"abc-123\"}",
            MediaType.APPLICATION_JSON));

    mvc.perform(MockMvcRequestBuilders.post(PAYMENTS_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .content(buildPaymentRequestJson("2222405343248877", 4, 2027, "GBP", 100, "123")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").isNotEmpty())
        .andExpect(jsonPath("$.status").value("Authorized"))
        .andExpect(jsonPath("$.cardNumberLastFour").value(8877))
        .andExpect(jsonPath("$.expiryMonth").value(4))
        .andExpect(jsonPath("$.expiryYear").value(2027))
        .andExpect(jsonPath("$.currency").value("GBP"))
        .andExpect(jsonPath("$.amount").value(100));

    mockBankServer.verify();
  }

  // -------------------------------------------------------------------------
  // POST /payments — declined
  // -------------------------------------------------------------------------

  @Test
  void whenValidPaymentWithEvenLastDigitThenDeclinedIsReturned() throws Exception {
    mockBankServer.expect(requestTo(BANK_URL))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withSuccess(
            "{\"authorized\":false,\"authorization_code\":\"\"}",
            MediaType.APPLICATION_JSON));

    mvc.perform(MockMvcRequestBuilders.post(PAYMENTS_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .content(buildPaymentRequestJson("2222405343248112", 1, 2027, "USD", 60000, "1234")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("Declined"))
        .andExpect(jsonPath("$.cardNumberLastFour").value(8112));

    mockBankServer.verify();
  }

  // -------------------------------------------------------------------------
  // POST /payments — bank unavailable (503) → stored as Declined
  // -------------------------------------------------------------------------

  @Test
  void whenBankReturns503ThenPaymentIsStoredAsDeclined() throws Exception {
    mockBankServer.expect(requestTo(BANK_URL))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

    mvc.perform(MockMvcRequestBuilders.post(PAYMENTS_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .content(buildPaymentRequestJson("2222405343248110", 1, 2027, "USD", 100, "123")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("Declined"));

    mockBankServer.verify();
  }

  @Test
  void whenBankReturns500ThenPaymentIsStoredAsDeclined() throws Exception {
    mockBankServer.expect(requestTo(BANK_URL))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withServerError());

    mvc.perform(MockMvcRequestBuilders.post(PAYMENTS_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .content(buildPaymentRequestJson("2222405343248110", 1, 2027, "USD", 100, "123")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("Declined"));

    mockBankServer.verify();
  }

  @Test
  void whenBankReturns4xxThenPaymentIsStoredAsDeclined() throws Exception {
    mockBankServer.expect(requestTo(BANK_URL))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY));

    mvc.perform(MockMvcRequestBuilders.post(PAYMENTS_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .content(buildPaymentRequestJson("2222405343248110", 1, 2027, "USD", 100, "123")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("Declined"));

    mockBankServer.verify();
  }

  // -------------------------------------------------------------------------
  // POST /payments — Rejected (validation failures)
  // -------------------------------------------------------------------------

  @Test
  void whenCardNumberIsTooShortThenRejectedIsReturned() throws Exception {
    mvc.perform(MockMvcRequestBuilders.post(PAYMENTS_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .content(buildPaymentRequestJson("12345", 6, 2028, "USD", 100, "123")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("Rejected"));
  }

  @Test
  void whenCardNumberContainsNonNumericCharactersThenRejectedIsReturned() throws Exception {
    mvc.perform(MockMvcRequestBuilders.post(PAYMENTS_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .content(buildPaymentRequestJson("2222405343abcd77", 6, 2028, "USD", 100, "123")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("Rejected"));
  }

  @Test
  void whenExpiryMonthIsOutOfRangeThenRejectedIsReturned() throws Exception {
    mvc.perform(MockMvcRequestBuilders.post(PAYMENTS_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .content(buildPaymentRequestJson("2222405343248877", 13, 2028, "USD", 100, "123")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("Rejected"));
  }

  @Test
  void whenExpiryYearIsInThePastThenRejectedIsReturned() throws Exception {
    mvc.perform(MockMvcRequestBuilders.post(PAYMENTS_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .content(buildPaymentRequestJson("2222405343248877", 1, 2020, "USD", 100, "123")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("Rejected"));
  }

  @Test
  void whenCardIsExpiredThenRejectedIsReturned() throws Exception {
    // January 2024 is reliably in the past for any month after January 2024.
    mvc.perform(MockMvcRequestBuilders.post(PAYMENTS_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .content(buildPaymentRequestJson("2222405343248877", 1, 2024, "USD", 100, "123")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("Rejected"))
        .andExpect(jsonPath("$.id").isNotEmpty())
        .andExpect(jsonPath("$.cardNumberLastFour").value(8877));
  }

  @Test
  void whenCurrencyIsNotThreeLettersThenRejectedIsReturned() throws Exception {
    mvc.perform(MockMvcRequestBuilders.post(PAYMENTS_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .content(buildPaymentRequestJson("2222405343248877", 6, 2028, "US", 100, "123")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("Rejected"));
  }

  @Test
  void whenCurrencyContainsLowercaseLettersThenRejectedIsReturned() throws Exception {
    mvc.perform(MockMvcRequestBuilders.post(PAYMENTS_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .content(buildPaymentRequestJson("2222405343248877", 6, 2028, "usd", 100, "123")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("Rejected"));
  }

  @Test
  void whenCurrencyIsNullThenRejectedIsReturned() throws Exception {
    mvc.perform(MockMvcRequestBuilders.post(PAYMENTS_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"card_number\":\"2222405343248877\",\"expiry_month\":6,\"expiry_year\":2028,"
                + "\"amount\":100,\"cvv\":\"123\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("Rejected"));
  }

  @Test
  void whenAmountIsZeroThenRejectedIsReturned() throws Exception {
    mvc.perform(MockMvcRequestBuilders.post(PAYMENTS_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .content(buildPaymentRequestJson("2222405343248877", 6, 2028, "USD", 0, "123")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("Rejected"));
  }

  @Test
  void whenAmountIsNegativeThenRejectedIsReturned() throws Exception {
    mvc.perform(MockMvcRequestBuilders.post(PAYMENTS_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .content(buildPaymentRequestJson("2222405343248877", 6, 2028, "USD", -1, "123")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("Rejected"));
  }

  @Test
  void whenCvvIsTooShortThenRejectedIsReturned() throws Exception {
    mvc.perform(MockMvcRequestBuilders.post(PAYMENTS_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .content(buildPaymentRequestJson("2222405343248877", 6, 2028, "USD", 100, "12")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("Rejected"));
  }

  @Test
  void whenCvvContainsNonNumericCharactersThenRejectedIsReturned() throws Exception {
    mvc.perform(MockMvcRequestBuilders.post(PAYMENTS_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .content(buildPaymentRequestJson("2222405343248877", 6, 2028, "USD", 100, "1ab")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("Rejected"));
  }

  @Test
  void whenAuthorizedPaymentCanBeRetrievedAfterProcessing() throws Exception {
    mockBankServer.expect(requestTo(BANK_URL))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withSuccess(
            "{\"authorized\":true,\"authorization_code\":\"xyz-789\"}",
            MediaType.APPLICATION_JSON));

    String postResult = mvc.perform(MockMvcRequestBuilders.post(PAYMENTS_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .content(buildPaymentRequestJson("2222405343248877", 4, 2027, "GBP", 100, "123")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("Authorized"))
        .andReturn()
        .getResponse()
        .getContentAsString();

    // Extract the ID and verify the payment can be fetched
    String paymentId = com.jayway.jsonpath.JsonPath.read(postResult, "$.id");
    mvc.perform(MockMvcRequestBuilders.get(PAYMENT_URL + "/" + paymentId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(paymentId))
        .andExpect(jsonPath("$.status").value("Authorized"))
        .andExpect(jsonPath("$.cardNumberLastFour").value(8877));

    mockBankServer.verify();
  }

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  private PostPaymentResponse buildStoredPayment(PaymentStatus status, int lastFour) {
    PostPaymentResponse payment = new PostPaymentResponse();
    payment.setId(UUID.randomUUID());
    payment.setAmount(100);
    payment.setCurrency("USD");
    payment.setStatus(status);
    payment.setExpiryMonth(12);
    payment.setExpiryYear(2026);
    payment.setCardNumberLastFour(lastFour);
    return payment;
  }

  private String buildPaymentRequestJson(String cardNumber, int expiryMonth, int expiryYear,
      String currency, int amount, String cvv) {
    return String.format(
        "{\"card_number\":\"%s\",\"expiry_month\":%d,\"expiry_year\":%d,"
            + "\"currency\":\"%s\",\"amount\":%d,\"cvv\":\"%s\"}",
        cardNumber, expiryMonth, expiryYear, currency, amount, cvv);
  }
}
