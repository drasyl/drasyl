/*
 * Copyright (c) 2020-2021.
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

import java.nio.file.Path;

/**
 * Utility class for operations on {@link Path}es.
 */
public class PathUtil {
    private PathUtil() {
        // util class
    }

    /**
     * Check if {@code path}'s file system supports POSIX.
     *
     * @param path the path
     * @return {@code true} if path's file system supports POSIX. Otherwise {@code false}.
     */
    public static boolean hasPosixSupport(final Path path) {
        return path.getFileSystem().supportedFileAttributeViews().contains("posix");
    }
}
