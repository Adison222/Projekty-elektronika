import java.io.*;
import java.util.*;
import pl.climagold.qc.GlyphSegmenter;
import pl.climagold.qc.IdentifierDecoder;
/** Desktop harness for checking the exact Java segmentation/decoder against TFLite in Python. */
public final class PipelineHarness {
  public static void main(String[] args) throws Exception {
    try(DataInputStream in=new DataInputStream(new BufferedInputStream(new FileInputStream(args[1])));
        DataOutputStream out=new DataOutputStream(new BufferedOutputStream(new FileOutputStream(args[2])))) {
      int count=in.readInt();out.writeInt(count);
      for(int sample=0;sample<count;sample++) {
        if(args[0].equals("segment")) {
          int w=in.readInt(),h=in.readInt();int[] pixels=new int[w*h];
          for(int i=0;i<pixels.length;i++)pixels[i]=in.readInt();
          try {var glyphs=GlyphSegmenter.segment(pixels,w,h);out.writeInt(glyphs.size());for(float[] glyph:glyphs)for(float v:glyph)out.writeFloat(v);}
          catch(IllegalArgumentException e){out.writeInt(0);}
        } else {
          int n=in.readInt();float[][] scores=new float[n][18];
          for(float[] row:scores)for(int j=0;j<18;j++)row[j]=in.readFloat();
          var candidates=IdentifierDecoder.decode(scores);out.writeInt(candidates.size());
          for(var c:candidates){byte[] bytes=c.text.getBytes(java.nio.charset.StandardCharsets.UTF_8);out.writeInt(bytes.length);out.write(bytes);}
        }
      }
    }
  }
}
