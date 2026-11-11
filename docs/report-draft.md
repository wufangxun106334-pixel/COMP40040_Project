# Manufacturing Mayhem — Reflective Report (DRAFT)

COMP40040 Individual Project · Student 25208508

## 1. Overview

This project builds a multi-agent system in ASTRA, on top of the CArtAgO artifacts supplied with the base project, that runs an automated factory: it retrieves raw materials, moves them between zones with battery-limited robots, runs workstations, and delivers orders at a packaging point. I designed the system with the GAIA methodology. The full analysis and design models are in the separate design document. This report focuses on what was hard, which decisions changed along the way, and what I would do differently.

The final system has two agent types:

- **`Main` (exactly one)** plays the Order Manager, Production Planner, Resource Broker, Workstation Manager and Metrics Keeper roles. It owns the order queue, works out the production steps, and hands transport tasks to robots.
- **`Robot` (one or more)** plays the Transporter role. Each instance carries out a transfer (move, load, move, unload) and replies with either `transferred` or `failure`. It then recharges and reports `ready`.

Coordination is **centralised task allocation**: one coordinator decides who does what, and it gives each transfer to whichever robot is currently idle. The agents interact through two message protocols: P4 `request transfer`, and P5 `inform transferred` / `failure` / `ready`. Zone exclusivity is enforced by the `Floorplan` artifact, not by negotiation between robots.

**Result.** On the three-order scenario (two `apple-pie` orders and one two-level `boxed-pie` order), the system delivered all three orders in 176 s with two robots sharing the work evenly (6 transfers each): `ordersDone=3`, `totalValue=50`. The `boxed-pie` order took 90 s, about twice as long as each single-level order, which matches the extra production stage.

## 2. Challenges and lessons learnt

### 2.1 Turning a GAIA design into running code

GAIA gave me roles, protocols and organisational rules, but none of these map one-to-one onto ASTRA constructs. The step that made the design usable was a **Services Model** in which every service names the actual plan that implements it: for example, *LoadOrder* is `!order`, *AssignTransport* is `!transfer`, and *ExecuteTransfer* is the Robot's `@message(request, transfer)` handler. After that, every design change had an obvious place in the code, and every code change had to be reflected back into the design document.

*Lesson:* a design is only traceable if the link goes down to plan names. Role names alone were too abstract to keep design and implementation consistent.

### 2.2 Scope creep: building, then removing, a Contract Net

My first design had three agent types, including a separate `Broker` agent that allocated transfers to robots with the **Contract Net Protocol** (call for proposals, bids based on distance and energy, award to the lowest cost). I implemented it, along with a task dependency graph to allow concurrent production steps. When I re-read the brief, I found that neither concurrency nor auction-based allocation is required, and that performance is "mainly for fun". Worse, CNP added nothing in practice: every robot returns to its charging point after each job, so all bids had the same cost and the auction never actually discriminated between robots.

I removed the Broker, the dependency graph, stock pre-checks and workstation reconfiguration, and moved to a two-agent design. The withdrawn designs are recorded in the design document (§9.4) as explicit trade-offs.

*Lesson:* check each feature against the requirements **before** implementing it. A coordination mechanism is only worth its complexity if the environment gives it something to decide.

### 2.3 Gaps in the provided artifacts

Several behaviours the brief describes were not supported by the base artifacts, and I had to extend them before the agents could be written cleanly:

- `PackagingPoint` could not be reset after delivery, so a second order could never be loaded. I added an explicit `cleared → loaded → fulfilled → cleared` state machine, exposed as an observable property.
- There was no way to discover zones by role, so agents had to hard-code names such as `ap1`. I added `Floorplan.zonesOfType`, and the agents now find every Access Point, Workstation, Storage Point and Packaging Point at runtime.
- There was nowhere to record order time and value, so I added a `Metrics` artifact.
- I fixed three small bugs: `load`/`unload` checked the wrong energy cost, `charge` could exceed the maximum battery, and an Access Point could overwrite an item that was already waiting.

*Lesson:* in an agent-plus-environment system, some "agent" problems are really environment problems. Fixing them in the artifact kept the agent code simpler and also made the rules (such as "cannot load a new order until the old one is delivered") hold for every agent, not just mine.

### 2.4 Planning multi-level production in a BDI language

