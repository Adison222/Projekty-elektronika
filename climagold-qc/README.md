# Climagold QC — v1.3 preview: prefiks + identyfikator

Android 10+. **Serię zdjęć wykonuje normalna aplikacja aparatu telefonu**, otwierana przez `INTENT_ACTION_STILL_IMAGE_CAMERA`. Zachowuje swój interfejs, przyciski obiektywów 2×/3×, flesz i pozostałe funkcje dostępne na danym telefonie. Climagold QC nie zastępuje tego aparatu. Wbudowany podgląd CameraX służy wyłącznie do skanowania numeru z karty lub naklejki.

## Co nowego w 1.3 — oznaczenia i podstawowy OCR offline
Aplikacja traktuje prefiks i numer obok niego jako jedno oznaczenie:

| Prefiks | Znaczenie | Przykład |
| --- | --- | --- |
| C | Centrala | `C 12345` → `C12345` |
| K | Kompakt | `K 0042` → `K0042` |
| IEW | Inne zamówienie | `IEW 123` → `IEW/123` |
| RIEW | Inne zamówienie elementów automatyki | `RIEW123` → `RIEW/123` |

Numer może być przyklejony do prefiksu lub oddzielony spacją. IEW/RIEW obsługują także slash i dwukropek; wynik jest ujednolicany do slasha. Zera wiodące są zachowywane. Prefiks bez numeru i same liczby z tabliczki nie tworzą oznaczenia w trybie centrali. Nie łączymy prefiksu z numerem z innego wiersza.

Skaner i import zdjęć wykonują **dwa odczyty offline**: oryginału i obrazu w skali szarości z podniesionym kontrastem. Możesz spróbować czytelnych ręcznych liter drukowanych i osobnych cyfr. To nadal ML Kit, a nie model wytrenowany specjalnie na piśmie ręcznym; bez przykładowych zdjęć z pracy nie potwierdzono skuteczności tej metody.

W ekranie **Sprawdź odczyt** wybierz **Podpowiedz oznaczenie C / K / IEW / RIEW**. Podpowiedzi szukają znanego prefiksu i numeru obok oraz proponują korekty `O→0`, `I/L/|→1`, `S→5`, `B→8`, `Z→2` w części liczbowej. Mogą także odczytać `R1EW` jako `RIEW`. Wymagana jest co najmniej jedna rzeczywiście rozpoznana cyfra. Sugestia dopiero po wybraniu trafia do pola edycji — **porównaj ją z oryginałem** przed zatwierdzeniem. Ręcznie wpisany numer nie podlega tym zamianom. Brak wyniku oznacza konieczność poprawienia oznaczenia lub wykonania wyraźniejszego zdjęcia, a nie automatyczne wysłanie do chmury.

## Co nowego w 1.2
- Nowy, granatowo-złoty interfejs, przewijany ekran główny, czytelna hierarchia akcji, przyciski z efektem dotyku i animowane przejścia. Animacje respektują systemowe wyłączenie animacji.
- **Wczytaj zdjęcie z numerem**: systemowy wybór jednego obrazu, podgląd, obsługa orientacji EXIF, odczyt offline i edytowalny wynik. Import do OCR nie potrzebuje uprawnienia do całej galerii i nie zmienia oryginału.
- **Numery ogólne**: osobny przełącznik pozwala wybierać liczby bez prefiksu, np. `001234`, i oznaczenia `AB-12/26`. Zachowuje zera wiodące. W tym trybie `123-125` jest jednym oznaczeniem, nie zakresem. Na zdjęciu mogą zostać wykryte także rok, napięcie czy inne liczby: wybierz właściwy numer.
- Odczyt zawsze można poprawić przed wyborem centrali. Tryb ogólny wybierz na ekranie głównym lub podczas korekty odczytu.
- Aparat prosi o dostęp dopiero przy uruchamianiu skanera.

## Pismo ręczne — opcjonalna usługa
Domyślny ML Kit działa offline i najlepiej rozpoznaje druk. Nie zapewnia niezawodnego odczytu pisma ręcznego. Dla pisma ręcznego dodano oddzielną integrację **Azure AI Vision Read v3.2**, obsługującą tekst drukowany i odręczny:

