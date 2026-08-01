#!/bin/sh

# RocketMQ 会把 brokerIP1 返回给远程客户端；必须使用宿主机可达地址，不能使用容器内地址。
set -eu

template="/home/rocketmq/conf/broker.conf"
rendered="/tmp/foodmate-broker.conf"
broker_ip="${ROCKETMQ_BROKER_IP1:-host.docker.internal}"

sed "s|__FOODMATE_BROKER_IP1__|${broker_ip}|g" "${template}" > "${rendered}"
exec sh mqbroker -c "${rendered}"
