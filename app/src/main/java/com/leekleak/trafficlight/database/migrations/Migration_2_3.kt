package com.leekleak.trafficlight.database.migrations

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.leekleak.trafficlight.database.CryptoManager
import com.leekleak.trafficlight.database.DataPlan.Companion.NULL_SUBSCRIBER

val MIGRATION_2_3 = object : Migration(2, 3) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("""
            CREATE TABLE IF NOT EXISTS `DataPlan_new` (
                `hashedSubscriberID` TEXT NOT NULL, 
                `encryptedSubscriberID` TEXT NOT NULL, 
                `simIndex` INTEGER NOT NULL, 
                `carrierName` TEXT NOT NULL, 
                `dataMax` INTEGER NOT NULL, 
                `startDate` INTEGER NOT NULL, 
                `interval` TEXT NOT NULL, 
                `intervalMultiplier` INTEGER NOT NULL, 
                `excludedApps` TEXT NOT NULL, 
                `notification` INTEGER NOT NULL DEFAULT 0, 
                `liveNotification` INTEGER NOT NULL DEFAULT 0, 
                `uiBackground` INTEGER NOT NULL, 
                PRIMARY KEY(`hashedSubscriberID`)
            )
        """.trimIndent())

        data class OldDataPlan(
            val subscriberID: String,
            val simIndex: Int,
            val carrierName: String,
            val dataMax: Long,
            val startDate: Long,
            val interval: String,
            val intervalMultiplier: Int,
            val excludedApps: String,
            val uiBackground: Int
        )

        val rows = mutableListOf<OldDataPlan>()
        connection.prepare(
            """
            SELECT subscriberID, simIndex, carrierName, dataMax, startDate, 
                   interval, intervalMultiplier, excludedApps, uiBackground 
            FROM DataPlan
            """.trimIndent()
        ).use { statement ->
            while (statement.step()) {
                rows.add(
                    OldDataPlan(
                        subscriberID = statement.getText(0),
                        simIndex = statement.getLong(1).toInt(),
                        carrierName = statement.getText(2),
                        dataMax = statement.getLong(3),
                        startDate = statement.getLong(4),
                        interval = statement.getText(5),
                        intervalMultiplier = statement.getLong(6).toInt(),
                        excludedApps = statement.getText(7),
                        uiBackground = statement.getLong(8).toInt()
                    )
                )
            }
        }

        rows.forEach { old ->
            val finalID = if (old.subscriberID == "null") NULL_SUBSCRIBER else old.subscriberID
            val hashedID = CryptoManager.hashIdentifier(finalID)
            val encryptedID = CryptoManager.encrypt(finalID)

            connection.prepare(
                """
                INSERT INTO DataPlan_new (
                    hashedSubscriberID, encryptedSubscriberID, simIndex, carrierName, 
                    dataMax, startDate, interval, intervalMultiplier, 
                    excludedApps, notification, liveNotification, uiBackground
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 0, ?)
                """.trimIndent()
            ).use { statement ->
                statement.bindText(1, hashedID)
                statement.bindText(2, encryptedID)
                statement.bindLong(3, old.simIndex.toLong())
                statement.bindText(4, old.carrierName)
                statement.bindLong(5, old.dataMax)
                statement.bindLong(6, old.startDate)
                statement.bindText(7, old.interval)
                statement.bindLong(8, old.intervalMultiplier.toLong())
                statement.bindText(9, old.excludedApps)
                statement.bindLong(10, old.uiBackground.toLong())
                statement.step()
            }
        }

        connection.execSQL("DROP TABLE DataPlan")
        connection.execSQL("ALTER TABLE DataPlan_new RENAME TO DataPlan")
    }
}