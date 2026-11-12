package com.example.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "study_sets")
data class StudySet(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val language: String, // "en" or "zh"
    val rawContent: String, // format: sữa = milk\n...
    val isPreset: Boolean = false,
    val lastModified: Long = System.currentTimeMillis(),
    val folderId: Long? = null
)

@Entity(tableName = "folders")
data class Folder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val lastModified: Long = System.currentTimeMillis()
)

@Entity(tableName = "starred_pairs")
data class StarredPair(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vi: String,
    val foreign: String,
    val language: String
)

object BuiltinLibraryCatalog {
    const val QUICK_VOCAB_FOLDER_NAME = "📌 Thư mục Từ vựng lưu nhanh"
    const val QUICK_VOCAB_SET_TITLE = "📌 Từ vựng lưu nhanh (Tổng hợp)"
    const val QUICK_NOTE_FOLDER_NAME = "📝 Thư mục Ghi chú & Mẹo nhớ"
    const val QUICK_NOTE_SET_TITLE = "📝 Sổ tay Ghi chú & Ngữ pháp"

    val PROTECTED_FOLDER_NAMES = setOf(
        "📘 Giáo trình Tiếng Trung - Quyển 1",
        "📗 Giáo trình Tiếng Trung - Quyển 2",
        "📙 Giáo trình Tiếng Trung - Quyển 3",
        "🖼️ Từ vựng theo Chủ đề (Sách Ảnh)",
        "🏭 1045 Câu Giao Tiếp Công Xưởng",
        "💬 1000 Câu Hỏi Đáp Thông Dụng",
        "🏆 Từ vựng HSK 1 - HSK 4",
        "🎓 Từ vựng HSK 5 - HSK 6",
        "👑 Từ vựng HSK Nâng cao (6+ & 7-9)",
        QUICK_VOCAB_FOLDER_NAME,
        QUICK_NOTE_FOLDER_NAME
    )

    fun isProtectedFolder(folderName: String): Boolean {
        val clean = folderName.trim()
        return PROTECTED_FOLDER_NAMES.contains(clean) ||
            clean.startsWith("📘 Giáo trình") ||
            clean.startsWith("📗 Giáo trình") ||
            clean.startsWith("📙 Giáo trình") ||
            clean.startsWith("🖼️ Từ vựng theo Chủ đề") ||
            clean.startsWith("🏭 1045 Câu") ||
            clean.startsWith("💬 1000 Câu") ||
            clean.startsWith("🏆 Từ vựng HSK") ||
            clean.startsWith("🎓 Từ vựng HSK") ||
            clean.startsWith("👑 Từ vựng HSK")
    }

    fun isProtectedStudySet(studySet: StudySet, folders: List<Folder>): Boolean {
        if (studySet.isPreset) return true
        val folder = folders.find { it.id == studySet.folderId }
        if (folder != null && isProtectedFolder(folder.name) &&
            folder.name != QUICK_VOCAB_FOLDER_NAME &&
            folder.name != QUICK_NOTE_FOLDER_NAME
        ) {
            return true
        }
        val t = studySet.title.trim()
        return t.startsWith("Q1 - Bài") ||
            t.startsWith("Q2 - Bài") ||
            t.startsWith("Q3 - Bài") ||
            t.startsWith("Chủ đề ") ||
            t.startsWith("Công xưởng - Bài") ||
            t.startsWith("Hỏi đáp - Bài") ||
            t.startsWith("HSK ")
    }
}

@Dao
interface StudySetDao {
    @Query("SELECT * FROM study_sets ORDER BY isPreset DESC, lastModified DESC")
    fun getAllStudySetsFlow(): Flow<List<StudySet>>

    @Query("SELECT * FROM study_sets WHERE id = :id LIMIT 1")
    suspend fun getStudySetById(id: Long): StudySet?

    @Query("SELECT COUNT(*) FROM study_sets")
    suspend fun getCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStudySet(studySet: StudySet): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStudySets(studySets: List<StudySet>)

    @Query("SELECT * FROM study_sets")
    suspend fun getStudySetsSync(): List<StudySet>

    @Update
    suspend fun updateStudySet(studySet: StudySet)

    @Delete
    suspend fun deleteStudySet(studySet: StudySet)

