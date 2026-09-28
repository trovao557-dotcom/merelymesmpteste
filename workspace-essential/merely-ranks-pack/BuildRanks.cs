using System;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Drawing.Imaging;
using System.IO;
using System.IO.Compression;
using System.Security.Cryptography;
using System.Text;

class BuildRanks
{
    const int BADGE_H = 8;
    const int GLYPH_W = 5;
    const int GLYPH_H = 5;
    const int ADVANCE = 5;
    const int PAD_X = 2;
    const int FONT_HEIGHT = 8;
    const int FONT_ASCENT = 7;

    static readonly string RankAssets = @"C:\Users\MerelyMe\.cursor\projects\c-Users-MerelyMe-Documents-MerelyMeSMP\assets";
    static readonly string RankRoot = @"C:\Users\MerelyMe\Documents\MerelyMeSMP";

    static void Main()
    {
        string root = @"C:\Users\MerelyMe\Documents\MerelyMeSMP\merely-ranks-pack";
        string src = Path.Combine(root, "src");
        if (Directory.Exists(src)) Directory.Delete(src, true);

        string fontTex = Path.Combine(src, "assets", "merelyme", "textures", "font");
        string mcFontTex = Path.Combine(src, "assets", "minecraft", "textures", "font");
        Directory.CreateDirectory(fontTex);
        Directory.CreateDirectory(mcFontTex);
        Directory.CreateDirectory(Path.Combine(src, "assets", "minecraft", "font"));
        Directory.CreateDirectory(Path.Combine(src, "assets", "merelyme", "font"));

        string[] ids = { "member", "knight", "warrior", "macer", "prime", "media", "partner", "helper", "mod", "owner", "admin" };
        string[] labels = { "MEMBER", "KNIGHT", "WARRIOR", "MACER", "PRIME", "MEDIA", "PARTNER", "HELPER", "MOD", "OWNER", "ADMIN" };

        StringBuilder bitmaps = new StringBuilder();
        Bitmap[] badges = new Bitmap[ids.Length];
        for (int i = 0; i < ids.Length; i++)
        {
            Bitmap badge = BuildRankBadge(labels[i], ids[i]);
            badges[i] = badge;
            Console.WriteLine(ids[i] + " " + badge.Width + "x" + badge.Height);
            string outPng = Path.Combine(fontTex, ids[i] + ".png");
            badge.Save(outPng, ImageFormat.Png);
            File.Copy(outPng, Path.Combine(mcFontTex, "merely_" + ids[i] + ".png"), true);

            string escaped = "\\u" + (0xE000 + i).ToString("X4");
            bitmaps.Append("    {\n");
            bitmaps.Append("      \"type\": \"bitmap\",\n");
            bitmaps.Append("      \"file\": \"minecraft:font/merely_" + ids[i] + ".png\",\n");
            bitmaps.Append("      \"ascent\": " + FONT_ASCENT + ",\n");
            bitmaps.Append("      \"height\": " + FONT_HEIGHT + ",\n");
            bitmaps.Append("      \"chars\": [ \"" + escaped + "\" ]\n");
            bitmaps.Append("    },\n");
        }

        WriteUi(src, bitmaps, fontTex, mcFontTex, root);

        string defaultJson =
            "{\n  \"providers\": [\n" +
            bitmaps.ToString() +
            "    { \"type\": \"space\", \"advances\": { \"\\uF801\": -1, \"\\uF802\": -2, \"\\uF803\": -4, \"\\uF804\": -8, \"\\uF805\": -16, \"\\uF806\": -32, \"\\uF807\": -64, \"\\uF808\": -128 } },\n" +
            "    { \"type\": \"reference\", \"id\": \"minecraft:include/space\" },\n" +
            "    { \"type\": \"reference\", \"id\": \"minecraft:include/default\", \"filter\": { \"uniform\": false } },\n" +
            "    { \"type\": \"reference\", \"id\": \"minecraft:include/unifont\" }\n" +
            "  ]\n}\n";
        File.WriteAllText(Path.Combine(src, "assets", "minecraft", "font", "default.json"), defaultJson, new UTF8Encoding(false));

        string bmp = bitmaps.ToString().TrimEnd();
        if (bmp.EndsWith(",")) bmp = bmp.Substring(0, bmp.Length - 1);
        File.WriteAllText(Path.Combine(src, "assets", "merelyme", "font", "ranks.json"),
            "{\n  \"providers\": [\n" + bmp + "\n  ]\n}\n", new UTF8Encoding(false));

        File.WriteAllText(Path.Combine(src, "pack.mcmeta"),
            "{\n  \"pack\": {\n    \"description\": \"MerelyMe SMP ranks, menu overlays and icons\",\n    \"pack_format\": 48,\n    \"supported_formats\": { \"min_inclusive\": 15, \"max_inclusive\": 99 },\n    \"min_format\": 15,\n    \"max_format\": 99\n  }\n}\n",
            new UTF8Encoding(false));

        File.WriteAllText(Path.Combine(root, "groups.yml"), BuildGroups(ids), new UTF8Encoding(false));
        WritePreview(root, badges, ids);
        for (int i = 0; i < badges.Length; i++) badges[i].Dispose();
        WriteTitlesSnippet(root);

        string zip = Path.Combine(root, "MerelyMe-Ranks.zip");
        if (File.Exists(zip)) File.Delete(zip);
        CreateZipWithForwardSlashes(src, zip);

        SHA1 sha = SHA1.Create();
        using (FileStream zfs = File.OpenRead(zip))
        {
            string hex = BitConverter.ToString(sha.ComputeHash(zfs)).Replace("-", "").ToLowerInvariant();
            File.WriteAllText(Path.Combine(root, "sha1.txt"), hex);
            Console.WriteLine("ZIP " + new FileInfo(zip).Length);
            Console.WriteLine("SHA1 " + hex);
        }
        sha.Dispose();
    }

