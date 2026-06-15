package com.checkout.payment.gateway.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.exception.EventProcessingException;
import com.checkout.payment.gateway.model.BankPaymentResponse;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import com.checkout.payment.gateway.repository.PaymentsRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

@ExtendWith(MockitoExtension.class)
class PaymentGatewayServiceTest {

  private static final String BANK_URL = "http://localhost:8080/payments";

  @Mock
  private PaymentsRepository paymentsRepository;

  @Mock
  private RestTemplate restTemplate;

  private PaymentGatewayService service;

  @BeforeEach
  void setUp() {
    service = new PaymentGatewayService(paymentsRepository, restTemplate, BANK_URL);
  }

  // -------------------------------------------------------------------------
  // Validation → REJECTED (bank never called)
  // -------------------------------------------------------------------------

  @Test
  void whenCardNumberIsInvalidThenPaymentIsRejected() {
    PostPaymentResponse response = service.processPayment(
        buildRequest("1234", 6, 2028, "USD", 100, "123"));

    assertThat(response.getStatus()).isEqualTo(PaymentStatus.REJECTED);
    verifyNoInteractions(restTemplate);
    verifyStored(response);
  }

  @Test
  void whenExpiryMonthIsOutOfRangeThenPaymentIsRejected() {
    PostPaymentResponse response = service.processPayment(
        buildRequest("2222405343248877", 13, 2028, "USD", 100, "123"));

    assertThat(response.getStatus()).isEqualTo(PaymentStatus.REJECTED);
    verifyNoInteractions(restTemplate);
    verifyStored(response);
  }

  @Test
  void whenCardIsExpiredThenPaymentIsRejected() {
    PostPaymentResponse response = service.processPayment(
        buildRequest("2222405343248877", 1, 2024, "USD", 100, "123"));

    assertThat(response.getStatus()).isEqualTo(PaymentStatus.REJECTED);
    verifyNoInteractions(restTemplate);
    verifyStored(response);
  }

  @Test
  void whenCurrencyIsInvalidThenPaymentIsRejected() {
    PostPaymentResponse response = service.processPayment(
        buildRequest("2222405343248877", 6, 2028, "JPY", 100, "123"));

    assertThat(response.getStatus()).isEqualTo(PaymentStatus.REJECTED);
    verifyNoInteractions(restTemplate);
    verifyStored(response);
  }

  @Test
  void whenAmountIsZeroThenPaymentIsRejected() {
    PostPaymentResponse response = service.processPayment(
        buildRequest("2222405343248877", 6, 2028, "USD", 0, "123"));

    assertThat(response.getStatus()).isEqualTo(PaymentStatus.REJECTED);
    verifyNoInteractions(restTemplate);
    verifyStored(response);
  }

  @Test
  void whenCvvIsInvalidThenPaymentIsRejected() {
    PostPaymentResponse response = service.processPayment(
        buildRequest("2222405343248877", 6, 2028, "USD", 100, "ab"));

    assertThat(response.getStatus()).isEqualTo(PaymentStatus.REJECTED);
    verifyNoInteractions(restTemplate);
    verifyStored(response);
  }

  // -------------------------------------------------------------------------
  // Bank responses
  // -------------------------------------------------------------------------

  @Test
  void whenBankAuthorizesPaymentThenAuthorizedIsReturned() {
    stubBank(true);

    PostPaymentResponse response = service.processPayment(
        buildRequest("2222405343248877", 6, 2028, "USD", 100, "123"));

    assertThat(response.getStatus()).isEqualTo(PaymentStatus.AUTHORIZED);
    assertThat(response.getCardNumberLastFour()).isEqualTo(8877);
    assertThat(response.getId()).isNotNull();
    verifyStored(response);
  }

  @Test
  void whenBankDeclinesPaymentThenDeclinedIsReturned() {
    stubBank(false);

    PostPaymentResponse response = service.processPayment(
        buildRequest("2222405343248112", 6, 2028, "USD", 100, "123"));

    assertThat(response.getStatus()).isEqualTo(PaymentStatus.DECLINED);
    verifyStored(response);
  }

  @Test
  void whenBankReturns4xxThenPaymentIsDeclined() {
    when(restTemplate.postForEntity(eq(BANK_URL), any(), eq(BankPaymentResponse.class)))
        .thenThrow(new HttpClientErrorException(HttpStatus.UNPROCESSABLE_ENTITY));

    PostPaymentResponse response = service.processPayment(
        buildRequest("2222405343248877", 6, 2028, "USD", 100, "123"));

    assertThat(response.getStatus()).isEqualTo(PaymentStatus.DECLINED);
    verifyStored(response);
  }

  @Test
  void whenBankReturns5xxThenPaymentIsDeclined() {
    when(restTemplate.postForEntity(eq(BANK_URL), any(), eq(BankPaymentResponse.class)))
        .thenThrow(new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE));

    PostPaymentResponse response = service.processPayment(
        buildRequest("2222405343248877", 6, 2028, "USD", 100, "123"));

    assertThat(response.getStatus()).isEqualTo(PaymentStatus.DECLINED);
    verifyStored(response);
  }

  // -------------------------------------------------------------------------
  // getPaymentById
  // -------------------------------------------------------------------------

  @Test
  void whenPaymentExistsThenItIsReturned() {
    PostPaymentResponse stored = new PostPaymentResponse();
    stored.setId(UUID.randomUUID());
    stored.setStatus(PaymentStatus.AUTHORIZED);
    when(paymentsRepository.get(stored.getId())).thenReturn(Optional.of(stored));

    assertThat(service.getPaymentById(stored.getId())).isEqualTo(stored);
  }

  @Test
  void whenPaymentDoesNotExistThenExceptionIsThrown() {
    UUID id = UUID.randomUUID();
    when(paymentsRepository.get(id)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getPaymentById(id))
        .isInstanceOf(EventProcessingException.class);
  }

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  private void stubBank(boolean authorized) {
    BankPaymentResponse bankResponse = new BankPaymentResponse();
    bankResponse.setAuthorized(authorized);
    when(restTemplate.postForEntity(eq(BANK_URL), any(), eq(BankPaymentResponse.class)))
        .thenReturn(ResponseEntity.ok(bankResponse));
  }

  private void verifyStored(PostPaymentResponse response) {
    ArgumentCaptor<PostPaymentResponse> captor = forClass(PostPaymentResponse.class);
    verify(paymentsRepository).add(captor.capture());
    assertThat(captor.getValue()).isSameAs(response);
  }

  private PostPaymentRequest buildRequest(String cardNumber, int expiryMonth, int expiryYear,
      String currency, int amount, String cvv) {
    PostPaymentRequest request = new PostPaymentRequest();
    request.setCardNumber(cardNumber);
    request.setExpiryMonth(expiryMonth);
    request.setExpiryYear(expiryYear);
    request.setCurrency(currency);
    request.setAmount(amount);
    request.setCvv(cvv);
    return request;
  }
}
