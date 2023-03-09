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

package manifold.science.measures;

import manifold.science.api.AbstractQuotientUnit;
import manifold.science.api.UnitCache;
import manifold.science.util.Rational;


import static manifold.science.measures.MetricScaleUnit.*;
import static manifold.science.util.CoercionConstants.r;
import static manifold.science.measures.TimeUnit.Second;

public final class PowerUnit extends AbstractQuotientUnit<EnergyUnit, TimeUnit, Power, PowerUnit> {
    private static final UnitCache<PowerUnit> CACHE = new UnitCache<>();

    public static PowerUnit pW = get(EnergyUnit.BASE, Second, 1p, "Picowatt", "pW");
    public static PowerUnit nW = get(EnergyUnit.BASE, Second, 1n, "Nanowatt", "nW");
    public static PowerUnit uW = get(EnergyUnit.BASE, Second, 1u, "Microwatt", "μW");
    public static PowerUnit mW = get(EnergyUnit.BASE, Second, 1m, "Milliwatt", "mW");
    public static PowerUnit W = get(EnergyUnit.BASE, Second, 1r, "Watt", "W");
    public static PowerUnit kW = get(EnergyUnit.BASE, Second, 1k, "Kilowatt", "kW");
    public static PowerUnit MW = get(EnergyUnit.BASE, Second, 1M, "Megawatt", "MW");
    public static PowerUnit GW = get(EnergyUnit.BASE, Second, 1G, "Gigawatt", "GW");

    public static final PowerUnit BASE = W;

    static public PowerUnit get(EnergyUnit energyUnit, TimeUnit timeUnit) {
        return get(energyUnit, timeUnit, null, null, null);
    }

    static public PowerUnit get(EnergyUnit energyUnit, TimeUnit timeUnit, Rational factor, String name, String symbol) {
        PowerUnit unit = new PowerUnit(energyUnit, timeUnit, factor, name, symbol);
        return CACHE.get(unit);
    }

    private PowerUnit(EnergyUnit energyUnit, TimeUnit timeUnit, Rational factor, String name, String symbol) {
        super(energyUnit, timeUnit, factor, name, symbol);
    }

    @Override
    public Power makeDimension(Number amount) {
        return new Power(Rational.get(amount), this);
    }

    public EnergyUnit getEnergyUnit() {
        return getLeftUnit();
    }

    public TimeUnit getTimeUnit() {
        return getRightUnit();
    }

    public ForceUnit div(VelocityUnit v) {
        return getEnergyUnit().getForceUnit();
    }

    public VelocityUnit div(ForceUnit force) {
        return getEnergyUnit().getForceUnit().getAccUnit().getVelocityUnit();
    }

    public CurrentUnit div(PotentialUnit potential) {
        return potential.getCurrentUnit();
    }

    public PotentialUnit div(CurrentUnit current) {
        return PotentialUnit.get(this, current);
    }
}
