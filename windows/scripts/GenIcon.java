import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.regex.*;
import javax.imageio.ImageIO;

/**
 * Builds packaging/tarkas.ico (and tarkas-tray.png) from the official T.A.L. logo files, never redrawn:
 * the SVG polygons and polylines are read and rasterised as they are. Sizes below 48 px use B-camadas, the
 * others AB-topo (identity rule). The retired lime accent polygon (#C7FF32) is skipped.
 * Usage: java windows/scripts/GenIcon.java <logo dir> <out dir>
 */
public class GenIcon {
  static final Pattern EL = Pattern.compile("<(polygon|polyline)\s+points=\"([^\"]+)\"([^>]*)/>");
  static String attr(String s, String name) {
    Matcher m = Pattern.compile(name + "=\"([^\"]*)\"").matcher(s);
    return m.find() ? m.group(1) : null;
  }
  static Color color(String hex) {
    return hex == null || hex.equals("none") ? null : Color.decode(hex);
  }

  static BufferedImage render(String svg, int size) {
    double vbW = 217.8, vbH = 310.0, tx = 56.961524, ty = 185;
    BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = img.createGraphics();
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    g.setColor(Color.decode("#080808"));
    g.fill(new RoundRectangle2D.Double(0, 0, size, size, size * 0.18, size * 0.18));
    double pad = size * 0.10;
    double s = (size - 2 * pad) / vbH;
    g.translate((size - vbW * s) / 2, pad);
    g.scale(s, s);
    g.translate(tx, ty);
    Matcher m = EL.matcher(svg);
    while (m.find()) {
      String rest = m.group(3);
      String fill = attr(rest, "fill");
      if ("#C7FF32".equalsIgnoreCase(fill)) continue;
      Path2D.Double p = new Path2D.Double();
      String[] pts = m.group(2).trim().split("\s+");
      for (int i = 0; i < pts.length; i++) {
        String[] xy = pts[i].split(",");
        double x = Double.parseDouble(xy[0]), y = Double.parseDouble(xy[1]);
        if (i == 0) p.moveTo(x, y); else p.lineTo(x, y);
      }
      if (m.group(1).equals("polygon")) p.closePath();
      Color f = color(fill);
      if (f != null) { g.setColor(f); g.fill(p); }
      Color st = color(attr(rest, "stroke"));
      if (st != null) {
        float w = Float.parseFloat(attr(rest, "stroke-width"));
        String cap = attr(rest, "stroke-linecap"), join = attr(rest, "stroke-linejoin");
        g.setStroke(new BasicStroke(w, "round".equals(cap) ? BasicStroke.CAP_ROUND : BasicStroke.CAP_BUTT,
          "round".equals(join) ? BasicStroke.JOIN_ROUND : BasicStroke.JOIN_MITER, 4f));
        g.setColor(st); g.draw(p);
      }
    }
    g.dispose();
    return img;
  }

  public static void main(String[] a) throws Exception {
    Path logos = Paths.get(a[0]), out = Paths.get(a[1]);
    Files.createDirectories(out);
    String topo = Files.readString(logos.resolve("AB-topo-negativo.svg"));
    String camadas = Files.readString(logos.resolve("B-camadas-negativo.svg"));
    int[] sizes = {16, 24, 32, 48, 64, 128, 256};
    List<byte[]> pngs = new ArrayList<>();
    for (int sz : sizes) {
      BufferedImage img = render(sz < 48 ? camadas : topo, sz);
      ByteArrayOutputStream bo = new ByteArrayOutputStream();
      ImageIO.write(img, "png", bo);
      pngs.add(bo.toByteArray());
      if (sz == 32) ImageIO.write(img, "png", out.resolve("tarkas-tray.png").toFile());
      if (sz == 256) ImageIO.write(img, "png", out.resolve("tarkas.png").toFile());
    }
    ByteBuffer b = ByteBuffer.allocate(6 + 16 * sizes.length + pngs.stream().mapToInt(x -> x.length).sum()).order(ByteOrder.LITTLE_ENDIAN);
    b.putShort((short) 0).putShort((short) 1).putShort((short) sizes.length);
    int offset = 6 + 16 * sizes.length;
    for (int i = 0; i < sizes.length; i++) {
      b.put((byte) (sizes[i] == 256 ? 0 : sizes[i])).put((byte) (sizes[i] == 256 ? 0 : sizes[i]));
      b.put((byte) 0).put((byte) 0).putShort((short) 1).putShort((short) 32);
      b.putInt(pngs.get(i).length).putInt(offset);
      offset += pngs.get(i).length;
    }
    for (byte[] p : pngs) b.put(p);
    Files.write(out.resolve("tarkas.ico"), b.array());
    System.out.println("wrote " + out.resolve("tarkas.ico") + " (" + sizes.length + " sizes)");
  }
}
