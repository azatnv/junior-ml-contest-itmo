package com.example.plugins

import com.example.Record
import com.example.addRecord
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import java.io.File

fun Application.configureRouting() {
    routing {
        static {
            files("src/main/resources")
            default("src/main/resources/index.html")
        }

        get("/") {
            call.respondFile(File("src/main/resources/index.html"))
        }

        get("/loginForm") {
            val user: UserSession? = call.sessions.get()
            if (user != null)
                call.respondRedirect("/me")
            else
                call.respondFile(File("src/main/resources/login.html"))
        }

        post("/uploadFile") {
            val multipartData = call.receiveMultipart()

            multipartData.forEachPart { part ->
                when (part) {
                    is PartData.FileItem -> {
                        val fileName = part.originalFileName as String
                        val fileMIME = part.contentType
                        val fileBytes = part.streamProvider().readBytes()

                        val email = call.sessions.get<UserSession>()?.email
                        if (email != null)
                            addRecord(fileName, fileMIME, fileBytes, email)
                    }
                    else -> {}
                }
            }

            call.respondRedirect("/me")
        }

        get("/logout") {
            call.sessions.clear<TokenSession>()
            call.sessions.clear<UserSession>()
            call.sessions.clear<List<Record>>()
            call.respondFile(File("src/main/resources/index.html"))
        }
    }
}
