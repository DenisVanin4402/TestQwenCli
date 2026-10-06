package ru.sberbank.pprb.agent.db.config;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.serializers.TimeSerializers;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.statemachine.data.jpa.JpaRepositoryStateMachinePersist;
import org.springframework.statemachine.data.jpa.JpaStateMachineRepository;
import org.springframework.statemachine.persist.DefaultStateMachinePersister;
import ru.sberbank.pprb.agent.model.dto.payment.PreparationDTO;
import ru.sberbank.pprb.agent.model.dto.payment.TrustedPaymentContextDTO;
import ru.sberbank.pprb.agent.model.enums.Operation;
import ru.sberbank.pprb.agent.model.enums.SessionEvent;
import ru.sberbank.pprb.agent.model.enums.SessionState;
import ru.sberbank.pprb.agent.model.enums.TurnOutcome;

/**
 * Настраивает штатный JPA persister SSM для конкретных типов нашего сценария. Формат состояния и
 * сериализаторы остаются библиотечными; здесь задаётся только стабильная регистрация типов.
 */
@Configuration(proxyBeanMethods = false)
public class NativePersistenceConfiguration {
    /** Создаёт штатный persister, который участвует в текущей транзакции JPA. */
    @Bean
    public DefaultStateMachinePersister<SessionState, SessionEvent, Object>
            nativeStateMachinePersister(JpaStateMachineRepository repository) {
        return new DefaultStateMachinePersister<>(
                new JpaRepositoryStateMachinePersist<>(
                        repository, NativePersistenceConfiguration::registerTypes));
    }

    /**
     * Закрепляет номера типов для сохранённого формата status-v3, включая предметный исход вместо
     * готового ответа пользователю. Изменение состава или номеров требует пересмотра formatId:
     * несовместимые данные нельзя десериализовать как прежний формат.
     */
    private static void registerTypes(Kryo kryo) {
        TimeSerializers.addDefaultSerializers(kryo);
        kryo.register(SessionState.class, 100);
        kryo.register(SessionEvent.class, 101);
        kryo.register(Operation.class, 102);
        kryo.register(TrustedPaymentContextDTO.class, 104);
        kryo.register(PreparationDTO.class, 105);
        kryo.register(TurnOutcome.class, 106);
        kryo.register(LocalDate.class, 107);
        kryo.register(Instant.class, 108);
        kryo.register(BigDecimal.class, 109);
        kryo.register(ru.sberbank.pprb.agent.model.dto.payment.ConfirmationValueDTO.class, 110);
    }
}
