# seckill-kafka 秒杀电商项目

> 全新秒杀电商项目，Spring Boot 3.2.7 + JDK 17 + Kafka + Redis + MySQL。

---

# 一、用户模块

## 1. 密码加密（两层 MD5）

| 层级 | 位置 | 算法 | 说明 |
|------|------|------|------|
| 第一层 | 客户端（前端） | `MD5(原始密码)` → 表单密码 | 防止明文传输 |
| 第二层 | 服务端 | `MD5(表单密码 + salt)` → 数据库密码 | 防拖库后被反查 |

- 即使数据库泄露，攻击者也无法拿到原始密码。
- salt 每个用户独立生成，存入数据库 `salt` 字段。

## 2. 验证码校验（Redis，拦截 DB 请求）

用户在真正查询数据库之前，必须先通过验证码校验。

### 生成规则

- 长度：**6 位**
- 字符集：大写字母（A-Z）+ 小写字母（a-z）+ 数字（0-9）
- 硬性要求：**至少包含 2 个数字**
- 区分大小写

### 存储策略

- 存储位置：**Redis**
- Key：`captcha:<手机号>`
- Value：6 位验证码原始字符串（如 `aB3xY9`）
- TTL：**60 秒**，过期自动清除

### 展示方式

- 服务端生成验证码图片（含干扰线/扭曲），返回 base64 图片给前端
- 用户肉眼识别后手动输入

## 3. 整体流程

```
用户输入手机号 + 密码
    ↓
服务端生成 6 位验证码 → 存 Redis（key=captcha:手机号, TTL=60s）
    ↓
返回验证码图片（base64）给前端
    ↓
用户输入验证码，提交 { phone, password, captcha }
    ↓
服务端从 Redis 取 captcha:phone 的值
    ↓
    Redis 里没有？→ 返回"验证码已失效，请刷新"
    ↓
    用户输入 === Redis 值？
      ├─ 否 → 自动刷新验证码（删旧 key，生新值，返新图片），返回"验证码错误"
      └─ 是 → 删掉 Redis key（一次性使用），继续下一步
              ↓
              查 MySQL，用户是否存在？
                ├─ 存在 → 校验密码，登录成功，生成 token 存 Redis
                └─ 不存在 → 注册新用户（加密密码 + 生成 salt），写入 MySQL
                            ↓
                            返回"注册成功，请重新登录"
```

## 4. 验证码刷新

| 触发方式 | 场景 | 行为 |
|----------|------|------|
| 被动刷新 | 用户输错验证码 | 删旧 key，生新验证码，写 Redis，返新图片 |
| 主动刷新 | 用户点击"看不清/换一张" | 调用 `GET /captcha?phone=xxx`，同上逻辑 |

- 刷新时必须**删除旧 Redis key**，防止新旧验证码共存导致校验错乱。
- 新验证码可直接覆盖同一个 key（`captcha:<手机号>`），key 名不需变化。

## 5. 接口

| Method | Path | 说明 |
|--------|------|------|
| POST | `/login` | 登录（phone + password + captcha） |
| POST | `/register` | 注册（phone + password + captcha） |
| GET | `/captcha?phone=xxx` | 获取/刷新验证码图片 |

## 6. Session 管理

- 登录成功生成 token（UUID），存入 Redis：`token:<token>` → user JSON，TTL 30 分钟。
- `UserArgumentResolver`：从请求头取 token，查 Redis 获取用户信息，自动注入 Controller 参数。
- 验证码一次性使用：校验通过后立即删除 Redis key。

---

# 二、商品模块

## 1. 商品数据

| 商品 | 原价 | 秒杀价 | 库存 | 折扣 |
|------|------|--------|------|------|
| iPhone 17 Pro Max | ¥9999 | ¥7999 | 80 台 | -¥2000 |
| iPhone 17 Pro | ¥8999 | ¥7299 | 100 台 | -¥1700 |
| iPhone 17 Air | ¥6999 | ¥5799 | 120 台 | -¥1200 |
| iPhone 17 | ¥5999 | ¥4999 | 100 台 | -¥1000 |
| Xiaomi 17 Max | ¥5299 | ¥3699 | 70 台 | -¥1600 |

- 5 个商品统一秒杀时段：**2026-05-31 20:00:00 ~ 20:03:00**（3 分钟）
- 商品图片使用占位图：`https://via.placeholder.com/300x300?text=商品名`

