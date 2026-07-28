package ru.deirel.liminalindustries.translation.audit;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.util.Set;
import java.util.TreeSet;

final class ComponentTranslationKeys {
    private ComponentTranslationKeys() {
    }

    static Set<String> collect(Component component) {
        Set<String> result = new TreeSet<>();
        collect(component, result);
        return result;
    }

    private static void collect(Component component, Set<String> result) {
        if (component.getContents() instanceof TranslatableContents translatable) {
            result.add(translatable.getKey());
            for (Object argument : translatable.getArgs()) {
                if (argument instanceof Component nested) {
                    collect(nested, result);
                }
            }
        }
        for (Component sibling : component.getSiblings()) {
            collect(sibling, result);
        }
    }
}
