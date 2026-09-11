package dev.zelo.renderscale.mixin;

import dev.kikugie.fletching_table.annotation.MixinEnvironment;
import dev.zelo.renderscale.RenderScale;
//? 1.21.1
//import net.minecraft.client.Camera;
//? 1.21.1
//import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
//? 1.21.1
//import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
@MixinEnvironment(type = MixinEnvironment.Env.CLIENT)
public abstract class MixinGameRenderer {
    @Inject(method = "render", at = @At("HEAD"))
    private void renderScale$updateDynamicScale(CallbackInfo callbackInfo) {
        if (RenderScale.getInstance() != null) {
            RenderScale.getInstance().updateDynamicScale();
        }
    }

    //? <1.21.4 {
    /*@Inject(method = "render", at = @At("RETURN"))
    private void renderScale$autotestFrameRendered(CallbackInfo callbackInfo) {
        dev.zelo.renderscale.gametest.RenderScaleAutoTest.INSTANCE.frameRendered();
    }
    *///?}

    @Inject(method = "renderLevel", at = @At(value = "HEAD"))
    private void takeOver(CallbackInfo callbackInfo) {
        RenderScale.getInstance().setShouldScale(true);
    }

    //? 1.21.1 {
    /*/^*
     * neoforge... please...
     ^/
    @Inject(method = "renderItemInHand", at = @At("HEAD"))
    private void renderScale$restoreViewportBeforeHand(Camera camera, float tickDelta, Matrix4f matrix,
            CallbackInfo callbackInfo) {
        Minecraft.getInstance().getMainRenderTarget().bindWrite(true);
    }
    *///?}

    @Inject(method = "renderLevel", at = @At(value = "RETURN"))
    private void handBack(CallbackInfo callbackInfo) {
        RenderScale.getInstance().setShouldScale(false);
    }
}
