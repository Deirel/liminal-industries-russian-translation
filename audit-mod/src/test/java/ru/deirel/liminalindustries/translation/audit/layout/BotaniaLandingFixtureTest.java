package ru.deirel.liminalindustries.translation.audit.layout;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BotaniaLandingFixtureTest {
    @Test
    void oldLandingIntersectsControlAndFixedTextFits() {
        JsonObject fixture = loadFixture();

        List<LayoutIssue> oldIssues = audit(fixture.get("old").getAsString());
        List<LayoutIssue> fixedIssues = audit(fixture.get("fixed").getAsString());

        assertEquals(
            LayoutIssue.Rule.TEXT_INTERSECTS_CONTROL,
            oldIssues.get(0).rule()
        );
        assertTrue(fixedIssues.isEmpty());
    }

    private List<LayoutIssue> audit(String source) {
        List<LayoutRegion> lines = layout(source);
        LayoutCapture capture = new LayoutCapture(
            "patchouli",
            "botania:lexicon",
            "patchouli:botania:lexicon:landing",
            "data/botania/patchouli_books/lexicon/book.json",
            "<landing>",
            null,
            "translation_key:botania.landing",
            "ru_ru",
            320,
            240,
            2,
            lines,
            List.of(region("page", LayoutRegion.Kind.PAGE, 0, 0, 116, 156)),
            List.of(region("clip", LayoutRegion.Kind.SCISSOR, 0, 0, 116, 156)),
            List.of(region("bottom-button", LayoutRegion.Kind.CONTROL, 0, 106, 116, 12))
        );
        return LayoutAnalyzer.analyze(capture);
    }

    private List<LayoutRegion> layout(String source) {
        String plain = source
            .replace("$(br2)", " ")
            .replaceAll("\\$\\([^)]*\\)", "");
        int charactersPerLine = 23;
        List<LayoutRegion> result = new ArrayList<>();
        int line = 0;
        for (int offset = 0; offset < plain.length(); offset += charactersPerLine) {
            int length = Math.min(charactersPerLine, plain.length() - offset);
            result.add(new LayoutRegion(
                "line-" + line,
                LayoutRegion.Kind.TEXT,
                "left",
                line,
                0,
                43 + line * 9,
                length * 5,
                9
            ));
            line++;
        }
        return result;
    }

    private JsonObject loadFixture() {
        return JsonParser.parseReader(new InputStreamReader(
            getClass().getResourceAsStream("/layout/botania-landing.json"),
            StandardCharsets.UTF_8
        )).getAsJsonObject();
    }

    private LayoutRegion region(
        String id,
        LayoutRegion.Kind kind,
        double x,
        double y,
        double width,
        double height
    ) {
        return new LayoutRegion(id, kind, "left", -1, x, y, width, height);
    }
}
