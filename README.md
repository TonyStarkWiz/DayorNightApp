# Day or Night

Kotlin / Compose MVVM app. User enters a city, we geocode it, fetch Open-Meteo `is_day` + next-hour rain, and show Day or Night. Room keeps the last successful forecast so the screen still works offline.

Data flow: `MainActivity` -> `MainViewModel` -> `WeatherRepository` -> (network check + Room cache + `WeatherRemoteDataSource` / Open-Meteo). Errors are mapped to `AppError` before they reach the UI.

---

## AppError.kt:
Problem: Retrofit, Moshi, and socket failures were leaking into the ViewModel as generic exceptions, so the UI could not tell "no internet" from "bad JSON".
Approach: One sealed `AppError` type used by API, repository, ViewModel, and UI. `AppException` carries that type out of the remote layer.
What I did: `NoInternet`, `Server(code)`, `Unauthorized`, `Parsing`, `Unexpected`. `toMessage()` is the user-facing string. Trade-off: sealed types are exhaustive in `when`, but Open-Meteo rarely returns 401 — we still handle it so the module is complete. `sealed class` vs enum: sealed lets `Server` hold a code. `object` subtypes have no extra data. `data class Server` holds the HTTP code. `Exception` is the JVM failure type; wrapping it avoids `catch (Exception)` with string matching later.

## ErrorMapper.kt:
Problem: Each layer was going to reinvent `if (e is UnknownHostException)`.
Approach: One mapper from `Throwable` -> `AppError`.
What I did: Unknown host / timeout / `IOException` => no internet. Moshi `JsonDataException` / `JsonEncodingException` => parsing. Retrofit `HttpException` 401/403 => unauthorized, other HTTP => server. Already-mapped `AppException` is passed through. Anything else => unexpected. Trade-off: treating all `IOException` as offline can hide a disk error; for this app the IO we care about is the network. `when` with `is` is Kotlin smart-casting. `HttpException.code()` is the status from OkHttp/Retrofit.

## WeatherRemoteDataSource.kt:
Problem: The assignment asked for error handling at the API layer, not only in the ViewModel.
Approach: Thin wrapper around `WeatherApi` that converts failures to `AppException` before the repository sees them.
What I did: `getForecast` calls Retrofit, catches `Exception`, maps, rethrows `AppException`. Trade-off: extra class vs mapping only in the repository. Keeping it here means the repository can trust remote failures are already typed. `suspend` is a coroutine that can call Retrofit without blocking the thread Retrofit uses internally.

## DispatcherProvider.kt:
Problem: Qualifiers (`@IoDispatcher`, `@MainDispatcher`, `@DefaultDispatcher`) made tests inject three values and were easy to mix up.
Approach: One `DispatcherProvider` with `io`, `main`, `default`. Production uses `AppDispatcherProvider` (`Dispatchers.IO/Main/Default`). Tests pass `UnconfinedTestDispatcher` for all three.
What I did: Replaced the qualifier module. Repository only uses `io`. Trade-off: a small interface vs Hilt `@Qualifier` annotations. The interface is easier to fake. `Dispatchers.IO` is the thread pool for blocking work (Room + network). `Dispatchers.Main` is the UI thread (ViewModel already uses `viewModelScope` which is Main). `Dispatchers.Default` is CPU work; unused today, kept so we do not add a third injection style later.

## NetworkChecker.kt:
Problem: Repository cannot call ConnectivityManager directly if we want unit tests without Android.
Approach: `fun interface NetworkChecker` with `isOnline()`. Production: `ConnectivityNetworkChecker`. Tests: `NetworkChecker { true }`.
What I did: API 23+ uses `NetworkCapabilities.NET_CAPABILITY_INTERNET`. API 21–22 uses deprecated `activeNetworkInfo`. Manifest has `ACCESS_NETWORK_STATE`. Trade-off: capability check can be true on a captive portal; good enough here. `fun interface` allows a lambda fake. `ConnectivityManager` is the system service for radios. `Build.VERSION.SDK_INT` is why we branch. `@Suppress("DEPRECATION")` is required for `activeNetworkInfo` below 23.

