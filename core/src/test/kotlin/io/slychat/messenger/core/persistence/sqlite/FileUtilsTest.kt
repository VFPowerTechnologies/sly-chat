package io.slychat.messenger.core.persistence.sqlite

import io.slychat.messenger.testutils.desc
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.util.*

class FileUtilsTest {
    private val fileUtils = FileUtils()

    private fun pathComponents(path: String): List<Pair<String, String>> {
        val components = ArrayList<Pair<String, String>>()
        fileUtils.withPathComponents(path) { parent, sub ->
            components.add(parent to sub)
        }

        return components
    }

    @Test
    fun `withPathComponents should handle the root directory`() {
        assertThat(pathComponents("/")).desc("Should return the empty list") {
            isEmpty()
        }
    }

    @Test
    fun `withPathComponents should handle a single level subdir`() {
        assertThat(pathComponents("/a")).desc("Should return the list of path combinations") {
            containsOnly("/" to "a")
        }
    }

    @Test
    fun `withPathComponents should handle multiple subdirs`() {
        val expected = listOf(
            "/" to "a",
            "/a" to "b",
            "/a/b" to "c",
            "/a/b/c" to "d"
        )

        assertThat(pathComponents("/a/b/c/d")).desc("Should return the list of path combinations") {
            containsOnlyElementsOf(expected)
        }
    }
}