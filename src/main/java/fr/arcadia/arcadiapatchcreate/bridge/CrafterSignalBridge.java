package fr.arcadia.arcadiapatchcreate.bridge;

public interface CrafterSignalBridge {

    /** Forces the next tick to read the redstone signal from the world again. */
    void arcadiaPatchCreate$invalidateSignal();
}
