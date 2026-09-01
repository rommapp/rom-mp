package com.rommapp.rommp.data.remote

import com.rommapp.rommp.data.remote.dto.PaginatedResponse
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins when page following stops. Stopping too early silently truncates a
 * library; never stopping loops against the server forever, so both failure
 * modes are asserted through the offsets actually requested.
 */
class PaginationTest {

    private val requestedOffsets = mutableListOf<Int>()

    private fun page(items: List<String>, total: Int = 0) =
        PaginatedResponse(items = items, total = total)

    private suspend fun collect(
        pageSize: Int,
        startOffset: Int = 0,
        pages: List<PaginatedResponse<String>>,
    ): List<String> = fetchAllPages(pageSize, startOffset) { _, offset ->
        requestedOffsets += offset
        pages.getOrElse(requestedOffsets.size - 1) { page(emptyList()) }
    }

    @Test
    fun `stops after a page shorter than the page size`() = runTest {
        val items = collect(
            pageSize = 2,
            pages = listOf(page(listOf("a", "b")), page(listOf("c"))),
        )

        assertEquals(listOf("a", "b", "c"), items)
        assertEquals(listOf(0, 2), requestedOffsets)
    }

    @Test
    fun `stops once the reported total is reached without fetching an empty page`() = runTest {
        val items = collect(
            pageSize = 2,
            pages = listOf(page(listOf("a", "b"), total = 4), page(listOf("c", "d"), total = 4)),
        )

        assertEquals(listOf("a", "b", "c", "d"), items)
        assertEquals(listOf(0, 2), requestedOffsets)
    }

    @Test
    fun `returns nothing for an empty first page`() = runTest {
        val items = collect(pageSize = 50, pages = listOf(page(emptyList())))

        assertEquals(emptyList<String>(), items)
        assertEquals(listOf(0), requestedOffsets)
    }

    @Test
    fun `keeps paging when the total is reported as zero`() = runTest {
        val items = collect(
            pageSize = 2,
            pages = listOf(
                page(listOf("a", "b"), total = 0),
                page(listOf("c", "d"), total = 0),
                page(listOf("e"), total = 0),
            ),
        )

        assertEquals(listOf("a", "b", "c", "d", "e"), items)
        assertEquals(listOf(0, 2, 4), requestedOffsets)
    }

    @Test
    fun `starts at the requested offset and steps by the page size`() = runTest {
        val items = collect(
            pageSize = 3,
            startOffset = 10,
            pages = listOf(page(listOf("a", "b", "c")), page(listOf("d"))),
        )

        assertEquals(listOf("a", "b", "c", "d"), items)
        assertEquals(listOf(10, 13), requestedOffsets)
    }

    @Test
    fun `stops on a full page that already covers the total`() = runTest {
        val items = collect(
            pageSize = 2,
            pages = listOf(page(listOf("a", "b"), total = 2)),
        )

        assertEquals(listOf("a", "b"), items)
        assertEquals(listOf(0), requestedOffsets)
    }
}
