# Sleep timer fade-out — план реализации

## Цель

В настройках воспроизведения появляется раздел «Sleep timer» (рядом с «Seek settings»), ведущий
на отдельный экран с двумя строками:

1. **Fade out** — тоггл «плавное затухание».
2. **Fade duration** — строка, открывающая модалку с длительностью фейда (слайдер + пресеты).
   Строка **disabled**, пока тоггл выключен. Слайдер на экран не выносим — только модалка.

Бэкенд (плавно гасить `player.volume` в последние N секунд таймера и откатывать громкость
обратно) — отдельный `RunningComponent`, зарегистрированный как остальные.

Локализации: `values/strings.xml` (en, дефолт) + `values-ru/strings.xml`. Остальные локали
откатятся на en — это приемлемо.

---

## Поток данных

```
PlaybackTimer (тик каждые 500мс)
   ├─ TimerTick(remainingSeconds) ─┐
   ├─ TimerExpired                 ├─> PlaybackEventBus ─> SleepTimerFadeService ─> exoPlayer.volume
   └─ TimerCancelled (НОВЫЙ)      ─┘                            │
                                                                v
                                              PlaybackPreferences (fade on/off, fade seconds)
                                                                ^
SleepTimerSettingsScreen <─ SettingsViewModel <─────────────────┘
```

Ключевой gap: сейчас при отмене таймера (`PlaybackCommand.CancelTimer` → `PlaybackTimer.stopTimer()`,
`PlaybackService.kt:159`) в шину **не уходит ни одно событие** — если фейд был активен, громкость
останется придушенной навсегда. Поэтому нужно новое событие `TimerCancelled`.

`player.volume` больше нигде в приложении не пишется (громкость буста идет через
`LoudnessEnhancer` на аудио-сессии), поэтому конфликтующих writers нет; при усилении бустом
зануленный `player.volume` честно дает тишину.

---

## 1. Бэкенд

### 1.1 `playback/PlaybackEventBus.kt` — новое событие

```kotlin
sealed class PlaybackEvent {
  data object PlaybackReady : PlaybackEvent()

  data object TimerExpired : PlaybackEvent()

  data object TimerCancelled : PlaybackEvent()

  data class TimerTick(
    val remainingSeconds: Long,
  ) : PlaybackEvent()
}
```

### 1.2 `playback/service/PlaybackTimer.kt` — эмит при остановке

В `stopTimer()` (сейчас строки 77–83) эмитим `TimerCancelled`, только если таймер реально был:

```kotlin
fun stopTimer() {
  Timber.d("Stopping timer")
  timer?.let { playbackEventBus.emit(PlaybackEvent.TimerCancelled) }
  timer?.cancel()
  timer = null

  exoPlayer.removeListener(playerListener)
}
```

Затрагивает три пути: ручная отмена, замена таймера (`startTimer` вызывает `stopTimer`),
истечение (`onFinished` → уже эмитит `TimerExpired`, потом `stopTimer` добавит `TimerCancelled`).
Для фейд-сервиса оба события идемпотентны (просто восстановление), так что дублирование безопасно.

### 1.3 `playback/MediaRepository.kt` — exhaustive `when`

Добавить пустую ветку рядом с `is PlaybackEvent.TimerExpired ->` (~строка 142):

```kotlin
is PlaybackEvent.TimerCancelled -> {}
```

### 1.4 Новый `playback/SleepTimerFadeService.kt`

Паттерн — `PlaybackEnhancerService` (Singleton + RunningComponent + свой scope).
`player.volume` обязан ставиться из application thread → `Dispatchers.Main`.

