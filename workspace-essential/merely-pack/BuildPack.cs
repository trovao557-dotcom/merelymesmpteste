using System;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Drawing.Imaging;
using System.IO;
using System.Security.Cryptography;

class BuildPack
{
    static Color C(int r, int g, int b) { return Color.FromArgb(255, r, g, b); }

    static readonly Color FrameDark = C(38, 40, 44);
    static readonly Color FrameMid = C(52, 55, 60);
    static readonly Color FrameHi = C(72, 76, 82);
    static readonly Color FrameEdge = C(22, 23, 26);
    static readonly Color SlotBg = C(12, 13, 15);
    static readonly Color SlotLine = C(58, 61, 66);
    static readonly Color Gold = C(212, 168, 58);
    static readonly Color GoldHi = C(240, 210, 110);
    static readonly Color GoldDark = C(140, 98, 28);
    static readonly Color Plate = C(214, 210, 200);
    static readonly Color PlateDark = C(168, 164, 154);
    static readonly Color Rivet = C(90, 90, 94);

    static void Main()
    {
        var root = Path.GetFullPath(Path.Combine(AppContext.BaseDirectory, ".."));
        if (!File.Exists(Path.Combine(root, "BuildPack.cs")))
            root = @"C:\Users\MerelyMe\Documents\MerelyMeSMP\merely-pack";

        var assets = Path.Combine(root, "src", "assets");
        ResetDir(Path.Combine(assets, "minecraft", "items"));
        ResetDir(Path.Combine(assets, "minecraft", "models", "item"));
        ResetDir(Path.Combine(assets, "merelyme", "textures", "item"));
        ResetDir(Path.Combine(assets, "merelyme", "models", "item"));

        string[] iconFiles = { "member.png", "knight.png", "warrior.png", "macer.png", "prime.png" };
        string[] iconIds = { "rank_member", "rank_knight", "rank_warrior", "rank_macer", "rank_prime" };
        var iconRoot = @"C:\Users\MerelyMe\Documents\MerelyMeSMP";
        for (int i = 0; i < iconFiles.Length; i++)
        {
            var src = Path.Combine(iconRoot, iconFiles[i]);
            Bitmap item = PrepareRankIcon(src);
            Save(item, Path.Combine(assets, "merelyme", "textures", "item", iconIds[i] + ".png"));
            File.WriteAllText(Path.Combine(assets, "merelyme", "models", "item", iconIds[i] + ".json"),
                "{\n  \"parent\": \"minecraft:item/generated\",\n  \"textures\": {\n    \"layer0\": \"merelyme:item/" + iconIds[i] + "\"\n  }\n}\n");
            item.Dispose();
        }

        File.WriteAllText(Path.Combine(assets, "minecraft", "items", "paper.json"), PaperItemsJson());
        File.WriteAllText(Path.Combine(root, "src", "pack.mcmeta"),
            "{\n  \"pack\": {\n    \"pack_format\": 48,\n    \"supported_formats\": { \"min_inclusive\": 34, \"max_inclusive\": 99 },\n    \"description\": \"MerelyMe SMP rank item icons\"\n  }\n}\n");

        var zip = Path.Combine(root, "MerelyMeSMP-Menus.zip");
        if (File.Exists(zip)) File.Delete(zip);
        System.IO.Compression.ZipFile.CreateFromDirectory(Path.Combine(root, "src"), zip, System.IO.Compression.CompressionLevel.Optimal, false);

        SHA1 sha = SHA1.Create();
        FileStream fs = File.OpenRead(zip);
        var hash = sha.ComputeHash(fs);
        fs.Dispose();
        sha.Dispose();
        var hex = BitConverter.ToString(hash).Replace("-", "").ToLowerInvariant();
        File.WriteAllText(Path.Combine(root, "sha1.txt"), hex);
        Console.WriteLine("ZIP " + new FileInfo(zip).Length);
        Console.WriteLine("SHA1 " + hex);
    }

    static void ResetDir(string dir)
    {
        if (Directory.Exists(dir)) Directory.Delete(dir, true);
        Directory.CreateDirectory(dir);
    }

