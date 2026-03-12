package com.upnp.fakeCall.ivr

import android.content.Context
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.StringWriter

class IvrConfigStore {

    fun load(context: Context): IvrConfig? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_CONFIG_XML, "").orEmpty()
        if (raw.isBlank()) return null
        return parse(raw)
    }

    fun save(context: Context, config: IvrConfig?) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = if (config == null) "" else serialize(config)
        prefs.edit().putString(KEY_CONFIG_XML, raw).apply()
    }

    fun serialize(config: IvrConfig): String {
        val writer = StringWriter()
        val serializer = Xml.newSerializer()
        serializer.setOutput(writer)
        serializer.startDocument("UTF-8", true)
        serializer.startTag(null, TAG_ROOT)
        serializer.attribute(null, ATTR_ROOT_ID, config.rootId)

        config.nodes.values.forEach { node ->
            serializer.startTag(null, TAG_NODE)
            serializer.attribute(null, ATTR_NODE_ID, node.id)
            serializer.attribute(null, ATTR_NODE_TITLE, node.title)
            serializer.attribute(null, ATTR_NODE_AUDIO_URI, node.audioUri)
            serializer.attribute(null, ATTR_NODE_AUDIO_LABEL, node.audioLabel)
            node.routes.forEach { (digit, targetId) ->
                serializer.startTag(null, TAG_ROUTE)
                serializer.attribute(null, ATTR_ROUTE_DIGIT, digit.toString())
                serializer.attribute(null, ATTR_ROUTE_TARGET, targetId)
                serializer.endTag(null, TAG_ROUTE)
            }
            serializer.endTag(null, TAG_NODE)
        }

        serializer.endTag(null, TAG_ROOT)
        serializer.endDocument()
        return writer.toString()
    }

    fun parse(raw: String): IvrConfig? {
        val parser = Xml.newPullParser()
        parser.setInput(raw.reader())

        var rootId = ""
        val nodes = mutableMapOf<String, IvrNode>()

        var currentId: String? = null
        var currentTitle = ""
        var currentAudioUri = ""
        var currentAudioLabel = ""
        var currentRoutes = mutableMapOf<Char, String>()

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        TAG_ROOT -> {
                            rootId = parser.getAttributeValue(null, ATTR_ROOT_ID).orEmpty()
                        }
                        TAG_NODE -> {
                            currentId = parser.getAttributeValue(null, ATTR_NODE_ID)
                            currentTitle = parser.getAttributeValue(null, ATTR_NODE_TITLE).orEmpty()
                            currentAudioUri = parser.getAttributeValue(null, ATTR_NODE_AUDIO_URI).orEmpty()
                            currentAudioLabel = parser.getAttributeValue(null, ATTR_NODE_AUDIO_LABEL).orEmpty()
                            currentRoutes = mutableMapOf()
                        }
                        TAG_ROUTE -> {
                            val digit = parser.getAttributeValue(null, ATTR_ROUTE_DIGIT)
                                ?.firstOrNull()
                            val target = parser.getAttributeValue(null, ATTR_ROUTE_TARGET)
                            if (digit != null && !target.isNullOrBlank()) {
                                currentRoutes[digit] = target
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == TAG_NODE && currentId != null) {
                        val node = IvrNode(
                            id = currentId!!,
                            title = currentTitle,
                            audioUri = currentAudioUri,
                            audioLabel = currentAudioLabel,
                            routes = currentRoutes.toMap()
                        )
                        nodes[currentId!!] = node
                        currentId = null
                        currentTitle = ""
                        currentAudioUri = ""
                        currentAudioLabel = ""
                        currentRoutes = mutableMapOf()
                    }
                }
            }
            event = parser.next()
        }

        if (nodes.isEmpty()) return null
        if (rootId.isBlank()) {
            rootId = nodes.keys.first()
        }
        return IvrConfig(rootId = rootId, nodes = nodes)
    }

    companion object {
        private const val PREFS_NAME = "fake_call_ivr"
        private const val KEY_CONFIG_XML = "ivr_config_xml"

        private const val TAG_ROOT = "ivr"
        private const val TAG_NODE = "node"
        private const val TAG_ROUTE = "route"

        private const val ATTR_ROOT_ID = "rootId"
        private const val ATTR_NODE_ID = "id"
        private const val ATTR_NODE_TITLE = "title"
        private const val ATTR_NODE_AUDIO_URI = "audioUri"
        private const val ATTR_NODE_AUDIO_LABEL = "audioLabel"
        private const val ATTR_ROUTE_DIGIT = "digit"
        private const val ATTR_ROUTE_TARGET = "target"
    }
}
