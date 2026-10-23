# i2pd for Android — дизайн нового приложения

Статус: черновик на согласование · Октябрь 2026

## 1. Цели и не-цели

**Цели**

1. Управление демоном i2pd через JNI: запуск, перезапуск, остановка — штатная (graceful) и быстрая.
2. Надёжная работа в фоне, в том числе после закрытия активити и удаления задачи из «недавних».
3. Графическая настройка `i2pd.conf` и туннелей (`tunnels.conf`, `tunnels.d/*.conf`) без потери комментариев и ручных правок.
4. Отображение состояния: жизненный цикл демона, сетевой статус роутера, трафик, туннели, клиентские сервисы, логи.

**Не-цели (на этом этапе)**

- VPN/`VpnService` и прозрачное проксирование трафика устройства.
- Встроенный браузер или мессенджер. Веб-консоль i2pd остаётся в WebView.
- Изменения протокола или логики роутера в самом i2pd. Правки upstream — только минимальные и только если без них никак (см. §5.6).
- Standalone-бинарник (`binary/jni`, `contrib/binary_pack`) — не трогаем, он живёт отдельно.

## 2. Платформа и ограничения

| Параметр | Значение | Комментарий |
|---|---|---|
| `minSdk` | 23 (Android 6.0) | Текущий базовый уровень AndroidX и Compose (BOM ≥ 2025.12) |
| `targetSdk` | 36 (Android 16) | |
| `compileSdk` | 37 | Требуется Compose 1.12 и свежими AndroidX. На поведение приложения не влияет, оно определяется `targetSdk` |
| build-tools | 36.0.0 | Версия по умолчанию для AGP 9.4.1 |
| Язык / UI | Kotlin, Jetpack Compose, Material 3 | Dynamic color на 31+ |
| ABI | `armeabi-v7a`, `arm64-v8a`, `x86`, `x86_64` | Сплиты по ABI + universal, как сейчас |
| NDK | актуальный LTS (r28+) | Выравнивание под страницы 16 KB по умолчанию (обязательно для target 35+) |
| Зависимости | Только AndroidX и Kotlin | Без Google Play Services и проприетарных библиотек — F-Droid |
| `applicationId` | `org.purplei2p.i2pd` | Обновление поверх старой версии, ключ подписи тот же |

Что требует target 36 и как это учитываем:

- **FGS-тип обязателен (34+).** Используем `specialUse` с `PROPERTY_SPECIAL_USE_FGS_SUBTYPE`. Почему не `dataSync`: на Android 15 у него лимит 6 часов в сутки, и его нельзя запускать из `BOOT_COMPLETED`.
- **`POST_NOTIFICATIONS` (33+)** — runtime-разрешение. Без него сервис работает, но уведомление скрыто. Просим разрешение на онбординге.
- **Edge-to-edge (35+)** и **predictive back (36)** — Compose закрывает это через `enableEdgeToEdge()` и insets.
- **`startForeground()`** вызываем сразу в `onStartCommand` с уведомлением «Запуск…», не дожидаясь демона.
- **Ограничения на старт FGS из фона (31+).** Источники старта и их статус — в §7.3.

## 3. Ключевые решения

| # | Решение | Почему |
|---|---|---|
| D1 | Демон работает в **отдельном процессе** `:router` | Глобальное состояние i2pd нельзя переинициализировать: `config::Init()` дописывает опции в глобальный `m_OptionsDesc`, `variables_map` не перезаписывается, роутер, netdb, транспорты и туннели — синглтоны с потоками. `ParseConfig` при ошибке вызывает `exit(EXIT_FAILURE)` и убивает весь процесс. Отдельный процесс даёт честный перезапуск, изоляцию падений и гарантированно быструю остановку |
| D2 | **Каждый запуск — новый процесс.** После остановки процесс `:router` завершается сам | Следствие D1: второй `Daemon.init()` в том же процессе ненадёжен |
| D3 | Супервизор — foreground-сервис в **главном процессе**. Процесс `:router` только привязан (bound) к нему | Супервизор переживает падение и перезапуск демона, держит уведомление, таймеры graceful-остановки и автоперезапуск. Привязка от FGS поднимает приоритет процесса `:router` до уровня FGS |
| D4 | Graceful-остановку реализуем сами в супервизоре | В Android-сборке i2pd graceful выключен (`#if ... !defined(ANDROID)` в `HTTPServer.cpp`) |
| D5 | Статусы — опрос нативного снимка раз в секунду в процессе `:router` и push в супервизор | Колбэков об изменениях у i2pd нет. Сам HTTP-сервер читает эти же счётчики из своего потока |
| D6 | datadir — приватный: `filesDir/i2pd`, `allowBackup=false` | Не нужны разрешения на хранилище, ключи роутера не попадают в бэкапы. Старые данные из `/sdcard/i2pd` переносим через SAF (§10.8) |
| D7 | Конфиги правим собственным **lossless INI-редактором** | Комментарии, порядок, неизвестные ключи и форматирование остаются нетронутыми. Сейчас commons-configuration2 переписывает файл целиком |
| D8 | Схема опций извлекается **из самой нативной библиотеки** (`m_OptionsDesc`) | GUI всегда соответствует версии i2pd в APK. Вручную поддерживается только UI-оверлей: метки, группировка, типы виджетов |

## 4. Архитектура процессов

```mermaid
flowchart LR
  subgraph main["Процесс org.purplei2p.i2pd (главный)"]
    UI["MainActivity (Compose)<br/>ViewModels"]
    SUP["RouterService<br/>FGS specialUse, супервизор"]
    REPO["RouterStateRepository<br/>StateFlow&lt;RouterState&gt;"]
    CFG["ConfigRepository<br/>INI + схема"]
    NET["NetworkMonitor<br/>ConnectivityManager callback"]
    BOOT["BootReceiver"]
    TILE["QS TileService"]
  end
  subgraph router["Процесс :router"]
    HOST["DaemonHostService<br/>(bound, AIDL)"]
    JNI["NativeDaemon (JNI)"]
    LIB["libi2pd.so<br/>i2pd + boost + openssl + miniupnpc"]
  end
  UI <--> REPO
  UI --> CFG
  SUP --> REPO
  NET --> SUP
  BOOT --> SUP
  TILE --> SUP
  SUP <-- "AIDL IDaemonHost / IDaemonCallback" --> HOST
  HOST --> JNI --> LIB
  CFG -. "файлы в filesDir/i2pd" .- LIB
```

- **UI и супервизор живут в одном процессе.** Они общаются напрямую через `RouterStateRepository` (`StateFlow`) и команды в `RouterController`; IPC между ними не нужен.
- **AIDL нужен только на границе** супервизор ↔ `:router`.
- **Процесс `:router` грузит только нативную библиотеку и минимум Kotlin**: никакого Compose, никаких ViewModel.
- **Удаление задачи из «недавних»** убивает активити, но не FGS. Супервизор продолжает работать и держит привязку к `:router` (подробности в §7).

## 5. Нативный слой

### 5.1. Структура

`app/jni` заменяется модулем `:native` с собственным `Android.mk` (ndk-build). Подготовка зависимостей остаётся в `binary/jni/build*.sh`, подробности в §13.

Нативные файлы:

- `jni_bridge.cpp` — регистрация методов через `RegisterNatives` в `JNI_OnLoad`. Так не нужны имена вида `Java_org_purplei2p_..._1JNI` и сгенерированный заголовок.
- `daemon_android.cpp` — обёртка над `Daemon` (`i2p::util::DaemonAndroid`): init, start, stop и сбор статуса.
- `log_sink.cpp` — `std::streambuf` для `Log::SendTo(std::shared_ptr<std::ostream>)`: кольцевой буфер строк плюс logcat в debug-сборках.
- `options_schema.cpp` — сериализация `m_OptionsDesc` в JSON (§10.3).

