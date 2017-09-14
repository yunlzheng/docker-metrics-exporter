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
                labels.put(String.format("container_label_%s", key.replace(".", "_")).toLowerCase(), containerLabels.get(key));
            }
            String containerName = getContainerName(container);
            LOGGER.info("inspect start container " + containerName + " information");
            labels.put("name", containerName);
            ContainerStats stats = docker.stats(container.id());
            Long limit = stats.memoryStats().limit();
            Long used = stats.memoryStats().usage();
            double usage = new BigDecimal(Double.toString(used)).divide(new BigDecimal(Double.toString(limit)), 4, BigDecimal.ROUND_HALF_UP).doubleValue();
            metrics = new ContainerMetrics(limit, used, usage);
            collected = true;
            LOGGER.info("inspect done container " + containerName + " information");

        } catch (Exception e) {
            collected = false;
            LOGGER.error(e.getMessage(), e);
        } finally {
            if (latch != null) {
                latch.countDown();
            }
        }

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
    private double memUsage;

    public ContainerMetrics(long memLimit, long memUsed, double memUsage) {
        this.memLimit = memLimit;
        this.memUsage = memUsage;
        this.memUsed = memUsed;
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
}
