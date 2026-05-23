package com.engreader.app.ui.main

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.foundation.shape.RoundedCornerShape
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

    Scaffold(
      modifier = modifier,
      contentWindowInsets = WindowInsets(0, 0, 0, 0),
      snackbarHost = { SnackbarHost(snackbarHostState) },
      floatingActionButton = {
        if (state.screen == AppScreen.Shelf) {
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
              actions = {
                TextButton(onClick = { viewModel.toggleShelfViewMode() }) {
                  Text(if (state.settings.shelfViewMode == ShelfViewMode.GRID) stringResource(R.string.action_list) else stringResource(R.string.action_grid))
                }
                TextButton(onClick = { viewModel.cycleSortMode() }) { Text(sortLabel(state.settings.shelfSortMode)) }
                TextButton(onClick = { viewModel.cycleThemeMode() }) { Text(themeLabel(state.settings.themeMode)) }
                TextButton(onClick = { showApiDialog = true }) { Text(stringResource(R.string.action_api_settings)) }
                TextButton(onClick = { exportBackupLauncher.launch("engreader_backup.zip") }) {
                  Text(stringResource(R.string.action_export_backup))
                }
                TextButton(onClick = { restoreBackupLauncher.launch(arrayOf("application/zip", "application/octet-stream")) }) {
                  Text(stringResource(R.string.action_restore_backup))
                }
              }
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
                onChapterChange = viewModel::setChapter,
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
                onToggleReaderMode = viewModel::toggleReaderMode,
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
  onChapterChange: (Int) -> Unit,
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
  onToggleReaderMode: () -> Unit,
) {
  var showToc by remember { mutableStateOf(false) }
  var showBookmarks by remember { mutableStateOf(false) }
  var showSearch by remember { mutableStateOf(false) }
  var showStyleDialog by remember { mutableStateOf(false) }
  val scope = rememberCoroutineScope()
  val contentColor = MaterialTheme.colorScheme.onBackground
  val config = LocalConfiguration.current
  val density = LocalDensity.current
  val screenHeightDp = config.screenHeightDp
  val maxCharsPerPage = remember(screenHeightDp, settings.fontScale, settings.lineHeightScale) {
    val charsPerLine = ((config.screenWidthDp - 24) * density.density / (10f * settings.fontScale)).toInt()
    val linesPerPage = (screenHeightDp * density.density / (20f * settings.lineHeightScale * settings.fontScale)).toInt()
    (charsPerLine * linesPerPage).coerceIn(1200, 5000)
  }

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

    if (settings.readerMode == ReaderMode.VERTICAL) {
      val paragraphSpacingDp = (14 * settings.paragraphSpacingScale).dp
      val horizontalMarginDp = (12 * settings.marginScale).dp
      LazyColumn(modifier = Modifier.weight(1f).padding(horizontal = horizontalMarginDp), verticalArrangement = Arrangement.spacedBy(paragraphSpacingDp)) {
        items(state.currentChapter.paragraphs.size) { paragraphIndex ->
          val paragraph = state.currentChapter.paragraphs[paragraphIndex]
          val paragraphAnnotations =
            state.annotations.filter { it.chapterIndex == state.currentChapter.index && it.paragraphIndex == paragraphIndex }
          Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp)) {
              ClickableText(
                text = renderParagraphWithAnnotations(paragraph, paragraphAnnotations, settings.showAnnotations),
                style =
                  TextStyle(
                    fontFamily = FontFamily.Serif,
                    fontSize = (18f * settings.fontScale).sp,
                    lineHeight = (30f * settings.lineHeightScale).sp,
                    textIndent = TextIndent(firstLine = 22.sp),
                    color = contentColor,
                  ),
                modifier = Modifier.pointerInput(paragraph) {
                  detectTapGestures(
                    onLongPress = {
                      onParagraphChange(paragraphIndex)
                      onSentenceLongPress(state.currentChapter.index, paragraphIndex, paragraph)
                    }
                  )
                },
                onClick = { offset ->
                  if (!chromeVisible) {
                    onToggleChrome()
                  } else {
                    onParagraphChange(paragraphIndex)
                    val mappedOffset =
                      mapRenderedOffsetToOriginal(paragraph, paragraphAnnotations, settings.showAnnotations, offset)
                    if (mappedOffset >= 0) {
                      onWordTap(state.currentChapter.index, paragraphIndex, paragraph, mappedOffset)
                    }
                  }
                },
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
    } else {
      val segments = remember(state.currentChapter.paragraphs, state.annotations, settings.showAnnotations, maxCharsPerPage) {
        buildPagedSegments(
          paragraphs = state.currentChapter.paragraphs,
          annotations = state.annotations.filter { it.chapterIndex == state.currentChapter.index },
          showAnnotations = settings.showAnnotations,
          maxCharsPerPage = maxCharsPerPage,
        )
      }
      val initialPage = remember(segments, state.currentParagraphIndex) { findPageForParagraph(segments, state.currentParagraphIndex) }
      val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { segments.size.coerceAtLeast(1) })
      LaunchedEffect(pagerState.currentPage) {
        segments.getOrNull(pagerState.currentPage)?.let { onParagraphChange(it.startParagraphIndex) }
      }
      Box(modifier = Modifier.weight(1f)) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
        val segment = segments.getOrElse(page) { ReaderPageSegment(0, "") }
        val horizontalMarginDp = (12 * settings.marginScale).dp
        Box(modifier = Modifier
          .fillMaxSize()
          .clickable(
            indication = null,
            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
          ) {
            if (!chromeVisible) onToggleChrome()
          }
          .pointerInput(segment) {
            detectTapGestures(
              onLongPress = {
                if (chromeVisible) {
                  onParagraphChange(segment.startParagraphIndex)
                  onSentenceLongPress(state.currentChapter.index, segment.startParagraphIndex, segment.text)
                }
              }
            )
          },
          contentAlignment = Alignment.TopCenter
        ) {
          Text(
            text = segment.text,
            style =
              TextStyle(
                fontFamily = FontFamily.Serif,
                fontSize = (19f * settings.fontScale).sp,
                lineHeight = (31f * settings.lineHeightScale).sp,
                textIndent = TextIndent(firstLine = 22.sp),
                color = contentColor,
              ),
            modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp).padding(top = 6.dp, start = horizontalMarginDp, end = horizontalMarginDp),
          )
        }
      }
        // Paged nav arrows
        Text(
          text = "<", fontSize = 36.sp,
          color = contentColor.copy(alpha = 0.3f),
          modifier = Modifier
            .align(Alignment.CenterStart).padding(start = 2.dp)
            .clickable {
              if (pagerState.currentPage > 0) {
                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
              } else { onPrevChapter() }
            },
        )
        Text(
          text = ">", fontSize = 36.sp,
          color = contentColor.copy(alpha = 0.3f),
          modifier = Modifier
            .align(Alignment.CenterEnd).padding(end = 2.dp)
            .clickable {
              if (pagerState.currentPage < segments.size - 1) {
                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
              } else { onNextChapter() }
            },
        )
      }
    }

    if (chromeVisible) {
      HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
      Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          FilledTonalButton(onClick = onToggleReaderMode) {
            Text(
              if (settings.readerMode == ReaderMode.VERTICAL) "⇄ ${stringResource(R.string.action_paged)}"
              else "↕ ${stringResource(R.string.action_scroll)}"
            )
          }
          Row {
            TextButton(onClick = onPrevChapter) { Text(stringResource(R.string.action_prev_chapter)) }
            TextButton(onClick = onNextChapter) { Text(stringResource(R.string.action_next_chapter)) }
          }
          Row {
            TextButton(onClick = { showToc = true }) { Text(stringResource(R.string.action_toc)) }
            TextButton(onClick = { showStyleDialog = true }) { Text("⚙ ${stringResource(R.string.action_style)}") }
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
private data class ReaderPageSegment(
  val startParagraphIndex: Int,
  val text: String,
)

private fun buildPagedSegments(
  paragraphs: List<String>,
  annotations: List<AnnotationRecord>,
  showAnnotations: Boolean,
  maxCharsPerPage: Int = 2400,
): List<ReaderPageSegment> {
  if (paragraphs.isEmpty()) return listOf(ReaderPageSegment(0, ""))
  val result = mutableListOf<ReaderPageSegment>()
  var pageStartParagraph = 0
  var currentLength = 0
  val builder = StringBuilder()

  for (paragraphIndex in paragraphs.indices) {
    val paragraph = paragraphs[paragraphIndex]
    val paragraphAnnotations = annotations.filter { it.paragraphIndex == paragraphIndex }
    val rendered = renderParagraphWithAnnotations(paragraph, paragraphAnnotations, showAnnotations).text.trim()
    val candidateLength = if (builder.isEmpty()) rendered.length else currentLength + rendered.length + 2

    if (builder.isNotEmpty() && candidateLength > maxCharsPerPage) {
      result += ReaderPageSegment(pageStartParagraph, builder.toString())
      builder.clear()
      pageStartParagraph = paragraphIndex
      currentLength = 0
    }

    if (builder.isNotEmpty()) builder.append("\n\n")
    builder.append(rendered)
    currentLength = builder.length
  }

  if (builder.isNotEmpty()) {
    result += ReaderPageSegment(pageStartParagraph, builder.toString())
  }
  return if (result.isEmpty()) listOf(ReaderPageSegment(0, "")) else result
}

private fun findPageForParagraph(segments: List<ReaderPageSegment>, paragraphIndex: Int): Int {
  if (segments.isEmpty()) return 0
  var bestIndex = 0
  for (index in segments.indices) {
    if (segments[index].startParagraphIndex <= paragraphIndex) {
      bestIndex = index
    } else {
      break
    }
  }
  return bestIndex
}

private fun renderParagraphWithAnnotations(
  paragraph: String,
  annotations: List<AnnotationRecord>,
  showAnnotations: Boolean,
): AnnotatedString {
  if (!showAnnotations || annotations.isEmpty()) return AnnotatedString(paragraph)
  val noteStyle = SpanStyle(fontSize = 13.sp, color = Color(0xFF9CA3AF))
  val styleRanges = mutableListOf<Pair<Int, Int>>()
  var rendered = paragraph

  val wordAnnotations = annotations.filter { it.type == AnnotationType.WORD }
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

  annotations.filter { it.type == AnnotationType.SENTENCE }.forEach { annotation ->
    val note = "\uFF08${annotation.translation}\uFF09"
    if (!rendered.endsWith(note)) {
      val start = rendered.length
      rendered += note
      styleRanges += start to (start + note.length)
    }
  }

  val builder = AnnotatedString.Builder(rendered)
  styleRanges.forEach { (start, end) ->
    builder.addStyle(noteStyle, start, end)
  }
  return builder.toAnnotatedString()
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

  annotations.filter { it.type == AnnotationType.SENTENCE }.forEach { annotation ->
    val note = "\uFF08${annotation.translation}\uFF09"
    if (!rendered.endsWith(note)) {
      val start = rendered.length
      rendered += note
      insertions += Insertion(start, note.length)
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
