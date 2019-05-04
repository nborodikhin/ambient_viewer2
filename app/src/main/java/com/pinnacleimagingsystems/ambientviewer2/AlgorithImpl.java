package com.pinnacleimagingsystems.ambientviewer2;

import android.graphics.Color;

public class AlgorithImpl implements Algorithm {
    private static AlgorithmImplMeta meta = new AlgorithmImplMeta();

    static final int DEGAMMA_SIZE = 256;
    private static final int GAMMA_SIZE = 1024;

    private static float GAMMA_TO_LINEAR[];
    private static int LINEAR_TO_GAMMA[];
    private static double DELTA[];
    private float MOD_ADJUSTMENT[];
    private int adjustmentTable[][];

    private float parameter = 0;
    private double Root = Math.pow(2.0,0.5);

    private float linearize(int value) {
        return GAMMA_TO_LINEAR[value];
    }

    private float adjust(int value) { return MOD_ADJUSTMENT[value];}

    private int delinearize(float value) {
        value *= (GAMMA_SIZE + 1);
        value = Math.min(GAMMA_SIZE, value);
        value = Math.max(0, value);
        return LINEAR_TO_GAMMA[(int) value];
    }

    private float linearizeExact(float value) {
        if (value <= 0.04045f) {
            return value / 12.92f;
        } else {
            float a = 0.055f;
            return (float) Math.pow((value + a) / (1.0f + a), 2.4f);
        }
    }

    private float delinearizeExact(float value) {
        return (float) Math.pow(value, 1/2.2f);
    }

    @Override
    public void init(float parameter, ColorInfo colorInfo) {
        this.parameter = parameter;
        initCurves();
    }

    private void initCurves() {
        if (GAMMA_TO_LINEAR == null) {
            GAMMA_TO_LINEAR = new float[DEGAMMA_SIZE];

            for (int i = 0; i < DEGAMMA_SIZE; i++) {
                float gamma = ((float) i) / 255f;
                GAMMA_TO_LINEAR[i] = linearizeExact(gamma);
            }
        }

        if (LINEAR_TO_GAMMA == null) {
            LINEAR_TO_GAMMA = new int[GAMMA_SIZE + 1];

            for (int i = 0; i < GAMMA_SIZE; i++) {
                float linear = ((float) i) / (GAMMA_SIZE);
                float gamma = delinearizeExact(linear);
                int intGamma = (int) Math.floor(gamma * 255.0f);

                LINEAR_TO_GAMMA[i] = intGamma;
            }
            LINEAR_TO_GAMMA[GAMMA_SIZE] = 255;
        }
        if (DELTA==null){
            DELTA = new double [11];
            for (int i=1; i<=10; i++){
                DELTA[i]=Math.pow(Root, (i-1))*0.002;
            }
        }
        //        if (MOD_ADJUSTMENT == null){
        MOD_ADJUSTMENT=new float[DEGAMMA_SIZE];
        float Lux = (float)(Math.pow(2,((parameter-10)/2)))*2500;   //Dynamic Two-step
        Lux = Math.min(Lux,2500);
        Lux = Math.max(Lux, 80);
        float X1=Lux/10000f; float K1=(0.5f-X1)*8f; float Y1=K1*X1;
        for (int i=0; i<DEGAMMA_SIZE; i++) {
            float temp = linearize(i);
            if (temp<X1)
                MOD_ADJUSTMENT[i]=K1;
            else
                MOD_ADJUSTMENT[i]=(Y1+(temp-X1)*(1f-Y1)/(1f-X1))/temp;
        }
        /*if (parameter>0)
        {
        float K1 = 4.0f; float K2=3.0f; float K3=2.5f; float K4=2.0f; float K5=1.5f;
        float Delta=(float)DELTA[parameter];
        for (int i=0; i<DEGAMMA_SIZE;i++) {
            float temp = linearize(i);
            if (temp<=Delta)
                MOD_ADJUSTMENT[i]=K1;
            else
            {
                if (temp<=Delta*2f)
                    MOD_ADJUSTMENT[i]=(K2*temp+Delta)/temp;
                else
                {
                    if (temp<=Delta*3f)
                        MOD_ADJUSTMENT[i]=(K3*temp+Delta*2f)/temp;
                    else
                    {
                        if (temp<=Delta*4f)
                            MOD_ADJUSTMENT[i]=(K4*temp+Delta*3.5f)/temp;
                        else
                        {
                            if (temp<=Delta*5f)
                                MOD_ADJUSTMENT[i]=(K5*temp+Delta*5.5f)/temp;
                            else
                                MOD_ADJUSTMENT[i]= (13f*Delta + (temp - Delta*5f)*(1.0f-(7.5f*Delta)) / (1.0f - (5f*Delta)))/(temp);
                        }
                    }
                }
            }
        }
        }
        else
            Arrays.fill(MOD_ADJUSTMENT,1);
    */
    }


    @Override
    public void apply(int[] rgbaPixels, int width, int height) {
        int numPixels = width * height;
        adjustmentTable=new int [256][256]; // i is Major, j is Minor
        for (int i=0;i<256;i++) {
            for (int j=0;j<=i;j++) {
                adjustmentTable[i][j]=delinearize(adjust(i)*linearize(j));
            }
        }
        for (int i = 0; i < numPixels; i++) {
            int pixel = rgbaPixels[i];
            int r = Color.red(pixel);
            int g = Color.green(pixel);
            int b = Color.blue(pixel);
            int l = Math.max(r,Math.max(g,b));
            r=adjustmentTable[l][r];
            g=adjustmentTable[l][g];
            b=adjustmentTable[l][b];
            rgbaPixels[i] = Color.rgb(r,g,b);
        }
    }

/*    private int apply(int component) {
        float linear = linearize(component);

        float k = 1.f + parameter * 0.5f;
        float x = 0.01f;
        float y = k * x;

        if (linear < x) {
            linear = linear * y / x;
        } else {
            linear = y + (linear - x) / (1.0f - x) * (1.0f - y);
        }

        return delinearize(linear);
        */

    @Override
    public AlgorithmImplMeta getMeta() {
        return meta;
    }
}
