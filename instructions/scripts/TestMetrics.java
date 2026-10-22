import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Метрики проекта автотестов. Один файл, без зависимостей, Java 11+:
 *
 *   java scripts/TestMetrics.java [--src src/test/java] [--cases test-cases]
 *                                 [--allure target/allure-results] [--out target/metrics.md]
 *
 * --src     статический разбор исходников: тесты, слои (UI/API/DB), критичность (@Severity),
 *           фичи (@Epic/@Feature), отключённые, параметризованные, связь с кейсами (@TmsLink), техдолг;
 * --cases   ручные кейсы TC-*.md: сколько их, какой приоритет, сколько покрыто автотестами;
 * --allure  результаты прогона (allure-results): статусы, флаки, перезапуски, длительность, падения.
 * Без --out отчёт печатается в консоль. Числа в отчёте — только то, что посчитано здесь.
 */
public class TestMetrics {

    private static final Set<String> TEST_ANNOTATIONS =
            Set.of("Test", "ParameterizedTest", "RepeatedTest", "TestFactory", "TestTemplate");
    private static final List<String> SEVERITY_ORDER =
            List.of("BLOCKER", "CRITICAL", "NORMAL", "MINOR", "TRIVIAL", "не указана");

    public static void main(String[] args) throws IOException {
        // Отчёт всегда в UTF-8: его читают человек, агент и git. В консоли Windows удобнее --out файл.
        System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8));
        Map<String, String> opts = parseArgs(args);
        StringBuilder md = new StringBuilder("# Метрики автотестов\n\n");
        Map<String, List<TestInfo>> testsByLink = new TreeMap<>();

        Path src = Paths.get(opts.getOrDefault("src", "src/test/java"));
        if (Files.isDirectory(src)) {
            List<TestInfo> tests = scanSources(src);
            for (TestInfo t : tests) {
                for (String link : t.tmsLinks) {
                    testsByLink.computeIfAbsent(link, k -> new ArrayList<>()).add(t);
                }
            }
            reportSources(md, tests, src);
        } else {
            md.append("_Исходники не найдены: ").append(src).append("_\n\n");
        }

        if (opts.containsKey("cases")) {
            reportCases(md, Paths.get(opts.get("cases")), testsByLink);
        }
        if (opts.containsKey("allure")) {
            reportAllure(md, Paths.get(opts.get("allure")));
        }

        if (opts.containsKey("out")) {
            Path out = Paths.get(opts.get("out"));
            if (out.getParent() != null) {
                Files.createDirectories(out.getParent());
            }
            Files.writeString(out, md.toString(), StandardCharsets.UTF_8);
            System.out.println("Отчёт: " + out.toAbsolutePath());
        } else {
            System.out.print(md);
        }
    }

    // ------------------------------------------------------------------ исходники

    static final class TestInfo {
        String file;
        String className;
        String method;
        String kind;
        String layer = "?";
        String severity = "не указана";
        String epic = "";
        String feature = "";
        final List<String> tags = new ArrayList<>();
        final List<String> tmsLinks = new ArrayList<>();
        boolean disabled;
        int cases = 1;
        boolean casesUnknown;
    }

    static final class Annotation {
        final String name;
        final String args;

        Annotation(String name, String args) {
            this.name = name;
            this.args = args;
        }
    }

    static List<TestInfo> scanSources(Path root) throws IOException {
        List<TestInfo> result = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).sorted().collect(Collectors.toList())) {
                result.addAll(scanFile(root, file));
            }
        }
        return result;
    }

    static List<TestInfo> scanFile(Path root, Path file) throws IOException {
        String text = stripComments(Files.readString(file, StandardCharsets.UTF_8));
        String rel = root.relativize(file).toString().replace('\\', '/');
        List<TestInfo> tests = new ArrayList<>();
        List<Annotation> pending = new ArrayList<>();
        List<Annotation> classAnnotations = new ArrayList<>();
        String className = file.getFileName().toString().replace(".java", "");
        int i = 0;
        int n = text.length();
        while (i < n) {
            char c = text.charAt(i);
            if (c == '"' || c == '\'') {
                i = skipLiteral(text, i);
                continue;
            }
            if (c == '@' && i + 1 < n && Character.isJavaIdentifierStart(text.charAt(i + 1))) {
                int j = i + 1;
                while (j < n && (Character.isJavaIdentifierPart(text.charAt(j)) || text.charAt(j) == '.')) {
                    j++;
                }
                String name = text.substring(i + 1, j);
                name = name.substring(name.lastIndexOf('.') + 1);
                int k = skipSpaces(text, j);
                String annArgs = "";
                if (k < n && text.charAt(k) == '(') {
                    int end = matchParen(text, k);
                    annArgs = text.substring(k + 1, end);
                    j = end + 1;
                }
                if (!name.equals("interface")) {
                    pending.add(new Annotation(name, annArgs));
                }
                i = j;
                continue;
            }
            if (!pending.isEmpty() && Character.isJavaIdentifierStart(c)) {
                int end = i;
                while (end < n && "({;=".indexOf(text.charAt(end)) < 0) {
                    end++;
                }
                String decl = text.substring(i, Math.min(end, n));
                Matcher cls = Pattern.compile("\\b(class|interface|record|enum)\\s+(\\w+)").matcher(decl);
                Matcher method = Pattern.compile("\\bvoid\\s+(\\w+)\\s*$").matcher(decl.trim());
                if (cls.find()) {
                    className = cls.group(2);
                    classAnnotations = new ArrayList<>(pending);
                } else if (method.find() && pending.stream().anyMatch(a -> TEST_ANNOTATIONS.contains(a.name))) {
                    tests.add(toTest(rel, className, method.group(1), pending, classAnnotations));
                }
                pending.clear();
                i = Math.max(end, i + 1);
                continue;
            }
            if (c == ';' || c == '{' || c == '}') {
                pending.clear();
            }
            i++;
        }
        return tests;
    }

    static TestInfo toTest(String file, String className, String method, List<Annotation> own, List<Annotation> cls) {
        TestInfo t = new TestInfo();
        t.file = file;
        t.className = className;
        t.method = method;
        List<Annotation> all = new ArrayList<>(cls);
        all.addAll(own);
        for (Annotation a : all) {
            switch (a.name) {
                case "Test": case "ParameterizedTest": case "RepeatedTest": case "TestFactory": case "TestTemplate":
                    t.kind = a.name;
                    break;
                case "Severity":
                    t.severity = lastToken(a.args).toUpperCase(Locale.ROOT);
                    break;
                case "Epic":
                    t.epic = firstString(a.args);
                    break;
                case "Feature":
                    t.feature = firstString(a.args);
                    break;
                case "Tag":
                    t.tags.add(firstString(a.args));
                    break;
                case "Tags":
                    Matcher m = Pattern.compile("@Tag\\(\\s*\"([^\"]*)\"").matcher(a.args);
                    while (m.find()) {
                        t.tags.add(m.group(1));
                    }
                    break;
                case "TmsLink":
                    t.tmsLinks.add(firstString(a.args));
                    break;
                case "Disabled": case "Ignore":
                    t.disabled = true;
                    break;
                default:
                    break;
            }
        }
        for (Annotation a : own) {
            switch (a.name) {
                case "CsvSource": t.cases = countCsvRows(a.args); break;
                case "ValueSource": t.cases = countValues(a.args); break;
                case "MethodSource": case "EnumSource": case "ArgumentsSource": case "CsvFileSource": case "FieldSource":
                    t.casesUnknown = true;
                    break;
                default: break;
            }
        }
        t.layer = layerOf(t);
        return t;
    }

    static String layerOf(TestInfo t) {
        for (String tag : t.tags) {
            String s = tag.toLowerCase(Locale.ROOT);
            if (List.of("ui", "api", "db", "e2e", "integration", "unit").contains(s)) {
                return s;
            }
        }
        for (String layer : List.of("ui", "api", "db", "e2e")) {
            if (t.file.contains("/" + layer + "/")) {
                return layer;
            }
        }
        return "?";
    }

    static void reportSources(StringBuilder md, List<TestInfo> tests, Path src) throws IOException {
        long methods = tests.size();
        long disabled = tests.stream().filter(t -> t.disabled).count();
        long parameterized = tests.stream().filter(t -> "ParameterizedTest".equals(t.kind)).count();
        long knownCases = tests.stream().filter(t -> !t.casesUnknown).mapToLong(t -> t.cases).sum();
        long unknownCases = tests.stream().filter(t -> t.casesUnknown).count();
        long classes = tests.stream().map(t -> t.file).distinct().count();

        md.append("## Автотесты в исходниках (").append(src).append(")\n\n")
                .append("| Показатель | Значение |\n|---|---|\n")
                .append("| Тестовых методов | ").append(methods).append(" |\n")
                .append("| Тестовых классов | ").append(classes).append(" |\n")
                .append("| Параметризованных методов | ").append(parameterized).append(" |\n")
                .append("| Тест-случаев (с раскрытием @CsvSource/@ValueSource) | ").append(knownCases)
                .append(unknownCases > 0 ? " + ещё " + unknownCases + " метод(ов) с @MethodSource/@EnumSource: число случаев видно только после прогона" : "")
                .append(" |\n")
                .append("| Отключено (@Disabled) | ").append(disabled).append(" |\n\n");

        table(md, "По слою (тег @Tag или пакет)", "Слой", count(tests, t -> t.layer), methods);
        Map<String, Long> severity = new LinkedHashMap<>();
        for (String s : SEVERITY_ORDER) {
            long c = tests.stream().filter(t -> t.severity.equals(s)).count();
            if (c > 0) {
                severity.put(s, c);
            }
        }
        tests.stream().map(t -> t.severity).filter(s -> !SEVERITY_ORDER.contains(s)).distinct()
                .forEach(s -> severity.put(s, tests.stream().filter(t -> t.severity.equals(s)).count()));
        table(md, "По критичности (@Severity)", "Критичность", severity, methods);
        table(md, "По фичам (@Epic / @Feature)", "Эпик / фича",
                count(tests, t -> (t.epic.isEmpty() ? "—" : t.epic) + " / " + (t.feature.isEmpty() ? "—" : t.feature)), methods);

        md.append("### Качество разметки\n\n| Показатель | Значение |\n|---|---|\n")
                .append("| Без @Severity | ").append(tests.stream().filter(t -> t.severity.equals("не указана")).count()).append(" |\n")
                .append("| Без @TmsLink (нет связи с ручным кейсом) | ").append(tests.stream().filter(t -> t.tmsLinks.isEmpty()).count()).append(" |\n")
                .append("| Без слоя (нет тега ui/api/db и пакета) | ").append(tests.stream().filter(t -> t.layer.equals("?")).count()).append(" |\n\n");

        Map<String, Integer> debt = new TreeMap<>();
        Pattern sleep = Pattern.compile("Thread\\.sleep|waitForTimeout|LoadState\\.NETWORKIDLE");
        Pattern todoLocator = Pattern.compile("TODO\\(locator\\)");
        Pattern xpath = Pattern.compile("\"(//|xpath=)");
        int sleeps = 0;
        int todos = 0;
        int xpaths = 0;
        try (Stream<Path> files = Files.walk(src)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList())) {
                String text = Files.readString(f, StandardCharsets.UTF_8);
                int s = countMatches(sleep, text);
                int x = countMatches(xpath, text);
                sleeps += s;
                xpaths += x;
                todos += countMatches(todoLocator, text);
                if (s > 0) {
                    debt.put(src.relativize(f).toString().replace('\\', '/'), s);
                }
            }
        }
        md.append("### Техдолг\n\n| Показатель | Значение |\n|---|---|\n")
                .append("| Паузы (Thread.sleep / waitForTimeout / NETWORKIDLE) | ").append(sleeps).append(" |\n")
                .append("| XPath-локаторы | ").append(xpaths).append(" |\n")
                .append("| TODO(locator) | ").append(todos).append(" |\n\n");
        if (!debt.isEmpty()) {
            md.append("Файлы с паузами: ").append(debt.entrySet().stream()
                    .map(e -> "`" + e.getKey() + "` (" + e.getValue() + ")").collect(Collectors.joining(", "))).append("\n\n");
        }
        List<TestInfo> off = tests.stream().filter(t -> t.disabled).collect(Collectors.toList());
        if (!off.isEmpty()) {
            md.append("Отключённые тесты: ").append(off.stream().map(t -> "`" + t.className + "#" + t.method + "`")
                    .collect(Collectors.joining(", "))).append("\n\n");
        }
    }

    // ------------------------------------------------------------------ ручные кейсы

    static void reportCases(StringBuilder md, Path root, Map<String, List<TestInfo>> testsByLink) throws IOException {
        md.append("## Ручные кейсы и покрытие автотестами (").append(root).append(")\n\n");
        if (!Files.isDirectory(root)) {
            md.append("_Папка не найдена_\n\n");
            return;
        }
        Pattern id = Pattern.compile("^(TC-\\d+)");
        Pattern priority = Pattern.compile("\\*\\*Приоритет\\*\\*\\s*\\|\\s*([^|\\n]+)|Приоритет\\s*[:|]\\s*([^|\\n]+)");
        Map<String, String> priorityByCase = new TreeMap<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path f : files.filter(p -> p.getFileName().toString().matches("TC-\\d+.*\\.md")).sorted().collect(Collectors.toList())) {
                Matcher m = id.matcher(f.getFileName().toString());
                if (!m.find()) {
                    continue;
                }
                Path parent = root.relativize(f).getParent();
                String key = (parent == null ? "" : parent.toString().replace('\\', '/') + "/") + m.group(1);
                Matcher p = priority.matcher(Files.readString(f, StandardCharsets.UTF_8));
                String prio = p.find() ? (p.group(1) != null ? p.group(1) : p.group(2)).trim() : "не указан";
                priorityByCase.put(key, prio);
            }
        }
        long total = priorityByCase.size();
        long covered = priorityByCase.keySet().stream().filter(testsByLink::containsKey).count();
        md.append("| Показатель | Значение |\n|---|---|\n")
                .append("| Ручных кейсов | ").append(total).append(" |\n")
                .append("| Покрыто автотестами (есть @TmsLink на кейс) | ").append(covered)
                .append(total > 0 ? String.format(Locale.ROOT, " (%.0f%%)", 100.0 * covered / total) : "").append(" |\n\n");

        Map<String, long[]> byPriority = new TreeMap<>();
        priorityByCase.forEach((k, prio) -> {
            long[] v = byPriority.computeIfAbsent(prio, x -> new long[2]);
            v[0]++;
            if (testsByLink.containsKey(k)) {
                v[1]++;
            }
        });
        md.append("| Приоритет | Кейсов | Автоматизировано | Покрытие |\n|---|---|---|---|\n");
        byPriority.forEach((p, v) -> md.append("| ").append(p).append(" | ").append(v[0]).append(" | ").append(v[1])
                .append(" | ").append(String.format(Locale.ROOT, "%.0f%%", 100.0 * v[1] / v[0])).append(" |\n"));
        md.append('\n');

        Set<String> orphan = new HashSet<>(testsByLink.keySet());
        orphan.removeAll(priorityByCase.keySet());
        if (!orphan.isEmpty()) {
            md.append("@TmsLink без кейса в папке (опечатка в ссылке или кейс удалён): ")
                    .append(orphan.stream().sorted().map(s -> "`" + s + "`").collect(Collectors.joining(", "))).append("\n\n");
        }
        List<String> notCovered = priorityByCase.entrySet().stream()
                .filter(e -> !testsByLink.containsKey(e.getKey()))
                .filter(e -> e.getValue().toLowerCase(Locale.ROOT).startsWith("выс") || e.getValue().equalsIgnoreCase("high"))
                .map(Map.Entry::getKey).collect(Collectors.toList());
        if (!notCovered.isEmpty()) {
            md.append("Не автоматизированы кейсы высокого приоритета: ")
                    .append(notCovered.stream().map(s -> "`" + s + "`").collect(Collectors.joining(", "))).append("\n\n");
        }
    }

    // ------------------------------------------------------------------ результаты прогона

    static final class RunResult {
        String name;
        String historyId;
        String status;
        String severity = "не указана";
        String message = "";
        boolean flakyFlag;
        long start;
        long stop;
    }

    @SuppressWarnings("unchecked")
    static void reportAllure(StringBuilder md, Path dir) throws IOException {
        md.append("## Прогон (").append(dir).append(")\n\n");
        if (!Files.isDirectory(dir)) {
            md.append("_Папка не найдена_\n\n");
            return;
        }
        List<RunResult> all = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path f : files.filter(p -> p.getFileName().toString().endsWith("-result.json")).collect(Collectors.toList())) {
                Map<String, Object> json = (Map<String, Object>) new JsonReader(Files.readString(f, StandardCharsets.UTF_8)).read();
                RunResult r = new RunResult();
                r.name = String.valueOf(json.getOrDefault("fullName", json.get("name")));
                r.historyId = String.valueOf(json.getOrDefault("historyId", r.name));
                r.status = String.valueOf(json.getOrDefault("status", "unknown"));
                r.start = toLong(json.get("start"));
                r.stop = toLong(json.get("stop"));
                Object details = json.get("statusDetails");
                if (details instanceof Map) {
                    Map<String, Object> d = (Map<String, Object>) details;
                    r.flakyFlag = Boolean.TRUE.equals(d.get("flaky"));
                    Object msg = d.get("message");
                    if (msg != null) {
                        r.message = msg.toString().lines().findFirst().orElse("");
                    }
                }
                Object labels = json.get("labels");
                if (labels instanceof List) {
                    for (Object o : (List<Object>) labels) {
                        Map<String, Object> l = (Map<String, Object>) o;
                        if ("severity".equals(l.get("name"))) {
                            r.severity = String.valueOf(l.get("value")).toUpperCase(Locale.ROOT);
                        }
                    }
                }
                all.add(r);
            }
        }
        if (all.isEmpty()) {
            md.append("_Нет файлов *-result.json_\n\n");
            return;
        }
        Map<String, List<RunResult>> byHistory = all.stream().collect(Collectors.groupingBy(r -> r.historyId, TreeMap::new, Collectors.toList()));
        List<RunResult> latest = new ArrayList<>();
        int retried = 0;
        List<String> flaky = new ArrayList<>();
        for (List<RunResult> runs : byHistory.values()) {
            runs.sort(Comparator.comparingLong(r -> r.stop));
            RunResult last = runs.get(runs.size() - 1);
            latest.add(last);
            if (runs.size() > 1) {
                retried++;
            }
            boolean passedSometimes = runs.stream().anyMatch(r -> r.status.equals("passed"));
            boolean failedSometimes = runs.stream().anyMatch(r -> r.status.equals("failed") || r.status.equals("broken"));
            if (last.flakyFlag || (passedSometimes && failedSometimes)) {
                flaky.add(last.name);
            }
        }
        Map<String, Long> statuses = count(latest, r -> r.status);
        long total = latest.size();
        long passed = statuses.getOrDefault("passed", 0L);
        long executed = total - statuses.getOrDefault("skipped", 0L);
        md.append("| Показатель | Значение |\n|---|---|\n")
                .append("| Тестов (уникальных historyId) | ").append(total).append(" |\n")
                .append("| Файлов результатов (с перезапусками) | ").append(all.size()).append(" |\n")
                .append("| Pass rate (passed / выполненные) | ")
                .append(executed > 0 ? String.format(Locale.ROOT, "%.1f%%", 100.0 * passed / executed) : "—").append(" |\n")
                .append("| Перезапускались | ").append(retried).append(" |\n")
                .append("| Флаки (метка flaky или разный исход в этом прогоне) | ").append(flaky.size()).append(" |\n\n");
        table(md, "Статусы (последний запуск каждого теста)", "Статус", statuses, total);

        Map<String, long[]> sev = new LinkedHashMap<>();
        for (RunResult r : latest) {
            long[] v = sev.computeIfAbsent(r.severity, k -> new long[2]);
            v[0]++;
            if (r.status.equals("passed")) {
                v[1]++;
            }
        }
        md.append("### По критичности\n\n| Критичность | Тестов | Прошло |\n|---|---|---|\n");
        sev.forEach((k, v) -> md.append("| ").append(k).append(" | ").append(v[0]).append(" | ").append(v[1]).append(" |\n"));
        md.append('\n');

        List<Long> durations = latest.stream().map(r -> Math.max(0, r.stop - r.start)).sorted().collect(Collectors.toList());
        long sum = durations.stream().mapToLong(Long::longValue).sum();
        md.append("### Длительность\n\n| Показатель | Значение |\n|---|---|\n")
                .append("| Сумма длительностей тестов | ").append(seconds(sum)).append(" |\n")
                .append("| Медиана | ").append(seconds(percentile(durations, 50))).append(" |\n")
                .append("| 90-й перцентиль | ").append(seconds(percentile(durations, 90))).append(" |\n")
                .append("| Максимум | ").append(seconds(durations.get(durations.size() - 1))).append(" |\n\n");
        md.append("Самые долгие:\n\n");
        latest.stream().sorted(Comparator.comparingLong((RunResult r) -> r.stop - r.start).reversed()).limit(5)
                .forEach(r -> md.append("- ").append(seconds(r.stop - r.start)).append(" — `").append(r.name).append("`\n"));
        md.append('\n');

        List<RunResult> failures = latest.stream().filter(r -> r.status.equals("failed") || r.status.equals("broken"))
                .collect(Collectors.toList());
        if (!failures.isEmpty()) {
            md.append("### Упавшие (").append(failures.size()).append(")\n\n");
            failures.stream().limit(20).forEach(r -> md.append("- `").append(r.name).append("` [").append(r.status)
                    .append("] ").append(r.message.length() > 160 ? r.message.substring(0, 160) + "…" : r.message).append('\n'));
            md.append('\n');
        }
        if (!flaky.isEmpty()) {
            md.append("### Флаки\n\n");
            flaky.forEach(f -> md.append("- `").append(f).append("`\n"));
            md.append('\n');
        }
    }

    // ------------------------------------------------------------------ утилиты

    static Map<String, String> parseArgs(String[] args) {
        Map<String, String> opts = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith("--") && i + 1 < args.length) {
                opts.put(args[i].substring(2), args[++i]);
            } else {
                System.err.println("Непонятный аргумент: " + args[i]);
                System.err.println("Использование: java TestMetrics.java [--src DIR] [--cases DIR] [--allure DIR] [--out FILE]");
                System.exit(2);
            }
        }
        return opts;
    }

    static <T> Map<String, Long> count(List<T> items, java.util.function.Function<T, String> key) {
        return items.stream().collect(Collectors.groupingBy(key, TreeMap::new, Collectors.counting()));
    }

    static void table(StringBuilder md, String title, String column, Map<String, Long> data, long total) {
        md.append("### ").append(title).append("\n\n| ").append(column).append(" | Тестов | Доля |\n|---|---|---|\n");
        data.forEach((k, v) -> md.append("| ").append(k).append(" | ").append(v).append(" | ")
                .append(total > 0 ? String.format(Locale.ROOT, "%.0f%%", 100.0 * v / total) : "—").append(" |\n"));
        md.append('\n');
    }

    static int countMatches(Pattern p, String text) {
        Matcher m = p.matcher(text);
        int c = 0;
        while (m.find()) {
            c++;
        }
        return c;
    }

    static int countCsvRows(String args) {
        int tb = args.indexOf("\"\"\"");
        if (tb >= 0) {
            int end = args.indexOf("\"\"\"", tb + 3);
            String block = args.substring(tb + 3, end < 0 ? args.length() : end);
            return (int) block.lines().map(String::trim).filter(l -> !l.isEmpty() && !l.startsWith("#")).count();
        }
        String values = args.replaceAll("(delimiterString|delimiter|nullValues|emptyValue|quoteCharacter)\\s*=\\s*(\"[^\"]*\"|'[^']*'|\\{[^}]*\\})", "");
        return Math.max(1, countMatches(Pattern.compile("\"(?:[^\"\\\\]|\\\\.)*\""), values));
    }

    static int countValues(String args) {
        int strings = countMatches(Pattern.compile("\"(?:[^\"\\\\]|\\\\.)*\""), args);
        if (strings > 0) {
            return strings;
        }
        Matcher m = Pattern.compile("\\{([^}]*)\\}").matcher(args);
        return m.find() ? (int) Stream.of(m.group(1).split(",")).filter(s -> !s.isBlank()).count() : 1;
    }

    static String firstString(String args) {
        Matcher m = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(args);
        return m.find() ? m.group(1) : args.trim();
    }

    static String lastToken(String args) {
        String s = args.trim();
        int dot = s.lastIndexOf('.');
        return dot >= 0 ? s.substring(dot + 1).trim() : s.replace("value", "").replace("=", "").trim();
    }

    static String stripComments(String s) {
        StringBuilder out = new StringBuilder(s.length());
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '"' || c == '\'') {
                int end = skipLiteral(s, i);
                out.append(s, i, end);
                i = end;
            } else if (s.startsWith("//", i)) {
                while (i < s.length() && s.charAt(i) != '\n') {
                    i++;
                }
            } else if (s.startsWith("/*", i)) {
                int end = s.indexOf("*/", i + 2);
                i = end < 0 ? s.length() : end + 2;
                out.append(' ');
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    static int skipLiteral(String s, int i) {
        if (s.startsWith("\"\"\"", i)) {
            int end = s.indexOf("\"\"\"", i + 3);
            return end < 0 ? s.length() : end + 3;
        }
        char q = s.charAt(i);
        int j = i + 1;
        while (j < s.length() && s.charAt(j) != q) {
            j += s.charAt(j) == '\\' ? 2 : 1;
        }
        return Math.min(j + 1, s.length());
    }

    static int skipSpaces(String s, int i) {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
            i++;
        }
        return i;
    }

    static int matchParen(String s, int open) {
        int depth = 0;
        int i = open;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '"' || c == '\'') {
                i = skipLiteral(s, i);
                continue;
            }
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
            i++;
        }
        return s.length() - 1;
    }

    static long toLong(Object o) {
        return o instanceof Number ? ((Number) o).longValue() : 0L;
    }

    static long percentile(List<Long> sorted, int p) {
        if (sorted.isEmpty()) {
            return 0;
        }
        int idx = (int) Math.ceil(p / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(idx, sorted.size() - 1)));
    }

    static String seconds(long ms) {
        return String.format(Locale.ROOT, "%.1f с", ms / 1000.0);
    }

    /** Минимальный JSON-парсер: объекты -> Map, массивы -> List, числа -> Long/Double. */
    static final class JsonReader {
        private final String s;
        private int i;

        JsonReader(String s) {
            this.s = s;
        }

        Object read() {
            skip();
            char c = s.charAt(i);
            if (c == '{') {
                Map<String, Object> map = new LinkedHashMap<>();
                i++;
                skip();
                if (s.charAt(i) == '}') {
                    i++;
                    return map;
                }
                while (true) {
                    skip();
                    String key = (String) read();
                    skip();
                    i++; // ':'
                    map.put(key, read());
                    skip();
                    if (s.charAt(i++) == '}') {
                        return map;
                    }
                }
            }
            if (c == '[') {
                List<Object> list = new ArrayList<>();
                i++;
                skip();
                if (s.charAt(i) == ']') {
                    i++;
                    return list;
                }
                while (true) {
                    list.add(read());
                    skip();
                    if (s.charAt(i++) == ']') {
                        return list;
                    }
                }
            }
            if (c == '"') {
                StringBuilder sb = new StringBuilder();
                i++;
                while (s.charAt(i) != '"') {
                    char ch = s.charAt(i++);
                    if (ch == '\\') {
                        char e = s.charAt(i++);
                        switch (e) {
                            case 'n': sb.append('\n'); break;
                            case 't': sb.append('\t'); break;
                            case 'r': sb.append('\r'); break;
                            case 'b': sb.append('\b'); break;
                            case 'f': sb.append('\f'); break;
                            case 'u': sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; break;
                            default: sb.append(e);
                        }
                    } else {
                        sb.append(ch);
                    }
                }
                i++;
                return sb.toString();
            }
            if (s.startsWith("true", i)) {
                i += 4;
                return Boolean.TRUE;
            }
            if (s.startsWith("false", i)) {
                i += 5;
                return Boolean.FALSE;
            }
            if (s.startsWith("null", i)) {
                i += 4;
                return null;
            }
            int start = i;
            while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) {
                i++;
            }
            String num = s.substring(start, i);
            if (num.isEmpty()) {
                throw new UncheckedIOException(new IOException("Не JSON около позиции " + start));
            }
            return num.contains(".") || num.contains("e") || num.contains("E") ? (Object) Double.parseDouble(num) : (Object) Long.parseLong(num);
        }

        private void skip() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
                i++;
            }
        }
    }
}
