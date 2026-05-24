package com.engreader.app.ui.main

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Surface
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.engreader.app.R
import com.engreader.app.ai.AiProvider
import com.engreader.app.model.AnnotationRecord
import com.engreader.app.model.AnnotationType
import com.engreader.app.model.BookRecord
import com.engreader.app.model.ChapterContent
import com.engreader.app.model.ParagraphSegment
import com.engreader.app.model.ReaderMode
import com.engreader.app.model.RepeatAnnotationMode
import com.engreader.app.model.ShelfSortMode
import com.engreader.app.model.ShelfViewMode
import com.engreader.app.model.ThemeMode
import com.engreader.app.theme.EpubReaderTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
  pendingImportUri: MutableStateFlow<Uri?>,
  onImportConsumed: () -> Unit,
  modifier: Modifier = Modifier,
  viewModel: MainScreenViewModel = viewModel(),
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val incomingUri by pendingImportUri.collectAsStateWithLifecycle()
  val snackbarHostState = remember { SnackbarHostState() }

  var showApiDialog by remember { mutableStateOf(false) }
  var selectedProvider by remember { mutableStateOf(AiProvider.DEEPSEEK) }
  var apiKeyInput by remember { mutableStateOf("") }
  var readerChromeVisible by rememberSaveable(state.screen, state.readerState?.book?.id) { mutableStateOf(false) }
  val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
  val coroutineScope = rememberCoroutineScope()

  val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
    if (uri != null) viewModel.importFromUri(uri)
  }
  val exportBackupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
    if (uri != null) viewModel.exportBackup(uri)
  }
  val restoreBackupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
    if (uri != null) viewModel.restoreBackup(uri)
  }

  LaunchedEffect(incomingUri) {
    incomingUri?.let {
      viewModel.importFromUri(it)
      onImportConsumed()
    }
  }

  LaunchedEffect(state.transientMessage) {
    state.transientMessage?.let {
      snackbarHostState.showSnackbar(it)
      viewModel.clearTransientMessage()
    }
  }

  BackHandler(enabled = state.screen == AppScreen.Reader) {
    if (readerChromeVisible) {
      readerChromeVisible = false
    } else {
      viewModel.backToShelf()
    }
  }

  EpubReaderTheme(themeMode = state.settings.themeMode) {
    ImmersiveSystemBars(enabled = state.screen == AppScreen.Reader && !readerChromeVisible)

    val isShelf = state.screen == AppScreen.Shelf
    ModalNavigationDrawer(
      drawerState = drawerState,
      gesturesEnabled = isShelf,
      drawerContent = {
        ModalDrawerSheet {
          Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(16.dp),
          )
          HorizontalDivider()
          TextButton(onClick = {
            viewModel.toggleShelfViewMode()
          }, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            Text(if (state.settings.shelfViewMode == ShelfViewMode.GRID) stringResource(R.string.action_list) else stringResource(R.string.action_grid))
          }
          TextButton(onClick = {
            viewModel.cycleSortMode()
          }, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            Text("Sort: ${sortLabel(state.settings.shelfSortMode)}")
          }
          TextButton(onClick = {
            viewModel.cycleThemeMode()
          }, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            Text("Theme: ${themeLabel(state.settings.themeMode)}")
          }
          HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
          TextButton(onClick = {
            showApiDialog = true
          }, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            Text(stringResource(R.string.action_api_settings))
          }
          TextButton(onClick = {
            exportBackupLauncher.launch("engreader_backup.zip")
          }, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            Text(stringResource(R.string.action_export_backup))
          }
          TextButton(onClick = {
            restoreBackupLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
          }, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            Text(stringResource(R.string.action_restore_backup))
          }
        }
      }
    ) {
    Scaffold(
      modifier = modifier,
      contentWindowInsets = WindowInsets(0, 0, 0, 0),
      snackbarHost = { SnackbarHost(snackbarHostState) },
      floatingActionButton = {
        if (isShelf) {
          FloatingActionButton(onClick = {
            importLauncher.launch(arrayOf("application/epub+zip", "application/octet-stream"))
          }) {
            Text("+", style = MaterialTheme.typography.headlineSmall)
          }
        }
      },
      topBar = {
        when (state.screen) {
          AppScreen.Shelf -> {
            TopAppBar(
              title = { },
              navigationIcon = {
                IconButton(onClick = {
                  coroutineScope.launch { drawerState.open() }
                }) {
                  Text("☰", style = MaterialTheme.typography.headlineSmall)
                }
              },
            )
          }

          AppScreen.Reader -> {
            if (readerChromeVisible) {
              TopAppBar(
                title = { Text(state.readerState?.book?.title ?: stringResource(R.string.title_reader), maxLines = 1) },
                navigationIcon = {
                  TextButton(onClick = { viewModel.backToShelf() }) {
                    Text("‹ ${stringResource(R.string.action_bookshelf)}")
                  }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                  containerColor = MaterialTheme.colorScheme.surface,
                  titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
                actions = {
                  TextButton(onClick = { viewModel.cycleThemeMode() }) { Text(themeLabel(state.settings.themeMode)) }
                  TextButton(onClick = { viewModel.addBookmark() }) { Text(stringResource(R.string.action_bookmark)) }
                  TextButton(onClick = { readerChromeVisible = false }) { Text(stringResource(R.string.action_hide_controls)) }
                },
              )
            }
          }
        }
      },
    ) { innerPadding ->
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding)
          .padding(horizontal = 12.dp)
      ) {
        when (state.screen) {
          AppScreen.Shelf -> ShelfContent(state.books, state.settings.shelfViewMode, state.progressByBook, viewModel::openBook, viewModel::deleteBook)
          AppScreen.Reader -> {
            state.readerState?.let { reader ->
              ReaderContent(
                state = reader,
                settings = state.settings,
                chromeVisible = readerChromeVisible,
                scrollToChapterIndex = state.scrollToChapterIndex,
                onChapterChange = viewModel::setChapter,
                onChapterScrolledTo = viewModel::onChapterScrolledTo,
                onClearScrollTarget = viewModel::clearScrollTarget,
                onPrevChapter = viewModel::prevChapter,
                onNextChapter = viewModel::nextChapter,
                onParagraphChange = viewModel::setCurrentParagraph,
                onRemoveBookmark = viewModel::removeBookmark,
                onSearch = viewModel::searchInCurrentBook,
                onSearchResultClick = viewModel::jumpToSearchResult,
                onDecreaseFont = viewModel::decreaseFont,
                onIncreaseFont = viewModel::increaseFont,
                onDecreaseLineHeight = viewModel::decreaseLineHeight,
                onIncreaseLineHeight = viewModel::increaseLineHeight,
                onDecreaseParagraphSpacing = viewModel::decreaseParagraphSpacing,
                onIncreaseParagraphSpacing = viewModel::increaseParagraphSpacing,
                onDecreaseMargin = viewModel::decreaseMargin,
                onIncreaseMargin = viewModel::increaseMargin,
                onWordTap = viewModel::translateWordAt,
                onSentenceLongPress = viewModel::translateSentenceAt,
                onRemoveAnnotation = viewModel::removeAnnotation,
                onToggleAnnotations = viewModel::toggleAnnotationVisibility,
                onCycleRepeatMode = viewModel::cycleRepeatAnnotationMode,
                onToggleChrome = { readerChromeVisible = !readerChromeVisible },
                justifyText = state.settings.justifyText,
                onToggleJustify = viewModel::toggleJustify,
              )
            }
          }
        }
        if (state.isLoading) {
          CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
      }
    }
    }
  }

  if (showApiDialog) {
    AlertDialog(
      onDismissRequest = { showApiDialog = false },
      confirmButton = {
        TextButton(onClick = {
          viewModel.saveApiKey(selectedProvider, apiKeyInput)
          apiKeyInput = ""
          showApiDialog = false
        }) {
          Text(stringResource(R.string.action_save))
        }
      },
      dismissButton = {
        TextButton(onClick = { showApiDialog = false }) { Text(stringResource(R.string.action_close)) }
      },
      title = { Text(stringResource(R.string.action_api_settings)) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${stringResource(R.string.label_provider)}:")
            TextButton(onClick = { selectedProvider = nextProvider(selectedProvider) }) { Text(selectedProvider.displayName) }
          }
          OutlinedTextField(
            value = apiKeyInput,
            onValueChange = { apiKeyInput = it },
            label = { Text(stringResource(R.string.label_api_key)) },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
          )
        }
      },
    )
  }
}

