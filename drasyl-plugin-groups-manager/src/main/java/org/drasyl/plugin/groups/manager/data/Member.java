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
package org.drasyl.plugin.groups.manager.data;

import org.drasyl.identity.CompressedPublicKey;

import java.util.Objects;

/**
 * Class is used to model the state of a member.
 * <p>
 * <b>This class should only plugin internally used.</b>
 * </p>
 */
public class Member {
    private final CompressedPublicKey publicKey;

    private Member(final CompressedPublicKey publicKey) {
        this.publicKey = publicKey;
    }

    public CompressedPublicKey getPublicKey() {
        return publicKey;
    }

    @Override
    public int hashCode() {
        return Objects.hash(publicKey);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final Member that = (Member) o;
        return Objects.equals(publicKey, that.publicKey);
    }

    @Override
    public String toString() {
        return "Member{" +
                "publicKey=" + publicKey +
                '}';
    }

    public static Member of(final CompressedPublicKey publicKey) {
        return new Member(publicKey);
    }
}