## CityForecast.kt:
Problem: We had `CityWeather` for the UI and would have needed a second Room entity with the same fields.
Approach: One `@Entity` used as the cache row and the success model.
What I did: `cityQuery` (lowercase search key) is `@PrimaryKey` so "Dallas" and "dallas" share a row. `cityName` is the display name from geocoding. `savedAt` is used for freshness. Trade-off: domain model carries Room annotations. For this size of app that is better than copying five fields through another DTO. `@Entity` tells Room to make a table. `@PrimaryKey` is the lookup column. `data class` gives equals/copy.

## ForecastDao.kt:
Problem: Repository needs to read/write cache without knowing SQLite.
Approach: Room `@Dao` with `getByQuery` and `upsert` (`OnConflictStrategy.REPLACE`).
What I did: Tests implement `ForecastDao` in memory (`FakeForecastDao`) so we never spin up a real database in unit tests. Trade-off: fake DAO does not prove SQL is valid; Room's processor checks the annotations at compile time. `@Insert(REPLACE)` overwrites the city row. `suspend` so Room runs off the main thread when used with `room-ktx`.

## WeatherDatabase.kt:
Problem: Need a process-local store that survives process death better than a Map, and works with Hilt.
Approach: Room `@Database` version 1, `exportSchema = false` (no schema dump in this interview project).
What I did: `WeatherDatabase` exposes `forecastDao()`. Built once as `@Singleton` in `RepositoryModule`. Trade-off: `exportSchema = false` skips migration history; fine for one table and no shipped users. `Room.databaseBuilder` creates the SQLite file `weather.db`.

## WeatherRepository.kt:
Problem: `CityWeather` duplicated `CityForecast`, and `getCityWeather` returning null mixed "city not found" with "call failed".
Approach: Keep `CityLocation` only for geocoding (lat/lng). Repository returns `WeatherResult`: `Success`, `Empty`, `Failure`.
What I did: `CityLookup` stays a `fun interface` so Geocoder can be faked. `Failure` can include cached data. Trade-off: `Empty` vs `Failure` — not finding a city is not a network error. `interface WeatherRepository` is what the ViewModel depends on.

## WeatherRepositoryImpl.kt:
Problem: Needed one place to choose API vs cache using network + freshness, and to never throw raw exceptions to the UI.
Approach: All work on `dispatchers.io`. Key = trimmed lowercase city.
What I did:
1. Offline + cache => `Success(fromCache = true)`. Offline + no cache => `Failure(NoInternet)`.
2. Online + cache younger than 15 minutes => return cache (avoids extra Open-Meteo calls).
3. Else geocode. Missing city + cache => stale cache. Missing city + no cache => `Empty`.
4. Remote fetch, map `is_day` / next-hour rain, `upsert` Room, `Success(fromCache = false)`.
5. Remote failure => `Failure` with cache if we have it.
`timeProvider` is injected as `() -> Long` so tests control freshness. Trade-off: 15 minute TTL (`CACHE_TTL_MS`) is a guess; too short hits the API more, too long shows stale day/night around sunrise. `withContext` hops off Main. `return@withContext` returns from the lambda, not the outer function.

## RepositoryModule.kt:
Problem: New collaborators (Room, network checker, dispatchers, mapper, remote source) must come from Hilt like `WeatherApi` already does.
Approach: `@Provides` in `SingletonComponent`, same style as `NetworkModule`.
What I did: `DispatcherProvider`, `ErrorMapper`, `NetworkChecker` (`@ApplicationContext`), `@Singleton` `WeatherDatabase`, DAO, `Geocoder`, `CityLookup`, `WeatherRemoteDataSource`, `WeatherRepository`. Trade-off: one module vs many; this app is small enough for one. `@InstallIn(SingletonComponent::class)` means one instance graph for the process. `@ApplicationContext` is the app context, safe to store. `@Singleton` on the DB avoids opening SQLite twice.

