# SOP — Manufacturing Mayhem（最小可交路线）

> 目标档位：**最小可交**（能验收、能讲清设计↔实现对应，不追求性能）
> 配套笔记：[week4.md](week4.md)（起点代码逐项核对）
> 起点代码：`manufacturing-mayhem/`（已加中文注释）

---

## 0. 验收线 —— 满足这几条就算「最小可交」

| # | 验收项 | 怎么证明 |
|---|---|---|
| 1 | **不假设数量**：zone / robot 数量变化，逻辑不变 | 把 `!setup` 里的 zone 与 robot 各翻倍，不加一行逻辑仍能跑通 |
| 2 | **≥2 机器人并发搬运** | 日志里出现两条 transfer 在时间上重叠 |
| 3 | **多层依赖**：WS-A 的产出能作为 WS-B 的输入 | 跑一条 A→B 的两级产线并成功交付 |
| 4 | **订单闭环**：FULFILLED → `deliver` → CLEARED → 自动换新订单 | 连续完成 2 张订单 |
| 5 | **目标函数可读**：每单完成时间 + 总价值 | `Metrics` 工件能打印出 每单耗时 / 总完成时间 / 总价值 |
| 6 | **低电自动充电** | 电量低于阈值自动回 home 充电后续跑 |
| 7 | **不因冲突死锁** | 故意让两机器人抢同一 zone，系统能等待/绕行而非卡死 |
| 8 | **设计可追溯** | GAIA 文档里每个 role / interaction，能在代码里指出对应的 `.astra` 计划 |

> 第 **8** 条是真正的评分重心（占 40%），前 7 条是它的证据。

---

## 1. 现有资产（不重写）

| 资产 | 位置 | 状态 |
|---|---|---|
| 起点代码 917 有效行（ASTRA 270 + Java 647） | `manufacturing-mayhem/` | ✅ 可用 |
| `Base.astra` 计划库（创建 + 读属性的样板） | `src/main/astra/Base.astra` | ✅ 复用，**不要重写** |
| 三个 astra 文件的中文注释 | 同上 | ✅ 已完成 |
| 起点代码逐项核对笔记 | [week4.md](week4.md) | ✅ 已完成 |
| GitHub 备份 | `origin/main` | ✅ 已推送 |

**可以直接复用的东西**（省事重点）：

- 所有工件（`Floorplan` / `AccessPoint` / `Workstation` / `Robot` …）**已经在工作**，不需要从零造
- `Base.astra` 里的 `!floorplan / !accessPoint / !at / !holding / !empty / !operate` 等计划**都是现成的 API**，写编排逻辑时直接调用
- `?locations` 已经是动态发现场地的入口
- `PropertyExtractor` 已经解决「CArtAgO 属性 → ASTRA 列表/字符串」的转换

---

## 2. 写代码前必读的硬约束

| 约束 | 后果 |
|---|---|
| 每个 zone **恰好 1 个角色**，同时**只能 1 个 robot** | 所有调度必须先抢 zone，`Floorplan` 被占会抛异常 |
| Access Point **取料后不可退回** | 取料时机必须想清楚，否则货卡在 ap 上 |
| 订单装入后**不可中途修改**，必须**显式 `deliver`** | 忘记 deliver → 订单永远不结清 |
| **不假设 agent / zone 数量** | 禁止出现 `ap1`、`r1` 这类硬编码 |
| `PackagingPoint.setOrder` **只能调用一次** | 换单需要先改 Java（见 M1-2） |
| `Workstation` 创建**无幂等保护**，重复建会抛异常 | 场景构建要判重 |
| `duration` 参数是 `long`，必须写 `2000l` | 语法错 |
| `Warehouse` **不是 zone**，机器人进不去 | 取货只能经 Access Point |
| 一次移动实际耗时 **≈4 秒**（两层 `sleep 2000`） | 调度估算时别按 2 秒算 |

---

## 3. 任务清单与执行顺序