```kotlin
package org.grakovne.lissen.playback

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.grakovne.lissen.common.RunningComponent
import org.grakovne.lissen.persistence.preferences.PlaybackPreferences
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SleepTimerFadeService
  @OptIn(UnstableApi::class)
  @Inject
  constructor(
    private val player: ExoPlayer,
    private val playbackEventBus: PlaybackEventBus,
    private val preferences: PlaybackPreferences,
  ) : RunningComponent {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var fadeJob: Job? = null
    private var fadeStarted = false
    private var originalVolume = 1f

    override fun onCreate() {
      scope.launch {
        playbackEventBus.events.collect { event ->
          when (event) {
            is PlaybackEvent.TimerTick -> onTick(event.remainingSeconds)
            PlaybackEvent.TimerExpired -> restoreVolume()
            PlaybackEvent.TimerCancelled -> restoreVolume()
            else -> {}
          }
        }
      }
    }

    private fun onTick(remainingSeconds: Long) {
      val fadeSeconds =
        if (preferences.isSleepTimerFadeEnabled()) {
          preferences.getSleepTimerFadeSeconds()
        } else {
          0
        }

      if (fadeSeconds <= 0 || remainingSeconds > fadeSeconds) {
        restoreVolume()
        return
      }

      if (!fadeStarted) {
        fadeStarted = true
        originalVolume = player.volume
        Timber.d("Sleep timer fade started, volume=$originalVolume, fadeSeconds=$fadeSeconds")
      }

      val target = computeFadeVolume(remainingSeconds, fadeSeconds, originalVolume)
      fadeJob?.cancel()
      fadeJob = scope.launch { rampTo(target) }
    }

    private suspend fun rampTo(target: Float) {
      val start = player.volume
      repeat(RAMP_STEPS) { i ->
        val fraction = (i + 1f) / RAMP_STEPS
        player.volume = (start + (target - start) * fraction).coerceIn(0f, 1f)
        delay(RAMP_INTERVAL_MILLIS)
      }
    }

    private fun restoreVolume() {
      fadeJob?.cancel()
      fadeJob = null
      if (fadeStarted) {
        player.volume = originalVolume
        fadeStarted = false
        Timber.d("Sleep timer fade reverted, volume=$originalVolume")
      }
    }

    companion object {
      private const val RAMP_STEPS = 20
      private const val RAMP_INTERVAL_MILLIS = 50L
    }
  }

internal fun computeFadeVolume(
  remainingSeconds: Long,
  fadeSeconds: Int,
  originalVolume: Float,
): Float =
  if (fadeSeconds <= 0) {
    originalVolume
  } else {
    (originalVolume * remainingSeconds.toFloat() / fadeSeconds.toFloat()).coerceIn(0f, 1f)
  }
```

Механика: тики приходят каждые 500мс с разрешением в секунду. Каждый тик внутри окна фейда
отменяет предыдущий рампу и запускает новый к чуть более низкой цели за 1с → непрерывное
плавное снижение вместо ступенек. `computeFadeVolume` — чистая функция, вынесена для юнит-тестов
(ExoPlayer в JVM-тесте не подделать).

### 1.5 Новый `playback/SleepTimerFadeModule.kt`

Копия `PlaybackEnhancerModule`:

```kotlin
package org.grakovne.lissen.playback

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import org.grakovne.lissen.common.RunningComponent

@Module
@InstallIn(SingletonComponent::class)
interface SleepTimerFadeModule {
  @Binds
  @IntoSet
  fun bindSleepTimerFadeService(service: SleepTimerFadeService): RunningComponent
}
```

Больше ничего не нужно: `LissenApplication.initRunningComponents()` подхватит сам.

### 1.6 `persistence/preferences/PlaybackPreferences.kt`

Два ключа и геттеры/сеттеры. Flow-и не заводим: fade-сервис читает настройки синхронно на каждом
тике (чтобы смена настройки подхватывалась мгновенно), а ViewModel использует `MutableStateFlow`
по образцу `seekTime` — flow из стора был бы мертвым кодом:

```kotlin
fun isSleepTimerFadeEnabled(): Boolean = store.getBoolean(KEY_SLEEP_TIMER_FADE_ENABLED, false)

fun saveSleepTimerFadeEnabled(value: Boolean) = store.putBoolean(KEY_SLEEP_TIMER_FADE_ENABLED, value)

fun getSleepTimerFadeSeconds(): Int =
  store.getInt(KEY_SLEEP_TIMER_FADE_SECONDS, DEFAULT_SLEEP_TIMER_FADE_SECONDS)
    .coerceIn(MIN_SLEEP_TIMER_FADE_SECONDS, MAX_SLEEP_TIMER_FADE_SECONDS)

fun saveSleepTimerFadeSeconds(seconds: Int) =
  store.putInt(KEY_SLEEP_TIMER_FADE_SECONDS, seconds.coerceIn(MIN_SLEEP_TIMER_FADE_SECONDS, MAX_SLEEP_TIMER_FADE_SECONDS))
```

В `companion object` (рядом с `KEY_VOLUME_BOOST`, ~строка 196):

