package com.phonecontroller.app

import android.content.Context

/**
 * The blocked-domain list, persisted in plain SharedPreferences, read by
 * [DomainBlockVpnService] on every DNS query. A domain match also blocks
 * its subdomains (blocking "facebook.com" also blocks "m.facebook.com"),
 * but not the other way around.
 */
object DomainBlocklistStore {
    private const val PREFS = "domain_blocklist"
    private const val KEY_DOMAINS = "blocked_domains"

    fun getBlockedDomains(context: Context): Set<String> {
        val raw = prefs(context).getString(KEY_DOMAINS, "") ?: ""
        return raw.lineSequence().map(::normalize).filter { it.isNotEmpty() }.toSet()
    }

    fun toEditableText(context: Context): String = getBlockedDomains(context).sorted().joinToString("\n")

    /** Takes raw one-per-line text straight from the edit box; normalizing happens here. */
    fun saveFromText(context: Context, rawLines: String) {
        val cleaned = rawLines.lineSequence()
            .map(::normalize)
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()
            .joinToString("\n")
        prefs(context).edit().putString(KEY_DOMAINS, cleaned).apply()
    }

    fun isBlocked(context: Context, queryDomain: String): Boolean {
        val domain = normalize(queryDomain)
        if (domain.isEmpty()) return false
        val blockedSet = getBlockedDomains(context)
        return blockedSet.any { blocked -> domain == blocked || domain.endsWith(".$blocked") }
    }

    private fun normalize(domain: String): String =
        domain.trim().lowercase().removePrefix("www.").trim('.')

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