    static void CreateZipWithForwardSlashes(string sourceDir, string zipPath)
    {
        using (FileStream fs = new FileStream(zipPath, FileMode.Create))
        using (ZipArchive archive = new ZipArchive(fs, ZipArchiveMode.Create))
        {
            foreach (string file in Directory.GetFiles(sourceDir, "*", SearchOption.AllDirectories))
            {
                string entry = file.Substring(sourceDir.Length + 1).Replace('\\', '/');
                archive.CreateEntryFromFile(file, entry, CompressionLevel.Optimal);
            }
        }
    }

    static string BuildGroups(string[] ids)
    {
        StringBuilder sb = new StringBuilder();
        sb.Append("_DEFAULT_:\n");
        sb.Append("  tabprefix: \"&#AEADAD&lPLAYER &7\"\n");
        sb.Append("  tagprefix: \"&#AEADAD&lPLAYER &7\"\n");
        sb.Append("  tabsuffix: \"\"\n");
        sb.Append("  tagsuffix: \"%sksmp_tag%\"\n");
        for (int i = 0; i < ids.Length; i++)
        {
            char ch = (char)(0xE000 + i);
            string line = "&f" + ch + "\uF802";
            sb.Append(ids[i] + ":\n");
            sb.Append("  tabprefix: \"" + line + "\"\n");
            sb.Append("  tagprefix: \"" + line + "\"\n");
            sb.Append("  tabsuffix: \"\"\n");
            sb.Append("  tagsuffix: \"%sksmp_tag%\"\n");
        }
        return sb.ToString();
    }

    static int TextWidth(string text)
    {
        return text.Length * ADVANCE - 1;
    }

    static string FindRankImage(string id)
    {
        string[] paths = {
            Path.Combine(RankAssets, "rank-" + id + ".png"),
            Path.Combine(RankRoot, id + ".png"),
            Path.Combine(RankRoot, "rank-" + id + ".png")
        };
        foreach (string p in paths)
            if (File.Exists(p)) return p;
        return null;
    }

    static Bitmap BuildRankBadge(string text, string id)
    {
        string custom = FindRankImage(id);
        if (custom != null)
        {
            Bitmap scaled = ScaleRankPhoto(custom, BADGE_H);
            if (scaled != null) return scaled;
        }
        return DrawBadge(text, id);
    }

    static Bitmap ScaleRankPhoto(string path, int targetH)
    {
        try
        {
            using (Bitmap src = (Bitmap)Image.FromFile(path))
            {
                Bitmap cropped = CropOpaque(src);
                int innerH = Math.Max(8, (int)Math.Round(targetH * 0.82));
                int targetW = Math.Max(8, (int)Math.Round(cropped.Width * (innerH / (double)cropped.Height)));
                Bitmap scaled = new Bitmap(targetW, innerH, PixelFormat.Format32bppArgb);
                using (Graphics g = Graphics.FromImage(scaled))
                {
                    g.Clear(Color.Transparent);
                    g.InterpolationMode = InterpolationMode.NearestNeighbor;
                    g.PixelOffsetMode = PixelOffsetMode.Half;
                    g.SmoothingMode = SmoothingMode.None;
                    g.DrawImage(cropped, 0, 0, targetW, innerH);
                }
                cropped.Dispose();

                Bitmap canvas = new Bitmap(targetW + 2, targetH, PixelFormat.Format32bppArgb);
                using (Graphics g = Graphics.FromImage(canvas))
                {
                    g.Clear(Color.Transparent);
                    int y = (targetH - innerH) / 2;
                    g.DrawImage(scaled, 0, y, targetW, innerH);
                }
                scaled.Dispose();
                return canvas;
            }
        }
        catch { return null; }
    }

