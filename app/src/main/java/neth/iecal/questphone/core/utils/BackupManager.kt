package neth.iecal.questphone.core.utils

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import neth.iecal.questphone.backed.repositories.AppWidgetConfig
import neth.iecal.questphone.backed.repositories.AppWidgetConfigDao
import neth.iecal.questphone.backed.repositories.QuestRepository
import neth.iecal.questphone.backed.repositories.StatsRepository
import neth.iecal.questphone.data.CommonQuestInfo
import neth.iecal.questphone.data.StatsInfo
import nethical.questphone.data.UserInfo

@Serializable
data class AppWidgetConfigBackup(
    val id: String,
    val widgetId: Int,
    val height: Int,
    val width: Int? = null,
    val borderless: Boolean = false,
    val background: Boolean = true,
    val themeColors: Boolean = true,
    val order: Int
)

@Serializable
data class BackupData(
    val version: Int = 1,
    val userInfo: UserInfo?,
    val quests: List<CommonQuestInfo>,
    val stats: List<StatsInfo>,
    val widgets: List<AppWidgetConfigBackup>
)

object BackupManager {
    private const val TAG = "BackupManager"

    private val jsonSerializer = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun createBackup(
        context: Context,
        userRepository: neth.iecal.questphone.backed.repositories.UserRepository,
        questRepository: QuestRepository,
        statsRepository: StatsRepository,
        widgetConfigDao: AppWidgetConfigDao
    ): String {
        val userInfo = userRepository.userInfo
        val quests = questRepository.getAllQuests().first()
        val stats = statsRepository.getAllStatsForUser().first()
        val widgets = widgetConfigDao.getAllConfigs().map {
            AppWidgetConfigBackup(
                id = it.id,
                widgetId = it.widgetId,
                height = it.height,
                width = it.width,
                borderless = it.borderless,
                background = it.background,
                themeColors = it.themeColors,
                order = it.order
            )
        }

        val backupData = BackupData(
            userInfo = userInfo,
            quests = quests,
            stats = stats,
            widgets = widgets
        )

        return jsonSerializer.encodeToString(backupData)
    }

    suspend fun restoreBackup(
        context: Context,
        backupJson: String,
        userRepository: neth.iecal.questphone.backed.repositories.UserRepository,
        questRepository: QuestRepository,
        statsRepository: StatsRepository,
        widgetConfigDao: AppWidgetConfigDao
    ): Boolean {
        return try {
            val backupData = jsonSerializer.decodeFromString<BackupData>(backupJson)

            // 1. Clear current databases
            questRepository.clearAll()
            statsRepository.deleteAll()
            widgetConfigDao.deleteAllConfigs()

            // 2. Restore User Info
            backupData.userInfo?.let {
                userRepository.userInfo = it
                userRepository.coinsState.value = it.coins
                userRepository.currentStreakState.value = it.streak.currentStreak
                userRepository.activeBoostsState.value = it.active_boosts
                userRepository.saveUserInfo(isSetLastUpdated = false)
            }

            // 3. Restore Quests
            questRepository.upsertAll(backupData.quests)

            // 4. Restore Stats
            backupData.stats.forEach { stat ->
                statsRepository.upsertStats(stat)
            }

            // 5. Restore Widgets
            backupData.widgets.forEach { widget ->
                widgetConfigDao.insertConfig(
                    AppWidgetConfig(
                        id = widget.id,
                        widgetId = widget.widgetId,
                        height = widget.height,
                        width = widget.width,
                        borderless = widget.borderless,
                        background = widget.background,
                        themeColors = widget.themeColors,
                        order = widget.order
                    )
                )
            }

            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore backup", e)
            false
        }
    }
}
