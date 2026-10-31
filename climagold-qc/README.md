# Climagold QC — v1.1 preview: aparat telefonu

Android 10+. **Serię zdjęć wykonuje normalna aplikacja aparatu telefonu**, otwierana przez `INTENT_ACTION_STILL_IMAGE_CAMERA`. Zachowuje swój interfejs, przyciski obiektywów 2×/3×, flesz i pozostałe funkcje dostępne na danym telefonie. Climagold QC nie zastępuje tego aparatu. Wbudowany podgląd CameraX służy wyłącznie do skanowania numeru z karty lub naklejki.

## Instalacja
Pobierz [Climagold-QC-v1.1-preview.apk](../releases/Climagold-QC-v1.1-preview.apk). Zainstaluj jako aktualizację v1, bez odinstalowywania poprzedniej wersji. Wersja ma ten sam identyfikator aplikacji i podpis debug jak dostarczony wcześniej APK v1.

## Praca z centralą
1. Zeskanuj kartę/naklejkę albo wpisz numer. OCR działa na telefonie, bez sieci.
2. Przy pierwszym użyciu zezwól na dostęp do **wszystkich zdjęć** — jest niezbędny, ponieważ zdjęcia tworzy osobna aplikacja aparatu. Dostęp tylko do wybranych zdjęć nie wystarczy do automatycznego wykrywania nowej serii.
3. Wybierz numer z listy i zatwierdź. Przy istniejących zdjęciach pojawi się informacja o tym samym folderze.
4. Otworzy się aparat telefonu. Rób dowolną serię, używając jego zoomu, obiektywów, flesza i podglądu. Nie ma zatwierdzania każdego zdjęcia przez Climagold QC.
5. Po zakończeniu wróć do Climagold QC przyciskiem Wstecz albo przez przełącznik aplikacji. Fabryczny aparat nie udostępnia przycisku „OK” kończącego sesję naszej aplikacji.
6. Wybierz **OK • wybierz i przypisz serię**. Sprawdź listę nowych zdjęć, odznacz ewentualne obce zdjęcia i zatwierdź całą serię.
7. **Android 11+:** zaakceptuj systemowe zezwolenie na modyfikację wybranej serii. Zdjęcia zostaną przeniesione do `DCIM/<numer>/`, bez dekodowania i ponownej kompresji. **Android 10:** aplikacja kopiuje serię do folderu centrali, pozostawiając oryginały w folderze aparatu; nie żąda zgody po każdym zdjęciu.

Sesja i jej numer są zapamiętywane również po ponownym otwarciu aplikacji. Można ponownie otworzyć aparat, dodać ujęcia albo zakończyć sesję bez przypisywania; zdjęcia nie zostaną wtedy usunięte. Nie zamykaj aplikacji podczas przypisywania. Błędy są raportowane; nieprzypisane oryginały pozostają w aparacie i można ponowić wybór serii.

## Numery i foldery
- `C14015-23` rozwija się do C14015…C14023; `C14568 c14578` daje dwa numery do wyboru.
- `C14877/26` z przykładowej karty generatora daje C14877, bez roku.
- C/K tracą prefiks w folderze: `K4567` → `DCIM/4567/`.
- IEW/RIEW i Z zachowują prefiks. Format IEW/RIEW wymaga separatora i numeru. OCR normalizuje litery i backslash do slash; slash w pojedynczej nazwie folderu zastępuje `∕`, ponieważ `/` jest separatorem katalogów.
- Zakresy powyżej 501 numerów i nietypowe formaty wymagają ręcznej korekty.

## Jakość i galeria
Zdjęcia powstają z jakością ustawioną w aparacie telefonu. Na Androidzie 11+ zmieniana jest wyłącznie lokalizacja pliku przez MediaStore; aplikacja nie przelicza pikseli. Na Androidzie 10 kopiowane są bajty udostępnione przez system, bez ponownego kodowania obrazu; Android może ukrywać metadane lokalizacji GPS.

Podczas fotografowania używaj podglądu fabrycznego aparatu. Po przypisaniu dostępna jest też galeria centrali: szczypanie powiększa, przesuwanie przesuwa, podwójne stuknięcie dopasowuje obraz. Kolejne zdjęcia wybiera się przyciskami. Pomniejszanie podglądu nie zmienia pliku zdjęcia.

## Zweryfikowane i wymagające testu na telefonie
- Zbudowano APK, Android Lint zakończył się bez błędów; 10 testów parsera przeszło.
- Zweryfikowano podpis APK i zgodność podpisu z v1.
- Nie testowano na fizycznym telefonie: uruchamiania aplikacji producenta, uprawnień MediaStore, przenoszenia zdjęć, skuteczności OCR i płynności. To wydanie testowe.
- Android wybiera aplikację obsługującą standardowe uruchomienie aparatu; przy kilku aplikacjach może pokazać wybór. Dostępność obiektywów i funkcji zależy od telefonu i aparatu, nie od Climagold QC.
- Wykrywane są nowe, ukończone obrazy w DCIM od rozpoczęcia sesji. System nie przekazuje identyfikatora „zdjęcie z naszej serii”, dlatego trzeba sprawdzić końcową listę. Inne zdjęcia utworzone lub zaimportowane w DCIM podczas sesji mogą na niej wystąpić. Pliki poza DCIM, filmy i część osobnych plików RAW nie są obsługiwane. Jeśli aparat jeszcze zapisuje zdjęcie, ponów wyszukiwanie.
- Brak gwarancji 120 Hz; odświeżaniem fabrycznego aparatu zarządza producent. Nasz ekran prosi o 120 Hz, jeśli telefon udostępnia taki tryb.
- APK jest podpisany kluczem debug do pilotażu; przed wdrożeniem produkcyjnym trzeba przygotować stały podpis wydawniczy. Artefakt CI może mieć inny podpis debug niż udostępniony APK.

Logo pochodzi z repozytorium GeneratorV47, format przykładowej karty z `przyklady/PL-1.86/C14877_PL.pdf`. Zdjęcia i OCR nie są wysyłane do chmury.

## Budowanie
JDK 17, Android SDK 35, Gradle 8.9, katalog `climagold-qc`:

```sh
gradle assembleDebug lintDebug
mkdir -p /tmp/qc-tests
javac -d /tmp/qc-tests app/src/main/java/pl/climagold/qc/Numbers.java tests/NumbersTest.java
java -cp /tmp/qc-tests NumbersTest
```

Workflow `.github/workflows/android.yml` buduje APK, uruchamia lint i testy parsera oraz udostępnia artefakt.
