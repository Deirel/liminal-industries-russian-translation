package ru.deirel.liminalindustries.translation;

record QuestTranslationField(String source, String translation) {
    String translate(String currentSource) {
        return source.equals(currentSource) ? translation : null;
    }
}
