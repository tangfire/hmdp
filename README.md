# 缓存

Redis 是一种基于内存的高性能键值数据库，其缓存机制通过将热点数据存储在内存中，显著提升应用的读写效率。以下从核心特性、应用场景、缓存策略及常见问题处理等方面详细介绍 Redis 的缓存功能：

---

### **一、Redis 缓存的核心特性**
1. **高性能读写**
  - **内存存储**：数据直接存储在内存中，读写速度可达每秒数十万次（读 11 万+/秒，写 8 万+/秒）。
  - **单线程模型**：避免多线程竞争和上下文切换，配合非阻塞 I/O 多路复用（如 epoll），高效处理并发请求。

2. **支持多种数据结构**  
   Redis 提供字符串（String）、哈希（Hash）、列表（List）、集合（Set）、有序集合（ZSet）等数据结构，满足复杂缓存需求。例如：
  - **字符串**：存储序列化的对象或简单键值；
  - **哈希**：缓存用户信息等结构化数据；
  - **有序集合**：实现排行榜等场景。

3. **持久化与高可用**
  - **RDB 快照**：定时生成内存数据的二进制快照，适合备份恢复；
  - **AOF 日志**：记录操作指令，保证数据更安全；
  - **主从复制与哨兵**：实现故障自动切换和数据冗余；
  - **集群模式**：支持数据分片，横向扩展容量。

4. **灵活的过期与淘汰策略**
  - **过期键删除**：采用惰性删除（查询时检查）结合定期删除（随机抽样）；
  - **内存淘汰策略**：包括 LRU（最近最少使用）、LFU（最不常用）、TTL（最短存活时间）等 8 种策略，应对内存不足问题。

---

### **二、Redis 缓存的主要应用场景**
1. **数据库查询结果缓存**  
   缓存频繁访问的数据库查询结果（如商品信息），减少直接访问数据库的压力。

2. **会话缓存（Session Cache）**  
   存储用户登录状态，支持无状态服务架构，提升横向扩展能力。

3. **页面片段或全页缓存**  
   缓存动态生成的 HTML 片段或整页内容（如电商首页），加速页面加载。

4. **分布式锁与限流**  
   利用 `SETNX` 命令实现分布式锁，或通过滑动窗口算法限制接口请求频率。

5. **消息队列与发布订阅**  
   使用列表（List）或 Stream 结构实现轻量级消息队列，支持异步任务处理。

---

### **三、缓存异常场景与解决方案**
1. **缓存穿透**
  - **问题**：大量请求查询不存在的数据（如恶意攻击），绕过缓存直接访问数据库。
  - **解决**：
    - 布隆过滤器（Bloom Filter）拦截无效 Key；
    - 缓存空值并设置较短过期时间。

2. **缓存击穿**
  - **问题**：热点数据突然过期，导致瞬时高并发请求压垮数据库。
  - **解决**：
    - 互斥锁（Mutex Lock）仅允许一个线程重建缓存；
    - 设置热点数据永不过期或异步续期。

3. **缓存雪崩**
  - **问题**：大量缓存同时失效或 Redis 集群宕机，引发数据库连锁崩溃。
  - **解决**：
    - 随机分散 Key 的过期时间；
    - 多级缓存（如本地缓存 + Redis）或集群容灾（哨兵模式）。

---

### **四、Redis 缓存的最佳实践**
1. **合理设置过期时间**  
   根据业务特点平衡缓存新鲜度与内存占用，避免过长或过短的 TTL。

2. **监控与调优**
  - 使用慢查询日志分析性能瓶颈；
  - 通过 Pipeline 批量操作减少网络开销。

3. **数据一致性处理**
  - 写操作时采用“先更新数据库，再删除缓存”（Cache-Aside 模式）；
  - 结合消息队列实现最终一致性。

---

