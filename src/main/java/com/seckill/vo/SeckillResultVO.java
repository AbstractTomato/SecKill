package com.seckill.vo;

/**
 * 秒杀轮询结果。
 *
 * <p>status 取值：
 * queued 表示 Redis 已标记抢购成功但 Consumer 还没写完 MySQL；
 * success 表示 seckill_order 已生成；
 * failed 表示没有入队记录，通常是未提交、已售罄或请求失败。</p>
 */
public class SeckillResultVO {

    private String status;
    private Long orderId;

    public SeckillResultVO() {
    }

    public SeckillResultVO(String status, Long orderId) {
        this.status = status;
        this.orderId = orderId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }
}
