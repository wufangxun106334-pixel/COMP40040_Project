package artifacts;

import java.util.HashMap;
import java.util.Map;

import cartago.Artifact;
import cartago.OPERATION;

// 度量记录（M1-3，GAIA 角色 R6 Metrics Keeper）。
// 不是 zone：不 install 进 Floorplan，机器人不会与之交互，只由 Manager 调用。
// 时间由本工件统一取（System.currentTimeMillis），agent 只需报告事件。
public class Metrics extends Artifact {
    public static final String TOTAL_TIME = "totalTime";
    public static final String TOTAL_VALUE = "totalValue";
    public static final String ORDERS_DONE = "ordersDone";

    // 进行中的订单：id -> 开始时间
    private Map<String, Long> started = new HashMap<>();

    @OPERATION
    public void init() {
        defineObsProperty(TOTAL_TIME, 0L);
        defineObsProperty(TOTAL_VALUE, 0);
        defineObsProperty(ORDERS_DONE, 0);
    }

    @OPERATION
    public void orderStarted(String id) throws Exception {
        if (started.containsKey(id)) throw new Exception("Metrics: order already started: " + id);
        started.put(id, System.currentTimeMillis());
        System.out.println("[Metrics] order " + id + " started");
    }

    @OPERATION
    public void orderFulfilled(String id, int value) throws Exception {
        // remove 保证同一订单只计一次（不重复计数）
        Long start = started.remove(id);
        if (start == null) throw new Exception("Metrics: unknown or already fulfilled order: " + id);
        if (value < 0) throw new Exception("Metrics: negative value for order: " + id);

        long duration = System.currentTimeMillis() - start;
        long totalTime = getObsProperty(TOTAL_TIME).longValue() + duration;
        int totalValue = getObsProperty(TOTAL_VALUE).intValue() + value;
        int ordersDone = getObsProperty(ORDERS_DONE).intValue() + 1;

        updateObsProperty(TOTAL_TIME, totalTime);
        updateObsProperty(TOTAL_VALUE, totalValue);
        updateObsProperty(ORDERS_DONE, ordersDone);
        System.out.println("[Metrics] order " + id + " fulfilled in " + duration + "ms, value=" + value
                + " | ordersDone=" + ordersDone + " totalTime=" + totalTime + "ms totalValue=" + totalValue);
    }
}
