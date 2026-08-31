package fr.arcadia.arcadiapatchcreate.bridge;

public interface RedstoneLinkBridge {

    /** The signal this link last transmitted, used to detect that nothing changed. */
    int arcadiaPatchCreate$getTransmittedSignal();
}
