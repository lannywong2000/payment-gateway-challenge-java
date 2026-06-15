package com.checkout.payment.gateway.controller;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class PaymentGatewayIntegrationTest {

  @Autowired
  private MockMvc mvc;

  private static final String PAYMENTS_URL = "/payments";
  private static final String PAYMENT_URL = "/payment";

  @Test
  void whenCardWithOddLastDigitThenAuthorized() throws Exception {
    mvc.perform(MockMvcRequestBuilders.post(PAYMENTS_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .content(buildRequest("2222405343248877", 6, 2028, "GBP", 100, "123")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("Authorized"))
        .andExpect(jsonPath("$.id").isNotEmpty())
        .andExpect(jsonPath("$.cardNumberLastFour").value(8877))
        .andExpect(jsonPath("$.expiryMonth").value(6))
        .andExpect(jsonPath("$.expiryYear").value(2028))
        .andExpect(jsonPath("$.currency").value("GBP"))
        .andExpect(jsonPath("$.amount").value(100));
  }

  @Test
  void whenCardWithEvenLastDigitThenDeclined() throws Exception {
    mvc.perform(MockMvcRequestBuilders.post(PAYMENTS_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .content(buildRequest("2222405343248112", 1, 2028, "USD", 60000, "1234")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("Declined"))
        .andExpect(jsonPath("$.cardNumberLastFour").value(8112));
  }

  @Test
  void whenCardEndingIn0ThenBankUnavailableAndDeclined() throws Exception {
    mvc.perform(MockMvcRequestBuilders.post(PAYMENTS_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .content(buildRequest("2222405343248110", 1, 2028, "USD", 100, "123")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("Declined"));
  }

  @Test
  void whenInvalidCardNumberThenRejectedWithoutContactingBank() throws Exception {
    mvc.perform(MockMvcRequestBuilders.post(PAYMENTS_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .content(buildRequest("1234", 6, 2028, "USD", 100, "123")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("Rejected"));
  }

  @Test
  void whenExpiredCardThenRejectedWithoutContactingBank() throws Exception {
    mvc.perform(MockMvcRequestBuilders.post(PAYMENTS_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .content(buildRequest("2222405343248877", 1, 2024, "USD", 100, "123")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("Rejected"));
  }

  @Test
  void whenAuthorizedPaymentThenCanBeRetrievedById() throws Exception {
    MvcResult result = mvc.perform(MockMvcRequestBuilders.post(PAYMENTS_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .content(buildRequest("2222405343248877", 6, 2028, "GBP", 100, "123")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("Authorized"))
        .andReturn();

    String id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");

    mvc.perform(MockMvcRequestBuilders.get(PAYMENT_URL + "/" + id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id))
        .andExpect(jsonPath("$.status").value("Authorized"))
        .andExpect(jsonPath("$.cardNumberLastFour").value(8877));
  }

  private String buildRequest(String cardNumber, int expiryMonth, int expiryYear,
      String currency, int amount, String cvv) {
    return String.format(
        "{\"card_number\":\"%s\",\"expiry_month\":%d,\"expiry_year\":%d,"
            + "\"currency\":\"%s\",\"amount\":%d,\"cvv\":\"%s\"}",
        cardNumber, expiryMonth, expiryYear, currency, amount, cvv);
  }
}
