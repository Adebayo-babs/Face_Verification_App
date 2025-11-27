package com.example.neurotecsdklibrary.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "face_templates")
data class FaceTemplateEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB)
    val template: ByteArray,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB)
    val image: ByteArray,
    val timestamp: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FaceTemplateEntity

        if (id != other.id) return false
        if (!template.contentEquals(other.template)) return false
        if (!image.contentEquals(other.image)) return false
        if (timestamp != other.timestamp) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + template.contentHashCode()
        result = 31 * result + image.contentHashCode()
        result = 31 * result + timestamp.hashCode()
        return result
    }
}
