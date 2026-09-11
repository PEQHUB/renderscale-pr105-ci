package dev.zelo.renderscale;

/** Run with scripts/test-dynamic-scale.sh; no Minecraft client or test libraries needed. */
public final class DynamicScaleControllerTest {
    public static void main(String[] args) {
        DynamicScaleController controller = new DynamicScaleController();
        simulate(controller, 30, 60, 50, 0.5, 1.0, 10);
        check(controller.getScale() == 0.5, "Sustained low FPS must reach, but never cross, the minimum");
        simulate(controller, 120, 60, 50, 0.5, 1.0, 10);
        check(controller.getScale() == 1.0, "Spare frame time must restore the maximum");

        controller.reset(0.5);
        simulate(controller, 60, 60, 50, 0.5, 1.0, 5);
        check(controller.getScale() > 0.5, "A frame cap at the target must still allow recovery");
        controller.reset(0.75);
        simulate(controller, 58, 60, 50, 0.5, 1.0, 5);
        check(controller.getScale() == 0.75, "Small timing fluctuations should not change resolution");

        DynamicScaleController slow = new DynamicScaleController();
        DynamicScaleController fast = new DynamicScaleController();
        simulate(slow, 30, 60, 1, 0.1, 1.0, 1);
        simulate(fast, 30, 60, 100, 0.1, 1.0, 1);
        check(fast.getScale() < slow.getScale(), "Higher aggression must reduce resolution faster");

        DynamicScaleController lowerFps = new DynamicScaleController();
        DynamicScaleController higherFps = new DynamicScaleController();
        // Exact frame intervals keep both simulations on the same adjustment boundary.
        simulate(lowerFps, 32, 64, 50, 0.1, 1.0, 2);
        simulate(higherFps, 64, 128, 50, 0.1, 1.0, 2);
        check(Math.abs(lowerFps.getScale() - higherFps.getScale()) < 0.0001,
                "Adjustment speed depends on elapsed time, not frame count");

        controller.reset(1.0);
        controller.update(0, 60, 100, 0.5, 1.0, true);
        check(!controller.update(100_000_000, 60, 100, 0.5, 1.0, true), "Do not resize every frame");
        check(!controller.update(2_000_000_000L, 60, 100, 0.5, 1.0, true), "Ignore isolated long stalls");
        check(controller.getScale() == 1.0, "A stall must not lower resolution");
        controller.resetTiming();
        check(!controller.update(10_000_000_000L, 60, 100, 0.5, 1.0, true), "Resume without sampling the pause");

        controller.reset(0.5);
        check(controller.update(0, 0, 50, 0.5, 1.5, true), "Disabling restores the configured scale");
        check(controller.getScale() == 1.5, "Disabled mode uses fixed scale");
        controller.update(0, 60, 50, 0.8, 0.4, true);
        check(controller.getScale() == 0.4, "A minimum above the maximum must respect the maximum");

        controller.reset(1.5);
        simulate(controller, 20, 60, 100, 0.5, 1.5, 5);
        check(controller.getScale() == 0.5, "Dynamic scaling supports supersampling as its upper limit");
        controller.reset(1.0);
        for (int frame = 0; frame < 600; frame++) {
            controller.update(frame * 33_333_333L, 60, 100, 0.5, 1.0, false);
        }
        check(controller.getScale() == 1.0, "A stationary camera must keep the resolution fixed");
        controller.update(600 * 33_333_333L, 60, 100, 0.5, 1.0, true);
        check(controller.getScale() < 1.0 && controller.getScale() >= 0.75,
                "Camera movement applies a bounded adjustment, without accumulating idle time");
        double heldScale = controller.getScale();
        for (int frame = 601; frame < 1200; frame++) {
            controller.update(frame * 33_333_333L, 1, 100, 0.5, 1.0, false);
        }
        check(controller.getScale() == heldScale, "Upward recovery also waits for camera movement");
        verifyPerceptualWeighting();
        System.out.println("Dynamic Scale controller tests passed");
    }

    private static void verifyPerceptualWeighting() {
        DynamicScaleController high = new DynamicScaleController();
        DynamicScaleController low = new DynamicScaleController();
        high.reset(2.0);
        low.reset(0.5);
        simulate(high, 32, 64, 50, 1.0, 2.0, 1);
        simulate(low, 32, 64, 50, 0.25, 0.5, 1);
        check(2.0 - high.getScale() < 0.5 - low.getScale(),
                "Higher scales must lose less linear resolution for the same FPS pressure");

        int highFrames = framesToScale(2.0, 1.0, 64);
        int lowFrames = framesToScale(0.5, 0.25, 64);
        check(highFrames > lowFrames * 4,
                "2x to 1x must receive extra smoothing beyond the old fourfold linear duration");

        high.reset(1.5);
        low.reset(0.375);
        simulate(high, 32, 16, 50, 1.0, 2.0, 1);
        simulate(low, 32, 16, 50, 0.25, 0.5, 1);
        check(high.getScale() - 1.5 < low.getScale() - 0.375,
                "Recovery must also make smaller adjustments at higher scales");

        for (int target : new int[] {16, 64}) {
            DynamicScaleController tiny = new DynamicScaleController();
            tiny.reset(0.04);
            for (int frame = 0; frame < 320; frame++) {
                double previous = tiny.getScale();
                tiny.update(frame * 31_250_000L, target, 100, 0.01, 0.1, true);
                check(Math.abs(tiny.getScale() - previous) <= previous * 0.25 + 1e-12,
                        "Weighting must never jump more than 25% at low scales");
                check(tiny.getScale() >= 0.01 && tiny.getScale() <= 0.1,
                        "Weighted adjustments must respect the configured bounds");
            }
        }
    }

    private static int framesToScale(double start, double end, int target) {
        DynamicScaleController controller = new DynamicScaleController();
        controller.reset(start);
        for (int frame = 0; frame < 3200; frame++) {
            controller.update(frame * 31_250_000L, target, 50, end, start, true);
            if (controller.getScale() == end) return frame;
        }
        throw new AssertionError("Weighted scaling did not reach its minimum");
    }

    private static void simulate(DynamicScaleController controller, int fps, int target, int aggression,
            double minimum, double maximum, int seconds) {
        controller.resetTiming();
        long frameNanos = Math.round(1_000_000_000.0 / fps);
        for (int frame = 0; frame <= fps * seconds; frame++) {
            controller.update(frame * frameNanos, target, aggression, minimum, maximum, true);
            check(controller.getScale() >= minimum && controller.getScale() <= maximum,
                    "Every adjustment must stay within bounds");
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
