package org.langera.audiofix

import java.security.MessageDigest


class Hash {

    static String md5(final String str) {
        MessageDigest digest = MessageDigest.getInstance("MD5")
        digest.update(str.bytes)
        new BigInteger(1, digest.digest()).toString(16).padLeft(32, '0')
    }
}
