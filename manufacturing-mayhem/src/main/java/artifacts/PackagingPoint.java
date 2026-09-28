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

@ARTIFACT_INFO(outports = { @OUTPORT(name="out-1"), @OUTPORT(name="out-fp") })
public class PackagingPoint extends Artifact {
    public static final String NONE = "none";
    private List<Item> items = new LinkedList<>();

    @OPERATION
    public void init() {
        // removeObsProperty("order");
    }

    @OPERATION
    public void install() throws OperationException {
        execLinkedOp("out-fp", "install", getId(), new String[] {});
    }

    @OPERATION
    public void setOrder(List<String> order) {
        defineObsProperty("order", order);
        defineObsProperty("outstanding", new ArrayList<String>(order));
    }

    @SuppressWarnings("unchecked")
    @OPERATION
    public void deliver() throws Exception {
        List<String> outstanding = (List<String>) getObsProperty("outstanding").getValue();
        if (outstanding.isEmpty()) {
            System.out.println("Delivered: " + items);
        } else
            throw new Exception("No item to deliver for: " + getId().getName());
    }

    @SuppressWarnings("unchecked")
    @LINK
    public void give(Item item) throws Exception {
        List<String> outstanding = (List<String>) getObsProperty("outstanding").getValue();
        if (outstanding.contains(item.type())) {
            items.add(item);
            outstanding.remove(item.type());
            updateObsProperty("outstanding", outstanding);
        } else 
            throw new Exception("Item: " + item.type() + "  not required by: " + getId().getName());
    }}