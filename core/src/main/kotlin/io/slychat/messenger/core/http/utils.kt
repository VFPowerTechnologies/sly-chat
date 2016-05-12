@file:JvmName("HttpUtils")
package io.slychat.messenger.core.http

import java.net.URLEncoder

fun toQueryString(params: List<Pair<String, String>>): String {
    if (params.isEmpty())
        return ""

    val builder = StringBuilder()

    for ((k, v) in params) {
        builder.append(URLEncoder.encode(k, "UTF-8"))
        builder.append("=")
        builder.append(URLEncoder.encode(v, "UTF-8"))
        builder.append("&")
    }
    builder.deleteCharAt(builder.length-1)

    return builder.toString()
}
