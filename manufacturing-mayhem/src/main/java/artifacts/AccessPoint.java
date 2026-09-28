package artifacts;

import cartago.ARTIFACT_INFO;
import cartago.Artifact;
import cartago.LINK;
import cartago.OPERATION;
import cartago.OUTPORT;
import cartago.OpFeedbackParam;
import cartago.OperationException;
import model.Item;

@ARTIFACT_INFO(outports = { @OUTPORT(name="out-1"), @OUTPORT(name="out-fp") })
public class AccessPoint extends Artifact {
    public static final String NONE = "none";
    private Item item;

    @OPERATION
    public void init() {
        defineObsProperty("available", NONE);
    }

    @OPERATION
    public void install() throws OperationException {
        execLinkedOp("out-fp", "install", getId(), new String[] {"available"});
        execLinkedOp("out-fp", "setCustomField", getId().getName(), "available", NONE);
    }

    @OPERATION
    public void retrieve(String type) throws OperationException {
        OpFeedbackParam<Item> item = new OpFeedbackParam<>();
        execLinkedOp("out-fp", "setStatus", getId().getName(), "retrieving");
        execLinkedOp("out-1", "retrieve", type, item);
        updateObsProperty("available", type);
        execLinkedOp("out-fp", "setStatus", getId().getName(), "idle");
        execLinkedOp("out-fp", "setCustomField", getId().getName(), "available", type);
        this.item = item.get();
    }

    @LINK
    public void take(OpFeedbackParam<Item> item) throws Exception {
        if (this.item == null) throw new Exception("Access Point " + getId().getName() + ": NO ITEM AVAILABLE");

        updateObsProperty("available", NONE);
        execLinkedOp("out-fp", "setCustomField", getId().getName(), "available", NONE);
        item.set(this.item);
        this.item = null;
    }
}