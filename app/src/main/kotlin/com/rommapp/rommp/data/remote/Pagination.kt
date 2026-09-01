package com.rommapp.rommp.data.remote

import com.rommapp.rommp.data.remote.dto.PaginatedResponse

/**
 * Follows a limit/offset endpoint page by page until every item is collected:
 * stops on a short (or empty) page, or once the reported total is reached.
 */
internal suspend fun <T> fetchAllPages(
    pageSize: Int,
    startOffset: Int = 0,
    fetchPage: suspend (limit: Int, offset: Int) -> PaginatedResponse<T>,
): List<T> {
    val items = mutableListOf<T>()
    var offset = startOffset
    while (true) {
        val page = fetchPage(pageSize, offset)
        items += page.items
        val exhausted = page.items.size < pageSize ||
            (page.total > 0 && offset + page.items.size >= page.total)
        if (exhausted) return items
        offset += pageSize
    }
}
