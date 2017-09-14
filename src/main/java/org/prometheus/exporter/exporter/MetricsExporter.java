package org.prometheus.exporter.exporter;

import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.messages.Container;
import com.spotify.docker.client.messages.ContainerStats;
import io.prometheus.client.Collector;
import io.prometheus.client.GaugeMetricFamily;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MetricsExporter extends Collector {

    private static Logger LOGGER = LoggerFactory.getLogger(MetricsExporter.class);
    private DefaultDockerClient docker;

    private static boolean collecting = false;
    private static List<MetricFamilySamples> cache = new ArrayList<>();

    public MetricsExporter() {
        docker = new DefaultDockerClient("unix:///var/run/docker.sock");
    }

    @Override
    public List<MetricFamilySamples> collect() {
        long start = System.currentTimeMillis();
        LOGGER.info("collector is working? " + collecting);
        LOGGER.info("collector data from docker daemon start");
        cache = doCollect();
        LOGGER.info(String.format("collector data from docker daemon finished[%d]", System.currentTimeMillis() - start));
        return cache;
    }

    private List<MetricFamilySamples> doCollect() {
        collecting = true;
        List<MetricFamilySamples> mfs = new ArrayList<>();


        try {
            List<Container> containers = docker.listContainers();
            for (Container container : containers) {

                if (container.names().isEmpty()) {
                    continue;
                }

                List<String> labels = new ArrayList<>();
                List<String> labelValues = new ArrayList<>();
                Map<String, String> containerLabels = container.labels();
                for (String key : containerLabels.keySet()) {
                    labels.add(String.format("container_label_%s", key.replace(".", "_")).toLowerCase());
                    labelValues.add(containerLabels.get(key));
                }

                String containerName = getContainerName(container);
                LOGGER.info("inspect container " + containerName + " information");
                labels.add(0, "name");
                labelValues.add(0, containerName);

                ContainerStats stats = docker.stats(container.id());

                GaugeMetricFamily memLimitGauge = new GaugeMetricFamily("io_container_mem_limit", "io_container_mem_limit", labels);
                GaugeMetricFamily memUsedGauge = new GaugeMetricFamily("io_container_mem_used", "io_container_mem_used", labels);
                GaugeMetricFamily memUsageGauge = new GaugeMetricFamily("io_container_mem_usage", "io_container_mem_usage", labels);


                addContainerMemMetrics(
                        labelValues,
                        memLimitGauge,
                        memUsedGauge,
                        memUsageGauge, stats);

                mfs.add(memLimitGauge);
                mfs.add(memUsedGauge);
                mfs.add(memUsageGauge);

            }
        } catch (Exception e) {
            LOGGER.error(e.getMessage());
        } finally {
            collecting = false;
        }

        return mfs;
    }

    private void addContainerMemMetrics(List<String> labelValues, GaugeMetricFamily memLimitGauge, GaugeMetricFamily memUsedGauge, GaugeMetricFamily memUsageGauge, ContainerStats stats) {
        Long limit = stats.memoryStats().limit();
        Long usage = stats.memoryStats().usage();
        memLimitGauge.addMetric(labelValues, limit);
        memUsedGauge.addMetric(labelValues, usage);
        memUsageGauge.addMetric(labelValues, new BigDecimal(Double.toString(usage)).divide(new BigDecimal(Double.toString(limit)), 4, BigDecimal.ROUND_HALF_UP).doubleValue());
    }

    private String getContainerName(Container container) {
        String name = container.names().get(0);
        return name.substring(1, name.length());
    }


}
