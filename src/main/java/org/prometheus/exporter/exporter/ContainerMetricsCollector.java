package org.prometheus.exporter.exporter;

import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.messages.Container;
import com.spotify.docker.client.messages.ContainerStats;
import io.prometheus.client.GaugeMetricFamily;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

public class ContainerMetricsCollector implements Runnable {

    private static Logger LOGGER = LoggerFactory.getLogger(ContainerMetricsCollector.class);

    private final Container container;
    private DefaultDockerClient docker;
    private final CountDownLatch latch;
    private GaugeMetricFamily memLimitGauge;
    private GaugeMetricFamily memUsedGauge;
    private GaugeMetricFamily memUsageGauge;

    private boolean collected;

    public ContainerMetricsCollector(Container container, DefaultDockerClient docker, CountDownLatch latch) {
        this.container = container;
        this.docker = docker;
        this.latch = latch;
        collected = false;
    }

    @Override
    public void run() {
        try {
            List<String> labels = new ArrayList<>();
            List<String> labelValues = new ArrayList<>();
            Map<String, String> containerLabels = container.labels();
            for (String key : containerLabels.keySet()) {
                labels.add(String.format("container_label_%s", key.replace(".", "_")).toLowerCase());
                labelValues.add(containerLabels.get(key));
            }

            String containerName = getContainerName(container);
            LOGGER.info("inspect start container " + containerName + " information");
            labels.add(0, "name");
            labelValues.add(0, containerName);

            ContainerStats stats = docker.stats(container.id());

            memLimitGauge = new GaugeMetricFamily("io_container_mem_limit", "io_container_mem_limit", labels);
            memUsedGauge = new GaugeMetricFamily("io_container_mem_used", "io_container_mem_used", labels);
            memUsageGauge = new GaugeMetricFamily("io_container_mem_usage", "io_container_mem_usage", labels);

            Long limit = stats.memoryStats().limit();
            Long usage = stats.memoryStats().usage();

            memLimitGauge.addMetric(labelValues, limit);
            memUsedGauge.addMetric(labelValues, usage);
            memUsageGauge.addMetric(labelValues, new BigDecimal(Double.toString(usage)).divide(new BigDecimal(Double.toString(limit)), 4, BigDecimal.ROUND_HALF_UP).doubleValue());

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

    public GaugeMetricFamily getMemLimitGauge() {
        return memLimitGauge;
    }

    public GaugeMetricFamily getMemUsageGauge() {
        return memUsageGauge;
    }

    public GaugeMetricFamily getMemUsedGauge() {
        return memUsedGauge;
    }
}
