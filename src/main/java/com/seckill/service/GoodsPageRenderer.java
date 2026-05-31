package com.seckill.service;

import com.seckill.utils.HtmlEscapeUtil;
import com.seckill.vo.GoodsVO;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 商品页面 HTML 渲染器。
 *
 * <p>文档要求缓存“整段 HTML”，所以这里不走 Thymeleaf 模板渲染，而是由服务层
 * 直接生成最终 HTML 字符串并写入 Redis。缓存命中后 Controller 可以直接返回
 * text/html，省掉模板解析、模型组装和数据库查询。</p>
 */
@Component
public class GoodsPageRenderer {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public String renderListPage(List<GoodsVO> goodsList) {
        StringBuilder builder = new StringBuilder(8192);
        builder.append("""
                <!doctype html>
                <html lang="zh-CN">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <title>秒杀商品列表</title>
                  <style>
                    body{margin:0;background:#f5f7fb;color:#1f2937;font-family:Arial,"Microsoft YaHei",sans-serif;}
                    .wrap{max-width:1120px;margin:0 auto;padding:28px 18px;}
                    h1{font-size:28px;margin:0 0 18px;}
                    .grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(240px,1fr));gap:16px;}
                    .card{background:#fff;border:1px solid #e5e7eb;border-radius:8px;padding:14px;}
                    .card img{width:100%;aspect-ratio:1/1;object-fit:cover;border-radius:6px;background:#eef2f7;}
                    .name{font-size:18px;font-weight:700;margin:12px 0 6px;}
                    .title{min-height:42px;color:#6b7280;font-size:14px;line-height:1.5;}
                    .price{display:flex;align-items:baseline;gap:8px;margin-top:10px;}
                    .seckill{color:#dc2626;font-size:22px;font-weight:700;}
                    .origin{color:#9ca3af;text-decoration:line-through;}
                    .meta{display:flex;justify-content:space-between;margin-top:10px;color:#4b5563;font-size:13px;}
                    a{display:inline-block;margin-top:12px;color:#2563eb;text-decoration:none;font-weight:700;}
                  </style>
                </head>
                <body>
                  <main class="wrap">
                    <h1>秒杀商品列表</h1>
                    <section class="grid">
                """);

        for (GoodsVO goods : goodsList) {
            builder.append("""
                        <article class="card">
                          <img src="%s" alt="%s">
                          <div class="name">%s</div>
                          <div class="title">%s</div>
                          <div class="price">
                            <span class="seckill">¥%s</span>
                            <span class="origin">¥%s</span>
                          </div>
                          <div class="meta">
                            <span>库存 %d</span>
                            <span>%s 开始</span>
                          </div>
                          <a href="/goods/detail/%d">查看详情</a>
                        </article>
                    """.formatted(
                    HtmlEscapeUtil.escape(goods.getGoodsImg()),
                    HtmlEscapeUtil.escape(goods.getGoodsName()),
                    HtmlEscapeUtil.escape(goods.getGoodsName()),
                    HtmlEscapeUtil.escape(goods.getGoodsTitle()),
                    formatPrice(goods.getSeckillPrice()),
                    formatPrice(goods.getGoodsPrice()),
                    goods.getStockCount(),
                    formatDate(goods),
                    goods.getId()
            ));
        }

        builder.append("""
                    </section>
                  </main>
                </body>
                </html>
                """);
        return builder.toString();
    }

    public String renderDetailPage(GoodsVO goods) {
        return """
                <!doctype html>
                <html lang="zh-CN">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <title>%s</title>
                  <style>
                    body{margin:0;background:#f5f7fb;color:#1f2937;font-family:Arial,"Microsoft YaHei",sans-serif;}
                    .wrap{max-width:960px;margin:0 auto;padding:28px 18px;}
                    .layout{display:grid;grid-template-columns:minmax(260px,360px) 1fr;gap:24px;background:#fff;border:1px solid #e5e7eb;border-radius:8px;padding:18px;}
                    img{width:100%;aspect-ratio:1/1;object-fit:cover;border-radius:6px;background:#eef2f7;}
                    h1{font-size:28px;margin:0 0 10px;}
                    .title{color:#6b7280;line-height:1.6;margin-bottom:18px;}
                    .price{display:flex;align-items:baseline;gap:10px;margin:12px 0;}
                    .seckill{color:#dc2626;font-size:30px;font-weight:700;}
                    .origin{color:#9ca3af;text-decoration:line-through;}
                    .row{margin:10px 0;color:#4b5563;}
                    .detail{margin-top:18px;line-height:1.8;}
                    .back{display:inline-block;margin-bottom:14px;color:#2563eb;text-decoration:none;font-weight:700;}
                    @media(max-width:720px){.layout{grid-template-columns:1fr;}}
                  </style>
                </head>
                <body>
                  <main class="wrap">
                    <a class="back" href="/goods/list">返回列表</a>
                    <section class="layout">
                      <img src="%s" alt="%s">
                      <div>
                        <h1>%s</h1>
                        <div class="title">%s</div>
                        <div class="price">
                          <span class="seckill">¥%s</span>
                          <span class="origin">¥%s</span>
                        </div>
                        <div class="row">秒杀库存：%d</div>
                        <div class="row">活动时间：%s 至 %s</div>
                        <div class="detail">%s</div>
                      </div>
                    </section>
                  </main>
                </body>
                </html>
                """.formatted(
                HtmlEscapeUtil.escape(goods.getGoodsName()),
                HtmlEscapeUtil.escape(goods.getGoodsImg()),
                HtmlEscapeUtil.escape(goods.getGoodsName()),
                HtmlEscapeUtil.escape(goods.getGoodsName()),
                HtmlEscapeUtil.escape(goods.getGoodsTitle()),
                formatPrice(goods.getSeckillPrice()),
                formatPrice(goods.getGoodsPrice()),
                goods.getStockCount(),
                goods.getStartDate().format(DATE_TIME_FORMATTER),
                goods.getEndDate().format(DATE_TIME_FORMATTER),
                HtmlEscapeUtil.escape(goods.getGoodsDetail())
        );
    }

    private String formatPrice(BigDecimal price) {
        return price == null ? "0.00" : price.setScale(2).toPlainString();
    }

    private String formatDate(GoodsVO goods) {
        return goods.getStartDate() == null ? "" : goods.getStartDate().format(DATE_TIME_FORMATTER);
    }
}
