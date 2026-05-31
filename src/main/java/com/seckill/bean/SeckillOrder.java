package com.seckill.bean;

/**
 * 秒杀订单关联表实体。
 *
 * <p>数据库层会给 user_id + goods_id 加唯一索引，这是 Consumer 幂等的最后防线：
 * 即使 Kafka 重复投递，也不会为同一个用户和同一个商品创建两条秒杀订单。</p>
 */
public class SeckillOrder {

    private Long id;
    private Long userId;
    private Long orderId;
    private Long goodsId;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public Long getGoodsId() {
        return goodsId;
    }

    public void setGoodsId(Long goodsId) {
        this.goodsId = goodsId;
    }
}
