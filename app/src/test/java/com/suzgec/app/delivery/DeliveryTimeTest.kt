package com.suzgec.app.delivery

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class DeliveryTimeTest {

    private val slots = listOf(9 * 60, 13 * 60, 19 * 60)

    private fun at(day: Int, h: Int, m: Int): Long = Calendar.getInstance().apply {
        set(2026, Calendar.NOVEMBER, day, h, m, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    @Test
    fun sonrakiTeslim() {
        assertEquals(at(26, 9, 0), Delivery.nextTrigger(at(26, 7, 30), slots))
        assertEquals(at(26, 13, 0), Delivery.nextTrigger(at(26, 9, 0), slots)) // tam saatte: bir sonraki
        assertEquals(at(26, 19, 0), Delivery.nextTrigger(at(26, 15, 0), slots))
        assertEquals(at(27, 9, 0), Delivery.nextTrigger(at(26, 21, 0), slots)) // ertesi gün
        // Ay sonu: 30 Kasım 23:59 → 1 Aralık 09:00
        val c = Calendar.getInstance().apply { timeInMillis = Delivery.nextTrigger(at(30, 23, 59), slots) }
        assertEquals(Calendar.DECEMBER, c.get(Calendar.MONTH))
        assertEquals(1, c.get(Calendar.DAY_OF_MONTH))
        assertEquals(9, c.get(Calendar.HOUR_OF_DAY))
    }

    @Test
    fun oncekiTeslim() {
        assertEquals(at(26, 13, 0), Delivery.previousTrigger(at(26, 15, 0), slots))
        assertEquals(at(26, 9, 0), Delivery.previousTrigger(at(26, 9, 0), slots))
        assertEquals(at(25, 19, 0), Delivery.previousTrigger(at(26, 8, 0), slots)) // önceki gün
    }
}
