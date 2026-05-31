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

> 待讨论

---

# 三、秒杀模块

> 待讨论

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
