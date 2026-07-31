package com.baronesa.emporio.service.payment.model;

public record PaymentInstallmentsSettings(
        boolean enabled,
        String minAmount, // decimal em reais (string para manter formatação do config)
        int maxTimes
) {}
