package dev.zelo.renderscale;

public final class DynamicScaleController {
    private static final double UPDATE_SECONDS = 0.5;
    private static final double MAX_RELATIVE_STEP = 0.25;
    private double scale = 1.0;
    private long previousFrame;
    private boolean hasPreviousFrame;
    private double averageFrameSeconds;
    private double elapsedSeconds;

    public double getScale() {
        return scale;
    }

    public void reset(double maximumScale) {
        scale = maximumScale;
        resetTiming();
    }

    // TODO: When resetting, start the dynamic scale at 1.0 instead of whatever the user has set
    public void resetTiming() {
        hasPreviousFrame = false;
        averageFrameSeconds = 0;
        elapsedSeconds = 0;
    }

    public boolean update(long now, int targetFps, int aggression, double minimumScale, double maximumScale, boolean cameraMoving) {
        double previousScale = scale;
        minimumScale = Math.min(minimumScale, maximumScale);
        scale = Math.max(minimumScale, Math.min(maximumScale, scale));
        if (targetFps <= 0) {
            reset(maximumScale);
            return scale != previousScale;
        }

        if (!hasPreviousFrame) {
            previousFrame = now;
            hasPreviousFrame = true;
            return scale != previousScale;
        }

        double frameSeconds = (now - previousFrame) / 1_000_000_000.0;
        previousFrame = now;
        // Ignore loading/GC BS.
        if (frameSeconds <= 0 || frameSeconds > 1.0) {
            resetTiming();
            return scale != previousScale;
        }

        double weight = 1.0 - Math.exp(-frameSeconds / 0.25);
        averageFrameSeconds = averageFrameSeconds == 0 ? frameSeconds
                : averageFrameSeconds + weight * (frameSeconds - averageFrameSeconds);

        // dont actually do anything while still because that's when its most noticable!!
        elapsedSeconds = Math.min(UPDATE_SECONDS, elapsedSeconds + frameSeconds);
        if (!cameraMoving) return scale != previousScale;
        if (elapsedSeconds < UPDATE_SECONDS) return scale != previousScale;

        double ratio = averageFrameSeconds * targetFps;
        double rate = 0.02 + 0.48 * Math.max(1, Math.min(100, aggression)) / 100.0;
        double adjustment = 0;

        // reduce scale oscillation
        if (ratio > 1.05) {
            adjustment = -rate * elapsedSeconds * Math.min(1.0, (ratio - 1.0) * 2.0);
        } else if (ratio <= 1.01) {
            adjustment = rate * elapsedSeconds * 0.5 * Math.max(0.1, Math.min(1.0, (1.0 - ratio) * 2.0));
        }
        if (adjustment != 0) {
            // weight each change by current PIXEL AREA since at low scales it becomes way more noticable
            double weightedScale = Math.cbrt(Math.max(0, scale * scale * scale + 3.0 * adjustment));
            double maximumStep = scale * MAX_RELATIVE_STEP;
            scale += Math.max(-maximumStep, Math.min(maximumStep, weightedScale - scale));
        }
        scale = Math.max(minimumScale, Math.min(maximumScale, scale));
        elapsedSeconds = 0;
        return scale != previousScale;
    }
}
