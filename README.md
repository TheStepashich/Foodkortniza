# Фудкортница

![Логотип «Картошка фри»](logo.png)

Мини-браузер для доставки еды на Android: вкладки с магазинами внизу экрана, без адресной строки. Пакет `net.potatom.foodkortniza`, Android 9+.

## Сборка

JDK 17+, Android SDK 36. Путь к SDK — в локальном `local.properties` (не попадает в git).

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug     # app-debug.apk, 8,8 МБ
./gradlew :app:assembleRelease   # app-release.apk, 828 КБ, R8 + shrinkResources
```

Release подписан debug-ключом, ставится так же. AGP 9.x, отдельный Kotlin-плагин не нужен.

## Как работает

- Один процесс: WebView каждой вкладки живёт в памяти, переключение без перезагрузки. Куки и localStorage общие.
- «+» открывает каталог и форму «Своя ссылка» (только HTTPS). Долгое нажатие на вкладку — меню, перетаскивание меняет порядок.
- Закрытие вкладки: два подтверждения, удаление WebView и cookies её доменов.
- Разрешения: `INTERNET` и геопозиция (`ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION`). Сайт получает координаты только после нашего диалога и системного разрешения; приложение их не хранит.
- Вход и оплата остаются на сайте магазина — пароли и данные карт приложение не видит.

## Каталог

Новый магазин — одна запись в `stores.json` (id, title, entryUrl, allowedHosts, emoji, categoryId, priority), без правок кода. Файл копируется в `app/src/main/assets/stores.json` как fallback.

Приложение раз в 6 часов тянет каталог по HTTPS из `BuildConfig.STORES_URL` (сейчас заглушка), кэширует его и при ошибке падает обратно на встроенный JSON. Пользовательские вкладки не затрагиваются.

## Ветки

- `main` — исходники приложения.
- `gh-pages` — сайт в корне для GitHub Pages, без Gradle и исходников. APK лежит в `downloads/foodkortniza.apk`.

Обновление сайта: собрать `:app:assembleRelease` на `main`, переключиться на `gh-pages`, заменить APK в `downloads/`, закоммитить и запушить.
