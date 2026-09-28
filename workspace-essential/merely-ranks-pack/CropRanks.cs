using System;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Drawing.Imaging;
using System.IO;

class CropRanks2 {
  static void Main() {
    string assets = @"C:\Users\MerelyMe\.cursor\projects\c-Users-MerelyMe-Documents-MerelyMeSMP\assets";
    string dest = @"C:\Users\MerelyMe\Documents\MerelyMeSMP\RanksImages\compact";
    Directory.CreateDirectory(dest);
    string[] ids = {"member","knight","warrior","macer","prime","media","partner","helper","mod","owner","admin","clipper"};
    string modSrc = @"C:\Users\MerelyMe\.cursor\projects\c-Users-MerelyMe-Documents-MerelyMeSMP\assets\c__Users_MerelyMe_AppData_Roaming_Cursor_User_workspaceStorage_a493e04fc29d31eaf4ef27c9dfe1cf4a_images_Design_sem_nome-removebg-preview-875e6284-7b5a-4bb8-87bf-5037ef57d9be.png";
    int targetH = 32;
    Bitmap[] scaled = new Bitmap[ids.Length];
    int maxW = 0;
    for (int i = 0; i < ids.Length; i++) {
      string path = ids[i] == "mod" ? modSrc : Path.Combine(assets, "rank-" + ids[i] + ".png");
      Bitmap src = (Bitmap)Image.FromFile(path);
      Bitmap c = CropBlack(src);
      src.Dispose();
      int w = Math.Max(8, (int)Math.Round(c.Width * (targetH / (double)c.Height)));
      Bitmap s = new Bitmap(w, targetH, PixelFormat.Format32bppArgb);
      using (Graphics g = Graphics.FromImage(s)) {
        g.Clear(Color.Transparent);
        g.InterpolationMode = InterpolationMode.NearestNeighbor;
        g.PixelOffsetMode = PixelOffsetMode.Half;
        g.DrawImage(c, 0, 0, w, targetH);
      }
      c.Dispose();
      scaled[i] = s;
      if (w > maxW) maxW = w;
      Console.WriteLine(ids[i] + " " + w + "x" + targetH);
    }
    for (int i = 0; i < ids.Length; i++) {
      Bitmap canvas = new Bitmap(maxW, targetH, PixelFormat.Format32bppArgb);
      using (Graphics g = Graphics.FromImage(canvas)) {
        g.Clear(Color.Transparent);
        int x = (maxW - scaled[i].Width) / 2;
        g.DrawImage(scaled[i], x, 0, scaled[i].Width, scaled[i].Height);
      }
      canvas.Save(Path.Combine(dest, ids[i] + ".png"), ImageFormat.Png);
      canvas.Dispose();
      scaled[i].Dispose();
    }
    Console.WriteLine("canvas " + maxW + "x" + targetH);
  }
  static Bitmap CropBlack(Bitmap src) {
    int minX = src.Width, minY = src.Height, maxX = 0, maxY = 0;
    for (int y = 0; y < src.Height; y++)
      for (int x = 0; x < src.Width; x++) {
        Color p = src.GetPixel(x, y);
        bool bg = p.A < 16 || (p.R < 18 && p.G < 18 && p.B < 18);
        if (!bg) {
          if (x < minX) minX = x; if (y < minY) minY = y;
          if (x > maxX) maxX = x; if (y > maxY) maxY = y;
        }
      }
    int w = maxX - minX + 1, h = maxY - minY + 1;
    Bitmap d = new Bitmap(w, h, PixelFormat.Format32bppArgb);
    for (int y = 0; y < h; y++)
      for (int x = 0; x < w; x++) {
        Color p = src.GetPixel(minX + x, minY + y);
        bool bg = p.A < 16 || (p.R < 18 && p.G < 18 && p.B < 18);
        d.SetPixel(x, y, bg ? Color.Transparent : p);
      }
    return d;
  }
}
