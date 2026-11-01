# 1.5 preview

- Prefiks R, np. R14577, w parserze, OCR, zakresach i nazwach folderów.
- Oznaczenia IEW/RIEW/Z/R bez separatora przed cyframi, np. IEW123 i RIEW123.
- Normalizacja oznaczeń zapamiętanych sesji z zachowaniem ich stanu.
- Galeria odczytuje także starsze foldery z separatorem; nowe zdjęcia zapisuje w poprawnym folderze bez separatora po prefiksie.
- 59 sprawdzeń parsera i testy wznowienia starszego oznaczenia; APK zgodny podpisem z v1.3/v1.4.

# 1.4 preview

- Automatyczne zakończenie przedziału serii po powrocie z aparatu i po wyłączeniu ekranu.
- Usługa pierwszoplanowa monitorująca blokadę podczas działania fabrycznego aparatu.
- Automatyczne przygotowanie i przypisanie serii po powrocie do aplikacji; wymagana systemowa zgoda Androida 11+ pozostaje. Android 10 kopiuje zdjęcia, zachowując oryginały.
- Trwały stan końca serii, ponawianie po odmowie zgody, częściowym błędzie i opóźnionym zapisie zdjęć.
- Ostatnia zakończona sesja z datą, numerem i przyciskiem dołączenia kolejnych zdjęć po potwierdzeniu.
- Nowy przedział przy wznowieniu nie obejmuje zdjęć z przerwy między sesjami.
- Testy granic sesji i symulowane testy cyklu życia Androida. Wymagane testy aparatu, blokady i MediaStore na telefonie.
- APK v1.4 podpisany tym samym kluczem co udostępniony APK v1.3.

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
