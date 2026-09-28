package artifacts;

import java.util.HashMap;
import java.util.Map;

import cartago.Artifact;
import cartago.ArtifactId;
import cartago.LINK;
import cartago.OPERATION;
import cartago.OpFeedbackParam;
import model.Location;
import ui.PlantViewer;
import ui.RobotPanel;
import ui.ZonePanel;

public class Floorplan extends Artifact {
    private Map<String, Location> locations = new HashMap<>();
    private PlantViewer viewer;
    private Map<String, ZonePanel> zones = new HashMap<>();
    private Map<String, RobotPanel> robots = new HashMap<>();

    @OPERATION
    public void init() {
        defineObsProperty("locations", locations.keySet());
        viewer = new PlantViewer();
    }

    @LINK
    public void install(cartago.ArtifactId id, String[] customFields) {
        locations.put(id.getName(), new Location(id));
        updateObsProperty("locations", locations.keySet());
        ZonePanel zonePanel = viewer.addZone(id.getName(), id.getArtifactType());
        for (String field : customFields) {
            zonePanel.addCustomField(field, "");
        }
        zones.put(id.getName(), zonePanel);
    }

    @LINK
    public void location(String name, OpFeedbackParam<ArtifactId> id) {
        id.set(locations.get(name).id());
    }

    @LINK
    public void setStatus(String name, String status) {
        zones.get(name).setStatus(status);
    }

    @LINK
    public void setRobotField(String name, String key, String value) {
        robots.get(name).setField(key, value);
    }

    @LINK
    public void setCustomField(String name, String custom, String value) {
        zones.get(name).setCustomField(custom, value);
    }

    @LINK
    public void move(String name, String from, String to) throws Exception {
        Location target = locations.get(to);
        Location source = locations.get(from);

        if (target.isOccupied()) throw new Exception("Target Location is Occupied: " + to);
        source.occupancy(null);
        zones.get(from).setAvailable("empty");
        Thread.sleep(2000);
        target.occupancy(name);
        zones.get(to).setAvailable(name);
        robots.get(name).setField("at", to);
    }

    @LINK
    public void place(String name, String to) throws Exception {
        Location target = locations.get(to);

        if (target.isOccupied()) throw new Exception("Target Location is Occupied: " + to);
        target.occupancy(name);
        zones.get(to).setAvailable(name);
        RobotPanel robotPanel = viewer.addRobot(name);
        robots.put(name, robotPanel);
        robotPanel.setField("at", to);

    }

    @OPERATION
    public void isOccupied(String name, OpFeedbackParam<Boolean> occupied) {
        Location location = locations.get(name);
        occupied.set(location.isOccupied());
    }
}
