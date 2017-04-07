package io.slychat.messenger.core.files

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import io.slychat.messenger.core.crypto.isValidUUIDFormat

class FileIdSerializer : JsonSerializer<FileId>() {
    override fun serialize(value: FileId, gen: JsonGenerator, serializers: SerializerProvider) {
        gen.writeString(value.string)
    }
}

//only used in serialization currently due to kotlin-mockito issues (see if we still get this in newer versions)
//should probably just have some module-level UUIDBasedId base class to share with MessageId & co
@JsonSerialize(using = FileIdSerializer::class)
data class FileId(val string: String) {
    init {
        require(isValidUUIDFormat(string)) { "$string is not a valid FileId" }
    }

    override fun toString(): String = string
}
