package ru.deirel.liminalindustries.translation.audit;

import net.minecraft.world.item.ItemStack;

import java.util.Objects;

record AuditBook(String logicalId, ItemStack stack) {
    AuditBook {
        if (logicalId.isBlank()) {
            throw new IllegalArgumentException("book logical ID must not be blank");
        }
        Objects.requireNonNull(stack, "stack");
        if (stack.isEmpty()) {
            throw new IllegalArgumentException(
                "book stack must not be empty: " + logicalId
            );
        }
        stack = stack.copy();
        stack.setCount(1);
    }
}
