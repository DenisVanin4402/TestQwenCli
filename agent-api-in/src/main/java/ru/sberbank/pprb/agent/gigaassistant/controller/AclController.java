package ru.sberbank.pprb.agent.gigaassistant.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import ru.sberbank.pprb.agent.gigaassistant.generated.api.AclApi;
import ru.sberbank.pprb.agent.gigaassistant.generated.model.AclRequest;
import ru.sberbank.pprb.agent.gigaassistant.generated.model.AclResponse;
import ru.sberbank.pprb.agent.gigaassistant.service.AclInputAdapter;

/**
 * Принимает локальный ACL POST по сгенерированному контракту и передаёт сообщение адаптеру канала.
 */
@RestController
@Profile("poc-local")
@RequiredArgsConstructor
public class AclController implements AclApi {
    private final AclInputAdapter adapter;

    /**
     * Передаёт заголовки и тело без интерпретации согласия, чтения сессии или преобразования
     * реквизитов.
     */
    @Override
    public ResponseEntity<AclResponse> handleMessage(
            String agentCode, String requestId, String gigachatSessionId, AclRequest aclRequest) {
        return adapter.handle(agentCode, requestId, gigachatSessionId, aclRequest);
    }
}
