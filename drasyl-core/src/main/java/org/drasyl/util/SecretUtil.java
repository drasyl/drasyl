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

/**
 * Utility class for security-related operations.
 */
public class SecretUtil {
    private SecretUtil() {
        // util class
    }

    /**
     * This method replaces each character in the return of <code>secret</code>'s {@link
     * #toString()}-call with a asterisk. Can be used to mask secrets (like private keys or
     * passwords).
     *
     * @param secret
     * @return
     */
    public static String maskSecret(Object secret) {
        if (secret != null) {
            String secretStr = secret.toString();
            if (secretStr != null) {
                return "*".repeat(secretStr.length());
            }
            else {
                return null;
            }
        }
        else {
            return null;
        }
    }
}
