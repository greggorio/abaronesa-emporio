package com.baronesa.emporio.service.payment;

import com.baronesa.emporio.entity.PaymentSettings;
import com.baronesa.emporio.repository.PaymentSettingsRepository;
import com.baronesa.emporio.service.payment.model.PaymentGatewayType;
import com.baronesa.emporio.service.payment.model.PaymentInstallmentsSettings;
import com.baronesa.emporio.util.ConfigManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PaymentSettingsService {

    private final PaymentSettingsRepository paymentSettingsRepository;
    private final ConfigManager configManager;

    @Transactional(readOnly = true)
    public PaymentGatewayType getActiveGateway() {
        PaymentSettings settings = getOrCreate();
        PaymentGatewayType configGateway = resolveDefaultGateway();
        if (configGateway != null && settings.getActiveGateway() != configGateway) {
            // Sincroniza com o valor configurado (permite troca via configuracoes)
            settings.setActiveGateway(configGateway);
            paymentSettingsRepository.save(settings);
        }
        return settings.getActiveGateway();
    }

    @Transactional
    public PaymentSettings setActiveGateway(PaymentGatewayType gateway) {
        PaymentSettings settings = getOrCreate();
        settings.setActiveGateway(gateway != null ? gateway : PaymentGatewayType.MERCADOPAGO);
        return paymentSettingsRepository.save(settings);
    }

    @Transactional(readOnly = true)
    public List<PaymentGatewayType> getAvailableGateways() {
        return Arrays.asList(PaymentGatewayType.values());
    }

    @Transactional(readOnly = true)
    public PaymentInstallmentsSettings getInstallmentSettings(PaymentGatewayType gateway) {
        if (gateway == null) {
            gateway = PaymentGatewayType.MERCADOPAGO;
        }
        return switch (gateway) {
            case MERCADOPAGO -> new PaymentInstallmentsSettings(
                    configManager.getBooleanConfig("mercadopago_installments_enabled", true),
                    configManager.getConfig("mercadopago_installments_min_amount", "0.00"),
                    configManager.getIntConfig("mercadopago_installments_max_times", 3)
            );
            case PAGSEGURO -> new PaymentInstallmentsSettings(
                    configManager.getBooleanConfig("pagseguro_installments_enabled", true),
                    configManager.getConfig("pagseguro_installments_min_amount", "0.00"),
                    configManager.getIntConfig("pagseguro_installments_max_times", 3)
            );
        };
    }

    private PaymentSettings getOrCreate() {
        return paymentSettingsRepository.findAll()
                .stream()
                .findFirst()
                .orElseGet(() -> paymentSettingsRepository.save(
                        PaymentSettings.builder()
                                .activeGateway(resolveDefaultGateway())
                                .build()
                ));
    }

    private PaymentGatewayType resolveDefaultGateway() {
        String cfg = configManager.getConfig("payment_default_gateway", PaymentGatewayType.MERCADOPAGO.name());
        try {
            return PaymentGatewayType.valueOf(cfg.trim().toUpperCase());
        } catch (Exception e) {
            return PaymentGatewayType.MERCADOPAGO;
        }
    }
}
