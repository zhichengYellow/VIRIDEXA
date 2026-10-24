package org.viridexa.api.plant;

public class PlantNotFoundException extends RuntimeException {
    public PlantNotFoundException(long id) {
        super("Plant " + id + " was not found.");
    }
}
