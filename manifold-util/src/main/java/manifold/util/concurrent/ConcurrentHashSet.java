/*
 * Copyright (c) 2018 - Manifold Systems LLC
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

package manifold.util.concurrent;

import java.util.Collection;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 *
 */
public class ConcurrentHashSet<K> implements Set<K> {
    private final ConcurrentMap<K, Boolean> _map;

    public ConcurrentHashSet() {
        _map = new ConcurrentHashMap<K, Boolean>();
    }

    public ConcurrentHashSet(int initialCapacity) {
        _map = new ConcurrentHashMap<K, Boolean>(initialCapacity);
    }

    public ConcurrentHashSet(Set<K> set) {
        _map = new ConcurrentHashMap<K, Boolean>(set.size());
        for (K value : set) {
            _map.put(value, Boolean.TRUE);
        }
    }

    public int size() {
        return _map.size();
    }

    public boolean isEmpty() {
        return _map.isEmpty();
    }

    public boolean contains(Object o) {
        return _map.containsKey(o);
    }

    public Iterator<K> iterator() {
        return _map.keySet().iterator();
    }

    public Object[] toArray() {
        return _map.keySet().toArray();
    }

    public <T> T[] toArray(T[] a) {
        return _map.keySet().toArray(a);
    }

    public boolean add(K o) {
        return _map.putIfAbsent(o, Boolean.TRUE) == null;
    }

    public boolean remove(Object o) {
        return _map.keySet().remove(o);
    }

    public boolean containsAll(Collection<?> c) {
        return _map.keySet().containsAll(c);
    }

    public boolean addAll(Collection<? extends K> c) {
        boolean ret = false;
        for (K value : c) {
            ret |= add(value);
        }

        return ret;
    }

    public boolean retainAll(Collection<?> c) {
        return _map.keySet().retainAll(c);
    }

    public boolean removeAll(Collection<?> c) {
        return _map.keySet().removeAll(c);
    }

    public void clear() {
        _map.clear();
    }

    @Override
    public String toString() {
        return _map.keySet().toString();
    }
}
