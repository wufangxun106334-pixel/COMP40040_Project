package model;

import cartago.ArtifactId;

public class Location {
    private ArtifactId id;
    private String occupancy;

    public Location(ArtifactId id) {
        this.id = id;
    }

    public ArtifactId id() {
        return id;
    }

    public String occupancy() {
        return occupancy;
    }

    public void occupancy(String occupancy) {
        this.occupancy = occupancy;
    }

    public boolean isOccupied() {
        return occupancy != null;
    }
}
