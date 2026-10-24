# Week 4 — manufacturing-mayhem 代码库核对笔记

> 对照材料：`COMP40040-Project-ManufacturingMayhem-解读.md`（作业要求解读）
> 对照对象：`manufacturing-mayhem/`（GitLab 起点代码库）

---

## 一、结论：相符，但 4 处是「文档写了 / 代码没有」

解读文档对**规格**的描述基本能对上代码，但其中一些状态是**设计层面的概念模型**，代码里并不存在。

| 文档说法 | 代码实际 | 判定 |
|---|---|---|
| Workstation `IDLE→READY→ACTIVE→DONE→IDLE`，DONE→IDLE 重置输入 | `Workstation.java` 四个常量齐全，`operate()` 走 ACTIVE→sleep(duration)→DONE，`take()` 回到 IDLE 并重置 `inputsNeeded` | ✅ 完全一致 |
| Robot 五态 `IDLE/MOVING/LOADING/UNLOADING/CHARGING` | `Robot.java` 五个常量同名 | ✅ 一致 |
| 每个 zone 同时只能 1 个 robot | `Floorplan.java` 的 `move()` / `place()` 用 `Location.isOccupied()` 抛异常拦截 | ✅ 一致 |
| Access Point 取料不可退回 | `Warehouse` 只有 `retrieve()`，没有归还操作 | ✅ 一致 |
| 充电点是机器人创建时生成、也是起点 | `RobotChargePoint.install(name)` 创建 Robot artifact，构造参数就是充电点名 | ✅ 一致 |
| **Packaging Point: `FULFILLED → CLEARED → 装新订单`** | 只有 `order` / `outstanding` 两个观察属性；`deliver()` **只 `println` 一下，既不重置订单也不报结单**；没有 CLEARED 状态；`setOrder` 二次调用会因属性已定义而报错 | ❌ **未实现，要自己写** |
| **Access Point 三态 `EMPTY/HOLDING/OCCUPIED`** | 只有观察属性 `available`（值为 `"none"` 或物料类型名），占用信息在 `Floorplan` 内部，不对外暴露 | ❌ 概念模型 |
| **Zone 作为一个有状态的元素** | 代码里**没有 Zone 类**，只有 `model/Location`（`ArtifactId` + `occupancy`）；「角色」由 artifact 类型隐式决定 | ❌ 概念模型 |
| **Item 有 `RAW→RETRIEVED→…→SHIPPED` 状态机** | `model/Item` 只有一个 `String type` 字段 | ❌ 概念模型 |
| **目标：最小化完成时间 + 最大化订单价值，并记录每单完成时间** | 全仓库**没有任何计时、价值、产品清单、场景（scenario）代码** | ❌ **完全没实现** |

两点文档里没有、但代码里存在的细节：

1. `Warehouse` **不是 zone** —— 它没 `install()` 进 floorplan，机器人飞不进去，只能通过 Access Point 取料。
2. `Main.astra` 里 **`r2` 只创建了 artifact，没有创建 robot agent**（只 `S.createAgent("r1", "Robot")`），r2 是个闲置的「房客」。

---

## 二、框架与运行链

| 项 | 值 |
|---|---|
| 构建 | Maven，`pom.xml`：`comp40040:project:1.0.0` |
| 父 POM | `com.astralanguage:astra-cartago-base:2.0.12`（本地 `~/.m2` 已有） |
| 语言级别 | Java 17 |
| 依赖 | `astra-interpreter` / `astra-apis` / `astra-compiler` / `astra-cartago` |
| 编译期 | `astra-maven-plugin` 在 `compile` 阶段把 `src/main/astra/*.astra` 编译到 classpath |
| 入口 | 父 POM 定义 `astra.main=Main`、`astra.name=main` → 启动 `Main` agent 的 `!main` 计划 |
| UI | `ui/PlantViewer`，Swing 窗口 400×800，左列 Zones、右列 Robots |

**agent 层次（3 个 ASTRA 文件）**

- `Main.astra` —— 编排者，`!main` 里手动跑一遍完整 SOP
- `Robot.astra` —— 被 Main 用 `S.createAgent` 拉起，`initial !init()`，收到 `request transfer(from,to)` 就执行「走到源点→load→走到目标→unload→顺路充电→回 inform」
- `Base.astra` —— 抽象基类，只有 `types` / `module` 声明 + 整套 artifact 操作计划库（不实例化）

---

## 三、初始状态的参数

### 3.1 硬编码常量（`Robot.java`）

| 参数 | 值 | 说明 |
|---|---|---|
| `MOVE_COST` | 100 | 每次 `move()` 扣 |
| `LOAD_COST` | 50 | 每次 `load()` / `unload()` 扣 |
| `RECHARGE_AMOUNT` | 300 | 每次 `charge()` 加 |
| 初始 `maxEnergy` | **800** | 由 `Base.astra` 里 `RobotChargePoint` 的 `cartago.params([800])` 传入 |

### 3.2 固定耗时（都是 `Thread.sleep`）

| 动作 | 耗时 |
|---|---|
| `move`（Robot 工件内） | 2000 ms |
| `Floorplan.move`（占用转移）**额外再等** | 2000 ms → **一次移动实际 ≈4 s** |
| `load` / `unload` | 各 2000 ms |
| `charge` | 1000 ms |
| 工作站生产 | `duration` 参数，示例 = 2000 ms |