### 商品详情（纯文字）

| 商品 | 详情 |
|------|------|
| iPhone 17 Pro Max | 6.9英寸超视网膜XDR显示屏，A19 Pro芯片，48MP三摄系统，钛金属设计，256GB起 |
| iPhone 17 Pro | 6.3英寸超视网膜XDR显示屏，A19 Pro芯片，48MP三摄系统，钛金属设计，128GB起 |
| iPhone 17 Air | 6.6英寸OLED全面屏，A19芯片，48MP双摄系统，超薄机身仅6.1mm，128GB起 |
| iPhone 17 | 6.1英寸OLED全面屏，A18芯片，48MP双摄系统，铝金属设计，128GB起 |
| Xiaomi 17 Max | 6.9英寸1.5K极窄四等边直屏，第五代骁龙8至尊版，徕卡2亿像素主摄，8000mAh金沙江电池，100W有线快充，3D超声波指纹，256GB起 |

## 2. 接口

| Method | Path | 说明 |
|--------|------|------|
| GET | `/goods/list` | 商品列表页（最高频入口） |
| GET | `/goods/detail/{goodsId}` | 商品详情页 |

## 3. 页面级缓存（整段 HTML）

- 商品列表页和详情页均缓存整段 HTML 到 Redis。
- key **不设 TTL**，永不过期；只在数据变更或秒杀结束时手动清除。
- 请求到达时先查缓存，命中直接返回 HTML，不走 Spring MVC 渲染。

### 缓存 Key 设计

| Key | Value | 说明 |
|-----|-------|------|
| `goods:list:page` | 整段 HTML | 商品列表页 |
| `goods:detail:page:<goodsId>` | 整段 HTML | 商品详情页 |
| `goods:list` | `List<GoodsVo>` 的 JSON | 服务端渲染时的数据缓存 |
| `seckill:stock:<goodsId>` | 整数（剩余库存） | 库存预热，秒杀时 DECR |

### 缓存失效

| 场景 | 清除的 key |
|------|-----------|
| 管理员修改商品信息 | `goods:list`, `goods:list:page`, `goods:detail:page:<id>` |
| 秒杀结束或全量刷新 | `goods:list`, `goods:list:page`, 所有合法商品的 `goods:detail:page:<id>` |
| 库存归零 | `goods:detail:page:<id>` |

`clearGoodsPageCache(null)` 表示全量清理：先从 JVM 内存中的 `validGoodsIds` 取快照，必要时通过 `goods:list` 补全合法商品 ID，再删除列表数据缓存、列表页 HTML 和所有详情页 HTML，避免只清列表页导致详情页残留。

## 4. 库存预热

- 服务启动时通过 `ApplicationRunner` 自动加载。
- 扫描 `seckill_goods` 表，将所有 `stock_count` 写入 Redis：`seckill:stock:<goodsId>`。
- 秒杀时直接在 Redis 中 `DECR`，秒杀结束后同步回 MySQL。

## 5. 防止缓存问题

| 问题 | 策略 |
|------|------|
| **穿透** | 5 个商品 ID 预知，非法 goodsId 直接返回参数错误，不查 DB |
| **击穿** | 商品缓存不设 TTL，永不过期，不存在热点 key 突然失效 |
| **雪崩** | 不设 TTL，不存在集中过期窗口 |
| **重建并发** | 分布式锁 `goods:list:lock`，未获取到锁的线程自旋重试读缓存；获取锁后双重检查，只有第一个线程查 DB 写缓存 |

---

# 三、秒杀模块

## 1. 核心流程（5 步）

```
用户发起秒杀 POST /seckill/{goodsId}
    ↓
① 时间窗口判断（JVM 内存，不查 Redis/MySQL）
   └─ 不在窗口内 → 返回"秒杀尚未开始"或"已结束"
    ↓
② 防重复检查（Redis Set）
   └─ SISMEMBER seckill:ordered:<goodsId> <userId> → 已存在 → 返回"已抢过"
    ↓
③ Redis DECR 原子减库存
   └─ stock < 0 → INCR 回滚 → 返回"已售罄"
    ↓
④ SADD 标记已抢 + 异步发送 Kafka 消息 { goodsId, userId }
   ├─ Kafka 发起失败 → 回滚库存+移除标记+删除消息标记 → 返回"系统繁忙"
   └─ Broker 异步确认失败 → 回调中回滚库存+移除标记+删除消息标记
    ↓
⑤ 返回 { status: "queuing" }，前端轮询等结果
```

