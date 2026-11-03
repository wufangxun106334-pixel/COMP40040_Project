package artifacts;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import cartago.ARTIFACT_INFO;
import cartago.Artifact;
import cartago.LINK;
import cartago.OPERATION;
import cartago.OUTPORT;
import cartago.OperationException;
import model.Item;

// 订单生命周期（M1-2）：CLEARED --setOrder--> LOADED --give(最后一件)--> FULFILLED --deliver--> CLEARED
@ARTIFACT_INFO(outports = { @OUTPORT(name="out-1"), @OUTPORT(name="out-fp") })
public class PackagingPoint extends Artifact {
    public static final String NONE = "none";
    public static final String STATUS = "status";
    public static final String CLEARED = "cleared";
    public static final String LOADED = "loaded";
    public static final String FULFILLED = "fulfilled";

    private List<Item> items = new LinkedList<>();

    @OPERATION
    public void init() {
        // 属性只在此处定义一次，之后一律 updateObsProperty，从而允许反复换单
        defineObsProperty(STATUS, CLEARED);
        defineObsProperty("order", new ArrayList<String>());
        defineObsProperty("outstanding", new ArrayList<String>());
    }

    @OPERATION
    public void install() throws OperationException {
        execLinkedOp("out-fp", "install", getId(), new String[] {STATUS});
        execLinkedOp("out-fp", "setCustomField", getId().getName(), STATUS, CLEARED);
    }

    @OPERATION
    public void setOrder(List<String> order) throws Exception {
        if (!status().equals(CLEARED))
            throw new Exception("Packaging Point " + getId().getName() + ": order not cleared (status=" + status() + ")");
        updateObsProperty("order", new ArrayList<String>(order));
        updateObsProperty("outstanding", new ArrayList<String>(order));
        setStatus(order.isEmpty() ? FULFILLED : LOADED);
    }

    @OPERATION
    public void deliver() throws Exception {
        if (!status().equals(FULFILLED))
            throw new Exception("No item to deliver for: " + getId().getName() + " (status=" + status() + ")");
        System.out.println("Delivered: " + items);
        items.clear();
        updateObsProperty("order", new ArrayList<String>());
        updateObsProperty("outstanding", new ArrayList<String>());
        setStatus(CLEARED);
    }

    @SuppressWarnings("unchecked")
    @LINK
    public void give(Item item) throws Exception {
        if (!status().equals(LOADED))
            throw new Exception("Packaging Point " + getId().getName() + ": not accepting items (status=" + status() + ")");
        // 复制一份再更新，确保观察者收到变化通知
        List<String> outstanding = new ArrayList<String>((List<String>) getObsProperty("outstanding").getValue());
        if (outstanding.contains(item.type())) {
            items.add(item);
            outstanding.remove(item.type());
            updateObsProperty("outstanding", outstanding);
            if (outstanding.isEmpty()) setStatus(FULFILLED);
        } else
            throw new Exception("Item: " + item.type() + "  not required by: " + getId().getName());
    }

    private String status() {
        return getObsProperty(STATUS).stringValue();
    }

    private void setStatus(String status) throws OperationException {
        updateObsProperty(STATUS, status);
        execLinkedOp("out-fp", "setCustomField", getId().getName(), STATUS, status);
    }
}
