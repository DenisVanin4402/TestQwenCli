package ru.sberbank.pprb.agent.chatui.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.sberbank.pprb.agent.gigaassistant.generated.api.AclApi;
import ru.sberbank.pprb.agent.gigaassistant.generated.model.AclRequest;
import ru.sberbank.pprb.agent.gigaassistant.generated.model.AclResponse;
import ru.sberbank.pprb.agent.gigaassistant.service.AclInputAdapter;

/** Локальное зеркало ACL: тот же generated-контракт и адаптер под отдельным префиксом UI. */
@RestController
@Profile("poc-local")
@RequestMapping("/local-api")
@RequiredArgsConstructor
public class LocalAclController implements AclApi {
    private final AclInputAdapter adapter;

    /** Передаёт ход общему адаптеру без HTTP-вызова основного контроллера и копии бизнес-логики. */
    @Override
    public ResponseEntity<AclResponse> handleMessage(
            String agentCode, String requestId, String gigachatSessionId, AclRequest aclRequest) {
        return adapter.handle(agentCode, requestId, gigachatSessionId, aclRequest);
    }
}