    static Bitmap CropOpaque(Bitmap src)
    {
        int minX = src.Width, minY = src.Height, maxX = 0, maxY = 0;
        for (int y = 0; y < src.Height; y++)
            for (int x = 0; x < src.Width; x++)
            {
                Color p = src.GetPixel(x, y);
                bool bg = p.A < 16 || (p.R < 18 && p.G < 18 && p.B < 18);
                if (!bg)
                {
                    if (x < minX) minX = x;
                    if (y < minY) minY = y;
                    if (x > maxX) maxX = x;
                    if (y > maxY) maxY = y;
                }
            }
        if (maxX < minX) return (Bitmap)src.Clone();
        int w = maxX - minX + 1, h = maxY - minY + 1;
        Bitmap d = new Bitmap(w, h, PixelFormat.Format32bppArgb);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
            {
                Color p = src.GetPixel(minX + x, minY + y);
                bool bg = p.A < 16 || (p.R < 18 && p.G < 18 && p.B < 18);
                d.SetPixel(x, y, bg ? Color.Transparent : p);
            }
        return d;
    }

    static Bitmap DrawBadge(string text, string id)
    {
        int textW = TextWidth(text);
        int innerW = textW + PAD_X * 2;
        int innerH = BADGE_H;
        Bitmap b = new Bitmap(innerW, BADGE_H, PixelFormat.Format32bppArgb);
        Color top, bot, borderCol;
        Palette(id, out top, out bot, out borderCol);

        int x0 = 0, y0 = 0, x1 = innerW - 1, y1 = BADGE_H - 1;

        for (int y = y0; y <= y1; y++)
        {
            int ly = y - y0;
            for (int x = x0; x <= x1; x++)
            {
                int lx = x - x0;
                bool chamfer = (lx == 0 && (ly == 0 || ly == BADGE_H - 1)) || (lx == innerW - 1 && (ly == 0 || ly == BADGE_H - 1));
                if (chamfer) continue;
                if (lx == 0 || ly == 0 || lx == innerW - 1 || ly == BADGE_H - 1)
                    b.SetPixel(x, y, borderCol);
                else if (id == "partner")
                    b.SetPixel(x, y, Rainbow(lx - 1, innerW - 2, ly < BADGE_H / 2));
                else
                    b.SetPixel(x, y, ly < BADGE_H / 2 ? top : bot);
            }
        }

        int tx = x0 + (innerW - textW) / 2;
        int ty = y0 + (BADGE_H - GLYPH_H) / 2;
        DrawText(b, text, tx, ty, Color.White);
        return b;
    }

    static void Palette(string id, out Color top, out Color bot, out Color border)
    {
        switch (id)
        {
            case "member": top = C(158, 158, 158); bot = C(97, 97, 97); border = C(45, 45, 45); break;
            case "knight": top = C(76, 175, 80); bot = C(46, 125, 50); border = C(20, 70, 24); break;
            case "warrior": top = C(160, 40, 40); bot = C(100, 18, 18); border = C(50, 8, 8); break;
            case "macer": top = C(33, 150, 243); bot = C(21, 101, 192); border = C(8, 50, 110); break;
            case "prime": top = C(255, 152, 0); bot = C(230, 81, 0); border = C(120, 40, 0); break;
            case "media": top = C(156, 39, 176); bot = C(106, 27, 154); border = C(55, 10, 80); break;
            case "helper": top = C(255, 193, 7); bot = C(255, 143, 0); border = C(140, 80, 0); break;
            case "mod": top = C(40, 70, 160); bot = C(22, 40, 110); border = C(10, 18, 55); break;
            case "owner": top = C(229, 57, 53); bot = C(183, 28, 28); border = C(90, 12, 12); break;
            case "admin": top = C(92, 107, 192); bot = C(57, 73, 171); border = C(25, 35, 100); break;
            default: top = C(80, 80, 80); bot = C(50, 50, 50); border = C(20, 20, 20); break;
        }
    }

    static Color Rainbow(int x, int span, bool light)
    {
        Color[] cols = light
            ? new[] { C(230, 50, 50), C(240, 140, 30), C(240, 210, 40), C(50, 190, 70), C(50, 120, 230), C(160, 70, 210) }
            : new[] { C(160, 25, 25), C(180, 90, 10), C(180, 150, 20), C(25, 130, 45), C(25, 75, 170), C(110, 40, 150) };
        int i = span <= 1 ? 0 : Math.Min(cols.Length - 1, x * cols.Length / span);
        return cols[i];
    }

    static Color C(int r, int g, int b) { return Color.FromArgb(255, r, g, b); }

    static void Plot(Bitmap b, int px, int py, Color col)
    {
        if (px >= 0 && py >= 0 && px < b.Width && py < b.Height)
            b.SetPixel(px, py, col);
    }

