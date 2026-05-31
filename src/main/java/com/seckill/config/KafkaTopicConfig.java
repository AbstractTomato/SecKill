package com.seckill.config;

import com.seckill.utils.KafkaTopicConstants;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    /**
     * 主秒杀订单 Topic。
     *
     * <p>本地开发时如果 Kafka 开启自动建 Topic，这个 Bean 不是必须的；
     * 但显式声明可以让应用启动时通过 KafkaAdmin 创建 Topic，配置更可见。</p>
     */
    @Bean
    public NewTopic seckillOrderTopic() {
        return TopicBuilder.name(KafkaTopicConstants.SECKILL_ORDER_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    /**
     * 死信 Topic。
     *
     * <p>Consumer 连续失败超过 3 次后把消息转入这里，避免单条坏消息一直阻塞
     * 正常秒杀订单消费。</p>
     */
    @Bean
    public NewTopic seckillOrderDltTopic() {
        return TopicBuilder.name(KafkaTopicConstants.SECKILL_ORDER_DLT_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
