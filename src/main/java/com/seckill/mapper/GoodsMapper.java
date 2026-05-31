package com.seckill.mapper;

import com.seckill.vo.GoodsVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface GoodsMapper {

    /**
     * 查询当前所有秒杀商品。
     *
     * <p>页面展示需要 goods 的静态信息和 seckill_goods 的活动信息，
     * 因此这里直接做关联查询，返回给服务层统一缓存。</p>
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
            ORDER BY g.id ASC
            """)
    List<GoodsVO> selectGoodsVOList();

    /**
     * 查询单个秒杀商品详情。
     *
     * <p>goods_id 在 seckill_goods 中有唯一约束，所以最多只会返回一条
     * 活动记录。不存在时返回 null，由服务层统一转成业务错误。</p>
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
    GoodsVO selectGoodsVOById(Long goodsId);
}