    static void Fill(Bitmap b, int x, int y, int w, int h, Color col)
    {
        for (int yy = 0; yy < h; yy++)
            for (int xx = 0; xx < w; xx++)
                Plot(b, x + xx, y + yy, col);
    }

    static void DrawText(Bitmap b, string text, int x, int y, Color col)
    {
        int cx = x;
        foreach (char ch in text)
        {
            string[] rows = Glyph(ch);
            for (int gy = 0; gy < GLYPH_H; gy++)
                for (int gx = 0; gx < GLYPH_W; gx++)
                    if (rows[gy][gx] == '#')
                        Plot(b, cx + gx, y + gy, col);
            cx += ADVANCE;
        }
    }

    static string[] Glyph(char c)
    {
        switch (c)
        {
            case 'A': return new[] { ".###.", "#...#", "#####", "#...#", "#...#" };
            case 'B': return new[] { "####.", "#...#", "####.", "#...#", "####." };
            case 'C': return new[] { ".###.", "#....", "#....", "#....", ".###." };
            case 'D': return new[] { "####.", "#...#", "#...#", "#...#", "####." };
            case 'E': return new[] { "#####", "#....", "####.", "#....", "#####" };
            case 'F': return new[] { "#####", "#....", "####.", "#....", "#...." };
            case 'G': return new[] { ".###.", "#....", "#.##.", "#...#", ".###." };
            case 'H': return new[] { "#...#", "#...#", "#####", "#...#", "#...#" };
            case 'I': return new[] { ".###.", "..#..", "..#..", "..#..", ".###." };
            case 'K': return new[] { "#...#", "#..#.", "###..", "#..#.", "#...#" };
            case 'L': return new[] { "#....", "#....", "#....", "#....", "#####" };
            case 'M': return new[] { "#...#", "##.##", "#.#.#", "#...#", "#...#" };
            case 'N': return new[] { "#...#", "##..#", "#.#.#", "#..##", "#...#" };
            case 'O': return new[] { ".###.", "#...#", "#...#", "#...#", ".###." };
            case 'P': return new[] { "####.", "#...#", "####.", "#....", "#...." };
            case 'R': return new[] { "####.", "#...#", "####.", "#.#..", "#...#" };
            case 'S': return new[] { ".####", "#....", ".###.", "....#", "####." };
            case 'T': return new[] { "#####", "..#..", "..#..", "..#..", "..#.." };
            case 'U': return new[] { "#...#", "#...#", "#...#", "#...#", ".###." };
            case 'W': return new[] { "#...#", "#...#", "#.#.#", "#.#.#", "#...#" };
            case 'Y': return new[] { "#...#", ".#.#.", "..#..", "..#..", "..#.." };
            default: return new[] { ".....", ".....", ".....", ".....", "....." };
        }
    }

    static void WritePreview(string root, Bitmap[] badges, string[] ids)
    {
        int scale = 10;
        int pad = 16;
        int maxW = 0;
        for (int i = 0; i < badges.Length; i++)
            if (badges[i].Width > maxW) maxW = badges[i].Width;
        int maxH = 0;
        for (int i = 0; i < badges.Length; i++)
            if (badges[i].Height > maxH) maxH = badges[i].Height;
        int cellW = maxW * scale + pad * 2;
        int cellH = maxH * scale + pad * 2;
        int cols = 3;
        int rows = (badges.Length + cols - 1) / cols;
        Bitmap sheet = new Bitmap(cols * cellW, rows * cellH, PixelFormat.Format32bppArgb);
        using (Graphics g = Graphics.FromImage(sheet))
        {
            g.Clear(Color.FromArgb(24, 24, 28));
            g.InterpolationMode = System.Drawing.Drawing2D.InterpolationMode.NearestNeighbor;
            g.PixelOffsetMode = System.Drawing.Drawing2D.PixelOffsetMode.Half;
            g.SmoothingMode = System.Drawing.Drawing2D.SmoothingMode.None;
            for (int i = 0; i < badges.Length; i++)
            {
                int col = i % cols;
                int row = i / cols;
                int ox = col * cellW + (cellW - badges[i].Width * scale) / 2;
                int oy = row * cellH + (cellH - badges[i].Height * scale) / 2;
                g.DrawImage(badges[i], ox, oy, badges[i].Width * scale, badges[i].Height * scale);
            }
        }
        sheet.Save(Path.Combine(root, "ranks-preview.png"), ImageFormat.Png);
        sheet.Dispose();
    }

    static readonly string[] IconIds = {
        "lock","unlock","check","close","ring","pencil","home","search",
        "gear","filter","trash","gem","toggle_off","toggle_on","tri_left","tri_right",
        "tri_up","tri_down","left","right","up","down","back","refresh",
        "minus","plus","info","help","warn","more"
    };

