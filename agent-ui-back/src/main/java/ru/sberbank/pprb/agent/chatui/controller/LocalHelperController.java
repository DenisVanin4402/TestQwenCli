package ru.sberbank.pprb.agent.chatui.controller;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import ru.sberbank.pprb.agent.chatui.generated.api.LocalHelperApi;
import ru.sberbank.pprb.agent.chatui.generated.model.FixtureList;
import ru.sberbank.pprb.agent.chatui.mapper.FixtureMapper;
import ru.sberbank.pprb.agent.chatui.service.FixtureCatalog;
import ru.sberbank.pprb.agent.gigaassistant.mapper.AclMapper;
import ru.sberbank.pprb.agent.model.enums.SessionState;
import ru.sberbank.pprb.agent.service.turn.SessionResponseRenderer;

/** Единственный вспомогательный API чата: загрузка синтетического платежа и начальных саджестов. */
@RestController
@Profile("poc-local")
public class LocalHelperController implements LocalHelperApi {
    private final FixtureCatalog catalog;
    private final FixtureMapper mapper;
    private final AclMapper aclMapper;
    private final SessionResponseRenderer renderer;
    private final String agentCode;
    private final String mode;

    /** Настройки интеграции остаются на сервере; UI получает только код агента и режим. */
    public LocalHelperController(
            FixtureCatalog catalog,
            FixtureMapper mapper,
            AclMapper aclMapper,
            SessionResponseRenderer renderer,
            @Value("${poc.acl.agent-code}") String agentCode,
            @Value("${integrations.invest-corr.stub}") String stubMode) {
        this.catalog = catalog;
        this.mapper = mapper;
        this.aclMapper = aclMapper;
        this.renderer = renderer;
        this.agentCode = agentCode;
        this.mode = "enabled".equals(stubMode) ? "stub" : "real";
    }

    /** Чтение каталога не создаёт сессию и не выполняет операцию. */
    @Override
    public ResponseEntity<FixtureList> getFixtures() {
        return ResponseEntity.ok(
                new FixtureList()
                        .agentCode(agentCode)
                        .integrationMode(mode)
                        .suggestions(
                                mapper.toSuggestions(
                                        aclMapper.toSuggestions(
                                                renderer.operationSuggestions(
                                                        SessionState.CHOOSING_REQUEST_TYPE))))
                        .fixtures(List.of(mapper.toFixture(catalog.payment(), "payment-42"))));
    }
}