### 各步骤详解

**Step 1 — 时间窗口判断**

- 服务启动时加载 `seckill_goods.start_date/end_date` 到 JVM 内存（`ConcurrentHashMap<goodsId, SeckillTimeWindow>`）。
- 5 个商品几百字节，零网络开销。所有商品同开同关，用 Map 结构方便扩展不同时间段。

**Step 2 — 防重复抢购**

- Redis Set：`seckill:ordered:<goodsId>` 记录已抢用户。
- 放在 DECR 之前：先过滤重复，不消耗库存扣减；判重失败直接返回，不需要回滚。

**Step 3 — Redis 原子减库存**

- 使用 `DECR` 而非 `GET + SET`：DECR 是原子操作，10000 并发同时 DECR，Redis 单线程顺序执行，结果绝对正确。
- `stock < 0` 时立即 `INCR` 回滚，确保库存值准确。

**Step 4 — 记录已抢 + 发送 Kafka 消息**

- `SADD seckill:ordered:<goodsId> <userId>` 标记已抢。
- Kafka Topic：`seckill-order`，消息体 `{ goodsId, userId }`，不传价格（Consumer 以 DB 为准）。
- Web 线程只发起 Kafka 异步发送，不调用 `.get()` 等待 Broker ack，避免高并发下 Tomcat Worker 被 Kafka 网络抖动拖住。
- 发送发起前写入 Redis `seckill:msg:<goodsId>:<userId>=SENT`，防止重复点击重复入队。
- 发送失败补偿：同步发起失败时立即 `INCR` 库存+1 + `SREM` 移除已抢标记 + 删除 `seckill:msg` 并返回"系统繁忙"；异步确认失败时在回调中执行同样补偿，用户后续轮询会从排队中变为失败。

**Step 5 — 返回结果**

- 立即返回 `{ status: "queuing" }`，不等 MySQL 写入。
- Redis Lua 预扣 + Kafka 异步发送发起后立即返回，用户几乎无感。

---

## 2. Lua 脚本原子化（修复 Step 2~4 非原子性）

### 主脚本：检查 + 扣库存 + 标记

Step 2+3+4 非原子——SRISMEMBER 通过后，DECR 成功，但 SADD 前 App 崩溃会导致库存已扣但用户未标记。用 Lua 打包为 Redis 单次原子执行：

```lua
-- seckill.lua
-- KEYS[1] = seckill:ordered:<goodsId>
-- KEYS[2] = seckill:stock:<goodsId>
-- ARGV[1] = userId

local isMember = redis.call('SISMEMBER', KEYS[1], ARGV[1])
if isMember == 1 then
    return {-1, '此商品已抢'}        -- 已抢过
end

local stock = redis.call('DECR', KEYS[2])
if stock < 0 then
    redis.call('INCR', KEYS[2])       -- 回滚
    return {-2, '已售罄'}
end

redis.call('SADD', KEYS[1], ARGV[1])   -- 标记已抢
return {stock, 'ok'}
```

Java 侧：执行 Lua → 返回值判断 → 成功才发 Kafka，失败不走 Kafka。

### 回滚脚本：Kafka 发送失败时回滚

```lua
-- rollback.lua
-- KEYS[1] = seckill:stock:<goodsId>
-- KEYS[2] = seckill:ordered:<goodsId>
-- ARGV[1] = userId

redis.call('INCR', KEYS[1])            -- 库存+1
redis.call('SREM', KEYS[2], ARGV[1])   -- 移除已抢标记
return 'ok'
```

---

## 3. Kafka 可靠性保障

### Producer 端

| 配置 | 值 | 说明 |
|------|-----|------|
| `acks` | `all` | leader + 所有 ISR 副本写入才确认 |
| `retries` | `3` | 发送失败自动重试 |
| 消息去重 | Redis `seckill:msg:<goodsId>:<userId>` TTL 5min | 防超时重复发送 |
| 发送方式 | 异步回调 | 不阻塞 Tomcat Worker，失败回调补偿 Redis 状态 |

`seckill:msg:<goodsId>:<userId>` 使用 String 状态值：`SENT` 表示 Producer 已发起发送，`PROCESSED` 表示 Consumer 已完成落库。Consumer 只把 `PROCESSED` 当作已处理，不能因为看到 `SENT` 就跳过订单创建。