A `boxed-pie` needs an `apple-pie`, which in turn needs `apples` and `pastry`. ASTRA has no built-in planner, so I wrote the dependency resolution myself as a recursive plan, `!expand`. It walks the recipe tree in **post-order** and records numbered `buildStep(k, workstation, item)` beliefs, so the step numbers are already a valid production order. `!produceItem` then executes steps 1..n. Intermediate products are parked in a Storage Point (recorded as `storedAt(item, sp)`), and the final product goes to the packaging point.

*Lesson:* representing the plan as **beliefs** rather than as nested goal calls made it inspectable and easy to debug: I could print the step list before anything moved. That mattered when each robot move takes several seconds.

### 2.5 Energy and liveness

The first multi-order run failed on the second order: robot `r1` reported `Insufficient Energy to move`. The robot only charged when asked, and nothing asked it. I changed the Transporter's behaviour so that after every transfer it returns to its charger and charges to full before replying. This is simple and safe, and it is also the reason the CNP bids were always equal (§2.2).

The first version of this fix created a hidden problem. A test run showed that **all 12 transfers were done by `r1`, while `r2` stayed idle the whole time**. There were two causes:

1. Plan contexts such as `robot(string r, ...)` always bind to the first matching belief.
2. A robot replied only after it had fully recharged, so `r1` was always free again by the time the next request came.

The system created as many robots as the scenario listed, but it really used only one. That breaks the spirit of the requirement not to assume a fixed number of agents.

I split the robot's reply in two:

- `transferred` is sent as soon as the item is unloaded;
- `ready` is sent after charging.

`Main` now keeps an `idle(robot)` belief and gives each job to any idle robot. While one robot recharges, the next job goes to the other. With two robots in the scenario, the work split evenly (6 transfers each) and the total time fell from 225 s to 176 s (−22%).

Because two robots can now move at the same time, a destination zone may be temporarily occupied. The movement plan therefore waits for the zone to become free (`!waitFree`) instead of failing.

*Lesson:* "supports N agents" must be tested by checking what each agent actually did, not just that N agents were created.

A related and more serious problem was **deadlock**. `Main` sent a request and then waited for `transferred(from, to)`. If anything went wrong inside the robot (an occupied zone, missing energy, a bad location), no reply ever came, and the whole system waited forever. I added a failure receipt:

- the Robot wraps the transfer in `try … recover` and sends `failure(transfer(from, to))`;
- `Main` waits for `transferred | failed`, and on failure fails the current goal instead of blocking;
- the order loop recovers, logs the failed order and stops cleanly.

I tested this by sending a robot to a zone that does not exist. The failure travelled all the way back and the system ended without hanging.

*Lesson:* in message-based coordination, **every request needs a reply on every path**. Handling only the success path is the most common way a multi-agent system deadlocks. This became organisational rule 9 in my design.

### 2.6 Language-level surprises in ASTRA

A few ASTRA details cost real debugging time:

- An agent that `extends Base` cannot share its own `types` declarations with the parent. Any formula that a `Base` plan uses must be declared in `Base`.
- `done` is a reserved word. A message called `done(...)` caused confusing compile errors until I renamed it.
- An agent must call `cartago.link()` before using even simple operations such as `cartago.println`; otherwise it fails with a `NullPointerException`.
- Queries that return values (`+?orderStatus(pp, status)`) need care. When the caller passes a constant instead of an unbound variable, the plan has to compare the value rather than assign it.

*Lesson:* write a tiny throw-away test agent for each new language feature before relying on it in the main system. I later used the same approach for edge-case tests of the artifacts.

## 3. Limitations and future work

- **Allocation is "first idle robot", not optimal.** The coordinator ignores distance and remaining energy. A cost-based choice is the natural next step, and it is also the point at which an allocation protocol such as CNP would start to pay off.
- **Zone waiting is check-then-move.** Two robots could in theory see the same zone as free at the same moment. The artifact then rejects the second move, and the failure receipt stops the order cleanly rather than deadlocking. A reservation step would remove this race.
- **Orders are processed one at a time** at each packaging point, and the production steps of an order run sequentially. Parallel steps on different workstations would reduce the time per order.
- **Charging to full after every job** is safe but wasteful. A threshold policy (charge only when the remaining energy cannot cover the next job) would cut idle time.
- **Order prioritisation by value** is not implemented. Orders are processed first in, first out.

## 4. Conclusion

The most important lessons were not about any single algorithm:

- Keep the design traceable down to plan names.
- Make sure every interaction can fail visibly.
- Resist building coordination machinery that the problem does not need.

The final system is deliberately simple: a star topology with one coordinator. Every part of it can be traced from a GAIA role and protocol to a specific ASTRA plan or CArtAgO operation.
