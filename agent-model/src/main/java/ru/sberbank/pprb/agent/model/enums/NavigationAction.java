package ru.sberbank.pprb.agent.model.enums;

import java.util.Arrays;
import java.util.Optional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Управление представлением текущего шага вне каталога бизнес-операций. */
@Getter
@RequiredArgsConstructor
public enum NavigationAction {
    RESUME("resume_operation"),
    RESET("reset_operation");

    private final String actionCode;

    /** Разрешает только фиксированные коды навигации. */
    public static Optional<NavigationAction> resolve(String code) {
        return Arrays.stream(values()).filter(action -> action.actionCode.equals(code)).findFirst();
    }
}
