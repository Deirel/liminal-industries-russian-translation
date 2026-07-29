package ru.deirel.liminalindustries.translation.audit;

import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;

record AuditContext(
    Minecraft minecraft,
    IJeiRuntime jeiRuntime,
    RussianLanguageIndex english
) {
}
