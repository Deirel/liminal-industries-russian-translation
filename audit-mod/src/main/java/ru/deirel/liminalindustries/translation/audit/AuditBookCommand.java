package ru.deirel.liminalindustries.translation.audit;

import com.mojang.brigadier.Command;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

@Mod.EventBusSubscriber(
    modid = LiminalIndustriesTranslationAuditMod.MOD_ID,
    value = Dist.CLIENT,
    bus = Mod.EventBusSubscriber.Bus.FORGE
)
public final class AuditBookCommand {
    private AuditBookCommand() {
    }

    @SubscribeEvent
    public static void registerCommand(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("liminal_ru_books")
                .executes(context -> giveBooks(
                    context.getSource().getPlayerOrException()
                ))
        );
    }

    private static int giveBooks(ServerPlayer player) {
        try {
            List<AuditBook> books = AuditBookCatalog.collect();
            for (AuditBook book : books) {
                player.getInventory().placeItemBackInInventory(
                    book.stack().copy()
                );
            }
            player.sendSystemMessage(Component.literal(
                "Выдано книг для проверки перевода: " + books.size() + "."
            ));
            return books.isEmpty() ? 0 : Command.SINGLE_SUCCESS;
        } catch (RuntimeException exception) {
            LiminalIndustriesTranslationAuditMod.LOGGER.error(
                "Could not give translated books",
                exception
            );
            player.sendSystemMessage(Component.literal(
                "Не удалось выдать книги: " + exception.getMessage()
            ));
            return 0;
        }
    }
}