依赖关系（必须自下而上）：

```mermaid
graph LR
    M0[M0 GAIA 分析模型] --> M1[M1 补工件缺口<br/>Java]
    M1 --> M2[M2 动态场景构建]
    M2 --> M3[M3 订单闭环]
    M3 --> M4[M4 任务编排 + 并发]
    M4 --> M5[M5 策略打磨]
    M5 --> M6[M6 报告 + 视频]
    M3 -.-> M6
    M0 -.-> M6
```

---

### M0 — GAIA 分析模型（纯文档，先做）

**为什么第一步**：ASTRA 的 agent 类型、计划分组、消息协议都从这里推导。**先写代码再补设计 = 返工**。

产出：**角色表 + 交互表**，每个角色一行「职责 / 权限 / 活动 / 协议」。

> ✅ **M0 交付物：[M0-GAIA-分析模型.md](M0-GAIA-分析模型.md)**（6 角色 + 7 协议 + 组织规则 + 角色→计划映射）

候选角色（GAIA 正式化后定稿，先按这个切分代码骨架）：

| 候选角色 | 职责 | 对应代码位置（预期） |
|---|---|---|
| Order Manager | 装载订单、追踪零件到齐、触发 deliver、计时 | `Main.astra` 订单计划组 |
| Production Planner | 展开多层依赖，算出要生产/搬运什么 | `Main.astra` 编排计划组 |
| Resource Broker | 分配 zone 与机器人，处理互斥 | `Main.astra` 或独立 `Broker.astra` |
| Transporter (×N) | 执行搬运，报告能量与位置 | `Robot.astra`（已存在，需泛化） |
| Workstation Monitor | 盯工作站状态，READY 即触发 operate | `Main.astra` 监视计划组 |
| Metrics Keeper | 记录时间与价值 | 新 `Metrics` 工件 + 查询计划 |

验收：能口头说明「用户需求 → 角色 → 交互协议 → ASTRA 计划」这条链每一跳。

---

### M1 — 补工件缺口（Java，让环境「可编排」）

**为什么第二步**：不补这几个缺口，M3/M4 只能靠硬编码绕过，后面必返工。

| # | 改什么 | 文件 | 具体动作 |
|---|---|---|---|
| M1-1 | 让 agent 能**按角色查 zone** | `Floorplan.java` | 增加 `zonesOfType(String type, OpFeedbackParam<List<String>>)`，返回某角色下所有 zone 名 |
| M1-2 | 订单能**结清 + 换单** | `PackagingPoint.java` | 补状态属性（`loaded`/`fulfilled`/`cleared`）；`deliver()` 真正重置 `order`/`outstanding`；允许再次 `setOrder` |
| M1-3 | 能**计时计价** | 新建 `artifacts/Metrics.java` | `orderStarted(id, time)` / `orderFulfilled(id, time, value)` / 观察属性 `totalTime`、`totalValue`、`ordersDone` |
| M1-4 | 修 3 个 bug | `Robot.java`、`AccessPoint.java` | `load`/`unload` 判电量误用 `MOVE_COST` → 改 `LOAD_COST`；`charge()` 加 `maxEnergy` 封顶；`retrieve` 在已持货时拒绝 |

验收：`mvn -o compile` 通过；写一个临时 `!main` 逐条调用新 API 并打印，全部有正确输出。

---

### M2 — 动态场景构建（替掉全部硬编码）

| # | 做什么 |
|---|---|
| M2-1 | 把 `!setup()` 的建场景动作抽成「**列表驱动**」：`[["ap","ap1"],["ap","ap2"],...]` 逐条循环创建 |
| M2-2 | `!main` 里用 `?locations` 拿到全部 zone，按类型归类成 `accessPoints / workstations / storagePoints / packagingPoints / chargers` 信念 |
| M2-3 | 机器人 agent 数量也从列表生成（`S.createAgent` 循环），数量 ≥2 |
| M2-4 | 删除所有 `ap1`、`ws1`、`pp1`、`r1` 字面量 |

