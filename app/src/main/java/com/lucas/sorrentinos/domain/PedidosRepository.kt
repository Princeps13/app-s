package com.lucas.sorrentinos.domain

import com.lucas.sorrentinos.data.ClienteDao
import com.lucas.sorrentinos.data.ClienteEntity
import com.lucas.sorrentinos.data.EstadoPedido
import com.lucas.sorrentinos.data.PedidoDao
import com.lucas.sorrentinos.data.PedidoEntity
import com.lucas.sorrentinos.data.SaborDocenas
import com.lucas.sorrentinos.data.SettingsDao
import com.lucas.sorrentinos.data.SettingsEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

data class PedidoDraft(
    val clienteNombre: String,
    val items: List<SaborCantidad>
)

data class WeekSummary(
    val totalVentas: Double = 0.0,
    val totalCostos: Double = 0.0,
    val ganancia: Double = 0.0,
    val cantidadPedidos: Int = 0,
    val cantidadPendientes: Int = 0
)

data class ClientePedidos(
    val clienteNombre: String,
    val totalPedidos: Int,
    val totalDocenas: Int
)

class PedidosRepository(
    private val settingsDao: SettingsDao,
    private val pedidoDao: PedidoDao,
    private val clienteDao: ClienteDao
) {
    fun observeSettings(): Flow<SettingsEntity> {
        return settingsDao.observeSettings().map { it ?: SettingsEntity() }
    }

    suspend fun saveSettings(costo: Double, venta: Double) {
        settingsDao.upsert(
            SettingsEntity(
                id = 1,
                costoDefaultPorDocena = costo,
                ventaDefaultPorDocena = venta
            )
        )
    }

    fun observeClientes(): Flow<List<ClienteEntity>> = clienteDao.observeAll()

    suspend fun createCliente(
        nombre: String,
        calle: String,
        numero: String,
        entreCalle: String,
        telefono: String
    ) {
        clienteDao.insert(
            ClienteEntity(
                nombre = nombre.trim(),
                calle = calle.trim(),
                numero = numero.trim(),
                entreCalle = entreCalle.trim(),
                telefono = telefono.trim()
            )
        )
    }

    fun observePendingForWeek(weekId: String): Flow<List<PedidoEntity>> =
        pedidoDao.observeByWeekAndEstado(weekId, EstadoPedido.PENDIENTE)

    fun observeDeliveredByWeek(weekId: String): Flow<List<PedidoEntity>> = pedidoDao.observeDeliveredByWeek(weekId)

    fun observeWeekIds(): Flow<List<String>> = pedidoDao.observeWeekIds()

    fun observeTopSaboresByWeek(weekId: String): Flow<List<SaborDocenas>> {
        return pedidoDao.observeActiveByWeek(weekId).map { pedidos ->
            pedidos
                .flatMap { pedido ->
                    PedidoDetalleCodec.decode(pedido.detalle).map { item ->
                        item.sabor to item.docenas
                    }
                }
                .groupBy({ it.first }, { it.second })
                .map { (sabor, docenas) -> SaborDocenas(sabor, docenas.sum()) }
                .sortedWith(compareByDescending<SaborDocenas> { it.totalDocenas }.thenBy { it.detalle })
        }
    }

    fun observeTopClientesByWeek(weekId: String): Flow<List<ClientePedidos>> {
        return pedidoDao.observeActiveByWeek(weekId).map { pedidos ->
            pedidos
                .groupBy { it.clienteNombre.trim() }
                .mapNotNull { (cliente, list) ->
                    if (cliente.isBlank()) {
                        null
                    } else {
                        ClientePedidos(
                            clienteNombre = cliente,
                            totalPedidos = list.size,
                            totalDocenas = list.sumOf { it.docenas }
                        )
                    }
                }
                .sortedWith(
                    compareByDescending<ClientePedidos> { it.totalPedidos }
                        .thenByDescending { it.totalDocenas }
                        .thenBy { it.clienteNombre }
                )
        }
    }

    fun observeWeekSummary(weekId: String): Flow<WeekSummary> {
        return combine(
            pedidoDao.observeWeekTotals(weekId),
            pedidoDao.observePendingCount(weekId)
        ) { totals, pending ->
            WeekSummary(
                totalVentas = totals.totalVenta,
                totalCostos = totals.totalCosto,
                ganancia = totals.totalVenta - totals.totalCosto,
                cantidadPedidos = totals.cantidadPedidos,
                cantidadPendientes = pending
            )
        }
    }

    suspend fun createPedido(draft: PedidoDraft, settings: SettingsEntity) {
        val now = System.currentTimeMillis()
        val weekId = WeekUtils.weekRangeFor(now).weekId
        val totalDocenas = draft.items.sumOf { it.docenas }
        pedidoDao.insert(
            PedidoEntity(
                clienteNombre = draft.clienteNombre.trim(),
                detalle = PedidoDetalleCodec.encode(draft.items),
                docenas = totalDocenas,
                estado = EstadoPedido.PENDIENTE,
                createdAt = now,
                weekId = weekId,
                costoUnitarioPorDocena = settings.costoDefaultPorDocena,
                ventaUnitarioPorDocena = settings.ventaDefaultPorDocena
            )
        )
    }

    suspend fun markEntregado(id: Int) {
        val item = pedidoDao.findById(id) ?: return
        pedidoDao.update(item.copy(estado = EstadoPedido.ENTREGADO))
    }

    suspend fun cancelPedido(id: Int) {
        val item = pedidoDao.findById(id) ?: return
        pedidoDao.update(item.copy(estado = EstadoPedido.CANCELADO))
    }

    suspend fun updatePedido(id: Int, clienteNombre: String, items: List<SaborCantidad>) {
        val item = pedidoDao.findById(id) ?: return
        pedidoDao.update(
            item.copy(
                clienteNombre = clienteNombre.trim(),
                detalle = PedidoDetalleCodec.encode(items),
                docenas = items.sumOf { it.docenas }
            )
        )
    }
}
