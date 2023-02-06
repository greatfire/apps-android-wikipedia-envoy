package org.greatfire.wikiunblocked

class Secrets {

    // Method calls will be added by gradle task hideSecret
    // Example : external fun getWellHiddenSecret(packageName: String): String

    companion object {
        init {
            System.loadLibrary("secrets")
        }
    }

    external fun getdotAddr(packageName: String): String

    external fun getdohUrl(packageName: String): String

    external fun gethystCert(packageName: String): String

    external fun getdnsttdomain(packageName: String): String

    external fun getdnsttpath(packageName: String): String

    external fun getdnsttkey(packageName: String): String

    external fun getdefProxy(packageName: String): String
}