package org.prometheus.exporter.exporter;

import org.junit.Test;

public class ContainerMetricsCollectorTest {

    @Test
    public void should_format_label() {
        String formatLabel = new ContainerMetricsCollector().formatLabel("io.kubernetes/hello/test\\world/aaa/");
        System.out.println(formatLabel);
    }


}