```kotlin
private const val KEY_SLEEP_TIMER_FADE_ENABLED = "sleep_timer_fade_enabled"
private const val KEY_SLEEP_TIMER_FADE_SECONDS = "sleep_timer_fade_seconds"
const val MIN_SLEEP_TIMER_FADE_SECONDS = 5
const val MAX_SLEEP_TIMER_FADE_SECONDS = 60
const val DEFAULT_SLEEP_TIMER_FADE_SECONDS = 30
```

---

## 2. UI

### 2.1 `ui/navigation/AppNavigationService.kt`

```kotlin
fun showSleepTimerSettings() = host.navigate(ROUTE_SETTINGS_SLEEP_TIMER)
```

+ константа `ROUTE_SETTINGS_SLEEP_TIMER = "settings_sleep_timer"` в companion (рядом с `ROUTE_SETTINGS_SEEK`).

### 2.2 `ui/navigation/AppNavHost.kt`

По образцу `ROUTE_SETTINGS_SEEK` (~строка 216):

```kotlin
composable(route = ROUTE_SETTINGS_SLEEP_TIMER) {
  SleepTimerSettingsScreen(onBack = navigationService::goBack)
}
```

### 2.3 `ui/screens/settings/advanced/PlaybackPreferencesScreen.kt`

Сразу после `AdvancedSettingsNavigationItemComposable` с seek (~строка 92):

```kotlin
AdvancedSettingsNavigationItemComposable(
  title = stringResource(R.string.sleep_timer_settings_title),
  description = stringResource(R.string.sleep_timer_settings_description),
  onclick = { navController.showSleepTimerSettings() },
)
```

### 2.4 Новый `ui/screens/settings/advanced/SleepTimerSettingsScreen.kt`

Каркас — `SeekSettingsScreen` (Scaffold + SettingsTopAppBar + verticalScroll Column).
Структурно:

```kotlin
@Composable
fun SleepTimerSettingsScreen(onBack: () -> Unit) {
  val viewModel: SettingsViewModel = hiltViewModel()
  val fadeEnabled by viewModel.sleepTimerFadeEnabled.collectAsState()
  val fadeSeconds by viewModel.sleepTimerFadeSeconds.collectAsState()

  var durationExpanded by remember { mutableStateOf(false) }

  Scaffold(
    topBar = {
      SettingsTopAppBar(
        title = stringResource(R.string.sleep_timer_settings_title),
        onBack = onBack,
      )
    },
    modifier =
      Modifier
        .systemBarsPadding()
        .fillMaxHeight(),
    content = { innerPadding ->
      Column(
        modifier =
          Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        SettingsToggleItem(
          title = stringResource(R.string.sleep_timer_fade_title),
          description = stringResource(R.string.sleep_timer_fade_description),
          initialState = fadeEnabled,
        ) { viewModel.preferSleepTimerFadeEnabled(it) }

        FadeDurationRowComposable(
          seconds = fadeSeconds,
          enabled = fadeEnabled,
          onClicked = { durationExpanded = true },
        )
      }
    },
  )

  if (durationExpanded) {
    FadeDurationBottomSheet(
      currentSeconds = fadeSeconds,
      onDismissRequest = { durationExpanded = false },
      onUpdate = { viewModel.preferSleepTimerFadeSeconds(it) },
    )
  }
}
```

`FadeDurationRowComposable` — копия приватного `SeekTimeRowComposable` из `SeekSettingsScreen.kt`
с параметром `enabled` (по образцу `SettingsToggleItem`: при `enabled = false` — без `clickable`,
текст с `alpha = 0.4f`):

```kotlin
@Composable
private fun FadeDurationRowComposable(
  seconds: Int,
  enabled: Boolean,
  onClicked: () -> Unit,
) {
  val context = LocalContext.current

  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .let {
          when (enabled) {
            true -> it.clickable { onClicked() }
            false -> it
          }
        }.padding(horizontal = 24.dp, vertical = 12.dp),
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = stringResource(R.string.sleep_timer_fade_duration_title),
        style = typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
        modifier = Modifier.padding(bottom = 4.dp),
        color =
          when (enabled) {
            true -> colorScheme.onBackground
            false -> colorScheme.onBackground.copy(alpha = 0.4f)
          },
      )
      Text(
        text = context.resources.getQuantityString(R.plurals.fade_duration_seconds, seconds, seconds),
        style = typography.bodyMedium,
        color =
          when (enabled) {
            true -> colorScheme.onSurfaceVariant
            false -> colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
          },
      )
    }
  }
}
```

