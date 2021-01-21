/*
 * Copyright (c) 2021.
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
package org.drasyl;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.PublishSubject;
import io.reactivex.rxjava3.subjects.Subject;
import org.drasyl.event.Event;
import org.drasyl.util.scheduler.DrasylSchedulerUtil;

/**
 * This particular Implementation of a drasyl Node shows exemplary how incoming Events can be
 * processed using the Observer Pattern.
 */
@SuppressWarnings({ "java:S106", "java:S107", "unused", "ResultOfMethodCallIgnored" })
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
    public void onEvent(final Event event) {
        events.onNext(event);
    }

    public static void main(final String[] args) throws DrasylException {
        final ObservableDrasylNode node = new ObservableDrasylNode(DrasylConfig.newBuilder().localHostDiscoveryEnabled(false).remoteExposeEnabled(false).build());
        node.events().subscribeOn(DrasylSchedulerUtil.getInstanceLight()).subscribe(System.out::println, System.err::println);
        node.start().join();
    }

    public Observable<Event> events() {
        return events;
    }
}