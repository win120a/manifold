/*
 * Copyright (c) 2020 - Manifold Systems LLC
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

package manifold.rt.api.util;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * A base class to simplify implementing iterators so that implementations only have to implement [computeNext]
 * to implement the iterator, calling [done] when the iteration is complete.
 */
public abstract class AbstractIterator<T> implements Iterator<T> {
    private State _state = State.NotReady;
    private T _nextValue;

    /**
     * Computes the next item in the iterator.
     * <p>
     * This callback method should call one of these two methods:
     * <p>
     * * [setNext] with the next value of the iteration
     * * [done] to indicate there are no more elements
     * <p>
     * Failure to call either method will result in the iteration terminating with a failed state
     */
    abstract protected void computeNext();

    @Override
    public boolean hasNext() {
        switch (_state) {
            case Done:
                return false;
            case Ready:
                return true;
            case Failed:
                throw new IllegalStateException();
            default:
                return tryToComputeNext();
        }
    }

    public T next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        _state = State.NotReady;
        return _nextValue;
    }

    private boolean tryToComputeNext() {
        _state = State.Failed;
        computeNext();
        return _state == State.Ready;
    }

    /**
     * Sets the next value in the iteration, called from the [computeNext] function
     */
    protected void setNext(T value) {
        _nextValue = value;
        _state = State.Ready;
    }

    /**
     * Sets the state to done so that the iteration terminates.
     */
    protected void done() {
        _state = State.Done;
    }

    enum State {
        Ready,
        NotReady,
        Done,
        Failed
    }

}