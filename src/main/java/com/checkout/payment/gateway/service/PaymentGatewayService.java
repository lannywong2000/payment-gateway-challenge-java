package com.checkout.payment.gateway.service;

import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.exception.EventProcessingException;
import com.checkout.payment.gateway.model.BankPaymentRequest;
import com.checkout.payment.gateway.model.BankPaymentResponse;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import com.checkout.payment.gateway.repository.PaymentsRepository;
import java.time.YearMonth;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

@Service
public class PaymentGatewayService {

  private static final Logger LOG = LoggerFactory.getLogger(PaymentGatewayService.class);
  private static final Set<String> SUPPORTED_CURRENCIES = Set.of("USD", "GBP", "EUR");

  private final PaymentsRepository paymentsRepository;
  private final RestTemplate restTemplate;
  private final String bankSimulatorUrl;

  public PaymentGatewayService(PaymentsRepository paymentsRepository,
      RestTemplate restTemplate,
      @Value("${bank.simulator.url}") String bankSimulatorUrl) {
    this.paymentsRepository = paymentsRepository;
    this.restTemplate = restTemplate;
    this.bankSimulatorUrl = bankSimulatorUrl;
  }

  public PostPaymentResponse getPaymentById(UUID id) {
    LOG.debug("Fetching payment with ID {}", id);
    return paymentsRepository.get(id)
        .orElseThrow(() -> new EventProcessingException("Payment not found for ID: " + id));
  }

  public PostPaymentResponse processPayment(PostPaymentRequest request) {
    LOG.debug("Processing payment request: {}", request);

    PostPaymentResponse response = buildResponse(request);

    if (!isRequestValid(request)) {
      LOG.warn("Payment request rejected due to validation failure, paymentId={}", response.getId());
      response.setStatus(PaymentStatus.REJECTED);
      paymentsRepository.add(response);
      return response;
    }

    BankPaymentRequest bankRequest = new BankPaymentRequest(
        request.getCardNumber(),
        request.getExpiryDate(),
        request.getCurrency(),
        request.getAmount(),
        request.getCvv()
    );

    try {
      ResponseEntity<BankPaymentResponse> bankResponse =
          restTemplate.postForEntity(bankSimulatorUrl, bankRequest, BankPaymentResponse.class);

      if (bankResponse.getStatusCode() == HttpStatus.OK && bankResponse.getBody() != null) {
        boolean authorized = bankResponse.getBody().isAuthorized();
        response.setStatus(authorized ? PaymentStatus.AUTHORIZED : PaymentStatus.DECLINED);
        LOG.info("Bank responded: authorized={}, paymentId={}", authorized, response.getId());
      } else {
        response.setStatus(PaymentStatus.DECLINED);
        LOG.warn("Unexpected bank response status: {}", bankResponse.getStatusCode());
      }
    } catch (HttpClientErrorException e) {
      LOG.warn("Bank returned client error {}: {}", e.getStatusCode(), e.getMessage());
      response.setStatus(PaymentStatus.DECLINED);
    } catch (HttpServerErrorException e) {
      LOG.warn("Bank returned server error {}: {}", e.getStatusCode(), e.getMessage());
      response.setStatus(PaymentStatus.DECLINED);
    } catch (Exception e) {
      LOG.error("An unexpected error occurred", e);
      response.setStatus(PaymentStatus.DECLINED);
    }

    paymentsRepository.add(response);
    return response;
  }

  private PostPaymentResponse buildResponse(PostPaymentRequest request) {
    PostPaymentResponse response = new PostPaymentResponse();
    response.setId(UUID.randomUUID());
    response.setCardNumberLastFour(request.getCardNumberLastFour());
    response.setExpiryMonth(request.getExpiryMonth());
    response.setExpiryYear(request.getExpiryYear());
    response.setCurrency(request.getCurrency());
    response.setAmount(request.getAmount());
    return response;
  }

  private boolean isRequestValid(PostPaymentRequest request) {
    return isCardNumberValid(request.getCardNumber())
        && isExpiryValid(request.getExpiryMonth(), request.getExpiryYear())
        && isCurrencyValid(request.getCurrency())
        && isAmountValid(request.getAmount())
        && isCvvValid(request.getCvv());
  }

  private boolean isCardNumberValid(String cardNumber) {
    return cardNumber != null && cardNumber.matches("\\d{14,19}");
  }

  private boolean isExpiryValid(int month, int year) {
    if (month < 1 || month > 12) {
      return false;
    }
    return !YearMonth.of(year, month).isBefore(YearMonth.now());
  }

  private boolean isCurrencyValid(String currency) {
    return currency != null && SUPPORTED_CURRENCIES.contains(currency);
  }

  private boolean isAmountValid(int amount) {
    return amount > 0;
  }

  private boolean isCvvValid(String cvv) {
    return cvv != null && cvv.matches("\\d{3,4}");
  }
}
