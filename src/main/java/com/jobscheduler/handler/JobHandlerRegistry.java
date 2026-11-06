package com.jobscheduler.handler;

import com.jobscheduler.model.JobType;

import java.util.EnumMap;
import java.util.Map;

/**
 * Maps each JobType to the handler that knows how to run it.
 *
 * [Design Pattern: Registry / Factory] The executor asks "who handles EMAIL?" instead of
 *   having a big if/else or switch on the job type.
 * [SOLID: OCP] Supporting a new job type never touches the executor.
 */
public class JobHandlerRegistry {

    private final Map<JobType, JobHandler> handlers = new EnumMap<>(JobType.class);

    public JobHandlerRegistry register(JobHandler handler) {
        handlers.put(handler.supports(), handler);
        return this; // allows chaining: registry.register(a).register(b)
    }

    public JobHandler get(JobType type) {
        JobHandler handler = handlers.get(type);
        if (handler == null) throw new IllegalArgumentException("No handler registered for " + type);
        return handler;
    }

    public boolean supports(JobType type) {
        return handlers.containsKey(type);
    }
}
