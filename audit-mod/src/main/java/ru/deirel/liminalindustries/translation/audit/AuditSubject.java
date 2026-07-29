package ru.deirel.liminalindustries.translation.audit;

import net.minecraft.network.chat.Component;

import java.util.Set;

record AuditSubject(
    String provider,
    String uid,
    String registryType,
    String registryId,
    String descriptionId,
    Component name,
    Set<String> discoveredFrom
) {
}
