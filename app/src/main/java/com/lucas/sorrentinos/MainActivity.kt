package com.lucas.sorrentinos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lucas.sorrentinos.data.PedidoEntity
import com.lucas.sorrentinos.data.SaborDocenas
import com.lucas.sorrentinos.domain.PedidoDetalleCodec
import com.lucas.sorrentinos.domain.SaborCantidad
import com.lucas.sorrentinos.domain.WeekSummary
import com.lucas.sorrentinos.ui.AppViewModel
import com.lucas.sorrentinos.ui.theme.AppsTheme
import java.text.NumberFormat
import java.util.Locale

private val SABORES_SORRENTINOS = listOf(
    "Jamón y queso",
    "Jamón y cheddar",
    "Jamón y roquefort",
    "Capresse",
    "Jamón y ricota",
    "Verdura",
    "Verdura y queso",
    "Verdura y ricota",
    "Calabaza",
    "Calabaza y ricota",
    "Calabaza y queso",
    "Calabaza y roquefort",
    "Roquefort y queso",
    "Ricota y queso",
    "Pollo y roquefort",
    "Pollo y verdura",
    "Pollo y ricota",
    "Pollo y queso"
)

data class PedidoFormItem(
    val sabor: String = SABORES_SORRENTINOS.first(),
    val docenas: String = "1"
)

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
                    weekLabels = uiState.weekLabels,
                    selectedWeekId = uiState.selectedDeliveredWeekId,
                    pedidos = uiState.deliveredPedidos,
                    onSelectWeek = viewModel::onSelectDeliveredWeek
                )

                TabDestination.RESUMEN -> ResumenTab(
                    weekIds = uiState.availableWeeks,
                    weekLabels = uiState.weekLabels,
                    selectedWeekId = uiState.selectedSummaryWeekId,
                    summary = uiState.weekSummary,
                    topSabores = uiState.topSabores,
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
    onCreate: (String, List<SaborCantidad>) -> Unit,
    onMarkDelivered: (Int) -> Unit,
    onCancel: (Int) -> Unit,
    onEdit: (Int, String, List<SaborCantidad>) -> Unit
) {
    var showCreate by remember { mutableStateOf(false) }
    var editPedido by remember { mutableStateOf<PedidoEntity?>(null) }

    Button(onClick = { showCreate = true }) {
        Text("Nuevo pedido")
    }

    if (showCreate) {
        PedidoFormDialog(
            title = "Nuevo pedido",
            initialCliente = "",
            initialItems = listOf(PedidoFormItem()),
            onDismiss = { showCreate = false },
            onConfirm = { cliente, items ->
                onCreate(cliente, items)
                showCreate = false
            }
        )
    }

    editPedido?.let { pedido ->
        PedidoFormDialog(
            title = "Editar pedido #${pedido.id}",
            initialCliente = pedido.clienteNombre,
            initialItems = PedidoDetalleCodec.decode(pedido.detalle)
                .ifEmpty { listOf(SaborCantidad(pedido.detalle, pedido.docenas)) }
                .map { PedidoFormItem(sabor = it.sabor, docenas = it.docenas.toString()) },
            onDismiss = { editPedido = null },
            onConfirm = { cliente, items ->
                onEdit(pedido.id, cliente, items)
                editPedido = null
            }
        )
    }

    if (pedidos.isEmpty()) {
        Text("No hay pedidos pendientes en la semana actual.")
        return
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
    weekLabels: Map<String, String>,
    selectedWeekId: String,
    pedidos: List<PedidoEntity>,
    onSelectWeek: (String) -> Unit
) {
    WeekSelector(
        weekIds = weekIds,
        weekLabels = weekLabels,
        selectedWeekId = selectedWeekId,
        onSelectWeek = onSelectWeek
    )

    if (pedidos.isEmpty()) {
        Text("No hay pedidos entregados para la semana seleccionada.")
        return
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(pedidos, key = { it.id }) { pedido ->
            PedidoCard(pedido = pedido)
        }
    }
}

@Composable
private fun ResumenTab(
    weekIds: List<String>,
    weekLabels: Map<String, String>,
    selectedWeekId: String,
    summary: WeekSummary,
    topSabores: List<SaborDocenas>,
    onSelectWeek: (String) -> Unit
) {
    WeekSelector(
        weekIds = weekIds,
        weekLabels = weekLabels,
        selectedWeekId = selectedWeekId,
        onSelectWeek = onSelectWeek
    )

    val currency = remember { NumberFormat.getCurrencyInstance(Locale("es", "AR")) }
    var showSabores by remember { mutableStateOf(true) }
    var showGanancia by remember { mutableStateOf(true) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("Total ventas: ${currency.format(summary.totalVentas)}", style = MaterialTheme.typography.titleMedium)
                Text("Total costos: ${currency.format(summary.totalCostos)}")
                Text("Cantidad de pedidos: ${summary.cantidadPedidos}")
                Text("Cantidad pendientes: ${summary.cantidadPendientes}")
            }
        }

        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Ganancia neta", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = { showGanancia = !showGanancia }) {
                        Text(if (showGanancia) "Ocultar" else "Mostrar")
                    }
                }
                if (showGanancia) {
                    Text("Ganancia = Ventas - Costos", fontWeight = FontWeight.Bold)
                    Text(
                        "${currency.format(summary.ganancia)} = ${currency.format(summary.totalVentas)} - ${currency.format(summary.totalCostos)}"
                    )
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Más pedidos por sabor", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = { showSabores = !showSabores }) {
                        Text(if (showSabores) "Ocultar" else "Mostrar")
                    }
                }

                if (showSabores) {
                    if (topSabores.isEmpty()) {
                        Text("Todavía no hay sabores cargados para esta semana.")
                    } else {
                        topSabores.take(5).forEachIndexed { index, sabor ->
                            Text("${index + 1}. ${sabor.detalle}: ${sabor.totalDocenas} docenas")
                        }
                    }
                }
            }
        }
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
            label = { Text("Costo por docena") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = venta,
            onValueChange = { venta = it },
            label = { Text("Venta por docena") },
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

    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("#${pedido.id} · ${pedido.clienteNombre}", style = MaterialTheme.typography.titleMedium)
            Text(
                "Sabores: ${PedidoDetalleCodec.toDisplayText(pedido.detalle).ifBlank { "-" }}",
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text("Docenas: ${pedido.docenas}")
            Text("Venta total: ${currency.format(totalVenta)}")
            actions?.invoke()
        }
    }
}

