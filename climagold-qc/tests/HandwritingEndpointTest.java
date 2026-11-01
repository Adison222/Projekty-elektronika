package pl.climagold.qc;

public final class HandwritingEndpointTest {
  private static void reject(String value) {
    try {
      HandwritingOcr.endpoint(value);
      throw new AssertionError("Accepted invalid endpoint: " + value);
    } catch (IllegalArgumentException expected) {
      // No upload credentials may be sent to arbitrary or insecure hosts.
    }
  }

  public static void main(String[] args) {
    if (!HandwritingOcr.endpoint(" https://my-ocr.cognitiveservices.azure.com ")
        .toString()
        .equals("https://my-ocr.cognitiveservices.azure.com/")) throw new AssertionError();
    reject("http://my-ocr.cognitiveservices.azure.com/");
    reject("https://example.com/");
    reject("https://my-ocr.cognitiveservices.azure.com.example.com/");
    reject("https://user@my-ocr.cognitiveservices.azure.com/");
    reject("https://my-ocr.cognitiveservices.azure.com:8443/");
    reject("https://my-ocr.cognitiveservices.azure.com/path");
    reject("https://my-ocr.cognitiveservices.azure.com/?key=test");
    reject("https://my-ocr.cognitiveservices.azure.com/#test");
    System.out.println("9 endpoint assertions passed");
  }
}
