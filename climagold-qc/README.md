# Climagold QC — v1.7 preview

Android 10+. Dokumentacja fotograficzna central przy użyciu fabrycznego aparatu telefonu.

[**Pobierz APK v1.7**](../releases/Climagold-QC-v1.7-preview.apk?raw=true)

## Zmiany w 1.7

- **Zdjęcie numeru trafia do folderu centrali.** „Odczytaj numer” wykonuje pełne zdjęcie. Po poprawieniu/wybraniu numeru i zatwierdzeniu centrali oryginalny plik jest zapisywany w jej folderze, przed otwarciem aparatu do serii. Dotyczy też zdjęcia wczytanego z galerii: powstaje kopia bez ponownej kompresji, źródło pozostaje w swoim miejscu. OCR korzysta z pomniejszonego podglądu, ale do folderu trafiają oryginalne bajty udostępnione przez system. Zdjęcie zachowuje orientację EXIF. Android może ograniczać metadane źródła.
- **Blokada krótsza niż 10 minut to przerwa.** Po odblokowaniu można kontynuować tę samą serię. Po upływie 10 minut blokady seria zostaje zamknięta z granicą czasu ustawioną na koniec tych 10 minut. Zwykły powrót z aparatu bez blokady nadal kończy serię.
- **Szybsze przypisywanie.** Usunięto obowiązkowe 5 sekund oczekiwania. Gotowe zdjęcia są przypisywane partiami po maksymalnie 64 wpisy MediaStore, bez kopiowania i kompresowania obrazów na Androidzie 11+. Postęp jest utrwalany po partii. Aparaty, które jeszcze zapisują, mają krótkie ponowienia; po przeniesieniu aplikacja ponownie sprawdza zdjęcia opublikowane z opóźnieniem. W razie niegotowego zdjęcia można ponowić zapis.
- **Opcjonalna jednorazowa konfiguracja zapisu bez każdorazowego pytania** na Androidzie 12+ przez systemowe uprawnienie zarządzania multimediami.
- **Podgląd w Galerii Samsung** dostaje wyłącznie listę zdjęć wybranego folderu/centrali. Lista central i wyszukiwanie pozostają w Climagold.
- **Usunięto odczyt pisma ręcznego**, model, jego dane i bibliotekę TensorFlow Lite. Pozostaje OCR wydruków offline oraz ręczne wpisywanie numeru. Azure pozostaje usunięte.

## Pierwsza konfiguracja szybkiego zapisu na Samsungu

Na Androidzie 12 lub nowszym otwórz **Ustawienia zapisu → Włącz szybki zapis**:

1. Zezwól na dostęp do **wszystkich zdjęć** i metadanych lokalizacji zdjęć.
2. Na ekranie systemowym włącz dla Climagold **Zarządzanie multimediami**.
3. Wróć do aplikacji. Ekran pokaże, czy wymagane uprawnienia są aktywne.

Android wymaga także `ACCESS_MEDIA_LOCATION`, aby żądanie modyfikacji mediów nie wyświetlało okna zgody. Nie jest to uprawnienie do bieżącej pozycji GPS telefonu. Aplikacja nie pobiera lokalizacji telefonu ani nie wysyła zdjęć.

To systemowe uprawnienie, nie obchodzenie zabezpieczeń przez nakładkę. Sama nakładka na aparat Samsunga nie zmienia właściciela zdjęć i nie znosi zgód. Aplikacja nadal wykonuje wymagane przez Android żądanie zapisu, ale system może je zatwierdzić automatycznie po nadaniu tych uprawnień. Bez nich oraz na Androidzie 11 pozostaje zgoda na partię zdjęć. Android 10 zachowuje dotychczasowe kopiowanie do folderu z pozostawieniem oryginałów.

**Zdjęcia z fabrycznego aparatu nadal są przypisywane po zakończeniu serii.** Nie wdrożono przenoszenia na bieżąco podczas pracy aparatu Samsunga ani nakładki ingerującej w jego interfejs. Bezpośredni zapis w docelowym folderze dotyczy zdjęcia numeru wykonanego przez Climagold. Nie zmieniamy aparatu do fotografowania serii.