    static readonly string[] GuiIds = {
        "shop","auction","orders","help","sell","ranks","kits","settings",
        "bounty","stats","rules","rtp","crates","confirm","lockin","team","homes","points"
    };

    static readonly string[] GuiTitles = {
        "SHOP","AUCTION","ORDERS","HELP","SELL","RANKS","KITS","SETTINGS",
        "BOUNTY","STATS","RULES","RTP","CRATES","CONFIRM","LOCKED","TEAM","HOMES","POINTS"
    };

    static readonly int[] GuiRows = { 3,6,6,6,6,6,3,5, 6,3,3,3,4,3,3,6,4,3 };

    static void WriteUi(string src, StringBuilder bitmaps, string fontTex, string mcFontTex, string root)
    {
        Directory.CreateDirectory(Path.Combine(src, "assets", "merelyme", "textures", "gui"));

        Bitmap[] icons = new Bitmap[IconIds.Length];
        for (int i = 0; i < IconIds.Length; i++)
        {
            Bitmap icon = DrawIcon(IconIds[i]);
            icons[i] = icon;
            string name = "icon_" + IconIds[i] + ".png";
            icon.Save(Path.Combine(fontTex, name), ImageFormat.Png);
            File.Copy(Path.Combine(fontTex, name), Path.Combine(mcFontTex, name), true);
            string escaped = "\\u" + (0xE020 + i).ToString("X4");
            bitmaps.Append("    {\n");
            bitmaps.Append("      \"type\": \"bitmap\",\n");
            bitmaps.Append("      \"file\": \"minecraft:font/icon_" + IconIds[i] + ".png\",\n");
            bitmaps.Append("      \"ascent\": 8,\n");
            bitmaps.Append("      \"height\": 9,\n");
            bitmaps.Append("      \"chars\": [ \"" + escaped + "\" ]\n");
            bitmaps.Append("    },\n");
        }
        WriteIconPreview(root, icons);
        for (int i = 0; i < icons.Length; i++) icons[i].Dispose();

        for (int i = 0; i < GuiIds.Length; i++)
        {
            Bitmap gui = DrawMenu(GuiTitles[i], GuiRows[i], GuiIds[i]);
            string name = "gui_" + GuiIds[i] + ".png";
            gui.Save(Path.Combine(fontTex, name), ImageFormat.Png);
            File.Copy(Path.Combine(fontTex, name), Path.Combine(mcFontTex, name), true);
            if (GuiIds[i] == "shop" || GuiIds[i] == "lockin" || GuiIds[i] == "auction")
                gui.Save(Path.Combine(root, "preview_" + GuiIds[i] + ".png"), ImageFormat.Png);
            gui.Dispose();
            string escaped = "\\u" + (0xE050 + i).ToString("X4");
            bitmaps.Append("    {\n");
            bitmaps.Append("      \"type\": \"bitmap\",\n");
            bitmaps.Append("      \"file\": \"minecraft:font/gui_" + GuiIds[i] + ".png\",\n");
            bitmaps.Append("      \"ascent\": 13,\n");
            bitmaps.Append("      \"height\": 256,\n");
            bitmaps.Append("      \"chars\": [ \"" + escaped + "\" ]\n");
            bitmaps.Append("    },\n");
            Console.WriteLine("gui " + GuiIds[i] + " rows=" + GuiRows[i]);
        }
    }

    static void WriteIconPreview(string root, Bitmap[] icons)
    {
        int scale = 6;
        int cell = 16 * scale + 12;
        int cols = 6;
        int rows = (icons.Length + cols - 1) / cols;
        Bitmap sheet = new Bitmap(cols * cell, rows * cell, PixelFormat.Format32bppArgb);
        using (Graphics g = Graphics.FromImage(sheet))
        {
            g.Clear(Color.FromArgb(36, 48, 62));
            g.InterpolationMode = System.Drawing.Drawing2D.InterpolationMode.NearestNeighbor;
            g.PixelOffsetMode = System.Drawing.Drawing2D.PixelOffsetMode.Half;
            g.SmoothingMode = System.Drawing.Drawing2D.SmoothingMode.None;
            for (int i = 0; i < icons.Length; i++)
            {
                int col = i % cols, row = i / cols;
                g.DrawImage(icons[i], col * cell + 6, row * cell + 6, 16 * scale, 16 * scale);
            }
        }
        sheet.Save(Path.Combine(root, "icons-preview.png"), ImageFormat.Png);
        sheet.Dispose();
    }

    static void WriteTitlesSnippet(string root)
    {
        StringBuilder sb = new StringBuilder();
        char shift = (char)0xF804;
        for (int i = 0; i < GuiIds.Length; i++)
        {
            char g = (char)(0xE050 + i);
            sb.Append(GuiIds[i] + "\t" + "&f" + shift + g + "\n");
        }
        File.WriteAllText(Path.Combine(root, "menu-title-prefix.txt"), sb.ToString(), new UTF8Encoding(false));
    }

