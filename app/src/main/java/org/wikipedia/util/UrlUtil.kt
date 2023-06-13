package org.wikipedia.util

import android.util.Log
import java.net.URI

class UrlUtil {

    companion object {

        private val TAG = "UrlUtil"

        fun sanitizeUrl(url: String, service: String): String {

            var sanitizedString = ""

            try {
                if (service.equals("update")) {
                    // extract number from url
                    val parts = url.split("/")
                    if (parts.size > 1) {
                        sanitizedString = parts[parts.size - 2]
                    }
                } else if (service.equals("envoy")) {
                    // extract domain and ip from queries
                    val uri = URI(url)
                    val rawQuery = uri.rawQuery
                    val queries = rawQuery.split("&")
                    for (i in 0 until queries.size) {
                        val queryParts = queries[i].split("=")
                        if (queryParts[0].equals("url")) {
                            if (!sanitizedString.isNullOrEmpty()) {
                                sanitizedString = sanitizedString.plus(",")
                            }
                            sanitizedString = sanitizedString.plus(
                                queryParts[1].substring(
                                    queryParts[1].indexOf('.') + 1,
                                    queryParts[1].indexOf("%2F", queryParts[1].indexOf('.'))
                                )
                            )
                        } else if (queryParts[0].equals("address")) {
                            if (!sanitizedString.isNullOrEmpty()) {
                                sanitizedString = sanitizedString.plus(",")
                            }
                            sanitizedString = sanitizedString.plus(queryParts[1])
                        }
                    }
                } else if (service.equals("snowflake")) {
                    // extract domain from queries
                    val uri = URI(url)
                    val rawQuery = uri.rawQuery
                    val queries = rawQuery.split("&")
                    for (i in 0 until queries.size) {
                        val queryParts = queries[i].split("=")
                        if (queryParts[0].equals("url")) {
                            sanitizedString = sanitizedString.plus(
                                queryParts[1].substring(
                                    queryParts[1].indexOf('.') + 1,
                                    queryParts[1].indexOf("/", queryParts[1].indexOf('.'))
                                )
                            )
                        }
                    }
                } else if (service.equals("https") || service.equals("direct")) {
                    // extract domain from url
                    sanitizedString = sanitizedString.plus(
                        url.substring(
                            url.indexOf('.') + 1,
                            url.indexOf("/", url.indexOf('.'))
                        )
                    )
                } else {
                    // no-op
                }
            } catch (e: Exception) {
                Log.e(TAG, "got exception while sanitizing url: " + e.message)
            }

            if (sanitizedString.isNullOrEmpty()) {
                Log.d(TAG, "failed to sanitize url for service " + service)
            } else {
                Log.d(TAG, "sanitized url for service " + service)
            }

            return sanitizedString
        }
    }
}