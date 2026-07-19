package com.athenyx.backend.audit.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Published when an admin exports the audit log to CSV.
 */
@Getter
public class ExportCsvEvent extends ApplicationEvent {

    private final Long actorId;
    private final String actorEmail;
    private final String filters;

    public ExportCsvEvent(Object source, Long actorId, String actorEmail, String filters) {
        super(source);
        this.actorId = actorId;
        this.actorEmail = actorEmail;
        this.filters = filters;
    }
}