### **总结**
Redis 缓存通过内存存储、多样化数据结构和灵活的淘汰策略，成为高并发场景下的核心组件。实际应用中需根据业务需求选择持久化方式、集群架构及异常处理方案，并持续监控性能以优化缓存命中率。对于复杂场景（如分布式锁、限流），可结合 Redis 特性设计定制化解决方案。


# 缓存更新策略

Redis 的缓存更新策略是确保缓存与数据源（如数据库）一致性、提升性能的核心机制。以下是基于业务场景和需求的常见策略及其应用分析：

---

### **一、主动更新策略**
#### 1. **Cache-Aside（旁路缓存）**
- **原理**：应用程序直接控制缓存更新，写操作时更新数据库并删除缓存，读操作时若缓存未命中则从数据库加载并回填。
- **操作流程**：
  - **写操作**：先更新数据库，后删除缓存（避免并发读导致脏数据）。
  - **读操作**：缓存命中直接返回；未命中则查询数据库并写入缓存，设置 TTL（超时时间）兜底。
- **优点**：简单易实现，适合高一致性场景（如电商订单）。
- **缺点**：需处理并发读写时的缓存击穿问题（如用分布式锁）。

#### 2. **Write-Through（读写穿透）**
- **原理**：缓存与数据库整合为一个服务，写操作时同步更新缓存和数据库。
- **优点**：业务层无需关心一致性，调用简单。
- **缺点**：实现复杂，需封装底层逻辑，性能可能因同步写入下降。

#### 3. **Write-Behind（异步写回）**
- **原理**：写操作仅更新缓存，由后台线程异步批量持久化到数据库。
- **优点**：写入性能高，适合写多读少场景（如日志记录）。
- **缺点**：数据可能短暂不一致，需容忍最终一致性。

---

### **二、被动更新策略**
#### 1. **超时剔除（TTL 机制）**
- **原理**：为缓存设置过期时间（如 `EXPIRE` 命令），到期自动删除，后续请求触发回填。
- **适用场景**：数据更新频率低且允许短暂不一致（如商品分类列表）。
- **优化**：结合随机 TTL 避免雪崩（如设置 30±5 分钟过期）。

#### 2. **内存淘汰策略**
当内存不足时，Redis 按配置策略淘汰数据：
- **LRU（最近最少使用）**：优先淘汰长期未访问的数据。
- **LFU（最不常用）**：淘汰使用频率最低的数据。
- **volatile-ttl**：淘汰剩余存活时间最短的键。
- **noeviction**：直接拒绝写入（适用于不能丢数据的场景）。

---

### **三、高一致性场景的特殊策略**
#### 1. **延迟双删**
- **步骤**：
  1. 先删除缓存；
  2. 更新数据库；
  3. 延迟一定时间后再次删除缓存（防止并发读回填旧数据）。
- **适用场景**：高并发写后需强一致性（如库存扣减）。

#### 2. **Binlog 监听异步更新**
- **原理**：通过中间件（如 Canal）监听数据库 Binlog 变更，异步更新缓存。
- **优点**：解耦业务逻辑，保证最终一致性。
- **注意点**：需处理消息顺序和重试机制（如 Kafka 顺序消费）。

#### 3. **分布式锁控制更新**
- **实现**：更新时加写锁，防止并发读写冲突（如 RedLock 算法）。
- **代价**：牺牲部分性能换取强一致性，适用于金融交易等场景。

---

### **四、策略选择建议**
| **场景**                | **推荐策略**                          | **理由**                                                                 |
|-------------------------|---------------------------------------|--------------------------------------------------------------------------|
| 低频更新（如排行榜）     | 定期生成 + 超时剔除                   | 降低数据库压力，容忍延迟。                                        |
| 高频写强一致（如支付）   | Cache-Aside + 延迟双删 + 分布式锁     | 确保数据实时一致，防止并发问题。                             |
| 大数据量异步处理（如日志）| Write-Behind + 持久化队列             | 提升吞吐量，接受最终一致性。                                         |
| 防雪崩/穿透             | 布隆过滤器 + 空值缓存                 | 拦截无效请求，避免数据库过载。                                   |

