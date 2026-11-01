# Lokalny model ręcznych oznaczeń

Model jest przeznaczony do propozycji znanych oznaczeń: C/K/IEW/RIEW/R/Z + 1–16 cyfr. Nie jest czytnikiem zdań ani pisma łączonego. Zdjęcie pozostaje na urządzeniu. Nie użyto zdjęć użytkownika do trenowania.

## Metoda

Użytkownik zaznacza pojedynczy wiersz. `GlyphSegmenter` progowaniem Otsu i składowymi obrazu oddziela znaki, odrzuca drobny szum, skaluje każdy znak do 20×20 na polu 28×28. Mała sieć CNN ma wspólne cechy i osobne głowice liter, cyfr i rodzaju znaku. Wejście: float32 [1,28,28,1], jasny znak na czarnym tle. Wyjście: 18 prawdopodobieństw w kolejności `0123456789CEIKRWZ?`. Wagi float16, obliczenia lokalne TensorFlow Lite na CPU.

`IdentifierDecoder` sprawdza tylko dozwolone prefiksy, po których muszą występować cyfry. Uwzględnia rolę znaku w oznaczeniu, zachowuje zera i zwraca do pięciu propozycji. Odrzuca część niepewnych wyników. Wynik nie jest automatycznie zatwierdzany.

Digital Ink z ML Kit wymaga śladów ruchu pióra, nie zdjęcia istniejącej kartki: https://developers.google.com/ml-kit/vision/digital-ink-recognition/ . Dlatego zastosowano klasyfikację znaków z obrazu. Dla zwykłego wydruku pozostaje osobny ML Kit Text Recognition.

## Dane i licencja

Cyfry: MNIST (Yann LeCun, Corinna Cortes, Christopher J. C. Burges), dokumentacja/licencja: https://keras.io/api/datasets/mnist/ . Litery: EMNIST Letters (Gregory Cohen, Saeed Afshar, Jonathan Tapson, Andre van Schaik), https://www.nist.gov/itl/products-and-services/emnist-dataset . Oryginalne zbiory obejmują ręczne znaki; litery zawierają formy małe i duże.

Wybrano C,E,I,K,R,W,Z oraz cyfry; pozostałe litery tworzą klasę „?”. Obrazy obrócono do prawidłowej orientacji, wykadrowano i znormalizowano. Użyto 88 560 znaków do uczenia, 9 840 do walidacji i 16 400 z oficjalnych zbiorów testowych do oceny. Model zakończył uczenie po 6 epokach z przywróceniem najlepszej walidacji. Parametry i skróty SHA256 wszystkich danych są w `evaluation.json`.

Wagi publikujemy na **CC BY-SA 3.0**, z uznaniem autorstwa zbiorów; szczegóły w [MODEL_LICENSE.txt](MODEL_LICENSE.txt), również dołączonym do APK. Ta licencja dotyczy wag modelu, nie całej aplikacji. Skrypt pobiera publiczne kopie danych z repozytoriów o przypiętych commitach i weryfikuje sumy.

## Co zmierzono

`evaluation.json` zawiera pomiar pojedynczych, znormalizowanych znaków: 95,88% poprawności dla znanych znaków; przy znanej roli znaku cyfry 98,46%, znane litery 96,55%. **To nie jest skuteczność odczytu całego numeru ze zdjęcia.**

`pipeline_evaluation.json` zawiera osobny pomiar całej ścieżki: segmentacja Java → model TFLite → dekoder Java. Oznaczenia złożono z losowych znaków oficjalnego zbioru testowego na czystym tle, ze stałymi odstępami. Końcowy pomiar: 70.00% poprawnych pierwszych propozycji, 72.50% wyników zawierających poprawny numer wśród propozycji, 22.33% bez propozycji. Dokładne wyniki i wszystkie propozycje zapisano w pliku. Pierwszy prototyp jednej głowicy uzyskał 64% poprawnych pierwszych propozycji na 300 takich oznaczeniach, wariant dołączony do aplikacji 70% na tych samych przykładach. Końcowy pomiar ma inny seed i 600 oznaczeń; przykłady korzystają z tej samej puli testowych znaków, więc nie stanowią niezależnego zbioru terenowego.

Nie wykonano porównania z ML Kit na tych samych zdjęciach. Nie zmierzono skuteczności na telefonie ani na rzeczywistych kartkach użytkownika. Cienie, przekrzywienie, linie tabel, dotykające się znaki i nietypowe kształty mogą powodować błędy. Klasa nieznanych znaków nie jest niezawodnym filtrem. Odczyt pozostaje eksperymentalną podpowiedzią wymagającą sprawdzenia prefiksu i każdej cyfry.

## Odtworzenie

Python 3.12, TensorFlow CPU 2.16.1, tf_keras 2.16.0 (bez dodatkowej instalacji pełnego tensorflow), Pillow 11.3, NumPy 1.26.4. Obliczenia CPU mogą różnić się nieznacznie między środowiskami. SHA256 dołączonych wag pozwala zweryfikować dokładny opublikowany plik.

```sh
python download_data.py --out /tmp/qc-data
TF_USE_LEGACY_KERAS=1 python train_glyphs.py --data /tmp/qc-data --out /tmp/qc-model --epochs 8
javac -d /tmp/qc-java ../app/src/main/java/pl/climagold/qc/GlyphSegmenter.java ../app/src/main/java/pl/climagold/qc/IdentifierDecoder.java PipelineHarness.java
python evaluate_pipeline.py --trained /tmp/qc-model --java java --classpath /tmp/qc-java --out /tmp/qc-evaluation
```

Są to polecenia z katalogu `model`. Zwykłe budowanie APK korzysta z gotowego pliku `app/src/main/assets/handwritten_glyphs.tflite`.