验收：**验收项 1** —— zone 与 robot 各翻倍，不改逻辑仍跑通。

---

### M3 — 订单闭环

| # | 做什么 |
|---|---|
| M3-1 | 订单生命周期计划：装载 → 逐个零件确认到达 → `deliver` → 调 `Metrics` 记时间/价值 → 装下一张 |
| M3-2 | 订单所需零件 → 展开成「原料清单 + 需生产的中间品清单」 |
| M3-3 | 价值来源定下来（订单里的零件数？固定值？写进场景配置） |

验收：**验收项 4 + 5** —— 连续完成 2 张订单，`Metrics` 打印出每单耗时与累计价值。

---

### M4 — 任务编排 + 并发（工作量最大，约占一半）

| # | 做什么 |
|---|---|
| M4-1 | **依赖展开**：递归解析 `inputsNeeded`，得到「先做什么、再做什么」的任务 DAG |
| M4-2 | **任务队列 + 机器人池**：可执行任务入队，空闲机器人认领 |
| M4-3 | **改串行 `transfer` 为异步**：`send` 后立即返回，用 `transferred` / `failed` 消息回调，而不是 `wait` 阻塞 |
| M4-4 | **失败与超时**：新增 `@message(failure, ...)` 处理器 + 超时重派任务（当前任何异常都会让 Main 永久卡死） |
| M4-5 | **zone 冲突处理**：拿不到 zone 就等 / 换序，而不是抛异常失败 |
| M4-6 | **Workstation 监视**：输入凑齐即自动 `operate`，产出即生成搬运任务 |

验收：**验收项 2、3、7** —— 并发日志重叠、两级产线跑通、抢 zone 不死锁。

---

### M5 — 策略打磨（可砍，砍了就少几分）

| # | 做什么 |
|---|---|
| M5-1 | 充电改**阈值触发**（当前 Robot 是搬完必充，白耗时间） |
| M5-2 | **存储背压**：目标被占 / 中间品等下游时，先 `sp` 暂存 |
| M5-3 | 优先级：先做能解锁下游的任务 |
| M5-4 | 日志可读（时间戳 + 订单号 + 机器人名） |

---

### M6 — 报告 + 视频

| # | 做什么 |
|---|---|
| M6-1 | 报告：**反思挑战与经验教训**（不是罗列功能），突出设计决策为什么这么定 |
| M6-2 | 把 M0 的 GAIA 模型放进报告，**每条设计指到具体计划名/行号** |
| M6-3 | 3–5 分钟视频：镜头对准「设计文档 → 代码 → 运行画面」的对应，不追求大片效果 |

---

## 4. 当前进度

| 阶段 | 状态 |
|---|---|
| 克隆起点代码 | ✅ 完成 |
| 三个 astra 文件加中文注释 | ✅ 完成 |
| 起点代码与作业要求逐项核对 | ✅ 完成（[week4.md](week4.md)） |
| GitHub 备份 | ✅ 完成 |
| **M0 GAIA 分析模型** | ✅ 完成（[M0-GAIA-分析模型.md](M0-GAIA-分析模型.md)） |
| M1 – M6 | ⬜ 未开始（下一步：**M1 补工件缺口**） |

---

## 5. 主要风险

| 风险 | 应对 |
|---|---|
| **先写代码后补设计** → 设计↔实现对不上，丢 40% | 严格执行 M0 → M1 顺序，M0 定稿前不写实现代码 |
| **M4 范围失控** | 先做「2 机器人 + 单层依赖」能跑通，再叠多层依赖 |
| 一次移动≈4 秒，调试周期长 | 临时把 `Thread.sleep` 调小（如 200ms）加速调试，交付前改回 |
| `Main` 单点卡死 | M4-4 的 `failure` + 超时是硬要求，不是加分项 |
| 设计文档写成功能清单 | GAIA 要写 **role / interaction / organisation rule**，不是"系统能做什么" |
