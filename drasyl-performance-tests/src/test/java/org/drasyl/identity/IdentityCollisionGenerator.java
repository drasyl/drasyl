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
package org.drasyl.identity;

import org.drasyl.crypto.Crypto;
import org.drasyl.crypto.CryptoException;

import java.security.KeyPair;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.MINUTES;

/**
 * This program generates new identities until a collision happens.
 */
//public class IdentityCollisionGenerator {
//    private static final Map<Identity, CompressedKeyPair> identities = new ConcurrentHashMap<>();
//
//    public static void main(String[] args) throws CryptoException {
//        long startTime = System.currentTimeMillis();
//        Identity address;
//        CompressedKeyPair compressedKeyPair;
//        do {
//            KeyPair keyPair = Crypto.generateKeys();
//            compressedKeyPair = CompressedKeyPair.of(keyPair);
//            address = Address.derive(compressedKeyPair.getPublicKey());
//
//            int size = identities.size();
//            if (size > 0 && size % 1000 == 0) {
//                System.out.println(size + " identities generated...");
//            }
//        }
//        while (identities.putIfAbsent(address, compressedKeyPair) == null);
//        long endTime = System.currentTimeMillis();
//
//        System.out.println();
//        System.out.println("Collision found!");
//        System.out.println();
//        System.out.println("Address              : " + address);
//        System.out.println("Public Key #1        : " + identities.get(address).getPublicKey());
//        System.out.println("Private Key #1       : " + identities.get(address).getPrivateKey());
//        System.out.println("Public Key #2        : " + compressedKeyPair.getPublicKey());
//        System.out.println("Private Key #2       : " + compressedKeyPair.getPrivateKey());
//        System.out.println();
//
//        System.out.println("Generated Identities : " + identities.size());
//        Duration runtime = Duration.ofMillis(endTime - startTime);
//        String runtimeStr = String.format("%sd %sh %sm %ss", runtime.toDays(),
//                runtime.toHours() - DAYS.toHours(runtime.toDays()),
//                runtime.toMinutes() - HOURS.toMinutes(runtime.toHours()),
//                runtime.getSeconds() - MINUTES.toSeconds(runtime.toMinutes()));
//        System.out.println("Total runtime        : " + runtimeStr);
//    }
//}
