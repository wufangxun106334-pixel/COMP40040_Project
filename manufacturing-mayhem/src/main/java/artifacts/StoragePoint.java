package artifacts;

import cartago.ARTIFACT_INFO;
import cartago.Artifact;
import cartago.LINK;
import cartago.OPERATION;
import cartago.OUTPORT;
import cartago.OpFeedbackParam;
import cartago.OperationException;
import model.Item;

@ARTIFACT_INFO(outports = { @OUTPORT(name="out-fp") })
public class StoragePoint extends Artifact {
    public static final String NONE = "none";
    private Item item;

    @OPERATION
    public void init() {
        defineObsProperty("available", NONE);
    }

    @OPERATION
    public void install() throws OperationException {
        execLinkedOp("out-fp", "install", getId(), new String[] { "available" });
        execLinkedOp("out-fp", "setCustomField", getId().getName(), "available", NONE);
    }

    @LINK
    public void take(OpFeedbackParam<Item> item) throws Exception {
        if (this.item == null) throw new Exception("Storage Point " + getId().getName() + ": NO ITEM AVAILABLE");

        updateObsProperty("available", NONE);
        execLinkedOp("out-fp", "setCustomField", getId().getName(), "available", NONE);
        item.set(this.item);
        this.item = null;
    }

    @LINK
    public void give(Item item) throws Exception {
        if (this.item != null) throw new Exception("Storage Point " + getId().getName() + " is FULL: " + this.item.type());
        updateObsProperty("available", item.type());
        execLinkedOp("out-fp", "setCustomField", getId().getName(), "available", item.type());
        this.item = item;
    }
}