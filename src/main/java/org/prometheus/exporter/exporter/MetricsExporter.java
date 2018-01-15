package org.prometheus.exporter.exporter;

import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.messages.Container;
import io.prometheus.client.Collector;
import io.prometheus.client.CounterMetricFamily;
import io.prometheus.client.GaugeMetricFamily;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static java.util.stream.Collectors.toList;

public class MetricsExporter extends Collector {

    private static Logger LOGGER = LoggerFactory.getLogger(MetricsExporter.class);
    private DefaultDockerClient docker;

    private static CountDownLatch _latch;
    private Executor executor;

    public MetricsExporter() {
        docker = new DefaultDockerClient("unix:///var/run/docker.sock");
        executor = Executors.newFixedThreadPool(20);
    }

    @Override
    public List<MetricFamilySamples> collect() {
        long start = System.currentTimeMillis();
        LOGGER.info("collector data from docker daemon start");
        List<MetricFamilySamples> metrics = doCollect();
        LOGGER.info(String.format("collector data from docker daemon finished[%d milliseconds]", System.currentTimeMillis() - start));
        return metrics;
    }

    private List<MetricFamilySamples> doCollect() {
        List<MetricFamilySamples> mfs = new ArrayList<>();
        try {

            List<Container> containers = docker.listContainers();
            _latch = new CountDownLatch(containers.size());
            List<ContainerMetricsCollector> collectors = containers.stream().map(container -> new ContainerMetricsCollector(container, docker, _latch)).collect(toList());
            collectors.forEach(executor::execute);
            _latch.await();

            List<ContainerMetricsCollector> results = collectors.stream().filter(ContainerMetricsCollector::isCollected).collect(toList());

            ArrayList<String> labels = getMetricsLabels(results);

            GaugeMetricFamily memLimitGauge = new GaugeMetricFamily("io_container_mem_limit", "io_container_mem_limit", labels);
            GaugeMetricFamily memUsedGauge = new GaugeMetricFamily("io_container_mem_used", "io_container_mem_used", labels);
            GaugeMetricFamily memUsageGauge = new GaugeMetricFamily("io_container_mem_usage", "io_container_mem_usage", labels);
            GaugeMetricFamily cpuUsageGauge = new GaugeMetricFamily("io_container_cpu_usage", "io_container_cpu_usage", labels);

            CounterMetricFamily blockReadBytesCounter = new CounterMetricFamily("io_container_block_read_bytes_total", "io_container_block_read_bytes_total", labels);
            CounterMetricFamily blockWriteBytesCounter = new CounterMetricFamily("io_container_block_write_bytes_total", "io_container_block_write_bytes_total", labels);

            CounterMetricFamily networkRxBytesCounter = new CounterMetricFamily("io_container_network_receive_bytes_total", "io_container_network_receive_bytes_total", labels);
            CounterMetricFamily networkTxBytesCounter = new CounterMetricFamily("io_container_network_transmit_bytes_total", "io_container_network_transmit_bytes_total", labels);

            results.forEach(collector -> {
                List<String> labelValues = getLabelValues(labels, collector.getLabels());
                memLimitGauge.addMetric(labelValues, collector.getMetrics().getMemLimit());
                memUsedGauge.addMetric(labelValues, collector.getMetrics().getMemUsed());
                memUsageGauge.addMetric(labelValues, collector.getMetrics().getMemUsage());
                cpuUsageGauge.addMetric(labelValues, collector.getMetrics().getCpuPercent());
                networkRxBytesCounter.addMetric(labelValues, collector.getMetrics().getRxBytes());
                networkTxBytesCounter.addMetric(labelValues, collector.getMetrics().getTxBytes());

                blockReadBytesCounter.addMetric(labelValues, collector.getMetrics().getBlkRead());
                blockWriteBytesCounter.addMetric(labelValues, collector.getMetrics().getBlkWrite());
            });

            mfs.add(memLimitGauge);
            mfs.add(memUsedGauge);
            mfs.add(memUsageGauge);
            mfs.add(cpuUsageGauge);
            mfs.add(networkRxBytesCounter);
            mfs.add(networkTxBytesCounter);
            mfs.add(blockReadBytesCounter);
            mfs.add(blockWriteBytesCounter);

        } catch (Exception e) {
            LOGGER.error(e.getMessage());
        }
        return mfs;
    }

    private List<String> getLabelValues(ArrayList<String> labels, Map<String, String> collectorLabels) {
        ArrayList<String> values = new ArrayList<>();
        labels.forEach(label -> values.add(collectorLabels.getOrDefault(label, "default")));
        return values;
    }

    private ArrayList<String> getMetricsLabels(List<ContainerMetricsCollector> results) {
        Set<String> labels = new HashSet<>();

        for (int i = 0; i < results.size(); i++) {
            Set<String> keys = results.get(i).getLabels().keySet();
            if (i == 0) {
                labels.addAll(keys);
            }
        }

        ArrayList<String> labs = new ArrayList<>();
        labs.addAll(labels);
        return labs;
    }


}