### 5.2. JNI API (Kotlin-сторона, `object NativeDaemon`)

```kotlin
// Вызывается один раз за жизнь процесса :router
external fun init(dataDir: String, args: Array<String>, language: String): InitResult // ok | error(message)
external fun start(): String?            // null = ok, иначе текст ошибки
external fun stop()                      // Daemon.stop(), блокирующий
external fun status(): NativeStatus      // снимок, см. §9
external fun setAcceptsTransit(accept: Boolean)
external fun reloadTunnels()             // i2p::client::context.ReloadConfig()
external fun setOnline(online: Boolean)  // i2p::transport::transports.SetOnline()
external fun readLog(sinceSeq: Long, max: Int): LogChunk
external fun setFatalHandler(cb: FatalHandler) // обёртка над i2p::log::SetThrowFunction
external fun describeOptions(): String   // JSON схемы, см. §10.3; вызывается и в главном процессе
external fun version(): String           // I2PD_VERSION
```

Аргументы при `init` формирует супервизор. Это даёт явные пути и не зависит от `DetectDataDir()`, которое на Android ищет `$EXTERNAL_STORAGE`:

```
--datadir=<filesDir>/i2pd
--conf=<datadir>/i2pd.conf
--tunconf=<datadir>/tunnels.conf
--tunnelsdir=<datadir>/tunnels.d
--certsdir=<datadir>/certificates
```

Лог-поток передаём через перегрузку `Daemon.init(argc, argv, std::shared_ptr<std::ostream> logstream)`. При непустом `logstream` i2pd игнорирует опции `log`/`logfile` и пишет только в наш поток (`Daemon.cpp`, ветка `if (logstream)`).

### 5.3. Ошибки и фатальные выходы

- **Исключения** `init`/`start` (`boost::exception`, `std::exception`, `...`) ловим на нативной стороне и возвращаем текстом, как сейчас.
- **`ThrowFatal`** (ошибка конфига, нечитаемый файл): через `SetThrowFunction` сообщение синхронно уходит в Kotlin, а оттуда oneway-вызовом `IDaemonCallback.onFatal(msg)` в супервизор. После этого i2pd вызывает `exit()`, и процесс `:router` умирает. Супервизор получает `binderDied` и уже знает причину: состояние `Failed(ConfigError(msg))`.
- **Нативный краш** (SIGSEGV и т.п.) выглядит как `binderDied` без предварительного `onFatal`: состояние `Failed(Crashed)`. Подробности есть в tombstone/logcat. В UI показываем последние строки нашего лог-буфера: супервизор периодически их забирает.

### 5.4. Остановка в нативе

`stop()` вызывает `Daemon.stop()`: клиенты, контекст, туннели, UPnP, NTP, транспорты, NetDb, HTTP, I2PControl, крипто, логгер. В процессе сохраняются профили и RouterInfo. Watchdog на Kotlin-стороне описан в §6.3.

### 5.5. Логи

- Наш `std::ostream` передаём в `Daemon.init(..., logstream)`. Строки кладём в кольцевой буфер (по умолчанию 5000 строк) с монотонным `seq`.
- Уровень берётся из `loglevel` в конфиге. В UI его можно поменять, это требует перезапуска (§10.6).
- Опции `log`/`logfile` при этом не действуют. Запись в файл `datadir/i2pd.log` с ротацией по размеру делает сам sink, если включена настройка приложения «Писать лог в файл». В GUI эти две опции скрываем.

### 5.6. Что, возможно, понадобится в upstream i2pd

- `m_OptionsDesc` не объявлен в `Config.h`, но определён как не-static. На первом этапе объявляем `extern` у себя. Корректнее добавить в upstream геттер `const options_description& GetOptionsDesc()`.
- По желанию: вынести счётчик graceful-остановки в `Daemon_Singleton`, чтобы веб-консоль на Android показывала «Graceful shutdown» и кнопку отмены. Не блокер.

## 6. Жизненный цикл демона

### 6.1. Состояния (`RouterState.lifecycle`)

```mermaid
stateDiagram-v2
  [*] --> Stopped
  Stopped --> Preparing: start
  Preparing --> Starting: ассеты и конфиги готовы, :router привязан
  Preparing --> Failed: ошибка подготовки
  Starting --> Running: start() == ok
  Starting --> Failed: init/start error, onFatal, binderDied
  Running --> Draining: stopGraceful
  Draining --> Running: cancelGraceful
  Draining --> Stopping: transit == 0 или дедлайн
  Running --> Stopping: stopNow
  Draining --> Stopping: stopNow
  Running --> Failed: binderDied (краш)
  Stopping --> Stopped: процесс :router завершён
  Failed --> Preparing: start / автоперезапуск
  Failed --> Stopped: dismiss
```

`Restart` — это не отдельное состояние, а флаг `restartPending` поверх `Draining`/`Stopping`. Когда процесс доходит до `Stopped`, супервизор сразу запускает `Preparing`.

### 6.2. Запуск

1. **`RouterController.start()`** вызывает `startForegroundService(RouterService, ACTION_START)`.
2. **`RouterService`**: `startForeground(...)` с текстом «Запуск…» и переход в `Preparing`.
3. **Подготовка (IO-диспетчер):**
   - создание datadir;
   - копирование ассетов: при первом запуске установки копируется всё; при обновлении приложения заменяются только сертификаты и `torrents/web`, конфиги, адресная книга и загрузки в `torrents/` не трогаются (удалённые или отключённые пользователем файлы не возвращаются);
   - предварительная валидация `i2pd.conf` по схеме (§10.5), чтобы ловить ошибки до запуска.
4. **`bindService(DaemonHostService)`** с `BIND_AUTO_CREATE | BIND_IMPORTANT`, затем `linkToDeath`. Переход в `Starting`.
5. **`host.start(params)`**: в `:router` на отдельном потоке выполняются `init()` и `start()`, результат приходит в `IDaemonCallback.onStarted()` или `onStartFailed(msg)`.
6. **`Running`**: `:router` начинает раз в секунду отправлять `onStatus(snapshot)`.

### 6.3. Остановка

| Режим | Поведение | Где используется |
|---|---|---|
| **Graceful** | `setAcceptsTransit(false)` → `Draining` с дедлайном (по умолчанию 10 мин, настраивается). Каждую секунду проверяем `transitTunnels`. При 0 или по дедлайну — быстрая остановка. Отменяется через `setAcceptsTransit(true)` | Кнопка «Остановить», действие в уведомлении |
| **Быстрая** | `host.stop()`: в `:router` вызывается `Daemon.stop()`, затем `Process.killProcess(myPid())`. Супервизор держит watchdog (8 с): если `binderDied` не пришёл, убивает процесс по PID (`Process.killProcess` работает для процессов того же UID) | «Остановить сейчас», перезапуск, выход |
| **Аварийная** | Сразу `killProcess` без `Daemon.stop()` | Повторное нажатие «Остановить сейчас», зависание `stop()` |

После `Stopped` супервизор выполняет `unbindService`, `stopForeground(STOP_FOREGROUND_REMOVE)` и `stopSelf()`. Главный процесс при закрытом UI система может спокойно выгрузить.

### 6.4. Перезапуск

`restart(graceful = false)`: выставляется `restartPending = true`, затем быстрая остановка (или graceful, если пользователь выбрал её). На `Stopped` — новый `bindService`, при этом система поднимает новый процесс `:router` с чистыми глобальными переменными. FGS всё время перезапуска не снимается, уведомление показывает «Перезапуск…».

