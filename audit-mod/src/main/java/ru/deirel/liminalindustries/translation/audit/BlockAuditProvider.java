package ru.deirel.liminalindustries.translation.audit;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

final class BlockAuditProvider implements AuditProvider {
    @Override
    public String id() {
        return "blocks";
    }

    @Override
    public List<AuditSubject> discover(AuditContext context) {
        List<AuditSubject> subjects = new ArrayList<>();
        for (Block block : ForgeRegistries.BLOCKS) {
            if (block == Blocks.AIR) {
                continue;
            }
            ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(block);
            subjects.add(new AuditSubject(
                id(),
                "block:" + blockId,
                "block",
                blockId.toString(),
                block.getDescriptionId(),
                block.getName(),
                Set.of("BLOCK_REGISTRY")
            ));
        }
        return subjects;
    }
}
