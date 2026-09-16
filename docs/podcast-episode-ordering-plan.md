# Порядок выпусков подкаста и настройки айтема — план реализации

Спека: `docs/podcast-episode-ordering-spec.md` (issues #239, #243).
План фиксирует два класса отклонений от спеки: (А) спека описывает устаревшее текущее
состояние — это упрощает работу; (Б) раздел 6 спеки буквально неисполним против ABS API —
ниже исправленная постановка вызовов. Всё остальное — по спеке, порядок фаз 7 → 8 → 4 → 5 → 6.

---

## А. Что спека говорит про «как сейчас», а в коде уже не так

Проверено по коду main (c4424730):

1. **Отдельного экрана «Оглавление» нет.** Оглавление уже разворачивается на месте:
   `PlayerViewModel.playingQueueExpanded` + `AnimatedVisibility` на блоке артворка
   (`PlayerScreen.kt:364`). Плитка «Главы» (`NavigationBarComposable.kt:89`) вызывает
   `togglePlayingQueue()`, не навигацию. → п. 4.6 спеки — no-op; фаза 4 превращается из
   «удалить экран» в «переезд управления состоянием».
2. **Cast в топбаре плеера нет** (`PlayerScreen.kt:204-300`: Search/Bookmarks/Info).
   → п. 3.6 и упоминания Cast — no-op.
3. **Поиск по главам уже есть** на экране плеера: `searchRequested` +
   `ChapterSearchActionComposable` в **слоте actions** топбара. Спека хочет поле в слоте
   **title** — это перенос, не новая фича.
4. Порядок оглавления сейчас: `PlayingQueueComposable` читает `book.chapters` как есть;
   фиксация порядка в `PodcastResponseConverter.orderEpisode()`
   (`PodcastResponseConverter.kt:124`) — **по дате публикации по возрастанию (старые первыми)**,
   null-даты — первыми. Это важно для дефолта (см. Б.3).

## Б. Что в спеке буквально неисполнимо (и как делаем)

### Б.1. API прогресса (раздел 6) — проверено по исходникам ABS-сервера

- `DELETE /api/me/progress/{itemId}` — **параметр не libraryItemId, а id записи прогресса**
  (`MeController.removeMediaProgress`: `mediaProgresses.find(mp => mp.id === req.params.id)`).
  Id записи — составной (`{userId}_{libraryItemId}[_{episodeId}]`).
- `PATCH /api/me/progress/{itemId}` с `{"isFinished": true}` **для подкаста возвращает 400**
  («Library item is not a book»): `User.createUpdateMediaProgressFromPayload` требует
  `episodeId` для подкастов. Прогресс подкаста хранится по эпизодам.
- Есть `PATCH /api/me/progress/batch/update` — принимает массив payload'ов
  `{libraryItemId, episodeId, isFinished, ...}`, одна HTTP-запрос на весь батч.

Исправленная постановка:

| Действие | Книга | Подкаст |
|---|---|---|
| Отметить/снять | `PATCH api/me/progress/{itemId}` body `{"isFinished": bool}` | `PATCH api/me/progress/batch/update`, payload на каждый эпизод (id эпизода = `PlayingChapter.id`); при 404 (старый сервер) — цикл `PATCH api/me/progress/{itemId}/{episodeId}` |
| Сбросить | `GET api/me` → найти запись прогресса айтема → `DELETE api/me/progress/{recordId}` | перечислить все записи прогресса с `libraryItemId == item` → `DELETE` каждой (N запросов) |

Замечания:
- `MediaProgressResponse` (`common/model/MediaProgressResponse.kt`) не содержит `id` записи —
  добавить поле (сервер его отдаёт в `getOldMediaProgress`).
- Снятие отметки (`isFinished: false`) сервер сам обнуляет `currentTime` и progress —
  повторного запроса не нужно.
- Очереди синхронизации оффлайна в приложении нет (проверено) → при ошибке сети показываем
  штатную ошибку, как предписано спекой («если он есть; иначе …»).

### Б.2. Состояние «отмечен прослушанным» для подкаста

У подкаста нет item-level `isFinished` — только per-episode. Определяем так:
**подкаст отмечен ⇒ у всех выпусков есть прогресс с `isFinished` (и выпусков ≥ 1)**.
Книга — `progress.isFinished`. Это нужно для переключения текста «Отметить…» ↔ «Снять отметку».

### Б.3. Дефолт сортировки — в спеке внутреннее противоречие

Таблица 5.1: дефолтное направление `PUBLISHED_AT` — убывание (новые первыми).
Раздел 7: дефолт обязан совпадать с текущим поведением, а текущее поведение —
**старые первыми** (см. А.4). Спека сама разрешает конфликт в пользу раздела 7.
Решение:
- фолбэк `EpisodeOrdering(PUBLISHED_AT, ascending = true)`;
- «направление по умолчанию» из таблицы применяется только когда пользователь **явно
  выбирает другой ключ**;
- тап по уже выбранному ключу — инверсия (из дефолта: первый тап по «Дате выхода» → новые первыми,
  ровно как просит #239).

### Б.4. Локализации

Спека 0.4 требует скопировать английский во все 26 локалей. Файлы локалей ведёт Weblate
(коммиты «Translated using Weblate …») — ручные копии создадут конфликт-шум и «переведённые»
строки на английском. Конвенция репозитория (`docs/sleep-timer-fade-plan.md`): **en
(`values/strings.xml`) + ru (`values-ru/strings.xml`)**, остальные откатываются на en.
Делаем по конвенции репозитория.

### Б.5. FAB сворачивания оглавления

В `PlayingQueueComposable` есть FAB («свернуть» при скролле вниз). Спека его не упоминает
и не запрещает. Решение: **сохраняем** (существующее поведение, эквивалент перехода в
Collapsed). Если по факту мешает — удалим одним коммитом.

---

## В. Архитектура: единый источник порядка

Сейчас `PlayingChapter.start/end` — кумулятивные смещения «склеенного» аудиопотока; они
пекутся в конвертере и по ним живут: плейлист (`PlaybackService.bookToChapterMediaItems` →
`resolveChapterToFiles`), `calculateChapterIndexAndPosition`, «Глава N из M», prev/next,
калькуляция «остатка» в шите скачивания (`ContentCachingManager`), синхронизация прогресса.

Принцип: **`DetailedItem.chapters` всегда уже отсортирован и с пересчитанными start/end**.
Тогда все перечисленные потребители получают новый порядок бесплатно (п. 8.5 спеки) и
никаких «своих копий» не появляется.

Точки:

1. **Модель** (`domain/DetailedItem.kt`): расширить `PlayingChapter` полями сортировки —
   `publishedAt: Long? = null`, `season: Int? = null`, `episodeNumber: Int? = null`,
   `filename: String? = null` (значения по умолчанию → книги и локальные файлы не затронуты;
   `Serializable` сохранён).
2. **Конвертер** (`PodcastResponseConverter`): убрать `orderEpisode()`; парсить `pubDate` →
   epoch ms (тот же `SimpleDateFormat`, локально на вызов), заполнять новые поля, выпуски
   остаются в серверном порядке; `start/end` и `totalCurrentTime` считать через движок
   (п.3) с фолбэк-порядком — поведение 1-в-1 как сейчас.
3. **Движок** (`common/EpisodeOrderingEngine.kt`, чистые функции):
   - `fun List<PlayingChapter>.sortedBy(ordering): List<PlayingChapter>` — стабильная;
     PUBLISHED_AT: nulls last; TITLE: `String.CASE_INSENSITIVE_ORDER`; SEASON/EPISODE:
     число если парсится, иначе строка, nulls last; FILENAME: natural compare
     (сегменты цифр/букв, цифры по значению — `Часть 2 < Часть 10`); `ascending=false` —
     `reversed()` от стабильного возрастающего.
   - `fun recomputeOffsets(chapters): List<PlayingChapter>` — префиксные суммы duration.
   - Unit-тесты (`app/src/test/.../common/EpisodeOrderingEngineTest.kt`): каждый ключ ×
     оба направления, nulls, natural sort, стабильность, ties.
4. **Хранение** (§7): Room-таблица `item_preferences(item_id TEXT PK, sort_key TEXT,
   sort_ascending INTEGER NOT NULL)` — новой сущностью в `LocalCacheStorage` (bump версии +
   миграция в `Migrations.kt` + schema json, CI собирает с `-Proom.schemaLocation`),
   `ItemPreferenceDao`, провайдер в `LocalCacheModule`, репозиторий
   `ItemPreferencesRepository.observeOrdering(itemId): Flow<EpisodeOrdering>` /
   `setOrdering(itemId, ordering)`; отсутствие записи → фолбэк Б.3.
5. **Применение к играющему айтему**: в `LissenMediaProvider.fetchBook` (или у входа в
   `playingBook`) — combine(книга, observeOrdering(itemId)) → для PODCAST применять
   `sortedBy + recomputeOffsets`, для книг — как есть. Кэш (`CachedBookProvider`) хранит
   сырой ответ, сортировка — поверх, при выдаче.
6. **Пересборка плейлиста на лету** (§8.4): новый `PlaybackCommand.ReorderPlaylist(item)`
   в `PlaybackEventBus` (рядом с `PreparePlayback`, `PlaybackService.kt:66`). Обработчик:
   взять `currentEpisodeId = chapters[mediaItemIndex].id` и позицию внутри эпизода,
   `bookToChapterMediaItems(пересобранная книга)`, `exoPlayer.setMediaItems(items, newIndex,
   posMs)` — на живом экземепплере, без release/prepare заново и без трогания аудиофокуса;
   `playWhenReady` не меняется. `MediaId(book.id, index)` в item'ах обновляется автоматически.

## Г. UI

### Г.1. Состояния (`PlayerViewModel`)

Заменить `playingQueueExpanded: Boolean` на `tocState: Collapsed | Expanded | Search`
(`searchRequested` схлопнуть в него; `updateSearch/dismissSearch` остаются).
`BackHandler` в `PlayerScreen.kt:163` перестроить на цепочку Search → Expanded → Collapsed → выход.

### Г.2. `PlayerScreen` (портрет)

- Разделить `PlayerArtworkAndControls` на **обложку** (её и анимировать
  `expandVertically/shrinkVertically` по `tocState == Collapsed`) и **блок контролов**
  (название, «Глава N из M», слайдер, транспорт — виден всегда). Сейчас под
  `AnimatedVisibility` лежит весь блок целиком (`PlayerScreen.kt:364-378`) — это главное
  отличие от спеки 4.3 («исчезает только обложка»).
- Заголовок секции: в `PlayingQueueComposable` статичный `Text`
  (`PlayingQueueComposable.kt:246-256`) → кликабельный `Row` (48dp, `Role.Button`,
  `stateDescription`, иконка `ArrowDropDown/ArrowDropUp`, тексты `player_toc_expand/collapse`).
  В `Collapsed` заголовок тоже показан (сейчас показан — сохранить).
- Топбар: Collapsed — Bookmarks+Info; Expanded — только Search; Search — поле в слоте title
  (перенести `ChapterSearchActionComposable` из actions в title, стиль по спеке 4.4,
  placeholder `player_toc_search_hint`, Close чистит текст).
- Landscape: поведение как сейчас (twoPane), только ряд плиток меняется.

### Г.3. Ряд плиток (`NavigationBarComposable`)

- Убрать NavigationBarItem «Главы» (и `selected = playingQueueExpanded`).
- Добавить «Настройки» (`Icons.Outlined.Settings`, `player_tile_settings`) → новый шит.
- Снять `enabled = hasEpisodes` со всех (спека 11: не дизейблить); проверить, что шиты
  скачивания/таймера не падают на пустом списке (у локальных однофайловых книг chapters
  непустой — одна «глава»; реальный пустой список — только ошибка загрузки, плейсхолдер
  `PlayingQueueFallbackComposable` остаётся).
- Обновить `NavigationBarPlaceholderComposable`.

### Г.4. Шит `ItemSettingsSheet` (новый, `ui/screens/player/composable/`)

`ModalBottomSheet(skipPartiallyExpanded = true)`, без заголовка. Строки — по образцу
`QuickSettingsComposable.kt` (раскрывающиеся секции «Группировка/Сортировка» — тот же
паттерн; при возможности вынести общую строку, но без рефактора шита библиотеки).
Содержимое — спека 5.1/5.2; состояние «отмечен» — по Б.2. Секция сортировки: 5 ключей,
короткие имена, стрелки направления, semantics `selected`/`stateDescription` (§10).

### Г.5. Действия и диалог

- `PlayerViewModel`: `markFinished(Boolean)`, `resetProgress()` → новые методы
  `LissenMediaProvider` → `MediaChannel`/`AudioBookshelfRepository` (вызовы по таблице Б.1;
  в `AudiobookshelfApiClient` добавить `@PATCH api/me/progress/{itemId}`,
  `@PATCH api/me/progress/{itemId}/{episodeId}`, `@PATCH api/me/progress/batch/update`,
  `@DELETE api/me/progress/{recordId}`; `id` в `MediaProgressResponse`).
- После mark/unmark: обновить локальный кэш прогресса (`media_progress` таблица Room).
- После reset: очистить локальный прогресс; если играет — остановить и в начало первой
  главы нового порядка (команда сервису тем же каналом `PlaybackEventBus`).
- Диалог сброса — `AlertDialog` по спеке 6.2 (кнопка confirm в `error`), тексты
  `item_reset_progress_text_book/_podcast`.

## Д. Строки и тесты

- Строки: таблица спеки 9 в `values/strings.xml` (en) + `values-ru` (Б.4). Удалить
  `player_screen_chapter_list_navigation_*`, если остались неиспользуемыми (проверить
  placeholder'ы).
- Unit: `EpisodeOrderingEngineTest` (новый), `PodcastResponseConverterTest` — обновить под
  новые поля/поведение, тесты конвертеров прогресса где заденет.
- E2E (`minifiedTest`): `PlaybackFlowE2ETest`/`LibraryFlowE2ETest` завязаны на ряд плиток и
  `chapterList` — обновить теги/сценарии под новые плитки и заголовок-переключатель.
- Прогон: `./gradlew build` (CI-эквивалент: `./gradlew build -Proom.schemaLocation=$PWD/app/schemas`).

## Е. Порядок коммитов (каждый — зелёная сборка)

1. `docs`: спека + план (этот коммит).
2. Модель + движок сортировки + unit-тесты (дефолт = текущее поведение, UI не меняется).
3. Room `item_preferences` + репозиторий + применение в выдаче `fetchBook`.
4. `PlaybackCommand.ReorderPlaylist` + пересборка плейлиста.
5. UI-состояния: заголовок-переключатель, обложка отдельно от контролов, поиск в title.
6. Плитки: «Настройки» вместо «Главы», без дизейбла; плейсхолдеры; E2E.
7. Шит `ItemSettingsSheet` + диалог сброса.
8. API прогресса + ViewModel-действия + локальный кэш.
9. Строки, a11y-полировка, чистка неиспользуемых ресурсов.

## Ж. Оценка и риски

Оценка: 3–5 рабочих дней. Длинные хвосты: пересборка плейлиста на живом плеере (Ж.1),
E2E (Ж.4), объём UI-состояний.

- **Ж.1.** `setMediaItems` на подготовленном экземепплере не должен ронять уведомления /
  MediaSession — проверять на реальном устройстве со сменой порядка во время игры;
  позиция внутри эпизода берётся из `player.currentPosition`, не из кумулятивной.
- **Ж.2.** Смена порядка меняет смысл кумулятивного `currentTime`, который уходит в
  синхронизацию прогресса (`PlaybackSynchronizationService`): после reorder следующий
  sync должен отправить новое кумулятивное значение для текущего эпизода — проверить,
  что серверная позиция «переезжает» корректно (эпизод-то тот же).
- **Ж.3.** Подкаст на сотни выпусков: reset = N DELETE, mark = 1 batch. Делать
  последовательно, ошибки — штатным тостом; откат частичного успеха не гарантируем
  (ABS не транзакционный) — приемлемо.
- **Ж.4.** E2E на change поведения плиток почти наверняка покраснеями — закладывать в фазу 6.
- **Ж.5.** `values-night` и adaptive-layout (`isWideLayout`) — проверить оба в обеих темах.

## З. Чек-лист acceptance (зеркало спеки 12, с поправками)

Как в спеке §12, плюс:
- [ ] Дефолт без записи в БД = старый порядок (старые первыми) — пользователи не видят изменений.
- [ ] Вызовы ABS соответствуют таблице Б.1 (не буквальному тексту §6).
- [ ] `./gradlew build` зелёный, lint без unused resources.
