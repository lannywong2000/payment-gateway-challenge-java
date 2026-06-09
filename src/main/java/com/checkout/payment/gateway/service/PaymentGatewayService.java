package com.checkout.payment.gateway.service;

import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.exception.EventProcessingException;
import com.checkout.payment.gateway.exception.PaymentValidationException;
import com.checkout.payment.gateway.model.BankPaymentRequest;
import com.checkout.payment.gateway.model.BankPaymentResponse;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import com.checkout.payment.gateway.repository.PaymentsRepository;
import java.time.YearMonth;
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

    validateExpiryDate(request.getExpiryMonth(), request.getExpiryYear());

    BankPaymentRequest bankRequest = new BankPaymentRequest(
        request.getCardNumber(),
        request.getExpiryDate(),
        request.getCurrency(),
        request.getAmount(),
        request.getCvv()
    );

    PostPaymentResponse response = new PostPaymentResponse();
    response.setId(UUID.randomUUID());
    response.setCardNumberLastFour(request.getCardNumberLastFour());
    response.setExpiryMonth(request.getExpiryMonth());
    response.setExpiryYear(request.getExpiryYear());
    response.setCurrency(request.getCurrency());
    response.setAmount(request.getAmount());

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
    }

    paymentsRepository.add(response);
    return response;
  }

  private void validateExpiryDate(int month, int year) {
    YearMonth expiry = YearMonth.of(year, month);
    YearMonth now = YearMonth.now();
    if (expiry.isBefore(now)) {
      throw new PaymentValidationException(
          "Card has expired: expiry date " + month + "/" + year + " is in the past");
    }
  }
}
