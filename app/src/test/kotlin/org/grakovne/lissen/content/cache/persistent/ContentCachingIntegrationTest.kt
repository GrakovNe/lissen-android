package org.grakovne.lissen.content.cache.persistent

import android.content.Context
import android.net.Uri
import com.squareup.moshi.Moshi
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.grakovne.lissen.channel.audiobookshelf.AudiobookshelfHostProvider
import org.grakovne.lissen.channel.audiobookshelf.Host
import org.grakovne.lissen.channel.audiobookshelf.common.api.AudioBookShelfApiService
import org.grakovne.lissen.channel.audiobookshelf.common.api.AudioBookshelfRepository
import org.grakovne.lissen.channel.audiobookshelf.common.api.ConditionalCache
import org.grakovne.lissen.channel.audiobookshelf.common.api.RequestHeadersProvider
import org.grakovne.lissen.channel.audiobookshelf.common.api.library.AudioBookshelfLibrarySyncService
import org.grakovne.lissen.channel.audiobookshelf.common.client.AudiobookshelfApiClient
import org.grakovne.lissen.channel.audiobookshelf.common.converter.BookmarkItemResponseConverter
import org.grakovne.lissen.channel.audiobookshelf.common.converter.BookmarksResponseConverter
import org.grakovne.lissen.channel.audiobookshelf.common.converter.ConnectionInfoResponseConverter
import org.grakovne.lissen.channel.audiobookshelf.common.converter.LibraryAuthorsResponseConverter
import org.grakovne.lissen.channel.audiobookshelf.common.converter.LibraryListResponseConverter
import org.grakovne.lissen.channel.audiobookshelf.common.converter.LibraryPageResponseConverter
import org.grakovne.lissen.channel.audiobookshelf.common.converter.LibraryResponseConverter
import org.grakovne.lissen.channel.audiobookshelf.common.converter.LoginResponseConverter
import org.grakovne.lissen.channel.audiobookshelf.common.converter.PlaybackSessionResponseConverter
import org.grakovne.lissen.channel.audiobookshelf.common.converter.RecentListeningResponseConverter
import org.grakovne.lissen.channel.audiobookshelf.library.LibraryAudiobookshelfChannel
import org.grakovne.lissen.channel.audiobookshelf.library.converter.BookResponseConverter
import org.grakovne.lissen.channel.audiobookshelf.library.converter.LibraryFilteringRequestConverter
import org.grakovne.lissen.channel.audiobookshelf.library.converter.LibraryOrderingRequestConverter
import org.grakovne.lissen.channel.audiobookshelf.library.converter.LibrarySearchItemsConverter
import org.grakovne.lissen.content.cache.persistent.api.CachedBookRepository
import org.grakovne.lissen.content.cache.persistent.api.CachedLibraryRepository
import org.grakovne.lissen.domain.AllItemsDownloadOption
import org.grakovne.lissen.domain.BookFile
import org.grakovne.lissen.domain.CacheStatus
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.Library
import org.grakovne.lissen.domain.LibraryType
import org.grakovne.lissen.domain.PlayingChapter
import org.grakovne.lissen.persistence.preferences.ConnectionPreferences
import org.grakovne.lissen.persistence.preferences.DownloadPreferences
import org.grakovne.lissen.persistence.preferences.LibraryPreferences
import org.grakovne.lissen.persistence.preferences.SessionPreferences
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.File

/**
 * Drives [ContentCachingManager] against a real Audiobookshelf stack:
 * Retrofit + Moshi + OkHttp on top of [MockWebServer], the real
 * [LibraryAudiobookshelfChannel], the real [OfflineBookStorageProperties] file layout
 * and the real chapter-to-file resolution. Only the Room-backed repositories are mocked,
 * so downloading media, writing it to hashed cache paths, resuming already cached
 * chapters and dropping a chapter from the disk are exercised end to end.
 */
class ContentCachingIntegrationTest {
  private val server = MockWebServer()

  @TempDir
  lateinit var storageDir: File

  private val hostProvider = mockk<AudiobookshelfHostProvider>()
  private val session = mockk<SessionPreferences>(relaxed = true)
  private val connection = mockk<ConnectionPreferences>(relaxed = true)
  private val requestHeadersProvider = mockk<RequestHeadersProvider>()
  private val loginResponseConverter = mockk<LoginResponseConverter>(relaxed = true)

  private val bookRepository = mockk<CachedBookRepository>(relaxed = true)
  private val libraryRepository = mockk<CachedLibraryRepository>(relaxed = true)
  private val downloadPreferences =
    mockk<DownloadPreferences> {
      every { getDownloadStoragePath() } returns null
    }

