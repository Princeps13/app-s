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
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lucas.sorrentinos.data.ClienteEntity
import com.lucas.sorrentinos.data.PedidoEntity
import com.lucas.sorrentinos.data.SaborDocenas
import com.lucas.sorrentinos.domain.ClientePedidos
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
                    clientes = uiState.clientes,
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
                    topClientes = uiState.topClientes,
                    onSelectWeek = viewModel::onSelectSummaryWeek
                )

                TabDestination.AJUSTES -> AjustesTab(
                    costoActual = uiState.settings.costoDefaultPorDocena,
                    ventaActual = uiState.settings.ventaDefaultPorDocena,
                    clientes = uiState.clientes,
                    onSave = viewModel::saveSettings,
                    onCreateCliente = viewModel::createCliente,
                    onUpdateCliente = viewModel::updateCliente
                )
            }
        }
    }
}

@Composable
private fun PendientesTab(
    pedidos: List<PedidoEntity>,
    clientes: List<ClienteEntity>,
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
            availableClientes = clientes.map { it.nombre },
            initialCliente = clientes.firstOrNull()?.nombre.orEmpty(),
            initialItems = listOf(PedidoFormItem()),
            onDismiss = { showCreate = false },
            onConfirm = { cliente, items ->
                onCreate(cliente, items)
                showCreate = false
            }
        )
    }

    editPedido?.let { pedido ->
        val namesForEdit = (clientes.map { it.nombre } + pedido.clienteNombre).distinct()
        PedidoFormDialog(
            title = "Editar pedido #${pedido.id}",
            availableClientes = namesForEdit,
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
    topClientes: List<ClientePedidos>,
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
    var showClientes by remember { mutableStateOf(true) }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        item {
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
        }

        item {
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
        }

        item {
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
                            topSabores.forEachIndexed { index, sabor ->
                                Text("${index + 1}. ${sabor.detalle}: ${sabor.totalDocenas} docenas")
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Clientes que más pidieron", style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = { showClientes = !showClientes }) {
                            Text(if (showClientes) "Ocultar" else "Mostrar")
                        }
                    }

                    if (showClientes) {
                        if (topClientes.isEmpty()) {
                            Text("No hay clientes con pedidos para esta semana.")
                        } else {
                            topClientes.forEachIndexed { index, cliente ->
                                Text("${index + 1}. ${cliente.clienteNombre}: ${cliente.totalPedidos} pedidos (${cliente.totalDocenas} docenas)")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AjustesTab(
    costoActual: Double,
    ventaActual: Double,
    clientes: List<ClienteEntity>,
    onSave: (Double, Double) -> Unit,
    onCreateCliente: (String, String, String, String, String) -> Unit,
    onUpdateCliente: (Int, String, String, String, String, String) -> Unit
) {
    var costo by remember(costoActual) { mutableStateOf(costoActual.toString()) }
    var venta by remember(ventaActual) { mutableStateOf(ventaActual.toString()) }
    var showClienteDialog by remember { mutableStateOf(false) }
    var showClientesDialog by remember { mutableStateOf(false) }

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

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { showClienteDialog = true }) {
                Text("Registrar cliente")
            }
            TextButton(onClick = { showClientesDialog = true }) {
                Text("Ver clientes")
            }
        }
    }

    if (showClienteDialog) {
        ClienteFormDialog(
            onDismiss = { showClienteDialog = false },
            onConfirm = { nombre, calle, numero, entreCalle, telefono ->
                onCreateCliente(nombre, calle, numero, entreCalle, telefono)
                showClienteDialog = false
            }
        )
    }

    if (showClientesDialog) {
        ClientesListDialog(
            clientes = clientes,
            onDismiss = { showClientesDialog = false },
            onUpdateCliente = onUpdateCliente
        )
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
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PedidoFormDialog(
    title: String,
    availableClientes: List<String>,
    initialCliente: String,
    initialItems: List<PedidoFormItem>,
    onDismiss: () -> Unit,
    onConfirm: (String, List<SaborCantidad>) -> Unit
) {
    var cliente by remember { mutableStateOf(initialCliente) }
    var clientesExpanded by remember { mutableStateOf(false) }
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
                if (availableClientes.isEmpty()) {
                    Text(
                        "No hay clientes registrados. Registralos desde Ajustes > Registrar cliente.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                ExposedDropdownMenuBox(
                    expanded = clientesExpanded,
                    onExpandedChange = { clientesExpanded = it }
                ) {
                    OutlinedTextField(
                        value = cliente,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Cliente") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = clientesExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = clientesExpanded,
                        onDismissRequest = { clientesExpanded = false }
                    ) {
                        availableClientes.forEach { nombre ->
                            DropdownMenuItem(
                                text = { Text(nombre) },
                                onClick = {
                                    cliente = nombre
                                    clientesExpanded = false
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

@Composable
private fun ClienteFormDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, String, String) -> Unit,
    title: String = "Registrar cliente",
    initialNombre: String = "",
    initialCalle: String = "",
    initialNumero: String = "",
    initialEntreCalle: String = "",
    initialTelefono: String = "",
    confirmLabel: String = "Registrar"
) {
    var nombre by remember(initialNombre) { mutableStateOf(initialNombre) }
    var calle by remember(initialCalle) { mutableStateOf(initialCalle) }
    var numero by remember(initialNumero) { mutableStateOf(initialNumero) }
    var entreCalle by remember(initialEntreCalle) { mutableStateOf(initialEntreCalle) }
    var telefono by remember(initialTelefono) { mutableStateOf(initialTelefono) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = nombre,
                    onValueChange = { nombre = it },
                    label = { Text("Nombre *") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = calle,
                    onValueChange = { calle = it },
                    label = { Text("Calle") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = numero,
                    onValueChange = { numero = it },
                    label = { Text("Número") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = entreCalle,
                    onValueChange = { entreCalle = it },
                    label = { Text("Entre calles") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = telefono,
                    onValueChange = { telefono = it },
                    label = { Text("Teléfono") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                onConfirm(nombre, calle, numero, entreCalle, telefono)
            }) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
private fun ClientesListDialog(
    clientes: List<ClienteEntity>,
    onDismiss: () -> Unit,
    onUpdateCliente: (Int, String, String, String, String, String) -> Unit
) {
    var editingCliente by remember { mutableStateOf<ClienteEntity?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Clientes registrados") },
        text = {
            if (clientes.isEmpty()) {
                Text("Todavía no hay clientes registrados.")
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    clientes.forEach { cliente ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(cliente.nombre, fontWeight = FontWeight.Bold)
                                if (cliente.calle.isNotBlank() || cliente.numero.isNotBlank()) {
                                    Text("Dirección: ${cliente.calle} ${cliente.numero}".trim())
                                }
                                if (cliente.entreCalle.isNotBlank()) {
                                    Text("Entre calles: ${cliente.entreCalle}")
                                }
                                if (cliente.telefono.isNotBlank()) {
                                    Text("Teléfono: ${cliente.telefono}")
                                }
                                TextButton(onClick = { editingCliente = cliente }) {
                                    Text("Editar")
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        }
    )

    editingCliente?.let { cliente ->
        ClienteFormDialog(
            title = "Editar cliente",
            initialNombre = cliente.nombre,
            initialCalle = cliente.calle,
            initialNumero = cliente.numero,
            initialEntreCalle = cliente.entreCalle,
            initialTelefono = cliente.telefono,
            confirmLabel = "Guardar cambios",
            onDismiss = { editingCliente = null },
            onConfirm = { nombre, calle, numero, entreCalle, telefono ->
                onUpdateCliente(cliente.id, nombre, calle, numero, entreCalle, telefono)
                editingCliente = null
            }
        )
    }
}
