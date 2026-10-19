package ru.bank.admin.at.core;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * Логин через UI один раз на прогон, дальше каждый тест стартует с сохранённой сессией
 * (storageState). Файл содержит куки — он лежит в target/ и не попадает в git.
 * Если тесты параллельно меняют настройки пользователя — заведите по учётке на поток.
 */
public final class AuthState {

    private static final Path FILE = Paths.get("target", ".auth", "admin.json");
    /** Сессия админки живёт дольше; берём с запасом, чтобы не поймать протухший токен посреди прогона. */
    private static final Duration MAX_AGE = Duration.ofMinutes(30);

    private AuthState() {
    }

    public static synchronized Path ensure(Browser browser) {
        if (isFresh()) {
            return FILE;
        }
        try (BrowserContext context = browser.newContext(
                new Browser.NewContextOptions().setBaseURL(Config.baseUrl()))) {
            Page page = context.newPage();
            page.navigate("/login");
            page.getByLabel("Логин").fill(Config.adminLogin());
            page.getByLabel("Пароль").fill(Config.adminPassword());
            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Войти")).click();
            // Ждём признак залогиненного состояния, а не паузу
            assertThat(page.getByRole(AriaRole.NAVIGATION)).isVisible();
            Files.createDirectories(FILE.getParent());
            context.storageState(new BrowserContext.StorageStateOptions().setPath(FILE));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return FILE;
    }

    private static boolean isFresh() {
        try {
            return Files.exists(FILE)
                    && Files.getLastModifiedTime(FILE).toInstant().isAfter(Instant.now().minus(MAX_AGE));
        } catch (IOException e) {
            return false;
        }
    }
}
