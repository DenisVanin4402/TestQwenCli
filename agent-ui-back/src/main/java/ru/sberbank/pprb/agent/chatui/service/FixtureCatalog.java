package ru.sberbank.pprb.agent.chatui.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import ru.sberbank.pprb.agent.model.dto.payment.TrustedPaymentContextDTO;

/**
 * Содержит единственный синтетический платёж POC; его имя используется только в локальном каталоге.
 */
@Component
@Profile("poc-local")
public class FixtureCatalog {
    /**
     * Возвращает новые данные примера, чтобы изменение ответа вызывающим кодом не изменяло
     * последующие выдачи каталога.
     */
    public TrustedPaymentContextDTO payment() {
        return TrustedPaymentContextDTO.builder()
                .epkId("test-org-1")
                .digitalUserId("test-user-1")
                .paymentId("test-payment-42")
                .paymentNumber("42")
                .paymentDate(LocalDate.of(2026, 10, 3))
                .amount(new BigDecimal("12500.00"))
                .currency("RUB")
                .recipientName("ООО Альфа")
                .organizationName("ООО Вектор")
                .build();
    }
}
