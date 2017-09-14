package org.prometheus.exporter.exporter;

public class Exporters {
    private static boolean initialized = false;

    public static synchronized void initialize() {
        if (!initialized) {
            new MetricsExporter().register();
            initialized = true;
        }
    }

}
