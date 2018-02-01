#!/bin/bash
echo "============================="
SCRIPT_PATH=$(cd `dirname $0`; pwd)
echo "scriptPath: "${SCRIPT_PATH}
ROOT_PATH=$(dirname ${SCRIPT_PATH})
echo "WORKSPACE:"${ROOT_PATH}
VERSION=$1
echo "PUBLISH VERSION:"${VERSION}

cd ${ROOT_PATH}
#./gradlew clean
#./gradlew assemble -PrunList=main

REGISTRY=registry.cn-hangzhou.aliyuncs.com
ACCOUNT=wise2cdev
PASSWORD=Wise2c2017
NAMESPACE=wise2c-dev
NAMESPACE2=wise2c-test
REPO=docker-metrics-exporter

docker build -t ${REGISTRY}/${NAMESPACE}/${REPO}:${VERSION} -f Dockerfile-local .
docker tag ${REGISTRY}/${NAMESPACE}/${REPO}:${VERSION} ${REGISTRY}/${NAMESPACE}/${REPO}
docker tag ${REGISTRY}/${NAMESPACE}/${REPO}:${VERSION} ${REGISTRY}/${NAMESPACE2}/${REPO}:${VERSION}
docker login -u="${ACCOUNT}" -p="${PASSWORD}" ${REGISTRY}
docker push ${REGISTRY}/${NAMESPACE}/${REPO}:${VERSION}
docker push ${REGISTRY}/${NAMESPACE2}/${REPO}:${VERSION}
docker push ${REGISTRY}/${NAMESPACE}/${REPO}
echo "===========SUCCESS=========="