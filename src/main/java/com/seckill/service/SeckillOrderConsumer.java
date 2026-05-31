package com.seckill.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seckill.dto.SeckillMessage;
import com.seckill.exception.BusinessException;
import com.seckill.utils.KafkaTopicConstants;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class SeckillOrderConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(SeckillOrderConsumer.class);
    private static final int MAX_RETRY_TIMES = 3;
    private static final long RETRY_BACKOFF_BASE_MILLIS = 1000L;
    private static final long RETRY_BACKOFF_MAX_MILLIS = 5000L;

    private final ObjectMapper objectMapper;
    private final OrderService orderService;
    private final SeckillService seckillService;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public SeckillOrderConsumer(ObjectMapper objectMapper,
                                OrderService orderService,
                                SeckillService seckillService,
                                KafkaTemplate<String, String> kafkaTemplate) {
        this.objectMapper = objectMapper;
        this.orderService = orderService;
        this.seckillService = seckillService;
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * 秒杀订单 Consumer。
     *
     * <p>手动提交 offset：只有订单处理成功、重复消息被安全跳过、或消息进入 DLT 后
     * 才 ack。中途异常不 ack，让 Kafka 保留这条消息用于重试。</p>
     */
    @KafkaListener(topics = KafkaTopicConstants.SECKILL_ORDER_TOPIC)
    public void onMessage(ConsumerRecord<String, String> record, Acknowledgment ack) {
        SeckillMessage message = null;
        try {
            message = objectMapper.readValue(record.value(), SeckillMessage.class);

            /*
             * Redis 去重：只有 PROCESSED 才认为处理完成。
             * Producer 写入的 SENT 只是“已尝试发送”，Consumer 不能因为 SENT 跳过落库。
             */
            if (seckillService.isMessageProcessed(message.getGoodsId(), message.getUserId())) {
                ack.acknowledge();
                return;
            }

            orderService.createSeckillOrder(message);
            seckillService.markMessageProcessed(message.getGoodsId(), message.getUserId());
            seckillService.clearConsumerRetry(message.getGoodsId(), message.getUserId());
            ack.acknowledge();
        } catch (DuplicateKeyException e) {
            /*
             * 数据库唯一索引兜底命中，说明订单已经存在。
             * 这种场景可以视为幂等成功，标记消息已处理并 ack。
             */
            if (message != null) {
                seckillService.markMessageProcessed(message.getGoodsId(), message.getUserId());
                seckillService.clearConsumerRetry(message.getGoodsId(), message.getUserId());
            }
            ack.acknowledge();
        } catch (Exception e) {
            handleFailure(record, ack, message, e);
        }
    }

    private void handleFailure(ConsumerRecord<String, String> record,
                               Acknowledgment ack,
                               SeckillMessage message,
                               Exception e) {
        if (message == null) {
            sendToDeadLetter(record, "message-parse-error");
            ack.acknowledge();
            return;
        }

        if (isNonRetryableFailure(e)) {
            LOGGER.error("Non-retryable seckill message failure, value={}", record.value(), e);
            sendToDeadLetter(record, e.getMessage());
            seckillService.clearConsumerRetry(message.getGoodsId(), message.getUserId());
            ack.acknowledge();
            return;
        }

        long retryTimes = seckillService.increaseConsumerRetry(message.getGoodsId(), message.getUserId());
        LOGGER.error("Failed to consume seckill message, retryTimes={}, value={}", retryTimes, record.value(), e);

        if (retryTimes >= MAX_RETRY_TIMES) {
            sendToDeadLetter(record, e.getMessage());
            seckillService.clearConsumerRetry(message.getGoodsId(), message.getUserId());
            ack.acknowledge();
            return;
        }

        /*
         * 不 ack 并抛出异常，让 Spring Kafka 保持这条消息未确认。
         * 抛出前先做短退避，避免临时故障时在极短时间内打满 3 次重试。
         */
        sleepBeforeRetry(retryTimes);
        throw new IllegalStateException("Seckill message consume failed, waiting retry", e);
    }

    private boolean isNonRetryableFailure(Exception e) {
        return e instanceof BusinessException;
    }

    private void sleepBeforeRetry(long retryTimes) {
        long backoffMillis = Math.min(RETRY_BACKOFF_BASE_MILLIS * retryTimes, RETRY_BACKOFF_MAX_MILLIS);
        try {
            Thread.sleep(backoffMillis);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting to retry seckill message", interruptedException);
        }
    }

    private void sendToDeadLetter(ConsumerRecord<String, String> record, String reason) {
        try {
            String key = record.key() == null ? "unknown" : record.key();
            String value = "{\"reason\":\"" + escape(reason) + "\",\"payload\":" + quote(record.value()) + "}";
            kafkaTemplate.send(KafkaTopicConstants.SECKILL_ORDER_DLT_TOPIC, key, value).get(3, TimeUnit.SECONDS);
        } catch (Exception sendException) {
            LOGGER.error("Failed to send seckill message to DLT, value={}", record.value(), sendException);
            throw new IllegalStateException("Failed to send message to DLT", sendException);
        }
    }

    private String quote(String value) {
        return "\"" + escape(value) + "\"";
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
