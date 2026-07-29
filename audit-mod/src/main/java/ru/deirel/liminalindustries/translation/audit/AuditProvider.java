package ru.deirel.liminalindustries.translation.audit;

import java.util.List;

interface AuditProvider {
    String id();

    List<AuditSubject> discover(AuditContext context);
}