@Composable
private fun BookCover(book: BookRecord, modifier: Modifier = Modifier) {
  val coverBitmap = remember(book.coverPath) {
    if (book.coverPath.isNotBlank()) {
      runCatching { BitmapFactory.decodeFile(book.coverPath) }.getOrNull()
    } else null
  }

  if (coverBitmap != null) {
    Image(
      bitmap = coverBitmap.asImageBitmap(),
      contentDescription = book.title,
      modifier = modifier,
    )
  } else {
    Box(
      modifier = modifier
        .background(MaterialTheme.colorScheme.surfaceVariant),
      contentAlignment = Alignment.Center,
    ) {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
          text = "📖",
          style = MaterialTheme.typography.headlineLarge,
        )
        Text(
          text = book.title.take(20),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 2,
          textAlign = TextAlign.Center,
          modifier = Modifier.padding(horizontal = 8.dp),
        )
      }
    }
  }
}

@Composable
private fun progressLabel(book: BookRecord, progress: com.engreader.app.model.ReadingProgressRecord?): String {
  if (progress == null) return stringResource(R.string.label_new)
  if (book.totalChapters <= 0) return "Ch. ${progress.chapterIndex + 1}"
  val pct = ((progress.chapterIndex + 1).toFloat() / book.totalChapters * 100).toInt().coerceIn(0, 100)
  return "${pct}%"
}

@Composable
private fun sortLabel(mode: ShelfSortMode): String {
  return when (mode) {
    ShelfSortMode.RECENT -> stringResource(R.string.sort_recent)
    ShelfSortMode.IMPORTED -> stringResource(R.string.sort_imported)
    ShelfSortMode.TITLE -> stringResource(R.string.sort_title)
  }
}

@Composable
private fun themeLabel(mode: ThemeMode): String {
  return when (mode) {
    ThemeMode.SYSTEM -> stringResource(R.string.theme_system)
    ThemeMode.DAY -> stringResource(R.string.theme_day)
    ThemeMode.NIGHT -> stringResource(R.string.theme_night)
  }
}

