package artifacts;

import cartago.ARTIFACT_INFO;
import cartago.Artifact;
import cartago.ArtifactConfig;
import cartago.OPERATION;
import cartago.OUTPORT;
import cartago.OperationException;

@ARTIFACT_INFO(outports = { @OUTPORT(name="out-fp") })
public class RobotChargePoint extends Artifact {
    public static final String NONE = "none";
    private int maxEnergy;

    @OPERATION
    public void init(int maxEnergy) {
        this.maxEnergy = maxEnergy;
        defineObsProperty("available", NONE);
    }

    @OPERATION
    public void install(String name) throws OperationException{
        execLinkedOp("out-fp", "install", getId(), new String[] {});
        
        try {
            makeArtifact(name, "artifacts.Robot", new ArtifactConfig(getId().getName(), maxEnergy));
            execLinkedOp("out-fp", "place", name, getId().getName());
        } catch (OperationException e) {
            e.printStackTrace();
        }
    }
}