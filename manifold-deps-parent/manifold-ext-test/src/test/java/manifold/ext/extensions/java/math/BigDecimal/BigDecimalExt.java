package manifold.ext.extensions.java.math.BigDecimal;

import manifold.ext.rt.api.Extension;
import manifold.ext.rt.api.This;
import manifold.ext.structural.SqlNumber;

import java.math.BigDecimal;

@Extension
public abstract class BigDecimalExt implements SqlNumber {
    public static BigDecimal getNumber(@This BigDecimal thiz) {
        return thiz;
    }

    public static double something(@This BigDecimal thiz, double dp, int d) {
        return thiz.add(new BigDecimal(d + dp)).doubleValue();
    }
}