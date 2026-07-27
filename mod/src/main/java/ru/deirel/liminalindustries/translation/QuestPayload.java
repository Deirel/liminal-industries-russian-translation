package ru.deirel.liminalindustries.translation;

import java.io.IOException;
import java.io.InputStream;

@FunctionalInterface
public interface QuestPayload {
    InputStream open(String relativePath) throws IOException;
}
