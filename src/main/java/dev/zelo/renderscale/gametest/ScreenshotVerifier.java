package dev.zelo.renderscale.gametest;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import dev.zelo.renderscale.Constants;

// Helper file so I don't have to keep checking if there's a regression
public final class ScreenshotVerifier {
    private static final int TOLERANCE = 2;
    private static final double MAX_HAND_REGION_DIFFERENCE = 0.04;

    private ScreenshotVerifier() {
    }

    /** Filtered upscaling must preserve the scene and native-resolution hotbar. */
    public static void verifyFilteredScaling(Path nativePath, Path scaledPath) {
        BufferedImage nativeImage = readImage(nativePath);
        BufferedImage scaledImage = readImage(scaledPath);
        int w = nativeImage.getWidth();
        int h = nativeImage.getHeight();
        if (scaledImage.getWidth() != w || scaledImage.getHeight() != h || isMonochrome(scaledImage)) {
            throw new AssertionError("Filtered scaling produced a blank image or changed the output size");
        }
        double sceneDifference = meanChannelDifference(nativeImage, scaledImage, 0, 0, w, h - 22);
        double hotbarDifference = meanChannelDifference(nativeImage, scaledImage, w / 2 - 91, h - 22, w / 2 + 91, h);
        if (sceneDifference > 0.04 || hotbarDifference > 0.01) {
            throw new AssertionError("Filtered scaling changed the scene or native hotbar: scene="
                    + sceneDifference + ", hotbar=" + hotbarDifference);
        }
    }

    /** Throws {@link AssertionError} if scaling is broken or the UI got scaled too. */
    public static void verifyScaling(Path nativePath, Path scaledPath) {
        BufferedImage nativeImage = readImage(nativePath);
        BufferedImage scaledImage = readImage(scaledPath);

        int w = scaledImage.getWidth();
        int h = scaledImage.getHeight();

        if (nativeImage.getWidth() != w || nativeImage.getHeight() != h) {
            throw new AssertionError("Native and scaled screenshots have different dimensions: "
                    + nativeImage.getWidth() + "x" + nativeImage.getHeight() + " and " + w + "x" + h);
        }

        // armor stand
        int wx0 = w * 41 / 100, wx1 = w * 60 / 100;
        int wy0 = h * 52 / 100, wy1 = h * 92 / 100;
        double worldNative = uniformBlockFraction(nativeImage, wx0, wy0, wx1, wy1);
        double worldScaled = uniformBlockFraction(scaledImage, wx0, wy0, wx1, wy1);

        // hotbar
        double uiNative = uniformBlockFraction(nativeImage, w / 2 - 91, h - 22, w / 2 + 91, h);
        double uiScaled = uniformBlockFraction(scaledImage, w / 2 - 91, h - 22, w / 2 + 91, h);

        // the hand
        double handRegionDifference = meanChannelDifference(nativeImage, scaledImage,
                w * 55 / 100, h * 65 / 100, w, h);

        Constants.LOG.info("Gametest uniform 2x2 block fractions: world native={} scaled={}, "
                        + "hotbar native={} scaled={}; hand-region difference={}",
                worldNative, worldScaled, uiNative, uiScaled, handRegionDifference);

        if (isMonochrome(scaledImage)) {
            throw new AssertionError("Scaled screenshot is a single colour - nothing was rendered");
        }

        if (worldScaled < 0.97) {
            throw new AssertionError("World in the scaled screenshot is not blocky enough to be a 0.5x nearest upscale"
                    + " (uniform 2x2 fraction " + worldScaled + ", expected >= 0.97)");
        }

        if (worldNative > 0.9) {
            throw new AssertionError("Armor stand region of the native screenshot has no fine detail (uniform fraction "
                    + worldNative + ") - is the scene missing, or was the native screenshot also scaled?");
        }

        if (uiNative > 0.9) {
            throw new AssertionError("Hotbar region of the native screenshot has no fine detail (uniform fraction "
                    + uiNative + ") - is the GUI hidden or the hotbar missing?");
        }

        if (uiScaled > 0.9) {
            throw new AssertionError("Hotbar region of the scaled screenshot is blocky (uniform fraction " + uiScaled
                    + ") - RenderScale scaled the UI, but the UI must stay at native resolution");
        }

        if (handRegionDifference > MAX_HAND_REGION_DIFFERENCE) {
            throw new AssertionError("Lower-right hand region differs too much between native and scaled screenshots "
                    + "(mean channel difference " + handRegionDifference + ", expected <= "
                    + MAX_HAND_REGION_DIFFERENCE + ") - is the first-person hand missing from one screenshot?");
        }
    }

    private static BufferedImage readImage(Path path) {
        try {
            BufferedImage image = ImageIO.read(path.toFile());
            if (image == null) throw new AssertionError("Screenshot is not a readable image: " + path);
            return image;
        } catch (IOException e) {
            throw new AssertionError("Failed to read screenshot " + path, e);
        }
    }

    /**
     * Fraction of 2x2 pixel blocks inside the given region whose four pixels match
     * within {@link #TOLERANCE} per channel. Blocks are aligned to even framebuffer
     * coordinates to match a 0.5x nearest-neighbour upscale.
     */
    private static double uniformBlockFraction(BufferedImage image, int x0, int y0, int x1, int y1) {
        x0 = Math.max(0, x0 + (x0 & 1));
        y0 = Math.max(0, y0 + (y0 & 1));
        x1 = Math.min(image.getWidth(), x1);
        y1 = Math.min(image.getHeight(), y1);

        int blocks = 0;
        int uniform = 0;

        for (int y = y0; y + 1 < y1; y += 2) {
            for (int x = x0; x + 1 < x1; x += 2) {
                blocks++;

                if (blockUniform(image.getRGB(x, y), image.getRGB(x + 1, y),
                        image.getRGB(x, y + 1), image.getRGB(x + 1, y + 1))) {
                    uniform++;
                }
            }
        }

        return blocks == 0 ? 0.0 : (double) uniform / blocks;
    }

    private static boolean blockUniform(int a, int b, int c, int d) {
        for (int shift = 0; shift <= 16; shift += 8) {
            int ca = (a >> shift) & 0xFF;
            int cb = (b >> shift) & 0xFF;
            int cc = (c >> shift) & 0xFF;
            int cd = (d >> shift) & 0xFF;

            int min = Math.min(Math.min(ca, cb), Math.min(cc, cd));
            int max = Math.max(Math.max(ca, cb), Math.max(cc, cd));
            if (max - min > TOLERANCE) return false;
        }

        return true;
    }

    /** Mean absolute RGB channel difference, normalized to the range 0..1. */
    private static double meanChannelDifference(BufferedImage a, BufferedImage b,
            int x0, int y0, int x1, int y1) {
        long difference = 0;
        long channels = 0;

        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                int ca = a.getRGB(x, y);
                int cb = b.getRGB(x, y);

                for (int shift = 0; shift <= 16; shift += 8) {
                    difference += Math.abs(((ca >> shift) & 0xFF) - ((cb >> shift) & 0xFF));
                    channels++;
                }
            }
        }

        return channels == 0 ? 0.0 : (double) difference / (channels * 255.0);
    }

    private static boolean isMonochrome(BufferedImage image) {
        int first = image.getRGB(0, 0);

        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (image.getRGB(x, y) != first) return false;
            }
        }

        return true;
    }
}