---

### **五、最佳实践**
1. **组合策略**：主用 Cache-Aside，辅以 TTL 兜底和异步 Binlog 监听。
2. **监控优化**：通过慢查询日志分析热点数据，动态调整淘汰策略。
3. **容错设计**：缓存更新失败时，采用重试队列或降级策略（如直接读库）。

通过灵活选择策略，可在性能与一致性之间取得平衡。具体实现需结合业务特点（如实时性要求、数据量级）及运维成本综合考量。

# Cache Aside Pattern

Redis 中的 **Cache Aside Pattern（旁路缓存模式）** 是一种通过应用程序显式管理缓存与数据库数据一致性的策略，适用于读多写少的高并发场景。以下是其核心原理、操作步骤及优化方案的详细解析：

---

### **一、核心原理**
Cache Aside Pattern 的核心思想是 **以数据库为权威数据源**，缓存仅作为数据库的辅助层。应用程序直接控制缓存的读写逻辑，通过删除而非更新缓存来避免并发写冲突和数据冗余。

---

### **二、操作流程**
#### **1. 读操作流程**
```plaintext
读请求 → 查询缓存 → 命中则返回 → 未命中则查数据库 → 回填缓存 → 返回数据
```
- **缓存命中**：直接返回缓存数据，减少数据库压力。
- **缓存未命中**：
    - 从数据库读取数据；
    - 将数据写入缓存（设置 TTL 兜底）；
    - 返回数据。

#### **2. 写操作流程**
```plaintext
写请求 → 更新数据库 → 删除缓存 → 返回成功
```
- **先更新数据库**：保证数据库的权威性。
- **后删除缓存**：避免旧数据残留，后续读请求触发回填最新数据。

---

### **三、设计考量与优缺点**
#### **1. 优势**
- **简单灵活**：无需依赖缓存中间件，由应用代码显式控制缓存逻辑。
- **避免写冲突**：通过删除而非更新缓存，减少并发写导致的数据不一致风险。
- **天然防缓存穿透**：未命中时通过数据库回填，结合空值缓存可拦截无效请求。

#### **2. 潜在问题**
- **短暂数据不一致**：在删除缓存后、下次回填前，可能读取到旧数据（概率较低）。
- **首次请求延迟**：新数据首次访问需回填缓存，可能增加数据库瞬时压力。
- **频繁写导致缓存命中率低**：频繁删除缓存可能影响热点数据访问效率。

---

### **四、优化方案**
#### **1. 降低不一致窗口**
- **延迟双删**：在更新数据库后，延迟一定时间（如 1 秒）再次删除缓存，减少并发读导致的脏数据残留。
- **异步监听 Binlog**：通过监听数据库变更日志（如 MySQL Binlog），异步删除或更新缓存，实现最终一致性（如使用 Canal、Debezium 等工具）。

#### **2. 提升缓存命中率**
- **预加载热点数据**：服务启动或定时任务预先加载高频访问数据。
- **分布式锁控制回填**：缓存未命中时，通过分布式锁保证仅一个线程回填数据，避免缓存击穿。

#### **3. 兜底策略**
- **设置缓存 TTL**：即使删除失败，过期时间可强制刷新数据。
- **数据校对与告警**：定期对比缓存与数据库数据差异，触发告警或自动修复。

---

### **五、适用场景**
- **读多写少**：如商品详情页、新闻资讯等高频读取场景。
- **容忍最终一致性**：如用户评论、社交动态更新等。
- **高并发写入**：结合延迟双删或异步队列缓解数据库压力。

---

### **六、与其他模式对比**
| **模式**          | **特点**                                                                 | **适用场景**               |
|-------------------|-------------------------------------------------------------------------|---------------------------|
| **Write Through** | 同步更新缓存与数据库，强一致但性能较低                                | 金融交易等高一致性场景       |
| **Write Behind**  | 异步批量更新数据库，性能高但存在数据丢失风险                          | 日志记录、用户行为分析       |
| **Cache Aside**   | 平衡性能与一致性，需处理短暂不一致窗口                            | 通用读多写少场景           |

