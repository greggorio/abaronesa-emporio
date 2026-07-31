package com.baronesa.emporio.service.payment.api;

import lombok.Data;

@Data
public class CardTokenRequest {
    private String cardNumber;
    private String cardholderName;
    private String cardExpirationMonth;
    private String cardExpirationYear;
    private String securityCode;
    private String identificationType;
    private String identificationNumber;
}