@Composable
private fun ImmersiveSystemBars(enabled: Boolean) {
  val view = LocalView.current
  DisposableEffect(enabled, view) {
    val activity = view.context as? android.app.Activity
    val window = activity?.window
    if (window != null) {
      WindowCompat.setDecorFitsSystemWindows(window, false)
      val controller = WindowCompat.getInsetsController(window, view)
      if (enabled) {
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
      } else {
        controller.show(WindowInsetsCompat.Type.systemBars())
      }
    }
    onDispose {
      if (window != null) {
        WindowCompat.getInsetsController(window, view).show(WindowInsetsCompat.Type.systemBars())
      }
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShelfContent(
  books: List<BookRecord>,
  viewMode: ShelfViewMode,
  progressByBook: Map<String, com.engreader.app.model.ReadingProgressRecord>,
  onOpenBook: (BookRecord) -> Unit,
  onDeleteBook: (String) -> Unit,
) {
  if (books.isEmpty()) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      Text(stringResource(R.string.hint_empty_books))
    }
    return
  }

  when (viewMode) {
    ShelfViewMode.GRID -> {
      LazyVerticalGrid(columns = GridCells.Adaptive(160.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(books, key = { it.id }) { book ->
          Card(modifier = Modifier.fillMaxWidth().clickable { onOpenBook(book) }) {
            Column {
              BookCover(book, Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)))
              Column(Modifier.padding(12.dp)) {
                Text(book.title, fontWeight = FontWeight.SemiBold, maxLines = 2)
                Spacer(Modifier.height(4.dp))
                Text(progressLabel(book, progressByBook[book.id]), style = MaterialTheme.typography.bodySmall)
                if (book.preRenderProgress < 1f && book.preRenderProgress > 0f) {
                  Spacer(Modifier.height(4.dp))
                  LinearProgressIndicator(
                    progress = { book.preRenderProgress },
                    modifier = Modifier.fillMaxWidth(),
                  )
                  Text(
                    "${(book.preRenderProgress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                  )
                }
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = { onDeleteBook(book.id) }) { Text(stringResource(R.string.action_delete)) }
              }
            }
          }
        }
      }
    }

    ShelfViewMode.LIST -> {
      LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(books, key = { it.id }) { book ->
          Card(modifier = Modifier.fillMaxWidth().clickable { onOpenBook(book) }) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
              BookCover(book, Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)))
              Spacer(Modifier.size(12.dp))
              Column(Modifier.weight(1f)) {
                Text(book.title, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(progressLabel(book, progressByBook[book.id]), style = MaterialTheme.typography.bodySmall)
              }
              TextButton(onClick = { onDeleteBook(book.id) }) { Text(stringResource(R.string.action_delete)) }
            }
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReaderContent(
  state: ReaderUiState,
  settings: com.engreader.app.model.ReaderSettings,
  chromeVisible: Boolean,
  scrollToChapterIndex: Int?,
  onChapterChange: (Int) -> Unit,
  onChapterScrolledTo: (Int) -> Unit,
  onClearScrollTarget: () -> Unit,
  onPrevChapter: () -> Unit,
  onNextChapter: () -> Unit,
  onParagraphChange: (Int) -> Unit,
  onRemoveBookmark: (String) -> Unit,
  onSearch: (String) -> Unit,
  onSearchResultClick: (SearchHit) -> Unit,
  onDecreaseFont: () -> Unit,
  onIncreaseFont: () -> Unit,
  onDecreaseLineHeight: () -> Unit,
  onIncreaseLineHeight: () -> Unit,
  onDecreaseParagraphSpacing: () -> Unit,
  onIncreaseParagraphSpacing: () -> Unit,
  onDecreaseMargin: () -> Unit,
  onIncreaseMargin: () -> Unit,
  onWordTap: (Int, Int, String, Int) -> Unit,
  onSentenceLongPress: (Int, Int, String) -> Unit,
  onRemoveAnnotation: (String) -> Unit,
  onToggleAnnotations: () -> Unit,
  onCycleRepeatMode: () -> Unit,
  onToggleChrome: () -> Unit,
  justifyText: Boolean,
  onToggleJustify: () -> Unit,
) {
  var showToc by remember { mutableStateOf(false) }
  var showBookmarks by remember { mutableStateOf(false) }
  var showSearch by remember { mutableStateOf(false) }
  var showStyleDialog by remember { mutableStateOf(false) }
  val contentColor = MaterialTheme.colorScheme.onBackground

  Column(modifier = Modifier
    .fillMaxSize()
  ) {
    if (chromeVisible) {
      Text(
        text = state.currentChapter.title,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        color = contentColor,
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
      )
      Text(
        text = "${state.currentChapter.index + 1}/${state.chapters.size}",
        style = MaterialTheme.typography.labelMedium,
        color = contentColor.copy(alpha = 0.7f),
        modifier = Modifier.padding(bottom = 8.dp),
      )
    }

    val flatItems = remember(state.chapters) { buildFlatItems(state.chapters) }
    val listState = rememberLazyListState()

    // Detect chapter changes during scroll
    LaunchedEffect(listState.firstVisibleItemIndex, state.chapters.size) {
      val item = flatItems.getOrNull(listState.firstVisibleItemIndex)
      val newChapterIndex = when (item) {
        is FlatItem.ChapterHeader -> item.chapterIndex
        is FlatItem.Paragraph -> item.chapterIndex
        null -> return@LaunchedEffect
      }
      if (newChapterIndex != state.currentChapterIndex) {
        onChapterScrolledTo(newChapterIndex)
      }
    }

    // Scroll to target chapter on TOC click / search result
    LaunchedEffect(scrollToChapterIndex) {
      scrollToChapterIndex?.let { targetChapter ->
        val targetIndex = flatItems.indexOfFirst {
          it is FlatItem.ChapterHeader && it.chapterIndex == targetChapter
        }
        if (targetIndex >= 0) {
          listState.scrollToItem(targetIndex)
          onClearScrollTarget()
        }
      }
    }

    val paragraphSpacingDp = (14 * settings.paragraphSpacingScale).dp
    val horizontalMarginDp = (12 * settings.marginScale).dp

    if (settings.readerMode == ReaderMode.PAGED) {
      // PAGED mode: one chapter per page with scrollable content
      val pagerState = rememberPagerState(
        initialPage = state.currentChapterIndex.coerceIn(0, (state.chapters.size - 1).coerceAtLeast(0)),
        pageCount = { state.chapters.size.coerceAtLeast(1) }
      )
      LaunchedEffect(pagerState.currentPage) {
        val newPage = pagerState.currentPage
        if (newPage != state.currentChapterIndex) {
          onChapterChange(newPage)
        }
      }
      HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
        val chapter = state.chapters.getOrNull(page)
        if (chapter != null && chapter.paragraphs.isNotEmpty()) {
          val columnState = rememberScrollState()
          Column(
            modifier = Modifier
              .fillMaxSize()
              .verticalScroll(columnState)
              .padding(horizontal = horizontalMarginDp),
            verticalArrangement = Arrangement.spacedBy(paragraphSpacingDp),
          ) {
            for (i in chapter.paragraphs.indices) {
              val paragraphAnnotations = state.annotations.filter {
                it.chapterIndex == chapter.index && it.paragraphIndex == i
              }
              val styled = chapter.styledParagraphs.getOrNull(i)
              val displayText = if (styled != null) {
                renderAnnotatedWithAnnotations(styled, paragraphAnnotations, settings.showAnnotations)
              } else {
                renderParagraphWithAnnotations(chapter.paragraphs[i], paragraphAnnotations, settings.showAnnotations)
              }
              val textAlign = if (justifyText) TextAlign.Justify else TextAlign.Left
              Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp)) {
                  ParagraphWithGestures(
                    displayText = displayText,
                    originalText = chapter.paragraphs[i],
                    textAlign = textAlign,
                    fontSize = (18f * settings.fontScale).sp,
                    lineHeight = (30f * settings.lineHeightScale).sp,
                    contentColor = contentColor,
                    paragraphAnnotations = paragraphAnnotations,
                    settings = settings,
                    onParagraphChange = { onParagraphChange(i) },
                    onWordTap = { mappedOffset ->
                      onWordTap(chapter.index, i, chapter.paragraphs[i], mappedOffset)
                    },
                    onSentenceLongPress = { mappedOffset ->
                      val sentence = extractSentenceAtOffset(chapter.paragraphs[i], mappedOffset)
                      onSentenceLongPress(chapter.index, i, sentence)
                    },
                    onToggleChrome = onToggleChrome,
                  )
                }
              }
            }
          }
        }
      }
    } else {
      // VERTICAL mode: use existing LazyColumn
    LazyColumn(
      state = listState,
      modifier = Modifier.weight(1f).padding(horizontal = horizontalMarginDp),
      verticalArrangement = Arrangement.spacedBy(paragraphSpacingDp),
    ) {
      items(flatItems.size) { index ->
        when (val item = flatItems[index]) {
          is FlatItem.ChapterHeader -> {
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggleChrome() }
                .padding(top = if (item.chapterIndex == 0) 0.dp else 12.dp, bottom = 4.dp),
              contentAlignment = Alignment.Center,
            ) {
              HorizontalDivider(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
              )
              Text(
                text = item.title,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = contentColor.copy(alpha = 0.6f),
                modifier = Modifier
                  .background(MaterialTheme.colorScheme.background)
                  .padding(horizontal = 12.dp, vertical = 2.dp),
              )
            }
          }

          is FlatItem.Paragraph -> {
            val paragraphAnnotations =
              state.annotations.filter {
                it.chapterIndex == item.chapterIndex && it.paragraphIndex == item.paragraphIndex
              }
            val textAlign = if (justifyText) TextAlign.Justify else TextAlign.Left
            val hasImages = item.segments.any { it is ParagraphSegment.ImageSegment }
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
              if (hasImages) {
                Column(modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp)) {
                  item.segments.forEach { segment ->
                    when (segment) {
                      is ParagraphSegment.TextSegment -> {
                        val displayText = if (item.styledText != null) {
                          renderAnnotatedWithAnnotations(item.styledText, paragraphAnnotations, settings.showAnnotations)
                        } else {
                          renderParagraphWithAnnotations(segment.text, paragraphAnnotations, settings.showAnnotations)
                        }
                        ParagraphWithGestures(
                          displayText = displayText,
                          originalText = item.text,
                          textAlign = textAlign,
                          fontSize = (18f * settings.fontScale).sp,
                          lineHeight = (30f * settings.lineHeightScale).sp,
                          contentColor = contentColor,
                          paragraphAnnotations = paragraphAnnotations,
                          settings = settings,
                          onParagraphChange = { onParagraphChange(item.paragraphIndex) },
                          onWordTap = { mappedOffset ->
                            onWordTap(item.chapterIndex, item.paragraphIndex, item.text, mappedOffset)
                          },
                          onSentenceLongPress = { mappedOffset ->
                            val sentence = extractSentenceAtOffset(item.text, mappedOffset)
                            onSentenceLongPress(item.chapterIndex, item.paragraphIndex, sentence)
                          },
                          onToggleChrome = onToggleChrome,
                        )
                      }

                      is ParagraphSegment.ImageSegment -> {
                        val isSvg = segment.imagePath.lowercase().endsWith(".svg")
                        if (isSvg) {
                          // SVG not natively supported; show placeholder
                          Text(
                            text = "[SVG: ${segment.altText.ifBlank { "image" }}]",
                            style = MaterialTheme.typography.labelSmall,
                            color = contentColor.copy(alpha = 0.4f),
                            modifier = Modifier.padding(vertical = 4.dp),
                          )
                        } else {
                          val bitmap = runCatching { BitmapFactory.decodeFile(segment.imagePath) }.getOrNull()
                          if (bitmap != null) {
                            Image(
                              bitmap = bitmap.asImageBitmap(),
                              contentDescription = segment.altText.ifBlank { "image" },
                              modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            )
                          } else {
                            Text(
                              text = "[Image: ${segment.altText.ifBlank { "unknown" }}]",
                              style = MaterialTheme.typography.labelSmall,
                              color = contentColor.copy(alpha = 0.4f),
                              modifier = Modifier.padding(vertical = 4.dp),
                            )
                          }
                        }
                      }
                    }
                  }
                  if (chromeVisible && paragraphAnnotations.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                      paragraphAnnotations.forEach { annotation ->
                        TextButton(onClick = { onRemoveAnnotation(annotation.id) }) {
                          Text(if (annotation.type == AnnotationType.WORD) annotation.anchorText else "句注")
                        }
                      }
                    }
                  }
                }
              } else {
                val displayText = if (item.styledText != null) {
                  renderAnnotatedWithAnnotations(item.styledText, paragraphAnnotations, settings.showAnnotations)
                } else {
                  renderParagraphWithAnnotations(item.text, paragraphAnnotations, settings.showAnnotations)
                }
                Column(modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp)) {
                  ParagraphWithGestures(
                    displayText = displayText,
                    originalText = item.text,
                    textAlign = textAlign,
                    fontSize = (18f * settings.fontScale).sp,
                    lineHeight = (30f * settings.lineHeightScale).sp,
                    contentColor = contentColor,
                    paragraphAnnotations = paragraphAnnotations,
                    settings = settings,
                    onParagraphChange = { onParagraphChange(item.paragraphIndex) },
                    onWordTap = { mappedOffset ->
                      onWordTap(item.chapterIndex, item.paragraphIndex, item.text, mappedOffset)
                    },
                    onSentenceLongPress = { mappedOffset ->
                      val sentence = extractSentenceAtOffset(item.text, mappedOffset)
                      onSentenceLongPress(item.chapterIndex, item.paragraphIndex, sentence)
                    },
                    onToggleChrome = onToggleChrome,
                  )
                  if (chromeVisible && paragraphAnnotations.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                      paragraphAnnotations.forEach { annotation ->
                        TextButton(onClick = { onRemoveAnnotation(annotation.id) }) {
                          Text(if (annotation.type == AnnotationType.WORD) annotation.anchorText else "句注")
                        }
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
    } // end else (VERTICAL mode)

    if (chromeVisible) {
      HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
      Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
          ) {
            TextButton(onClick = onPrevChapter) { Text(stringResource(R.string.action_prev_chapter)) }
            TextButton(onClick = { showToc = true }) { Text(stringResource(R.string.action_toc)) }
            TextButton(onClick = { showSearch = true }) { Text(stringResource(R.string.action_search)) }
            TextButton(onClick = { showBookmarks = true }) { Text(stringResource(R.string.action_bookmark_list)) }
            TextButton(onClick = onNextChapter) { Text(stringResource(R.string.action_next_chapter)) }
          }
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
          ) {
            FilledTonalButton(onClick = { showStyleDialog = true }) {
              Text("⚙ ${stringResource(R.string.action_style)}")
            }
          }
        }
      }
    }
  }

  if (showToc) {
    AlertDialog(
      onDismissRequest = { showToc = false },
      confirmButton = { TextButton(onClick = { showToc = false }) { Text(stringResource(R.string.action_close)) } },
      title = { Text(stringResource(R.string.action_toc)) },
      text = {
        LazyColumn {
          items(state.chapters) { chapter ->
            Text(
              text = chapter.title,
              modifier = Modifier
                .fillMaxWidth()
                .clickable {
                  onChapterChange(chapter.index)
                  showToc = false
                }
                .padding(vertical = 6.dp),
              style = MaterialTheme.typography.bodyMedium,
            )
          }
        }
      },
    )
  }

  if (showBookmarks) {
    AlertDialog(
      onDismissRequest = { showBookmarks = false },
      confirmButton = { TextButton(onClick = { showBookmarks = false }) { Text(stringResource(R.string.action_close)) } },
      title = { Text(stringResource(R.string.action_bookmark_list)) },
      text = {
        if (state.bookmarks.isEmpty()) {
          Text(stringResource(R.string.hint_no_bookmarks))
        } else {
          LazyColumn {
            items(state.bookmarks, key = { it.id }) { bookmark ->
              Row(
                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(bookmark.chapterTitle, modifier = Modifier.weight(1f))
                TextButton(onClick = { onRemoveBookmark(bookmark.id) }) { Text(stringResource(R.string.action_delete)) }
              }
            }
          }
        }
      },
    )
  }

  if (showSearch) {
    var searchQuery by rememberSaveable(state.book.id) { mutableStateOf("") }
    AlertDialog(
      onDismissRequest = { showSearch = false },
      confirmButton = { TextButton(onClick = { showSearch = false }) { Text(stringResource(R.string.action_close)) } },
      title = { Text(stringResource(R.string.action_search)) },
      text = {
        Column {
          OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text(stringResource(R.string.action_search)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
          )
          Spacer(Modifier.height(8.dp))
          Button(onClick = { onSearch(searchQuery) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_search)) }
          Spacer(Modifier.height(8.dp))
          if (state.searchResults.isNotEmpty()) {
            LazyColumn {
              items(state.searchResults) { hit ->
                Text(
                  text = hit.snippet,
                  modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                      onSearchResultClick(hit)
                      showSearch = false
                    }
                    .padding(vertical = 4.dp),
                  style = MaterialTheme.typography.bodySmall,
                )
              }
            }
          }
        }
      },
    )
  }

  if (showStyleDialog) {
    AlertDialog(
      onDismissRequest = { showStyleDialog = false },
      confirmButton = { TextButton(onClick = { showStyleDialog = false }) { Text(stringResource(R.string.action_close)) } },
      title = { Text(stringResource(R.string.action_style)) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Text("Font Size", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onDecreaseFont) { Text("-") }
            Text("%.1f".format(settings.fontScale), modifier = Modifier.width(40.dp), textAlign = TextAlign.Center)
            Button(onClick = onIncreaseFont) { Text("+") }
          }
          Text("Line Spacing", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onDecreaseLineHeight) { Text("-") }
            Text("%.1f".format(settings.lineHeightScale), modifier = Modifier.width(40.dp), textAlign = TextAlign.Center)
            Button(onClick = onIncreaseLineHeight) { Text("+") }
          }
          Text("Paragraph Spacing", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onDecreaseParagraphSpacing) { Text("-") }
            Text("%.1f".format(settings.paragraphSpacingScale), modifier = Modifier.width(40.dp), textAlign = TextAlign.Center)
            Button(onClick = onIncreaseParagraphSpacing) { Text("+") }
          }
          Text("Page Margins", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onDecreaseMargin) { Text("-") }
            Text("%.1f".format(settings.marginScale), modifier = Modifier.width(40.dp), textAlign = TextAlign.Center)
            Button(onClick = onIncreaseMargin) { Text("+") }
          }
          Text("Text Alignment", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onToggleJustify) {
              Text(if (justifyText) "Left" else "Justify")
            }
          }
        }
      },
    )
  }
}