    static Color WoodTop = C(126, 78, 42);
    static Color WoodBase = C(72, 44, 22);
    static Color WoodDeep = C(48, 28, 14);
    static Color TitleBg = C(48, 42, 58);
    static Color TitleBorder = C(205, 205, 215);
    static Color Slot = C(42, 38, 52);
    static Color SlotHi = C(58, 52, 68);
    static Color SlotLine = C(28, 24, 34);
    static Color Pillar = C(235, 235, 240);
    static Color PillarGold = C(255, 210, 55);
    static Color PillarCap = C(176, 126, 48);
    static Color Gold = C(240, 188, 48);
    static Color GoldDark = C(168, 112, 16);
    static Color Cream = C(236, 220, 180);

    static Bitmap DrawMenu(string title, int rows, string id)
    {
        Bitmap b = new Bitmap(256, 256, PixelFormat.Format32bppArgb);
        int w = 176;
        int h = 17 + rows * 18 + 8;

        if (rows == 3)
        {
            string tplPath = @"C:\Users\MerelyMe\Documents\MerelyMeSMP\merely-ranks-pack\template-menu.png";
            if (File.Exists(tplPath))
            {
                using (Bitmap src = (Bitmap)Image.FromFile(tplPath))
                using (Graphics g = Graphics.FromImage(b))
                {
                    g.Clear(Color.Transparent);
                    g.InterpolationMode = InterpolationMode.NearestNeighbor;
                    g.PixelOffsetMode = PixelOffsetMode.Half;
                    g.SmoothingMode = SmoothingMode.None;
                    g.DrawImage(src, 0, 0, w, h);
                }
                DrawMenuTitle(b, title, w);
                if (id == "lockin")
                {
                    int lockSlotY = 18;
                    int py = lockSlotY + Math.Max(0, rows * 18 / 2 - 14);
                    DrawPadlock(b, w / 2 - 10, py);
                    DrawSmallText(b, "SELECT", w / 2 - 18, py + 22, Cream);
                }
                return b;
            }
        }

        FillRect(b, 0, 0, w, h, WoodBase);
        FillRect(b, 0, 0, w, 3, WoodTop);
        FillRect(b, 0, h - 4, w, 4, WoodDeep);

        DrawPillar(b, 0, 2, h - 6);
        DrawPillar(b, w - 7, 2, h - 6);

        int hx = 8, hy = 4, hw = w - 16, hh = 12;
        FillRect(b, hx, hy, hw, hh, TitleBg);
        FillRect(b, hx, hy, hw, 1, TitleBorder);
        FillRect(b, hx, hy + hh - 1, hw, 1, C(28, 24, 36));
        FillRect(b, hx, hy, 1, hh, TitleBorder);
        FillRect(b, hx + hw - 1, hy, 1, hh, C(28, 24, 36));
        FillRect(b, hx + 1, hy + 1, 3, 2, Gold);
        FillRect(b, hx + hw - 4, hy + 1, 3, 2, Gold);

        DrawMenuTitle(b, title, w);

        int slotY = 18;
        for (int r = 0; r < rows; r++)
            for (int c = 0; c < 9; c++)
                DrawWell(b, 7 + c * 18, slotY + r * 18);

        if (id == "lockin")
        {
            int py = slotY + Math.Max(0, rows * 18 / 2 - 14);
            DrawPadlock(b, w / 2 - 10, py);
            DrawSmallText(b, "SELECT", w / 2 - 18, py + 22, Cream);
        }

        return b;
    }

    static void DrawMenuTitle(Bitmap b, string title, int w)
    {
        int textW = title.Length * ADVANCE - 1;
        int maxW = w - 22;
        if (textW > maxW)
        {
            int chars = Math.Max(3, maxW / ADVANCE);
            title = title.Substring(0, Math.Min(title.Length, chars));
            textW = title.Length * ADVANCE - 1;
        }
        int tx = (w - textW) / 2;
        int ty = 5;
        DrawSmallText(b, title, tx, ty, Color.White);
    }

    static void DrawPillar(Bitmap b, int x, int y, int h)
    {
        FillRect(b, x, y, 7, h, PillarCap);
        FillRect(b, x + 1, y + 1, 5, h - 2, Pillar);
        FillRect(b, x + 3, y + 2, 1, h - 4, PillarGold);
        FillRect(b, x + 1, y, 5, 2, Gold);
        FillRect(b, x + 1, y + h - 2, 5, 2, GoldDark);
    }

    static void DrawSmallText(Bitmap b, string text, int x, int y, Color col)
    {
        int cx = x;
        foreach (char ch in text)
        {
            string[] rows = Glyph(ch);
            for (int gy = 0; gy < GLYPH_H; gy++)
                for (int gx = 0; gx < GLYPH_W; gx++)
                    if (rows[gy][gx] == '#')
                        Plot(b, cx + gx, y + gy, col);
            cx += ADVANCE;
        }
    }

