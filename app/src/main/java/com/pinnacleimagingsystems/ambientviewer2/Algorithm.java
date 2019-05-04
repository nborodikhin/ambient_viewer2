package com.pinnacleimagingsystems.ambientviewer2;

public interface Algorithm {
    class Gains {
        public final float r;
        public final float g;
        public final float b;

        public Gains(float r, float g, float b) {
            this.r = r;
            this.g = g;
            this.b = b;
        }
    }

    class Matrix {

    }

    class ColorInfo {
        public final Gains gains;
        public final Matrix matrix;


        public ColorInfo(Gains gains, Matrix matrix) {
            this.gains = gains;
            this.matrix = matrix;
        }
    }

    interface Meta {
        int parameterMin();
        int parameterMax();
        float defaultParameter(int lux);
    }

    Meta getMeta();

    void init(float parameter, ColorInfo colorInfo);
    void apply(int[] rgbaPixels, int width, int height);
}
