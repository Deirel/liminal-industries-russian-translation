package ru.deirel.liminalindustries.translation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

record QuestTranslationField(
    String source,
    String sourceHash,
    String translation
) {
    QuestTranslationField {
        String actualHash = sourceHash(source);
        if (!actualHash.equals(sourceHash)) {
            throw new IllegalArgumentException(
                "Quest source hash does not match its source text"
            );
        }
    }

    String translate(String currentSource) {
        if (!source.equals(currentSource)
            || !sourceHash.equals(sourceHash(currentSource))) {
            return null;
        }
        return translation;
    }

    static String sourceHash(String source) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return "sha256:" + HexFormat.of().formatHex(
                digest.digest(source.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
