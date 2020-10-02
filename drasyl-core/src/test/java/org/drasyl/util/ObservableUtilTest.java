/*
 * Copyright (c) 2020.
 *
 * This file is part of drasyl.
 *
 *  drasyl is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  drasyl is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with drasyl.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.drasyl.util;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.drasyl.util.ObservableUtil.pairWithPreviousObservable;

@ExtendWith(MockitoExtension.class)
class ObservableUtilTest {
    @Nested
    class PairWithPreviousObservable {
        @Test
        void shouldPairCurrentAndPreviousItems() {
            final List<String> items = List.of("A", "B", "C", "D", "E");

            final Observable<Pair<String, String>> observable = pairWithPreviousObservable(Observable.fromIterable(items));
            final TestObserver<Pair<String, String>> observer = observable.test();

            observer.assertValues(
                    Pair.of("A", null),
                    Pair.of("B", "A"),
                    Pair.of("C", "B"),
                    Pair.of("D", "C"),
                    Pair.of("E", "D")
            );
            observer.assertComplete();
        }
    }
}