`FadeDurationBottomSheet` — копия `SeekTimeBottomSheet`: та же структура модалки
(`LissenModalBottomSheet`, заголовок, haptic `withHaptic(view)`, пресеты-кружки).
Единственное отличие — вместо `SeekTimeSlider` берем `CommonSlider` напрямую
(`ui/components/slider/CommonSlider.kt`), потому что `SeekTimeSlider` печатает заголовок
через `seek_interval_seconds` — текст про перемотку:

```kotlin
CommonSlider(
  internalValue = selectedSeconds.coerceIn(5, 60),
  range = 5..60,
  formatHeader = { value ->
    val v = value.roundToInt().coerceIn(5, 60)
    context.resources.getQuantityString(R.plurals.fade_duration_seconds, v, v)
  },
  formatIndex = { "$it" },
  labeledIndexes = (5..60 step 5).toList(),
  onUpdate = { selectedSeconds = it.roundToInt().coerceIn(5, 60) },
)
```

Пресеты — круглые кнопки, как в seek:

```kotlin
private val fadeTimePresets = listOf(5, 10, 15, 30, 60)
```

### 2.5 `viewmodel/SettingsViewModel.kt`

По образцу `_seekTime` (строки 129–130, 437–453):

```kotlin
private val _sleepTimerFadeEnabled = MutableStateFlow(playback.isSleepTimerFadeEnabled())
val sleepTimerFadeEnabled: StateFlow<Boolean> = _sleepTimerFadeEnabled.asStateFlow()

private val _sleepTimerFadeSeconds = MutableStateFlow(playback.getSleepTimerFadeSeconds())
val sleepTimerFadeSeconds: StateFlow<Int> = _sleepTimerFadeSeconds.asStateFlow()

fun preferSleepTimerFadeEnabled(value: Boolean) {
  Timber.d("User action: preferSleepTimerFadeEnabled $value")
  _sleepTimerFadeEnabled.value = value
  playback.saveSleepTimerFadeEnabled(value)
}

fun preferSleepTimerFadeSeconds(seconds: Int) {
  Timber.d("User action: preferSleepTimerFadeSeconds $seconds")
  _sleepTimerFadeSeconds.value = seconds
  playback.saveSleepTimerFadeSeconds(seconds)
}
```

---

## 3. Бэкап настроек

`persistence/preferences/SettingsBackup.kt` — additive nullable-поля (schemaVersion не bumped,
как с остальными полями):

```kotlin
val sleepTimerFadeEnabled: Boolean? = null,
val sleepTimerFadeSeconds: Int? = null,
```

`SettingsBackupManager.exportSettings()`:

```kotlin
sleepTimerFadeEnabled = playback.isSleepTimerFadeEnabled(),
sleepTimerFadeSeconds = playback.getSleepTimerFadeSeconds(),
```

`SettingsBackupManager.importSettings()`:

```kotlin
backup.sleepTimerFadeEnabled?.let { playback.saveSleepTimerFadeEnabled(it) }
backup.sleepTimerFadeSeconds?.let { playback.saveSleepTimerFadeSeconds(it) }
```

---

## 4. Локализации

`app/src/main/res/values/strings.xml`:

```xml
<string name="sleep_timer_settings_title">Sleep timer</string>
<string name="sleep_timer_settings_description">Fade out before playback stops</string>
<string name="sleep_timer_fade_title">Fade out</string>
<string name="sleep_timer_fade_description">Gradually reduce volume before the sleep timer stops playback</string>
<string name="sleep_timer_fade_duration_title">Fade duration</string>
<plurals name="fade_duration_seconds">
    <item quantity="one">%d second</item>
    <item quantity="other">%d seconds</item>
</plurals>
```

`app/src/main/res/values-ru/strings.xml`:

```xml
<string name="sleep_timer_settings_title">Таймер сна</string>
<string name="sleep_timer_settings_description">Плавное затухание перед остановкой воспроизведения</string>
<string name="sleep_timer_fade_title">Плавное затухание</string>
<string name="sleep_timer_fade_description">Постепенно уменьшать громкость перед остановкой воспроизведения</string>
<string name="sleep_timer_fade_duration_title">Длительность затухания</string>
<plurals name="fade_duration_seconds">
    <item quantity="one">%d секунда</item>
    <item quantity="few">%d секунды</item>
    <item quantity="many">%d секунд</item>
    <item quantity="other">%d секунды</item>
</plurals>
```

---

## 5. Edge cases (что заложено в дизайн)