    static Bitmap DrawContainer(int rows)
    {
        int w = 176;
        int h = 114 + rows * 18;
        var bmp = new Bitmap(w, h, PixelFormat.Format32bppArgb);
        Graphics g = Graphics.FromImage(bmp);
        g.SmoothingMode = SmoothingMode.None;
        g.InterpolationMode = InterpolationMode.NearestNeighbor;
        g.PixelOffsetMode = PixelOffsetMode.Half;
        g.Clear(Color.Transparent);

        Fill(bmp, 0, 0, w, h, FrameDark);
        Rect(bmp, 0, 0, w, h, FrameEdge);
        Rect(bmp, 1, 1, w - 2, h - 2, FrameHi);
        Rect(bmp, 2, 2, w - 4, h - 4, FrameMid);

        GoldCorner(bmp, 2, 2, false, false);
        GoldCorner(bmp, w - 16, 2, true, false);
        GoldCorner(bmp, 2, h - 16, false, true);
        GoldCorner(bmp, w - 16, h - 16, true, true);

        int plateW = 92;
        int plateX = (w - plateW) / 2;
        Fill(bmp, plateX, 3, plateW, 12, Plate);
        Rect(bmp, plateX, 3, plateW, 12, PlateDark);
        bmp.SetPixel(plateX + 2, 5, Rivet);
        bmp.SetPixel(plateX + plateW - 3, 5, Rivet);
        bmp.SetPixel(plateX + 2, 12, Rivet);
        bmp.SetPixel(plateX + plateW - 3, 12, Rivet);

        DrawSlotGrid(bmp, 7, 17, 9, rows);

        int invTop = 17 + rows * 18 + 14;
        DrawSlotGrid(bmp, 7, invTop, 9, 3);
        DrawSlotGrid(bmp, 7, invTop + 58, 9, 1);

        g.Dispose();
        return bmp;
    }

    static void DrawSlotGrid(Bitmap bmp, int x, int y, int cols, int rows)
    {
        Fill(bmp, x - 1, y - 1, cols * 18 + 1, rows * 18 + 1, SlotLine);
        for (int r = 0; r < rows; r++)
            for (int c = 0; c < cols; c++)
                Fill(bmp, x + c * 18, y + r * 18, 16, 16, SlotBg);
    }

    static void GoldCorner(Bitmap bmp, int x, int y, bool flipX, bool flipY)
    {
        for (int i = 0; i < 14; i++)
        {
            int t = i < 3 || i > 10 ? 1 : 0;
            Color col = i % 3 == 0 ? GoldHi : (i % 2 == 0 ? Gold : GoldDark);
            int x1 = flipX ? x + 13 - Math.Min(i, 8) : x;
            int y1 = flipY ? y + 13 - Math.Min(i, 8) : y;
            int ww = flipX ? 8 - Math.Max(0, 5 - i) : Math.Min(i + 3, 10);
            int hh = 2;
            if (i < 10)
            {
                int gx = flipX ? x + (13 - Math.Min(i + 2, 13)) : x;
                int gy = flipY ? y + i : y + i;
                if (i < 4) Fill(bmp, gx, gy, 10, 1, col);
            }
        }
        for (int i = 0; i < 11; i++)
        {
            int px = flipX ? x + 13 - Math.Min(i, 10) : x + Math.Min(i, 10);
            int py = flipY ? y + 2 : y + 2;
            bmp.SetPixel(Clamp(px, bmp.Width), Clamp(py, bmp.Height), i % 2 == 0 ? GoldHi : Gold);
            int p2x = flipX ? x + 2 : x + 2;
            int p2y = flipY ? y + 13 - Math.Min(i, 10) : y + Math.Min(i, 10);
            bmp.SetPixel(Clamp(p2x, bmp.Width), Clamp(p2y, bmp.Height), Gold);
        }
        int rx = flipX ? x + 3 : x + 3;
        int ry = flipY ? y + 3 : y + 3;
        bmp.SetPixel(Clamp(rx, bmp.Width), Clamp(ry, bmp.Height), Rivet);
        bmp.SetPixel(Clamp(flipX ? x + 8 : x + 8, bmp.Width), Clamp(flipY ? y + 3 : y + 3, bmp.Height), Rivet);
    }

