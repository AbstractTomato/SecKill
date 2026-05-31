package com.seckill.dto;

/**
 * 秒杀 Kafka 消息体。
 *
 * <p>消息里只传 goodsId 和 userId，不传价格。Consumer 创建订单时重新从数据库
 * 查询秒杀商品信息，以数据库价格为准，避免客户端或旧消息携带的价格污染订单。</p>
 */
public class SeckillMessage {

    private Long goodsId;
    private Long userId;

    public SeckillMessage() {
    }

    public SeckillMessage(Long goodsId, Long userId) {
        this.goodsId = goodsId;
        this.userId = userId;
    }

    public Long getGoodsId() {
        return goodsId;
    }

    public void setGoodsId(Long goodsId) {
        this.goodsId = goodsId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }
}
