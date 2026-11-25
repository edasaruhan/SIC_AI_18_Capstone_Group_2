package com.suzgec.app.ui

import androidx.compose.ui.graphics.Color
import com.suzgec.app.rules.Category

/** Kategoriler yalnız renkle değil, ad + simge + renkle gösterilir. */
val Category.symbol: String
    get() = when (this) {
        Category.KRITIK -> "‼"
        Category.ONEMLI -> "!"
        Category.NORMAL -> "●"
        Category.ONEMSIZ -> "○"
        Category.SILINENLER -> "✕"
        Category.DOKUNMA -> "⊘"
        Category.MODEL_BEKLIYOR -> "…"
    }

val Category.color: Color
    get() = when (this) {
        Category.KRITIK -> Color(0xFFC62828)
        Category.ONEMLI -> Color(0xFFEF6C00)
        Category.NORMAL -> Color(0xFF1565C0)
        Category.ONEMSIZ -> Color(0xFF607D8B)
        Category.SILINENLER -> Color(0xFF6D4C41)
        Category.DOKUNMA -> Color(0xFF2E7D32)
        Category.MODEL_BEKLIYOR -> Color(0xFF757575)
    }
