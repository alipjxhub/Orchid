package com.eden.orchid.testhelpers

import com.eden.common.util.EdenUtils
import com.eden.orchid.api.generators.OrchidCollection
import com.eden.orchid.api.render.OrchidRenderer
import com.eden.orchid.api.theme.pages.OrchidPage
import com.eden.orchid.utilities.OrchidUtils
import org.apache.commons.io.IOUtils
import java.io.InputStream
import java.nio.charset.StandardCharsets
import javax.inject.Singleton

@Singleton
class TestRenderer : OrchidRenderer {

    val renderedPageMap = mutableMapOf<String, TestRenderedPage>()

    override fun render(page: OrchidPage, content: InputStream): Boolean {
        val outputPath = OrchidUtils.normalizePath(page.reference.path)
        val outputName: String?
        if (EdenUtils.isEmpty(OrchidUtils.normalizePath(page.reference.outputExtension))) {
            outputName = OrchidUtils.normalizePath(page.reference.fileName)
        } else {
            outputName =
                OrchidUtils.normalizePath(page.reference.fileName) + "." + OrchidUtils.normalizePath(page.reference.outputExtension)
        }

        val fullFilePath = "/" + OrchidUtils.normalizePath("$outputPath/$outputName")

        val outputFile = TestRenderedPage(fullFilePath, content, page)
        renderedPageMap[outputFile.path] = outputFile
        return true
    }

    data class TestRenderedPage(
        val path: String,
        private val contentStream: InputStream,
        val origin: OrchidPage
    ) {

        var evaluated: Boolean = false

        val content: String by lazy {
            IOUtils.toString(contentStream, StandardCharsets.UTF_8)
        }

        override fun toString(): String {
            return "TestRenderer.TestRenderedPage(path=" + this.path + ", origin=" + this.origin + ")"
        }
    }

    data class TestIndexedCollection(
        val origin: OrchidCollection<*>
    ) {
        var evaluated: Boolean = false

        val collectionType = origin.collectionType
        val collectionId = origin.collectionId

        override fun toString(): String {
            return "TestIndexedCollection(origin=$origin, evaluated=$evaluated, collectionType='$collectionType', collectionId=$collectionId)"
        }
    }
}
