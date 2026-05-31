package com.seckill.controller;

import com.seckill.service.GoodsService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GoodsController {

    private final GoodsService goodsService;

    public GoodsController(GoodsService goodsService) {
        this.goodsService = goodsService;
    }

    /**
     * 商品列表页。
     *
     * <p>这里返回的是完整 HTML 字符串，不包 Result。这样浏览器访问
     * /goods/list 时能直接看到页面，也符合“缓存整段 HTML”的文档要求。</p>
     */
    @GetMapping(value = "/goods/list", produces = MediaType.TEXT_HTML_VALUE)
    public String list() {
        return goodsService.getGoodsListPageHtml();
    }

    /**
     * 商品详情页。
     *
     * <p>非法 goodsId 会在服务层被本地合法 ID 集合拦截，不会继续查询数据库，
     * 用来规避缓存穿透。</p>
     */
    @GetMapping(value = "/goods/detail/{goodsId}", produces = MediaType.TEXT_HTML_VALUE)
    public String detail(@PathVariable Long goodsId) {
        return goodsService.getGoodsDetailPageHtml(goodsId);
    }
}
