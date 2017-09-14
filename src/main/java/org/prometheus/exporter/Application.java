package org.prometheus.exporter;

import io.prometheus.client.spring.boot.EnablePrometheusEndpoint;
import org.prometheus.exporter.exporter.Exporters;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;


@EnableAutoConfiguration
@EnablePrometheusEndpoint
public class Application implements CommandLineRunner {

    public static void main(String[] args) throws Exception {
        SpringApplication.run(Application.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        Exporters.initialize();
    }
}