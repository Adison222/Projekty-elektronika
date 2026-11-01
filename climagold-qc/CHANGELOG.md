# 1.3 preview

- Prefiksy C (centrala), K (kompakt), IEW (inne zamówienie), RIEW (inne zamówienie elementów automatyki) z numerem obok, ze spacją lub bez.
- IEW/RIEW bez obowiązkowego separatora; kanoniczna postać z ukośnikiem, zachowane zera wiodące.
- Dwa lokalne odczyty obrazu: oryginał i poprawiony kontrast, zarówno dla skanera, jak i importu.
- Jawne sugestie korekty OCR dla znanych prefiksów i części liczbowej; brak automatycznej zamiany ręcznie wpisanych identyfikatorów.
- Czytelne ręczne litery drukowane można próbować odczytać offline; skuteczność na rzeczywistych zdjęciach pozostaje do sprawdzenia. Nie dodano dedykowanego modelu pisma ręcznego.
- 43 sprawdzenia parsera, w tym fałszywe dopasowania, OCR, zera i zakres przy granicy long.

# 1.2 preview

- Nowy interfejs, układ akcji, przewijanie, animowane przejścia i efekty dotyku.
- Import zdjęcia do OCR z podglądem, skalowaniem i obsługą orientacji.
- Edycja wyniku OCR przed wyborem numeru.
- Osobny tryb numerów ogólnych bez wymogu prefiksu.
- Opcjonalny odczyt pisma ręcznego z importowanego zdjęcia przez Azure AI Vision Read; wymaga konfiguracji i jawnej akcji wysłania.
- Uprawnienie aparatu dopiero przy użyciu skanera.
- Zachowany aparat telefonu i przypisywanie całej serii bez rekompresji.

# v1.1 preview — 2026-10-31

- Serie zdjęć wykonuje zewnętrzna aplikacja aparatu telefonu z jej własnym interfejsem i obiektywami.
- Przywracanie rozpoczętej sesji, wykrywanie nowych zdjęć w DCIM i wybór całej serii po powrocie.
- Android 11+: systemowa zgoda na serię i przenoszenie bez kompresji. Android 10: kopiowanie bez ponownego kodowania, oryginały pozostają na miejscu.
- Dostęp do wszystkich zdjęć pozwala wykryć poprzednie foldery również ze starszych instalacji.
- CameraX służy wyłącznie do skanowania numerów. Usunięto ścieżkę fotografowania centrali we wbudowanym aparacie.
- Zachowano podpis APK v1, podniesiono versionCode do 2. Szczegóły ograniczeń i testów w README.

# v1 preview — 2026-10-31

Pierwsza wersja testowa: CameraX, OCR offline ML Kit, wybór numeru i zakresów, zatwierdzanie przed serią, zapis w DCIM, informacja o wcześniejszych zdjęciach, galeria z gestami, ostrość i zoom, lampa, zapamiętanie ostatniej centrali, logo Climagold i złoto-grafitowy motyw. Szczegóły ograniczeń i testów w README.md.