---

### **总结**
Cache Aside Pattern 是 Redis 缓存设计的经典策略，通过显式控制缓存逻辑，在性能与一致性之间取得平衡。实际应用中需结合 **延迟双删、Binlog 监听、预加载** 等优化手段，并根据业务特点选择 TTL 和锁机制，确保系统高效稳定运行。对于强一致性要求极高的场景（如库存扣减），可结合分布式锁或事务消息队列进一步加固。


# 缓存穿透

Redis 中的 **缓存穿透** 是指客户端请求的数据在缓存和数据库中均不存在，导致所有请求直接穿透缓存层，持续冲击数据库的现象。这种情况常见于恶意攻击或无效参数请求，可能引发数据库过载甚至宕机。以下是其核心原理、解决方案及实践要点：

---

### **一、缓存穿透的核心原理**
1. **触发场景**
    - 请求的 **Key 既不在缓存中，也不在数据库中**，例如恶意构造的非法 ID（如负数）或数据库中已删除的数据。
    - 高并发场景下，大量此类请求绕过缓存直接访问数据库，导致数据库压力激增。

2. **危害**
    - **缓存层失效**：缓存无法拦截无效请求，失去保护数据库的作用。
    - **数据库压力**：高频无效查询占用数据库资源，可能引发系统崩溃。

---

### **二、解决方案与实现**
#### **1. 缓存空对象（Null Caching）**
- **原理**：当数据库查询结果为空时，将空值（如空字符串 `""`）写入缓存，并设置较短的过期时间（例如 2-5 分钟）。
- **代码示例**（基于商户查询场景）：
  ```java
  public Result queryById(Long id) {
      String key = "shop:" + id;
      String shopJson = redis.get(key);
      // 缓存命中空值
      if (shopJson != null && shopJson.isEmpty()) {
          return Result.error("数据不存在");
      }
      // 数据库查询
      Shop shop = db.getById(id);
      if (shop == null) {
          // 缓存空值并设置过期时间
          redis.setex(key, 2 * 60, "");
          return Result.error("数据不存在");
      }
      // 缓存有效数据
      redis.setex(key, 30 * 60, serialize(shop));
      return Result.ok(shop);
  }
  ```
- **优点**：实现简单，有效拦截重复无效请求。
- **缺点**：
    - 内存浪费：大量空值占用缓存空间。
    - 短暂不一致：若数据库后续新增数据，缓存空值需手动清理或等待过期。

#### **2. 布隆过滤器（Bloom Filter）**
- **原理**：使用位数组和哈希函数预存所有合法 Key。请求到达时，先通过布隆过滤器判断 Key 是否存在：
    - **不存在**：直接拦截请求，避免访问数据库。
    - **存在**：允许继续查询缓存或数据库。
- **实现示例**（Guava 库）：
  ```java
  BloomFilter<String> bloomFilter = BloomFilter.create(
      Funnels.stringFunnel(Charset.defaultCharset()), 
      1000000,  // 预期数据量
      0.01      // 误判率
  );
  // 初始化时加载合法 Key
  bloomFilter.put("valid_key_1");
  bloomFilter.put("valid_key_2");

  // 请求处理逻辑
  if (!bloomFilter.mightContain(key)) {
      return Result.error("非法请求");
  }
  ```
- **优点**：内存占用极低（1亿数据约需 12MB），适合大规模数据场景。
- **缺点**：
    - **误判率**：可能将不存在的 Key 误判为存在（可调整哈希函数数量和位数组大小降低概率）。
    - **更新延迟**：数据新增时需同步更新布隆过滤器，不适合频繁变动的数据集。

