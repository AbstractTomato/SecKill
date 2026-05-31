package com.seckill.bean;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 秒杀活动商品表对应的实体。
 *
 * <p>同一个 goods_id 在当前设计中只绑定一个秒杀活动，因此数据库通过
 * uk_goods_id 做唯一约束。后续如果支持多场次秒杀，可以把唯一约束调整为
 * goods_id + start_date 或活动 ID。</p>
 */
public class SeckillGoods {

    private Long id;
    private Long goodsId;
    private BigDecimal seckillPrice;
    private Integer stockCount;
    private LocalDateTime startDate;
    private LocalDateTime endDate;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getGoodsId() {
        return goodsId;
    }

    public void setGoodsId(Long goodsId) {
        this.goodsId = goodsId;
    }

    public BigDecimal getSeckillPrice() {
        return seckillPrice;
    }

    public void setSeckillPrice(BigDecimal seckillPrice) {
        this.seckillPrice = seckillPrice;
    }

    public Integer getStockCount() {
        return stockCount;
    }

    public void setStockCount(Integer stockCount) {
        this.stockCount = stockCount;
    }

    public LocalDateTime getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDateTime startDate) {
        this.startDate = startDate;
    }

    public LocalDateTime getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDateTime endDate) {
        this.endDate = endDate;
    }
}
