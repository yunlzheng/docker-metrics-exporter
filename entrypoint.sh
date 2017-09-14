#!/usr/bin/env bash

echo 'Start Docker Metrics Exporter....'
java -Xmx1024m -Djava.security.egd=file:/dev/./urandom -jar docker-metrics-exporter.jar $@
