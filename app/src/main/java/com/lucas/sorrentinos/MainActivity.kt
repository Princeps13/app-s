package com.lucas.sorrentinos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lucas.sorrentinos.data.PedidoEntity
import com.lucas.sorrentinos.domain.WeekSummary
import com.lucas.sorrentinos.ui.AppViewModel
import com.lucas.sorrentinos.ui.theme.AppsTheme
import java.text.NumberFormat
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppsTheme {
                PedidosSorrentinosApp(viewModel)
            }
        }
    }
}

enum class TabDestination(val label: String) {
    PENDIENTES("Pendientes"),
    ENTREGADOS("Entregados"),
    RESUMEN("Resumen"),
    AJUSTES("Ajustes")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PedidosSorrentinosApp(viewModel: AppViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableStateOf(TabDestination.PENDIENTES) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            NavigationBar {
                TabDestination.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = tab == selectedTab,
                        onClick = { selectedTab = tab },
                        label = { Text(tab.label) },
                        icon = {}
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator()
            }

            when (selectedTab) {
                TabDestination.PENDIENTES -> PendientesTab(
                    pedidos = uiState.pendingPedidos,
                    onCreate = viewModel::createPedido,
                    onMarkDelivered = viewModel::markEntregado,
                    onCancel = viewModel::cancelPedido,
                    onEdit = viewModel::updatePedido
                )

                TabDestination.ENTREGADOS -> EntregadosTab(
                    weekIds = uiState.availableWeeks,
                    selectedWeekId = uiState.selectedDeliveredWeekId,
                    pedidos = uiState.deliveredPedidos,
                    onSelectWeek = viewModel::onSelectDeliveredWeek
                )

                TabDestination.RESUMEN -> ResumenTab(
                    weekIds = uiState.availableWeeks,
                    selectedWeekId = uiState.selectedSummaryWeekId,
                    summary = uiState.weekSummary,
                    onSelectWeek = viewModel::onSelectSummaryWeek
                )

                TabDestination.AJUSTES -> AjustesTab(
                    costoActual = uiState.settings.costoDefaultPorDocena,
                    ventaActual = uiState.settings.ventaDefaultPorDocena,
                    onSave = viewModel::saveSettings
                )
            }
        }
    }
}

