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

    @Update
    suspend fun updateStudySet(studySet: StudySet)

    @Delete
    suspend fun deleteStudySet(studySet: StudySet)

    @Query("DELETE FROM study_sets WHERE id = :id")
    suspend fun deleteById(id: Long)

    // Folder support
    @Query("SELECT * FROM folders ORDER BY lastModified DESC")
    fun getAllFoldersFlow(): Flow<List<Folder>>

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

    suspend fun prePopulatePresets() {
        if (studySetDao.getCount() > 0) return

        // 1. English Presets
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

        // 2. Chinese Presets
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

        enPresets.forEach { studySetDao.insertStudySet(it) }
        zhPresets.forEach { studySetDao.insertStudySet(it) }
    }
}
