package ru.bank.admin.at.api;

import com.microsoft.playwright.APIRequest;
import com.microsoft.playwright.APIRequestContext;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;
import ru.bank.admin.at.config.Cfg;
import ru.bank.admin.at.core.AuthState;
import ru.bank.admin.at.core.PlaywrightExtension;

/**
 * Фикстура для API-тестов: новый APIRequestContext на тест, с baseURL из конфига и сессией из {@link AuthState}.
 * Браузер нужен только один раз на поток — чтобы получить куки входа.
 *
 * [ЗАПОЛНИТЬ] Если API авторизуется токеном, а не кукой: замените setStorageStatePath на
 * setExtraHTTPHeaders(Map.of("Authorization", "Bearer " + token)), токен получайте отдельным запросом.
 */
public final class ApiExtension implements AfterEachCallback, ParameterResolver {

    private static final ExtensionContext.Namespace NS = ExtensionContext.Namespace.create(ApiExtension.class);

    @Override
    public boolean supportsParameter(ParameterContext param, ExtensionContext ctx) {
        return param.getParameter().getType() == APIRequestContext.class;
    }

    @Override
    public Object resolveParameter(ParameterContext param, ExtensionContext ctx) {
        return ctx.getStore(NS).getOrComputeIfAbsent(APIRequestContext.class, key ->
                PlaywrightExtension.playwright().request().newContext(new APIRequest.NewContextOptions()
                        .setBaseURL(Cfg.get().apiUrl())
                        .setStorageStatePath(AuthState.ensure(PlaywrightExtension.browser()))
                        .setIgnoreHTTPSErrors(true)), APIRequestContext.class);
    }

    @Override
    public void afterEach(ExtensionContext ctx) {
        APIRequestContext request = ctx.getStore(NS).remove(APIRequestContext.class, APIRequestContext.class);
        if (request != null) {
            request.dispose();
        }
    }
}