@Composable
private fun PendientesTab(
    pedidos: List<PedidoEntity>,
    onCreate: (String, String, Int) -> Unit,
    onMarkDelivered: (Int) -> Unit,
    onCancel: (Int) -> Unit,
    onEdit: (Int, String, String, Int) -> Unit
) {
    var showCreate by remember { mutableStateOf(false) }
    var editPedido by remember { mutableStateOf<PedidoEntity?>(null) }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { showCreate = true }) {
            Text("Nuevo pedido")
        }
    }

    if (showCreate) {
        PedidoFormDialog(
            title = "Nuevo pedido",
            initialCliente = "",
            initialDetalle = "",
            initialDocenas = "1",
            onDismiss = { showCreate = false },
            onConfirm = { cliente, detalle, docenas ->
                onCreate(cliente, detalle, docenas)
                showCreate = false
            }
        )
    }

    editPedido?.let { pedido ->
        PedidoFormDialog(
            title = "Editar pedido #${pedido.id}",
            initialCliente = pedido.clienteNombre,
            initialDetalle = pedido.detalle,
            initialDocenas = pedido.docenas.toString(),
            onDismiss = { editPedido = null },
            onConfirm = { cliente, detalle, docenas ->
                onEdit(pedido.id, cliente, detalle, docenas)
                editPedido = null
            }
        )
    }

    if (pedidos.isEmpty()) {
        Text("No hay pedidos pendientes en la semana actual.")
        return
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(pedidos, key = { it.id }) { pedido ->
            PedidoCard(
                pedido = pedido,
                actions = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onMarkDelivered(pedido.id) }) {
                            Text("Marcar entregado")
                        }
                        TextButton(onClick = { onCancel(pedido.id) }) {
                            Text("Cancelar")
                        }
                        TextButton(onClick = { editPedido = pedido }) {
                            Text("Editar")
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun EntregadosTab(
    weekIds: List<String>,
    selectedWeekId: String,
    pedidos: List<PedidoEntity>,
    onSelectWeek: (String) -> Unit
) {
    WeekSelector(weekIds = weekIds, selectedWeekId = selectedWeekId, onSelectWeek = onSelectWeek)

    if (pedidos.isEmpty()) {
        Text("No hay pedidos entregados para la semana seleccionada.")
        return
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(pedidos, key = { it.id }) { pedido ->
            PedidoCard(pedido = pedido)
        }
    }
}

@Composable
private fun ResumenTab(
    weekIds: List<String>,
    selectedWeekId: String,
    summary: WeekSummary,
    onSelectWeek: (String) -> Unit
) {
    WeekSelector(weekIds = weekIds, selectedWeekId = selectedWeekId, onSelectWeek = onSelectWeek)

    val currency = remember { NumberFormat.getCurrencyInstance(Locale("es", "AR")) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Total ventas: ${currency.format(summary.totalVentas)}")
        Text("Total costos: ${currency.format(summary.totalCostos)}")
        Text("Ganancia: ${currency.format(summary.ganancia)}", fontWeight = FontWeight.Bold)
        Text("Cantidad de pedidos: ${summary.cantidadPedidos}")
        Text("Cantidad pendientes: ${summary.cantidadPendientes}")
    }
}

@Composable
private fun AjustesTab(costoActual: Double, ventaActual: Double, onSave: (Double, Double) -> Unit) {
    var costo by remember(costoActual) { mutableStateOf(costoActual.toString()) }
    var venta by remember(ventaActual) { mutableStateOf(ventaActual.toString()) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = costo,
            onValueChange = { costo = it },
            label = { Text("Costo default por docena") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = venta,
            onValueChange = { venta = it },
            label = { Text("Venta default por docena") },
            modifier = Modifier.fillMaxWidth()
        )
        Button(onClick = {
            val costoValue = costo.toDoubleOrNull()
            val ventaValue = venta.toDoubleOrNull()
            if (costoValue != null && ventaValue != null) {
                onSave(costoValue, ventaValue)
            }
        }) {
            Text("Guardar")
        }
    }
}

@Composable
private fun PedidoCard(pedido: PedidoEntity, actions: @Composable (() -> Unit)? = null) {
    val currency = remember { NumberFormat.getCurrencyInstance(Locale("es", "AR")) }
    val totalVenta = pedido.docenas * pedido.ventaUnitarioPorDocena
    val totalCosto = pedido.docenas * pedido.costoUnitarioPorDocena
    val ganancia = totalVenta - totalCosto

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text("#${pedido.id} - ${pedido.clienteNombre}", style = MaterialTheme.typography.titleMedium)
        Text("Detalle: ${pedido.detalle.ifBlank { "-" }}")
        Text("Docenas: ${pedido.docenas}")
        Text("Venta total: ${currency.format(totalVenta)}")
        Text("Costo total: ${currency.format(totalCosto)}")
        Text("Ganancia: ${currency.format(ganancia)}")
        actions?.invoke()
    }
}

@Composable
private fun WeekSelector(weekIds: List<String>, selectedWeekId: String, onSelectWeek: (String) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                weekIds.forEach { weekId ->
                    FilterChip(
                        selected = weekId == selectedWeekId,
                        onClick = { onSelectWeek(weekId) },
                        label = { Text(weekId) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PedidoFormDialog(
    title: String,
    initialCliente: String,
    initialDetalle: String,
    initialDocenas: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String, Int) -> Unit
) {
    var cliente by remember { mutableStateOf(initialCliente) }
    var detalle by remember { mutableStateOf(initialDetalle) }
    var docenas by remember { mutableStateOf(initialDocenas) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = cliente,
                    onValueChange = { cliente = it },
                    label = { Text("Cliente") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = detalle,
                    onValueChange = { detalle = it },
                    label = { Text("Detalle") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = docenas,
                    onValueChange = { docenas = it },
                    label = { Text("Docenas") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val d = docenas.toIntOrNull() ?: 0
                onConfirm(cliente, detalle, d)
            }) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
