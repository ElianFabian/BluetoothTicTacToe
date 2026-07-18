package com.elianfabian.bluetoothtictactoe.rpc

import com.elianfabian.lapisbt_rpc.LapisSerializationStrategy
import com.elianfabian.lapisbt_rpc.serializer.LapisSerializer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import kotlinx.serialization.serializer
import java.io.InputStream
import java.io.OutputStream
import kotlin.reflect.KClass

@OptIn(ExperimentalSerializationApi::class)
class JsonLapisSerializationStrategy(
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
) : LapisSerializationStrategy {

    @Suppress("UNCHECKED_CAST")
    override fun serializerForClass(type: KClass<*>): LapisSerializer<*>? {
        return try {
            val kSerializer = json.serializersModule.serializer(type.java) as KSerializer<Any>
            JsonLapisSerializer(json, kSerializer)
        } catch (e: Exception) {
            null
        }
    }
}

@OptIn(ExperimentalSerializationApi::class)
class JsonLapisSerializer<T>(
    private val json: Json,
    private val kSerializer: KSerializer<T>
) : LapisSerializer<T> {

    override fun serialize(stream: OutputStream, data: T) {
        json.encodeToStream(kSerializer, data, stream)
        stream.flush()
    }

    override fun deserialize(stream: InputStream): T {
        return json.decodeFromStream(kSerializer, stream)
    }
}