### 6.5. Падения и автоперезапуск

- `binderDied` в `Running`/`Starting` без запрошенной остановки → `Failed(Crashed | ConfigError)`.
- Если включён «Автоперезапуск» (по умолчанию да): backoff 2 с → 10 с → 60 с, не больше 3 попыток за 10 минут. `ConfigError` автоматически не перезапускаем: без правки конфига результат будет тот же.
- Система тоже может убить `:router` при нехватке памяти. Это выглядит как краш и обрабатывается так же.

### 6.6. Операции над остановленным роутером

Часть операций с datadir безопасна только при остановленном демоне: те, что меняют файлы, которые работающий роутер пишет сам и сохраняет при остановке (личность роутера, ключи назначений, адресная книга, netDb, профили, теги — `ROUTER_WRITTEN_CATEGORIES`). Иначе при остановке i2pd записал бы старое состояние обратно. Конфиги, сертификаты и прочие файлы роутер только читает: их меняем без остановки, а применяем через баннеры «Перезапустить» и «Применить». Для первой группы в супервизоре есть `runWithRouterStopped(operation)`:

1. Если роутер работает, выполняется быстрая остановка, и супервизор запоминает, что его нужно запустить снова.
2. В состоянии `Stopped` на IO-диспетчере выполняется операция. Новые команды запуска в это время откладываются.
3. Если роутер работал до операции, он запускается снова. Если операция упала, ошибка возвращается в UI, а роутер всё равно запускается, если работал.

## 7. Работа в фоне

### 7.1. Сервисы в манифесте

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

<service android:name=".service.RouterService"
    android:exported="false"
    android:foregroundServiceType="specialUse"
    android:stopWithTask="false">
  <property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
      android:value="I2P router: relays encrypted traffic for the user and the I2P network" />
</service>

<service android:name=".daemon.DaemonHostService"
    android:exported="false"
    android:process=":router" />
```

Хранилищных разрешений нет совсем: ни `READ/WRITE_EXTERNAL_STORAGE`, ни `MANAGE_EXTERNAL_STORAGE`, ни `requestLegacyExternalStorage`.

### 7.2. Почему демон переживает «смахивание» из недавних

- `RouterService` — это FGS со `stopWithTask="false"` и `START_STICKY`. Удаление задачи вызывает у него `onTaskRemoved()`, а не остановку.
- Процесс `:router` привязан к FGS. Его importance вычисляется от клиента, то есть он тоже на уровне FGS: не замораживается cached-app freezer'ом и не выгружается раньше времени.
- Сетевые ограничения (Doze, App Standby, Data Saver, background network restrictions) применяются **к UID**, а оба процесса делят один UID. Пока у UID есть FGS, сеть доступна.
- Если система всё-таки убьёт главный процесс (`START_STICKY`): `RouterService` поднимется заново с `intent == null`. Сохранённое в `SharedPreferences` намерение `shouldRun = true` → перезапускаем демон. Процесс `:router` при этом погибнет вместе с отвязкой, и это нормально: следующий запуск всё равно идёт в новом процессе.
- **OEM-убийцы** (MIUI/HyperOS, ColorOS, EMUI, OneUI с агрессивной оптимизацией): на онбординге и в настройках предлагаем снять оптимизацию батареи (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) и даём ссылки на OEM-экраны автозапуска (как сейчас в `SettingsActivity`, но с исправленным сравнением производителя) плюс dontkillmyapp.com.

### 7.3. Источники запуска

| Источник | Как стартует FGS | Разрешено на 31+ |
|---|---|---|
| Кнопка в UI | `startForegroundService` из активити на переднем плане | Да |
| Действие в уведомлении | `PendingIntent.getForegroundService` | Да (исключение для действий пользователя с уведомлением) |
| `BOOT_COMPLETED` | `startForegroundService` из `BootReceiver` | Да, для `specialUse` (запрет на Android 15 касается только `dataSync`, `camera`, `mediaPlayback` и некоторых других типов) |
| Плитка QS | `TileService.onClick` | Проверено на устройстве: разрешено. Если система откажет (`ForegroundServiceStartNotAllowedException`), плитка открывает прозрачную `StartRouterActivity` через `startActivityAndCollapse` (на 34+ — с `PendingIntent`). На 34+ ещё не проверено |
| START_STICKY-перезапуск | система | Да |

Автозапуск при загрузке включается настройкой `autostartOnBoot` (SharedPreferences), а не файлом-флагом в `cacheDir`. После обновления приложения (`MY_PACKAGE_REPLACED`, тоже исключение из запрета на FGS из фона) роутер запускается снова, если работал до обновления.

### 7.4. Wakelock

Без partial wakelock в глубоком сне CPU останавливается: таймеры роутера замирают, транзитные туннели рвутся. Поэтому:

- Настройка «Не давать устройству засыпать» с выбором «Нет» (по умолчанию), «Всегда» и «Только при зарядке»; в диалоге выбора — предупреждение о расходе батареи. Wakelock держит `RouterService`, пока процесс роутера жив (`Running`/`Draining` и переходы). «При зарядке» — пока устройство подключено к питанию (заряжается или уже заряжено): `PowerMonitor` берёт текущее состояние из sticky `ACTION_BATTERY_CHANGED` и следит за `ACTION_POWER_CONNECTED`/`DISCONNECTED` через приёмник, зарегистрированный в коде (из манифеста они с Android 8 не приходят). Переключатель прежних сборок переносится как «Всегда»/«Нет».

### 7.5. Сеть

`NetworkMonitor` в супервизоре: `registerDefaultNetworkCallback` (24+) или `registerNetworkCallback` (23). Изменения пробрасываются в `host.setOnline()` с дебаунсом 2 с. Старый манифест-ресивер на `CONNECTIVITY_CHANGE` удаляется: с Android 7 он не получает события.

### 7.6. Уведомление

- **Канал** «Состояние роутера», `IMPORTANCE_LOW`, без звука.
- **Заголовок** — состояние жизненного цикла; **текст** — `Сеть: OK (v4) · ↓ 12 KB/s ↑ 8 KB/s · транзит 34`.
- **Обновление** — не чаще раза в 5 с и только при изменении текста.
- **Действия:** «Остановить» (graceful), «Сейчас» (fast), «Перезапуск». В `Draining`: «Отменить» и «Сейчас».
- **Тап** открывает `MainActivity`.

## 8. IPC (AIDL)

```aidl
// :router реализует
interface IDaemonHost {
    void registerCallback(IDaemonCallback cb);
    oneway void start(in StartParams params);
    oneway void stop();                       // Daemon.stop() + exit процесса
    void setAcceptsTransit(boolean accept);
    oneway void reloadTunnels();
    oneway void setOnline(boolean online);
    StatusSnapshot getStatus();
    LogChunk readLog(long sinceSeq, int max);
    String describeOptions();                 // при необходимости; основной путь — §10.3
}

