package com.seckill.config;

import com.seckill.service.GoodsService;
import com.seckill.service.SeckillService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class StockPreloadRunner implements ApplicationRunner {

    private final GoodsService goodsService;
    private final SeckillService seckillService;

    public StockPreloadRunner(GoodsService goodsService, SeckillService seckillService) {
        this.goodsService = goodsService;
        this.seckillService = seckillService;
    }

    /**
     * 应用启动后预热秒杀库存。
     *
     * <p>秒杀核心链路后续会优先扣 Redis 库存，而不是每次请求都访问 MySQL。
     * 因此服务启动时先扫描当前秒杀商品，把 stock_count 写入
     * seckill:stock:&lt;goodsId&gt;。这里不设置 TTL，活动结束后再统一清理或回写。</p>
     */
    @Override
    public void run(ApplicationArguments args) {
        goodsService.preloadStockToRedis();
        seckillService.loadTimeWindows();
    }
}
