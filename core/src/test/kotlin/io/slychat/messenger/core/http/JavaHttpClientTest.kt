package io.slychat.messenger.core.http

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@JsonInclude(JsonInclude.Include.NON_NULL)
data class Post(
    @JsonProperty("id") val id: Int?,
    @JsonProperty("userId") val userId: Int,
    @JsonProperty("title") val title: String,
    @JsonProperty("body") val body: String
)

class JavaHttpClientTest {
    private lateinit var client: HttpClient
    private var doOnlineTests = System.getProperty("keytap.tests.doOnline", "false").toBoolean()

    @Before
    fun before() {
        client = io.slychat.messenger.core.http.JavaHttpClient()
    }

    @Test
    fun `get should return a body on 200`() {
        Assume.assumeTrue(doOnlineTests)

        val response = client.get("http://www.google.com/", listOf())
        assertEquals(200, response.code)
        assertTrue(response.body.length > 0)
    }

    @Test
    fun `get should return a body on 404`() {
        Assume.assumeTrue(doOnlineTests)

        val response = client.get("http://www.google.com/dfafadfa", listOf())
        assertEquals(404, response.code)
        assertTrue(response.body.length > 0)
    }

    @Test
    fun `postJSON should property return a response`() {
        Assume.assumeTrue(doOnlineTests)

        val objectMapper = ObjectMapper()
        val post = Post(null, 1, "title", "body")
        val body = objectMapper.writeValueAsBytes(post)

        val response = client.postJSON("http://jsonplaceholder.typicode.com/posts", body, listOf())
        assertEquals(201, response.code)

        val returnedPost = objectMapper.readValue(response.body, Post::class.java)
        assertEquals(post.copy(id = 101), returnedPost)
    }

    //TODO check for debug mode
    @Test
    fun `get should ignore bad SSL certs when in debug mode`() {
        Assume.assumeTrue(doOnlineTests)

        val url = "https://wrong.host.badssl.com/"

        val client = io.slychat.messenger.core.http.JavaHttpClient()
        client.get(url, listOf())
    }
}