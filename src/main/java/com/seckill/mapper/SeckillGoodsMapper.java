package com.seckill.mapper;

import com.seckill.vo.GoodsVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SeckillGoodsMapper {

    /**
     * 乐观扣减 MySQL 秒杀库存。
     *
     * <p>Redis 已经做过预扣库存，但 Consumer 落库时仍然要用 stock_count > 0
     * 做防线。MySQL 会对命中的行加行锁，多个 Consumer 并发时也能串行化扣减。</p>
     */
    @Update("""
            UPDATE seckill_goods
            SET stock_count = stock_count - 1
            WHERE goods_id = #{goodsId} AND stock_count > 0
            """)
    int reduceStock(Long goodsId);

    /**
     * Consumer 创建订单时重新查询商品和秒杀价格。
     *
     * <p>价格以数据库为准，不信任 Kafka 消息中的任何价格字段。</p>
     */
    @Select("""
            SELECT
                g.id,
                g.goods_name,
                g.goods_title,
                g.goods_img,
                g.goods_detail,
                g.goods_price,
                g.goods_stock,
                sg.seckill_price,
                sg.stock_count,
                sg.start_date,
                sg.end_date
            FROM goods g
            INNER JOIN seckill_goods sg ON sg.goods_id = g.id
            WHERE g.id = #{goodsId}
            """)
    GoodsVO selectGoodsForOrder(Long goodsId);
}
