package com.checkout.payment.gateway.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;

public class PostPaymentRequest implements Serializable {

  @JsonProperty("card_number")
  private String cardNumber;

  @JsonProperty("expiry_month")
  private int expiryMonth;

  @JsonProperty("expiry_year")
  private int expiryYear;

  private String currency;

  private int amount;

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
    try {
      return Integer.parseInt(cardNumber.substring(cardNumber.length() - 4));
    } catch (Exception e) {
      return 0;
    }
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
