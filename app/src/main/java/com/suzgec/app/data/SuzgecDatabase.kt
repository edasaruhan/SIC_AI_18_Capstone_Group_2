package com.suzgec.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [NotificationRecord::class, BoxOpen::class, Correction::class, OrderPair::class],
    version = 7,
    exportSchema = true,
)
abstract class SuzgecDatabase : RoomDatabase() {
    abstract fun notificationDao(): NotificationDao

    companion object {
        @Volatile private var instance: SuzgecDatabase? = null

        fun get(context: Context): SuzgecDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    SuzgecDatabase::class.java,
                    "suzgec.db",
                )
                    // Yazılı migration'lar: M51'deki gerçek kullanım verisi silinmesin (destructive migration yok).
                    .addMigrations(*ALL_MIGRATIONS)
                    .build()
                    .also { instance = it }
            }
    }
}
