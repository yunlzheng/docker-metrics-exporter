package org.prometheus.exporter.exporter;

import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.messages.Container;
import io.prometheus.client.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static java.util.stream.Collectors.toList;

public class MetricsExporter extends Collector {

    private static Logger LOGGER = LoggerFactory.getLogger(MetricsExporter.class);
    private DefaultDockerClient docker;

    private static CountDownLatch _latch;

    public MetricsExporter() {
        docker = new DefaultDockerClient("unix:///var/run/docker.sock");
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
            Executor executor = Executors.newFixedThreadPool(collectors.size());
            collectors.forEach(executor::execute);
            _latch.await();
            collectors.stream().filter(ContainerMetricsCollector::isCollected).forEach(collector -> {
                mfs.add(collector.getMemLimitGauge());
                mfs.add(collector.getMemUsageGauge());
                mfs.add(collector.getMemUsedGauge());
            });
        } catch (Exception e) {
            LOGGER.error(e.getMessage());
        }

        return mfs;
    }


}