  private lateinit var context: Context
  private lateinit var properties: OfflineBookStorageProperties
  private lateinit var channel: LibraryAudiobookshelfChannel
  private lateinit var manager: ContentCachingManager

  private val chapters =
    listOf(
      PlayingChapter(true, null, 10.0, 0.0, 10.0, "Chapter 1", "c0"),
      PlayingChapter(true, null, 10.0, 10.0, 20.0, "Chapter 2", "c1"),
    )

  private val book =
    DetailedItem(
      id = "book",
      title = "Book",
      subtitle = null,
      author = null,
      narrator = null,
      publisher = null,
      series = emptyList(),
      year = null,
      abstract = null,
      files =
        listOf(
          BookFile("f0", "f0.mp3", 10.0, 100, "audio/mpeg"),
          BookFile("f1", "f1.mp3", 10.0, 100, "audio/mpeg"),
        ),
      chapters = chapters,
      progress = null,
      libraryId = "lib1",
      libraryType = LibraryType.LIBRARY,
      localProvided = false,
      createdAt = 0L,
      updatedAt = 0L,
    )

  @BeforeEach
  fun setUp() {
    server.start()
    stubUriParsing()

    context =
      mockk {
        every { getExternalFilesDir(OfflineBookStorageProperties.MEDIA_CACHE_FOLDER) } returns storageDir
        every { cacheDir } returns File(storageDir.parentFile, "cache")
        every { getSystemService(any<Class<Any>>()) } returns null
      }

    every { hostProvider.provideHost() } returns Host.external(server.url("").toString())
    every { session.getAccessToken() } returns "token"
    every { connection.getSslBypass() } returns false
    every { connection.getClientCertAlias() } returns null
    every { requestHeadersProvider.fetchRequestHeaders() } returns emptyList()

    val service =
      AudioBookShelfApiService(
        context = context,
        hostProvider = hostProvider,
        session = session,
        connection = connection,
        requestHeadersProvider = requestHeadersProvider,
        loginResponseConverter = loginResponseConverter,
        conditionalCache = ConditionalCache(),
      )

    val http = OkHttpClient.Builder().build()

    val api =
      Retrofit
        .Builder()
        .baseUrl(server.url(""))
        .client(http)
        .addConverterFactory(MoshiConverterFactory.create(Moshi.Builder().build()))
        .build()
        .create(AudiobookshelfApiClient::class.java)

    service.clientFactory = { AudioBookShelfApiService.ChannelClients(api = api, http = http) }

    val repository = AudioBookshelfRepository(context, service)

    channel =
      LibraryAudiobookshelfChannel(
        hostProvider = hostProvider,
        repository = repository,
        recentListeningResponseConverter = RecentListeningResponseConverter(),
        preferences = mockk<LibraryPreferences>(relaxed = true),
        syncService = mockk<AudioBookshelfLibrarySyncService>(relaxed = true),
        sessionResponseConverter = PlaybackSessionResponseConverter(),
        libraryListResponseConverter = LibraryListResponseConverter(),
        libraryResponseConverter = LibraryResponseConverter(),
        connectionInfoResponseConverter = ConnectionInfoResponseConverter(),
        bookmarksResponseConverter = BookmarksResponseConverter(BookmarkItemResponseConverter()),
        bookmarkItemResponseConverter = BookmarkItemResponseConverter(),
        libraryOrderingRequestConverter = LibraryOrderingRequestConverter(),
        libraryFilteringRequestConverter = LibraryFilteringRequestConverter(),
        libraryPageResponseConverter = LibraryPageResponseConverter(),
        libraryAuthorsResponseConverter = LibraryAuthorsResponseConverter(),
        bookResponseConverter = BookResponseConverter(),
        librarySearchItemsConverter = LibrarySearchItemsConverter(),
      )

    properties = OfflineBookStorageProperties(context, downloadPreferences)

    manager =
      ContentCachingManager(
        context = context,
        bookRepository = bookRepository,
        libraryRepository = libraryRepository,
        properties = properties,
        registry = CachingSessionRegistry(),
        progress = ContentCachingProgress(),
      )
  }

  @AfterEach
  fun tearDown() {
    runCatching { server.close() }
    unmockkAll()
  }

