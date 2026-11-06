package com.jobscheduler.dashboard;

import com.jobscheduler.api.JobSchedulerService;
import com.jobscheduler.metrics.MetricsCollector;
import com.jobscheduler.model.Job;
import com.jobscheduler.model.JobExecution;
import com.jobscheduler.model.JobType;
import com.jobscheduler.trigger.TriggerScheduler;
import com.jobscheduler.util.NamedThreadFactory;
import com.jobscheduler.worker.Worker;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * Web dashboard built on the JDK's own HTTP server (no Spring, no extra libraries).
 *
 *   GET /             → the dashboard page (src/main/resources/dashboard/index.html)
 *   GET /api/stats    → live counts, metrics, workers, schedules, dead letter queue
 *   GET /api/history  → latest attempts from SQLite
 *   GET /api/job?id=X → one job and every attempt it made
 *
 * [SOLID: SRP] Only turns engine data into JSON over HTTP. No scheduling logic lives here.
 */
public class WebDashboard {

    private final JobSchedulerService scheduler;
    private final HttpServer server;

    public WebDashboard(JobSchedulerService scheduler, int port) throws IOException {
        this.scheduler = scheduler;
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", this::serveIndex);
        server.createContext("/api/stats", ex -> sendJson(ex, 200, stats()));
        server.createContext("/api/history", ex -> sendJson(ex, 200, recentHistory(ex)));
        server.createContext("/api/job", this::serveJob);
        server.setExecutor(Executors.newFixedThreadPool(4, new NamedThreadFactory("http")));
    }

    public void start() {
        server.start();
    }

    public void stop() {
        server.stop(0);
    }

    // ------------------------------------------------------------------ endpoints

    private void serveIndex(HttpExchange ex) throws IOException {
        if (!ex.getRequestURI().getPath().equals("/")) {
            send(ex, 404, "text/plain", "Not found".getBytes(StandardCharsets.UTF_8));
            return;
        }
        try (InputStream in = getClass().getResourceAsStream("/dashboard/index.html")) {
            if (in == null) {
                send(ex, 500, "text/plain", "index.html missing".getBytes(StandardCharsets.UTF_8));
                return;
            }
            send(ex, 200, "text/html; charset=utf-8", in.readAllBytes());
        }
    }

    private Map<String, Object> stats() {
        MetricsCollector m = scheduler.getMetrics();
        long now = System.currentTimeMillis();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("uptimeMs", m.uptimeMs());
        out.put("submitted", m.getSubmitted());
        out.put("counts", scheduler.statusCounts());
        out.put("queueSize", scheduler.queueSize());

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("throughputPerMin", round(m.throughputPerMinute()));
        metrics.put("avgWaitMs", round(m.averageWaitMs()));
        metrics.put("retries", m.getRetries());
        metrics.put("failedAttempts", m.getFailedAttempts());
        metrics.put("timeouts", m.getTimeouts());
        metrics.put("utilization", round(scheduler.getWorkerPool().averageUtilization()));
        metrics.put("strategy", scheduler.getWorkerPool().strategyName());
        out.put("metrics", metrics);

        List<Object> types = new ArrayList<>();
        for (JobType type : JobType.values()) {
            types.add(Map.of("type", type.name(), "attempts", m.attempts(type), "avgExecMs", round(m.averageExecMs(type))));
        }
        out.put("types", types);

        List<Object> workers = new ArrayList<>();
        for (Worker w : scheduler.getWorkerPool().getWorkers()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", w.getId());
            row.put("status", w.getStatus());
            row.put("currentJob", w.getCurrentJobId());
            row.put("tasksHandled", w.getTasksHandled());
            row.put("utilization", round(w.utilization(now)));
            workers.add(row);
        }
        out.put("workers", workers);

        List<Object> schedules = new ArrayList<>();
        for (TriggerScheduler.ScheduledJob s : scheduler.getScheduledJobs()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", s.getName());
            row.put("trigger", s.getTriggerDescription());
            row.put("runs", s.getRuns());
            row.put("nextFireAt", s.isActive() ? s.getNextFireAt() : null);
            row.put("active", s.isActive());
            schedules.add(row);
        }
        out.put("schedules", schedules);

        List<Object> dead = new ArrayList<>();
        for (Job job : scheduler.getDeadJobs()) {
            dead.add(jobToMap(job));
        }
        out.put("deadLetters", dead);
        return out;
    }

    private List<Object> recentHistory(HttpExchange ex) {
        int limit = 25;
        String requested = queryParams(ex).get("limit");
        if (requested != null) {
            try {
                limit = Math.max(1, Math.min(200, Integer.parseInt(requested)));
            } catch (NumberFormatException ignored) {
                // keep default
            }
        }
        List<Object> rows = new ArrayList<>();
        for (JobExecution e : scheduler.getHistory().findRecent(limit)) {
            rows.add(executionToMap(e));
        }
        return rows;
    }

    private void serveJob(HttpExchange ex) throws IOException {
        String id = queryParams(ex).get("id");
        Job job = id == null ? null : scheduler.getJob(id.trim()).orElse(null);
        if (job == null) {
            sendJson(ex, 404, Map.of("error", "No job with id '" + id + "'"));
            return;
        }
        List<Object> attempts = new ArrayList<>();
        for (JobExecution e : scheduler.getHistory().findByJobId(job.getId())) {
            attempts.add(executionToMap(e));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("job", jobToMap(job));
        out.put("attempts", attempts);
        sendJson(ex, 200, out);
    }

    // ------------------------------------------------------------------ helpers

    private static Map<String, Object> jobToMap(Job job) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", job.getId());
        row.put("name", job.getName());
        row.put("type", job.getType());
        row.put("priority", job.getPriority());
        row.put("status", job.getStatus());
        row.put("attempts", job.getAttempts());
        row.put("maxRetries", job.getMaxRetries());
        row.put("dependsOn", job.getDependsOn());
        row.put("lastError", job.getLastError());
        return row;
    }

    private static Map<String, Object> executionToMap(JobExecution e) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("jobId", e.jobId());
        row.put("jobName", e.jobName());
        row.put("type", e.jobType());
        row.put("priority", e.priority());
        row.put("attempt", e.attempt());
        row.put("workerId", e.workerId());
        row.put("status", e.status());
        row.put("durationMs", e.durationMs());
        row.put("finishedAt", e.finishedAt());
        row.put("error", e.error());
        return row;
    }

    private static double round(double value) {
        return Math.round(value * 10) / 10.0;
    }

    private static Map<String, String> queryParams(HttpExchange ex) {
        Map<String, String> params = new HashMap<>();
        String query = ex.getRequestURI().getRawQuery();
        if (query == null) return params;
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                params.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8), URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
            }
        }
        return params;
    }

    private static void sendJson(HttpExchange ex, int status, Object body) throws IOException {
        send(ex, status, "application/json; charset=utf-8", Json.toJson(body).getBytes(StandardCharsets.UTF_8));
    }

    private static void send(HttpExchange ex, int status, String contentType, byte[] body) throws IOException {
        ex.getResponseHeaders().set("Content-Type", contentType);
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(status, body.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(body);
        }
    }
}
