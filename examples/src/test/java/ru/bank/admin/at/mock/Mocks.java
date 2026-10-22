package ru.bank.admin.at.mock;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;
import ru.bank.admin.at.config.Cfg;
import ru.bank.admin.at.core.Attach;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Стабы одного теста. Помнит, что создал, и в конце теста удаляет только своё.
 * При падении прикладывает в Allure все запросы, которые система отправила по ключам теста:
 * сразу видно, ушёл ли запрос во внешнюю систему и с какими данными.
 *
 * Подключение: @ExtendWith(Mocks.Extension.class), параметр теста Mocks mocks.
 */
public final class Mocks {

    private static volatile MockServer server;

    private final MockServer mockServer;
    private final Deque<String> createdStubIds = new ArrayDeque<>();
    private final Map<String, String> keysByPath = new LinkedHashMap<>();

    private Mocks(MockServer mockServer) {
        this.mockServer = mockServer;
    }

    public void stub(Stub stub) {
        createdStubIds.push(mockServer.register(stub));
        keysByPath.put(stub.path(), stub.correlationKey());
    }

    public List<RecordedRequest> requests(String path, String correlationKey) {
        return mockServer.requests(path, correlationKey);
    }

    /**
     * Ждёт, пока система отправит во внешнюю систему ровно один запрос с ключом теста, и возвращает его.
     * Ожидание по условию, а не пауза: запрос может уйти асинхронно после ответа UI.
     */
    public RecordedRequest awaitSingleRequest(String path, String correlationKey) {
        List<RecordedRequest> found = new ArrayList<>();
        await("запрос " + path + " с ключом " + correlationKey)
                .atMost(Duration.ofSeconds(20))
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> {
                    found.clear();
                    found.addAll(requests(path, correlationKey));
                    assertThat(found).hasSize(1);
                });
        return found.get(0);
    }

    private void cleanup() {
        while (!createdStubIds.isEmpty()) {
            String id = createdStubIds.pop();
            try {
                mockServer.remove(id);
            } catch (RuntimeException e) {
                Attach.text("Не удалось удалить стаб " + id, e.toString());
            }
        }
    }

    private String journal() {
        StringBuilder sb = new StringBuilder();
        keysByPath.forEach((path, key) -> {
            sb.append("=== ").append(path).append(" | ключ ").append(key).append('\n');
            try {
                requests(path, key).forEach(r -> sb.append(r).append("\n\n"));
            } catch (RuntimeException e) {
                sb.append("журнал недоступен: ").append(e).append('\n');
            }
        });
        return sb.toString();
    }

    private static MockServer server() {
        if (server == null) {
            synchronized (Mocks.class) {
                if (server == null) {
                    server = new SberMockClient(Cfg.required(Cfg.get().mockAdminUrl(), "mock.admin.url"));
                }
            }
        }
        return server;
    }

    public static final class Extension implements AfterEachCallback, ParameterResolver {

        private static final ExtensionContext.Namespace NS = ExtensionContext.Namespace.create(Mocks.class);

        @Override
        public void afterEach(ExtensionContext ctx) {
            Mocks mocks = ctx.getStore(NS).remove(Mocks.class, Mocks.class);
            if (mocks == null) {
                return;
            }
            try {
                if (ctx.getExecutionException().isPresent()) {
                    Attach.text("Журнал мок-сервера по ключам теста", mocks.journal());
                }
            } finally {
                mocks.cleanup(); // никогда не reset(): мок-сервер общий
            }
        }

        @Override
        public boolean supportsParameter(ParameterContext param, ExtensionContext ctx) {
            return param.getParameter().getType() == Mocks.class;
        }

        @Override
        public Object resolveParameter(ParameterContext param, ExtensionContext ctx) {
            return ctx.getStore(NS).getOrComputeIfAbsent(Mocks.class, key -> new Mocks(server()), Mocks.class);
        }
    }
}
