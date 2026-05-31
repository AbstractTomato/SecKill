package com.seckill.service;

import com.seckill.bean.OrderInfo;
import com.seckill.bean.SeckillOrder;
import com.seckill.dto.SeckillMessage;
import com.seckill.exception.BusinessException;
import com.seckill.mapper.OrderMapper;
import com.seckill.mapper.SeckillGoodsMapper;
import com.seckill.result.ResultCode;
import com.seckill.vo.GoodsVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private static final int ORDER_STATUS_NEW = 0;

    private final OrderMapper orderMapper;
    private final SeckillGoodsMapper seckillGoodsMapper;

    public OrderService(OrderMapper orderMapper, SeckillGoodsMapper seckillGoodsMapper) {
        this.orderMapper = orderMapper;
        this.seckillGoodsMapper = seckillGoodsMapper;
    }

    @Transactional
    public void createSeckillOrder(SeckillMessage message) {
        /*
         * 应用层幂等检查：Kafka 可能重复投递，重复消息直接跳过。
         * 数据库唯一索引 uk_user_goods 是最终兜底，防止并发重复写。
         */
        SeckillOrder existingOrder = orderMapper.selectSeckillOrder(message.getUserId(), message.getGoodsId());
        if (existingOrder != null) {
            return;
        }

        GoodsVO goods = seckillGoodsMapper.selectGoodsForOrder(message.getGoodsId());
        if (goods == null) {
            throw new BusinessException(ResultCode.GOODS_NOT_FOUND);
        }

        /*
         * MySQL 乐观扣库存。
         * Redis 已经预扣库存，但落库阶段仍用 stock_count > 0 做第二道防线；
         * affected rows 为 0 说明数据库库存异常或已经没有库存，交给 Consumer 重试/DLT。
         */
        int affectedRows = seckillGoodsMapper.reduceStock(message.getGoodsId());
        if (affectedRows == 0) {
            throw new BusinessException(ResultCode.SECKILL_SOLD_OUT);
        }

        OrderInfo orderInfo = new OrderInfo();
        orderInfo.setUserId(message.getUserId());
        orderInfo.setGoodsId(message.getGoodsId());
        orderInfo.setGoodsName(goods.getGoodsName());
        orderInfo.setGoodsCount(1);
        orderInfo.setGoodsPrice(goods.getSeckillPrice());
        orderInfo.setOrderStatus(ORDER_STATUS_NEW);
        orderMapper.insertOrderInfo(orderInfo);

        SeckillOrder seckillOrder = new SeckillOrder();
        seckillOrder.setUserId(message.getUserId());
        seckillOrder.setOrderId(orderInfo.getId());
        seckillOrder.setGoodsId(message.getGoodsId());
        orderMapper.insertSeckillOrder(seckillOrder);
    }
}
