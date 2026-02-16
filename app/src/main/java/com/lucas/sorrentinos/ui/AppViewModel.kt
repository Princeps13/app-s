package com.lucas.sorrentinos.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lucas.sorrentinos.data.AppDatabase
import com.lucas.sorrentinos.data.PedidoEntity
import com.lucas.sorrentinos.data.SaborDocenas
import com.lucas.sorrentinos.data.SettingsEntity
import com.lucas.sorrentinos.domain.PedidoDraft
import com.lucas.sorrentinos.domain.PedidosRepository
import com.lucas.sorrentinos.domain.SaborCantidad
import com.lucas.sorrentinos.domain.WeekSummary
import com.lucas.sorrentinos.domain.WeekUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class UiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val settings: SettingsEntity = SettingsEntity(),
    val currentWeekId: String = WeekUtils.currentWeekRange().weekId,
    val selectedDeliveredWeekId: String = WeekUtils.currentWeekRange().weekId,
    val selectedSummaryWeekId: String = WeekUtils.currentWeekRange().weekId,
    val availableWeeks: List<String> = emptyList(),
    val weekLabels: Map<String, String> = emptyMap(),
    val pendingPedidos: List<PedidoEntity> = emptyList(),
    val deliveredPedidos: List<PedidoEntity> = emptyList(),
    val weekSummary: WeekSummary = WeekSummary(),
    val topSabores: List<SaborDocenas> = emptyList()
)

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = PedidosRepository(
        settingsDao = AppDatabase.getInstance(application).settingsDao(),
        pedidoDao = AppDatabase.getInstance(application).pedidoDao()
    )

    private val currentWeekId = WeekUtils.currentWeekRange().weekId
    private val loading = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)
    private val selectedDeliveredWeekId = MutableStateFlow(currentWeekId)
    private val selectedSummaryWeekId = MutableStateFlow(currentWeekId)

    @Suppress("UNCHECKED_CAST")
    val uiState: StateFlow<UiState> = combine(
        loading,
        error,
        repository.observeSettings(),
        repository.observeWeekIds(),
        repository.observePendingForWeek(currentWeekId),
        selectedDeliveredWeekId.flatMapLatest { repository.observeDeliveredByWeek(it) },
        selectedSummaryWeekId.flatMapLatest { repository.observeWeekSummary(it) },
        selectedSummaryWeekId.flatMapLatest { repository.observeTopSaboresByWeek(it) },
        selectedDeliveredWeekId,
        selectedSummaryWeekId
    ) { values ->
        val weekIds = values[3] as List<String>
        val deliveredWeek = values[8] as String
        val summaryWeek = values[9] as String
        val baseWeeks = listOf(currentWeekId)
        val available = (weekIds + baseWeeks).distinct().sortedDescending()

        UiState(
            isLoading = values[0] as Boolean,
            errorMessage = values[1] as String?,
            settings = values[2] as SettingsEntity,
            currentWeekId = currentWeekId,
            selectedDeliveredWeekId = if (deliveredWeek in available) deliveredWeek else currentWeekId,
            selectedSummaryWeekId = if (summaryWeek in available) summaryWeek else currentWeekId,
            availableWeeks = available,
            weekLabels = available.associateWith { WeekUtils.labelFromWeekId(it) },
            pendingPedidos = values[4] as List<PedidoEntity>,
            deliveredPedidos = values[5] as List<PedidoEntity>,
            weekSummary = values[6] as WeekSummary,
            topSabores = values[7] as List<SaborDocenas>
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    fun clearError() {
        error.value = null
    }

    fun onSelectDeliveredWeek(weekId: String) {
        selectedDeliveredWeekId.value = weekId
    }

    fun onSelectSummaryWeek(weekId: String) {
        selectedSummaryWeekId.value = weekId
    }

    fun saveSettings(costo: Double, venta: Double) {
        if (costo < 0 || venta < 0) {
            error.value = "Los valores de ajustes deben ser mayores o iguales a 0."
            return
        }
        runAction { repository.saveSettings(costo, venta) }
    }

    fun createPedido(clienteNombre: String, items: List<SaborCantidad>) {
        when {
            clienteNombre.isBlank() -> {
                error.value = "El nombre del cliente es obligatorio."
                return
            }

            items.isEmpty() -> {
                error.value = "Tenés que cargar al menos un sabor con cantidad."
                return
            }

            items.any { it.docenas <= 0 || it.sabor.isBlank() } -> {
                error.value = "Revisá los sabores: todos deben tener nombre y cantidad mayor a 0."
                return
            }

            uiState.value.settings.costoDefaultPorDocena < 0 || uiState.value.settings.ventaDefaultPorDocena < 0 -> {
                error.value = "Revisá ajustes: costo y venta deben ser mayores o iguales a 0."
                return
            }
        }
        runAction {
            repository.createPedido(
                PedidoDraft(
                    clienteNombre = clienteNombre,
                    items = items
                ),
                uiState.value.settings
            )
        }
    }

    fun markEntregado(id: Int) = runAction { repository.markEntregado(id) }

    fun cancelPedido(id: Int) = runAction { repository.cancelPedido(id) }

    fun updatePedido(id: Int, clienteNombre: String, items: List<SaborCantidad>) {
        when {
            clienteNombre.isBlank() -> {
                error.value = "El nombre del cliente es obligatorio."
                return
            }

            items.isEmpty() -> {
                error.value = "Tenés que cargar al menos un sabor con cantidad."
                return
            }

            items.any { it.docenas <= 0 || it.sabor.isBlank() } -> {
                error.value = "Revisá los sabores: todos deben tener nombre y cantidad mayor a 0."
                return
            }
        }
        runAction { repository.updatePedido(id, clienteNombre, items) }
    }

    private fun runAction(action: suspend () -> Unit) {
        viewModelScope.launch {
            loading.value = true
            try {
                action()
            } catch (e: Exception) {
                error.value = e.message ?: "Ocurrió un error inesperado."
            } finally {
                loading.value = false
            }
        }
    }
}
