# Climagold QC — v1.6 preview

Aplikacja Android 10+ do fotografowania central. Zdjęcia wykonuje zwykły aparat telefonu ze swoimi obiektywami i ustawieniami. Climagold wybiera numer, pilnuje serii i porządkuje pliki.

[**Pobierz APK v1.6**](../releases/Climagold-QC-v1.6-preview.apk?raw=true)

## Co zmieniono

- Prostszy ekran startowy: **Nowa seria zdjęć** i **Centrale i zdjęcia**, a po zakończeniu serii także możliwość dołączenia do ostatniej sesji.
- Kolejność pracy: numer → zdjęcia → zapis. Czytelne przyciski, granatowo-złote kolory, efekty dotyku i krótkie przejścia respektujące systemowe wyłączenie animacji.
- Sugestie numerów wyświetlają się od razu po OCR i podczas wpisywania. Nie trzeba ich osobno otwierać.
- Przeglądarka central z wyszukiwaniem, liczbą zdjęć i otwieraniem galerii konkretnego folderu.
- Naprawiono **Invalid column date_taken**: zapytania używają właściwej kolumny Android MediaStore `datetaken`, przez stałą `DATE_TAKEN`.
- Usunięto Azure, konfigurację usługi i uprawnienie do Internetu. Odczyt wydruku i pisma ręcznego odbywa się lokalnie.
- Dodano mały model uczony na ręcznych literach i cyfrach oraz odczyt ograniczony do znanych prefiksów i numeru. To funkcja eksperymentalna; każdą propozycję trzeba sprawdzić.

## Instalacja i odzyskanie zakończonej serii

APK v1.6 ma ten sam podpis co udostępnione wersje v1.3–v1.5: instaluj jako aktualizację, **bez odinstalowywania aplikacji**. Zachowasz stan oczekującej serii. Po aktualizacji wróć do zakończonej serii i naciśnij **Dokończ przypisywanie zdjęć**. Poprawka błędu daty obejmuje wyszukiwanie zdjęć i kopiowanie na Androidzie 10.

Podpis różni się od historycznego APK v1.1. Jest to podpis debug do pilotażu; artefakty GitHub Actions mogą mieć inny podpis niż publikowany APK. Sumy plików znajdują się w `releases/SHA256SUMS`.

## Fotografowanie

1. Wybierz **Nowa seria zdjęć**. Wpisz numer, zeskanuj oznaczenie aparatem albo wczytaj jego zdjęcie.
2. Wybierz automatyczną podpowiedź lub popraw numer. Potwierdź właściwą centralę i wybierz **Rób zdjęcia**.
3. Rób serię aparatem telefonu. Nie trzeba zatwierdzać każdego ujęcia.
4. Wróć do Climagold: seria zakończy się i rozpocznie zapis. Blokada lub wygaszenie ekranu także zamyka przedział serii. **Po odblokowaniu wróć do aplikacji**, żeby dokończyć zapis.
5. Android 11+ pokaże zgodę na przeniesienie zdjęć do `DCIM/<numer>/`. Android 10 kopiuje zdjęcia i zachowuje oryginały. Obrazy nie są ponownie kompresowane.
6. Aby zrobić kolejne ujęcia tej samej centrali, wybierz **Dołącz do ostatniej sesji** i potwierdź. Zdjęcia z przerwy pomiędzy seriami nie są dołączane.

Potrzebny jest dostęp do wszystkich zdjęć, ponieważ tworzy je zewnętrzny aparat. Powiadomienie trwającej serii pozwala usłudze monitorować blokadę. Systemowej zgody na modyfikację cudzych zdjęć nie da się potwierdzić przy zablokowanym ekranie. Odmowa lub błąd zachowują serię do ponowienia. Zakończenie bez przypisywania nie usuwa zdjęć.

## Numery i odczyt

Obsługiwane prefiksy: **C, K, IEW, RIEW, R, Z**, np. `C13932`, `K0042`, `IEW123`, `RIEW123`, `R14577`, `Z123`. Prefiks musi mieć numer obok siebie. Między prefiksem a cyframi wynik nie zawiera `/`; stary zapis jest normalizowany. Zera wiodące pozostają.