// супервизор реализует
oneway interface IDaemonCallback {
    void onStarted(String version);
    void onStartFailed(String message);
    void onFatal(String message);
    void onStatus(in StatusSnapshot s);
}
```

`StatusSnapshot`, `StartParams` и `LogChunk` — классы с `@Parcelize` в общем модуле `:daemon-api`.

## 9. Статусы

### 9.1. `StatusSnapshot` (из натива, раз в секунду)

| Поле | Источник в i2pd |
|---|---|
| `routerStatusV4`, `routerStatusV6` | `context.GetStatus()` / `GetStatusV6()`: OK, Firewalled, Unknown, Proxy, Mesh, Stan |
| `routerErrorV4`, `routerErrorV6` | `context.GetError()` / `GetErrorV6()`: ClockSkew, Offline, SymmetricNAT, FullConeNAT, NoDescriptors |
| `testingV4`, `testingV6` | `context.GetTesting()` / `GetTestingV6()` |
| `uptimeSec` | `context.GetUptime()` |
| `acceptsTransit`, `floodfill` | `context.AcceptsTunnels()`, `IsFloodfill()` |
| `bwIn`, `bwOut`, `bwTransit` (15 с) | `transports.GetIn/Out/TransitBandwidth15s()` |
| `totalRx`, `totalTx`, `totalTransit` | `transports.GetTotalReceivedBytes()`… |
| `tunnelsIn`, `tunnelsOut`, `transitTunnels` | `tunnels.CountInbound/Outbound/TransitTunnels()` |
| `tunnelSuccessRate` | `tunnels.GetTunnelCreationSuccessRate()` |
| `routers`, `floodfills`, `leaseSets` | `netdb.GetNumRouters/Floodfills/LeaseSets()` |
| `services` | `client::context.GetHttpProxy/GetSocksProxy/GetSAMBridge/GetBOBCommandChannel/GetI2CPServer()` + адрес и порт из конфига |
| `webConsoleUrl` | `http.address`, `http.port` |
| `netId`, `version` | `context.GetNetID()`, `I2PD_VERSION` |

Объект создаём через JNI один раз за тик: `NewObject` с закешированными `jmethodID`.

### 9.2. `RouterState` (в главном процессе)

```kotlin
data class RouterState(
    val lifecycle: Lifecycle,          // Stopped | Preparing | Starting | Running | Draining(deadline) | Stopping | Failed(reason)
    val restartPending: Boolean,
    val status: StatusSnapshot?,       // последний снимок, null если демон не запущен
    val pendingRestartReasons: Set<String>, // изменённые опции, требующие перезапуска (§10.6)
)
```

Когда никого нет (UI закрыт, а уведомлению хватает текста), частоту опроса можно снизить до 5 с: супервизор передаёт `setStatusInterval()`. Это оптимизация, не обязательная для первой версии.

## 10. Система конфигурации

### 10.1. Файлы в datadir

```
filesDir/i2pd/
  i2pd.conf            ← правит GUI
  tunnels.conf         ← правит GUI
  tunnels.d/*.conf     ← правит GUI; файлы из ассетов (Telegram, IRC) копируются только при первом запуске
  certificates/        ← из ассетов, обновляется при смене версии
  torrents/web/        ← веб-интерфейс torrents из i2pd contrib: заменяется при смене версии, недостающие файлы возвращаются при каждом запуске
  torrents/downloads/  ← загрузки туннелей torrents по умолчанию
  addressbook/ netDb/ peerProfiles/ router.keys router.info *.dat ...
```

Настройки самого приложения (автозапуск, wakelock, таймаут graceful, автоперезапуск, тема, запрет скриншотов) хранятся в SharedPreferences (`AppSettingsStore`), а не в `i2pd.conf`. Не DataStore: `BootReceiver` и сервису значения нужны синхронно.

### 10.2. Lossless INI

Модуль `:config` — чистый Kotlin/JVM, полностью покрывается unit-тестами.

- **Модель** `IniDocument`: список строк-узлов `Comment | Blank | Section(name) | Entry(key, value, rawLine) | Unknown(raw)`. Глобальная секция (до первого `[...]`) — это секция с именем `""`.
- **Чтение** не нормализует ничего, кроме концов строк. Запись выводит те же узлы. Изменённый `Entry` переписывается в формате `key = value`, отступы и комментарий в конце строки сохраняются.
- **API:** `get(section, key)`, `set(section, key, value)` (создаёт секцию или ключ в конце соответствующей секции), `remove(section, key)` (удаляет строку или по выбору комментирует её `# key = value`), `sections()`, `addSection()`, `removeSection()`, `renameSection()`.
- **Соответствие опциям i2pd:** `[http] port = 7070` ↔ `http.port`. Ключи с точкой внутри секции (`inbound.length` в `[httpproxy]`) дают опцию `httpproxy.inbound.length`. Разбиение — по первой точке.
- **Значение по умолчанию.** Если значение совпадает с дефолтом схемы и ключа в файле не было, ключ не записываем: файл остаётся компактным.
- **Атомарная запись:** `tmp` → `fsync` → `rename`, перед первым изменением сессии делаем бэкап `i2pd.conf.bak`.
- **Многозначные ключи** (в boost это `composing`, у i2pd почти не встречаются) сохраняются как есть.

### 10.3. Схема опций из нативной библиотеки

- **`describeOptions()`** обходит `m_OptionsDesc.options()` и возвращает по строке на опцию: имя, тип, есть ли значение по умолчанию, значение по умолчанию, описание. Поля разделены табуляцией, `\\`, `\t` и `\n` экранированы. Не JSON: так JVM-модулю `:config` не нужна JSON-библиотека. Секция берётся из префикса имени: группы опций в boost приватны.
- **Тип** определяется по `semantic()->apply_default(boost::any&)` и `any.type()`: `bool`, `std::string`, `uint16_t`, `uint32_t`, `int`, `uint64_t`, `double`. Опции без дефолта получают тип `string`.
- **Где вызывается.** В **главном процессе** (`System.loadLibrary` + `config::Init()`, роутер не запускается). Результат кешируется в `filesDir/schema-<версия>.json`, поэтому библиотека грузится в главный процесс один раз на версию приложения.
- **Почему не в `:router`.** Повторный `config::Init()` перед `Daemon.init()` сломает запуск (D1).
- **Модули, которых нет в Android-сборке** (`unix.*`, `openbsd.*`, `daemon`, `service`, `pidfile`), прячет оверлей.

### 10.4. UI-оверлей (вручную, в Kotlin)

`OptionUi(name, group, level = Basic|Advanced|Hidden, widget, labelRes, summaryRes, requiresRestart = true)`.

- **Виджеты:** `Switch`, `Port`, `Number(min, max)`, `Text`, `Enum(values)` (`loglevel`: none/critical/error/warn/info/debug; `bandwidth`: L/O/P/X или число КБ/с), `HostOrIp`, `FilePath(datadir-relative)`.
- **Basic** — примерно 25 опций: сеть (`ipv4`, `ipv6`, `bandwidth`, `share`, `notransit`, `floodfill`), транспорты (`ntcp2.enabled`, `ssu2.enabled`, `upnp.enabled`), прокси (`httpproxy.*`, `socksproxy.*`: enabled, address, port, outproxy), веб-консоль (`http.*`), SAM, I2CP, `loglevel`, `limits.transittunnels`, `persist.profiles`.
- **Advanced** — все остальные опции из схемы, сгруппированные по секциям. Метка = имя опции, подпись = описание из boost (англ.).
- **Опции, которых нет в оверлее,** автоматически попадают в Advanced. Новые опции из свежего i2pd появляются в GUI без правок кода.
- **Переводы:** строки оверлея лежат в `strings.xml`, существующие переводы переносим.

### 10.5. Валидация

- **По типу и диапазону** из схемы и оверлея (порт сервиса 1..65535, числа, enum).
- **Порты роутера** (`port`, `ntcp2.port`, `ssu2.port`): 0 — «авто», i2pd сам выбирает из 9111–30776, пропуская зарезервированные (`RouterContext::SelectRandomPort`, `IsPortInReservedRange`). Значение вне этого диапазона или зарезервированный порт — предупреждение: сохранить можно после подтверждения.
- **Неизвестный ключ** в `i2pd.conf` — ошибка. i2pd вызывает `parse_config_file` без `allow_unregistered`, значит неизвестный ключ = `exit()` при старте. GUI показывает такие ключи с действием «удалить/закомментировать».
- **Кросс-проверки:** конфликт портов между `http`, `httpproxy`, `socksproxy`, `sam`, `i2cp`, `i2pcontrol` и клиентскими туннелями; порты < 1024 не разрешены для непривилегированного приложения.
- **Перед стартом** валидация запускается повторно (§6.2). Если есть ошибки, состояние `Failed(ConfigError)` и ссылка на нужный экран настроек.

### 10.6. Применение изменений

| Что изменено | Как применяется |
|---|---|
| `i2pd.conf` (любая опция) | Сохраняем файл. При старте роутера запоминается текст конфига; если после этого изменились **действующие значения** опций (форматирование, комментарии и явно записанные значения по умолчанию не считаются), появляется баннер со списком «опция: было → стало» и кнопками «Откатить» (вернуть конфиг запущенного роутера) и «Перезапустить» (§6.4) |
| `tunnels.conf`, `tunnels.d/*` | `reloadTunnels()` без перезапуска (`ClientContext::ReloadConfig`) |
| «Принимать транзит» | Сразу через `setAcceptsTransit()`, плюс по выбору сохраняем `notransit` в конфиг |
| Настройки приложения | Сразу |

### 10.7. Редактор туннелей

Туннели читаются из `tunnels.conf` и всех файлов `tunnels.d/*.conf` (i2pd берёт только файлы с окончанием `.conf`, подкаталоги не читает).

- **Синтаксис** у файлов туннелей другой, чем у `i2pd.conf`: их читает `boost::property_tree::read_ini`. Комментарий — только строка, начинающаяся с `#` или `;`; `#` внутри строки — часть значения; строка без `=` делает файл ошибочным (i2pd 3.0 тогда читает его по секциям); повтор секции или ключа в файле — ошибка. `IniDocument` поддерживает оба диалекта (`IniDialect`).
- **Список** — по файлам, в порядке файла; у каждого туннеля тип, сводка (`127.0.0.1:3443 → …b32.i2p:3443`), проблемы.
- **Общие назначения.** Туннели с одним постоянным `keys` используют одно назначение во всём роутере: i2pd находит запущенное назначение по хешу ключей в любом файле и у прокси `i2pd.conf` и перенастраивает его I2CP-параметрами каждого файла. Внутри одного файла назначение и I2CP-параметры задаёт первая секция с этим ключом, следующие берут его как есть. Поэтому порядок секций в файле важен (например, `Telegram.conf`): редактор не переставляет и не выносит секции, показывает участников общего назначения и предупреждает при удалении первой секции группы.
- **Форма** зависит от `type`: `client`, `server`, `http`, `irc`, `udpclient`, `udpserver`, `socks`, `httpproxy`, `torrents`; `websocks` (устарел, i2pd его не запускает) редактируется как есть, но не создаётся.
- **Параметры — все, что читает код i2pd** (`TunnelParams`): ветки `ClientContext::ReadTunnels` по типам, `GetUDPTunnelMaxWindow` и `ReadI2CPOptions` с группами `i2cp.leaseSetClient.dh*`/`psk*`. Значения по умолчанию — из кода (`DEFAULT_*` и т. п.), у двух опций I2CP свои для серверов и torrents (`ReadI2CPOptions(isServer)`): `i2p.streaming.answerPings`, `i2cp.leaseSetEncType`. Основные поля видны сразу. «Дополнительные параметры» (после адреса и пометки об общем назначении) и весь блок I2CP свёрнуты и открываются только после «Я знаю, что делаю» — это тонкая настройка; подтверждения хватает на оба блока до закрытия формы. Блок с заданными значениями или ошибкой открыт сразу. Тест `TunnelSourcesTest` разбирает исходники сабмодуля и падает, если i2pd читает параметр, которого нет в `TunnelParams`, или наоборот. Ключ, который i2pd для этого типа не читает (опечатка, параметр другого типа, `i2cp.dontPublishLeaseSet`), показывается как проблема; такие ключи сохраняются в «Прочих параметрах».
- **torrents**: под полем `trackers` — кнопки известных трекеров I2P (DgTrack, R4SAS, Skank, InsulaOcculta; `TorrentTrackers`), только тех, которых ещё нет в списке (сравнение без регистра и завершающего `/`); нажатие дописывает адрес через запятую без пробелов. `rpcpath` — в основном блоке, по умолчанию пуст (RPC на `/rpc`; i2pd добавляет `/` сам: `transmission` даст `/transmission/rpc`, как у клиентов Transmission). Пока `rpcport` пуст или 0, под ним предупреждение, что экран торрентов недоступен, и нажимаемое «9191», которое подставляет порт. Каталог загрузок (`TorrentsStorage`): i2pd пишет обычным файловым доступом (`std::filesystem`), SAF ему недоступен. По умолчанию у нового туннеля — медиапапка приложения `Android/media/org.purplei2p.i2pd/torrents` (`getExternalMediaDirs`) на карте памяти, если она есть (меньше износ встроенной флеш-памяти), иначе во внутренней памяти: разрешений не нужно ни на одной версии, папка видна в файловых менеджерах и по USB, но может удаляться вместе с приложением или при очистке его данных — под полем об этом пометка. Под полем — кнопки медиапапки на каждом томе и «Выбрать папку» (системный выбор папки, путь из id документа `<том>:<путь>`). Для чужой папки на Android 11+ нужен «Доступ ко всем файлам» (`MANAGE_EXTERNAL_STORAGE`), на Android 6–10 — `WRITE_EXTERNAL_STORAGE` и только внутренняя общая память (на карту памяти до 11 можно писать лишь в папки приложения); после выбора такой папки открывается окно «Доступ к папке» с кнопкой «Разрешить» (экран «Доступ ко всем файлам» или запрос разрешения): выдал — окно закрывается само, вернулся без выдачи — окно остаётся с текстом, что разрешение не дано, и кнопками медиапапок внутренней памяти и карты; без возможности выдать (карта на Android 6–10) — сразу эти кнопки. Под полем остаются строка о недостающем доступе и «Подробнее», открывающая окно снова (для пути, введённого вручную, окно само не открывается). Старые туннели с `torrents/downloads` не переносятся. Каталог создаётся при сохранении (i2pd только читает из него `.torrent`; данные, `.torrent` и `.resume` лежат рядом, и раздача идёт оттуда — переноса через RPC нет). У завершённого торрента есть «Сохранить в…»: копия файлов (поле `files` из `torrent-get`) в папку, выбранную через SAF; торрент остаётся на месте и раздаётся дальше, копирование идёт и после закрытия экрана, пока жив процесс (`TorrentExports`); RPC совместим с Transmission, `web` отдаёт через RPC-порт файлы `torrents/web` — веб-интерфейс из `contrib/torrents` i2pd (в assets — ссылка на сабмодуль, как `certificates`); RPC-порт участвует в проверке конфликтов портов. У torrents-туннеля с `rpcport` > 0 в строке списка есть кнопка «Торренты» — встроенный клиент RPC (`TorrentsRpc`): `POST http://<rpcaddress>:<rpcport>/<rpcpath>/rpc` (адрес `0.0.0.0`/`::` → `127.0.0.1`), Basic-авторизация из `rpcuser`/`rpcpass` при `rpcauth`; сессионного заголовка Transmission i2pd не требует. Экран: добавление по magnet-ссылке или `.torrent`-файлу из системного окна выбора (до 10 МБ, `metainfo` в base64), кнопки «Запустить все» / «Остановить все» вверху (`torrent-start`/`torrent-stop` без `ids` — i2pd применяет их ко всем; каждая активна, только если что-то меняет), список с прогрессом, состоянием, скоростями, пирами, ETA и рейтингом (только поля `torrent-get`, которые i2pd заполняет по-настоящему), запуск/остановка, переанонс, удаление с файлами или без, сводка `session-stats` (общие скорости i2pd отдаёт только внутри `current-stats`, а не на верхнем уровне, как Transmission: читаются оба места) и узлы `dht-stats`; обновление раз в 2 с, пока экран открыт. Регистрации обработчиком `magnet:` и `.torrent` нет намеренно (небезопасно), добавляется по запросам пользователей.
- **Новые туннели.** Кнопка «Добавить туннель» в карточке файла дописывает секцию в конец этого файла (порядок существующих секций не меняется; имя не должно совпадать ни с одним туннелем файла, включая закомментированные). Кнопка «Новый файл» внизу списка создаёт новый файл `tunnels.d/<имя>.conf`. Существующие туннели правятся в своём файле.
- **Отключение файла** — переименование целиком в `.conf.disabled`: i2pd пропускает такие файлы, порядок секций не трогается.
- **Отключение туннеля** — комментирование его секции: перед каждой строкой ставится `#` (пустые строки внутри становятся `#`, бывшие комментарии — `##`), включение снимает ровно один `#`/`;` и даёт тот же текст. Закомментированные вручную блоки (`#[имя]` и идущие подряд закомментированные строки до пустой строки, настоящей секции или следующего `#[…]`, хотя бы с одним `ключ = значение`) показываются в списке на своём месте как «Отключён (закомментирован)» и включаются так же; строки-описания при этом остаются комментариями, чтобы в файле не появилась строка, которую i2pd отвергнет. Отключение первого туннеля группы с общими ключами спрашивает подтверждение: адрес и I2CP переходят к следующему.
- **Применение** — баннер «Применить» → `reloadTunnels()` (`ClientContext::ReloadConfig`) без перезапуска роутера. Баннер показывается, пока содержимое включённых файлов отличается от прочитанного роутером при запуске или последнем применении: отменённое изменение баннер снимает.
- **Адрес** туннеля с постоянными ключами считается из файла ключей: b32 — SHA-256 публичной идентичности (длина сертификата из файла, поэтому верно для всех типов подписи; сверено с i2pd для типов 0–11). Для зашифрованного lease set (`i2cp.leaseSetType = 5`) дополнительно b33 (`BlindedPublicKey::ToB33`, только подписи 7 и 11). `signaturetype` 4, 5, 6 и 8 i2pd не генерирует и молча создаёт DSA — это показывается как ошибка.

### 10.8. Ручной редактор, импорт/экспорт, миграция

- **Ручной редактор:** простой текстовый редактор каждого файла с подсветкой секций и валидацией при сохранении. Это запасной путь для опытных пользователей.
- **Экспорт и импорт:** zip через SAF (`ACTION_CREATE_DOCUMENT` / `ACTION_OPEN_DOCUMENT`). Внутри конфиги, `tunnels.d`, ключи (`router.keys`, `*.dat`), адресная книга. netDb и профили — по выбору.
- **Миграция со старой версии (`/sdcard/i2pd`):**
  1. При первом запуске новой версии datadir пуст. Онбординг предлагает «Перенести данные из прежней версии».
  2. `ACTION_OPEN_DOCUMENT_TREE` с подсказкой на `primary:i2pd`: пользователь выбирает папку, и мы копируем через `DocumentFile`. Разрешения на хранилище не нужны ни на одной версии Android.
  3. Копируем: `i2pd.conf`, `tunnels.conf`, `tunnels.d/`, `router.keys`, `router.info`, `*.dat`, `addressbook/`. netDb — по выбору: так быстрее старт, но можно и пересидировать.
  4. После копирования валидируем `i2pd.conf` по схеме. Устаревшие ключи (например `ssu`, если его уберут) предлагаем удалить.
  5. Отказ от миграции = чистая установка с конфигами из ассетов.

### 10.9. Управление хранилищем

Экран «Хранилище» (Настройки → Данные) управляет файлами в приватном datadir без root и без разрешений на хранилище.

| Категория | Что показываем | Действия |
|---|---|---|
| Ключи роутера (`router.keys`, `router.info`) | Наличие, дата | Экспорт; импорт (новая идентичность роутера); удаление (при старте сгенерируется новая) |
| Ключи назначений (`*.dat`, на которые ссылаются `keys =` в туннелях и прокси) | Список, какой туннель использует файл | Импорт, экспорт, удаление. Адрес `.b32.i2p` — вместе с редактором туннелей (§10.7) |
| netDb (`netDb/`) | Число записей, размер | Очистить (роутер пересидирует при старте) |
| Профили (`peerProfiles/`) | Размер | Очистить |
| Адресная книга (`addressbook/`) | Число записей, размер | Очистить, импорт `hosts.txt` |
| Конфиги (`i2pd.conf`, `tunnels.conf`, `tunnels.d/*`) | Список, дата изменения | Импорт с валидацией по схеме (§10.5) и бэкапом `.bak`, экспорт, сброс к встроенным |
| Сертификаты (`certificates/`) | Версия ассетов | Сброс к встроенным |
| Логи, временные файлы | Размер | Очистить |
| Произвольные файлы (режим «Для опытных») | Дерево datadir с размерами | Загрузить файл в выбранную папку, экспорт, удаление; папка удаляется со всем содержимым по долгому нажатию (подсказка под списком) |

Правила:

- **Импорт и экспорт идут через SAF**: `ACTION_OPEN_DOCUMENT` и `ACTION_CREATE_DOCUMENT`. Разрешения не нужны.
- **Опасные операции** (очистка, сброс, замена, удаление) — только с подтверждением. Через `runWithRouterStopped` (§6.6) — только если они затрагивают файлы, которые пишет роутер; категория определяется по каждому затронутому файлу. Чтение и экспорт работают на запущенном роутере.
- **Импорт** (zip, папка старой версии, отдельный файл) распаковывается во временный каталог рядом с datadir (не в кеш: перемещённые файлы сохраняют группу, и файлы из кеша считались бы кешем приложения), затем файлы заменяются. Для файлов туннелей импортированное состояние главнее: импорт `x.conf` удаляет `x.conf.disabled` и наоборот.
- **Безопасность путей:** целевой путь нормализуется (`canonicalPath`) и обязан лежать внутри datadir, никаких `..` и симлинков наружу. Запись атомарная: `tmp` → `fsync` → `rename`. На импорт стоит лимит размера.
- **Опционально, пока не сделано:** `DocumentsProvider` с корнем «i2pd». Он открывает datadir в системном приложении «Файлы» и в любом SAF-пикере, так что опытный пользователь может копировать файлы любым файловым менеджером. По умолчанию выключен (настройка «Открыть datadir для файловых менеджеров»). При записи через провайдер на запущенном роутере показываем предупреждение и предлагаем перезапуск.

## 11. UI (Compose)

Навигация: нижняя панель «Статус · Туннели · Настройки · Логи», дополнительные экраны открываются поверх.

1. **Статус**
   - Шапка: логотип, название и описание проекта (та же шапка в «О приложении» и на экране первого запуска).
   - Панель из трёх действий с иконками и подписями: «Запустить»/«Остановить», «Перезапустить», «Веб-консоль». Подписи переносятся на вторую строку, если не помещаются. «Остановить» — штатная (graceful) остановка, удержание кнопки останавливает сразу; когда роутер впервые доходит до «Работает», показывается обучающая подсказка: экран затемнён, кроме кнопки «Остановить», рядом выноска со стрелкой (`CoachMark`); закрывается «Понятно», нажатием в любом месте, «Назад» или первым удержанием и больше не показывается. В `Draining` первая кнопка — «Отменить», удержание тоже останавливает сразу. Недоступные действия затенены.
   - Блок состояния: первая строка — жизненный цикл (озвучивается TalkBack при смене), под ней обратный отсчёт `Draining`/автоперезапуска или текст ошибки с «Закрыть»; дальше подробности статуса.
   - Метрики: аптайм, скорость ↓↑ и транзита, трафик всего, туннели входящие/исходящие/транзит, успешность создания туннелей, роутеры/floodfill/leaseset.
   - Чипы служб после блока состояния: HTTP-прокси, SOCKS-прокси, SAM, BOB, I2CP, веб-консоль. Тап переключает на «Настройки» и открывает группу службы в основных настройках роутера с прокруткой к ней. Следом — чип на каждый torrents-туннель включённых файлов: «Торренты», а при нескольких — «Торренты (имя туннеля)». Тап переключает на «Туннели»: при `rpcport` > 0 — сразу экран торрентов, иначе форма туннеля, где видно предупреждение о порте.
   - Графики за последние 5 минут: скорость (↓, ↑, транзит) и число туннелей (входящие, исходящие, транзит); новые точки справа, подпись максимума. Буфер на 300 точек (статус приходит раз в секунду) живёт в главном процессе (`StatusHistory`) и очищается при остановке роутера.
   - Баннеры: «Требуется перезапуск», «Оптимизация батареи включена», «Уведомления выключены», ошибка конфигурации.
2. **Туннели** — список, добавление, редактирование, удаление, «Применить» (`reloadTunnels`).
3. **Настройки:**
   - **Роутер:** Basic-группы (сеть, транспорты, HTTP- и SOCKS-прокси, веб-консоль, SAM, BOB, I2CP, логи, лимиты); «Все параметры» (вся схема) — вкладки по разделам i2pd.conf, первой вкладкой «Проблемы (N)», пока есть неизвестные параметры или ошибки; поиск по имени и описанию ищет по всем разделам сразу и на время поиска скрывает вкладки; «Редактировать файл вручную».
   - **Приложение:** автозапуск, автоперезапуск, таймаут graceful, wakelock, тема, запрет скриншотов, оптимизация батареи, OEM-автозапуск.
   - **Данные:** хранилище (§10.9), экспорт, импорт, миграция, «Сбросить конфиг к умолчаниям».
   - **О приложении:** шапка с логотипом, версии приложения и i2pd, ABI; ссылки на сайт, исходный код и баг-трекер (открываются в браузере только по нажатию); «Лицензии открытого ПО» — список компонентов (i2pd, OpenSSL, Boost, MiniUPnPc, `getifaddrs` из bionic, Kotlin, AndroidX/Compose) с текстами лицензий из `assets/licenses`.
   - **Запрет скриншотов:** по умолчанию **вкл**. На экранах видны адреса b32, ключи, логи и конфиги, поэтому окно активности получает `FLAG_SECURE`. Это запрещает скриншоты и запись экрана, а в «Недавних» вместо миниатюры показывается пустое окно. Диалоги и меню Compose наследуют флаг от окна (`SecureFlagPolicy.Inherit`), веб-консоль тоже закрыта. Выключение применяется сразу, без перезапуска активности.
4. **Логи** — живой вывод из кольцевого буфера с фильтром по уровню и поиском, «Поделиться» (текстовый файл через `FileProvider`), «Очистить».
5. **Веб-консоль** — WebView на `webConsoleUrl`: только loopback, без JS-интерфейсов, `MetricsOptOut`. Без cleartext-разрешения на весь трафик: `network_security_config` разрешает cleartext только для `127.0.0.1`/`localhost`.
6. **Онбординг** (первый запуск) — что такое i2pd, разрешение на уведомления, оптимизация батареи, автозапуск прошивки (только на её устройствах), миграция, «Запустить».

**OEM-автозапуск.** Прошивки Xiaomi/Redmi/POCO, Huawei, Honor, OPPO/realme/OnePlus, vivo/iQOO и ASUS имеют свой менеджер автозапуска поверх оптимизации батареи: без разрешения они не доставляют `BOOT_COMPLETED` и убивают приложение в фоне даже с FGS. Узнать, выдано ли разрешение, нельзя, поэтому на таких устройствах (`Build.MANUFACTURER`/`BRAND`) есть пункт «Автозапуск (производитель)» в «Работе в фоне» и шаг на экране первого запуска. Они открывают экран автозапуска по известным компонентам (они разные в версиях прошивки, перебираются по очереди), иначе системную карточку приложения. Шаг считается выполненным, когда экран открыт.

**Плитка QS:** состояние Active/Inactive/Unavailable по `RouterState`, тап — старт или graceful-стоп.

## 12. Модули

```
:app            Compose UI, навигация, ViewModels, онбординг, MainActivity
:service        RouterService (супервизор), RouterController, RouterStateRepository,
                NetworkMonitor, BootReceiver, TileService, уведомления, AppSettings (DataStore)
:daemon-api     AIDL, Parcelable-модели (StatusSnapshot, StartParams, LogChunk)
:daemon         DaemonHostService (:router), NativeDaemon (JNI-обёртка)
:native         C++ JNI-мост, сборка libi2pd.so (ndk-build/CMake), ссылки на binary/jni
:config         Lossless INI, модель схемы, оверлей, валидация, ConfigRepository — JVM-модуль
```

DI — вручную: `AppContainer` в `Application`. Hilt для такого объёма избыточен и тянет kapt/ksp.

## 13. Сборка и CI

- **Gradle:** актуальные стабильные AGP/Kotlin/Compose на момент начала работы, версии зафиксированы в `gradle/libs.versions.toml`. JDK 17+.
- **Тексты ошибок:** экраны хранят исключение, а не строку, и показывают его через `errorText()`: для обычных причин (нет места, нет доступа, не найдено, нет связи, нет ответа вовремя, роутер отклонил запрос RPC) — переведённая фраза и системный текст в скобках (нужен в отчётах), для прочих — только системный текст. Причина ищется по классу и по имени errno в тексте (`ENOSPC`, `EACCES`, …) вдоль цепочки `cause` (`errorReason`, тест `ErrorTextTest`). Ошибка сохранения в текстовом редакторе `i2pd.conf` теперь видна (раньше показывалась только при неудачной загрузке).
- **Клавиатура:** окно рисуется edge-to-edge и под клавиатуру не сжимается, поэтому содержимое вкладок получает `imePadding()` (после `consumeWindowInsets` отступов Scaffold, чтобы нижняя панель не учитывалась дважды), а у активности `adjustResize` — без него на Android до 11 отступы клавиатуры не приходят. Формы прокручиваются до последних полей при открытой клавиатуре.
- **Нативные зависимости** (`binary/jni`, `API=23`, NDK r29). Сабмодуль остался один — `i2pd`, закреплён на коммите (`56958857`: тег 3.0.0 и включение RPC torrents на Android) без `branch` в `.gitmodules`, так что `git submodule update --remote` его не сдвигает; обновление — только явной сменой коммита. `fetch_deps.sh` скачивает релизные архивы Boost 1.84.0, OpenSSL 3.5.9 (LTS) и miniupnpc 2.3.3 в игнорируемый `deps/`, проверяет sha256 из самого скрипта и распаковывает (`.version` — что распаковано); уже лежащий архив не качается заново, так что для офлайн-сборки или `prebuild` F-Droid достаточно положить архивы туда. Готовых бинарников нет: всё собирается из исходников. Прежние сабмодули `openssl` (379 МБ истории) и `miniupnp` (монорепозиторий с miniupnpd) убраны. Архив miniupnpc с сайта автора (tuxfamily, запасной — free.fr только по HTTP, архив защищает контрольная сумма) сверен с тегом `miniupnpc_2_3_3`: отличаются только CVS-строки `$Id$`.
- **OpenSSL** собирается своим `Configure` (`build_openssl.sh`, в `deps/openssl/out/<ABI>`), кеш по stamp-файлу (ревизия NDK + API): перенос его генерируемых заголовков и perlasm в ndk-build не окупается.
- **miniupnpc** компилируется прямо в ndk-build (`deps.mk`): файлы и определения из его `CMakeLists.txt`, `miniupnpcstrings.h` генерирует `fetch_deps.sh` (`OS_STRING` "Android", как у CMake), заголовки для `<miniupnpc/...>` лежат в `deps/miniupnpc/export`. cmake для сборки больше не нужен.
- **ifaddrs:** `getifaddrs()` появился в bionic только с API 24, а `minSdk` 23; i2pd использует его для MTU интерфейса, опций `ifname*` и поиска IPv6/Yggdrasil-адреса. Реализация из bionic (AOSP, BSD 2-Clause; `bionic_macros.h` — Apache 2.0) лежит в `binary/jni/ifaddrs` с `LICENSE` вместо прежнего сабмодуля `android-ifaddrs`; уйдёт при `minSdk` 24 (i2pd включает `"ifaddrs.h"`, без копии подхватится системный).
- **Boost:** Boost-for-Android (поддерживает NDK только до 27.0) убран. `fetch_deps.sh` распаковывает из релизного архива только заголовки и исходники `program_options`, `json` и `url`, они компилируются прямо в ndk-build (`binary/jni/deps.mk`).
- **i2pd ≥ 3.0** требует C++20: `-std=c++20` в обоих `Application.mk`.
- **`binary/jni/deps.mk`** — общие определения модулей и списка исходников i2pd для приложения (`native/jni/Android.mk`) и standalone-бинарника (`binary/jni/Android.mk`).
- **Флаги:** `-Wl,-z,max-page-size=16384` явно (на r28+ уже по умолчанию), `APP_STL := c++_static`, `-fvisibility=hidden` плюс экспорт только `JNI_OnLoad`.
- **Подпись:** конфиг release-подписи берётся из `keystore.properties` или переменных окружения, а не из `build.gradle`.
- **Размер релиза:** R8 и удаление неиспользуемых ресурсов, `localeFilters` по языкам приложения (иначе в APK попадают переводы всего AndroidX), `.so` в APK сжаты (`useLegacyPackaging`, распаковываются при установке, как в старом приложении). APK arm64 — 6,9 МБ (было 21,9 МБ, у 2.61.0 — 7,2 МБ). Проверено, но не включено: `-Oz`/ThinLTO/ICF (`libi2pd.so` 12,7 → 10,1 МБ) и OpenSSL без неиспользуемых алгоритмов и протоколов (→ 8,4 МБ).
- **Воспроизводимость:** APK одинаковы байт в байт при сборке из другого каталога, с NDK в другом месте и с зависимостями, собранными с нуля. Для этого:
  - `dependenciesInfo` выключен — блок зависимостей для Google Play шифруется случайным ключом;
  - `-ffile-prefix-map` (корень репозитория → `.`, NDK → `/ndk`) и `-fdebug-compilation-dir=.` в обоих `Application.mk` (действуют и на Boost с miniupnpc): Boost.Asio вкомпилирует пути исходников, а пути в отладочной информации меняют build ID даже после strip;
  - OpenSSL: `--prefix=/` с установкой через `DESTDIR` (иначе абсолютный путь сборки попадает в `OPENSSLDIR` и другие каталоги) и `SOURCE_DATE_EPOCH` из `RELEASE_DATE` в `VERSION.dat` (строка «built on»);
  - в `META-INF/version-control-info.textproto` попадает ревизия git, поэтому одинаковый результат даёт сборка из того же коммита.
- **CI:**
  - исправить переключение JDK: `actions/setup-java` вместо `export` в отдельном шаге;
  - кешировать собранные библиотеки (`binary/jni/*/out`) по хешу сабмодулей;
  - unit-тесты `:config` и lint;
  - instrumented-тесты на эмуляторе — отдельным job'ом, по ручному запуску.
- **F-Droid:** без изменений в метаданных, кроме `minSdk`. Сборка должна оставаться воспроизводимой.

## 14. Тестирование

- **`:config`** (JUnit):
  - круговой тест чтение → запись = исходный файл на наборе реальных конфигов (ассеты, старые пользовательские, с комментариями, CRLF, BOM);
  - set/remove;
  - соответствие секций и опций;
  - валидация и конфликты портов.
- **`:native`:** smoke-тест `describeOptions()` — JSON парсится, есть ключевые опции, типы распознаны.
- **`:service`** (Robolectric или instrumented):
  - переходы стейт-машины на фейковом `IDaemonHost`: старт, ошибка, `onFatal` → `binderDied`, краш с backoff, graceful с отменой, перезапуск, watchdog быстрой остановки.
- **Ручной чек-лист на устройствах** (Android 6, 10, 13, 14, 15, 16 и один MIUI/HyperOS):
  - смахивание из недавних — демон жив 1 ч, туннели работают;
  - перезагрузка с автозапуском;
  - смена сети Wi-Fi ↔ LTE;
  - быстрая остановка < 10 с, graceful < дедлайна;
  - битый конфиг → понятная ошибка;
  - миграция со старой версии.

## 15. Этапы реализации

1. **Каркас:** новые модули, Gradle-обвязка, minSdk 23/target 36, сборка `libi2pd.so` с новым NDK, CI.
2. **Демон и процессы:** `:native` JNI-мост, `DaemonHostService` в `:router`, AIDL, `RouterService` со стейт-машиной, уведомление. Минимальный экран статуса со старт/стоп/перезапуском. → Здесь проверяем фоновую работу и остановку на устройствах.
3. **Статусы и логи:** полный `StatusSnapshot`, экран статуса, логи, плитка QS, автозапуск, сеть, wakelock, настройки приложения, баннеры батареи и уведомлений, веб-консоль.
4. **Конфигурация:** `:config` (INI + схема + оверлей + валидация), экраны настроек Basic/Advanced/ручной редактор, баннер перезапуска.
5. **Туннели:** редактор туннелей, `reloadTunnels`.
6. **Данные:** онбординг, миграция с `/sdcard/i2pd`, экспорт/импорт, управление хранилищем (§10.9).
7. **Полировка:** запрет скриншотов с выключением в настройках, переводы, доступность, тёмная тема, планшеты, удаление старого кода (на этапе 1 он был перенесён в `legacy/`, на этом этапе удалён).

## 16. Открытые вопросы

1. Запуск FGS из `TileService.onClick` на 34+ — проверить на устройстве (запасной путь через trampoline-активити уже есть).
2. ~~Отключение туннеля?~~ Решено: файл целиком переименовывается в `.conf.disabled` (§10.7).
3. ~~Нужен ли «Режим только клиента» как пресет?~~ Решено: не делаем.
4. ~~Изменить ли дефолтный `loglevel` с `none` на `warn`?~~ Решено: `warn`, `bandwidth` остаётся `L`.
5. Апстримить ли в i2pd геттер `GetOptionsDesc()` и поддержку graceful для Android-сборки (§5.6)?
6. Надёжность «Сохранить в…»: копия идёт в процессе приложения и может прерваться вместе с ним, оставив неполный файл. Отложено до сообщений пользователей; тогда — короткая foreground-служба `dataSync` с уведомлением о прогрессе и запись во временный `*.part` с переименованием в конце.
