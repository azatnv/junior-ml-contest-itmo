package com.example.plugins

import com.example.Record
import com.example.verifyOrAddGoogleUser
import com.example.getUser
import io.ktor.server.sessions.*
import io.ktor.server.auth.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.apache.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.client.request.*
import io.ktor.server.routing.*
import kotlinx.serialization.*
import kotlinx.serialization.json.Json

class TokenSession(val accessToken: String)

data class UserSession(
    val id: Int,
    val idGoogle: String,
    val email: String,
    val password:  String,
    val name:  String,
    val givenName:  String,
    val familyName:  String,
    val picture:  String)

@Serializable
data class GoogleUserInfo (
    val id: String,
    val name: String,
    val email: String,
    @SerialName("given_name") val givenName: String,
    @SerialName("family_name") val familyName: String,
    val picture: String)


val applicationHttpClient = HttpClient(Apache)

private val json = Json {
    ignoreUnknownKeys = true
    prettyPrint = true
}

fun Application.configureSecurity() {
    install(Sessions) {
        cookie<TokenSession>("TOKEN_SESSION")

        cookie<UserSession>("USER_SESSION")
        cookie<List<Record>>("USER_RECORDS")
    }
    
    install(Authentication) {
        oauth("auth-oauth-google") {
            urlProvider = { "http://localhost:80/callback" }
            providerLookup = {
                OAuthServerSettings.OAuth2ServerSettings(
                    name = "google",
                    authorizeUrl = "https://accounts.google.com/o/oauth2/auth",
                    accessTokenUrl = "https://accounts.google.com/o/oauth2/token",
                    requestMethod = HttpMethod.Post,
                    clientId = "832007472955-bp1pektbhs5n7fbdriunmqkoet9ponm6.apps.googleusercontent.com",
                    clientSecret = "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",
                    defaultScopes = listOf("https://www.googleapis.com/auth/userinfo.profile", "https://www.googleapis.com/auth/userinfo.email")
                )
            }
            client = applicationHttpClient
        }
    }

    routing {
        authenticate("auth-oauth-google") {
            get("login") {
                call.respondRedirect("/callback")
            }

            get("/callback") {
                val principal: OAuthAccessTokenResponse.OAuth2? = call.authentication.principal()
                call.sessions.set(TokenSession(principal?.accessToken.toString()))
                call.respondRedirect("/hello")
            }
        }

        get("/hello") {
            val tokenSession: TokenSession? = call.sessions.get()
            if (tokenSession != null) {
                val googleUserInfoJson : String = applicationHttpClient.get("https://www.googleapis.com/oauth2/v2/userinfo") {
                    headers {
                        append(HttpHeaders.Authorization, "Bearer ${tokenSession.accessToken}")
                    }
                }.body()

                val googleUserInfo: GoogleUserInfo = json.decodeFromString(googleUserInfoJson)
                verifyOrAddGoogleUser(googleUserInfo)

                val userInterface = getUser(googleUserInfo.email)

                if (userInterface != null) {
                    val user = UserSession(
                        id = userInterface.id,
                        idGoogle = userInterface.idGoogle,
                        email = userInterface.email,
                        password = userInterface.password,
                        name = userInterface.name,
                        givenName = userInterface.givenName,
                        familyName = userInterface.familyName,
                        picture = userInterface.picture
                    )
                    call.sessions.set(user)
                    call.respondRedirect("/me")
                } else call.respondRedirect("/login")
            } else call.respondRedirect("/login")
        }
    }
}