## MainViewModel.kt:
Problem: A single data class with nullable `isDay` + `errorMessage` could not represent loading, empty, success, and error-with-cache at once.
Approach: `sealed class DayNightUiState`: `Idle`, `Loading`, `Empty`, `Success`, `Error`.
What I did: Blank input => `Empty`. Repository `Empty` => city not found. `Success` / `Failure` map 1:1. Unexpected throw => `Error(Unexpected)` and `Log.e`. Trade-off: sealed UI state is more `when` branches in Compose, but the compiler enforces we handle each one. `StateFlow` is a hot stream; Compose `collectAsState()` recomposes on emit. `viewModelScope` cancels work if the ViewModel is cleared. `@HiltViewModel` + `@Inject` is how the repository gets in.

## MainActivity.kt:
Problem: UI only knew spinner vs error string vs day/night.
Approach: `when (uiState)` for Idle / Loading / Empty / Success / Error. `ForecastResult` draws sun/moon. Error with `cached` still shows the last forecast plus the error text. `fromCache` shows "Showing saved data".
What I did: Left the city `TextField` as it was. Extracted `ForecastResult` so success and cached-error share layout. Trade-off: a bit more UI code; no extra navigation. `collectAsState()` subscribes to the ViewModel. `sealed` `when` is exhaustive so we cannot forget a state.

## AndroidManifest.xml:
Problem: `ConnectivityManager` needs `ACCESS_NETWORK_STATE`.
Approach: Added that permission next to `INTERNET`.
What I did: No other manifest changes. Trade-off: none for this feature.

## app/build.gradle:
Problem: Room is not on the classpath.
Approach: `room-runtime`, `room-ktx`, `kapt room-compiler` 2.7.2 (needed for Kotlin 2.2 metadata).
What I did: `kapt` matches existing Hilt. Trade-off: KSP is faster; the project already uses kapt. `room-ktx` adds `suspend` DAO support.

## ErrorMapperTest.kt / WeatherRepositoryImplTest.kt / MainViewModelTest.kt:
Problem: Assignment required mocked/faked unit tests for mapper, repository, dispatchers, network, and DAO.
Approach: Fake `WeatherApi`, `ForecastDao`, `NetworkChecker`, `DispatcherProvider`. `runTest` + `UnconfinedTestDispatcher`. No Robolectric.
What I did: Mapper tests for 401, 503, DNS, JSON, unexpected. Repository tests for offline cache, offline empty, fresh cache skip API, stale fetch+upsert, API fail fallback, empty city. ViewModel tests for blank, empty, success, error+cache. Trade-off: fakes do not exercise Room SQL or real ConnectivityManager; those would be instrumented tests.

## WeatherApi.kt / ForecastResponse.kt / NetworkModule.kt:
Problem: None for this change — they already model Open-Meteo and provide Retrofit.
Approach: Left them in place. Remote data source sits on top of `WeatherApi`.
What I did: No signature changes. `Moshi` still parses `is_day`. Unsafe OkHttp client was already in the starter.

---

## Cache rules (short)

| Condition | Result |
| --- | --- |
| Offline, have row | Success, cached |
| Offline, no row | Failure, no internet |
| Online, row younger than 15 min | Success, cached |
| Online, stale/missing, city found, API ok | Success, save row |
| Online, API fails, have row | Failure + cached UI |
| City unknown, no row | Empty |

## Libraries used

- **Hilt**: compile-time DI. `@Module` / `@Provides` / `@Inject`.
- **Retrofit**: HTTP for `/v1/forecast`.
- **Moshi**: JSON to `ForecastResponse`.
- **Room**: SQLite cache.
- **Coroutines / Flow**: `suspend`, `withContext`, `StateFlow`.
- **Compose**: UI `when` on sealed state.
- **JUnit + kotlinx-coroutines-test**: JVM unit tests.
