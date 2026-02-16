package com.lucas.sorrentinos.domain

data class SaborCantidad(
    val sabor: String,
    val docenas: Int
)

object PedidoDetalleCodec {
    private const val ENTRY_SEPARATOR = "||"
    private const val VALUE_SEPARATOR = "::"

    fun encode(items: List<SaborCantidad>): String {
        return items
            .mapNotNull { item ->
                val sabor = item.sabor.trim()
                if (sabor.isBlank() || item.docenas <= 0) {
                    null
                } else {
                    "${sabor.replace(VALUE_SEPARATOR, "").replace(ENTRY_SEPARATOR, "")}$VALUE_SEPARATOR${item.docenas}"
                }
            }
            .joinToString(ENTRY_SEPARATOR)
    }

    fun decode(raw: String): List<SaborCantidad> {
        if (raw.isBlank()) return emptyList()

        if (!raw.contains(VALUE_SEPARATOR)) {
            return listOf(SaborCantidad(raw.trim(), 1)).filter { it.sabor.isNotBlank() }
        }

        return raw
            .split(ENTRY_SEPARATOR)
            .mapNotNull { part ->
                val pieces = part.split(VALUE_SEPARATOR)
                if (pieces.size != 2) return@mapNotNull null
                val sabor = pieces[0].trim()
                val docenas = pieces[1].trim().toIntOrNull() ?: return@mapNotNull null
                if (sabor.isBlank() || docenas <= 0) null else SaborCantidad(sabor, docenas)
            }
    }

    fun toDisplayText(raw: String): String {
        val decoded = decode(raw)
        if (decoded.isEmpty()) return raw
        return decoded.joinToString(" · ") { "${it.sabor} (${it.docenas})" }
    }
}