Źródło wymagań systemowych: [Android — Media management permission](https://developer.android.com/training/data-storage/shared/media#media-management-permission).

## Naturalny przebieg pracy

1. **Nowa seria zdjęć** → zeskanuj wydruk, wczytaj zdjęcie albo wpisz numer.
2. Podpowiedzi pojawią się automatycznie. Sprawdź numer i wybierz **Rób zdjęcia**. Jeżeli użyto OCR, zdjęcie numeru zostanie zapisane w tym folderze.
3. Fotografuj fabrycznym aparatem Samsunga, korzystając z jego obiektywów i ustawień.
4. Możesz zablokować telefon na mniej niż 10 minut i wrócić do tej samej serii. Jeżeli po odblokowaniu widzisz Climagold zamiast aparatu, wybierz **Otwórz aparat**.
5. Powrót z aparatu bez blokady albo **Zakończ i przypisz zdjęcia** zamyka serię i uruchamia przypisywanie. Po długiej blokadzie wróć do Climagold po odblokowaniu, aby dokończyć zapis.
6. **Dołącz do ostatniej sesji** pozwala po potwierdzeniu zrobić kolejne zdjęcia tej samej centrali. Nie dołącza zdjęć wykonanych w przerwie pomiędzy seriami.

Błąd zapisu zdjęcia numeru zachowuje jego prywatny oryginał do ponowienia. Po ponownym uruchomieniu aplikacji dostępne jest **Dokończ numer ze zdjęcia**. Nowa seria lub anulowanie odczytu odrzuca niezapisany szkic. Zdjęcie numeru już zapisane w folderze pozostaje tam także wtedy, gdy później anulujesz serię w aparacie.

Przy błędzie przenoszenia zdjęcia pozostają w dotychczasowych folderach; wybierz **Dokończ przypisywanie zdjęć**. Nie odinstalowuj aplikacji z niezapisanym szkicem lub oczekującą serią.

## Galeria Samsung

**Centrale i zdjęcia** pokazuje foldery bezpośrednio w DCIM z cyfrą w nazwie i co najmniej jednym zdjęciem, z pominięciem folderów aparatu. Wyszukaj numer i dotknij folderu. Climagold pobiera wyłącznie jego zdjęcia i przekazuje je do `com.sec.android.gallery3d` jako ograniczony zestaw.

Użyty jest systemowy kontrakt [`MediaStore.ACTION_REVIEW_SECURE`](https://developer.android.com/reference/android/provider/MediaStore#ACTION_REVIEW_SECURE): podstawowy obraz + jawna lista pozostałych URI w `ClipData`. Zwykłe `ACTION_VIEW` nie gwarantuje ograniczenia przewijania do danej centrali, dlatego nie jest używane jako zamiennik. Ten tryb podglądu może ograniczać edycję i udostępnianie. Nie jest to nawigacja po wszystkich albumach telefonu.

**Obsługa tego kontraktu zależy od wersji Galerii Samsung i wymaga sprawdzenia na telefonie.** Jeśli galeria nie obsługuje żądania albo folder ma ponad 500 zdjęć (limit rozmiaru przekazywanego komunikatu), aplikacja użyje swojego podglądu wyłącznie zdjęć centrali. Gdy zewnętrzna galeria się otworzy, lecz wyświetli nieprawidłowy zestaw, po powrocie dostępny jest **Podgląd zastępczy**. Nie otwieramy po cichu całej galerii telefonu.

## Oznaczenia

Obsługiwane prefiksy: **C, K, IEW, RIEW, R, Z**, np. `C13932`, `K0042`, `IEW123`, `RIEW123`, `R14577`, `Z123`. Między prefiksem a cyframi nie ma `/`. Zachowujemy zera wiodące. OCR wydruków działa offline w dwóch przebiegach i podpowiada typowe korekty; propozycję zawsze sprawdź.

C/K mają numeryczne foldery (`C13932` → `DCIM/13932/`), pozostałe zachowują prefiks (`R14577` → `DCIM/R14577/`). Przeglądarka pokazuje rzeczywistą nazwę, bez zgadywania C/K. Dotychczasowe pliki nie są reorganizowane automatycznie. Galeria wybranej centrali uwzględnia również wcześniejsze warianty jej folderów.

`C14015-23` rozwija się do C14015…C14023; `C14877/26` daje C14877 bez roku. **Inny format numeru** pozwala wpisać samą liczbę lub niestandardowe oznaczenie. Slash w dalszej części nazwy folderu jest zastępowany przez `∕`.

## Instalacja i weryfikacja

APK v1.7 ma ten sam podpis co udostępnione v1.3–v1.6; można instalować jako aktualizację bez odinstalowywania. Suma SHA256: `releases/SHA256SUMS`. Podpis debug służy do pilotażu; historyczny APK v1.1 i niezależne artefakty CI mogą mieć inny podpis.

Kontrole: budowanie APK, Android Lint, testy parsera, okna sesji i Robolectric obejmujące blokadę krótszą niż 10 minut, granicę 10 minut i opóźnione wznowienie, zapis oryginału numeru i ponowienie po błędzie, ograniczoną listę przekazywaną galerii, przenoszenie partiami i częściowe niepowodzenie. Nie testowano na fizycznym Samsungu. Nie zmierzono rzeczywistej szybkości zapisu ani zachowania Galerii Samsung na urządzeniu — to wydanie testowe.

Usługa pierwszoplanowa pilnuje blokady. Wymuszone zatrzymanie procesu lub ograniczenia producenta mogą zakłócić działanie. Gdy system wstrzyma wykonanie timera podczas uśpienia, przy wznowieniu aplikacja sprawdza zapisany czas przerwy i zamyka okno na właściwej granicy 10 minut. Zmiana zegara w ramach tego samego uruchomienia telefonu nie wydłuża przerwy.

Przypisywanie serii opiera się na nowych zdjęciach w DCIM i przedziale czasu; aparat nie nadaje identyfikatora naszej sesji. Inne zdjęcia z tego przedziału mogą też zostać przypisane. Filmy, część osobnych RAW i pliki spoza DCIM nie są obsługiwane. Bardzo późno opublikowane zdjęcia mogą wymagać ręcznego przypisania.

## Budowanie

JDK 17, Android SDK 35, Gradle 8.9, katalog `climagold-qc`:

```sh
gradle assembleDebug lintDebug testDebugUnitTest
mkdir -p /tmp/qc-tests
javac -d /tmp/qc-tests app/src/main/java/pl/climagold/qc/Numbers.java tests/NumbersTest.java
java -cp /tmp/qc-tests NumbersTest
javac -d /tmp/qc-tests app/src/main/java/pl/climagold/qc/SessionWindow.java tests/SessionWindowTest.java
java -cp /tmp/qc-tests SessionWindowTest
```

Workflow `.github/workflows/android.yml` buduje i udostępnia artefakt APK. Logo pochodzi z repozytorium GeneratorV47.
