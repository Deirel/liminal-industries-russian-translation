package ru.deirel.liminalindustries.translation.audit;

import com.mojang.brigadier.Command;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(
    modid = LiminalIndustriesTranslationAuditMod.MOD_ID,
    value = Dist.CLIENT,
    bus = Mod.EventBusSubscriber.Bus.FORGE
)
public final class AuditClientEvents {
    private AuditClientEvents() {
    }

    @SubscribeEvent
    public static void registerClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("liminal_ru_audit")
                .executes(context -> {
                    ItemTranslationAudit.Result result = ItemTranslationAudit.run();
                    context.getSource().sendSystemMessage(Component.literal(result.message()));
                    return result.success() ? Command.SINGLE_SUCCESS : 0;
                })
        );
    }
}
