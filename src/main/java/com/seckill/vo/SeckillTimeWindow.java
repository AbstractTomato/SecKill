package com.seckill.vo;

import java.time.LocalDateTime;

/**
 * 秒杀时间窗口的 JVM 内存快照。
 *
 * <p>请求入口只需要判断当前时间是否在活动时间内，没有必要每次访问 Redis 或
 * MySQL。启动时加载到内存后，时间判断就是一次 Map 查询和本地时间比较。</p>
 */
public class SeckillTimeWindow {

    private Long goodsId;
    private LocalDateTime startDate;
    private LocalDateTime endDate;

    public SeckillTimeWindow() {
    }

    public SeckillTimeWindow(Long goodsId, LocalDateTime startDate, LocalDateTime endDate) {
        this.goodsId = goodsId;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public Long getGoodsId() {
        return goodsId;
    }

    public void setGoodsId(Long goodsId) {
        this.goodsId = goodsId;
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
