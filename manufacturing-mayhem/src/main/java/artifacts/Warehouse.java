package artifacts;

import cartago.Artifact;
import cartago.OPERATION;
import cartago.ObsProperty;
import cartago.OpFeedbackParam;
import model.Item;

public class Warehouse extends Artifact {
    @OPERATION
    public void init() {
    }

    @OPERATION
    public void add(String item, int qty) {
        ObsProperty property = getObsProperty(item);
        if (property == null)
            defineObsProperty(item, qty);
        else
            updateObsProperty(item, property.intValue()+qty);    
    }

    @OPERATION
    public void retrieve(String type, OpFeedbackParam<Item> item) throws Exception {
        int qty = getObsProperty(type).intValue();
        if (qty == 0)
            throw new Exception("Warehouse contains no items of type: " + type);
        updateObsProperty(type, qty-1);
        item.set(new Item(type));
    }
    
}