    @Query("DELETE FROM study_sets WHERE id = :id")
    suspend fun deleteById(id: Long)

    // Folder support
    @Query("SELECT * FROM folders ORDER BY id ASC")
    fun getAllFoldersFlow(): Flow<List<Folder>>

    @Query("SELECT * FROM folders")
    suspend fun getFoldersSync(): List<Folder>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: Folder): Long

    @Update
    suspend fun updateFolder(folder: Folder)

    @Delete
    suspend fun deleteFolder(folder: Folder)

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun deleteFolderById(id: Long)

    @Query("UPDATE study_sets SET folderId = :folderId WHERE id = :setId")
    suspend fun updateStudySetFolder(setId: Long, folderId: Long?)

    @Query("UPDATE study_sets SET folderId = NULL WHERE folderId = :folderId")
    suspend fun clearFolderIdFromStudySets(folderId: Long)

    // Star/Favorite support
    @Query("SELECT * FROM starred_pairs ORDER BY id DESC")
    fun getAllStarredPairsFlow(): Flow<List<StarredPair>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStarredPair(pair: StarredPair): Long

    @Query("DELETE FROM starred_pairs WHERE vi = :vi AND `foreign` = :foreign")
    suspend fun deleteStarredPair(vi: String, foreign: String)
}

@Database(entities = [StudySet::class, Folder::class, StarredPair::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun studySetDao(): StudySetDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "translation_practice_db"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

data class TranslationPair(
    val vi: String,
    val foreign: String
)

class StudySetRepository(private val studySetDao: StudySetDao) {
    val allStudySets: Flow<List<StudySet>> = studySetDao.getAllStudySetsFlow()
    val allFolders: Flow<List<Folder>> = studySetDao.getAllFoldersFlow()
    val allStarredPairs: Flow<List<StarredPair>> = studySetDao.getAllStarredPairsFlow()

    suspend fun getById(id: Long): StudySet? = studySetDao.getStudySetById(id)

    suspend fun insert(studySet: StudySet): Long = studySetDao.insertStudySet(studySet)

    suspend fun update(studySet: StudySet) = studySetDao.updateStudySet(studySet)

    suspend fun delete(studySet: StudySet) = studySetDao.deleteStudySet(studySet)

    suspend fun deleteById(id: Long) = studySetDao.deleteById(id)

    // Folder operations
    suspend fun insertFolder(folder: Folder): Long = studySetDao.insertFolder(folder)
    suspend fun updateFolder(folder: Folder) = studySetDao.updateFolder(folder)
    suspend fun deleteFolder(folder: Folder) {
        studySetDao.clearFolderIdFromStudySets(folder.id)
        studySetDao.deleteFolder(folder)
    }
    suspend fun deleteFolderById(id: Long) = studySetDao.deleteFolderById(id)
    suspend fun assignStudySetToFolder(setId: Long, folderId: Long?) = studySetDao.updateStudySetFolder(setId, folderId)

    // Starred operations
    suspend fun insertStarredPair(pair: StarredPair): Long = studySetDao.insertStarredPair(pair)
    suspend fun deleteStarredPair(vi: String, foreign: String) = studySetDao.deleteStarredPair(vi, foreign)

    suspend fun prePopulatePresets(context: Context? = null) {
        if (studySetDao.getCount() == 0) {
            val enPresets = listOf(
                StudySet(
                    title = "💬 Giao tiếp cơ bản (EN)",
                    language = "en",
                    isPreset = true,
                    rawContent = """
                        Chào buổi sáng. = Good morning.
                        Hôm nay trời đẹp. = Today is a beautiful day.
                        Bạn có khỏe không? = How are you?
                        Tôi rất vui được gặp bạn. = I am very glad to meet you.
                        Cảm ơn bạn rất nhiều. = Thank you very much.
                        Chúc ngủ ngon. = Good night.
                        Hẹn gặp lại bạn sau. = See you again later.
                        Tôi có thể giúp gì cho bạn? = How can I help you?
                    """.trimIndent()
                ),
                StudySet(
                    title = "🎓 Từ vựng IELTS (EN)",
                    language = "en",
                    isPreset = true,
                    rawContent = """
                        tích lũy = accumulate
                        đáng kể = significant
                        giải quyết = address
                        thuận lợi = beneficial
                        thực thi = implement
                        thúc đẩy = promote
                        cản trở = hinder
                        ngăn chặn = prevent
                        nhận thức = perceive
                        ổn định = stable
                    """.trimIndent()
                ),
                StudySet(
                    title = "💡 Thành ngữ tiếng Anh (EN)",
                    language = "en",
                    isPreset = true,
                    rawContent = """
                        Một mũi tên trúng hai đích = Kill two birds with one stone
                        Dễ như ăn bánh = A piece of cake
                        Mưa như trút nước = Rain cats and dogs
                        Cực kỳ hiếm khi = Once in a blue moon
                        Để xem chuyện gì xảy ra = Play it by ear
                        Không phán xét qua vẻ bề ngoài = Don't judge a book by its cover
                        Đi ngủ = Hit the sack
                        Tập trung làm việc = Hit the nail on the head
                    """.trimIndent()
                )
            )

            val zhPresets = listOf(
                StudySet(
                    title = "💬 Giao tiếp cơ bản (ZH)",
                    language = "zh",
                    isPreset = true,
                    rawContent = """
                        Chào buổi sáng. = 早上好。 (Zǎoshang hǎo.)
                        Hôm nay thời tiết rất đẹp. = 今天天气很好。 (Jīntiān tiānqì hěn hǎo.)
                        Bạn khỏe không? = 你好吗？ (Nǐ hǎo ma?)
                        Rất vui được gặp bạn. = 很高兴认识你。 (Hěn gāoxìng rènshi nǐ.)
                        Cảm ơn bạn rất nhiều. = 非常感谢你。 (Fēicháng gǎnxiè nǐ.)
                        Chúc ngủ ngon. = 晚安。 (Wǎn'ān.)
                        Hẹn gặp lại sau. = 再见。 (Zàijiàn.)
                        Tôi có thể giúp gì cho bạn? = 我能帮你什么吗？ (Wǒ néng bāng nǐ shénme ma?)
                    """.trimIndent()
                ),
                StudySet(
                    title = "🎓 Từ vựng HSK (ZH)",
                    language = "zh",
                    isPreset = true,
                    rawContent = """
                        thời gian = 时间 (shíjiān)
                        chuẩn bị = 准备 (zhǔnbèi)
                        hy vọng = 希望 (xīwàng)
                        quyết định = 决定 (juédìng)
                        ảnh hưởng = 影响 (yǐngxiǎng)
                        đặc biệt = 特别 (tèbié)
                        sức khỏe = 健康 (jiànkāng)
                        môi trường = 环境 (huánjìng)
                        thành tích = 成绩 (chéngjì)
                        nguy hiểm = 危险 (wēixiǎn)
                    """.trimIndent()
                ),
                StudySet(
                    title = "💡 Thành ngữ tiếng Trung (ZH)",
                    language = "zh",
                    isPreset = true,
                    rawContent = """
                        Nhập gia tùy tục = 入乡随俗 (Rù xiāng suí sú)
                        Vạn sự khởi đầu nan = 万事开头难 (Wàn shì kāi tóu nán)
                        Có chí thì nên = 有志者事竟成 (Yǒu zhì zhě shì jìng chéng)
                        Đồng cam cộng khổ = 同甘共苦 (Tóng gān gòng kǔ)
                        Hữu danh vô thực = 有名无实 (Yǒu míng wú shí)
                        Mắt thấy tai nghe = 眼见为实 (Yǎn jiàn wéi shí)
                        Một mũi tên trúng hai đích = 一箭双雕 (Yī jiàn shuāng diāo)
                    """.trimIndent()
                )
            )

            studySetDao.insertStudySets(enPresets + zhPresets)
        }

        if (context != null) {
            ensureBuiltinLibraryLoaded(context, forceReload = false)
            ensureQuickSaveFoldersExist()
        }
    }

    suspend fun ensureQuickSaveFoldersExist() {
        val currentFolders = studySetDao.getFoldersSync().associateBy { it.name }.toMutableMap()
        val existingSets = studySetDao.getStudySetsSync()
        val now = System.currentTimeMillis()

        val vocabFolderId = currentFolders[BuiltinLibraryCatalog.QUICK_VOCAB_FOLDER_NAME]?.id ?: run {
            studySetDao.insertFolder(Folder(name = BuiltinLibraryCatalog.QUICK_VOCAB_FOLDER_NAME, lastModified = now + 1000L))
        }
        val noteFolderId = currentFolders[BuiltinLibraryCatalog.QUICK_NOTE_FOLDER_NAME]?.id ?: run {
            studySetDao.insertFolder(Folder(name = BuiltinLibraryCatalog.QUICK_NOTE_FOLDER_NAME, lastModified = now + 2000L))
        }

        if (existingSets.none { it.folderId == vocabFolderId }) {
            studySetDao.insertStudySet(
                StudySet(
                    title = BuiltinLibraryCatalog.QUICK_VOCAB_SET_TITLE,
                    language = "zh",
                    rawContent = "Bảo hiểm = 保险 (Bǎo xiǎn)\nPhỏng vấn = 面试 (Miàn shì)\nSơ yếu lý lịch = 履历表 (Lǚ lì biǎo)",
                    isPreset = false,
                    lastModified = now + 1000L,
                    folderId = vocabFolderId
                )
            )
        }

        if (existingSets.none { it.folderId == noteFolderId }) {
            studySetDao.insertStudySet(
                StudySet(
                    title = BuiltinLibraryCatalog.QUICK_NOTE_SET_TITLE,
                    language = "zh",
                    rawContent = "[Ghi chú: Cần ôn tập] Công ty có đóng bảo hiểm cho nhân viên không? = 公司会给员工上保险吗？ (Gōngsī huì gěi yuángōng shàng bǎoxiǎn ma?)",
                    isPreset = false,
                    lastModified = now + 2000L,
                    folderId = noteFolderId
                )
            )
        }
    }

    suspend fun ensureBuiltinLibraryLoaded(context: Context, forceReload: Boolean = false): Int {
        return try {
            val prefs = context.getSharedPreferences("StudyStatePrefs", Context.MODE_PRIVATE)
            val alreadyLoaded = prefs.getBoolean("builtin_library_v1_loaded", false)
            val existingFolders = studySetDao.getFoldersSync()
            if (alreadyLoaded && !forceReload && existingFolders.size >= 5) {
                return 0
            }

            val jsonText = java.util.zip.GZIPInputStream(context.assets.open("builtin_library.bin"))
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }

            val root = org.json.JSONObject(jsonText)
            val foldersArr = root.getJSONArray("folders")
            val currentFolders = studySetDao.getFoldersSync().associateBy { it.name }.toMutableMap()
            val existingSetTitles = studySetDao.getStudySetsSync().map { "${it.folderId}:${it.title}" }.toMutableSet()

            var insertedCount = 0
            val now = System.currentTimeMillis()

            for (i in 0 until foldersArr.length()) {
                val fObj = foldersArr.getJSONObject(i)
                val folderName = fObj.getString("name")
                val folderId = currentFolders[folderName]?.id ?: run {
                    val newId = studySetDao.insertFolder(Folder(name = folderName, lastModified = now - i * 1000L))
                    currentFolders[folderName] = Folder(id = newId, name = folderName)
                    newId
                }

                val setsArr = fObj.getJSONArray("sets")
                val batch = ArrayList<StudySet>(setsArr.length())
                for (j in 0 until setsArr.length()) {
                    val sObj = setsArr.getJSONObject(j)
                    val title = sObj.getString("title")
                    val rawContent = sObj.getString("rawContent")
                    val key = "$folderId:$title"
                    if (forceReload || !existingSetTitles.contains(key)) {
                        batch.add(
                            StudySet(
                                title = title,
                                language = "zh",
                                rawContent = rawContent,
                                isPreset = false,
                                lastModified = now - (i * 100000L) + (setsArr.length() - j) * 10L,
                                folderId = folderId
                            )
                        )
                        existingSetTitles.add(key)
                    }
                }
                if (batch.isNotEmpty()) {
                    studySetDao.insertStudySets(batch)
                    insertedCount += batch.size
                }
            }

            prefs.edit().putBoolean("builtin_library_v1_loaded", true).apply()
            insertedCount
        } catch (e: Exception) {
            android.util.Log.e("StudySetRepository", "Failed to load builtin_library.bin", e)
            0
        }
    }
}
