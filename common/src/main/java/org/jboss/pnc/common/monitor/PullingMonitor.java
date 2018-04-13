/**
 * JBoss, Home of Professional Open Source.
 * Copyright 2014 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.pnc.common.monitor;

import org.jboss.pnc.common.util.NamedThreadFactory;
import org.jboss.pnc.common.util.ObjectWrapper;
import org.jboss.pnc.common.util.TimeUtils;
import org.jboss.util.collection.ConcurrentSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PreDestroy;
import javax.enterprise.context.ApplicationScoped;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 *
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 * @author Jakub Bartecek &lt;jbartece@redhat.com&gt;
 */
@ApplicationScoped
public class PullingMonitor {
    private static final Logger log = LoggerFactory.getLogger(PullingMonitor.class);

    /** Time how long to wait until all services are fully up and running (in seconds) */
    private static final int DEFAULT_TIMEOUT = 300;
    private static final String PULLING_MONITOR_TIMEOUT_KEY = "pulling_monitor_timeout";

    /** Interval between two checks if the services are fully up and running (in second) */
    private static final int DEFAULT_CHECK_INTERVAL = 1;
    private static final String PULLING_MONITOR_CHECK_INTERVAL_KEY = "pulling_monitor_check_interval";

    /** */
    private static final TimeUnit DEFAULT_TIME_UNIT = TimeUnit.SECONDS;

    private static final String PULLING_MONITOR_THREADPOOL_KEY = "pulling_monitor_threadpool";
    private static final int DEFAULT_EXECUTOR_THREADPOOL_SIZE = 4;

    private ScheduledExecutorService executorService;
    private ScheduledExecutorService executorTest;
    private ScheduledExecutorService timeOutVerifierService;

    private ConcurrentSet<RunningTask> runningTasks;

    private int timeout;
    private int checkInterval;

    public PullingMonitor() {
        log.info("PullingMonitor Object created!");

        timeout = getValueFromPropertyOrDefault(PULLING_MONITOR_TIMEOUT_KEY, DEFAULT_TIMEOUT, "timeout");
        checkInterval = getValueFromPropertyOrDefault(PULLING_MONITOR_CHECK_INTERVAL_KEY,
                DEFAULT_CHECK_INTERVAL,
                "check interval");

        int threadSize = getValueFromPropertyOrDefault(PULLING_MONITOR_THREADPOOL_KEY,
                DEFAULT_EXECUTOR_THREADPOOL_SIZE,
                "thread size");

        runningTasks = new ConcurrentSet<>();
        startTimeOutVerifierService();
        executorService = Executors.newScheduledThreadPool(threadSize, new NamedThreadFactory("pulling-monitor"));
        executorTest = Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory("pulling-monitor-thread-check"));

        Runnable periodicTask = new Runnable() {
            public void run() {
                ThreadPoolExecutor threadPoolExecutor = (ScheduledThreadPoolExecutor) executorService;
                log.error("#### >>>> Thread Pulling Monitor:: Pool: "
                        + threadPoolExecutor.getPoolSize() + " MaxPool: " + threadPoolExecutor.getMaximumPoolSize()
                        + " ####");
            }
        };

        executorTest.scheduleAtFixedRate(periodicTask, 1, 2, TimeUnit.SECONDS);
    }

    public void monitor(Runnable onMonitorComplete, Consumer<Exception> onMonitorError, Supplier<Boolean> condition) {
        log.info("PullingMonitor :: calling monitor");
        monitor(onMonitorComplete, onMonitorError, condition, checkInterval, timeout, DEFAULT_TIME_UNIT);
        log.info("PullingMonitor :: Done calling monitor");
    }

    /**
     * Periodically checks the condition and calls onMonitorComplete when it returns true.
     * If timeout is reached onMonitorError is called.
     *
     * @param onMonitorComplete
     * @param onMonitorError
     * @param condition
     * @param checkInterval
     * @param timeout
     * @param timeUnit Unit used for checkInterval and timeout
     */
    public void monitor(Runnable onMonitorComplete, Consumer<Exception> onMonitorError, Supplier<Boolean> condition, int checkInterval, int timeout, TimeUnit timeUnit) {
        AtomicInteger timeWaiting = new AtomicInteger(0);

        ObjectWrapper<RunningTask> runningTaskReference = new ObjectWrapper<>();
        Runnable monitor = () -> {
            RunningTask runningTask = runningTaskReference.get();

            if (runningTask == null) {
                return;
            }

            try {
                int waiting = timeWaiting.addAndGet(checkInterval);

                // Check if given condition is satisfied
                log.info("PullingMonitor :: checking if condition satisfied...");
                if (condition.get()) {
                    log.info("PullingMonitor :: condition satisfied!");
                    runningTasks.remove(runningTask);
                    log.info("PullingMonitor :: removed running task reference from list!");
                    runningTask.cancel();
                    log.info("PullingMonitor :: running task cancelled!");
                    onMonitorComplete.run();
                    log.info("PullingMonitor :: omComplete callback called");
                }
            } catch (Exception e) {
                log.error(e.getMessage());
                log.error(e.toString());
                e.printStackTrace();
                log.info("PullingMonitor :: <><> exception :: removing running task reference from list!");
                runningTasks.remove(runningTask);
                log.info("PullingMonitor :: <><> exception :: removed running task reference from list!");
                runningTask.cancel();
                onMonitorError.accept(e);
            }
        };
        ScheduledFuture<?> future = executorService.scheduleWithFixedDelay(monitor, 0L, checkInterval, timeUnit);
        Consumer<RunningTask> onTimeout = (runningTask) -> {
            runningTasks.remove(runningTask);
            onMonitorError.accept(new MonitorException("Service was not ready in: " + timeout + " " + timeUnit.toString()));
        };
        RunningTask runningTask = new RunningTask(future, timeout, TimeUtils.chronoUnit(timeUnit), onTimeout);
        runningTasks.add(runningTask);
        runningTaskReference.set(runningTask);
    }

    public ScheduledFuture<?> timer(Runnable task, long delay, TimeUnit timeUnit) {
        return executorService.schedule(task, delay, timeUnit);
    }

    private void startTimeOutVerifierService() {
        Runnable terminateTimedOutTasks = () -> {
            runningTasks.parallelStream()
                    .forEach(runningTask -> runningTask.terminateIfTimedOut());
        };
        timeOutVerifierService = Executors.newScheduledThreadPool(1);
        timeOutVerifierService.scheduleWithFixedDelay(terminateTimedOutTasks, 0L, 250, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    public void destroy() {
        executorService.shutdownNow();
        timeOutVerifierService.shutdownNow();
    }

    /**
     * If propertyName has no value (either specified in system property or environment property), then just return
     * the default value. System property value has priority over environment property value.
     *
     * If value can't be parsed, just return the default value.
     *
     * @param propertyName property name to check the value
     * @param defaultValue default value to use
     * @param description description to print in case value can't be parsed as an integer
     *
     * @return value from property, or default value
     */
    private int getValueFromPropertyOrDefault(String propertyName, int defaultValue, String description) {

        int value = defaultValue;

        String valueEnv = System.getenv(propertyName);
        String valueSys = System.getProperty(propertyName);

        try {
            if (valueSys != null) {
                value = Integer.parseInt(valueSys);
            } else if (valueEnv != null) {
                value = Integer.parseInt(valueEnv);
            }
            log.info("Updated " + description + " for PullingMonitor to: " + value);
            return value;
        } catch (NumberFormatException e) {
            log.warn("Could not parse the '" + propertyName +
                    "' system property. Using default value: " + defaultValue);
            return value;
        }
    }
}
