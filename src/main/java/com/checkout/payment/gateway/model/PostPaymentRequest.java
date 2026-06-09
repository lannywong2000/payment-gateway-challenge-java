package com.checkout.payment.gateway.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.io.Serializable;

public class PostPaymentRequest implements Serializable {

  @NotBlank(message = "card_number is required")
  @Pattern(regexp = "\\d{14,19}", message = "card_number must be 14-19 numeric digits")
  @JsonProperty("card_number")
  private String cardNumber;

  @Min(value = 1, message = "expiry_month must be between 1 and 12")
  @Max(value = 12, message = "expiry_month must be between 1 and 12")
  @JsonProperty("expiry_month")
  private int expiryMonth;

  @Min(value = 2024, message = "expiry_year must not be in the past")
  @JsonProperty("expiry_year")
  private int expiryYear;

  @NotBlank(message = "currency is required")
  @Pattern(regexp = "USD|GBP|EUR", message = "currency must be one of: USD, GBP, EUR")
  private String currency;

  @Positive(message = "amount must be a positive integer")
  private int amount;

  @NotBlank(message = "cvv is required")
  @Pattern(regexp = "\\d{3,4}", message = "cvv must be 3 or 4 numeric digits")
  private String cvv;

  public String getCardNumber() {
    return cardNumber;
  }

  public void setCardNumber(String cardNumber) {
    this.cardNumber = cardNumber;
  }

  public int getExpiryMonth() {
    return expiryMonth;
  }

  public void setExpiryMonth(int expiryMonth) {
    this.expiryMonth = expiryMonth;
  }

  public int getExpiryYear() {
    return expiryYear;
  }

  public void setExpiryYear(int expiryYear) {
    this.expiryYear = expiryYear;
  }

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String currency) {
    this.currency = currency;
  }

  public int getAmount() {
    return amount;
  }

  public void setAmount(int amount) {
    this.amount = amount;
  }

  public String getCvv() {
    return cvv;
  }

  public void setCvv(String cvv) {
    this.cvv = cvv;
  }

  public int getCardNumberLastFour() {
    if (cardNumber == null || cardNumber.length() < 4) {
      return 0;
    }
    return Integer.parseInt(cardNumber.substring(cardNumber.length() - 4));
  }

  @JsonProperty("expiry_date")
  public String getExpiryDate() {
    return String.format("%02d/%d", expiryMonth, expiryYear);
  }

  @Override
  public String toString() {
    String maskedCard = cardNumber != null && cardNumber.length() >= 4
        ? "****" + cardNumber.substring(cardNumber.length() - 4)
        : "****";
    return "PostPaymentRequest{" +
        "cardNumber='" + maskedCard + '\'' +
        ", expiryMonth=" + expiryMonth +
        ", expiryYear=" + expiryYear +
        ", currency='" + currency + '\'' +
        ", amount=" + amount +
        '}';
  }
}
