using System;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Drawing.Imaging;
using System.IO;

class Preview {
  static void Main() {
    string dir = @"C:\Users\MerelyMe\Documents\MerelyMeSMP\RanksImages\compact";
    string[] ids = {"member","knight","warrior","macer","prime","media","partner","helper","mod","owner","admin","clipper"};
    int scale = 10;
    int cols = 3;
    int cellW = 79 * scale + 40;
    int cellH = 32 * scale + 50;
    int rows = (ids.Length + cols - 1) / cols;
    Bitmap sheet = new Bitmap(cols * cellW, rows * cellH, PixelFormat.Format32bppArgb);
    using (Graphics g = Graphics.FromImage(sheet)) {
      g.Clear(Color.FromArgb(40, 40, 44));
      g.InterpolationMode = InterpolationMode.NearestNeighbor;
      g.PixelOffsetMode = PixelOffsetMode.Half;
      g.SmoothingMode = SmoothingMode.None;
      using (Font font = new Font("Consolas", 12, FontStyle.Bold))
      using (Brush tb = new SolidBrush(Color.White)) {
        for (int i = 0; i < ids.Length; i++) {
          int col = i % cols, row = i / cols;
          int ox = col * cellW, oy = row * cellH;
          Bitmap src = (Bitmap)Image.FromFile(Path.Combine(dir, ids[i] + ".png"));
          g.DrawImage(src, ox + 20, oy + 28, src.Width * scale, src.Height * scale);
          g.DrawString(ids[i].ToUpperInvariant(), font, tb, ox + 20, oy + 6);
          src.Dispose();
        }
      }
    }
    string outp = Path.Combine(dir, "preview-grande.png");
    sheet.Save(outp, ImageFormat.Png);
    Console.WriteLine(outp + " " + sheet.Width + "x" + sheet.Height);
    sheet.Dispose();
  }
}
