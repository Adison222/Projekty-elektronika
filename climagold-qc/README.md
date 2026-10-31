# Climagold QC — v1 preview

Aplikacja Android 10+ do zdjęć kontroli jakości central. Wydanie testowe, niezweryfikowane na fizycznym telefonie.

## Instalacja i obsługa
Pobierz `releases/Climagold-QC-v1-preview.apk` z tego repozytorium, otwórz na telefonie i zezwól na instalację z wybranego źródła. Po uruchomieniu zezwól na aparat.

1. Skanuj kartę/naklejkę albo wpisz numer. OCR działa lokalnie (model jest w APK).
2. Wybierz numer z listy i zatwierdź folder. Zakres C14015-23 daje C14015…C14023. Numer C14877/26 z przykładowej karty generatora daje C14877.
3. Rób serię przyciskiem „Zrób zdjęcie”. Nie ma zatwierdzania każdego ujęcia. Poczekaj na komunikat o zapisaniu przed następnym zdjęciem.
4. „Zdjęcia tej centrali” otwiera galerię: szczypanie powiększa, przeciąganie przesuwa, podwójne stuknięcie przywraca dopasowanie. Przyciski przełączają zdjęcia.
5. „OK” kończy serię. Ostatni numer jest zapamiętany po zamknięciu aplikacji.

Zdjęcia trafiają przez Android MediaStore do `DCIM/<numer>/`. C/K tracą prefiks. Ponowny wybór numeru wykrywa istniejące zdjęcia i wyświetla komunikat. Pliki mają unikalne nazwy, nie są nadpisywane. IEW/RIEW i Z zachowują prefiks. OCR normalizuje wielkość liter oraz backslash do slash; slash w nazwie folderu zastępuje znak ∕, ponieważ separator ścieżki nie może być częścią nazwy jednego folderu.

## Aparat i jakość
CameraX wybiera najwyższą dostępną rozdzielczość dla danego zestawu strumieni, tryb maksymalnej jakości i JPEG quality 100. Zapisane JPEG nie są ponownie skalowane ani kompresowane przez aplikację. JPEG nadal jest formatem stratnym. Miniatura galerii jest pomniejszana wyłącznie do wyświetlania; oryginał pozostaje bez zmian.

Aparat obsługuje powiększenie gestem, dotknięcie punktu ostrości i zmianę trybu lampy. Nie daje wszystkich funkcji aplikacji producenta (np. firmowego HDR, nocnego przetwarzania i wszystkich obiektywów). Aplikacja prosi system o tryb ekranu 120 Hz, jeśli taki tryb istnieje; nie gwarantuje 120 klatek aparatu ani stałej płynności.

## Weryfikacja i ograniczenia
- Przetestowano 10 przypadków parsera, kompilację APK oraz Android Lint.
- Nie przeprowadzono testów fotograficznych, gestów i skuteczności OCR na fizycznym telefonie. Zawsze sprawdź numer przed zatwierdzeniem.
- Zweryfikowano format karty z `GeneratorV47/przyklady/PL-1.86/C14877_PL.pdf`; logo pochodzi z `generator_naklejek/dokumentacja/climagold_ui.png`.
- Zakresy powyżej 501 numerów i nieznane formaty nie są automatycznie rozwijane. Format IEW/RIEW musi zawierać separator i numer.
- Wykrywanie istniejących folderów obejmuje zdjęcia widoczne tej instalacji aplikacji. Po reinstalacji lub przy zdjęciach utworzonych przez inne aplikacje potrzebna będzie dodatkowa obsługa dostępu. Nie odinstalowuj aplikacji między seriami.
- Galeria ma gesty powiększania/przesuwania, lecz zmiana zdjęć odbywa się przyciskami. Nie ma jeszcze kosza, adnotacji, eksportu ZIP ani synchronizacji.
- APK jest podpisany kluczem debug i przeznaczony do pilotażu. Stały podpis wydawniczy trzeba przygotować przed wdrożeniem produkcyjnym. Kolejne buildy CI mogą użyć innego klucza debug.

## Budowanie
JDK 17, Android SDK 35, Gradle 8.9.

```sh
gradle assembleDebug lintDebug
mkdir -p /tmp/qc-tests
javac -d /tmp/qc-tests app/src/main/java/pl/climagold/qc/Numbers.java tests/NumbersTest.java
java -cp /tmp/qc-tests NumbersTest
```

Workflow `.github/workflows/android.yml` buduje APK i udostępnia artefakt. Brak usług, sekretów i połączeń chmurowych potrzebnych do działania aplikacji. Zdjęcia nie opuszczają telefonu.
