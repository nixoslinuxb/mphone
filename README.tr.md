# pcm — Çevrimdışı Telefon, Rehber ve SMS

[English](README.md) · **Türkçe**

Kotlin ve Jetpack Compose ile yazılmış, tamamen çevrimdışı çalışan açık kaynak Android telefon, rehber ve mesajlaşma uygulaması. Ağ izni yok, telemetri yok, hesap yok — aramalar, kişiler ve mesajlar cihazdan dışarı çıkmaz.

![Platform](https://img.shields.io/badge/platform-Android-3DDC84)
![Kotlin](https://img.shields.io/badge/Kotlin-2.2-7F52FF)
![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4)
![License](https://img.shields.io/badge/license-MIT-blue)

## Özellikler

### Telefon

- Canlı kişi eşleştirmeli ve T9 benzeri aramalı tuş takımı.
- Tam ekran gelen / giden arama arayüzü, arama süresi ve arama kaydı.
- Spam koruması: Türk spam numarası engelleme, yurt dışı numara filtresi ve kurumsal arayan kimliği kaydı (bankalar, operatörler, kargo vb.).
- Engellenen numaralar ve spam arama listeleri.
- Uygulama içi dalga formu oynatıcılı arama ses kaydı.
- Çift SIM desteği ve arama başına SIM seçimi.
- Varsayılan telefon uygulaması olarak atanabilme (`InCallService` + `CallScreeningService`).

### Rehber

- Sistem rehber sağlayıcısından okuma ve yazma.
- Kişi, fotoğraf ve not ekleme, düzenleme, silme.
- Normalize edilmiş numaraya göre mükerrer kişi birleştirme.
- İzin olmadan da erişilebilen yerel önbellek.

### Mesajlar (SMS)

- Material 3 sohbet arayüzlü konuşma akışları.
- Gönderme, alma, çok parçalı SMS ve uzun basınca SIM seçimi.
- **Tek tek mesaj silme** — mesaja uzun basıp yalnızca o mesajı silme.
- **Marka mesajları salt okunur** — alfanümerik gönderici kimliklerine (banka, kargo vb.) yanıt gönderilemez.
- Mesaj içinde bağlantı algılama, metin / bağlantı kopyalama, panodan yapıştırma.
- Gönderim hız sınırı ve mükerrer gönderim koruması.
- Spam SMS algılama ve spam gelen kutusu.
- Bildirimden hızlı yanıt ve "okundu olarak işaretle".
- Varsayılan SMS uygulaması olarak atanabilme.

### Gizlilik

- `INTERNET` izni yok — uygulama hiçbir yere veri gönderemez.
- Tüm veriler sistem sağlayıcılarında ve cihaz üzerindeki yerel önbellekte kalır.
- Analitik yok, reklam yok, hesap yok.

## Gereksinimler

| Araç | Sürüm |
| --- | --- |
| Android SDK | API 37 (`compileSdk` / `targetSdk`), `minSdk` 36 |
| JDK | 11+ |
| Gradle | 9.7.1 (wrapper dahil) |
| AGP | 9.4.1 |

## Derleme

1. `local.properties` oluşturup Android SDK yolunu belirtin:

   ```properties
   sdk.dir=/path/to/Android/Sdk
   ```

2. Debug APK'yı derleyin:

   ```bash
   ./gradlew assembleDebug
   ```

   APK çıktısı: `app/build/outputs/apk/debug/app-debug.apk`

3. Cihaza veya emülatöre kurun:

   ```bash
   ./gradlew installDebug
   # veya
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

## İzinler

Uygulama yalnızca bir telefon/SMS uygulamasının ihtiyaç duyduğu izinleri ister: rehber, arama kaydı, telefon durumu, SMS, arama ses kaydı, bildirimler ve tam ekran arama niyetleri. Bilinçli olarak **ağ erişimi yoktur**.

## Teknolojiler

- Kotlin + Jetpack Compose (Material 3)
- AndroidX Activity, Lifecycle, Core
- Kişi fotoğrafları için Coil
- Kotlin Coroutines / StateFlow

## Lisans

MIT — bkz. [LICENSE](LICENSE).