@Composable
private fun repeatModeLabel(mode: RepeatAnnotationMode): String {
  return when (mode) {
    RepeatAnnotationMode.TAP_ONLY -> stringResource(R.string.repeat_tap_only)
    RepeatAnnotationMode.CHAPTER_AUTO -> stringResource(R.string.repeat_chapter_auto)
    RepeatAnnotationMode.BOOK_AUTO -> stringResource(R.string.repeat_book_auto)
  }
}

private fun renderParagraphWithAnnotations(
  paragraph: String,
  annotations: List<AnnotationRecord>,
  showAnnotations: Boolean,
): AnnotatedString {
  if (!showAnnotations || annotations.isEmpty()) return AnnotatedString(paragraph)
  val noteStyle = SpanStyle(fontSize = 13.sp, color = Color(0xFFE53935))
  val styleRanges = mutableListOf<Pair<Int, Int>>()
  var rendered = paragraph

  val wordAnnotations = annotations
    .filter { it.type == AnnotationType.WORD }
    .sortedBy { paragraph.indexOf(it.anchorText, ignoreCase = true) }
  var searchStart = 0
  wordAnnotations.forEach { annotation ->
    val index = rendered.indexOf(annotation.anchorText, startIndex = searchStart, ignoreCase = true)
    if (index >= 0) {
      val insertAt = index + annotation.anchorText.length
      val note = "\uFF08${annotation.translation}\uFF09"
      rendered = rendered.substring(0, insertAt) + note + rendered.substring(insertAt)
      styleRanges += insertAt to (insertAt + note.length)
      searchStart = insertAt + note.length
    }
  }

  val sentenceAnnotations = annotations.filter { it.type == AnnotationType.SENTENCE }
    .sortedBy { paragraph.indexOf(it.anchorText, ignoreCase = true) }
  sentenceAnnotations.forEach { annotation ->
    val index = rendered.indexOf(annotation.anchorText, startIndex = searchStart, ignoreCase = true)
    if (index >= 0) {
      val insertAt = index + annotation.anchorText.length
      val note = "\uFF08${annotation.translation}\uFF09"
      rendered = rendered.substring(0, insertAt) + note + rendered.substring(insertAt)
      styleRanges += insertAt to (insertAt + note.length)
      searchStart = insertAt + note.length
    } else {
      // Fallback: append at end if sentence not found
      val note = "\uFF08${annotation.translation}\uFF09"
      if (!rendered.endsWith(note)) {
        val start = rendered.length
        rendered += note
        styleRanges += start to (start + note.length)
      }
    }
  }

  val builder = AnnotatedString.Builder(rendered)
  styleRanges.forEach { (start, end) ->
    builder.addStyle(noteStyle, start, end)
  }
  return builder.toAnnotatedString()
}

