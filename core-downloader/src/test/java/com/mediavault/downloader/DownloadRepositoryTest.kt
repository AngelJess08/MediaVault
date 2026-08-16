package com.mediavault.downloader

import android.content.Context
import com.mediavault.downloader.model.MediaInfo
import com.mediavault.downloader.model.Platform
import com.mediavault.downloader.queue.DownloadQueue
import com.mediavault.downloader.repository.DownloadRepository
import com.mediavault.downloader.ytdlp.YtDlpExecutor
import com.mediavault.storage.db.dao.DownloadDao
import com.mediavault.storage.db.dao.QueueDao
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class DownloadRepositoryTest {

    private val context = mockk<Context>(relaxed = true)
    private val ytDlpExecutor = mockk<YtDlpExecutor>()
    private val downloadQueue = mockk<DownloadQueue>(relaxed = true)
    private val queueDao = mockk<QueueDao>(relaxed = true)
    private val downloadDao = mockk<DownloadDao>(relaxed = true)
    private lateinit var repository: DownloadRepository

    @Before
    fun setup() {
        repository = DownloadRepository(context, ytDlpExecutor, downloadQueue, queueDao, downloadDao)
    }

    @Test
    fun fetchMediaInfo_returnsMediaInfo() = runBlocking {
        val expectedUrl = "https://youtube.com/watch?v=123"
        val expectedInfo = MediaInfo(
            url = expectedUrl,
            title = "Test Video",
            description = "Desc",
            thumbnailUrl = null,
            duration = 10L,
            platform = Platform.YOUTUBE,
            uploader = "Test Author",
            uploadDate = "2026-08-15",
            isPlaylist = false,
            playlistItems = null,
            formats = emptyList(),
            subtitles = emptyList(),
            isLive = false
        )
        
        coEvery { ytDlpExecutor.extractInfo(expectedUrl, null) } returns expectedInfo

        val result = repository.fetchMediaInfo(expectedUrl)

        assertEquals(expectedInfo, result)
        coVerify { ytDlpExecutor.extractInfo(expectedUrl, null) }
    }
}
