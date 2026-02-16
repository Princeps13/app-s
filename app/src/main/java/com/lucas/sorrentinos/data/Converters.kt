package com.lucas.sorrentinos.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromEstado(value: EstadoPedido): String = value.name

    @TypeConverter
    fun toEstado(value: String): EstadoPedido = EstadoPedido.valueOf(value)
}
