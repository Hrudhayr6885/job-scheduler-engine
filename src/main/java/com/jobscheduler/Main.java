package com.jobscheduler;

import com.jobscheduler.api.JobSchedulerService;
import com.jobscheduler.api.SchedulerConfig;
import com.jobscheduler.dashboard.ConsoleDashboard;
import com.jobscheduler.dashboard.WebDashboard;
import com.jobscheduler.demo.DemoWorkload;
import com.jobscheduler.handler.EmailHandler;
import com.jobscheduler.handler.InvoiceHandler;
import com.jobscheduler.handler.JobHandlerRegistry;
import com.jobscheduler.handler.ReportHandler;
import com.jobscheduler.handler.TimeoutHandlerDecorator;
import com.jobscheduler.history.SqliteExecutionHistoryRepository;
import com.jobscheduler.metrics.MetricsCollector;
import com.jobscheduler.model.Job;
import com.jobscheduler.model.JobStatus;
import com.jobscheduler.model.JobType;
import com.jobscheduler.util.NamedThreadFactory;
import com.jobscheduler.worker.LeastLoadedStrategy;
import com.jobscheduler.worker.Worker;

import java.net.BindException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Starts the engine, the dashboards and the demo workload.
 *
 *   java -jar target/job-scheduler.jar [workers] [port] [--exit]
 *   e.g. java -jar target/job-scheduler.jar 8 8080
 *
 * This is where the concrete classes are chosen and plugged in
 * ([SOLID: DIP]: everything else only knows interfaces).
 */
public final class Main {

    public static void main(String[] args) throws Exception {
        int workers = 8;
        int port = 8080;
        boolean exitWhenDone = false;
        int position = 0;
        for (String arg : args) {
            if (arg.equals("--exit")) exitWhenDone = true;
            else if (position++ == 0) workers = Integer.parseInt(arg);
            else port = Integer.parseInt(arg);
        }

        printBanner(workers);

        // 1. Handlers, each wrapped with a timeout ([Design Pattern: Decorator])
        ExecutorService handlerThreads = Executors.newCachedThreadPool(new NamedThreadFactory("handler"));
        JobHandlerRegistry registry = new JobHandlerRegistry()
                .register(new TimeoutHandlerDecorator(new InvoiceHandler(), handlerThreads))
                .register(new TimeoutHandlerDecorator(new EmailHandler(), handlerThreads))
                .register(new TimeoutHandlerDecorator(new ReportHandler(), handlerThreads));

        // 2. Execution history in SQLite (fresh for every demo run)
        Files.createDirectories(Path.of("data"));
        SqliteExecutionHistoryRepository history = new SqliteExecutionHistoryRepository("data/job_history.db");
        history.clear();

        // 3. The engine (Facade): config + handlers + history + worker strategy + clock
        JobSchedulerService scheduler = new JobSchedulerService(
                new SchedulerConfig(workers, 5_000), registry, history, new LeastLoadedStrategy(), Clock.systemUTC());

        // 4. Dashboards
        WebDashboard web;
        try {
            web = new WebDashboard(scheduler, port);
        } catch (BindException e) {
            System.err.println("Port " + port + " is already in use. Try: java -jar target/job-scheduler.jar " + workers + " 9090");
            System.exit(1);
            return;
        }
        ConsoleDashboard console = new ConsoleDashboard(scheduler);

        WebDashboard webToStop = web;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            console.stop();
            webToStop.stop();
            scheduler.shutdown();
            handlerThreads.shutdownNow();
            history.close();
        }));

        scheduler.start();
        web.start();
        System.out.println("Dashboard → http://localhost:" + port + "  (open it now and watch the jobs flow)");

        // 5. The workload from the problem statement
        DemoWorkload.run(scheduler);
        console.start(2_000);

        while (!scheduler.isIdle()) {
            Thread.sleep(500);
        }
        console.stop();
        console.print();
        printSummary(scheduler);

        if (exitWhenDone) {
            System.exit(0);
        }
        System.out.println("\nDashboard is still running at http://localhost:" + port + ". Press Ctrl+C to stop.");
        Thread.currentThread().join(); // keep the web dashboard alive
    }

    private static void printBanner(int workers) {
        System.out.println("""
                ╔══════════════════════════════════════════════════════════╗
                ║     Job Scheduling and Resource Allocation Engine        ║
                ╚══════════════════════════════════════════════════════════╝""");
        System.out.println("Workers: " + workers + "  |  Worker strategy: Least Loaded  |  Aging: +1 priority level per 5s waiting");
    }

    private static void printSummary(JobSchedulerService scheduler) {
        MetricsCollector m = scheduler.getMetrics();
        Map<JobStatus, Long> counts = scheduler.statusCounts();
        long total = counts.values().stream().mapToLong(Long::longValue).sum();

        System.out.println("\n══════════════════════════ FINAL SUMMARY ══════════════════════════");
        System.out.printf("Jobs: %,d total in %.1fs  |  throughput %,.0f jobs/min%n", total, m.uptimeMs() / 1000.0, m.throughputPerMinute());
        System.out.printf("Succeeded %,d  |  Dead (DLQ) %d  |  Skipped %d  |  Cancelled %d%n",
                counts.get(JobStatus.SUCCEEDED), counts.get(JobStatus.DEAD), counts.get(JobStatus.SKIPPED), counts.get(JobStatus.CANCELLED));
        System.out.printf("Retries %,d  |  Failed attempts %,d  |  Timeouts %d  |  Avg queue wait %,.0f ms%n",
                m.getRetries(), m.getFailedAttempts(), m.getTimeouts(), m.averageWaitMs());

        System.out.println("\nAverage execution time per job type:");
        for (JobType type : JobType.values()) {
            System.out.printf("  %-8s %,6d attempts   avg %6.1f ms%n", type, m.attempts(type), m.averageExecMs(type));
        }

        System.out.println("\nWorker utilization:");
        long now = System.currentTimeMillis();
        for (Worker w : scheduler.getWorkerPool().getWorkers()) {
            System.out.printf("  %s  %,5d tasks   %5.1f%% busy%n", w.getId(), w.getTasksHandled(), w.utilization(now));
        }

        System.out.println("\nDead letter queue:");
        for (Job job : scheduler.getDeadJobs()) {
            System.out.printf("  %-14s %-28s attempts=%d  error=%s%n", job.getId(), job.getName(), job.getAttempts(), job.getLastError());
        }

        System.out.printf("%nExecution history: %,d attempts saved in data/job_history.db%n", scheduler.getHistory().count());
        System.out.println("  Try: sqlite3 data/job_history.db \"SELECT status, COUNT(*) FROM job_executions GROUP BY status;\"");
    }
}