**超时但实际已写入**：Kafka 发送超时但 Broker 实际写入成功时，重试会产生重复消息。Consumer 端通过 Redis `PROCESSED` 去重标记 + DB 唯一约束兜底。

### Consumer 端

- **手动提交 offset**：`enable-auto-commit=false`，`Acknowledgment.acknowledge()` 处理成功才确认。
- **去重检查**：消费前检查 Redis `seckill:msg:<goodsId>:<userId>` 是否为 `PROCESSED`，已处理跳过。
- **异常分类**：消息解析失败和业务永久异常（如商品不存在、DB 库存已不可扣）直接进入 DLT；数据库连接抖动等临时异常才重试。
- **最大重试 3 次**：临时异常按 1s、2s 递增退避后重试，第三次仍失败则转入死信队列（DLT），不阻塞正常消息，人工排查。
- **DLT 写入**：死信路径低频，保持同步等待 DLT 发送成功后再 `ack`，避免主消息确认了但死信消息丢失。

```java
@KafkaListener(topics = "seckill-order")
public void onMessage(ConsumerRecord<String, String> record, Acknowledgment ack) {
    try {
        SeckillMessage msg = parseMessage(record.value());
        
        // 去重
        if (alreadyProcessed(msg)) {
            ack.acknowledge();
            return;
        }
        
        // 扣 MySQL 库存 + 创建订单
        handleOrder(msg);
        
        // 标记已处理
        markProcessed(msg);
        
        ack.acknowledge();
    } catch (Exception e) {
        // 解析错误/永久业务异常直接 DLT；临时异常退避后重试，最多3次
        handleFailure(record, ack, msg, e);
    }
}
```

---

## 4. Consumer 处理逻辑

```
收到消息 { goodsId, userId }
    ↓
① 去重检查：Redis GET seckill:msg:<goodsId>:<userId>
   └─ 值为 PROCESSED → ack，跳过
    ↓
② 乐观锁扣减 MySQL 库存：
   UPDATE seckill_goods SET stock_count = stock_count - 1
   WHERE goods_id = ? AND stock_count > 0
   └─ affected rows = 0 → 库存异常，记录日志
    ↓
③ 创建订单记录到 order_info 表
    ↓
④ 写入 seckill_order 关联表
    ↓
⑤ 标记已处理：Redis SET seckill:msg:<goodsId>:<userId> PROCESSED，TTL 5min
    ↓
⑥ ack.acknowledge()

失败处理：

- 消息体无法解析：直接写入 `seckill-order-dlt`，然后 ack。
- `BusinessException` 这类永久业务异常：直接写入 DLT，清理 retry key，然后 ack。
- 其他临时异常：`seckill:retry:<goodsId>:<userId>` 自增并设置 5min TTL；第 1、2 次分别 sleep 1s、2s 后抛出异常触发重新投递；第 3 次仍失败则写入 DLT、清理 retry key、ack。
```

**削峰原理**：Kafka 批量消费 + MySQL 行锁串行化写入，流量峰值被 Kafka 缓冲，Consumer 按自身吞吐处理。

---

## 5. 轮询接口

| Method | Path | 说明 |
|--------|------|------|
| POST | `/seckill/{goodsId}` | 执行秒杀（需登录，`UserSession` 注入） |
| GET | `/seckill/result/{goodsId}` | 查询秒杀结果（前端轮询用，间隔 1-2s） |

`/seckill/result/{goodsId}` 查两层：

| 查询目标 | 含义 |
|----------|------|
| Redis `seckill:ordered:<goodsId>` | SISMEMBER 返回 1 → 消息已入队 |
| MySQL `seckill_order` 表 | 有记录 → Consumer 已处理完，带订单号返回 |

前端根据返回展示：排队中 / 抢购成功 + 订单号 / 秒杀失败。

---

## 6. Redis Key 总览

| Key | 类型 | 说明 |
|-----|------|------|
| `seckill:stock:<goodsId>` | String(INT) | 预减库存，Lua 中 DECR |
| `seckill:ordered:<goodsId>` | Set | 已抢购用户集合 |
| `seckill:msg:<goodsId>:<userId>` | String | Producer/Consumer 去重状态，值为 `SENT` 或 `PROCESSED`，TTL 5min |
| `seckill:retry:<goodsId>:<userId>` | String(INT) | Consumer 临时失败重试计数，TTL 5min |