### 3.3 每个 artifact 的初始观察属性

| 工件 | 观察属性（初始值） |
|---|---|
| `Floorplan` | `locations` = 全部 zone 名集合 |
| `Warehouse` | 每种物料一个 **int**（= 库存量） |
| `AccessPoint` | `available` = `"none"` |
| `StoragePoint` | `available` = `"none"` |
| `RobotChargePoint` | `available` = `"none"` |
| `Robot` | `state`=`idle`、`holding`=`none`、`at`=充电点名、`energy`=`maxEnergy`、`maxEnergy` |
| `Workstation` | `state`=`idle`；`configure()` 后再加 `inputs`(list)、`output`(string)、`duration`(long)、`inputsNeeded`(list) |
| `PackagingPoint` | `order`(list)、`outstanding`(list)；**没有 state 属性** |

### 3.4 合法角色约束（写死在代码里的白名单）

- 能 `load` 的工件类型：`AccessPoint` / `StoragePoint` / `Workstation`
- 能 `unload` 的工件类型：`PackagingPoint` / `StoragePoint` / `Workstation`
- `Warehouse` / `RobotChargePoint` / `Floorplan` **不在任何白名单里** —— 机器人无法对它们装卸

---

## 四、元素与类清单

**ASTRA 层**：`Base`、`Main`、`Robot`

**CArtAgO 工件层**（`src/main/java/artifacts/`）

| 类 | 对应文档角色 | 对外接口 |
|---|---|---|
| `Floorplan` | 场地总表（**枢纽**） | `install` / `move` / `place` / `location` / `isOccupied` / `setStatus` / `setRobotField` / `setCustomField`（全是 `@LINK`） |
| `Warehouse` | 仓库 | `add` / `retrieve` |
| `AccessPoint` | 取料点 | `retrieve`（取自仓库）、`take`（被机器人取走） |
| `StoragePoint` | 存储点 | `give` / `take`，容量 1 |
| `Workstation` | 工作站 | `configure` / `operate` / `give` / `take` |
| `PackagingPoint` | 包装点 | `setOrder` / `give` / `deliver`（桩） |
| `Robot`（工件） | 机器人本体 | `move` / `load` / `unload` / `charge` |
| `RobotChargePoint` | 充电点 | `install`（顺带创建 Robot 工件） |

**model**：`Item`（只有 `type`）、`Location`（`ArtifactId` + `occupancy`）

**modules**：`PropertyExtractor` —— 把 CArtAgO 观察属性转成 ASTRA 的 `ListTerm` / `String` / `Formula`（`listValues` / `stringValue` / `isBound`）

**ui**：`PlantViewer`、`ZonePanel`、`RobotPanel`

> **关键架构点**：所有 zone 工件通过 `install()` + `linkArtifacts(aid, "out-fp", fpId)` 挂到唯一的 `Floorplan` 上。**zone 互斥的唯一权威在 Floorplan**，robot 工件自己不管冲突，它只是调 `out-fp.move(...)` 让 Floorplan 校验并抛异常。

---

## 五、初始场景设定（`Main.astra` 的 `!setup`）

```text
仓库:      apples × 2, pastry × 10
区域(7个): ap1, ap2      → AccessPoint
          sp1            → StoragePoint
          pp1            → PackagingPoint
          ws1            → Workstation
          r1-charger     → RobotChargePoint (r1 的家)
          r2-charger     → RobotChargePoint (r2 的家)
订单:      pp1 ← ["apple-pie"]        ← 单零件订单
工作站:    ws1: [apples, pastry] → apple-pie, duration = 2000ms
机器人工件: r1(maxEnergy 800), r2(maxEnergy 800)
机器人 agent: 只有 r1（S.createAgent("r1","Robot")）
```

`!main` 里手写的示例 SOP：

```text
retrieve(apples) → ap1 → transfer(r1, ap1, ws1)
?inputsNeeded(ws1) 得 pastry → retrieve(pastry) → ap1 → transfer(r1, ap1, ws1)
?state(ws1) → operate(ws1)
transfer(r1, ws1, sp1)        ← 成品先丢进存储点
?available(sp1) → transfer(r1, sp1, pp1)
```

与此对应的 `+!transfer` 计划是**「发请求 + 等回复」的同步调用**（`send(request,...)` → `wait(transferred(...))`），一次只能有一个 `transfer` 在飞。

---

## 六、和作业要求的差距（待办清单）

1. **写死数量**：`ap1/ap2/sp1/pp1/ws1/r1/r2` 全硬编码 → 违反「不假设 agent 数量」，要改成从 `?locations` 动态发现 + 按角色分类。
2. **没有调度编排**：`Main.astra` 是「一个人干活」的顺序脚本，没有多机器人协同、没有任务分配、没有冲突处理。
3. **Packaging Point 是半成品**：`deliver()` 不结单、不换单、不产生价值统计。
4. **没有目标函数实现**：无计时、无订单价值、无产品清单读取。
5. **没有背压/暂存策略**：`sp1` 建了但从没被 `give`，Storage Point 的完整通道（`load` 自 `StoragePoint` 合法）没被用到。
6. 顺手可修的小 bug：`load`/`unload` 判电量误用了 `MOVE_COST`；`charge()` 不封顶可超 `maxEnergy`；`AccessPoint.retrieve` 在已持货时会被覆盖。