private fun renderAnnotatedWithAnnotations(
  styled: AnnotatedString,
  annotations: List<AnnotationRecord>,
  showAnnotations: Boolean,
): AnnotatedString {
  if (!showAnnotations || annotations.isEmpty()) return styled
  val paragraph = styled.text
  val noteStyle = SpanStyle(fontSize = 13.sp, color = Color(0xFFE53935))
  data class Insertion(val at: Int, val length: Int)
  val insertions = mutableListOf<Insertion>()
  var rendered = paragraph

  val wordAnnotations = annotations
    .filter { it.type == AnnotationType.WORD }
    .sortedBy { paragraph.indexOf(it.anchorText, ignoreCase = true) }
  var searchStart = 0
  wordAnnotations.forEach { annotation ->
    val index = rendered.indexOf(annotation.anchorText, startIndex = searchStart, ignoreCase = true)
    if (index >= 0) {
      val insertAt = index + annotation.anchorText.length
      val note = "\uFF08${annotation.translation}\uFF09"
      rendered = rendered.substring(0, insertAt) + note + rendered.substring(insertAt)
      insertions += Insertion(insertAt, note.length)
      searchStart = insertAt + note.length
    }
  }

  val sentenceAnnotations = annotations.filter { it.type == AnnotationType.SENTENCE }
    .sortedBy { rendered.indexOf(it.anchorText, startIndex = searchStart, ignoreCase = true) }
  sentenceAnnotations.forEach { annotation ->
    val index = rendered.indexOf(annotation.anchorText, startIndex = searchStart, ignoreCase = true)
    if (index >= 0) {
      val insertAt = index + annotation.anchorText.length
      val note = "\uFF08${annotation.translation}\uFF09"
      rendered = rendered.substring(0, insertAt) + note + rendered.substring(insertAt)
      insertions += Insertion(insertAt, note.length)
      searchStart = insertAt + note.length
    } else {
      val note = "\uFF08${annotation.translation}\uFF09"
      if (!rendered.endsWith(note)) {
        val start = rendered.length
        rendered += note
        insertions += Insertion(start, note.length)
      }
    }
  }

  val builder = AnnotatedString.Builder(rendered)
  // Copy original styles, adjusting offsets for insertions that occurred before each style
  styled.spanStyles.forEach { spanStyle ->
    val adjustment = insertions
      .filter { it.at <= spanStyle.start }
      .sumOf { it.length }
    val newStart = spanStyle.start + adjustment
    val newEnd = spanStyle.end + adjustment
    builder.addStyle(spanStyle.item, newStart, newEnd)
  }
  // Overlay annotation styles
  insertions.forEach { insertion ->
    builder.addStyle(noteStyle, insertion.at, insertion.at + insertion.length)
  }
  return builder.toAnnotatedString()
}

