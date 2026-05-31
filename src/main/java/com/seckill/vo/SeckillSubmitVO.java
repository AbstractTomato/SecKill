package com.seckill.vo;

/**
 * 秒杀请求立即返回结果。
 *
 * <p>真正订单创建由 Kafka Consumer 异步完成，因此提交接口只告诉前端消息已经
 * 入队，前端再轮询 /seckill/result/{goodsId} 查询最终订单结果。</p>
 */
public class SeckillSubmitVO {

    private String status;

    public SeckillSubmitVO() {
    }

    public SeckillSubmitVO(String status) {
        this.status = status;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
