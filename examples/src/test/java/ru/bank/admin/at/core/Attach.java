package ru.bank.admin.at.core;

import io.qameta.allure.Allure;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Все вложения в Allure — только через этот класс: смена версии Allure затронет одно место.
 * Код для Allure 2.x. В Allure 3.x addAttachment удалён: используйте
 * Allure.attachment(name, type, stream, AttachmentOptions.withFileExtension(ext)).
 */
public final class Attach {

    private Attach() {
    }

    public static void text(String name, String content) {
        Allure.addAttachment(name, "text/plain", content, "txt");
    }

    public static void json(String name, String content) {
        Allure.addAttachment(name, "application/json", content, "json");
    }

    public static void png(String name, byte[] bytes) {
        Allure.addAttachment(name, "image/png", new ByteArrayInputStream(bytes), "png");
    }

    public static void file(String name, String mimeType, String extension, Path path) {
        try {
            Allure.addAttachment(name, mimeType, new ByteArrayInputStream(Files.readAllBytes(path)), extension);
        } catch (IOException e) {
            throw new UncheckedIOException("Не удалось приложить файл " + path, e);
        }
    }
}
