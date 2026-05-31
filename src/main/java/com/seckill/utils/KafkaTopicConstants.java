package com.seckill.utils;

/**
 * Kafka Topic 常量集中放置，避免 Producer 和 Consumer 里各写各的字符串。
 */
public final class KafkaTopicConstants {

    public static final String SECKILL_ORDER_TOPIC = "seckill-order";
    public static final String SECKILL_ORDER_DLT_TOPIC = "seckill-order-dlt";

    private KafkaTopicConstants() {
    }
}
