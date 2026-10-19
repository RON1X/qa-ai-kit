package ru.bank.admin.at.core;

import io.qameta.allure.Allure;
import io.qameta.allure.AttachmentOptions;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Все вложения в Allure — только через этот класс, чтобы смена версии Allure
 * затрагивала одно место. Код написан под Allure 3.x; строки для 2.x — в комментариях.
 */
public final class Attach {

    private Attach() {
    }

    public static void text(String name, String content) {
        Allure.attachment(name, content); // одинаково в 2.x и 3.x
    }

    public static void png(String name, byte[] bytes) {
        Allure.attachment(name, "image/png", new ByteArrayInputStream(bytes),
                AttachmentOptions.withFileExtension("png"));
        // Allure 2.x: Allure.addAttachment(name, "image/png", new ByteArrayInputStream(bytes), "png");
    }

    public static void file(String name, String mimeType, String extension, Path path) {
        try {
            byte[] bytes = Files.readAllBytes(path);
            Allure.attachment(name, mimeType, new ByteArrayInputStream(bytes),
                    AttachmentOptions.withFileExtension(extension));
            // Allure 2.x: Allure.addAttachment(name, mimeType, new ByteArrayInputStream(bytes), extension);
        } catch (IOException e) {
            throw new UncheckedIOException("Не удалось приложить файл " + path, e);
        }
    }
}
