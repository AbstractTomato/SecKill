package com.seckill.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seckill.exception.BusinessException;
import com.seckill.mapper.GoodsMapper;
import com.seckill.result.ResultCode;
import com.seckill.utils.RedisKeyUtil;
import com.seckill.vo.GoodsVO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class GoodsService {

    private static final Duration CACHE_LOCK_TTL = Duration.ofSeconds(10);
    private static final int CACHE_RETRY_TIMES = 10;
    private static final long CACHE_RETRY_SLEEP_MILLIS = 50L;
    private static final TypeReference<List<GoodsVO>> GOODS_LIST_TYPE = new TypeReference<>() {
    };

    private final GoodsMapper goodsMapper;
    private final GoodsPageRenderer goodsPageRenderer;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 本地合法商品 ID 集合。
     *
     * <p>文档要求“5 个商品 ID 预知，非法 goodsId 直接返回参数错误，不查 DB”。
     * 启动库存预热时会从 seckill_goods 关联查询结果中加载合法 ID，详情请求
     * 先检查这个集合；不在集合内的请求会直接失败，避免缓存穿透。</p>
     */
    private final Set<Long> validGoodsIds = ConcurrentHashMap.newKeySet();

    public GoodsService(GoodsMapper goodsMapper,
                        GoodsPageRenderer goodsPageRenderer,
                        StringRedisTemplate stringRedisTemplate,
                        ObjectMapper objectMapper) {
        this.goodsMapper = goodsMapper;
        this.goodsPageRenderer = goodsPageRenderer;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }

    public String getGoodsListPageHtml() {
        String pageKey = RedisKeyUtil.goodsListPageKey();
        String cachedHtml = stringRedisTemplate.opsForValue().get(pageKey);
        if (cachedHtml != null) {
            return cachedHtml;
        }

        /*
         * 页面缓存未命中时，先拿商品数据缓存，再渲染整段 HTML。
         * HTML key 不设置 TTL，避免热点页面在同一时间集中失效造成雪崩。
         */
        List<GoodsVO> goodsList = getGoodsList();
        String html = goodsPageRenderer.renderListPage(goodsList);
        stringRedisTemplate.opsForValue().set(pageKey, html);
        return html;
    }

    public String getGoodsDetailPageHtml(Long goodsId) {
        assertValidGoodsId(goodsId);

        String pageKey = RedisKeyUtil.goodsDetailPageKey(goodsId);
        String cachedHtml = stringRedisTemplate.opsForValue().get(pageKey);
        if (cachedHtml != null) {
            return cachedHtml;
        }

        /*
         * 详情页也使用 goods:list 数据缓存中的 GoodsVO，减少单商品 DB 查询。
         * 对本项目当前 5 个商品的规模来说，列表缓存足够轻，也更容易保持一致。
         */
        GoodsVO goods = getGoodsList().stream()
                .filter(item -> Objects.equals(item.getId(), goodsId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ResultCode.GOODS_NOT_FOUND));

        String html = goodsPageRenderer.renderDetailPage(goods);
        stringRedisTemplate.opsForValue().set(pageKey, html);
        return html;
    }

    public List<GoodsVO> getGoodsList() {
        List<GoodsVO> cachedGoodsList = readGoodsListFromCache();
        if (cachedGoodsList != null) {
            return cachedGoodsList;
        }
        return rebuildGoodsListCacheWithLock();
    }

    public void preloadStockToRedis() {
        List<GoodsVO> goodsList = getGoodsList();
        refreshValidGoodsIds(goodsList);

        /*
         * 秒杀时库存扣减走 Redis DECR，这里在应用启动时预热库存。
         * key 不设置 TTL，活动结束或后台同步库存后再由管理逻辑统一清理。
         */
        for (GoodsVO goods : goodsList) {
            stringRedisTemplate.opsForValue().set(
                    RedisKeyUtil.seckillStockKey(goods.getId()),
                    String.valueOf(goods.getStockCount())
            );
        }
    }

    public void clearGoodsPageCache(Long goodsId) {
        /*
         * 后续管理后台修改商品信息、秒杀结束、库存归零时可以复用这个方法。
         * goods:list 是服务端渲染的数据缓存，列表页和详情页是最终 HTML 缓存。
         */
        Set<Long> detailGoodsIds = detailGoodsIdsToClear(goodsId);
        stringRedisTemplate.delete(RedisKeyUtil.goodsListKey());
        stringRedisTemplate.delete(RedisKeyUtil.goodsListPageKey());
        for (Long detailGoodsId : detailGoodsIds) {
            stringRedisTemplate.delete(RedisKeyUtil.goodsDetailPageKey(detailGoodsId));
        }
    }

    private Set<Long> detailGoodsIdsToClear(Long goodsId) {
        if (goodsId != null) {
            return Set.of(goodsId);
        }

        if (validGoodsIds.isEmpty()) {
            getGoodsList();
        }
        return new HashSet<>(validGoodsIds);
    }

    private List<GoodsVO> rebuildGoodsListCacheWithLock() {
        String lockKey = RedisKeyUtil.goodsListLockKey();
        Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", CACHE_LOCK_TTL);

        if (Boolean.TRUE.equals(locked)) {
            try {
                /*
                 * 双重检查：拿到锁后再读一次缓存。
                 * 如果前一个线程刚好已经重建完成，当前线程无需再查 DB。
                 */
                List<GoodsVO> cachedGoodsList = readGoodsListFromCache();
                if (cachedGoodsList != null) {
                    return cachedGoodsList;
                }

                List<GoodsVO> goodsList = goodsMapper.selectGoodsVOList();
                refreshValidGoodsIds(goodsList);
                writeGoodsListToCache(goodsList);
                return goodsList;
            } finally {
                stringRedisTemplate.delete(lockKey);
            }
        }

        /*
         * 没拿到锁的线程短暂自旋读取缓存。
         * 重建一般只需要一次 DB 查询和一次 Redis 写入，短轮询比所有线程一起查 DB 更稳。
         */
        for (int i = 0; i < CACHE_RETRY_TIMES; i++) {
            sleepQuietly();
            List<GoodsVO> cachedGoodsList = readGoodsListFromCache();
            if (cachedGoodsList != null) {
                return cachedGoodsList;
            }
        }

        /*
         * 极端情况下锁持有线程异常或耗时过长，这里递归重试一次完整流程。
         * 锁本身有 10 秒 TTL，不会永久阻塞。
         */
        return rebuildGoodsListCacheWithLock();
    }

    private List<GoodsVO> readGoodsListFromCache() {
        String json = stringRedisTemplate.opsForValue().get(RedisKeyUtil.goodsListKey());
        if (json == null) {
            return null;
        }

        try {
            List<GoodsVO> goodsList = objectMapper.readValue(json, GOODS_LIST_TYPE);
            refreshValidGoodsIds(goodsList);
            return goodsList;
        } catch (JsonProcessingException e) {
            /*
             * 缓存内容解析失败时删除坏缓存，让下一次请求走重建流程。
             * 这比继续返回错误数据更可控。
             */
            stringRedisTemplate.delete(RedisKeyUtil.goodsListKey());
            return null;
        }
    }

    private void writeGoodsListToCache(List<GoodsVO> goodsList) {
        try {
            stringRedisTemplate.opsForValue().set(RedisKeyUtil.goodsListKey(), objectMapper.writeValueAsString(goodsList));
        } catch (JsonProcessingException e) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR);
        }
    }

    private void assertValidGoodsId(Long goodsId) {
        if (goodsId == null || goodsId <= 0) {
            throw new BusinessException(ResultCode.GOODS_NOT_FOUND);
        }

        /*
         * 如果应用刚启动且预热还没完成，先触发一次列表加载来填充 validGoodsIds。
         * 正常情况下 ApplicationRunner 会提前完成这一步。
         */
        if (validGoodsIds.isEmpty()) {
            getGoodsList();
        }

        if (!validGoodsIds.contains(goodsId)) {
            throw new BusinessException(ResultCode.GOODS_NOT_FOUND);
        }
    }

    private void refreshValidGoodsIds(List<GoodsVO> goodsList) {
        Set<Long> latestIds = new HashSet<>();
        for (GoodsVO goods : goodsList) {
            latestIds.add(goods.getId());
        }
        validGoodsIds.clear();
        validGoodsIds.addAll(latestIds);
    }

    private void sleepQuietly() {
        try {
            Thread.sleep(CACHE_RETRY_SLEEP_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ResultCode.SYSTEM_ERROR);
        }
    }
}
