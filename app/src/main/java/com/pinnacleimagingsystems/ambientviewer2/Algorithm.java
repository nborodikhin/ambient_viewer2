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
        final float m11;
        final float m12;
        final float m13;
        final float m21;
        final float m22;
        final float m23;
        final float m31;
        final float m32;
        final float m33;

        public Matrix(float m11, float m12, float m13, float m21, float m22, float m23, float m31, float m32, float m33) {
            this.m11 = m11;
            this.m12 = m12;
            this.m13 = m13;
            this.m21 = m21;
            this.m22 = m22;
            this.m23 = m23;
            this.m31 = m31;
            this.m32 = m32;
            this.m33 = m33;
        }
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