#### **3. 参数校验与限流**
- **参数校验**：在业务层拦截非法请求（如非正数 ID、格式错误的邮箱），减少无效查询。
- **接口限流**：
    - 对高频请求的 IP 或用户实施限流（如令牌桶算法）。
    - 结合黑名单机制，拦截恶意攻击源。

---

### **三、方案对比与选型建议**
| **方案**         | **适用场景**                     | **优点**                | **缺点**                |
|------------------|---------------------------------|-------------------------|-------------------------|
| **缓存空对象**   | 数据变更低频，容忍短暂不一致     | 实现简单，快速生效       | 内存占用高，需维护空值  |
| **布隆过滤器**   | 大规模静态数据（如用户 ID 列表） | 内存效率高，拦截精准     | 误判率存在，更新复杂     |
| **参数校验/限流**| 高频攻击或明显无效请求           | 前置防御，减少无效流量   | 依赖业务逻辑，需动态调整 |

---

### **四、实践优化建议**
1. **组合策略**：
    - 对核心业务数据使用 **布隆过滤器 + 缓存空对象**，兼顾内存效率与容错性。
    - 设置不同 TTL：热点数据永不过期，普通数据设置随机过期时间（如 `基础 TTL + 随机分钟数`），避免集中失效。

2. **监控与告警**：
    - 监控缓存命中率与空值占比，及时调整策略。
    - 使用慢查询日志分析异常请求模式。

3. **数据一致性处理**：
    - 通过 **数据库 Binlog 监听**（如 Canal）异步清理或更新缓存空值。
    - 采用 **双删策略**：更新数据库后，延迟二次删除缓存，减少脏数据。

---

### **总结**
缓存穿透是 Redis 高并发场景下的典型问题，需结合业务特点选择 **缓存空对象**、**布隆过滤器** 或 **限流校验** 等方案。对于动态数据，推荐以参数校验为基础，辅以空值缓存；对于静态数据，布隆过滤器能显著降低内存消耗。实际应用中需平衡性能、一致性与维护成本，通过监控和自动化机制保障系统稳定。


# 缓存雪崩

### Redis 缓存雪崩详解及解决方案

**缓存雪崩**是 Redis 在高并发场景下面临的典型问题，指 **大量缓存数据在同一时间集中失效** 或 **Redis 服务宕机**，导致所有请求直接穿透到数据库，引发数据库崩溃的连锁反应。以下从核心原理、触发场景及解决方案展开说明：

---

#### **一、缓存雪崩的触发原因**
1. **批量 Key 同时失效**
    - 缓存数据设置了相同的过期时间（如促销活动 Key 统一设置 24 小时过期），导致集中失效后请求涌入数据库。
    - **案例**：某电商平台在凌晨批量更新商品缓存，因统一过期时间导致数据库 QPS 瞬间飙升 10 倍。

2. **Redis 服务宕机**
    - 单点 Redis 服务器硬件故障、网络中断或集群节点故障，导致缓存层整体不可用。
    - **案例**：某视频网站因机房断电导致 Redis 主节点宕机，未配置高可用架构，最终服务崩溃 30 分钟。

---

#### **二、解决方案与实战策略**
##### **1. 分散缓存过期时间**
- **核心思路**：避免 Key 集中失效，为过期时间增加随机偏移量。
  ```bash
  # 基础 TTL（如 24 小时） + 随机分钟数（如 0~60 分钟）
  SET key value EX $((86400 + RANDOM % 3600))
  ```
- **优化效果**：某金融系统通过分散过期时间，将数据库峰值 QPS 从 10 万降至 1 万。
- **适用场景**：周期性更新数据（如每日排行榜）。

##### **2. 高可用架构设计**
- **Redis 哨兵模式（Sentinel）**  
  自动监控主节点状态，故障时切换从节点为主节点，保障服务连续性。  
  **配置示例**：
  ```conf
  sentinel monitor mymaster 127.0.0.1 6379 2
  sentinel down-after-milliseconds mymaster 5000
  ```
