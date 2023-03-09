/*
 * Copyright (c) 2019 - Manifold Systems LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package manifold.science.util;

import manifold.science.measures.AccelerationUnit;
import manifold.science.measures.AngleUnit;
import manifold.science.measures.ChargeUnit;
import manifold.science.measures.CurrentUnit;
import manifold.science.measures.EnergyUnit;
import manifold.science.measures.ForceUnit;
import manifold.science.measures.FrequencyUnit;
import manifold.science.measures.HeatCapacityUnit;
import manifold.science.measures.LengthUnit;
import manifold.science.measures.MassUnit;
import manifold.science.measures.MomentumUnit;
import manifold.science.measures.PowerUnit;
import manifold.science.measures.TemperatureUnit;
import manifold.science.measures.TimeUnit;
import manifold.science.measures.VelocityUnit;
import manifold.science.measures.VolumeUnit;
import manifold.science.api.Dimension;

/**
 * A collection of commonly used SI units specified as standard abbreviations.
 * <p/>
 * Import constants of this class individually like this:
 * <pre><code>
 *   import static manifold.science.util.UnitConstants.m;
 * </code></pre>
 * Or import all of them like this:
 * <pre><code>
 *   import static manifold.science.util.UnitConstants.*;
 * </code></pre>
 * Then use them to conveniently express {@link Dimension} values like this:
 * <pre><code>
 *   Length distance = 90 mph * 25 min;
 * </code></pre>
 * If you don't see the unit you're looking for here, make your own! Units are just types -- you can simply declare a
 * constant of the unit type you want and use the constant as a unit qualifier:
 * <pre><code>
 *   ForceUnit lbf = slug ft/s/s;  // pound-force unit </code></pre>
 * Now you can make expressions using {@code lbf} force units!
 * <pre><code>
 *   Force force = 72 lbf;
 * </code></pre>
 * Note unlike floating point literals, these expressions retain the precision of literal decimal values.
 */
public interface UnitConstants {
    LengthUnit mum = LengthUnit.Micro;
    LengthUnit mm = LengthUnit.Milli;
    LengthUnit cm = LengthUnit.Centi;
    LengthUnit m = LengthUnit.Meter;
    LengthUnit km = LengthUnit.Kilometer;
    LengthUnit in = LengthUnit.Inch;
    LengthUnit ft = LengthUnit.Foot;
    LengthUnit yd = LengthUnit.Yard;
    LengthUnit mi = LengthUnit.Mile;

    TimeUnit ns = TimeUnit.Nano;
    TimeUnit mus = TimeUnit.Micro;
    TimeUnit ms = TimeUnit.Milli;
    TimeUnit s = TimeUnit.Second;
    TimeUnit min = TimeUnit.Minute;
    TimeUnit hr = TimeUnit.Hour;
    TimeUnit day = TimeUnit.Day;
    TimeUnit wk = TimeUnit.Week;
    TimeUnit mo = TimeUnit.Month;
    TimeUnit yr = TimeUnit.Year;
    TimeUnit tmo = TimeUnit.TrMonth;
    TimeUnit tyr = TimeUnit.TrYear;

    MassUnit amu = MassUnit.AtomicMass;
    MassUnit mug = MassUnit.Micro;
    MassUnit mg = MassUnit.Milli;
    MassUnit g = MassUnit.Gram;
    MassUnit kg = MassUnit.Kilogram;
    MassUnit ct = MassUnit.Carat;
    MassUnit dr = MassUnit.Dram;
    MassUnit gr = MassUnit.Grain;
    MassUnit Nt = MassUnit.Newton;
    MassUnit oz = MassUnit.Ounce;
    MassUnit ozt = MassUnit.TroyOunce;
    MassUnit lb = MassUnit.Pound;
    MassUnit slug = MassUnit.Slug;
    MassUnit st = MassUnit.Stone;
    MassUnit sht = MassUnit.Ton;
    MassUnit lt = MassUnit.TonUK;
    MassUnit tonne = MassUnit.Tonne;
    MassUnit Mo = MassUnit.Solar;

    VolumeUnit L = VolumeUnit.LITER;
    VolumeUnit mL = VolumeUnit.MILLI_LITER;
    VolumeUnit fl_oz = VolumeUnit.FLUID_OZ;
    VolumeUnit gal = VolumeUnit.GALLON;
    VolumeUnit qt = VolumeUnit.QUART;
    VolumeUnit pt = VolumeUnit.PINT;
    VolumeUnit cup = VolumeUnit.CUP;
    VolumeUnit tbsp = VolumeUnit.TABLE_SPOON;
    VolumeUnit tsp = VolumeUnit.TEA_SPOON;

    AngleUnit cyc = AngleUnit.Turn;
    AngleUnit rad = AngleUnit.Radian;
    AngleUnit mrad = AngleUnit.Milli;
    AngleUnit nrad = AngleUnit.Nano;
    AngleUnit arcsec = AngleUnit.ArcSecond;
    AngleUnit mas = AngleUnit.MilliArcSecond;
    AngleUnit grad = AngleUnit.Gradian;
    AngleUnit quad = AngleUnit.Quadrant;
    AngleUnit moa = AngleUnit.MOA;
    AngleUnit deg = AngleUnit.Degree;

    TemperatureUnit dK = TemperatureUnit.Kelvin;
    TemperatureUnit dC = TemperatureUnit.Celsius;
    TemperatureUnit dF = TemperatureUnit.Fahrenheit;

    VelocityUnit mph = mi / hr;

    AccelerationUnit ag = AccelerationUnit.GRAVITY;

    MomentumUnit Ns = kg
    m/s;

    ForceUnit N = kg
    m/s/s;
    ForceUnit dyn = g
    cm/s/s;
    ForceUnit lbf = slug
    ft/s/s;

    EnergyUnit joule = N
    m;
    EnergyUnit J = joule;
    EnergyUnit erg = dyn
    cm;
    EnergyUnit kcal = EnergyUnit.kcal;

    PowerUnit watt = J / s;
    PowerUnit W = watt;

    HeatCapacityUnit C = J / dK;

    FrequencyUnit Hz = cyc / s;
    FrequencyUnit kHz = cyc / ms;
    FrequencyUnit MHz = cyc / mus;
    FrequencyUnit GHz = cyc / ns;
    FrequencyUnit rpm = cyc / min;

    ChargeUnit coulomb = ChargeUnit.Coulomb;
    CurrentUnit amp = coulomb / s;
}

