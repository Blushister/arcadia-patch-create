[Etape1]
Test des commandes : 
    /arcadiapatchcreate status
        - Réponse : [net.minecraft.client.gui.components.ChatComponent/]: [CHAT] Arcadia Patch Create status | belt=false skips=0 | fluid=false skips=63982 failures=0 | factoryGauge=off interval=1 skips=0 forced=0 | mspt=0.00

    /arcadiapatchcreate belt status
        -Réponse : [net.minecraft.client.gui.components.ChatComponent/]: [CHAT] Belt patch | enabled=false skips=0

    /arcadiapatchcreate fluid status
        -Réponse : [net.minecraft.client.gui.components.ChatComponent/]: [CHAT] Fluid patch | enabled=false skips=63982 failures=0
    
    /arcadiapatchcreate factoryGauge status
        - Réponse : [net.minecraft.client.gui.components.ChatComponent/]: [CHAT] Factory Gauge throttle | mode=off interval=1 simulatedMspt=off currentMspt=0.00 skips=0 forced=0

    /arcadiapatchcreate belt enabled false
        -Réponse : [net.minecraft.client.gui.components.ChatComponent/]: [CHAT] Set belt patch enabled=false

    /arcadiapatchcreate belt enabled true
        -Réponse : [net.minecraft.client.gui.components.ChatComponent/]: [CHAT] Set belt patch enabled=true

    /arcadiapatchcreate factoryGauge mode off
        -Réponse : [net.minecraft.client.gui.components.ChatComponent/]: [CHAT] Set Factory Gauge throttle mode=off
    
    /arcadiapatchcreate factoryGauge mode static 2
        -Réponse : [net.minecraft.client.gui.components.ChatComponent/]: [CHAT] Set Factory Gauge throttle mode=static interval=2

    /arcadiapatchcreate factoryGauge mode adaptive
        -Réponse : [net.minecraft.client.gui.components.ChatComponent/]: [CHAT] Set Factory Gauge throttle mode=adaptive
    
    /arcadiapatchcreate factoryGauge simulateMspt 45
        -Réponse : [net.minecraft.client.gui.components.ChatComponent/]: [CHAT] Set simulated mspt=45.00

    /arcadiapatchcreate factoryGauge status
        -Réponse : [CHAT] Factory Gauge throttle | mode=adaptive interval=3 simulatedMspt=45.0 currentMspt=45.00 skips=0 forced=0

    /arcadiapatchcreate factoryGauge clearSimulatedMspt
        -Réponse : [CHAT] Cleared simulated mspt override.
    

3