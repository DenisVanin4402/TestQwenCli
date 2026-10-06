package ru.sberbank.pprb.agent.common.idempotency;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Отмечает методы, повторное выполнение которых должна будет контролировать библиотека
 * пользователя. Пока библиотека не подключена, аннотация только описывает ключ вызова: она не
 * сохраняет ответы и не препятствует повторной обработке одинакового HTTP-запроса.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Idempotent {
    /**
     * Имя метода для различения операций в библиотеке; задаётся отдельно от ключа конкретного
     * вызова.
     */
    String signature();

    /** Нужно ли будущей библиотеке отвергать разные данные, переданные с одним ключом вызова. */
    boolean needCheckHash();

    /**
     * Положение отдельного String-аргумента с ключом повторной обработки, начиная с нуля. DTO с
     * данными сообщения не является строковым ключом; например, в методе (input, idempotencyKey)
     * индекс равен 1.
     */
    int keyParameterIndex();
}
