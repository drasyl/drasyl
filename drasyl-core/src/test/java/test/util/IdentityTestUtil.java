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
package test.util;

import org.drasyl.identity.Identity;

public class IdentityTestUtil {
    public static final Identity ID_1;
    public static final Identity ID_2;
    public static final Identity ID_3;
    public static final Identity ID_4;
    public static final Identity ID_5;

    static {
        ID_1 = Identity.of(-2082598243,
                "18cdb282be8d1293f5040cd620a91aca86a475682e4ddc397deabe300aad9127",
                "65f20fc3fdcaf569cdcf043f79047723d8856b0169bd4c475ba15ef1b37d27ae18cdb282be8d1293f5040cd620a91aca86a475682e4ddc397deabe300aad9127");
        ID_2 = Identity.of(-2122268831,
                "622d860a23517b0e20e59d8a481db4da2c89649c979d7318bc4ef19828f4663e",
                "fc10ab6bb85c51c453dbfe44c0c29d96d1a365257ad871dea49c29c98f1f8421622d860a23517b0e20e59d8a481db4da2c89649c979d7318bc4ef19828f4663e");
        ID_3 = Identity.of(-2142814279,
                "f43772fd65e9fa28e729c71c199ef21c7f2b019be924e87f94f3dc27e9e63853",
                "c0b360e58d296c2e39c44ef67d07ff9150d072c7e87cda7e8ef9ffe516ce2574f43772fd65e9fa28e729c71c199ef21c7f2b019be924e87f94f3dc27e9e63853");
        ID_4 = Identity.of(-2146545473,
                "7a4b877986bd660bf3fc371d74f9049660213d2b39390ff8932307b5a0818b97",
                "f92962d289fcd3b7b007adfa5141bcb236d792015b9ed790950669fac314beda7a4b877986bd660bf3fc371d74f9049660213d2b39390ff8932307b5a0818b97");
        ID_5 = Identity.of(-2144629378,
                "b20a3def5777433017fb624ffba2b9c058a2eb1dcec0e2f8fcd2d843468ea9fc",
                "8aed43756bea9689520a855b5ab26fb0c938ce3462038be791c28255849ae254b20a3def5777433017fb624ffba2b9c058a2eb1dcec0e2f8fcd2d843468ea9fc");
    }

    private IdentityTestUtil() {
        // util class
    }
}