    static void DrawWell(Bitmap b, int x, int y)
    {
        FillRect(b, x, y, 18, 18, SlotLine);
        FillRect(b, x + 1, y + 1, 16, 16, Slot);
        FillRect(b, x + 2, y + 2, 14, 14, C(36, 32, 46));
        FillRect(b, x + 2, y + 2, 14, 1, SlotHi);
        FillRect(b, x + 2, y + 2, 1, 14, SlotHi);
        FillRect(b, x + 14, y + 14, 2, 2, C(24, 20, 30));
    }

    static void DrawPadlock(Bitmap b, int x, int y)
    {
        FillRect(b, x + 5, y, 10, 8, C(90, 100, 112));
        FillRect(b, x + 7, y + 2, 6, 6, Color.Transparent);
        for (int i = 7; i <= 12; i++)
            for (int j = 2; j <= 6; j++)
                Plot(b, x + i, y + j, Color.FromArgb(0, 0, 0, 0));
        FillRect(b, x + 7, y + 2, 6, 1, C(90, 100, 112));
        FillRect(b, x + 2, y + 8, 16, 14, Gold);
        FillRect(b, x + 3, y + 9, 14, 4, C(255, 220, 90));
        FillRect(b, x + 2, y + 20, 16, 2, GoldDark);
        FillRect(b, x + 8, y + 12, 4, 6, GoldDark);
        FillRect(b, x + 8, y + 18, 4, 3, C(40, 24, 8));
    }

    static void FillRect(Bitmap b, int x, int y, int w, int h, Color c)
    {
        for (int yy = y; yy < y + h; yy++)
            for (int xx = x; xx < x + w; xx++)
                Plot(b, xx, yy, c);
    }

