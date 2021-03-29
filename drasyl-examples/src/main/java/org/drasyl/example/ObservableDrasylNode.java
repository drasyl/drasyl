/*
 * Copyright (c) 2020-2021 Heiko Bornholdt and Kevin Röbert
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE
 * OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.drasyl.example;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.PublishSubject;
import io.reactivex.rxjava3.subjects.Subject;
import org.drasyl.DrasylConfig;
import org.drasyl.DrasylException;
import org.drasyl.DrasylNode;
import org.drasyl.annotation.NonNull;
import org.drasyl.event.Event;

/**
 * This particular Implementation of a drasyl Node shows exemplary how incoming Events can be
 * processed using the Observer Pattern.
 */
@SuppressWarnings({ "java:S106", "java:S107", "java:S2096", "unused", "ResultOfMethodCallIgnored" })
public class ObservableDrasylNode extends DrasylNode {
    private final Subject<Event> events;

    public ObservableDrasylNode() throws DrasylException {
        super();
        this.events = PublishSubject.create();
    }

    public ObservableDrasylNode(final DrasylConfig config) throws DrasylException {
        super(config);
        this.events = PublishSubject.<Event>create().toSerialized();
    }

    @Override
    public void onEvent(final @NonNull Event event) {
        events.onNext(event);
    }

    public Observable<Event> events() {
        return events;
    }

    public static void main(final String[] args) throws DrasylException {
        final ObservableDrasylNode node = new ObservableDrasylNode();
        node.events().subscribe(System.out::println, System.err::println);
        node.start().join();
    }
}