private fun extractSentenceAtOffset(text: String, offset: Int): String {
  if (text.isEmpty() || offset !in text.indices) return text
  // Expand to find sentence boundaries (., !, ?)
  val sentenceEnds = setOf('.', '!', '?')
  var start = offset
  var end = offset
  // Go left to find start of sentence
  while (start > 0) {
    val ch = text[start - 1]
    if (ch == '\n' || (sentenceEnds.contains(ch) && (start >= text.length || text[start].isWhitespace()))) {
      break
    }
    start--
  }
  // Skip leading whitespace and newlines
  while (start < text.length && (text[start].isWhitespace())) start++
  // Go right to find end of sentence
  while (end < text.length) {
    val ch = text[end]
    if (sentenceEnds.contains(ch)) {
      end++ // include the punctuation
      break
    }
    if (ch == '\n') break
    end++
  }
  val result = text.substring(start, end.coerceAtMost(text.length)).trim()
  return result.ifBlank { text.trim().take(100) }
}

@Composable
private fun ParagraphWithGestures(
  displayText: AnnotatedString,
  originalText: String,
  textAlign: TextAlign,
  fontSize: TextUnit,
  lineHeight: TextUnit,
  contentColor: Color,
  paragraphAnnotations: List<AnnotationRecord>,
  settings: com.engreader.app.model.ReaderSettings,
  onParagraphChange: () -> Unit,
  onWordTap: (Int) -> Unit,
  onSentenceLongPress: (Int) -> Unit,
  onToggleChrome: () -> Unit,
) {
  var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
  var pressPixelOffset by remember { mutableStateOf(Offset.Zero) }

  // Detect heading
  val headingAnnotations = displayText.getStringAnnotations("HEADING", 0, displayText.text.length)
  val isHeading = headingAnnotations.isNotEmpty()
  // Detect blockquote
  val blockquoteAnnotations = displayText.getStringAnnotations("BLOCKQUOTE", 0, displayText.text.length)
  val isBlockquote = blockquoteAnnotations.isNotEmpty()
  val context = LocalView.current.context

  val textStyle = TextStyle(
    fontFamily = FontFamily.Serif,
    fontSize = if (isHeading) (22f * settings.fontScale).sp else fontSize,
    lineHeight = lineHeight,
    fontWeight = if (isHeading) FontWeight.Bold else FontWeight.Normal,
    textAlign = textAlign,
    textIndent = TextIndent(),
    color = contentColor,
    hyphens = if (textAlign == TextAlign.Justify) Hyphens.Auto else Hyphens.Unspecified,
  )

  // Blockquote: wrap with left bar
  if (isBlockquote) {
    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
      Box(
        modifier = Modifier
          .width(4.dp)
          .fillMaxHeight()
          .background(contentColor.copy(alpha = 0.3f), RoundedCornerShape(2.dp))
      )
      Spacer(Modifier.width(8.dp))
      BasicText(
        text = displayText,
        style = textStyle.copy(color = contentColor.copy(alpha = 0.85f), fontStyle = FontStyle.Italic),
        onTextLayout = { textLayoutResult = it },
        modifier = Modifier
          .weight(1f)
          .pointerInput(displayText) {
            detectTapGestures(
              onPress = { offset -> pressPixelOffset = offset; tryAwaitRelease() },
              onTap = {
                val layout = textLayoutResult ?: return@detectTapGestures
                val charOffset = layout.getOffsetForPosition(pressPixelOffset)
                onParagraphChange()
                val urlAnnotations = displayText.getStringAnnotations("URL", charOffset, charOffset)
                if (urlAnnotations.isNotEmpty()) {
                  val intent = Intent(Intent.ACTION_VIEW, Uri.parse(urlAnnotations.first().item))
                  runCatching { context.startActivity(intent) }
                  return@detectTapGestures
                }
                val mappedOffset = mapRenderedOffsetToOriginal(originalText, paragraphAnnotations, settings.showAnnotations, charOffset)
                if (mappedOffset >= 0 && originalText.getOrNull(mappedOffset)?.isLetter() == true) {
                  onWordTap(mappedOffset)
                } else {
                  onToggleChrome()
                }
              },
              onLongPress = { offset ->
                val layout = textLayoutResult ?: return@detectTapGestures
                val charOffset = layout.getOffsetForPosition(offset)
                onParagraphChange()
                val mappedOffset = mapRenderedOffsetToOriginal(originalText, paragraphAnnotations, settings.showAnnotations, charOffset)
                if (mappedOffset >= 0) { onSentenceLongPress(mappedOffset) }
              },
            )
          },
      )
    }
  } else {
    BasicText(
      text = displayText,
    style = textStyle,
    onTextLayout = { textLayoutResult = it },
    modifier = Modifier
      .fillMaxWidth()
      .pointerInput(displayText) {
        detectTapGestures(
          onPress = { offset ->
            pressPixelOffset = offset
            tryAwaitRelease()
          },
          onTap = {
            val layout = textLayoutResult ?: return@detectTapGestures
            val charOffset = layout.getOffsetForPosition(pressPixelOffset)
            onParagraphChange()
            // Check for URL link first
            val urlAnnotations = displayText.getStringAnnotations("URL", charOffset, charOffset)
            if (urlAnnotations.isNotEmpty()) {
              val url = urlAnnotations.first().item
              val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
              runCatching { context.startActivity(intent) }
              return@detectTapGestures
            }
            val mappedOffset =
              mapRenderedOffsetToOriginal(originalText, paragraphAnnotations, settings.showAnnotations, charOffset)
            if (mappedOffset >= 0 && originalText.getOrNull(mappedOffset)?.isLetter() == true) {
              onWordTap(mappedOffset)
            } else {
              onToggleChrome()
            }
          },
          onLongPress = { offset ->
            val layout = textLayoutResult ?: return@detectTapGestures
            val charOffset = layout.getOffsetForPosition(offset)
            onParagraphChange()
            val mappedOffset =
              mapRenderedOffsetToOriginal(originalText, paragraphAnnotations, settings.showAnnotations, charOffset)
            if (mappedOffset >= 0) {
              onSentenceLongPress(mappedOffset)
            }
          },
        )
      },
    )
  }
}

