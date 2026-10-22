package ru.bank.admin.at.core;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Tracing;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;
import ru.bank.admin.at.config.Cfg;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Браузерная фикстура для UI-тестов:
 * - Playwright и Browser — по одному на поток (Playwright Java не потокобезопасен);
 * - BrowserContext и Page — новые на каждый тест (изоляция кук, storage, вкладок);
 * - трасса пишется всегда, а в Allure попадает только при падении (вместе со скриншотом).
 */
public final class PlaywrightExtension
        implements BeforeEachCallback, AfterTestExecutionCallback, AfterEachCallback, ParameterResolver {

    /** Атрибут, о котором договорились с фронтом. Без настройки getByTestId ищет data-testid. */
    private static final String TEST_ID_ATTRIBUTE = "data-qa";

    private static final ThreadLocal<Playwright> PLAYWRIGHT = ThreadLocal.withInitial(() -> {
        Playwright playwright = Playwright.create();
        playwright.selectors().setTestIdAttribute(TEST_ID_ATTRIBUTE);
        Runtime.getRuntime().addShutdownHook(new Thread(playwright::close));
        return playwright;
    });

    private static final ThreadLocal<Browser> BROWSER = ThreadLocal.withInitial(() ->
            PLAYWRIGHT.get().chromium().launch(new BrowserType.LaunchOptions().setHeadless(Cfg.get().headless())));

    private static final ExtensionContext.Namespace NS = ExtensionContext.Namespace.create(PlaywrightExtension.class);

    /** Для API-тестов и утилит, которым нужен Playwright текущего потока. */
    public static Playwright playwright() {
        return PLAYWRIGHT.get();
    }

    public static Browser browser() {
        return BROWSER.get();
    }

    @Override
    public void beforeEach(ExtensionContext ctx) {
        BrowserContext context = browser().newContext(new Browser.NewContextOptions()
                .setBaseURL(Cfg.get().baseUrl())
                .setStorageStatePath(AuthState.ensure(browser()))
                .setLocale(Cfg.get().locale())
                .setViewportSize(1600, 900));
        context.tracing().start(new Tracing.StartOptions()
                .setTitle(ctx.getDisplayName())
                .setScreenshots(true)
                .setSnapshots(true)
                .setSources(true));
        ctx.getStore(NS).put(BrowserContext.class, context);
        ctx.getStore(NS).put(Page.class, context.newPage());
    }

    /** Выполняется до @AfterEach теста: трасса фиксирует состояние ровно в момент падения. */
    @Override
    public void afterTestExecution(ExtensionContext ctx) {
        BrowserContext context = ctx.getStore(NS).get(BrowserContext.class, BrowserContext.class);
        Page page = ctx.getStore(NS).get(Page.class, Page.class);
        if (context == null) {
            return;
        }
        if (ctx.getExecutionException().isPresent()) {
            if (page != null && !page.isClosed()) {
                Attach.png("Скриншот в момент падения", page.screenshot(new Page.ScreenshotOptions().setFullPage(true)));
            }
            Path trace = Paths.get("target", "traces",
                    ctx.getRequiredTestClass().getSimpleName() + "." + ctx.getRequiredTestMethod().getName() + ".zip");
            context.tracing().stop(new Tracing.StopOptions().setPath(trace));
            Attach.file("Playwright trace (открыть: playwright show-trace)", "application/zip", "zip", trace);
        } else {
            context.tracing().stop();
        }
    }

    @Override
    public void afterEach(ExtensionContext ctx) {
        BrowserContext context = ctx.getStore(NS).remove(BrowserContext.class, BrowserContext.class);
        if (context != null) {
            context.close();
        }
    }

    @Override
    public boolean supportsParameter(ParameterContext param, ExtensionContext ctx) {
        Class<?> type = param.getParameter().getType();
        return type == Page.class || type == BrowserContext.class;
    }

    @Override
    public Object resolveParameter(ParameterContext param, ExtensionContext ctx) {
        return ctx.getStore(NS).get(param.getParameter().getType());
    }
}
