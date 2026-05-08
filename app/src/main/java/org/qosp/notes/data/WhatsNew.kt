package org.qosp.notes.data

import kotlinx.serialization.Serializable

@Serializable
data class WhatsNew(
    val updates: List<WhatsNewItem>
)

@Serializable
data class WhatsNewItem(
    val id: Int,
    val version: String? = null,
    val title: String,
    val items: List<String>
)
