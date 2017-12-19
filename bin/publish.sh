./gradlew build
docker build -t registry.cn-hangzhou.aliyuncs.com/wise2c-test/docker-metrics-exporter:stable -f Dockerfile-local .
docker push registry.cn-hangzhou.aliyuncs.com/wise2c-test/docker-metrics-exporter:stable