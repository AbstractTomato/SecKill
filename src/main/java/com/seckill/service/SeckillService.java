package com.seckill.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seckill.bean.SeckillOrder;
import com.seckill.dto.SeckillMessage;
import com.seckill.exception.BusinessException;
import com.seckill.mapper.OrderMapper;
import com.seckill.result.ResultCode;
import com.seckill.utils.KafkaTopicConstants;
import com.seckill.utils.RedisKeyUtil;
import com.seckill.vo.GoodsVO;
import com.seckill.vo.SeckillResultVO;
import com.seckill.vo.SeckillSubmitVO;
import com.seckill.vo.SeckillTimeWindow;
import com.seckill.vo.UserSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SeckillService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SeckillService.class);
    private static final Duration MESSAGE_DEDUP_TTL = Duration.ofMinutes(5);
    private static final Duration SECKILL_RATE_LIMIT_TTL = Duration.ofSeconds(1);
    private static final String MESSAGE_STATUS_SENT = "SENT";
    private static final String MESSAGE_STATUS_PROCESSED = "PROCESSED";

    /**
     * 主 Lua 脚本：判重 + 扣库存 + 标记已抢。
     *
     * <p>如果把 SISMEMBER、DECR、SADD 拆成三次 Redis 调用，应用在中间任意一步
     * 崩溃都可能留下不一致状态。Lua 在 Redis 单线程中一次执行完成，保证这三个
     * 动作对外表现为一个原子操作。</p>
     *
     * <p>返回值约定：
     * -1 表示用户已抢过；
     * -2 表示库存不足；
     * 大于等于 0 表示扣减成功，值为扣减后的剩余库存。</p>
     */
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT = new DefaultRedisScript<>("""
            local isMember = redis.call('SISMEMBER', KEYS[1], ARGV[1])
            if isMember == 1 then
                return -1
            end

            local stock = redis.call('DECR', KEYS[2])
            if stock < 0 then
                redis.call('INCR', KEYS[2])
                return -2
            end

            redis.call('SADD', KEYS[1], ARGV[1])
            return stock
            """, Long.class);

    /**
     * Kafka 发送失败时的 Redis 回滚脚本。
     *
     * <p>入口请求已经扣了 Redis 库存并把用户写入 ordered Set，只有消息成功进入
     * Kafka 后异步订单才有机会创建；如果发送失败，必须补回库存并移除已抢标记。</p>
     */
    private static final DefaultRedisScript<Long> ROLLBACK_SCRIPT = new DefaultRedisScript<>("""
            redis.call('INCR', KEYS[1])
            redis.call('SREM', KEYS[2], ARGV[1])
            return 1
            """, Long.class);

    private final GoodsService goodsService;
    private final OrderMapper orderMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final Map<Long, SeckillTimeWindow> timeWindowMap = new ConcurrentHashMap<>();

    public SeckillService(GoodsService goodsService,
                          OrderMapper orderMapper,
                          StringRedisTemplate stringRedisTemplate,
                          KafkaTemplate<String, String> kafkaTemplate,
                          ObjectMapper objectMapper) {
        this.goodsService = goodsService;
        this.orderMapper = orderMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public void loadTimeWindows() {
        /*
         * 商品列表缓存已经包含 start_date/end_date，复用它可以避免额外 Mapper。
         * 这里把时间窗口复制到 JVM Map，请求入口只做本地判断。
         */
        List<GoodsVO> goodsList = goodsService.getGoodsList();
        timeWindowMap.clear();
        for (GoodsVO goods : goodsList) {
            timeWindowMap.put(goods.getId(), new SeckillTimeWindow(goods.getId(), goods.getStartDate(), goods.getEndDate()));
        }
    }

    public SeckillSubmitVO submit(Long goodsId, UserSession userSession) {
        checkTimeWindow(goodsId);

        Long userId = userSession.getId();
        checkSeckillRateLimit(goodsId, userId);

        String orderedKey = RedisKeyUtil.seckillOrderedKey(goodsId);
        String stockKey = RedisKeyUtil.seckillStockKey(goodsId);
        String userIdText = String.valueOf(userId);

        Long scriptResult = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                List.of(orderedKey, stockKey),
                userIdText
        );
        if (Objects.equals(scriptResult, -1L)) {
            throw new BusinessException(ResultCode.SECKILL_REPEAT);
        }
        if (Objects.equals(scriptResult, -2L)) {
            throw new BusinessException(ResultCode.SECKILL_SOLD_OUT);
        }
        if (scriptResult == null) {
            throw new BusinessException(ResultCode.SECKILL_BUSY);
        }

        /*
         * Producer 侧消息去重标记。
         * SENT 表示已经尝试发送入队；PROCESSED 表示 Consumer 已经完成落库。
         * Consumer 只把 PROCESSED 当成“已处理”，不会因为 SENT 而跳过。
         */
        String messageKey = RedisKeyUtil.seckillMessageKey(goodsId, userId);
        Boolean firstMessage = stringRedisTemplate.opsForValue()
                .setIfAbsent(messageKey, MESSAGE_STATUS_SENT, MESSAGE_DEDUP_TTL);
        if (!Boolean.TRUE.equals(firstMessage)) {
            return new SeckillSubmitVO("queuing");
        }

        try {
            String messageJson = objectMapper.writeValueAsString(new SeckillMessage(goodsId, userId));
            kafkaTemplate.send(KafkaTopicConstants.SECKILL_ORDER_TOPIC, goodsId + ":" + userId, messageJson)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            rollbackKafkaSendFailure(goodsId, userId, messageKey, ex);
                        }
                    });
            return new SeckillSubmitVO("queuing");
        } catch (Exception e) {
            rollbackKafkaSendFailure(goodsId, userId, messageKey, e);
            throw new BusinessException(ResultCode.SECKILL_BUSY);
        }
    }

    public SeckillResultVO getResult(Long goodsId, UserSession userSession) {
        checkKnownGoods(goodsId);

        SeckillOrder seckillOrder = orderMapper.selectSeckillOrder(userSession.getId(), goodsId);
        if (seckillOrder != null) {
            return new SeckillResultVO("success", seckillOrder.getOrderId());
        }

        Boolean queued = stringRedisTemplate.opsForSet()
                .isMember(RedisKeyUtil.seckillOrderedKey(goodsId), String.valueOf(userSession.getId()));
        if (Boolean.TRUE.equals(queued)) {
            return new SeckillResultVO("queued", null);
        }

        return new SeckillResultVO("failed", null);
    }

    public boolean isMessageProcessed(Long goodsId, Long userId) {
        return MESSAGE_STATUS_PROCESSED.equals(stringRedisTemplate.opsForValue().get(RedisKeyUtil.seckillMessageKey(goodsId, userId)));
    }

    public void markMessageProcessed(Long goodsId, Long userId) {
        stringRedisTemplate.opsForValue().set(RedisKeyUtil.seckillMessageKey(goodsId, userId), MESSAGE_STATUS_PROCESSED, MESSAGE_DEDUP_TTL);
    }

    public long increaseConsumerRetry(Long goodsId, Long userId) {
        String retryKey = RedisKeyUtil.seckillRetryKey(goodsId, userId);
        Long retryTimes = stringRedisTemplate.opsForValue().increment(retryKey);
        stringRedisTemplate.expire(retryKey, MESSAGE_DEDUP_TTL);
        return retryTimes == null ? 1L : retryTimes;
    }

    public void clearConsumerRetry(Long goodsId, Long userId) {
        stringRedisTemplate.delete(RedisKeyUtil.seckillRetryKey(goodsId, userId));
    }

    private void rollbackRedisState(Long goodsId, Long userId) {
        stringRedisTemplate.execute(
                ROLLBACK_SCRIPT,
                List.of(RedisKeyUtil.seckillStockKey(goodsId), RedisKeyUtil.seckillOrderedKey(goodsId)),
                String.valueOf(userId)
        );
    }

    private void rollbackKafkaSendFailure(Long goodsId, Long userId, String messageKey, Throwable ex) {
        try {
            rollbackRedisState(goodsId, userId);
            stringRedisTemplate.delete(messageKey);
        } catch (Exception rollbackException) {
            LOGGER.error("Failed to rollback Redis state after Kafka send failure, goodsId={}, userId={}",
                    goodsId, userId, rollbackException);
        }
        LOGGER.error("Failed to send seckill message to Kafka, goodsId={}, userId={}", goodsId, userId, ex);
    }

    private void checkSeckillRateLimit(Long goodsId, Long userId) {
        Boolean allowed = stringRedisTemplate.opsForValue().setIfAbsent(
                RedisKeyUtil.seckillRateKey(goodsId, userId),
                "1",
                SECKILL_RATE_LIMIT_TTL
        );
        if (!Boolean.TRUE.equals(allowed)) {
            throw new BusinessException(ResultCode.RATE_LIMITED);
        }
    }

    private void checkTimeWindow(Long goodsId) {
        SeckillTimeWindow timeWindow = getTimeWindow(goodsId);
        LocalDateTime now = LocalDateTime.now();

        if (now.isBefore(timeWindow.getStartDate())) {
            throw new BusinessException(ResultCode.SECKILL_NOT_STARTED);
        }
        if (now.isAfter(timeWindow.getEndDate())) {
            throw new BusinessException(ResultCode.SECKILL_ENDED);
        }
    }

    private void checkKnownGoods(Long goodsId) {
        getTimeWindow(goodsId);
    }

    private SeckillTimeWindow getTimeWindow(Long goodsId) {
        if (goodsId == null || goodsId <= 0) {
            throw new BusinessException(ResultCode.GOODS_NOT_FOUND);
        }

        if (timeWindowMap.isEmpty()) {
            loadTimeWindows();
        }

        SeckillTimeWindow timeWindow = timeWindowMap.get(goodsId);
        if (timeWindow == null) {
            throw new BusinessException(ResultCode.GOODS_NOT_FOUND);
        }
        return timeWindow;
    }
}
