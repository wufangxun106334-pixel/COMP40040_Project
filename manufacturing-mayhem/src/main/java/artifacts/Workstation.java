package artifacts;

import java.util.ArrayList;
import java.util.List;

import cartago.ARTIFACT_INFO;
import cartago.Artifact;
import cartago.LINK;
import cartago.OPERATION;
import cartago.OUTPORT;
import cartago.OpFeedbackParam;
import cartago.OperationException;
import model.Item;

@ARTIFACT_INFO(outports = { @OUTPORT(name="out-fp") })
public class Workstation extends Artifact {
    private static final String IDLE = "idle";
    private static final String READY = "ready";
    private static final String ACTIVE = "active";
    private static final String DONE = "done";
    private static final String INPUTS_NEEDED = "inputsNeeded";
    private static final String INPUTS = "inputs";
    private static final String STATE = "state";

    private Item[] inputs;
    private Item output;

    @OPERATION
    public void init() {
        defineObsProperty(STATE, IDLE);
    }

    @OPERATION
    public void configure(List<String> inputTypes, String outputType, long duration) throws Exception {
        defineObsProperty("inputs", inputTypes);
        defineObsProperty("output", outputType);

        inputs = new Item[inputTypes.size()];

        defineObsProperty(INPUTS_NEEDED, new ArrayList<String>(inputTypes));
        defineObsProperty("duration", duration);

        execLinkedOp("out-fp", "setCustomField", getId().getName(), "inputs", getObsProperty("inputs").getValue().toString());
        //execLinkedOp("out-fp", "setCustomField", getId().getName(), "outputs", getObsProperty("outputs").getValue().toString());
        execLinkedOp("out-fp", "setCustomField", getId().getName(), "outputs", getObsProperty("output").getValue().toString());
        execLinkedOp("out-fp", "setCustomField", getId().getName(), "inputsNeeded", getObsProperty("inputs").getValue().toString());
    }

    @OPERATION
    public void install() throws OperationException {
        execLinkedOp("out-fp", "install", getId(), new String[] {"inputs", "inputsNeeded", "outputs", "available"});
    }
    
    @OPERATION
    public void operate() throws Exception {
        if (!getObsProperty(STATE).stringValue().equals(READY)) {
            throw new Exception("Workstation: " + getId().getName() + " is not ready for operation");
        }

        updateObsProperty(STATE, ACTIVE);
        execLinkedOp("out-fp", "setStatus", getId().getName(), ACTIVE);

        Thread.sleep(getObsProperty("duration").longValue());
        
        inputs = new Item[inputs.length];
        output = new Item(getObsProperty("output").stringValue());
        updateObsProperty(STATE, DONE);
        execLinkedOp("out-fp", "setStatus", getId().getName(), DONE);
    }
    
    @SuppressWarnings("unchecked")
    private List<String> createInputsNeededList() {
        List<String> inputs = (List<String>) getObsProperty(INPUTS).getValue();
        return new ArrayList<String>(inputs);
    }

    @SuppressWarnings("unchecked")
    @LINK
    public void give(Item item) throws Exception {
        int i = 0;
        List<String> types = (List<String>) getObsProperty(INPUTS).getValue();
        List<String> inputsNeeded = (List<String>) getObsProperty(INPUTS_NEEDED).getValue();
        while (i < inputs.length) {
            if (types.get(i).equals(item.type()) && inputs[i] == null) {
                inputs[i] = item;

                // Update the inputs needed list to reflect the addition of the new item...
                inputsNeeded.remove(item.type());
                updateObsProperty(INPUTS_NEEDED, inputsNeeded);
                execLinkedOp("out-fp", "setCustomField", getId().getName(), "inputsNeeded", inputsNeeded.toString());
                if (inputsNeeded.isEmpty()) {
                    updateObsProperty(STATE, READY);
                    execLinkedOp("out-fp", "setStatus", getId().getName(), READY);
                }
                return;
            }
            i++;
        }
        throw new Exception("Item not required: " + item.type());
    }

    @LINK
    public void take(OpFeedbackParam<Item> item) throws Exception {
        if (this.output == null) throw new Exception("Workstation " + getId().getName() + ": NO ITEM AVAILABLE");

        item.set(this.output);
        this.output = null;
        updateObsProperty(INPUTS_NEEDED, createInputsNeededList());
        updateObsProperty(STATE, IDLE);
        execLinkedOp("out-fp", "setStatus", getId().getName(), IDLE);
    }

}