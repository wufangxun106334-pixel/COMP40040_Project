package artifacts;

import java.util.Arrays;
import java.util.List;

import cartago.ARTIFACT_INFO;
import cartago.Artifact;
import cartago.ArtifactId;
import cartago.OPERATION;
import cartago.OUTPORT;
import cartago.OpFeedbackParam;
import model.Item;


@ARTIFACT_INFO(outports = { @OUTPORT(name="out-fp")})
public class Robot extends Artifact {
    private static final List<String> LOAD_TYPES = Arrays.asList(new String[] {"artifacts.AccessPoint", "artifacts.StoragePoint", "artifacts.Workstation"});
    private static final List<String> UNLOAD_TYPES = Arrays.asList(new String[] {"artifacts.PackagingPoint", "artifacts.StoragePoint", "artifacts.Workstation"});
    private static final String AT = "at";
    private static final String ENERGY = "energy";
    private static final String MAX_ENERGY = "maxEnergy";
    private static final String HOLDING = "holding";
    private static final String NONE = "none";
    private static final String STATE = "state";
    private static final String IDLE = "idle";
    private static final String MOVING = "moving";
    private static final String LOADING = "loading";
    private static final String UNLOADING = "unloading";
    private static final String CHARGING = "charging";

    private static final int MOVE_COST = 100;
    private static final int LOAD_COST = 50;
    private static final int RECHARGE_AMOUNT = 300;

    private Item item;
    
    @OPERATION
    public void init(String location, int maxEnergy) {
        defineObsProperty(STATE, IDLE);
        defineObsProperty(HOLDING, NONE);
        defineObsProperty(AT, location);
        defineObsProperty(ENERGY, maxEnergy);
        defineObsProperty(MAX_ENERGY, maxEnergy);
    }

    @OPERATION
    public void move(String location) throws Exception {
        int energy = getObsProperty(ENERGY).intValue();
        if (energy < MOVE_COST) throw new Exception("Insufficient Energy to move: " + getId().getName());

        String at = getObsProperty(AT).stringValue();
        updateObsProperty(STATE, MOVING);
        execLinkedOp("out-fp", "setRobotField", getId().getName(), "status", MOVING);

        Thread.sleep(2000);
        execLinkedOp("out-fp", "move", getId().getName(), at, location);

        updateObsProperty(STATE, IDLE);
        updateObsProperty(AT, location);
        updateObsProperty(ENERGY, energy - MOVE_COST);
        execLinkedOp("out-fp", "setRobotField", getId().getName(), "status", IDLE);
        execLinkedOp("out-fp", "setRobotField", getId().getName(), "at", location);
        execLinkedOp("out-fp", "setRobotField", getId().getName(), "energy", getObsProperty(ENERGY).stringValue());
    }

    @OPERATION
    public void charge() throws Exception {
        cartago.ArtifactId deviceId = getDeviceArtifactId(getObsProperty(AT).stringValue());
        if (deviceId.getArtifactType().equals("artifacts.RobotChargePoint")) {
            execLinkedOp("out-fp", "setRobotField", getId().getName(), "status", CHARGING);
            Thread.sleep(1000);
            updateObsProperty(ENERGY, getObsProperty(ENERGY).intValue() + RECHARGE_AMOUNT);
            execLinkedOp("out-fp", "setRobotField", getId().getName(), "status", IDLE);
            execLinkedOp("out-fp", "setRobotField", getId().getName(), "energy", getObsProperty(ENERGY).stringValue());
        }
    }


    private ArtifactId getDeviceArtifactId(String at) throws Exception {
        OpFeedbackParam<ArtifactId> deviceId = new OpFeedbackParam<>();
        
        // Getting device at location...
        execLinkedOp("out-fp", "location", at, deviceId);

        // Checking Device Type (should be artifacts.AccessPoint)
        return deviceId.get();
    }

    @OPERATION
    public void load() throws Exception {
        int energy = getObsProperty(ENERGY).intValue();
        if (energy < MOVE_COST) throw new Exception("Insufficient Energy to load: " + getId().getName());

        String at = getObsProperty(AT).stringValue();
        ArtifactId did = getDeviceArtifactId(at);
        if (!LOAD_TYPES.contains(did.getArtifactType())) {
            throw new Exception("Invalid Location:  " + at + " device: " + did.getArtifactType());
        }

        // Executing Operation on device (hopefully)
        updateObsProperty(STATE, LOADING);
        execLinkedOp("out-fp", "setRobotField", getId().getName(), "status", LOADING);

        Thread.sleep(2000);
        OpFeedbackParam<Item> item = new OpFeedbackParam<>();
        execLinkedOp(did, "take", item);
        this.item = item.get();

        updateObsProperty(HOLDING, this.item.type());
        updateObsProperty(ENERGY, energy - LOAD_COST);
        updateObsProperty(STATE, IDLE);
        execLinkedOp("out-fp", "setRobotField", getId().getName(), "status", IDLE);
        execLinkedOp("out-fp", "setRobotField", getId().getName(), "holding", this.item.type());
        execLinkedOp("out-fp", "setRobotField", getId().getName(), "energy", getObsProperty(ENERGY).stringValue());
    }

    @OPERATION
    public void unload() throws Exception {
        int energy = getObsProperty(ENERGY).intValue();
        if (energy < MOVE_COST) throw new Exception("Insufficient Energy to unload: " + getId().getName());

        String at = getObsProperty(AT).stringValue();
        ArtifactId did = getDeviceArtifactId(at);
        if (!UNLOAD_TYPES.contains(did.getArtifactType())) {
            throw new Exception("Invalid Location:  " + at + " device: " + did.getArtifactType());
        }

        // Executing Operation on device (hopefully)
        updateObsProperty(STATE, UNLOADING);
        execLinkedOp("out-fp", "setRobotField", getId().getName(), "status", UNLOADING);

        Thread.sleep(2000);
        execLinkedOp(did, "give", item);
        updateObsProperty(HOLDING, NONE);
        updateObsProperty(ENERGY, energy - LOAD_COST);
        updateObsProperty(STATE, IDLE);
        execLinkedOp("out-fp", "setRobotField", getId().getName(), "status", IDLE);
        execLinkedOp("out-fp", "setRobotField", getId().getName(), "holding", NONE);
        execLinkedOp("out-fp", "setRobotField", getId().getName(), "energy", getObsProperty(ENERGY).stringValue());
    }
}