package com.upnp.fakeCall.ivr

class IvrStateMachine(private val config: IvrConfig) {

    private var currentNodeId: String = config.rootId

    fun currentNode(): IvrNode? {
        return config.nodes[currentNodeId]
    }

    fun handleDtmf(digit: Char): IvrNode? {
        val current = currentNode() ?: return null
        val nextId = when (digit) {
            '0' -> config.rootId
            else -> current.routes[digit]
        } ?: return null

        currentNodeId = nextId
        return config.nodes[nextId]
    }
}
