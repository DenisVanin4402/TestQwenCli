package ru.sberbank.pprb.agent.chatui.mapper;

import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import ru.sberbank.pprb.agent.chatui.generated.model.Fixture;
import ru.sberbank.pprb.agent.chatui.generated.model.FixtureMetadata;
import ru.sberbank.pprb.agent.chatui.generated.model.FixtureParameter;
import ru.sberbank.pprb.agent.chatui.generated.model.Payment;
import ru.sberbank.pprb.agent.common.acl.AclProfile;
import ru.sberbank.pprb.agent.common.mapper.MappingConfig;
import ru.sberbank.pprb.agent.model.dto.payment.TrustedPaymentContextDTO;

/** Преобразует серверный тестовый платёж и саджесты в контракт каталога fixtures. */
@Mapper(config = MappingConfig.class)
public interface FixtureMapper {
    /** Оба generated-типа получены из одного определения AclSuggestion. */
    List<ru.sberbank.pprb.agent.chatui.generated.model.AclSuggestion> toSuggestions(
            List<ru.sberbank.pprb.agent.gigaassistant.generated.model.AclSuggestion> suggestions);

    /** Переносит исходные реквизиты синтетического платежа. */
    Payment toPayment(TrustedPaymentContextDTO context);

    /** Соединяет карточку платежа и готовый пример ACL metadata под локальным именем каталога. */
    @Mapping(target = "payment", source = "context")
    @Mapping(target = "metadata", source = "context")
    @Mapping(target = "fixtureId", source = "fixtureId")
    Fixture toFixture(TrustedPaymentContextDTO context, String fixtureId);

    /**
     * Преобразует полный тестовый контекст в существующие поля ACL, без собственного формата
     * команды.
     */
    @Mapping(target = "organization.epkId", source = "epkId")
    @Mapping(target = "customerInfo.digitalUserId", source = "digitalUserId")
    @Mapping(target = "additionalInfo", expression = "java(toParameters(context))")
    FixtureMetadata toMetadata(TrustedPaymentContextDTO context);

    /**
     * Создаёт одну строковую пару ACL; дата и сумма приводятся к нужному формату до этого вызова.
     */
    FixtureParameter toParameter(String key, String value);

    /** Возвращает семь строковых параметров платежа в форме, пригодной для первого ACL-входа. */
    default List<FixtureParameter> toParameters(TrustedPaymentContextDTO context) {
        return List.of(
                toParameter(AclProfile.PAYMENT_ID, context.getPaymentId()),
                toParameter(AclProfile.PAYMENT_NUMBER, context.getPaymentNumber()),
                toParameter(AclProfile.PAYMENT_DATE, context.getPaymentDate().toString()),
                toParameter(AclProfile.AMOUNT, context.getAmount().toPlainString()),
                toParameter(AclProfile.CURRENCY, context.getCurrency()),
                toParameter(AclProfile.RECIPIENT_NAME, context.getRecipientName()),
                toParameter(AclProfile.ORGANIZATION_NAME, context.getOrganizationName()));
    }
}
