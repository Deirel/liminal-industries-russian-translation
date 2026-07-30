package ru.deirel.liminalindustries.translation.audit.layout;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.SharedConstants;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

public final class LayoutReportWriter {
    public static final Path REPORT_PATH = Path.of(
        "liminal-industries-ru-audit",
        "book-layout-audit.json"
    );
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private LayoutReportWriter() {
    }

    public static Path write(
        Path gameDirectory,
        List<LayoutCapture> captures,
        List<LayoutIssue> issues
    ) throws IOException {
        Path output = gameDirectory.resolve(REPORT_PATH);
        Files.createDirectories(output.getParent());
        JsonObject root = new JsonObject();
        long translationErrors = issues.stream()
            .filter(issue -> issue.severity() == LayoutIssue.Severity.ERROR)
            .filter(issue -> issue.rule() != LayoutIssue.Rule.MISSING_CONTENT)
            .filter(issue ->
                issue.classification() == LayoutIssue.Classification.TRANSLATION_LAYOUT
            )
            .count();
        long missingContentErrors = missingContentErrors(issues);
        long blockingErrors = blockingErrors(issues);
        root.addProperty("schema", 1);
        root.addProperty("result", blockingErrors == 0 ? "PASS" : "FAIL");
        root.addProperty("generated_at", Instant.now().toString());
        root.addProperty(
            "minecraft_version",
            SharedConstants.getCurrentVersion().getName()
        );
        root.addProperty("checked_screens", captures.size());
        root.addProperty("issues", issues.size());
        root.addProperty("blocking_errors", blockingErrors);
        root.addProperty("translation_errors", translationErrors);
        root.addProperty("missing_content_errors", missingContentErrors);

        JsonArray screens = new JsonArray();
        captures.forEach(capture -> {
            JsonObject value = new JsonObject();
            value.addProperty("engine", capture.engine());
            value.addProperty("book", capture.book());
            value.addProperty("screen", capture.screenId());
            value.addProperty("resource", capture.resource());
            value.addProperty("entry", capture.entry());
            if (capture.page() != null) {
                value.addProperty("page", capture.page());
            }
            value.addProperty("text_source", capture.textSource());
            value.addProperty("language", capture.language());
            value.addProperty("width", capture.screenWidth());
            value.addProperty("height", capture.screenHeight());
            value.addProperty("gui_scale", capture.guiScale());
            screens.add(value);
        });
        root.add("screens", screens);

        JsonArray issueValues = new JsonArray();
        issues.forEach(issue -> issueValues.add(GSON.toJsonTree(issue)));
        root.add("issue_details", issueValues);
        Files.writeString(
            output,
            GSON.toJson(root) + "\n",
            StandardCharsets.UTF_8
        );
        writeHtml(output.getParent(), issues);
        return output;
    }

    static long blockingErrors(List<LayoutIssue> issues) {
        long translationErrors = issues.stream()
            .filter(issue -> issue.severity() == LayoutIssue.Severity.ERROR)
            .filter(issue -> issue.rule() != LayoutIssue.Rule.MISSING_CONTENT)
            .filter(issue ->
                issue.classification() == LayoutIssue.Classification.TRANSLATION_LAYOUT
            )
            .count();
        return translationErrors + missingContentErrors(issues);
    }

    static long missingContentErrors(List<LayoutIssue> issues) {
        return issues.stream()
            .filter(issue -> issue.severity() == LayoutIssue.Severity.ERROR)
            .filter(issue -> issue.rule() == LayoutIssue.Rule.MISSING_CONTENT)
            .map(LayoutReportWriter::contentIssueKey)
            .distinct()
            .count();
    }

    private static String contentIssueKey(LayoutIssue issue) {
        return issue.screenId() + "\u0000" + issue.text().id();
    }

    private static void writeHtml(Path directory, List<LayoutIssue> issues)
        throws IOException {
        StringBuilder html = new StringBuilder(
            "<!doctype html><meta charset=\"utf-8\"><title>Book layout audit</title>"
                + "<style>body{font:14px sans-serif;max-width:1100px;margin:2rem auto}"
                + "img{max-width:100%;border:1px solid #999}code{font-size:12px}</style>"
                + "<h1>Book layout audit</h1>"
        );
        for (LayoutIssue issue : issues) {
            html.append("<section><h2>")
                .append(escape(issue.rule().name()))
                .append("</h2><p><code>")
                .append(escape(issue.screenId()))
                .append("</code> ")
                .append(escape(issue.classification().name()))
                .append("</p>");
            if (issue.screenshot() != null) {
                html.append("<a href=\"")
                    .append(escape(issue.screenshot()))
                    .append("\"><img src=\"")
                    .append(escape(issue.screenshot()))
                    .append("\"></a>");
            }
            html.append("</section>");
        }
        Files.writeString(
            directory.resolve("book-layout-audit.html"),
            html.toString(),
            StandardCharsets.UTF_8
        );
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;")
            .replace("\"", "&quot;")
            .replace("<", "&lt;")
            .replace(">", "&gt;");
    }
}
