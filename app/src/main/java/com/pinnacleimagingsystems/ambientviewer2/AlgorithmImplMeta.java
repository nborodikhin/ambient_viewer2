package com.pinnacleimagingsystems.ambientviewer2;

import static java.lang.Math.log;

public class AlgorithmImplMeta implements Algorithm.Meta {
    @Override
    public int parameterMin() {
        return 0;
    }

    @Override
    public int parameterMax() {
        return 5;
    }

    @Override
    public float defaultParameter(int lux) {
        lux = Math.min(2500, lux);
        lux = Math.max(80, lux);

        return (float) (log(lux / 2500.0f) / log(2) + 5);
    }
}
