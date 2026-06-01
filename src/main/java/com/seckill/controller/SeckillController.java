package com.seckill.controller;

import com.seckill.annotation.RateLimit;
import com.seckill.result.Result;
import com.seckill.service.SeckillService;
import com.seckill.vo.SeckillResultVO;
import com.seckill.vo.SeckillSubmitVO;
import com.seckill.vo.UserSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RateLimit
@RestController
public class SeckillController {

    private final SeckillService seckillService;

    public SeckillController(SeckillService seckillService) {
        this.seckillService = seckillService;
    }

    /**
     * 秒杀入口。
     *
     * <p>UserSession 由 UserArgumentResolver 从请求头 token 自动注入。
     * 这个接口只做轻量判断、Redis 原子预扣和 Kafka 入队，不等待 MySQL 订单创建。</p>
     */
    @PostMapping("/seckill/{goodsId}")
    public Result<SeckillSubmitVO> seckill(@PathVariable Long goodsId, UserSession userSession) {
        return Result.success(seckillService.submit(goodsId, userSession));
    }

    /**
     * 秒杀结果轮询接口。
     *
     * <p>前端提交秒杀后每 1-2 秒轮询一次。先查 MySQL 是否已经生成订单；
     * 如果没有订单但 Redis ordered Set 里有用户，说明消息已入队、仍在排队处理。</p>
     */
    @GetMapping("/seckill/result/{goodsId}")
    public Result<SeckillResultVO> result(@PathVariable Long goodsId, UserSession userSession) {
        return Result.success(seckillService.getResult(goodsId, userSession));
    }
}
