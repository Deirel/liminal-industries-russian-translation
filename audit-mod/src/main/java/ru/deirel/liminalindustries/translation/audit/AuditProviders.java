package ru.deirel.liminalindustries.translation.audit;

import java.util.List;

final class AuditProviders {
    private static final List<AuditProvider> ALL = List.of(
        new ItemAuditProvider(),
        new BlockAuditProvider(),
        new PatchouliAuditProvider(),
        new ImmersiveEngineeringManualAuditProvider(),
        new MantleBookAuditProvider()
    );

    private AuditProviders() {
    }

    static List<AuditProvider> all() {
        return ALL;
    }
}