- **Redis Cluster 集群模式**  
  数据分片存储（16384 个槽），支持横向扩展和节点容灾，避免单点故障。  
  **优势**：某社交平台通过 Cluster 实现 99.99% 可用性，跨机房部署容忍区域性故障。

##### **3. 多级缓存架构**
- **本地缓存（如 Caffeine/Guava）**  
  作为 Redis 的二级缓存，在 Redis 失效时扛住部分流量。  
  **案例**：新闻 App 在 Redis 宕机时，本地缓存承接 50% 请求，避免数据库崩溃。
- **分布式缓存（如 Memcached）**  
  与 Redis 形成互补，分散风险。例如电商平台将商品详情页静态数据存储至 Memcached。

##### **4. 服务降级与熔断机制**
- **熔断策略（Hystrix/Sentinel）**  
  当数据库压力超过阈值时，直接返回默认数据（如“系统繁忙，请稍后重试”）。  
  **配置示例**：
  ```java
  // 熔断规则：1 分钟内失败率超 50% 触发熔断
  DegradeRule rule = new DegradeRule("db_query")
      .setGrade(RuleConstant.DEGRADE_GRADE_EXCEPTION_RATIO)
      .setCount(0.5)
      .setTimeWindow(60);
  ```
- **降级兜底数据**  
  预先配置静态数据（如默认商品信息），保障核心功能可用。

##### **5. 持久化与快速恢复**
- **RDB/AOF 持久化**  
  定期生成快照（RDB）或记录操作日志（AOF），重启后快速恢复数据。  
  **优化实践**：阿里云通过 OSS 存储每日 RDB 快照，实现分钟级灾难恢复。
- **缓存预热**  
  服务启动时加载热点数据，避免冷启动期雪崩。例如某直播平台在活动前 1 小时预热明星直播间数据。

---

#### **三、综合方案对比**
| **方案**              | **适用阶段** | **优势**                    | **局限**                  |
|-----------------------|--------------|-----------------------------|---------------------------|
| 分散过期时间          | 事前预防     | 低成本，易实施              | 无法应对 Redis 宕机       |
| 高可用架构            | 事前预防     | 保障服务连续性              | 部署和维护成本较高        |
| 多级缓存              | 事中抵抗     | 分流压力，提升系统韧性      | 数据一致性管理复杂        |
| 熔断降级              | 事中抵抗     | 保护数据库核心服务          | 用户体验可能受损          |
| 持久化与预热          | 事后恢复     | 快速恢复，减少数据丢失      | 依赖备份频率和恢复速度    |

---

#### **四、最佳实践建议**
1. **组合策略**：以 **分散过期时间 + Redis Cluster** 为基础，结合 **本地缓存 + 熔断降级** 构建防御体系。
2. **监控预警**：
    - 实时监控缓存命中率、Redis 节点状态及数据库 QPS。
    - 设置阈值告警（如缓存命中率 < 80% 触发预警）。
3. **压测与演练**：定期模拟缓存雪崩场景，验证系统容灾能力。例如某大厂在“双 11”前进行全链路压测。

---

通过以上策略，可有效应对缓存雪崩问题，在保障系统高可用的同时平衡性能与成本。实际应用中需根据业务特点（如数据更新频率、一致性要求）动态调整方案。


# 缓存击穿

Redis 的 **缓存击穿** 是指 **某个热点 Key 在缓存中突然失效**，导致瞬时大量并发请求直接穿透到数据库，引发数据库负载骤增甚至崩溃的现象。以下从核心原理、解决方案及实战优化策略展开详解：

---

### **一、缓存击穿的核心原理**
1. **触发场景**
    - **热点数据过期**：如秒杀商品缓存失效、热门新闻突然过期。
    - **高并发访问**：大量用户同时请求同一热点数据，缓存重建期间请求全部压至数据库。

2. **危害**
    - **数据库瞬时压力**：请求量远超数据库承载能力，导致响应延迟或宕机。
    - **连锁反应**：若数据库崩溃，可能引发服务雪崩，影响整个系统可用性。

