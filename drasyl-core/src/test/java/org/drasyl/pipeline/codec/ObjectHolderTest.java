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
package org.drasyl.pipeline.codec;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@ExtendWith(MockitoExtension.class)
class ObjectHolderTest {
    @Test
    void shouldReturnCorrectValues() throws ClassNotFoundException {
        byte[] obj = new byte[]{};
        Class<Integer> clazz = Integer.class;

        ObjectHolder objectHolder = ObjectHolder.of(clazz, obj);

        assertEquals(obj, objectHolder.getObject());
        assertEquals(clazz, objectHolder.getClazz());
        assertEquals(clazz.getName(), objectHolder.getClazzAsString());

        // Ignore toString
        objectHolder.toString();
    }

    @Test
    void equalsTest() {
        byte[] obj = new byte[]{};
        Class<Integer> clazz = Integer.class;

        ObjectHolder objectHolder1 = ObjectHolder.of(clazz, obj);
        ObjectHolder objectHolder2 = ObjectHolder.of(clazz, obj);
        ObjectHolder objectHolder3 = ObjectHolder.of(Boolean.class, obj);

        assertEquals(objectHolder1, objectHolder2);
        assertNotEquals(objectHolder1, objectHolder3);
    }

    @Test
    void hashCodeTest() {
        byte[] obj = new byte[]{};
        Class<Integer> clazz = Integer.class;

        ObjectHolder objectHolder1 = ObjectHolder.of(clazz, obj);
        ObjectHolder objectHolder2 = ObjectHolder.of(clazz, obj);
        ObjectHolder objectHolder3 = ObjectHolder.of(Boolean.class, obj);

        assertEquals(objectHolder1.hashCode(), objectHolder2.hashCode());
        assertNotEquals(objectHolder1.hashCode(), objectHolder3.hashCode());
    }
}