@Composable
private fun WeekSelector(
    weekIds: List<String>,
    weekLabels: Map<String, String>,
    selectedWeekId: String,
    onSelectWeek: (String) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        item {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                weekIds.forEach { weekId ->
                    FilterChip(
                        selected = weekId == selectedWeekId,
                        onClick = { onSelectWeek(weekId) },
                        label = { Text(weekLabels[weekId] ?: weekId) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PedidoFormDialog(
    title: String,
    initialCliente: String,
    initialItems: List<PedidoFormItem>,
    onDismiss: () -> Unit,
    onConfirm: (String, List<SaborCantidad>) -> Unit
) {
    var cliente by remember { mutableStateOf(initialCliente) }
    val items = remember(initialItems) {
        mutableStateListOf<PedidoFormItem>().apply {
            addAll(initialItems.ifEmpty { listOf(PedidoFormItem()) })
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = cliente,
                    onValueChange = { cliente = it },
                    label = { Text("Cliente") },
                    modifier = Modifier.fillMaxWidth()

                )

                items.forEachIndexed { index, item ->
                    var saboresExpanded by remember(index, item.sabor) { mutableStateOf(false) }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ExposedDropdownMenuBox(
                                expanded = saboresExpanded,
                                onExpandedChange = { saboresExpanded = it }
                            ) {
                                OutlinedTextField(
                                    value = item.sabor,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Sabor #${index + 1}") },
                                    trailingIcon = {
                                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = saboresExpanded)
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .menuAnchor()
                                )
                                ExposedDropdownMenu(
                                    expanded = saboresExpanded,
                                    onDismissRequest = { saboresExpanded = false }
                                ) {
                                    SABORES_SORRENTINOS.forEach { sabor ->
                                        DropdownMenuItem(
                                            text = { Text(sabor) },
                                            onClick = {
                                                items[index] = item.copy(sabor = sabor)
                                                saboresExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = item.docenas,
                                onValueChange = { value -> items[index] = item.copy(docenas = value) },
                                label = { Text("Cantidad de docenas") },
                                modifier = Modifier.fillMaxWidth()
                            )

                            TextButton(
                                onClick = {
                                    if (items.size > 1) {
                                        items.removeAt(index)
                                    }
                                }
                            ) {
                                Text("Quitar sabor")
                            }
                        }
                    }
                }

                Button(onClick = { items.add(PedidoFormItem()) }) {
                    Text("Agregar sabor")
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onConfirm(
                    cliente,
                    items.map {
                        SaborCantidad(
                            sabor = it.sabor,
                            docenas = it.docenas.toIntOrNull() ?: 0
                        )
                    }
                )
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