---

### **二、解决方案与实战策略**
#### **1. 互斥锁（Mutex Lock）**
- **原理**：缓存失效时，通过分布式锁（如 Redisson）确保仅一个线程重建缓存，其他线程等待锁释放后重试读取缓存。
- **代码示例**（基于商品查询场景）：
  ```java
  public Product getProduct(String key) {
      Product product = redis.get(key);
      if (product == null) {
          RLock lock = redisson.getLock(key + "_lock");
          try {
              if (lock.tryLock(3, 10, TimeUnit.SECONDS)) { // 尝试获取锁，最多等待3秒
                  product = db.query(key);                 // 查询数据库
                  redis.setex(key, 3600, product);          // 重建缓存
              } else {
                  Thread.sleep(100);                        // 未获取锁则短暂休眠后重试
                  return getProduct(key);
              }
          } finally {
              lock.unlock();
          }
      }
      return product;
  }
  ```
- **优点**：避免数据库重复查询，适合高并发场景。
- **缺点**：锁竞争可能增加延迟，需合理设置超时时间。

#### **2. 逻辑过期（Logical Expiration）**
- **原理**：缓存永不过期，但在 Value 中存储逻辑过期时间，异步线程定期更新数据。
- **实现步骤**：
    1. 缓存数据时附加逻辑过期字段（如 `expireTime`）；
    2. 请求命中缓存后检查逻辑过期时间，若过期则触发异步更新；
    3. 返回旧数据给用户，保证服务可用性。
- **适用场景**：容忍短暂数据不一致的业务（如新闻资讯）。

#### **3. 热点数据永不过期（物理不过期）**
- **原理**：对极高频访问的数据（如首页推荐商品）设置缓存永不过期，通过定时任务或监听数据库变更异步更新。
- **优化技巧**：
    - **定时预热**：在低峰期预加载次日热点数据（如电商大促前夜）；
    - **双写策略**：更新数据库后同步更新缓存，确保数据一致性。

#### **4. 熔断降级**
- **原理**：当检测到数据库压力超过阈值时，直接返回兜底数据（如默认商品信息）或静态页面，保护数据库。
- **工具支持**：
    - Sentinel/Hystrix：设置熔断规则（如 1 分钟内失败率超 50% 触发熔断）；
    - 静态化处理：将热点数据生成静态 HTML 页面，通过 CDN 分发。

---

### **三、综合方案对比与选型建议**
| **方案**        | **适用场景**               | **优点**                | **缺点**                |
|-----------------|---------------------------|-------------------------|-------------------------|
| 互斥锁          | 强一致性要求（如库存扣减） | 数据实时一致            | 锁竞争可能增加延迟       |
| 逻辑过期        | 容忍短暂不一致（如排行榜） | 高可用性，用户体验平滑  | 需处理异步更新逻辑       |
| 热点数据永不过期 | 极高频访问数据（如秒杀）   | 零延迟，无击穿风险      | 内存占用高，更新需同步  |
| 熔断降级        | 数据库过载保护             | 快速止损，保护核心服务  | 用户体验可能受损        |

---

### **四、实战优化建议**
1. **监控与预警**：
    - 实时监控缓存命中率、锁竞争频率及数据库 QPS；
    - 设置告警阈值（如缓存命中率 < 90% 或数据库 QPS > 1 万触发预警）。

2. **压力测试**：
    - 模拟热点 Key 失效场景，验证系统承压能力；
    - 优化线程池参数（如锁等待超时时间、异步更新线程数）。

3. **多级缓存架构**：
    - **本地缓存**（Caffeine/Guava）+ **Redis 集群**：本地缓存扛住瞬时流量，Redis 集群保障分布式一致性；
    - **案例**：美团外卖通过本地缓存拦截 50% 的 Redis 请求，降低击穿风险。

---

