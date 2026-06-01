package com.seckill.mapper;

import com.seckill.bean.OrderInfo;
import com.seckill.bean.SeckillOrder;
import com.seckill.vo.OrderDetailVO;
import com.seckill.vo.OrderVO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface OrderMapper {

    /**
     * 查询用户是否已经生成秒杀订单。
     *
     * <p>轮询接口用它判断 Consumer 是否已经处理完成；Consumer 也用它做一次
     * 应用层幂等检查。数据库唯一索引是最终兜底。</p>
     */
    @Select("""
            SELECT id, user_id, order_id, goods_id
            FROM seckill_order
            WHERE user_id = #{userId} AND goods_id = #{goodsId}
            """)
    SeckillOrder selectSeckillOrder(@Param("userId") Long userId, @Param("goodsId") Long goodsId);

    @Select("""
            SELECT oi.id AS order_id, oi.goods_name, oi.goods_price, oi.order_status, oi.create_date
            FROM order_info oi
            INNER JOIN seckill_order so ON so.order_id = oi.id
            WHERE so.user_id = #{userId}
            ORDER BY oi.create_date DESC
            """)
    List<OrderVO> selectOrderList(@Param("userId") Long userId);

    @Select("""
            SELECT oi.id AS order_id,
                   oi.goods_id,
                   oi.goods_name,
                   g.goods_img,
                   g.goods_detail,
                   oi.goods_count,
                   oi.goods_price,
                   oi.order_status,
                   oi.create_date
            FROM order_info oi
            INNER JOIN seckill_order so ON so.order_id = oi.id
            INNER JOIN goods g ON g.id = oi.goods_id
            WHERE oi.id = #{orderId} AND so.user_id = #{userId}
            """)
    OrderDetailVO selectOrderDetail(@Param("orderId") Long orderId, @Param("userId") Long userId);

    /**
     * 插入订单主表。
     *
     * <p>useGeneratedKeys 会把自增订单 ID 回填到 orderInfo.id，随后用于写入
     * seckill_order.order_id。</p>
     */
    @Insert("""
            INSERT INTO order_info (user_id, goods_id, goods_name, goods_count, goods_price, order_status, create_date)
            VALUES (#{userId}, #{goodsId}, #{goodsName}, #{goodsCount}, #{goodsPrice}, #{orderStatus}, NOW())
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertOrderInfo(OrderInfo orderInfo);

    @Insert("""
            INSERT INTO seckill_order (user_id, order_id, goods_id)
            VALUES (#{userId}, #{orderId}, #{goodsId})
            """)
    int insertSeckillOrder(SeckillOrder seckillOrder);
}
