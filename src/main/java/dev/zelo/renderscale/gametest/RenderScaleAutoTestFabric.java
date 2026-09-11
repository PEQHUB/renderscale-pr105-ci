package dev.zelo.renderscale.gametest;

//? fabric && <1.21.4 {

/*import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public class RenderScaleAutoTestFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        if (!RenderScaleAutoTest.ENABLED) return;

        ClientTickEvents.END_CLIENT_TICK.register(RenderScaleAutoTest.INSTANCE::tick);
    }
}
*///?}
