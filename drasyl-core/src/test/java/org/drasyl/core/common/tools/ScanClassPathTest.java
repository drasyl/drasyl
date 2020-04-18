/*
 * Copyright (c) 2020
 *
 * This file is part of Relayserver.
 *
 * Relayserver is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Relayserver is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Relayserver.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.drasyl.core.common.tools;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Collection;

import org.junit.Assert;
import org.junit.jupiter.api.Test;

import org.drasyl.core.common.models.Pair;

class ScanClassPathTest {

    @Test
    @SuppressWarnings("rawtypes")
    void test() throws IOException, ClassNotFoundException {

        ScanClassPath scp = new ScanClassPath();

        scp.scanForAnnotationSystemPath(MyAnnotation.class, null, null);

        Collection<String> classes = scp.getClasses();

        Assert.assertThat(classes, contains(SomeTestClass.class.getName()));

        classes.forEach(s -> {
            Pair<Class, MyAnnotation> pair = scp.loadClassAndGetAnnotation(s, MyAnnotation.class);
            Assert.assertThat(pair.left(), is(SomeTestClass.class));
            Assert.assertThat(pair.right(), is(instanceOf(MyAnnotation.class)));
        });
    }

    @MyAnnotation
    public class SomeTestClass {

    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface MyAnnotation {

    }

}
