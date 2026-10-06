package ru.sberbank.pprb.agent.gigaassistant.config;

import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;
import org.openapitools.jackson.nullable.JsonNullableModule;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Настраивает чтение локального ACL: отсутствие поля отличается от null, а строковые реквизиты не
 * принимают числа и boolean.
 */
@Configuration(proxyBeanMethods = false)
@Profile("poc-local")
public class AclJsonConfiguration {
    /**
     * Сохраняет факт явной передачи null, чтобы адаптер не дополнил ошибочное значение из прежней
     * сессии.
     */
    @Bean
    public JsonNullableModule aclNullableModule() {
        return new JsonNullableModule();
    }

    /** Запрещает Jackson незаметно превращать неверный JSON-тип реквизита в допустимую строку. */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer aclStringCoercion() {
        return builder ->
                builder.postConfigurer(
                        mapper ->
                                mapper.coercionConfigFor(LogicalType.Textual)
                                        .setCoercion(
                                                CoercionInputShape.Integer, CoercionAction.Fail)
                                        .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                                        .setCoercion(
                                                CoercionInputShape.Boolean, CoercionAction.Fail));
    }
}