    static int Clamp(int v, int max) { return Math.Max(0, Math.Min(max - 1, v)); }

    static void Fill(Bitmap bmp, int x, int y, int w, int h, Color c)
    {
        for (int yy = y; yy < y + h; yy++)
            for (int xx = x; xx < x + w; xx++)
                if (xx >= 0 && yy >= 0 && xx < bmp.Width && yy < bmp.Height)
                    bmp.SetPixel(xx, yy, c);
    }

    static void Rect(Bitmap bmp, int x, int y, int w, int h, Color c)
    {
        Fill(bmp, x, y, w, 1, c);
        Fill(bmp, x, y + h - 1, w, 1, c);
        Fill(bmp, x, y, 1, h, c);
        Fill(bmp, x + w - 1, y, 1, h, c);
    }

    static Bitmap Pad256(Bitmap src)
    {
        var pad = new Bitmap(256, 256, PixelFormat.Format32bppArgb);
        Graphics g = Graphics.FromImage(pad);
        g.Clear(Color.Transparent);
        g.DrawImageUnscaled(src, 0, 0);
        g.Dispose();
        return pad;
    }

    static Bitmap PrepareRankIcon(string path)
    {
        Bitmap src = (Bitmap)Image.FromFile(path);
        int minX = src.Width, minY = src.Height, maxX = 0, maxY = 0;
        for (int y = 0; y < src.Height; y++)
            for (int x = 0; x < src.Width; x++)
            {
                var p = src.GetPixel(x, y);
                if (p.R + p.G + p.B > 30)
                {
                    if (x < minX) minX = x;
                    if (y < minY) minY = y;
                    if (x > maxX) maxX = x;
                    if (y > maxY) maxY = y;
                }
            }
        if (maxX <= minX) { minX = 0; minY = 0; maxX = src.Width - 1; maxY = src.Height - 1; }
        int cw = maxX - minX + 1;
        int ch = maxY - minY + 1;
        var cropped = new Bitmap(cw, ch, PixelFormat.Format32bppArgb);
        for (int y = 0; y < ch; y++)
            for (int x = 0; x < cw; x++)
            {
                var p = src.GetPixel(minX + x, minY + y);
                if (p.R + p.G + p.B <= 30) cropped.SetPixel(x, y, Color.Transparent);
                else cropped.SetPixel(x, y, p);
            }
        var dest = new Bitmap(64, 32, PixelFormat.Format32bppArgb);
        using (var g = Graphics.FromImage(dest))
        {
            g.Clear(Color.Transparent);
            g.InterpolationMode = InterpolationMode.NearestNeighbor;
            g.PixelOffsetMode = PixelOffsetMode.Half;
            g.DrawImage(cropped, 0, 0, 64, 32);
        }
        cropped.Dispose();
        src.Dispose();
        return dest;
    }

    static void Save(Bitmap bmp, string path)
    {
        Directory.CreateDirectory(Path.GetDirectoryName(path));
        bmp.Save(path, ImageFormat.Png);
    }

    static string PaperItemsJson()
    {
        return @"{
  ""model"": {
    ""type"": ""range_dispatch"",
    ""property"": ""custom_model_data"",
    ""fallback"": { ""type"": ""model"", ""model"": ""minecraft:item/paper"" },
    ""entries"": [
      { ""threshold"": 1001, ""model"": { ""type"": ""model"", ""model"": ""merelyme:item/rank_member"" } },
      { ""threshold"": 1002, ""model"": { ""type"": ""model"", ""model"": ""merelyme:item/rank_knight"" } },
      { ""threshold"": 1003, ""model"": { ""type"": ""model"", ""model"": ""merelyme:item/rank_warrior"" } },
      { ""threshold"": 1004, ""model"": { ""type"": ""model"", ""model"": ""merelyme:item/rank_macer"" } },
      { ""threshold"": 1005, ""model"": { ""type"": ""model"", ""model"": ""merelyme:item/rank_prime"" } }
    ]
  }
}
";
    }
}
