#!/bin/sh
# FoodMate RocketMQ Topic 与 consumer group 初始化。
#
# 该脚本以 network_mode=service:rocketmq-broker 运行，因此 127.0.0.1:10911 就是 Broker。
# Broker 关闭了 autoCreateTopicEnable/autoCreateSubscriptionGroup，Topic 必须在此显式创建。
# 脚本可重复执行：mqadmin updateTopic/updateSubGroup 对已存在对象是幂等更新。
set -eu

MQADMIN="/home/rocketmq/rocketmq-${ROCKETMQ_VERSION}/bin/mqadmin"
NAMESRV="${NAMESRV_ADDR:-foodmate-rocketmq-namesrv:9876}"
BROKER="${BROKER_ADDR:-127.0.0.1:10911}"

# RocketMQ 5.x gRPC PushConsumer uses group-only retry topics. The older
# remoting client may create a topic containing the source topic as well, but
# the Python runtime queries these group-only topics during consumption.
for group in \
    "${GROUP_PYTHON_AGENT_COMMAND:-foodmate-python-agent-command-v1}" \
    "${GROUP_PYTHON_AGENT_RESULT:-foodmate-python-agent-result-v1}"; do
    retry_topic="%RETRY%${group}"
    echo "[foodmate] creating gRPC retry topic ${retry_topic}"
    "$MQADMIN" updateTopic -n "$NAMESRV" -b "$BROKER" -t "$retry_topic" -r 1 -w 1 -p 6 -a +message.type=NORMAL || true
    if ! "$MQADMIN" topicList -n "$NAMESRV" 2>/dev/null | grep -qx "$retry_topic"; then
        echo "[foodmate] gRPC retry topic ${retry_topic} is not visible" >&2
        exit 1
    fi
done


# mqadmin 默认 JVM 参数偏大，本地限制到 256m。
export JAVA_OPT_EXT="-Xms256m -Xmx256m -Xmn128m"

echo "[foodmate] 等待 Broker ${BROKER} 就绪 ..."
i=0
while [ "$i" -lt 60 ]; do
    if "$MQADMIN" brokerStatus -n "$NAMESRV" -b "$BROKER" >/dev/null 2>&1; then
        echo "[foodmate] Broker 已就绪"
        break
    fi
    i=$((i + 1))
    sleep 2
done
if [ "$i" -ge 60 ]; then
    echo "[foodmate] Broker 在 120 秒内未就绪，初始化失败" >&2
    exit 1
fi

# Agent 主链路 Topic（ADR-0005）。读写队列各 4 个：本地单 Broker 够用，
# 又能让同一 consumer group 的多个实例并行消费不同 run_id。
#
# 注意：mqadmin 在子命令抛异常时仍然返回退出码 0，因此不能只靠 set -e。
# 每个对象创建后都要回读校验，否则「初始化成功」会掩盖 Topic 根本不存在。
echo "[foodmate] 创建 RocketMQ Proxy 系统 Topic DefaultHeartBeatSyncerTopic"
"$MQADMIN" updateTopic -n "$NAMESRV" -b "$BROKER" -t "DefaultHeartBeatSyncerTopic" -r 1 -w 1 -p 6 -a +message.type=NORMAL || true
if ! "$MQADMIN" topicList -n "$NAMESRV" 2>/dev/null | grep -qx "DefaultHeartBeatSyncerTopic"; then
    echo "[foodmate] Proxy 系统 Topic 初始化失败" >&2
    exit 1
fi

