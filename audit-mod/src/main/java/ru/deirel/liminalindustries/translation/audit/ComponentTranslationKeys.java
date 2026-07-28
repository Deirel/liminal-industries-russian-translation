package ru.deirel.liminalindustries.translation.audit;

import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.util.Map;
import java.util.TreeMap;

final class ComponentTranslationKeys {
    private ComponentTranslationKeys() {
    }

    static Map<String, String> collect(Component component, Language language) {
        Map<String, String> result = new TreeMap<>();
        collect(component, language, result);
        return result;
    }

    private static void collect(
        Component component,
        Language language,
        Map<String, String> result
    ) {
        if (component.getContents() instanceof TranslatableContents translatable) {
            String template = translatable.getFallback() == null
                ? language.getOrDefault(translatable.getKey())
                : language.getOrDefault(translatable.getKey(), translatable.getFallback());
            result.putIfAbsent(translatable.getKey(), template);
            Object[] arguments = translatable.getArgs();
            for (int index : TranslationTemplate.referencedArguments(template)) {
                if (index < 0 || index >= arguments.length) {
                    continue;
                }
                Object argument = arguments[index];
                if (argument instanceof Component nested) {
                    collect(nested, language, result);
                }
            }
        }
        for (Component sibling : component.getSiblings()) {
            collect(sibling, language, result);
        }
    }
}
