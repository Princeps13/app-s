package com.lucas.sorrentinos.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val id: Int = 1,
    val costoDefaultPorDocena: Double = 0.0,
    val ventaDefaultPorDocena: Double = 0.0
)

enum class EstadoPedido {
    PENDIENTE,
    ENTREGADO,
    CANCELADO
}

@Entity(tableName = "pedidos")
data class PedidoEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val clienteNombre: String,
    val detalle: String,
    val docenas: Int,
    val estado: EstadoPedido,
    val createdAt: Long,
    val weekId: String,
    val costoUnitarioPorDocena: Double,
    val ventaUnitarioPorDocena: Double
)
