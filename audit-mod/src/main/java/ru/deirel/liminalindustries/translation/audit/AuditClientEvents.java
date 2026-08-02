package ru.deirel.liminalindustries.translation.audit;

import com.mojang.brigadier.Command;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.deirel.liminalindustries.translation.audit.layout.LayoutEngineAdapters;
import ru.deirel.liminalindustries.translation.audit.layout.LayoutAuditRunner;

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
        event.getDispatcher().register(
            Commands.literal("liminal_ru_layout_audit")
                .executes(context -> {
                    LayoutAuditRunner.StartResult result = LayoutAuditRunner.start();
                    context.getSource().sendSystemMessage(Component.literal(result.message()));
                    return result.started() ? Command.SINGLE_SUCCESS : 0;
                })
        );
        for (String engine : LayoutEngineAdapters.engines()) {
            event.getDispatcher().register(
                Commands.literal("liminal_ru_layout_audit_" + engine)
                    .executes(context -> {
                        LayoutAuditRunner.StartResult result =
                            LayoutAuditRunner.start(engine);
                        context.getSource().sendSystemMessage(
                            Component.literal(result.message())
                        );
                        return result.started() ? Command.SINGLE_SUCCESS : 0;
                    })
            );
        }
    }

    @SubscribeEvent
    public static void afterScreenRender(ScreenEvent.Render.Post event) {
        LayoutAuditRunner.onRendered(event.getScreen());
    }

    @SubscribeEvent
    public static void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            AutoAuditController.tick();
            LayoutAuditRunner.tickAutoStart();
        }
    }
}
