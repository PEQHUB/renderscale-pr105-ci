package dev.zelo.renderscale.gametest;

// The Fabric Client Gametest API only exists for Minecraft 1.21.4+
//? fabric && >=1.21.4 {
//~ if >= 26.1 'getClientWorld' -> 'getClientLevel' {

import java.nio.file.Path;

import dev.zelo.renderscale.RenderScale;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

// scripts/run-gametests.sh
public class RenderScaleClientGameTest implements FabricClientGameTest {
    private static final float TEST_SCALE = 0.5f;

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            singleplayer.getClientLevel().waitForChunksRender();

            singleplayer.getServer().runCommand("fill -1 148 -1 1 148 1 minecraft:barrier");
            singleplayer.getServer().runCommand("tp @p 0.5 149.0 0.5 0 0");
            singleplayer.getServer().runCommand(
                    "summon minecraft:armor_stand 0.5 149.0 3.5 {Glowing:1b,NoGravity:1b,Rotation:[180f,0f]}");
            singleplayer.getServer().runCommand("time set noon");
            context.waitTicks(10);

            // GUI check
            context.runOnClient(client -> {
                client.options.guiScale().set(1);
                //? >= 26.1 {
                client.resizeGui();
                //?} else
                //client.resizeDisplay();
            });

            setRenderScale(context, 1.0f);
            // TODO: Can I just disable vignette...?
            context.waitTicks(60);
            Path nativeShot = context.takeScreenshot("renderscale_native");

            setRenderScale(context, TEST_SCALE);
            context.waitTicks(5);
            Path scaledShot = context.takeScreenshot("renderscale_scaled");

            ScreenshotVerifier.verifyScaling(nativeShot, scaledShot);
            verifyDynamicScale(context, nativeShot);
        }
    }

    private static void verifyDynamicScale(ClientGameTestContext context, Path nativeShot) {
        try {
            context.runOnClient(client -> {
                client.options.enableVsync().set(false);
                client.options.framerateLimit().set(30);
                RenderScale.getConfig().scale = 1.0f;
                RenderScale.getConfig().targetFrameRate = 1000;
                RenderScale.getConfig().aggressionLevel = dev.zelo.renderscale.config.RenderScaleConfig.Aggression.EXTREME;
                RenderScale.getConfig().minimumScale = 50;
                RenderScale.CONFIG.save();
            });
            context.waitTicks(20);
            context.runOnClient(client -> {
                if (RenderScale.getInstance().getRenderScaleFactor() != 1.0) {
                    throw new AssertionError("A stationary camera must hold its resolution");
                }
            });
            context.waitFor(client -> {
                client.player.setYRot(client.player.getYRot() > 0 ? -0.1f : 0.1f);
                return RenderScale.getInstance().getRenderScaleFactor() == 0.5;
            }, 1200);
            context.runOnClient(client -> client.player.setYRot(0));
            // Let first-person hand sway settle after the simulated camera turns.
            context.waitTicks(20);
            context.runOnClient(client -> {
                RenderScale renderer = RenderScale.getInstance();
                if (renderer.renderTarget.width != client.getWindow().getWidth() / 2
                        || renderer.renderTarget.height != client.getWindow().getHeight() / 2
                        || renderer.getCurrentScaleFactor() != 1.0) {
                    throw new AssertionError("Dynamic Scale must resize the world target and preserve native UI sizing");
                }
            });
            Path dynamicShot = context.takeScreenshot("renderscale_dynamic");
            ScreenshotVerifier.verifyScaling(nativeShot, dynamicShot);

            // Change the target without resetting the controller to exercise upward recovery.
            context.runOnClient(client -> RenderScale.getConfig().targetFrameRate = 1);
            context.waitTicks(20);
            context.runOnClient(client -> {
                if (RenderScale.getInstance().getRenderScaleFactor() != 0.5) {
                    throw new AssertionError("Recovery must also wait for camera movement");
                }
            });
            context.waitFor(client -> {
                client.player.setYRot(client.player.getYRot() > 0 ? -0.1f : 0.1f);
                return RenderScale.getInstance().getRenderScaleFactor() == 1.0;
            }, 1200);
            context.runOnClient(client -> client.player.setYRot(0));
            context.runOnClient(client -> {
                RenderScale renderer = RenderScale.getInstance();
                if (renderer.renderTarget.width != client.getWindow().getWidth()
                        || renderer.renderTarget.height != client.getWindow().getHeight()) {
                    throw new AssertionError("Recovery must restore the world render target size");
                }
            });
        } finally {
            context.runOnClient(client -> {
                RenderScale.getConfig().targetFrameRate = 0;
                RenderScale.getConfig().aggressionLevel = dev.zelo.renderscale.config.RenderScaleConfig.Aggression.NORMAL;
                RenderScale.CONFIG.save();
            });
        }
    }

    private static void setRenderScale(ClientGameTestContext context, float scale) {
        context.runOnClient(client -> {
            RenderScale.getConfig().scale = scale;
            RenderScale.getConfig().targetFrameRate = 0;
            // Saving fires the save listener, which resizes the render targets
            RenderScale.CONFIG.save();
        });
    }

}
//~}
//?}
