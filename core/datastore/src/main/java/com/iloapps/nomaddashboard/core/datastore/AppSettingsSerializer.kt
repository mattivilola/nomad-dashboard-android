package com.iloapps.nomaddashboard.core.datastore

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.iloapps.nomaddashboard.core.model.AppSettings
import java.io.InputStream
import java.io.OutputStream

object AppSettingsSerializer : Serializer<AppSettingsProto> {
    override val defaultValue: AppSettingsProto = AppSettings().toProto()

    override suspend fun readFrom(input: InputStream): AppSettingsProto =
        try {
            AppSettingsProto.parseFrom(input)
        } catch (exception: Exception) {
            throw CorruptionException("Cannot read app settings proto.", exception)
        }

    override suspend fun writeTo(t: AppSettingsProto, output: OutputStream) {
        t.writeTo(output)
    }
}
