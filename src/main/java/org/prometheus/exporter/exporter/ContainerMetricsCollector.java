package org.prometheus.exporter.exporter;

import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.messages.Container;
import com.spotify.docker.client.messages.ContainerStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

/**
 * How to cal container metrics https://github.com/moby/moby/blob/eb131c5383db8cac633919f82abad86c99bffbe5/cli/command/container/stats_helpers.go#L185:75
 */
public class ContainerMetricsCollector implements Runnable {

    private static Logger LOGGER = LoggerFactory.getLogger(ContainerMetricsCollector.class);

    private final Container container;
    private final DefaultDockerClient docker;
    private final CountDownLatch latch;

    private boolean collected;
    private Map<String, String> labels = new HashMap<>();
    private ContainerMetrics metrics;

    public ContainerMetricsCollector(Container container, DefaultDockerClient docker, CountDownLatch latch) {
        this.container = container;
        this.docker = docker;
        this.latch = latch;
        collected = false;
    }

    @Override
    public void run() {
        try {
            Map<String, String> containerLabels = container.labels();
            for (String key : containerLabels.keySet()) {
                labels.put(String.format("container_label_%s", key.replace(".", "_").replace("-", "_")).toLowerCase(), containerLabels.get(key));
            }
            String containerName = getContainerName(container);
            labels.put("name", containerName);
            labels.put("image", container.image());
            ContainerStats stats = docker.stats(container.id());
            Long memLimit = stats.memoryStats().limit();
            Long memUsed = stats.memoryStats().usage();

            Long previousCPU = stats.precpuStats().cpuUsage().totalUsage();
            Long previousSystem = stats.precpuStats().systemCpuUsage();
            double cpuPercent = calculateCPUPercentUnix(previousCPU, previousSystem, stats);

            double memUsage = new BigDecimal(Double.toString(memUsed))
                    .divide(
                            new BigDecimal(Double.toString(memLimit)), 4, BigDecimal.ROUND_HALF_UP
                    )
                    .doubleValue();
            metrics = new ContainerMetrics(memLimit, memUsed, memUsage, cpuPercent);
            LOGGER.info(metrics.toString());
            collected = true;

        } catch (Exception e) {
            collected = false;
            LOGGER.error(e.getMessage(), e);
        } finally {
            if (latch != null) {
                latch.countDown();
            }
        }

    }

    private double calculateCPUPercentUnix(Long previousCPU, Long previousSystem, ContainerStats stats) {
        double cpuPercent = 0.0d;
        long cpuDelta = stats.cpuStats().cpuUsage().totalUsage() - previousCPU;
        long systemDelta = stats.cpuStats().systemCpuUsage() - previousSystem;

        if (systemDelta > 0 && cpuDelta > 0) {
            cpuPercent = getCpuUsage(cpuDelta, systemDelta) * stats.cpuStats().cpuUsage().percpuUsage().size() * 100.0d;
        }
        return cpuPercent;

    }

    private double getCpuUsage(long cpuDelta, long systemDelta) {


        double usage = new BigDecimal(Double.toString(cpuDelta))
                .divide(
                        new BigDecimal(Double.toString(systemDelta)), 4, BigDecimal.ROUND_HALF_UP
                ).doubleValue();

        return usage;
    }

    private String getContainerName(Container container) {
        String name = container.names().get(0);
        return name.substring(1, name.length());
    }

    public boolean isCollected() {
        return collected;
    }

    public Map<String, String> getLabels() {
        return labels;
    }

    public ContainerMetrics getMetrics() {
        return metrics;
    }
}

class ContainerMetrics {
    private long memLimit;
    private long memUsed;
    private double cpuPercent;
    private double memUsage;

    public ContainerMetrics(long memLimit, long memUsed, double memUsage, double cpuPercent) {
        this.memLimit = memLimit;
        this.memUsage = memUsage;
        this.memUsed = memUsed;
        this.cpuPercent = cpuPercent;
    }

    public long getMemLimit() {
        return memLimit;
    }

    public void setMemLimit(long memLimit) {
        this.memLimit = memLimit;
    }

    public double getMemUsage() {
        return memUsage;
    }

    public void setMemUsage(double memUsage) {
        this.memUsage = memUsage;
    }

    public long getMemUsed() {
        return memUsed;
    }

    public void setMemUsed(long memUsed) {
        this.memUsed = memUsed;
    }

    public double getCpuPercent() {
        return cpuPercent;
    }

    public void setCpuPercent(double cpuPercent) {
        this.cpuPercent = cpuPercent;
    }

    @Override
    public String toString() {
        return "ContainerMetrics{" +
                "cpuPercent=" + cpuPercent +
                ", memLimit=" + memLimit +
                ", memUsed=" + memUsed +
                ", memUsage=" + memUsage +
                '}';
    }
}
