package org.viridexa.api.plant;

public class PlantNotFoundException extends RuntimeException {
    public PlantNotFoundException(long id) {
        super("Verified public plant " + id + " was not found.");
    }
}
