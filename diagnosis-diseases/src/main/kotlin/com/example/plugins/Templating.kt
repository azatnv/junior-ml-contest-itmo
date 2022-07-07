package com.example.plugins

import com.example.getUserRecords
import com.github.mustachejava.DefaultMustacheFactory
import io.ktor.http.*
import io.ktor.server.mustache.Mustache
import io.ktor.server.mustache.MustacheContent
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import java.io.File
import java.time.format.DateTimeFormatter

fun Application.configureTemplating() {
    install(Mustache) {
        mustacheFactory = DefaultMustacheFactory(".")
    }

    routing {
        get("/me") {
            val user: UserSession? = call.sessions.get()
            if (user != null) {
                val userRecordsList = getUserRecords(user.email)
                val userRecordsListHTML = ArrayList<String>(userRecordsList.size)
                for ((i, r) in userRecordsList.withIndex()) {
                    val mimeIcon = if (r.mimeType == "image/jpeg")
                        "images/icons/jpeg.png" else "images/icons/wav.png"

                    val statusIcon =
                        when (r.diagnosis) {
                            "" -> "images/icons/process.png"
                            "Здоровый" -> "images/icons/green_icon.svg"
                            else -> "images/icons/red-icon.jpg"
                        }

                    val html = """
                        <li class="list-group-item d-flex justify-content-between align-content-center">
                            <div class="d-flex flex-row">
                                <img 	src="$mimeIcon" 
                                        width="40" height="40" />
                                <div class="ml-2">
                                    <a href="/files?index=$i" style="color: whitesmoke;">${r.name}</a>
                                    <div class="about">
                                        <span id="datetime">${r.uploadDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))}</span>
                                    </div>
                                </div>
                            </div>
                            <div class="check">
                                <img 	src="$statusIcon" 
                                        width="15" height="15" />
                            </div>
                        </li>
                    """.trimIndent()
                    userRecordsListHTML.add(i, html)
                }

                val m = MustacheContent("me.hbs", mapOf(
                    "user" to user,
                    "listFilesHTML" to userRecordsListHTML,
                    "preview" to true
                ))
                call.respond(m)
            } else
                call.respondFile(File("src/main/resources/index.html"))
        }

        get("/files") {
            val index = call.request.queryParameters["index"]?.toInt()
            val user: UserSession? = call.sessions.get()
            if (user != null && index != null) {
                val userRecordsList = getUserRecords(user.email)
                val userRecordsListHTML = ArrayList<String>(userRecordsList.size)

                for ((i, r) in userRecordsList.withIndex()) {
                    val mimeIcon = if (r.mimeType == "image/jpeg")
                        "images/icons/jpeg.png" else "images/icons/wav.png"

                    val statusIcon =
                        when (r.diagnosis) {
                            "" -> "images/icons/process.png"
                            "Здоровый" -> "images/icons/green_icon.svg"
                            else -> "images/icons/red-icon.jpg"
                        }
                    val backColor = if (i == index) "#5e92c3" else "#805ec3"
                    val html = """
                        <li class="list-group-item d-flex justify-content-between align-content-center" style="background: $backColor;">
                            <div class="d-flex flex-row">
                                <img 	src="$mimeIcon" 
                                        width="40" height="40" />
                                <div class="ml-2">
                                    <a href="/files?index=$i" style="color: whitesmoke;">${r.name}</a>
                                    <div class="about">
                                        <span id="datetime">${r.uploadDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))}</span>
                                    </div>
                                </div>
                            </div>
                            <div class="check">
                                <img href="/files?index=$i" src="$statusIcon" 
                                        width="15" height="15" />
                            </div>
                        </li>
                    """.trimIndent()
                    userRecordsListHTML.add(i, html)
                }

                val record = userRecordsList[index]
                val mimeIcon = if (record.mimeType == "image/jpeg")
                    "images/icons/jpeg.png" else "images/icons/wav.png"
                val recordHTML = """
                        <li class="list-group-item d-flex justify-content-between align-content-center" style="background: #5e92c3;">
                            <div class="d-flex flex-row">
                                <img 	src="$mimeIcon" 
                                        width="40" height="40" />
                                <div class="ml-2">
                                    <a style="color: whitesmoke;">${record.name}</a>
                                    <div class="about">
                                        <span id="datetime">${record.uploadDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))}</span>
                                    </div>
                                </div>
                            </div>
                        </li>
                    """.trimIndent()

                val media = File(record.locationPath)
                val ct = ContentType.parse(record.mimeType)
                val fileName = "${record.userRecordId}.${ct.contentSubtype}"
                val dir = File("src/main/resources/media")
                if (dir.exists())
                    for (file in dir.listFiles()!!)
                        if (!file.isDirectory)
                            file.delete()
                media.copyTo(File("src/main/resources/media/$fileName"))

                val mediaHTML =
                    if (record.mimeType == "image/jpeg")
                    """
                                    <img src="/media/$fileName" style="height: 30vh;"
                                    alt="Изображение">
                                """.trimIndent()
                else
                    """
                                        <audio controls="controls">
                                            <source src="/media/$fileName" type="audio/wav">
                                            Ваш браузер не поддерживает элемент 
                                            <code>audio</code>.
                                    </audio>
                                """.trimIndent()

                val m = MustacheContent(
                    "me.hbs", mapOf(
                        "user" to user,
                        "listFilesHTML" to userRecordsListHTML,
                        "preview" to false,
                        "diagnosis" to if (record.diagnosis == "") "... обработка ..." else record.diagnosis,
                        "probability" to (record.probability * 100).toInt().toString() + "%",
                        "recordHTML" to recordHTML,
                        "mediaHTML" to mediaHTML
                    )
                )
                call.respond(m)
            } else
                call.respondFile(File("src/main/resources/index.html"))
        }
    }
}