| Ситуация | Поведение |
|---|---|
| Отмена таймера в разгар фейда | `TimerCancelled` → `restoreVolume()` → громкость прежняя |
| Замена таймера в разгар фейда | `stopTimer` → restore; новые тики: remaining > fade → тишина-норма, remaining ≤ fade → рампа продолжается от текущего значения |
| Выключение тоггла в разгар фейда | `onTick` перечитывает prefs каждый тик → следующий тик восстановит громкость |
| Увеличение длительности фейда тиком раньше | следующий же тик пересчитает окно, рампа подхватит |
| Пауза в разгар фейда (опция «до конца главы») | таймер на паузе → тики стоят → громкость заморожена на текущем уровне; после resume фейд продолжается |
| Пауза при опции «N минут» | таймер тикает и на паузе (существующее поведение) → фейд дойдет до нуля; при play воспроизведение сразу встанет — согласуется с текущей семантикой |
| Включенный volume boost (LoudnessEnhancer) | `player.volume` — отдельная ручка на AudioTrack, 0 × усиление = 0, фейд работает |
| Убийство процесса в разгар фейда | `player.volume` не персистится, при пересоздании плеера = 1f |
| `TimerExpired` | `MediaRepository` паузит; fade-сервис восстанавливает громкость, чтобы следующий play был нормальным |
| restore без активного фейда | no-op (`fadeStarted == false`) |

---

## 6. Тесты

1. **Новый `app/src/test/kotlin/org/grakovne/lissen/playback/SleepTimerFadeVolumeTest.kt`** —
   чистая функция `computeFadeVolume`:
   - `remaining > fadeSeconds` → original (в т.ч. граница `remaining == fadeSeconds + 1`);
   - `remaining == fadeSeconds` → original;
   - середина (`remaining=15, fade=30, original=1f`) → `0.5f`;
   - `remaining = 0` → `0f`;
   - `originalVolume = 0.5f` → пропорция от 0.5;
   - `fadeSeconds = 0` → original (защита от деления);
   - clamp: `remaining < 0` (защита) → `0f`.

2. **`SettingsBackupManagerTest`** (расширить существующий, рядом со строками 170/399):
   - export содержит `sleepTimerFadeEnabled=true`, `sleepTimerFadeSeconds=45`;
   - import применяет оба значения в `PlaybackPreferences`;
   - import бэкапа с `null`-полями оставляет дефолты (`false` / `30`).

3. **`PlaybackEventBusTest`** — эмит `TimerCancelled` проходит через шину (по образцу существующих кейсов).

Регрессия затронутого `when` в `MediaRepository` — компилятором (exhaustive), отдельный тест не нужен.

---

## 7. Порядок работ и проверка

1. Бэкенд: `PlaybackEvent` → `PlaybackTimer` → `MediaRepository` → `SleepTimerFadeService` + module → prefs.
2. UI: навигация → экран → модалка → ViewModel.
3. Бэкап + строки en/ru.
4. Тесты.

```
./gradlew formatKotlin && ./gradlew lintKotlin testDebugUnitTest assembleDebug
```

Ручная QA-проверка на эмуляторе:
- таймер 1 мин, фейд 30с, тоггл on → на последних 30с громкость плавно уходит в ноль, стоп;
- отмена таймера на 20-й секунде фейда → громкость сразу прежняя;
- тоггл off → строка длительности некликабельна и приглушена;
- фейд off во время активного фейда → громкость возвращается на следующем тике;
- export/import конфига переносит обе настройки.

---

## Итого файлов

**Новые (5):** `SleepTimerFadeService.kt`, `SleepTimerFadeModule.kt`, `SleepTimerSettingsScreen.kt`,
`SleepTimerFadeVolumeTest.kt` (юниты чистой функции), `SleepTimerFadeServiceTest.kt`
(интеграция: шина + сервис + мок плеера на виртуальном времени).

**Правки (13):** `PlaybackEventBus.kt`, `PlaybackTimer.kt`, `MediaRepository.kt`,
`PlaybackPreferences.kt`, `SettingsBackup.kt`, `SettingsBackupManager.kt`,
`Route.kt`, `AppNavigationService.kt`, `AppNavHost.kt`, `PlaybackPreferencesScreen.kt`,
`SettingsViewModel.kt`, `values/strings.xml`, `values-ru/strings.xml`;
тесты: `SettingsBackupManagerTest.kt`, `SettingsViewModelTest.kt`, `PlaybackEventBusTest.kt`.