  @Test
  fun `downloads all chapters into the hashed cache and records the book`() =
    runBlocking {
      coEvery { bookRepository.fetchBook("book") } returns null
      server.enqueue(audio("bytes-f0"))
      server.enqueue(audio("bytes-f1"))
      server.enqueue(notFound())
      server.enqueue(libraries())

      val states = manager.cacheMediaItem(book, AllItemsDownloadOption, channel, 0.0).toList()

      assertEquals(CacheState(CacheStatus.Completed), states.last())
      assertTrue(states.contains(CacheState(CacheStatus.Caching)))

      assertEquals("bytes-f0", properties.provideMediaCachePatch("book", "f0").readText())
      assertEquals("bytes-f1", properties.provideMediaCachePatch("book", "f1").readText())
      assertTrue(properties.provideBookCache("book").walkTopDown().none { it.name.endsWith(".tmp") })

      assertEquals("/api/items/book/file/f0", server.takeRequest().url.encodedPath)
      assertEquals("/api/items/book/file/f1", server.takeRequest().url.encodedPath)
      assertEquals("/api/items/book/cover", server.takeRequest().url.encodedPath)
      assertEquals("/api/libraries", server.takeRequest().url.encodedPath)

      coVerify { bookRepository.cacheBook(book, chapters, emptyList()) }
      coVerify { libraryRepository.cacheLibraries(listOf(Library("lib1", "Audio", LibraryType.LIBRARY))) }
    }

  @Test
  fun `serves already cached chapters without re-downloading them`() =
    runBlocking {
      coEvery { bookRepository.fetchBook("book") } returns
        book.copy(chapters = chapters.map { it.copy(available = it.id == "c0") })
      server.enqueue(audio("bytes-f1"))
      server.enqueue(notFound())
      server.enqueue(libraries())

      val states = manager.cacheMediaItem(book, AllItemsDownloadOption, channel, 0.0).toList()

      assertEquals(CacheState(CacheStatus.Completed), states.last())
      assertEquals(3, server.requestCount)
      assertFalse(properties.provideMediaCachePatch("book", "f0").exists())
      assertEquals("bytes-f1", properties.provideMediaCachePatch("book", "f1").readText())
    }

  @Test
  fun `reports an error and keeps no media when the download fails`() =
    runBlocking {
      coEvery { bookRepository.fetchBook("book") } returns null
      server.enqueue(MockResponse.Builder().code(500).build())

      val states = manager.cacheMediaItem(book, AllItemsDownloadOption, channel, 0.0).toList()

      assertEquals(CacheState(CacheStatus.Error), states.last())
      assertFalse(properties.provideMediaCachePatch("book", "f0").exists())
      assertTrue(properties.provideBookCache("book").walkTopDown().none { it.name.endsWith(".tmp") })
      coVerify(exactly = 0) { bookRepository.cacheBook(any(), any(), emptyList()) }
    }

  @Test
  fun `dropping a chapter removes its file from the disk and keeps the rest`() =
    runBlocking {
      coEvery { bookRepository.fetchBook("book") } returns null
      server.enqueue(audio("bytes-f0"))
      server.enqueue(audio("bytes-f1"))
      server.enqueue(notFound())
      server.enqueue(libraries())
      manager.cacheMediaItem(book, AllItemsDownloadOption, channel, 0.0).toList()

      manager.dropCache(book, chapters.last())

      assertEquals("bytes-f0", properties.provideMediaCachePatch("book", "f0").readText())
      assertFalse(properties.provideMediaCachePatch("book", "f1").exists())
      coVerify { bookRepository.cacheBook(book, emptyList(), listOf(chapters.last())) }
    }

  private fun audio(content: String) =
    MockResponse
      .Builder()
      .code(200)
      .body(content)
      .build()

  private fun notFound() = MockResponse.Builder().code(404).build()

  private fun libraries() =
    MockResponse
      .Builder()
      .code(200)
      .body("""{"libraries":[{"id":"lib1","name":"Audio","mediaType":"book","displayOrder":1}]}""")
      .addHeader("Content-Type", "application/json")
      .build()

  private fun stubUriParsing() {
    mockkStatic(Uri::class)

    every { Uri.parse(any()) } answers {
      val parts = mutableListOf(firstArg<String>().trimEnd('/'))

      val uri = mockk<Uri>()
      val builder = mockk<Uri.Builder>()
      every { uri.buildUpon() } returns builder
      every { builder.appendPath(any()) } answers {
        parts.add("/" + firstArg<String>())
        builder
      }
      every { builder.build() } answers {
        val built = mockk<Uri>()
        every { built.toString() } returns parts.joinToString("")
        built
      }

      uri
    }
  }
}