1. W Azure utwórz zasób Azure AI Vision obsługujący Read v3.2. Potrzebujesz własnego klucza i endpointu `https://NAZWA.cognitiveservices.azure.com/`. Usługa może być płatna.
2. W aplikacji wybierz **Wczytaj zdjęcie z numerem**, następnie **Pismo ręczne · OCR w chmurze**.
3. Wpisz adres i klucz. Przycisk **Wyślij i odczytaj** wysyła całe wybrane zdjęcie (pomniejszone do maks. 2400 px) do wskazanej usługi Microsoft. Nie następuje automatyczne wysyłanie po błędzie OCR offline.
4. Sprawdź i popraw wynik przed wyborem numeru. Skuteczność zależy od czytelności pisma i zdjęcia.

Zapamiętywany jest tylko adres usługi. Klucz jest używany w pamięci do jednej operacji, bez zapisu na dysku, w logach i repozytorium. W tej wersji chmurowy odczyt jest dostępny dla importowanego zdjęcia; skaner na żywo korzysta z OCR offline. Nie skonfigurowano konta Azure i nie wykonano odczytu na rzeczywistej usłudze podczas prac nad tą wersją.

## Instalacja
Nowy APK v1.3 jest budowany przez [GitHub Actions](https://github.com/Adison222/Projekty-elektronika/actions) jako artefakt `Climagold-QC-v1.3-preview-apk`. Pobierz artefakt z udanego uruchomienia dla odpowiedniego commita i rozpakuj APK. Wersja ma ten sam identyfikator aplikacji, ale podpis debug z CI może różnić się od wcześniejszej instalacji. Nie odinstalowuj używanej aplikacji bez zabezpieczenia danych sesji. Historyczny [APK v1.1](../releases/Climagold-QC-v1.1-preview.apk) nie zawiera nowych funkcji.

## Praca z centralą
1. Zeskanuj kartę/naklejkę, wczytaj zdjęcie albo wpisz numer. Domyślny OCR działa na telefonie, bez sieci.
2. Przy pierwszym użyciu zezwól na dostęp do **wszystkich zdjęć** — jest niezbędny, ponieważ zdjęcia tworzy osobna aplikacja aparatu. Dostęp tylko do wybranych zdjęć nie wystarczy do automatycznego wykrywania nowej serii.
3. Wybierz numer z listy i zatwierdź. Przy istniejących zdjęciach pojawi się informacja o tym samym folderze.
4. Otworzy się aparat telefonu. Rób dowolną serię, używając jego zoomu, obiektywów, flesza i podglądu. Nie ma zatwierdzania każdego zdjęcia przez Climagold QC.
5. Po zakończeniu wróć do Climagold QC przyciskiem Wstecz albo przez przełącznik aplikacji. Fabryczny aparat nie udostępnia przycisku „OK” kończącego sesję naszej aplikacji.
6. Wybierz **Wybierz i przypisz zdjęcia**. Sprawdź listę nowych zdjęć, odznacz ewentualne obce zdjęcia i zatwierdź całą serię.
7. **Android 11+:** zaakceptuj systemowe zezwolenie na modyfikację wybranej serii. Zdjęcia zostaną przeniesione do `DCIM/<numer>/`, bez dekodowania i ponownej kompresji. **Android 10:** aplikacja kopiuje serię do folderu centrali, pozostawiając oryginały w folderze aparatu; nie żąda zgody po każdym zdjęciu.

Sesja i jej numer są zapamiętywane również po ponownym otwarciu aplikacji. Można ponownie otworzyć aparat, dodać ujęcia albo zakończyć sesję bez przypisywania; zdjęcia nie zostaną wtedy usunięte. Nie zamykaj aplikacji podczas przypisywania. Błędy są raportowane; nieprzypisane oryginały pozostają w aparacie i można ponowić wybór serii.

## Numery i foldery
- `C14015-23` rozwija się do C14015…C14023; `C14568 c14578` daje dwa numery do wyboru.
- `C14877/26` z przykładowej karty generatora daje C14877, bez roku.
- C/K tracą prefiks w folderze: `K4567` → `DCIM/4567/`.
- IEW/RIEW i Z zachowują prefiks. Format IEW/RIEW wymaga numeru; separator przed nim jest opcjonalny. OCR normalizuje litery i backslash do slash; slash w pojedynczej nazwie folderu zastępuje `∕`, ponieważ `/` jest separatorem katalogów.
- Zakresy powyżej 501 numerów i nietypowe formaty wymagają ręcznej korekty.

## Jakość i galeria
Zdjęcia powstają z jakością ustawioną w aparacie telefonu. Na Androidzie 11+ zmieniana jest wyłącznie lokalizacja pliku przez MediaStore; aplikacja nie przelicza pikseli. Na Androidzie 10 kopiowane są bajty udostępnione przez system, bez ponownego kodowania obrazu; Android może ukrywać metadane lokalizacji GPS.

Podczas fotografowania używaj podglądu fabrycznego aparatu. Po przypisaniu dostępna jest też galeria centrali: szczypanie powiększa, przesuwanie przesuwa, podwójne stuknięcie dopasowuje obraz. Kolejne zdjęcia wybiera się przyciskami. Pomniejszanie podglądu nie zmienia pliku zdjęcia.

## Zweryfikowane i wymagające testu na telefonie
- Lokalnie zbudowano APK v1.3 i uruchomiono Android Lint bez błędów. Przeszło 43 sprawdzenia parsera i 9 sprawdzeń adresu usługi OCR. CI wykonuje te same kontrole.
- Wersja 1.3 nie obiecuje zgodności podpisu z historycznym APK.
- Nie testowano na fizycznym telefonie: uruchamiania aplikacji producenta, uprawnień MediaStore, przenoszenia zdjęć, skuteczności OCR i płynności. To wydanie testowe.
- Android wybiera aplikację obsługującą standardowe uruchomienie aparatu; przy kilku aplikacjach może pokazać wybór. Dostępność obiektywów i funkcji zależy od telefonu i aparatu, nie od Climagold QC.
- Wykrywane są nowe, ukończone obrazy w DCIM od rozpoczęcia sesji. System nie przekazuje identyfikatora „zdjęcie z naszej serii”, dlatego trzeba sprawdzić końcową listę. Inne zdjęcia utworzone lub zaimportowane w DCIM podczas sesji mogą na niej wystąpić. Pliki poza DCIM, filmy i część osobnych plików RAW nie są obsługiwane. Jeśli aparat jeszcze zapisuje zdjęcie, ponów wyszukiwanie.
- Brak gwarancji 120 Hz; odświeżaniem fabrycznego aparatu zarządza producent. Nasz ekran prosi o 120 Hz, jeśli telefon udostępnia taki tryb.
- APK jest podpisany kluczem debug do pilotażu; przed wdrożeniem produkcyjnym trzeba przygotować stały podpis wydawniczy. Artefakt CI może mieć inny podpis debug niż udostępniony APK.

Logo pochodzi z repozytorium GeneratorV47, format przykładowej karty z `przyklady/PL-1.86/C14877_PL.pdf`. Zdjęcia i OCR domyślnie pozostają na telefonie. Wyjątkiem jest jawne użycie opcjonalnego odczytu pisma ręcznego w chmurze.

## Budowanie
JDK 17, Android SDK 35, Gradle 8.9, katalog `climagold-qc`:

```sh
gradle assembleDebug lintDebug
mkdir -p /tmp/qc-tests
javac -d /tmp/qc-tests app/src/main/java/pl/climagold/qc/Numbers.java tests/NumbersTest.java
java -cp /tmp/qc-tests NumbersTest
javac -cp "$ANDROID_HOME/platforms/android-35/android.jar" -d /tmp/qc-tests app/src/main/java/pl/climagold/qc/HandwritingOcr.java tests/HandwritingEndpointTest.java
java -cp "/tmp/qc-tests:$ANDROID_HOME/platforms/android-35/android.jar" pl.climagold.qc.HandwritingEndpointTest
```

Workflow `.github/workflows/android.yml` buduje APK, uruchamia lint i testy parsera oraz udostępnia artefakt.