    static Bitmap DrawIcon(string id)
    {
        Bitmap b = new Bitmap(16, 16, PixelFormat.Format32bppArgb);
        Color ink = C(28, 22, 18);
        Color yel = Gold;
        Color yelD = GoldDark;
        Color grey = C(120, 128, 140);
        Color greyD = C(70, 76, 88);
        switch (id)
        {
            case "lock":
                FillRect(b, 5, 2, 6, 5, grey); FillRect(b, 6, 3, 4, 3, Color.FromArgb(0,0,0,0));
                for (int i = 6; i <= 9; i++) for (int j = 3; j <= 5; j++) Plot(b, i, j, Color.FromArgb(0,0,0,0));
                FillRect(b, 3, 6, 10, 8, yel); FillRect(b, 4, 7, 8, 2, C(255,220,90));
                FillRect(b, 7, 9, 2, 3, yelD); break;
            case "unlock":
                FillRect(b, 8, 1, 5, 5, grey); FillRect(b, 9, 2, 3, 3, Color.FromArgb(0,0,0,0));
                FillRect(b, 3, 6, 10, 8, yel); FillRect(b, 7, 9, 2, 3, yelD); break;
            case "check":
                FillRect(b, 3, 8, 3, 3, C(40,180,70)); FillRect(b, 5, 9, 3, 3, C(40,180,70));
                FillRect(b, 7, 7, 3, 3, C(40,180,70)); FillRect(b, 9, 5, 3, 3, C(40,180,70));
                FillRect(b, 11, 3, 2, 3, C(90,220,110)); break;
            case "close":
                for (int i = 3; i <= 12; i++) { Plot(b, i, i, C(220,40,50)); Plot(b, i, i+1, C(220,40,50)); Plot(b, i, 15-i, C(220,40,50)); Plot(b, i, 14-i, C(180,20,30)); }
                break;
            case "ring":
                FillRect(b, 4, 3, 8, 2, yel); FillRect(b, 4, 11, 8, 2, yel); FillRect(b, 3, 5, 2, 6, yel); FillRect(b, 11, 5, 2, 6, yel); break;
            case "pencil":
                FillRect(b, 4, 10, 3, 3, C(220,80,100)); FillRect(b, 6, 6, 4, 6, yel); FillRect(b, 9, 3, 3, 4, C(80,80,90)); break;
            case "home":
                FillRect(b, 3, 7, 10, 7, Cream); FillRect(b, 2, 6, 12, 2, C(200,50,50)); FillRect(b, 7, 2, 2, 4, C(200,50,50)); FillRect(b, 7, 10, 3, 4, WoodBase); break;
            case "search":
                FillRect(b, 3, 3, 8, 8, grey); FillRect(b, 5, 5, 4, 4, C(80,140,200)); FillRect(b, 10, 10, 4, 3, WoodTop); break;
            case "gear":
                FillRect(b, 6, 2, 4, 12, grey); FillRect(b, 2, 6, 12, 4, grey); FillRect(b, 4, 4, 8, 8, greyD); FillRect(b, 6, 6, 4, 4, grey); break;
            case "filter":
                FillRect(b, 3, 3, 10, 3, grey); FillRect(b, 5, 6, 6, 3, grey); FillRect(b, 7, 9, 2, 4, greyD); break;
            case "trash":
                FillRect(b, 5, 2, 6, 2, grey); FillRect(b, 4, 4, 8, 10, greyD); FillRect(b, 6, 6, 1, 6, grey); FillRect(b, 9, 6, 1, 6, grey); break;
            case "gem":
                FillRect(b, 6, 2, 4, 3, C(210,220,230)); FillRect(b, 4, 5, 8, 6, C(180,190,210)); FillRect(b, 6, 11, 4, 3, grey); break;
            case "toggle_off":
                FillRect(b, 2, 6, 12, 5, greyD); FillRect(b, 3, 7, 5, 3, grey); break;
            case "toggle_on":
                FillRect(b, 2, 6, 12, 5, C(40,160,80)); FillRect(b, 8, 7, 5, 3, Color.White); break;
            case "tri_left":
                for (int i = 0; i < 5; i++) FillRect(b, 8 - i, 5 + i, 1, 6 - i * 2, grey); break;
            case "tri_right":
                for (int i = 0; i < 5; i++) FillRect(b, 7 + i, 5 + i, 1, 6 - i * 2, grey); break;
            case "tri_up":
                for (int i = 0; i < 5; i++) FillRect(b, 5 + i, 8 - i, 6 - i * 2, 1, grey); break;
            case "tri_down":
                for (int i = 0; i < 5; i++) FillRect(b, 5 + i, 6 + i, 6 - i * 2, 1, grey); break;
            case "left":
                FillRect(b, 3, 7, 10, 3, yel); for (int i = 0; i < 5; i++) FillRect(b, 3 + i, 5 + i, 1, 7 - i * 2, yel); break;
            case "right":
                FillRect(b, 3, 7, 10, 3, yel); for (int i = 0; i < 5; i++) FillRect(b, 12 - i, 5 + i, 1, 7 - i * 2, yel); break;
            case "up":
                FillRect(b, 7, 3, 3, 10, yel); for (int i = 0; i < 5; i++) FillRect(b, 5 + i, 3 + i, 7 - i * 2, 1, yel); break;
            case "down":
                FillRect(b, 7, 3, 3, 10, yel); for (int i = 0; i < 5; i++) FillRect(b, 5 + i, 12 - i, 7 - i * 2, 1, yel); break;
            case "back":
                FillRect(b, 3, 7, 10, 3, yel); FillRect(b, 3, 4, 3, 6, yel); for (int i = 0; i < 4; i++) FillRect(b, 3 + i, 5 + i, 1, 5 - i, yel); break;
            case "refresh":
                FillRect(b, 4, 3, 8, 2, yel); FillRect(b, 10, 3, 2, 6, yel); FillRect(b, 4, 11, 8, 2, yel); FillRect(b, 4, 7, 2, 6, yel); break;
            case "minus":
                FillRect(b, 3, 7, 10, 3, yel); break;
            case "plus":
                FillRect(b, 3, 7, 10, 3, yel); FillRect(b, 7, 3, 3, 10, yel); break;
            case "info":
                FillRect(b, 7, 2, 3, 3, yel); FillRect(b, 7, 6, 3, 8, yel); break;
            case "help":
                FillRect(b, 5, 2, 6, 2, yel); FillRect(b, 10, 4, 2, 3, yel); FillRect(b, 7, 7, 3, 3, yel); FillRect(b, 7, 12, 3, 3, yel); break;
            case "warn":
                FillRect(b, 7, 2, 3, 9, yel); FillRect(b, 7, 12, 3, 3, yel); break;
            case "more":
                FillRect(b, 3, 7, 2, 2, yel); FillRect(b, 7, 7, 2, 2, yel); FillRect(b, 11, 7, 2, 2, yel); break;
            default:
                FillRect(b, 4, 4, 8, 8, yel); break;
        }
        for (int y = 0; y < 16; y++)
            for (int x = 0; x < 16; x++)
            {
                Color p = b.GetPixel(x, y);
                if (p.A < 10) continue;
                bool edge = x == 0 || y == 0 || x == 15 || y == 15
                    || b.GetPixel(Math.Max(0, x - 1), y).A < 10
                    || b.GetPixel(Math.Min(15, x + 1), y).A < 10
                    || b.GetPixel(x, Math.Max(0, y - 1)).A < 10
                    || b.GetPixel(x, Math.Min(15, y + 1)).A < 10;
                if (edge) b.SetPixel(x, y, ink);
            }
        return b;
    }
}
