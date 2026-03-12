package com.upnp.fakeCall.ivr

data class IvrConfig(
    val rootId: String,
    val nodes: Map<String, IvrNode>
)

data class IvrNode(
    val id: String,
    val title: String,
    val audioUri: String = "",
    val audioLabel: String = "",
    val routes: Map<Char, String> = emptyMap()
)
