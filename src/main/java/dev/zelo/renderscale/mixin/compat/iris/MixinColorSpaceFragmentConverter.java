// TODO: Figure out if the lag spikes on dynamic scale happen in other versions
// Doesn't seem to happen on 26.3
//? iris && 1.21.1 {
/*package dev.zelo.renderscale.mixin.compat.iris;

import dev.kikugie.fletching_table.annotation.MixinEnvironment;
import net.irisshaders.iris.gl.IrisRenderSystem;
import net.irisshaders.iris.gl.program.Program;
import net.irisshaders.iris.pathways.colorspace.ColorSpace;
import org.lwjgl.opengl.GL11C;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.irisshaders.iris.pathways.colorspace.ColorSpaceFragmentConverter", remap = false)
@MixinEnvironment(type = MixinEnvironment.Env.CLIENT)
public abstract class MixinColorSpaceFragmentConverter {
    @Shadow private int width;
    @Shadow private int height;
    @Shadow private ColorSpace colorSpace;
    @Shadow private Program program;
    @Shadow private int swapTexture;

    @Inject(method = "rebuildProgram", at = @At("HEAD"), cancellable = true)
    private void renderScale$resizeWithoutRecompiling(int width, int height, ColorSpace colorSpace, CallbackInfo ci) {
        // Iris's conversion shader depends on the color space, but not the resolution.
        // Keep its program and framebuffer when only the output dimensions change.
        if (program == null || this.colorSpace != colorSpace) return;

        if (this.width != width || this.height != height) {
            IrisRenderSystem.texImage2D(swapTexture, GL11C.GL_TEXTURE_2D, 0, GL11C.GL_RGBA8,
                    width, height, 0, GL11C.GL_RGBA, GL11C.GL_UNSIGNED_BYTE, null);
            this.width = width;
            this.height = height;
        }
        ci.cancel();
    }
}
*///?}
