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

import manifold.science.api.AbstractMeasure;
import manifold.science.util.Rational;

public final class HeatCapacity extends AbstractMeasure<HeatCapacityUnit, HeatCapacity> {
    public HeatCapacity(Rational value, HeatCapacityUnit unit, HeatCapacityUnit displayUnit) {
        super(value, unit, displayUnit);
    }

    public HeatCapacity(Rational value, HeatCapacityUnit unit) {
        this(value, unit, unit);
    }

    @Override
    public HeatCapacityUnit getBaseUnit() {
        return HeatCapacityUnit.BASE;
    }

    @Override
    public HeatCapacity make(Rational value, HeatCapacityUnit unit, HeatCapacityUnit displayUnit) {
        return new HeatCapacity(value, unit, displayUnit);
    }

    @Override
    public HeatCapacity make(Rational value, HeatCapacityUnit unit) {
        return new HeatCapacity(value, unit);
    }

    public Energy times(Temperature temperature) {
        return new Energy(toBaseNumber() * temperature.toBaseNumber(), EnergyUnit.BASE, getDisplayUnit() * temperature.getDisplayUnit());
    }
}