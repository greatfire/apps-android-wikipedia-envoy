package org.greatfire.wikiunblocked

class Secrets {

    // Method calls will be added by gradle task hideSecret
    // Example : external fun getWellHiddenSecret(packageName: String): String

    companion object {
        init {
            System.loadLibrary("secrets")
        }
    }

    external fun geturlStart(packageName: String): String

    external fun geturlEnd(packageName: String): String

    external fun geturlSources(packageName: String): String

    external fun geturlInterval(packageName: String): String

    external fun gethystCert(packageName: String): String

    external fun getdefProxy(packageName: String): String
}