---

## 7. 可靠性保障总表

| 环节 | 问题 | 措施 |
|------|------|------|
| 时间窗口 | 查 DB 压力大 | JVM 内存，零网络开销 |
| 库存扣减 | 多线程竞态 | Redis 原子 DECR |
| 防重+扣库存+标记 | 三步非原子 | Lua 脚本打包为单次原子操作 |
| Kafka 发送失败 | 状态不一致 | 同步发起失败或异步确认失败时执行 Lua 回滚脚本 INCR+SREM，并删除 `seckill:msg` |
| Producer 超时 | 消息可能重复 | Consumer 去重（Redis + DB 唯一键） |
| Broker 宕机 | 消息丢失 | `acks=all`，多 ISR 副本 |
| Consumer 失败 | 消息丢失 | 手动提交 offset，失败不确认 |
| Consumer 快速/无限重试 | 阻塞后续消息或瞬间打入 DLT | 临时异常 1s、2s 退避，最大 3 次 + 死信队列；永久业务异常直接 DLT |
| 重复消费 | 重复创建订单 | Redis 去重标记 + DB 唯一约束 |

---

# 四、订单模块

> 待讨论

---

# 五、Kafka 消息模块

> 待讨论

---

# 六、限流 & 防刷

> 待讨论

---

# 附：数据表设计

## user 表

```sql
CREATE TABLE `user` (
    `id`              BIGINT        AUTO_INCREMENT PRIMARY KEY COMMENT '用户ID',
    `phone`           VARCHAR(11)   NOT NULL UNIQUE COMMENT '手机号',
    `nickname`        VARCHAR(20)   NOT NULL COMMENT '昵称（自动生成：用户+4位随机数）',
    `password`        VARCHAR(32)   NOT NULL COMMENT '两层MD5密文',
    `salt`            VARCHAR(10)   NOT NULL COMMENT '独立salt',
    `register_date`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
    `last_login_date` DATETIME      DEFAULT NULL COMMENT '最近登录时间',
    INDEX `idx_phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';
```

### 设计要点

- **窄表**：字段少，B+树每页存更多行，索引查询更快。
- **phone 唯一索引**：登录/注册的唯一查询入口，一次命中。
- **nickname 自动生成**：`用户` + 随机4位数字（如 `用户9527`）。
- **user 表不参与高并发链路**：仅做身份认证，秒杀的核心压力在 Redis + Kafka + 订单/库存。
- **last_login_date**：每次登录成功更新，数据记录用途。

## goods 表（商品基础信息）

```sql
CREATE TABLE `goods` (
    `id`            BIGINT         AUTO_INCREMENT PRIMARY KEY COMMENT '商品ID',
    `goods_name`    VARCHAR(64)    NOT NULL COMMENT '商品名称',
    `goods_title`   VARCHAR(128)   DEFAULT NULL COMMENT '商品标题/副标题',
    `goods_img`     VARCHAR(256)   DEFAULT NULL COMMENT '商品图片URL（占位图）',
    `goods_detail`  TEXT           DEFAULT NULL COMMENT '商品详情描述（纯文字）',
    `goods_price`   DECIMAL(10,2)  NOT NULL COMMENT '市场原价',
    `goods_stock`   INT            NOT NULL DEFAULT 0 COMMENT '总库存',
    INDEX `idx_goods_name` (`goods_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品表';
```

## seckill_goods 表（秒杀活动信息）

```sql
CREATE TABLE `seckill_goods` (
    `id`             BIGINT        AUTO_INCREMENT PRIMARY KEY COMMENT '秒杀商品ID',
    `goods_id`       BIGINT        NOT NULL COMMENT '关联商品ID',
    `seckill_price`  DECIMAL(10,2) NOT NULL COMMENT '秒杀价格',
    `stock_count`    INT           NOT NULL COMMENT '秒杀可用库存',
    `start_date`     DATETIME      NOT NULL COMMENT '秒杀开始时间',
    `end_date`       DATETIME      NOT NULL COMMENT '秒杀结束时间',
    INDEX `idx_start_end` (`start_date`, `end_date`),
    UNIQUE KEY `uk_goods_id` (`goods_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀商品表';
```

- **goods 和 seckill_goods 分表**：静态商品信息和秒杀活动信息解耦。
- 展示时用 `GoodsVo` 关联查询，`goods_id` 唯一绑定一个秒杀活动。