private fun mapRenderedOffsetToOriginal(
  paragraph: String,
  annotations: List<AnnotationRecord>,
  showAnnotations: Boolean,
  renderedOffset: Int,
): Int {
  if (!showAnnotations || annotations.isEmpty()) return renderedOffset
  data class Insertion(val start: Int, val length: Int)

  val insertions = mutableListOf<Insertion>()
  var rendered = paragraph
  var searchStart = 0
  annotations.filter { it.type == AnnotationType.WORD }.forEach { annotation ->
    val index = rendered.indexOf(annotation.anchorText, startIndex = searchStart, ignoreCase = true)
    if (index >= 0) {
      val insertAt = index + annotation.anchorText.length
      val note = "\uFF08${annotation.translation}\uFF09"
      rendered = rendered.substring(0, insertAt) + note + rendered.substring(insertAt)
      insertions += Insertion(insertAt, note.length)
      searchStart = insertAt + note.length
    }
  }

  val sentenceAnnotations = annotations.filter { it.type == AnnotationType.SENTENCE }
    .sortedBy { rendered.indexOf(it.anchorText, startIndex = searchStart, ignoreCase = true) }
  sentenceAnnotations.forEach { annotation ->
    val index = rendered.indexOf(annotation.anchorText, startIndex = searchStart, ignoreCase = true)
    if (index >= 0) {
      val insertAt = index + annotation.anchorText.length
      val note = "\uFF08${annotation.translation}\uFF09"
      rendered = rendered.substring(0, insertAt) + note + rendered.substring(insertAt)
      insertions += Insertion(insertAt, note.length)
      searchStart = insertAt + note.length
    } else {
      val note = "\uFF08${annotation.translation}\uFF09"
      if (!rendered.endsWith(note)) {
        val start = rendered.length
        rendered += note
        insertions += Insertion(start, note.length)
      }
    }
  }

  var adjusted = renderedOffset
  insertions.sortedBy { it.start }.forEach { insertion ->
    if (renderedOffset in insertion.start until (insertion.start + insertion.length)) {
      return -1
    }
    if (renderedOffset >= insertion.start + insertion.length) {
      adjusted -= insertion.length
    }
  }
  return adjusted.coerceIn(0, paragraph.lastIndex.coerceAtLeast(0))
}

