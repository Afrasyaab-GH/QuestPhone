package neth.iecal.questphone.core.utils

import android.content.Context
import android.util.Log
import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
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

    private fun encrypt(plainText: String, password: CharArray): String {
        val salt = ByteArray(16).apply { SecureRandom().nextBytes(this) }
        val iv = ByteArray(16).apply { SecureRandom().nextBytes(this) }

        val spec = PBEKeySpec(password, salt, 10000, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val secretKey = factory.generateSecret(spec)
        val secretKeySpec = SecretKeySpec(secretKey.encoded, "AES")

        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, IvParameterSpec(iv))
        val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

        // Format: salt (16 bytes) + iv (16 bytes) + ciphertext
        val combined = ByteArray(salt.size + iv.size + cipherText.size)
        System.arraycopy(salt, 0, combined, 0, salt.size)
        System.arraycopy(iv, 0, combined, salt.size, iv.size)
        System.arraycopy(cipherText, 0, combined, salt.size + iv.size, cipherText.size)

        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    private fun decrypt(combinedBase64: String, password: CharArray): String {
        val combined = Base64.decode(combinedBase64, Base64.DEFAULT)
        val salt = ByteArray(16)
        val iv = ByteArray(16)
        val cipherText = ByteArray(combined.size - salt.size - iv.size)

        System.arraycopy(combined, 0, salt, 0, salt.size)
        System.arraycopy(combined, salt.size, iv, 0, iv.size)
        System.arraycopy(combined, salt.size + iv.size, cipherText, 0, cipherText.size)

        val spec = PBEKeySpec(password, salt, 10000, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val secretKey = factory.generateSecret(spec)
        val secretKeySpec = SecretKeySpec(secretKey.encoded, "AES")

        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, IvParameterSpec(iv))
        val decryptedBytes = cipher.doFinal(cipherText)

        return String(decryptedBytes, Charsets.UTF_8)
    }

    suspend fun createBackup(
        context: Context,
        userRepository: neth.iecal.questphone.backed.repositories.UserRepository,
        questRepository: QuestRepository,
        statsRepository: StatsRepository,
        widgetConfigDao: AppWidgetConfigDao,
        password: String? = null
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

        val plainJson = jsonSerializer.encodeToString(backupData)
        return if (!password.isNullOrEmpty()) {
            encrypt(plainJson, password.toCharArray())
        } else {
            plainJson
        }
    }

    suspend fun restoreBackup(
        context: Context,
        backupJsonOrCiphertext: String,
        userRepository: neth.iecal.questphone.backed.repositories.UserRepository,
        questRepository: QuestRepository,
        statsRepository: StatsRepository,
        widgetConfigDao: AppWidgetConfigDao,
        password: String? = null
    ): Boolean {
        return try {
            val isEncrypted = !backupJsonOrCiphertext.trim().startsWith("{")
            val backupJson = if (isEncrypted) {
                if (password.isNullOrEmpty()) {
                    Log.e(TAG, "Backup is encrypted but no password was provided.")
                    return false
                }
                decrypt(backupJsonOrCiphertext, password.toCharArray())
            } else {
                backupJsonOrCiphertext
            }

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
