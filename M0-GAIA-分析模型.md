---
project: COMP40040 Manufacturing Mayhem
stage: M0 — GAIA 分析模型
type: design
status: 初稿（待定稿）
date: 2026-09-28
tags: [COMP40040, GAIA, MAS, analysis, manufacturing-mayhem]
---

# M0 — GAIA 分析模型（Manufacturing Mayhem）

> **阶段定位**：GAIA **分析阶段（analysis phase）** 的产出 = **角色模型（Roles Model）+ 交互模型（Interactions Model）+ 组织规则（Organisational Rules）**。
> 本文件即 **M0 交付物**；M1–M6 全部从这里推导。**M0 定稿前不写实现代码。**
> 配套：`SOP.md`、`overview.md`、`COMP40040-Project-ManufacturingMayhem-解读.md`。

---

## 0. 交付物与验收

**交付物**

| # | 产物 | 在哪 |
| --- | --- | --- |
| 1 | **角色表**（每角色：职责 / 权限 / 活动 / 协议） | §2 |
| 2 | **交互表**（协议：发起方 / 响应方 / 消息 / 结果） | §3 |
| 3 | **组织规则** | §4 |
| 4 | **角色 → ASTRA 计划** 可追溯映射 | §5 |

**验收标准（SOP 原文）**：能口头说明「**用户需求 → 角色 → 交互协议 → ASTRA 计划**」这条链的**每一跳**。
→ 见 §1（需求→角色）与 §5（角色→计划）。

> GAIA 术语提醒：GAIA 用 **liveness（活性，"好事终将发生"）** 与 **safety（安全性，"坏事永不发生"）** 描述职责；用 **protocol（协议）** 描述角色间交互，用 **activity（活动）** 描述角色私有动作。

---

## 1. 从需求到角色（Derivation：需求 → 角色）

| 需求 / 验收线（来自作业 + SOP） | 需要的能力 | 承担角色 |
| --- | --- | --- |
| 订单闭环：装载 → 到齐 → deliver → 换单（验收 4） | 跟踪订单、触发交付、换单 | **Order Manager** |
| 目标函数：每单耗时 + 总价值（验收 5） | 计时计价 | **Metrics Keeper** |
| 多层依赖：WS-A 产出作为 WS-B 输入（验收 3） | 展开依赖、生成任务 | **Production Planner** |
| ≥2 机器人并发搬运（验收 2） | 任务分配、并发 | **Resource Broker** + **Transporter** |
| 不因冲突死锁（验收 7） | 互斥、等待/绕行 | **Resource Broker** |
| 低电自动充电（验收 6） | 能量管理 | **Transporter**（+ Broker 协调充电点） |
| 不假设 zone/robot 数量（验收 1） | 动态发现角色、按池分派 | 所有角色（尤其 **Resource Broker**） |
| 设计可追溯（验收 8） | 角色 ↔ 计划映射 | 本文件 §5 |

> **结论**：需求自然切出 **6 个角色** —— OM、PP、RB、T(×N)、WM、MK。

---

## 2. 角色模型（Roles Model）

> 每个角色给 4 项 GAIA 属性：**职责（liveness/safety）· 权限 · 活动 · 协议**。

### R1 · Order Manager（OM，订单管理者）