private fun nextProvider(current: AiProvider): AiProvider {
  return when (current) {
    AiProvider.DEEPSEEK -> AiProvider.GEMINI
    AiProvider.GEMINI -> AiProvider.OPENAI
    AiProvider.OPENAI -> AiProvider.DEEPSEEK
  }
}

private sealed class FlatItem {
  data class ChapterHeader(val chapterIndex: Int, val title: String) : FlatItem()
  data class Paragraph(
    val chapterIndex: Int,
    val paragraphIndex: Int,
    val text: String,
    val styledText: AnnotatedString?,
    val segments: List<com.engreader.app.model.ParagraphSegment>,
  ) : FlatItem()
}

private fun buildFlatItems(chapters: List<ChapterContent>): List<FlatItem> {
  val items = mutableListOf<FlatItem>()
  for (chapter in chapters) {
    items += FlatItem.ChapterHeader(chapter.index, chapter.title)
    for (i in chapter.paragraphs.indices) {
      items += FlatItem.Paragraph(
        chapterIndex = chapter.index,
        paragraphIndex = i,
        text = chapter.paragraphs[i],
        styledText = chapter.styledParagraphs.getOrNull(i),
        segments = chapter.segments.getOrNull(i).orEmpty(),
      )
    }
  }
  return items
}
