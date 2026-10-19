package ru.bank.admin.at.core;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Реестр уборки за одним тестом. Правило: зарегистрировал уборку СРАЗУ после создания
 * сущности, а не в конце теста. Шаги выполняются в обратном порядке (дети раньше родителей),
 * даже если тест упал. Ошибка уборки не скрывает ошибку теста, а уходит в отчёт.
 *
 * Подключение: @ExtendWith({PlaywrightExtension.class, Cleanup.Extension.class}) — именно в таком
 * порядке, тогда уборка через API браузерного контекста отработает до его закрытия.
 */
public final class Cleanup {

    private final Deque<Step> steps = new ArrayDeque<>();

    /** Описание обязательно: оно попадёт в отчёт, если уборка не удалась. */
    public void add(String description, Runnable action) {
        steps.push(new Step(description, action));
    }

    private List<String> runAll() {
        List<String> failures = new ArrayList<>();
        while (!steps.isEmpty()) {
            Step step = steps.pop();
            try {
                step.action().run();
            } catch (RuntimeException e) {
                failures.add(step.description() + ": " + e);
            }
        }
        return failures;
    }

    private record Step(String description, Runnable action) {
    }

    public static final class Extension implements AfterEachCallback, ParameterResolver {

        private static final ExtensionContext.Namespace NS = ExtensionContext.Namespace.create(Cleanup.class);

        @Override
        public void afterEach(ExtensionContext ctx) {
            Cleanup cleanup = ctx.getStore(NS).remove(Cleanup.class, Cleanup.class);
            if (cleanup == null) {
                return;
            }
            List<String> failures = cleanup.runAll();
            if (!failures.isEmpty()) {
                Attach.text("Уборка не удалась", String.join("\n", failures));
            }
        }

        @Override
        public boolean supportsParameter(ParameterContext param, ExtensionContext ctx) {
            return param.getParameter().getType() == Cleanup.class;
        }

        @Override
        public Object resolveParameter(ParameterContext param, ExtensionContext ctx) {
            // JUnit 6: getOrComputeIfAbsent помечен deprecated, используйте computeIfAbsent
            return ctx.getStore(NS).getOrComputeIfAbsent(Cleanup.class, key -> new Cleanup(), Cleanup.class);
        }
    }
}