| 属性 | 内容 |
| --- | --- |
| **职责 (Liveness)** | ① 装入的每张订单**最终被结清**：$\text{LOADED}(o) \Rightarrow \Diamond\,\text{CLEARED}(o)$；② 结清后**最终装入下一张**：$\text{CLEARED}(o) \Rightarrow \Diamond\,\text{LOADED}(o')$；③ 每单的 start / fulfil 时间**最终被记录** |
| **职责 (Safety)** | ④ 订单在 **FULFILLED 之前**不得被修改；⑤ `outstanding` 非空时**不得** deliver |
| **权限 (Permissions)** | 读 `PackagingPoint.order` / `outstanding`；调用 `PackagingPoint.setOrder`（仅换单）与 `PackagingPoint.deliver`；调用 `Metrics.orderStarted/orderFulfilled` |
| **活动 (Activities)** | 监控 `outstanding` 变空 → 触发 deliver；请求 Product List 生成新订单；把事件交给 MK |
| **协议 (Protocols)** | `P1 Order-Intake`（→ PP）、`P6 Metrics-Record`（→ MK）；deliver 为**工件操作**（非 agent 消息） |

### R2 · Production Planner（PP，生产规划者）

| 属性 | 内容 |
| --- | --- |
| **职责 (Liveness)** | ① 对每个 `outstanding` 零件，**最终产生**「已取料」或「已生产」的执行方案；② 多层依赖**最终被展开**为可执行任务 DAG（有向无环图）；③ 任务**最终被派发** |
| **职责 (Safety)** | ④ 不得请求**未 ready** 的工作站开工；⑤ 不得违反配方（recipe）；⑥ 不得请求**不存在 / 库存为 0** 的物料 |
| **权限 (Permissions)** | 读 `Workstation.inputsNeeded/state/output`、`Warehouse` 库存、`AccessPoint.available`、`Product List`；**只读**，不直接动机器人 |
| **活动 (Activities)** | **递归展开 `inputsNeeded`**（多层依赖）；生成三类任务（取料 / 搬运 / 生产）；任务排序（先解锁下游） |
| **协议 (Protocols)** | `P1 Order-Intake`（收 OM）、`P2 Task-Dispatch`（→ RB）、`P3 Work-Order`（↔ WM） |

### R3 · Resource Broker（RB，资源代理）

| 属性 | 内容 |
| --- | --- |
| **职责 (Liveness)** | ① 每个待处理搬运**最终被授予**某机器人，或**最终失败并上报**；② zone 释放后**最终唤醒**等待者；③ 冲突时**最终重试成功** |
| **职责 (Safety)** | ④ **同一 zone 不得同时授予两个机器人**（互斥）；⑤ **同一机器人不得同时承接两个搬运**；⑥ 不得把任务授予**能量不足**的机器人（除非先安排充电） |
| **权限 (Permissions)** | 读 `Floorplan.locations/isOccupied`、`Robot.state/energy/at/holding`；写「分配表」（内部信念） |
| **活动 (Activities)** | 匹配（task ↔ 空闲 robot）；冲突解决（等待 / 换序）；**死锁避免**（打破循环等待）；充电点协调 |
| **协议 (Protocols)** | `P2 Task-Dispatch`（收 PP）、`P4 Transport-Assign`（→ T）、`P5 Transport-Result`（收 T） |

### R4 · Transporter（T，搬运者）— ×N（每个机器人一个）

| 属性 | 内容 |
| --- | --- |
| **职责 (Liveness)** | ① 每个被分配的搬运**最终回报** `transferred` 或 `failed`；② 电量低于阈值时**最终**回到充电点并**完成充电** |
| **职责 (Safety)** | ③ **一次只搬 1 件**；④ **不进入被占 zone**；⑤ 不在未装载时 unload、不超载 |
| **权限 (Permissions)** | `Robot` 工件：`move` / `load` / `unload` / `charge`；读自身 `state/at/holding/energy`；间接触发 `AccessPoint.take`、`StoragePoint.take/give`、`Workstation.take/give`、`PackagingPoint.give` |
| **活动 (Activities)** | `move` / `load` / `unload` / `charge` |
| **协议 (Protocols)** | `P4 Transport-Assign`（收 RB）、`P5 Transport-Result`（回 RB）、`P7 Charge`（自触发 / 向 RB 申请充电点） |

### R5 · Workstation Monitor（WM，工作站监视者）— ×N 或集中式

| 属性 | 内容 |
| --- | --- |
| **职责 (Liveness)** | ① 工作站 READY 时**最终被 operate**；② DONE 时**产出最终被取走** |
| **职责 (Safety)** | ③ **不得 operate 非 ready 的工作站**；④ 不得丢失产出 |
| **权限 (Permissions)** | 读 `Workstation.state/inputsNeeded/output`；调用 `Workstation.operate` |
| **活动 (Activities)** | 监视状态；触发 operate；报告产出就绪 |
| **协议 (Protocols)** | `P3 Work-Order`（收 PP 的 operate 请求 / 回 `outputReady`） |

### R6 · Metrics Keeper（MK，度量记录者）

| 属性 | 内容 |
| --- | --- |
| **职责 (Liveness)** | ① 每个订单事件**最终被记录**；② `totalTime` / `totalValue` / `ordersDone` **最终正确** |
| **职责 (Safety)** | ③ **不重复计数**；④ 累计值**单调不减** |
| **权限 (Permissions)** | 读/写 `Metrics` 工件 |
| **活动 (Activities)** | 记录 start / fulfil 时间与价值；累计 |
| **协议 (Protocols)** | `P6 Metrics-Record`（收 OM） |

---

## 3. 交互模型（Interactions Model）

### 3.1 协议表（Protocols）

| ID | 协议 | 发起方 | 响应方 | 触发 | 消息（请求 → 回复） | 成功结果 | 失败结果 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| **P1** | Order-Intake | OM | PP | 订单装入 | `request(newOrder, items)` → `inform(accepted)` | PP 生成任务 | PP 拒收（物料不存在） |
| **P2** | Task-Dispatch | PP | RB | 有待搬运任务 | `request(transport, from, to, item)` → `inform(granted)\|inform(denied)` | 任务进入分配 | 无可用机器人 → 重试 |
| **P3** | Work-Order | PP | WM | 某 WS 输入将齐 / 收到 outputReady | `request(operate, ws)` ↔ `inform(outputReady, ws, item)` | WS 产出成品 | WS 未 ready → 等待 |
| **P4** | Transport-Assign | RB | T | 有任务 + 有机器人 | `request(transfer, from, to)` | 机器人执行搬运 | — |
| **P5** | Transport-Result | T | RB（转 PP） | 搬运结束 | `inform(transferred, from, to)` / `inform(failure, reason)` | PP 更新任务状态 | PP 重派任务 |
| **P6** | Metrics-Record | OM | MK | 订单开始 / 完成 | `inform(orderStarted, id, t)` / `inform(orderFulfilled, id, t, value)` | 计入统计 | — |
| **P7** | Charge | T | RB（可选） | 电量 < 阈值 | `request(charger)` → `inform(go, charger)` | 回充电点充电 | 充电点被占 → 等待 |

> **对照现有代码**：当前只有 `P4/P5` 的雏形——`Main` 用 `send(request, robot, transfer(from,to))` + `wait(transferred(from,to))`（见 `Main.astra` `+!transfer`），`Robot.astra` 用 `@message(request,...,transfer(from,to))` 接收、`send(inform, sender, transferred(from,to))` 回复。**P1、P2、P3、P5-failure、P6、P7 都是新增。**

### 3.2 消息词汇表（Message Vocabulary）

> 参照 `Base.astra` 的 `types comms { transfer, transferred }` 扩展。

| 消息公式 | 方向 | 含义 |
| --- | --- | --- |
| `newOrder(list items)` | OM → PP | 新订单所需零件清单 |
| `transport(string from, string to, string item)` | PP → RB | 请求把 item 从 from 搬到 to |
| `transfer(string from, string to)` | RB → T | 指派一次搬运（现有） |
| `transferred(string from, string to)` | T → RB/PP | 完成回执（现有） |
| `failure(string reason)` | T → RB/PP | 失败回执（**新增，防死锁**） |
| `operate(string ws)` | PP → WM | 请求运行工作站 |
| `outputReady(string ws, string item)` | WM → PP | 产出就绪 |
| `orderStarted(string id, long t)` / `orderFulfilled(string id, long t, int value)` | OM → MK | 订单事件 |

### 3.3 交互总览图

```mermaid
flowchart LR
    subgraph Coord[协调层]
        OM[R1 Order Manager]
        PP[R2 Production Planner]
        RB[R3 Resource Broker]
        MK[R6 Metrics Keeper]
    end
    subgraph Exec[执行层]
        T[R4 Transporter ×N]
        WM[R5 Workstation Monitor]
    end
    OM -- P1 newOrder --> PP
    OM -- P6 orderEvent --> MK
    PP -- P2 transport --> RB
    PP -- P3 operate --> WM
    WM -- P3 outputReady --> PP
    RB -- P4 transfer --> T
    T -- P5 transferred/failure --> RB
    T -. P7 charge .-> RB

    AP[[AccessPoint]] -. artifact .- T
    SP[[StoragePoint]] -. artifact .- T
    WS[[Workstation]] -. artifact .- WM
    WS -. artifact .- T
    PPpoint[[PackagingPoint]] -. artifact .- OM
    WS -. artifact .- T
    FP[[Floorplan]] -. isOccupied .- RB
```

---

## 4. 组织规则（Organisational Rules）

### 4.1 角色关系（Relationships）

| 关系 | 从 | 到 | 说明 |
| --- | --- | --- | --- |
| **Authority（权威）** | PP | RB, WM | PP 定任务，RB/WM 执行 |
| **Authority（权威）** | RB | T | RB 分配，T 执行 |
| **Peer（对等）** | OM ↔ PP | — | 订单与生产的协商 |
| **Peer（对等）** | OM ↔ MK | — | 事件上报 |
| **Acquaintance（认识）** | 全角色 | Floorplan（artifact） | 通过环境共享状态 |

### 4.2 结构规则（Structural）

1. **每张订单恰好由 1 个 Order Manager 管理**（1 PackagingPoint ↔ 1 OM 实例）。
2. **每个工作站恰好由 1 个 Workstation Monitor 监视**（可集中为 1 个 agent 扮演多个 WM 角色）。
3. **每个机器人恰好由 1 个 Transporter 扮演**（机器人数 = Transporter 数，**不写死 N**）。
4. **所有搬运必须经 RB 分配**——Transporter **不得**被 PP 直接指派（强制单一分配权，防冲突）。
5. **zone 互斥的最终权威是 `Floorplan`**（代码已实现 `isOccupied` + `move` 抛异常）；RB 是**上层调度**，Floorplan 是**底层兜底**。

### 4.3 行为规则（Behavioural / Safety）

6. **一个 zone 同时至多 1 个机器人**（安全属性，Floorplan 强制）。
7. **一个机器人同一时刻至多 1 个搬运任务**（RB 强制）。
8. **订单未 FULFILLED 不得 CLEARED**；未 CLEARED 不得装新单（OM 强制）。
9. **任何失败必须回执** `failure`，否则会重演当前代码的 `Main` 永久 `wait` 死锁（`Robot.astra` 目前无失败回执）。

---

## 5. 角色 → ASTRA 计划 映射（Traceability）

> **验收 8 的核心**：每个 role / interaction 都能指到具体计划。

| 角色 | 现有可复用计划（`Base.astra` / `Main.astra` / `Robot.astra`） | 需新增计划 | 目标文件 |
| --- | --- | --- | --- |
| **OM** | `!order`、`!packagingPoint`、`?available`(pp) | `!orderLoop`、`!deliver`、`?outstanding`、`!loadNextOrder` | `Main.astra` 订单计划组 |
| **PP** | `?inputsNeeded`、`?state`、`!configure`、`!available`(ap)、`!operate` | `!expandDependencies`、`!planTasks`、`!dispatch` | `Main.astra` 编排计划组 |
| **RB** | `?locations`、`?free`（`isOccupied`） | `!brokerLoop`、`!assign`、`!release`、`?idleRobots` | `Broker.astra`（新）或 `Main.astra` |
| **T** | `!at`、`!holding`、`!empty`、`@message(request,...,transfer)` 骨架 | 泛化 `@message`、加 `@message(failure)`、阈值充电 | `Robot.astra` |
| **WM** | `?state`、`!operate` | 监视循环、`outputReady` 上报 | `Main.astra` 监视计划组 |
| **MK** | — | 依赖 `Metrics` 工件 | 新 `Metrics.java` + 查询计划 |

**「需求 → 角色 → 协议 → 计划」一跳示例**（把这条链讲清楚即可过验收）：

```text
需求：多层依赖（验收 3）
  └─ 角色：Production Planner(R2)
       └─ 协议：P2 Task-Dispatch / P3 Work-Order
            └─ 计划：!expandDependencies (Main.astra)
                     → request(transport,...) → Broker
                     → !operate (Base.astra, 已有)
```

---

## 6. 与现有代码的差距（喂给 M1）

> M0 已把「需要什么能力」定清；下列是**角色能落地的前提**，与 SOP M1 一一对应。

| 角色需求 | 代码缺口 | 对应 SOP 任务 |
| --- | --- | --- |
| RB 要按角色查 zone（不写死 `ap1`） | `Floorplan` 无「按类型列 zone」 | **M1-1** `zonesOfType(...)` |
| OM 要结单 + 换单 | `PackagingPoint.deliver()` 是打印桩、`setOrder` 只能调一次 | **M1-2** |
| MK 要计时计价 | 无 `Metrics` 工件 | **M1-3** `Metrics.java` |
| T 要正确的能量与拒绝逻辑 | `load/unload` 误用 `MOVE_COST`、`charge` 不封顶、`retrieve` 可覆盖 | **M1-4** |

---

## 7. 暂定 agent 映射（Design 预览，非 M0 交付）

> GAIA 分析阶段只定义**逻辑角色**；「角色 → agent」是**设计阶段**的事。这里给一个**最小可交**的暂定切法：

| Agent 类型 | 扮演角色 | 理由 |
| --- | --- | --- |
| `Main`（协调者，1 个） | OM + PP + RB + WM + MK | 最小可交、改动最小；集中式调度 |
| `Robot`（×N） | T | 已有骨架，泛化即可 |

> 若时间允许，可把 **RB** 拆成独立 `Broker.astra`，让设计更「多 agent」、也更符合 GAIA 的角色分离。**但最小可交不强制拆。**

---

## 8. 验收自检（对照 SOP 8 条）

| SOP 验收 | 本模型如何保证 |
| --- | --- |
| 1 不假设数量 | RB 用 `?locations` + `zonesOfType` 动态发现；机器人数 = Transporter 数 |
| 2 ≥2 机器人并发 | P2/P4/P5 异步派发；RB 维护「机器人池」 |
| 3 多层依赖 | PP 的 `!expandDependencies` 递归展开 `inputsNeeded` |
| 4 订单闭环 | OM 的 `!orderLoop` + `!deliver` + `!loadNextOrder` |
| 5 目标函数可读 | MK 的 `Metrics`（totalTime/totalValue/ordersDone） |
| 6 低电自动充电 | T 的阈值充电（P7） |
| 7 不死锁 | RB 互斥 + P5 `failure` 回执 + 重派 |
| 8 设计可追溯 | §5 角色→计划映射表 |

---

## 附：M0 定稿检查清单

- [ ] 6 个角色是否都有「职责 / 权限 / 活动 / 协议」四项？
- [ ] 每条需求是否都能指到 ≥1 个角色？
- [ ] 每个协议是否有明确发起方 / 响应方 / 消息 / 成功与失败结果？
- [ ] 每个角色是否都能指到具体 ASTRA 计划（现有或待建）？
- [ ] 安全属性是否覆盖：zone 互斥、单机器人单任务、订单先 FULFILLED 后 CLEARED、失败必回执？
- [ ] 是否仍然**没有任何硬编码数量**（`ap1` / `r1` …）？

> 定稿后 → 进入 **M1**（补 Java 工件缺口）。