- **Wydruk:** dwa lokalne odczyty ML Kit, oryginału i obrazu o podniesionym kontraście. Sugestie uwzględniają typowe pomyłki liter i cyfr.
- **Pismo ręczne:** wybierz tę opcję w skanerze lub przy wczytanym zdjęciu. Zaznacz palcem jeden wiersz zawierający tylko prefiks i numer, następnie wybierz rozpoznawanie. Model rozpoznaje osobne znaki, a reguły składają tylko dozwolone prefiksy i cyfry. Najlepszy materiał to ciemne, oddzielne litery drukowane i cyfry na jasnym tle. Pismo łączone, nakładające się znaki, linie tabeli, cień i obrót mogą uniemożliwić odczyt. Wtedy popraw kadr lub wpisz numer.
- **Inny format numeru:** pozwala użyć samych cyfr albo niestandardowego oznaczenia. Ręczny model jest przeznaczony do znanych prefiksów, nie do dowolnego tekstu.

Propozycje są edytowalne, nigdy nie rozpoczynają serii bez potwierdzenia. Nie należy traktować ich jako pewnego odczytu. [Opis modelu, źródła danych i wyniki pomiarów](model/README.md).

## Centrale i zdjęcia

Lista pokazuje foldery bezpośrednio w DCIM, zawierające zdjęcia i cyfrę w nazwie, z pominięciem folderów aparatu. Najnowsze są na górze. Wpisz numer w wyszukiwarkę i dotknij folderu, aby otworzyć jego zdjęcia w aplikacji. Galeria obsługuje powiększanie, przesuwanie i kolejne zdjęcia.

C/K mają wspólne nazwy liczbowe (`C13932` → `DCIM/13932/`), więc lista pokazuje rzeczywistą nazwę `13932`, bez zgadywania prefiksu. Pozostałe zachowują prefiks (`R14577` → `DCIM/R14577/`). Puste foldery nie są widoczne w MediaStore. Istniejące pliki nie są automatycznie reorganizowane.

Zakres `C14015-23` rozwija się do C14015…C14023; `C14877/26` daje C14877 bez roku. Slash należący do dalszej części niestandardowego numeru jest zapisywany w pojedynczej nazwie folderu jako `∕`. Galeria wybranej centrali uwzględnia stare warianty nazw z separatorem.

## Weryfikacja i ograniczenia

Zbudowano APK, uruchomiono Android Lint, testy parsera, reguł sesji i Robolectric: cykl życia, przenoszenie, poprawna kolumna daty, automatyczne sugestie, foldery i dekoder ręcznych znaków. Sprawdzono również renderowane podglądy ekranów. Model sprawdzono na publicznych danych i syntetycznych oznaczeniach; szczegóły w dokumentacji modelu.

Nie testowano tej wersji na fizycznym telefonie ani na rzeczywistych zdjęciach ręcznych oznaczeń użytkownika. Wydanie pozostaje testowe. Zachowanie zewnętrznego aparatu, uprawnień i procesów w tle zależy od telefonu. Wymuszone zatrzymanie aplikacji lub restart może uniemożliwić odebranie blokady.

Wykrywanie serii opiera się na nowych zdjęciach w DCIM i czasie wykonania, z datą dodania jako rezerwą. Inne zdjęcia z tego samego przedziału też mogą zostać zakwalifikowane. Filmy, pliki poza DCIM i część osobnych RAW nie są obsługiwane. Przy opóźnionym zapisie aparatu ponów wyszukiwanie.

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

Model jest dołączony do APK, nie wymaga trenowania podczas budowania aplikacji. Workflow `.github/workflows/android.yml` wykonuje kontrole i publikuje artefakt APK.

Logo pochodzi z repozytorium GeneratorV47. Informacje o danych i licencji wag: `model/README.md` oraz dołączony `MODEL_LICENSE.txt`.
