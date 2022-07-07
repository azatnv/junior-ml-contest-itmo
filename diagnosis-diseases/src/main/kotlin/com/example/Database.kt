package com.example

import com.example.plugins.GoogleUserInfo
import io.ktor.http.*
import org.ktorm.database.Database
import org.ktorm.dsl.*
import org.ktorm.entity.*
import org.ktorm.schema.*
import org.ktorm.support.postgresql.PostgreSqlDialect
import java.io.File
import java.time.LocalDateTime
import kotlin.io.path.*


val uploadDir = Path("upload")

val db = Database.connect(
    url = "jdbc:postgresql://localhost:5432/azatnv_db",
    user = "azatnv",
    password = "azatnv",
    driver = "org.postgresql.Driver",
    dialect = PostgreSqlDialect()
)
val Database.users get() = this.sequenceOf(Users)
val Database.records get() = this.sequenceOf(Records)

interface User : Entity<User> {
    val id: Int
    val idGoogle: String
    val email: String
    val password:  String
    val name:  String
    val givenName:  String
    val familyName:  String
    val picture:  String
}

interface Record : Entity<Record> {
    val id: Int
    val user: User

    val userRecordId: Int
    val name: String
    val mimeType: String
    val locationPath: String
    val uploadDate:  LocalDateTime
    val diagnosis:  String
    val probability:  Float
    val modelName:  String
}

object Users : Table<User>("t_user") {
    val id = int("id").primaryKey().bindTo { it.id }
    val idGoogle = varchar("id_google").bindTo { it.idGoogle }
    val email = varchar("email").bindTo { it.email }
    val password = varchar("password").bindTo { it.password }
    val name = varchar("name").bindTo { it.name }
    val givenName = varchar("given_name").bindTo { it.givenName }
    val familyName = varchar("family_name").bindTo { it.familyName }
    val picture = varchar("picture").bindTo { it.picture }
}

object Records : Table<Record>("t_record") {
    val id = int("id").primaryKey().bindTo { it.id }
    val userId = int("user_id").references(Users) { it.user }

    val userRecordId = int("user_record_id").bindTo { it.userRecordId }
    val name = varchar("name").bindTo { it.name }
    val mimeType = varchar("mime_type").bindTo { it.mimeType }
    val locationPath = varchar("location_path").bindTo { it.locationPath }
    val uploadDate = datetime("upload_date").bindTo { it.uploadDate }
    val diagnosis = varchar("diagnosis").bindTo { it.diagnosis }
    val probability = float("probability").bindTo { it.probability }
    val modelName = varchar("model_name").bindTo { it.modelName }
}

fun verifyOrAddGoogleUser(gUser: GoogleUserInfo) {
    val numberRecords = db
        .from(Users)
        .select(Users.email)
        .where { (Users.email eq gUser.email) }.totalRecords

    if (numberRecords == 0) {
        db.insert(Users) {
            set(it.idGoogle, gUser.id)
            set(it.email, gUser.email)
            set(it.name, gUser.name)
            set(it.givenName, gUser.givenName)
            set(it.familyName, gUser.familyName)
            set(it.picture, gUser.picture)
            set(it.password, null)
        }
    }
}
fun getUser(email: String): User? = db.users.find { it.email eq email }

fun addRecord(fileName: String, fileMIME: ContentType?, fileBytes: ByteArray, email: String) {
    val currentUser = db.users.find { it.email eq email }

    if (currentUser != null) {
        val maxUserRecordId = db.records
            .filter { it.userId eq currentUser.id }
            .aggregateColumns { max(it.userRecordId) }
        val nextUserRecordId = if (maxUserRecordId == null) 1 else maxUserRecordId + 1

        if (!uploadDir.exists())
            uploadDir.createDirectory()
        val userDir = Path("$uploadDir\\${currentUser.id}")
        if (!userDir.exists())
            userDir.createDirectory()

        val locationPath = Path(
            "$userDir\\$nextUserRecordId.${fileMIME?.contentSubtype}"
        )
        if (!locationPath.exists()) {
            locationPath.createFile().writeBytes(fileBytes)

            val uploadDate = LocalDateTime.now()
            db.insert(Records) {
                set(it.userId, currentUser.id)
                set(it.userRecordId, nextUserRecordId)
                set(it.name, fileName)
                set(it.mimeType, fileMIME.toString())
                set(it.locationPath, locationPath.absolutePathString())
                set(it.uploadDate, uploadDate)
            }

            val pb = ProcessBuilder(listOf(
                "D:\\Program\\miniconda3\\condabin\\conda.bat",
                "run", "python", ".\\script.py", locationPath.absolutePathString())
            ).directory(
                File("D:\\ai")
            )
            pb.start()
        }
    }
}

fun getUserRecords(email: String): ArrayList<Record> {
    val currentUser = db.users.find { it.email eq email }
    if (currentUser != null) {
        val records = db.records.filter { it.userId eq currentUser.id }.sortedBy { it.uploadDate.desc() }

        return records.toList() as ArrayList<Record>
    }
    return ArrayList()
}