for topic in \
    "${TOPIC_AGENT_COMMAND:-foodmate-agent-command-v1}" \
    "${TOPIC_AGENT_EVENT:-foodmate-agent-event-v1}" \
    "${TOPIC_AGENT_PROPOSAL:-foodmate-agent-proposal-v1}" \
    "${TOPIC_AGENT_RESULT:-foodmate-agent-result-v1}" \
    "${TOPIC_KNOWLEDGE_INDEX:-foodmate-knowledge-index-v1}" \
    "${TOPIC_KNOWLEDGE_INDEX_RESULT:-foodmate-knowledge-index-result-v1}" \
    "${TOPIC_KNOWLEDGE_VISIBILITY:-foodmate-knowledge-visibility-v1}" \
    "${TOPIC_KNOWLEDGE_PURGE:-foodmate-knowledge-purge-v1}" \
    "${TOPIC_KNOWLEDGE_PURGE_RESULT:-foodmate-knowledge-purge-result-v1}"; do
    echo "[foodmate] 创建 Topic ${topic}"
    # 本地只有一个 Python Runtime 实例；固定单队列，避免 Python 5.x PushConsumer
    # 单实例只领取一个分配队列时，Producer 把消息随机写到未领取队列。
    "$MQADMIN" updateTopic -n "$NAMESRV" -b "$BROKER" -t "$topic" -r "${TOPIC_QUEUE_COUNT:-1}" -w "${TOPIC_QUEUE_COUNT:-1}" -p 6 -a +message.type=NORMAL || true
    # Topic 名只允许 ^[%|a-zA-Z0-9_-]+$，点号会被 Broker 拒绝，因此契约使用连字符。
    if ! "$MQADMIN" topicList -n "$NAMESRV" 2>/dev/null | grep -qx "$topic"; then
        echo "[foodmate] Topic ${topic} 创建后不可见，初始化失败" >&2
        exit 1
    fi
done

# 后台域 Topic 在 ADR-0005 中已预留，但 M1-4 没有生产者/消费者，因此不创建，
# 避免 Topic 列表出现无人负责的空 Topic。

# consumer group 订阅关系。稳定命名，禁止每次启动随机生成（配置指南 §5.9）。
# 最后一个是自动化测试专用组：Broker 关闭了 autoCreateSubscriptionGroup，
# 测试无法临时创建消费组；用独立组消费才不会挪动 Java/Python 正式组的位点。
for group in \
    "CID_DefaultHeartBeatSyncerTopic" \
    "${GROUP_JAVA_AGENT_EVENT:-foodmate-java-agent-event-v1}" \
    "${GROUP_JAVA_AGENT_PROPOSAL:-foodmate-java-agent-proposal-v1}" \
    "${GROUP_PYTHON_AGENT_COMMAND:-foodmate-python-agent-command-v1}" \
    "${GROUP_PYTHON_AGENT_RESULT:-foodmate-python-agent-result-v1}" \
    "${GROUP_PYTHON_KNOWLEDGE_INDEX:-foodmate-python-knowledge-index-v1}" \
    "${GROUP_JAVA_KNOWLEDGE_INDEX_RESULT:-foodmate-java-knowledge-index-result-v1}" \
    "${GROUP_PYTHON_KNOWLEDGE_VISIBILITY:-foodmate-python-knowledge-visibility-v1}" \
    "${GROUP_PYTHON_KNOWLEDGE_PURGE:-foodmate-python-knowledge-purge-v1}" \
    "${GROUP_JAVA_KNOWLEDGE_PURGE_RESULT:-foodmate-java-knowledge-purge-result-v1}" \
    "${GROUP_SELFTEST:-foodmate-selftest-v1}"; do
    echo "[foodmate] 创建 consumer group ${group}"
    # RocketMQ 5.x 只有在消费者真正订阅后才建 %RETRY% Topic，因此不能用 topicList 回读；
    # updateSubGroup 成功时会打印 "success"，把它作为校验信号。
    broadcast="false"
    if [ "$group" = "CID_DefaultHeartBeatSyncerTopic" ]; then
        # Proxy 的系统心跳主题必须广播给每个 Proxy 实例；-d 才是广播开关，-m 仅表示消费起点。
        broadcast="true"
    fi
    if ! "$MQADMIN" updateSubGroup -n "$NAMESRV" -b "$BROKER" -g "$group" \
        -s true -m false -d "$broadcast" -q 1 -w 1 2>&1 | grep -q "success"; then
        echo "[foodmate] consumer group ${group} 创建失败" >&2
        exit 1
    fi
done

echo "[foodmate] 已就绪的 FoodMate Topic："
"$MQADMIN" topicList -n "$NAMESRV" 2>/dev/null | grep '^foodmate-'
echo "[foodmate] RocketMQ 初始化完成"