### **五、行业最佳实践**
- **字节跳动**：对直播热点数据采用 **逻辑过期 + 异步更新**，结合布隆过滤器拦截非法请求。
- **腾讯**：在 Redis 集群中为秒杀商品设置 **永不过期策略**，通过定时任务每日凌晨刷新数据。
- **阿里**：使用 **Redisson 红锁（RedLock）** 实现跨节点分布式锁，防止主从切换导致锁失效。

---

通过上述策略，可有效应对缓存击穿问题，平衡性能与一致性需求。实际应用中需根据业务特点（如数据更新频率、实时性要求）选择组合方案，并通过监控和压测持续优化系统韧性。


# Redis工具类


```java
package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

/**
 * Redis工具类
 */
@Slf4j
@Component
public class CacheClient {

    private final StringRedisTemplate stringRedisTemplate;

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void set(String key, Object value, Long time, TimeUnit timeUnit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, timeUnit);

    }

    public void setWithLogicExpire(String key, Object value, Long time, TimeUnit timeUnit) {

        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(timeUnit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));


    }

    public <R, ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit timeUnit) {
        // 1. 从redis查询商铺缓存
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);

        // 2. 判断是否存在
        if (StrUtil.isNotBlank(json)) {
            // 3. 存在，直接返回
            return JSONUtil.toBean(json, type);
        }

        // 判断命中的是否是空值
        if (json != null) {
            // 返回一个错误信息
            return null;
        }

        // 4. 不存在，根据id查询数据库
        R r = dbFallback.apply(id);

        if (r == null) {
            // 5. 不存在，返回错误
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);

            return null;
        }

        // 6. 存在，写入redis
        this.set(key, r, time, timeUnit);

        return r;
    }

    /**
     * 线程池
     */
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);


    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);

    }

    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }

    /**
     * 缓存击穿-逻辑时间解决方案
     *
     * @param id
     * @return
     */
    public <R, ID> R queryWithLogicalExpire(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit timeUnit) {
        // 1. 从redis查询商铺缓存
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);

        // 2. 判断是否存在
        // 因为是热点key,所以我们基本上认为Redis中的这个热点key是存在的
        if (StrUtil.isBlank(json)) {
            // 不存在，直接返回null
            return null;
        }

        RedisData redisData = JSONUtil.toBean(json, RedisData.class);

        JSONObject data = (JSONObject) redisData.getData();
        R r = JSONUtil.toBean(data, type);
        LocalDateTime expireTime = redisData.getExpireTime();

        // 判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            // 未过期，直接返回店铺信息
            return r;
        }

        // 已过期，需要缓存重建
        // 缓存重建

        // 获取互斥锁
        String localKey = LOCK_SHOP_KEY + id;

        boolean isLock = tryLock(localKey);

        if (isLock) {
            // 成功，开启独立线程，实现缓存重建
            CACHE_REBUILD_EXECUTOR.submit(() -> {

                try {
                    // 重建缓存
                    // 查询数据库
                    R r1 = dbFallback.apply(id);
                    // 写入缓存
                    this.setWithLogicExpire(key, r1, time, timeUnit);


                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    unlock(localKey);
                }


            });
        }


        return r;
    }


}

```

# Redis实现全局唯一id

```java
package com.hmdp.utils;

import org.springframework.cglib.core.Local;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIdWorker {

    private final StringRedisTemplate stringRedisTemplate;

    public RedisIdWorker(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 开始时间戳
     */
    private static final long BEGIN_TIMESTAMP = 1640995200L;

    /**
     * 序列号的位置
     */
    private static final int COUNT_BITS = 32;

    public long nextId(String keyPrefix) {
        // 1. 生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowSecond - BEGIN_TIMESTAMP;

        // 2. 生成序列号
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);

        return timestamp << COUNT_BITS | count;


    }

    public static void main(String[] args) {
        LocalDateTime time = LocalDateTime.of(2022, 1, 1, 0, 0, 0);
        long second = time.toEpochSecond(ZoneOffset.UTC);
        System.out.println("second = " + second);

    }


}

```