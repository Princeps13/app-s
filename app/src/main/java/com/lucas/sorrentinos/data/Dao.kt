package com.lucas.sorrentinos.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SettingsDao {
    @Query("SELECT * FROM settings WHERE id = 1")
    fun observeSettings(): Flow<SettingsEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(settings: SettingsEntity)
}

data class WeekTotals(
    val totalVenta: Double,
    val totalCosto: Double,
    val cantidadPedidos: Int
)

data class SaborDocenas(
    val detalle: String,
    val totalDocenas: Int
)

@Dao
interface PedidoDao {
    @Insert
    suspend fun insert(pedido: PedidoEntity)

    @Update
    suspend fun update(pedido: PedidoEntity)

    @Query("SELECT * FROM pedidos WHERE weekId = :weekId AND estado = :estado ORDER BY createdAt DESC")
    fun observeByWeekAndEstado(weekId: String, estado: EstadoPedido): Flow<List<PedidoEntity>>

    @Query("SELECT * FROM pedidos WHERE weekId = :weekId AND estado = 'ENTREGADO' ORDER BY createdAt DESC")
    fun observeDeliveredByWeek(weekId: String): Flow<List<PedidoEntity>>

    @Query("SELECT DISTINCT weekId FROM pedidos ORDER BY weekId DESC")
    fun observeWeekIds(): Flow<List<String>>

    @Query("SELECT * FROM pedidos WHERE weekId = :weekId AND estado != 'CANCELADO'")
    fun observeActiveByWeek(weekId: String): Flow<List<PedidoEntity>>

    @Query(
        """
        SELECT
            COALESCE(SUM(docenas * ventaUnitarioPorDocena), 0.0) AS totalVenta,
            COALESCE(SUM(docenas * costoUnitarioPorDocena), 0.0) AS totalCosto,
            COUNT(*) AS cantidadPedidos
        FROM pedidos
        WHERE weekId = :weekId AND estado != 'CANCELADO'
        """
    )
    fun observeWeekTotals(weekId: String): Flow<WeekTotals>

    @Query("SELECT COUNT(*) FROM pedidos WHERE weekId = :weekId AND estado = 'PENDIENTE'")
    fun observePendingCount(weekId: String): Flow<Int>

    @Query("SELECT * FROM pedidos WHERE id = :id")
    suspend fun findById(id: Int): PedidoEntity?
}

@Dao
interface ClienteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(cliente: ClienteEntity)

    @Query("SELECT * FROM clientes ORDER BY nombre COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<ClienteEntity>>
}
