from pathlib import Path
import base64
import io
import json
import shutil
import zipfile

from PIL import Image, ImageDraw, ImageFont
import numpy as np


ROOT = Path(r"C:\Users\MerelyMe\Documents\MerelyMeSMP\rank-menu-test")
BASE = ROOT / "base-pack"
OUT = ROOT / "MerelyMeSMP-RankMenu-Test-v1"
USER_GUI = Path(
    r"C:\Users\MerelyMe\AppData\Local\Temp\codex-clipboard-ecf09164-9257-44c9-846e-bc378e475cbe.png"
)
GENERATED_TOP_GUI = ROOT / "assets-source/blue_menu_top_only_generated.png"
WINGS_ZIP = Path(r"C:\Users\MerelyMe\Downloads\Wing Cosmetics - Artillex-Studios.zip")
HATS_ZIP = Path(r"C:\Users\MerelyMe\Downloads\Hats Pack - Artillex-Studios.zip")
CRATES_ZIP = Path(r"C:\Users\MerelyMe\Downloads\crate_pack_2.zip")
MODEL_ZIP = Path(r"C:\Users\MerelyMe\Downloads\model.zip")
ICE_ZIP = Path(r"C:\Users\MerelyMe\Downloads\Ice_Set_v1.0.1.zip")
TOOLTIPS_ZIP = Path(r"C:\Users\MerelyMe\Downloads\Simply Tooltips.zip")
KEYS_ZIP = Path(r"C:\Users\MerelyMe\Downloads\key1.zip")
BUBBLE_ICONS_ZIP = Path(r"C:\Users\MerelyMe\Downloads\Bubble Icons by MrAstro_.zip")
SERVER_LOGO_IMAGE = Path(
    r"C:\Users\MerelyMe\AppData\Local\Temp\codex-clipboard-a6be254d-7693-4f51-a144-19af8b4fb39a.png"
)
MINECRAFT_JAR = Path(
    r"C:\Users\MerelyMe\AppData\Roaming\ModrinthApp\meta\versions\1.21.11-0.19.3\1.21.11-0.19.3.jar"
)
SPECIAL_SPONGE_IMAGE = Path(
    r"C:\Users\MerelyMe\AppData\Local\Temp\codex-clipboard-a325a21a-5749-4b09-bcdf-8b1e02b8705b.png"
)
SPECIAL_BUCKET_IMAGE = Path(
    r"C:\Users\MerelyMe\AppData\Local\Temp\codex-clipboard-bba98067-7b82-4ba7-a42b-596f54383f6e.png"
)

if OUT.exists():
    shutil.rmtree(OUT)
shutil.copytree(BASE, OUT)

(OUT / "pack.mcmeta").write_text(
    json.dumps(
        {
            "pack": {
                "description": "§6MerelyMe Texture Pack §8• §fMenus, ranks & cosmetics",
                "pack_format": 75,
                "min_format": 75,
                "max_format": 86,
            }
        },
        indent=2,
    ),
    encoding="utf-8",
)

# Branded pack icon shown in Minecraft's resource-pack list.
pack_icon = Image.new("RGBA", (256, 256), (12, 18, 28, 255))
icon_pixels = pack_icon.load()
for y in range(256):
    for x in range(256):
        glow = max(0.0, 1.0 - (((x - 128) / 155) ** 2 + ((y - 92) / 125) ** 2))
        icon_pixels[x, y] = (
            int(10 + 8 * glow),
            int(16 + 34 * glow),
            int(27 + 42 * glow),
            255,
        )

logo = Image.open(SERVER_LOGO_IMAGE).convert("RGBA")
logo_box = logo.getchannel("A").getbbox()
if logo_box:
    logo = logo.crop(logo_box)
logo.thumbnail((232, 104), Image.Resampling.LANCZOS)
pack_icon.alpha_composite(logo, ((256 - logo.width) // 2, 28))

icon_draw = ImageDraw.Draw(pack_icon)
font_path = Path(r"C:\Windows\Fonts\arialbd.ttf")
try:
    title_font = ImageFont.truetype(str(font_path), 27)
    subtitle_font = ImageFont.truetype(str(font_path), 21)
except Exception:
    title_font = ImageFont.load_default()
    subtitle_font = title_font

def centred_icon_text(y: int, text: str, font, fill, stroke_fill) -> None:
    box = icon_draw.textbbox((0, 0), text, font=font, stroke_width=2)
    x = (256 - (box[2] - box[0])) // 2
    icon_draw.text(
        (x, y), text, font=font, fill=fill, stroke_width=2,
        stroke_fill=stroke_fill,
    )

centred_icon_text(150, "MERELYME", title_font, (255, 183, 35, 255), (53, 27, 5, 255))
centred_icon_text(184, "TEXTURE PACK", subtitle_font, (122, 231, 89, 255), (8, 45, 20, 255))

# Thin Minecraft-like frame so the icon stays readable on light and dark menus.
icon_draw.rectangle((7, 7, 248, 248), outline=(78, 94, 116, 255), width=5)
icon_draw.rectangle((12, 12, 243, 243), outline=(25, 33, 47, 255), width=3)
pack_icon.resize((128, 128), Image.Resampling.LANCZOS).save(OUT / "pack.png")

gui_dir = OUT / "assets/minecraft/optifine/gui/container/merelyme"
gui_dir.mkdir(parents=True, exist_ok=True)

# Remove the old broad rules and the global generic chest replacement.  The
# blue frame must only appear in the explicitly selected server menus.
for broad_rule in (
    gui_dir / "plugin_menu_large.properties",
    gui_dir / "plugin_menu_small.properties",
):
    if broad_rule.exists():
        broad_rule.unlink()
global_chest = OUT / "assets/minecraft/textures/gui/container/generic_54.png"
if global_chest.exists():
    global_chest.unlink()

# Use the newly supplied top-only artwork itself.  Its transparent canvas is
# cropped to the visible frame, then the header and each slot row are resized
# separately. This preserves the exact corners, blue plaque and side rails
# while pinning every painted slot to Minecraft's 18px grid.
supplied = Image.open(USER_GUI).convert("RGBA")
frame = supplied.crop((106, 81, 1431, 913))
source = Image.new("RGBA", (176, 133), (0, 0, 0, 0))
header = frame.crop((0, 0, frame.width, 149)).resize(
    (176, 17), Image.Resampling.LANCZOS
)
source.alpha_composite(header, (0, 0))
row_top, row_bottom = 149, 759
row_height = (row_bottom - row_top) / 5
for row in range(6):
    source_row = min(row, 4)
    y1 = round(row_top + source_row * row_height)
    y2 = round(row_top + (source_row + 1) * row_height)
    slot_row = frame.crop((0, y1, frame.width, y2)).resize(
        (176, 18), Image.Resampling.LANCZOS
    )
    source.alpha_composite(slot_row, (0, 17 + row * 18))
bottom_rail = frame.crop((0, 759, frame.width, frame.height)).resize(
    (176, 8), Image.Resampling.LANCZOS
)
source.alpha_composite(bottom_rail, (0, 125))

# Keep the blue artwork only on the server-menu half.  The lower inventory and
# hotbar come directly from the Minecraft 1.21.11 texture, so player slots look
# completely normal as requested.
with zipfile.ZipFile(MINECRAFT_JAR) as minecraft_jar:
    vanilla_chest = Image.open(
        minecraft_jar.open("assets/minecraft/textures/gui/container/generic_54.png")
    ).convert("RGBA")
top_only = vanilla_chest.copy()
top_only.alpha_composite(source.crop((0, 0, 176, 133)), (0, 0))
gui_texture = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
gui_texture.alpha_composite(top_only.crop((0, 0, 176, 222)), (0, 0))
gui_path = gui_dir / "selected_menus_blue_top.png"
gui_texture.save(gui_path)

# OptiGUI 2.3 cannot title-match the server's virtual inventories.  Use the
# proven server-menu glyph technique instead: a negative-space character moves
# a private-use bitmap eight pixels left, and the bitmap paints only the upper
# menu.  Every requested menu gets its own labelled bitmap and exact row count.
for stale in gui_dir.glob("*.properties"):
    stale.unlink()
optigui_dir = OUT / "assets/merelyme/optigui/gui"
if optigui_dir.exists():
    shutil.rmtree(optigui_dir)

GUI_GLYPHS = [
    ("crates", "CRATES", 4, "\uE200"),
    ("afk_crate", "AFK CRATE", 3, "\uE201"),
    ("stats", "STATS", 3, "\uE202"),
    ("rank_perks", "RANK PERKS", 6, "\uE203"),
    ("help", "HELP", 4, "\uE204"),
    ("shop_3", "SHOP", 3, "\uE205"),
    ("shop_4", "SHOP", 4, "\uE206"),
    ("shop_5", "SHOP", 5, "\uE207"),
    ("playtime", "PLAYTIME", 6, "\uE208"),
    ("one_v_one", "1V1", 3, "\uE209"),
    ("two_v_two", "2V2", 3, "\uE20A"),
    ("duel_players", "DUEL PLAYERS", 6, "\uE20B"),
    ("tournaments", "TOURNAMENTS", 3, "\uE20C"),
    ("kits", "KITS", 3, "\uE20D"),
    ("kit_preview", "KIT PREVIEW", 4, "\uE20E"),
    ("media", "MEDIA", 3, "\uE20F"),
    ("rules", "RULES", 3, "\uE210"),
    ("battle_pass", "BATTLE PASS", 6, "\uE211"),
    ("update", "UPDATE", 3, "\uE212"),
    ("daily_wheel", "DAILY WHEEL", 5, "\uE213"),
    ("glory_shop", "GLORY SHOP", 5, "\uE214"),
    ("glory", "GLORY", 6, "\uE215"),
    ("afk_crate_4", "AFK CRATE", 4, "\uE216"),
]

# Extra title-aware variants used by the client helper. These let ordinary
# plugin inventories receive the same frame even when their server title has
# no private-use marker. Every row count is prepared so slots always align.
AUTO_GUI_LABELS = {
    "crates": "CRATES",
    "claim": "CLAIM",
    "shop": "SHOP",
    "stats": "STATS",
    "rank_perks": "RANK PERKS",
    "sell": "SELL",
    "orders": "ORDERS",
    "auction_house": "AUCTION HOUSE",
    "afk_crate": "AFK CRATE",
    "vote": "VOTE",
    "update": "UPDATE",
    "battle_pass": "BATTLE PASS",
    "kits": "KITS",
    "kit_member": "MEMBER",
    "kit_knight": "KNIGHT",
    "kit_warrior": "WARRIOR",
    "kit_macer": "MACER",
    "kit_prime": "PRIME",
    "homes": "HOMES",
    "tournaments": "TOURNAMENTS",
    "rtp": "RTP",
    "playtime": "PLAYTIME",
    "rewards": "REWARDS",
    "two_v_two": "2V2",
    "one_v_one": "1V1",
}

GUI_FONT = {
    "A": ("01110", "10001", "10001", "11111", "10001", "10001", "10001"),
    "B": ("11110", "10001", "10001", "11110", "10001", "10001", "11110"),
    "C": ("01111", "10000", "10000", "10000", "10000", "10000", "01111"),
    "D": ("11110", "10001", "10001", "10001", "10001", "10001", "11110"),
    "E": ("11111", "10000", "10000", "11110", "10000", "10000", "11111"),
    "F": ("11111", "10000", "10000", "11110", "10000", "10000", "10000"),
    "G": ("01111", "10000", "10000", "10111", "10001", "10001", "01111"),
    "H": ("10001", "10001", "10001", "11111", "10001", "10001", "10001"),
    "I": ("01110", "00100", "00100", "00100", "00100", "00100", "01110"),
    "K": ("10001", "10010", "10100", "11000", "10100", "10010", "10001"),
    "L": ("10000", "10000", "10000", "10000", "10000", "10000", "11111"),
    "M": ("10001", "11011", "10101", "10101", "10001", "10001", "10001"),
    "N": ("10001", "11001", "10101", "10011", "10001", "10001", "10001"),
    "O": ("01110", "10001", "10001", "10001", "10001", "10001", "01110"),
    "P": ("11110", "10001", "10001", "11110", "10000", "10000", "10000"),
    "R": ("11110", "10001", "10001", "11110", "10100", "10010", "10001"),
    "S": ("01111", "10000", "10000", "01110", "00001", "00001", "11110"),
    "T": ("11111", "00100", "00100", "00100", "00100", "00100", "00100"),
    "U": ("10001", "10001", "10001", "10001", "10001", "10001", "01110"),
    "V": ("10001", "10001", "10001", "10001", "10001", "01010", "00100"),
    "W": ("10001", "10001", "10001", "10101", "10101", "11011", "10001"),
    "Y": ("10001", "10001", "01010", "00100", "00100", "00100", "00100"),
    "1": ("00100", "01100", "00100", "00100", "00100", "00100", "01110"),
    "2": ("01110", "10001", "00001", "00010", "00100", "01000", "11111"),
}

def draw_gui_label(image: Image.Image, label: str, y: int = 5) -> None:
    # Use solid two-pixel strokes so every title stays bright and readable on
    # the blue plaque.  Seven-pixel spacing keeps the bold letters separate.
    advance = 7
    text_width = len(label) * advance - 1
    x = (176 - text_width) // 2
    pixels = image.load()
    for character in label:
        if character == " ":
            x += advance
            continue
        glyph = GUI_FONT[character]
        for gy, line in enumerate(glyph):
            for gx, bit in enumerate(line):
                if bit == "1":
                    # A navy offset followed by a 2x2 pure-white pixel stroke.
                    for ox, oy in ((2, 2), (2, 1), (1, 2)):
                        if x + gx + ox < 176 and y + gy + oy < 256:
                            pixels[x + gx + ox, y + gy + oy] = (8, 35, 75, 255)
                    for ox, oy in ((0, 0), (1, 0), (0, 1), (1, 1)):
                        pixels[x + gx + ox, y + gy + oy] = (255, 255, 255, 255)
        x += advance

gui_font_dir = OUT / "assets/minecraft/textures/font"
gui_font_dir.mkdir(parents=True, exist_ok=True)
gui_providers = []
title_lines = []
preview_cells = []
for gui_id, label, rows, character in GUI_GLYPHS:
    # Header + complete slot rows + the eight-pixel bottom rail.  The previous
    # layout painted the rail over the lower part of the final slot row.
    top_height = 25 + rows * 18
    glyph_image = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    glyph_image.alpha_composite(source.crop((0, 0, 176, top_height)), (0, 0))
    # Finish every row count with the exact ornate bottom rail from the source.
    # It stays within the vanilla draw area, so it is also visible with Vulkan.
    glyph_image.alpha_composite(
        source.crop((0, 125, 176, 133)), (0, top_height - 8)
    )
    # Keep every menu title slightly higher inside the blue plaque.
    draw_gui_label(glyph_image, label, 4)
    texture_name = f"gui_blue_{gui_id}.png"
    glyph_image.save(gui_font_dir / texture_name)
    gui_providers.append({
        "type": "bitmap",
        "file": f"minecraft:font/gui_blue_{gui_id}.png",
        "ascent": 13,
        "height": 256,
        "chars": [character],
    })
    title_lines.append(f"{gui_id}\t&f\uF804{character}\t{rows} rows\t{label}")
    preview_cells.append((label, rows, glyph_image))

for gui_id, label in AUTO_GUI_LABELS.items():
    for rows in range(1, 7):
        top_height = 25 + rows * 18
        glyph_image = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
        glyph_image.alpha_composite(source.crop((0, 0, 176, top_height)), (0, 0))
        glyph_image.alpha_composite(
            source.crop((0, 125, 176, 133)), (0, top_height - 8)
        )
        draw_gui_label(glyph_image, label, 4)
        glyph_image.save(gui_font_dir / f"gui_blue_auto_{gui_id}_{rows}.png")

# Add GUI glyphs to the default font used by inventory titles.  Keep the rank
# glyph providers and vanilla references already present in this pack.
default_font_path = OUT / "assets/minecraft/font/default.json"
default_font = json.loads(default_font_path.read_text(encoding="utf-8"))
providers = default_font.get("providers", [])
providers = [
    p for p in providers
    if not (
        p.get("type") == "bitmap"
        and "gui_blue_" in str(p.get("file", ""))
    )
    and not (
        p.get("type") == "space"
        and "\uF804" in p.get("advances", {})
    )
]
insert_at = next(
    (i for i, provider in enumerate(providers) if provider.get("type") == "reference"),
    len(providers),
)
providers[insert_at:insert_at] = gui_providers + [{
    "type": "space",
    "advances": {
        "\uF801": -1, "\uF802": -2, "\uF803": -4, "\uF804": -8,
        "\uF805": -16, "\uF806": -32, "\uF807": -64, "\uF808": -128,
    },
}]
default_font["providers"] = providers
default_font_path.write_text(
    json.dumps(default_font, indent=2, ensure_ascii=False), encoding="utf-8"
)
(ROOT / "Menu-Glyph-Titles.txt").write_text(
    "\n".join(title_lines) + "\n", encoding="utf-8"
)

# Contact sheet for reviewing every label without opening Minecraft.
sheet_width = 720
cell_width, cell_height = 240, 210
sheet_rows = (len(preview_cells) + 2) // 3
sheet = Image.new("RGBA", (sheet_width, sheet_rows * cell_height), (15, 22, 34, 255))
sheet_draw = ImageDraw.Draw(sheet)
for index, (label, rows, glyph_image) in enumerate(preview_cells):
    col, row = index % 3, index // 3
    cell_x, cell_y = col * cell_width, row * cell_height
    crop_height = 25 + rows * 18
    preview_gui = glyph_image.crop((0, 0, 176, crop_height)).resize(
        (211, crop_height * 211 // 176), Image.Resampling.NEAREST
    )
    sheet.alpha_composite(preview_gui, (cell_x + 14, cell_y + 28))
    sheet_draw.text((cell_x + 14, cell_y + 7), label, fill=(220, 235, 255, 255))
sheet.save(ROOT / "Blue-Menu-Labels-Preview.png")

textures = OUT / "assets/merelyme/textures/item"
models = OUT / "assets/merelyme/models/item"
textures.mkdir(parents=True, exist_ok=True)
models.mkdir(parents=True, exist_ok=True)

pixl = ROOT / "pixl-ranks/Ranks"
base_rank = OUT / "assets/merelyme/textures/font/ranks"

# A purpose-built 7x7 alphabet. Every glyph uses the same cell, baseline,
# stroke width and centre axis. It is later enlarged by exactly 4x, so no
# fractional resize can remove or shift individual pixels.
PIXEL_FONT = {
    "A": ("0011100", "0100010", "1000001", "1000001", "1111111", "1000001", "1000001"),
    "B": ("1111100", "1000010", "1000010", "1111100", "1000010", "1000010", "1111100"),
    "C": ("0111110", "1000001", "1000000", "1000000", "1000000", "1000001", "0111110"),
    "D": ("1111100", "1000010", "1000001", "1000001", "1000001", "1000010", "1111100"),
    "E": ("1111111", "1000000", "1000000", "1111110", "1000000", "1000000", "1111111"),
    "G": ("0111110", "1000001", "1000000", "1001111", "1000001", "1000001", "0111110"),
    "H": ("1000001", "1000001", "1000001", "1111111", "1000001", "1000001", "1000001"),
    "I": ("1111111", "0001000", "0001000", "0001000", "0001000", "0001000", "1111111"),
    "K": ("1000001", "1000010", "1000100", "1111000", "1000100", "1000010", "1000001"),
    "L": ("1000000", "1000000", "1000000", "1000000", "1000000", "1000000", "1111111"),
    "M": ("1000001", "1100011", "1010101", "1001001", "1000001", "1000001", "1000001"),
    "N": ("1000001", "1100001", "1010001", "1001001", "1000101", "1000011", "1000001"),
    "O": ("0011100", "0100010", "1000001", "1000001", "1000001", "0100010", "0011100"),
    "P": ("1111100", "1000010", "1000010", "1111100", "1000000", "1000000", "1000000"),
    "R": ("1111100", "1000010", "1000010", "1111100", "1000100", "1000010", "1000001"),
    "S": ("0111110", "1000001", "1000000", "0111110", "0000001", "1000001", "0111110"),
    "T": ("1111111", "0001000", "0001000", "0001000", "0001000", "0001000", "0001000"),
    "W": ("1000001", "1000001", "1000001", "1001001", "1010101", "1010101", "0100010"),
    "Y": ("1000001", "1000001", "0100010", "0010100", "0001000", "0001000", "0001000"),
}

RANK_COLOURS = {
    "player": ((218, 218, 218, 255), (100, 100, 100, 255), (54, 54, 54, 255)),
    "member": ((192, 192, 192, 255), (92, 92, 92, 255), (48, 48, 48, 255)),
    "booster": ((232, 92, 226, 255), (119, 56, 122, 255), (65, 32, 68, 255)),
    "knight": ((61, 235, 57, 255), (42, 103, 45, 255), (24, 63, 27, 255)),
    "warrior": ((255, 64, 68, 255), (121, 43, 49, 255), (72, 26, 31, 255)),
    "macer": ((54, 195, 238, 255), (34, 95, 121, 255), (19, 57, 73, 255)),
    "prime": ((255, 204, 0, 255), (255, 132, 0, 255), (105, 54, 8, 255)),
    "clipper": ((0, 145, 180, 255), (18, 67, 82, 255), (7, 42, 52, 255)),
    "media": ((210, 62, 246, 255), (103, 47, 121, 255), (61, 27, 73, 255)),
    "helper": ((255, 151, 25, 255), (119, 72, 28, 255), (68, 41, 17, 255)),
    "mod": ((61, 141, 239, 255), (46, 82, 119, 255), (25, 48, 72, 255)),
    "admin": ((162, 75, 239, 255), (77, 44, 108, 255), (43, 25, 62, 255)),
    "owner": ((255, 48, 55, 255), (119, 43, 48, 255), (69, 24, 28, 255)),
}


def draw_rank_letters(badge: Image.Image, label: str) -> None:
    pixels = badge.load()
    for index, char in enumerate(label):
        glyph_x = 4 + index * 8
        for gy, row in enumerate(PIXEL_FONT[char]):
            for gx, bit in enumerate(row):
                if bit == "1":
                    pixels[glyph_x + gx, 2 + gy] = (255, 255, 255, 255)


def make_clean_rank_badge(label: str, colours, include_letters: bool = True) -> Image.Image:
    main, lower, outline = colours
    # One extra source pixel of padding on every side keeps the lettering away
    # from the coloured border without making long ranks exceed 256px later.
    width = len(label) * 8 + 7
    badge = Image.new("RGBA", (width, 11), (0, 0, 0, 0))
    pixels = badge.load()

    # Crisp sandwich-shaped shell with one-pixel rounded corners.
    for y in range(11):
        for x in range(width):
            edge = x == 0 or x == width - 1 or y == 0 or y == 10
            corner = (x in (0, width - 1)) and (y in (0, 10))
            if corner:
                continue
            if edge:
                pixels[x, y] = outline
            elif y <= 6:
                pixels[x, y] = main
            else:
                pixels[x, y] = lower

    # Identical 7x7 cells, one blank column between letters and equal padding.
    if include_letters:
        draw_rank_letters(badge, label)
    return badge


for rank_name, rank_colours in RANK_COLOURS.items():
    make_clean_rank_badge(rank_name.upper(), rank_colours).save(base_rank / f"{rank_name}.png")

rank_sources = {rank: base_rank / f"{rank}.png" for rank in RANK_COLOURS}

# The chat/TAB version must be authored at its final on-screen resolution.
# Minecraft previously had to shrink a 52px cell to 10px, which made the
# square strokes look rounded and caused chat/TAB to differ from the source.
# Clean 5x6 alphabet based on the compact badge proportions in the supplied
# reference. Thin one-pixel strokes keep the lettering away from the frame.
FONT_PIXEL_FONT = {
    "A": ("01110", "10001", "11111", "10001", "10001"),
    "B": ("11110", "10001", "11110", "10001", "11110"),
    "C": ("01111", "10000", "10000", "10000", "01111"),
    "D": ("11110", "10001", "10001", "10001", "11110"),
    "E": ("11111", "10000", "11110", "10000", "11111"),
    "G": ("01111", "10000", "10111", "10001", "01111"),
    "H": ("10001", "10001", "11111", "10001", "10001"),
    "I": ("11111", "00100", "00100", "00100", "11111"),
    "K": ("10001", "10010", "11100", "10010", "10001"),
    "L": ("10000", "10000", "10000", "10000", "11111"),
    "M": ("10001", "11011", "10101", "10001", "10001"),
    "N": ("10001", "11001", "10101", "10011", "10001"),
    "O": ("01110", "10001", "10001", "10001", "01110"),
    "P": ("11110", "10001", "11110", "10000", "10000"),
    "R": ("11110", "10001", "11110", "10010", "10001"),
    "S": ("01111", "10000", "01110", "00001", "11110"),
    "T": ("11111", "00100", "00100", "00100", "00100"),
    "W": ("10001", "10001", "10101", "11011", "10001"),
    "Y": ("10001", "01010", "00100", "00100", "00100"),
}


def make_font_rank_badge(label: str, colours) -> Image.Image:
    main, lower, outline = colours
    # MaceSMP-like ratio: short label, taller shell and two-pixel side margins.
    width = len(label) * 6 + 3
    # Native-size cell prevents Minecraft from blurring the square pixels.
    canvas = Image.new("RGBA", (width, 10), (0, 0, 0, 0))
    pixels = canvas.load()

    # Eight-pixel badge: 20% shorter while retaining the full six-pixel letters.
    for y in range(1, 9):
        for x in range(width):
            corner = (x in (0, width - 1)) and (y in (1, 8))
            if corner:
                continue
            edge = x == 0 or x == width - 1 or y in (1, 8)
            if edge:
                pixels[x, y] = outline
            elif y <= 5:
                pixels[x, y] = main
            else:
                pixels[x, y] = lower

    # Targhette-inspired five-by-five letters: square strokes, symmetric forms
    # and one full pixel of vertical breathing room inside the same shell.
    text_width = len(label) * 6 - 1
    glyph_start = (width - text_width) // 2
    for index, char in enumerate(label):
        glyph_x = glyph_start + index * 6
        for gy, row in enumerate(FONT_PIXEL_FONT[char]):
            for gx, bit in enumerate(row):
                if bit == "1":
                    pixels[glyph_x + gx, 2 + gy] = (255, 255, 255, 255)
    return canvas


# Prepare the font badges used in chat, nametags and TAB.
font_rank_dir = OUT / "assets/merelyme/textures/font/ranks"
font_rank_dir.mkdir(parents=True, exist_ok=True)
for rank_file in sorted(base_rank.glob("*.png")):
    rank_name = rank_file.stem
    label = rank_name.upper()
    make_font_rank_badge(label, RANK_COLOURS[rank_name]).save(
        font_rank_dir / rank_file.name
    )

rank_font_json = OUT / "assets/merelyme/font/ranks.json"
rank_font_data = json.loads(rank_font_json.read_text(encoding="utf-8"))
for provider in rank_font_data.get("providers", []):
    provider["ascent"] = 8
    provider["height"] = 10
rank_font_json.write_text(
    json.dumps(rank_font_data, indent=2, ensure_ascii=False), encoding="utf-8"
)


def badge_canvas(source_path: Path) -> Image.Image:
    badge = Image.open(source_path).convert("RGBA")
    # Fill almost the entire item width so the lettering remains readable in
    # a 16x16 GUI slot, while retaining hard pixel edges.
    target_width = 60
    target_height = max(1, round(badge.height * target_width / badge.width))
    badge = badge.resize((target_width, target_height), Image.Resampling.NEAREST)
    canvas = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    canvas.alpha_composite(
        badge, ((64 - badge.width) // 2, (64 - badge.height) // 2)
    )
    return canvas


entries = []
cmd = 1201
for rank, src in rank_sources.items():
    name = f"rank_badge_{rank}"
    badge_canvas(src).save(textures / f"{name}.png")
    (models / f"{name}.json").write_text(
        json.dumps(
            {
                "parent": "minecraft:item/generated",
                "textures": {"layer0": f"merelyme:item/{name}"},
            },
            indent=2,
        ),
        encoding="utf-8",
    )
    entries.append(
        {
            "threshold": cmd,
            "model": {
                "type": "minecraft:model",
                "model": f"merelyme:item/{name}",
            },
        }
    )
    cmd += 1

icon_root = ROOT / "basic-ui/Basic UI Icons/Icons"
icon_names = [
    "arrow_down", "arrow_left", "arrow_right", "arrow_up",
    "check", "check_color", "close", "close_color", "coins",
    "exclamation", "exclamation_color", "lock_closed", "lock_open",
    "minus", "minus_color", "plus", "plus_color", "question",
    "question_color", "refresh", "search", "settings",
]
icon_map = {
    1251 + index: (f"ui_{name}", icon_root / f"{name}.png")
    for index, name in enumerate(icon_names)
}
for icon_cmd, (name, src) in icon_map.items():
    icon = Image.open(src).convert("RGBA").resize((32, 32), Image.Resampling.NEAREST)
    canvas = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    canvas.alpha_composite(icon)
    canvas.save(textures / f"{name}.png")
    (models / f"{name}.json").write_text(
        json.dumps(
            {
                "parent": "minecraft:item/generated",
                "textures": {"layer0": f"merelyme:item/{name}"},
            },
            indent=2,
        ),
        encoding="utf-8",
    )
    entries.append(
        {
            "threshold": icon_cmd,
            "model": {
                "type": "minecraft:model",
                "model": f"merelyme:item/{name}",
            },
        }
    )

# Give only the named Special Crate tools the Ice Set artwork.  These CIT
# rules deliberately match both the material and display name, leaving every
# normal vanilla tool untouched.
special_cit = OUT / "assets/minecraft/optifine/cit/merelyme_special"
special_cit.mkdir(parents=True, exist_ok=True)


def write_generated_model(name: str, texture: str, handheld: bool = False) -> None:
    (models / f"{name}.json").write_text(
        json.dumps(
            {
                "parent": "minecraft:item/handheld" if handheld else "minecraft:item/generated",
                "textures": {"layer0": texture},
            },
            indent=2,
        ),
        encoding="utf-8",
    )


def write_cit(name: str, items: str, name_pattern: str) -> None:
    (special_cit / f"{name}.properties").write_text(
        "type=item\n"
        f"matchItems={items}\n"
        f"model=merelyme:item/{name}\n"
        f"nbt.display.Name=iregex:{name_pattern}\n",
        encoding="utf-8",
    )


with zipfile.ZipFile(ICE_ZIP) as ice_zip:
    for out_name, source_name in {
        "special_drill": "ice_pickaxe",
        "special_shovel": "ice_shovel",
        "special_axe": "ice_axe",
        "special_sell_axe": "ice_hoe",
        "special_hoe": "ice_hoe",
    }.items():
        (textures / f"{out_name}.png").write_bytes(
            ice_zip.read(f"ItemsAdder/contents/ice_set/resourcepack/ice_set/textures/{source_name}.png")
        )
        write_generated_model(out_name, f"merelyme:item/{out_name}", handheld=True)

# Recolour Minecraft's real 16x16 sponge texture instead of using the flat
# supplied render.  A cube model now appears as a proper blue sponge both in
# the GUI and in the player's hand.
with zipfile.ZipFile(MINECRAFT_JAR) as minecraft_jar:
    raw_sponge = Image.open(
        minecraft_jar.open("assets/minecraft/textures/block/sponge.png")
    ).convert("RGBA")
sponge_pixels = np.asarray(raw_sponge).copy()
source_rgb = sponge_pixels[:, :, :3].astype(np.float32)
luma = (
    source_rgb[:, :, 0] * 0.2126
    + source_rgb[:, :, 1] * 0.7152
    + source_rgb[:, :, 2] * 0.0722
) / 255.0
dark = np.array([18, 72, 145], dtype=np.float32)
light = np.array([118, 224, 247], dtype=np.float32)
blue_rgb = dark + (light - dark) * np.clip((luma - 0.20) / 0.65, 0, 1)[:, :, None]
sponge_pixels[:, :, :3] = blue_rgb.astype(np.uint8)
Image.fromarray(sponge_pixels, "RGBA").save(textures / "special_sponge.png")
# A placed item no longer has its custom name/CIT data, so Minecraft switches
# to the vanilla block texture. Override that block texture in this test pack
# as well so a placed Special Sponge stays visibly blue.
vanilla_block_textures = OUT / "assets/minecraft/textures/block"
vanilla_block_textures.mkdir(parents=True, exist_ok=True)
Image.fromarray(sponge_pixels, "RGBA").save(vanilla_block_textures / "sponge.png")
(models / "special_sponge.json").write_text(
    json.dumps(
        {
            "parent": "minecraft:block/cube_all",
            "textures": {"all": "merelyme:item/special_sponge"},
        },
        indent=2,
    ),
    encoding="utf-8",
)

bucket = Image.open(SPECIAL_BUCKET_IMAGE).convert("RGBA")
bucket.thumbnail((64, 64), Image.Resampling.NEAREST)
bucket_canvas = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
bucket_canvas.alpha_composite(bucket, ((64 - bucket.width) // 2, (64 - bucket.height) // 2))
bucket_canvas.save(textures / "special_bucket.png")
write_generated_model("special_bucket", "merelyme:item/special_bucket")

write_cit("special_drill", "minecraft:netherite_pickaxe", ".*SPECIAL (DRILL|PICKAXE).*" )
write_cit("special_shovel", "minecraft:netherite_shovel", ".*SPECIAL SHOVEL.*")
write_cit("special_axe", "minecraft:netherite_axe", ".*SPECIAL AXE.*")
write_cit("special_sell_axe", "minecraft:diamond_axe", ".*SPECIAL SELL AXE.*")
write_cit(
    "special_hoe",
    "minecraft:netherite_hoe minecraft:diamond_hoe minecraft:iron_hoe",
    ".*SPECIAL HOE.*",
)
write_cit("special_sponge", "minecraft:sponge", ".*SPECIAL SPONGE.*")
write_cit("special_bucket", "minecraft:water_bucket", ".*SPECIAL BUCKET.*")

# Replace the coloured candle placeholders used by SKCrates with proper key
# artwork from the supplied Key 1 pack. Matching both the material and the
# reward name keeps ordinary candles completely unchanged.
key_source_root = (
    "en/itemsadder-type/plugins/ItemsAdder/contents/weekly_dot/"
    "resourcepack/key1/textures/items"
)
with zipfile.ZipFile(KEYS_ZIP) as keys_zip:
    key_sources = {
        "crate_key_iron": "iron_key1.png",
        "crate_key_gold": "golden_key1.png",
    }
    for out_name, source_name in key_sources.items():
        (textures / f"{out_name}.png").write_bytes(
            keys_zip.read(f"{key_source_root}/{source_name}")
        )
        write_generated_model(out_name, f"merelyme:item/{out_name}")

    # Coal uses the same clean round-key silhouette in a near-black palette.
    # Grey highlights keep the shape readable on Minecraft's dark GUI.
    coal_key = Image.open(
        keys_zip.open(f"{key_source_root}/stone_key1.png")
    ).convert("RGBA")
    coal_pixels = np.asarray(coal_key).copy()
    coal_rgb = coal_pixels[:, :, :3].astype(np.float32)
    coal_luma = (
        coal_rgb[:, :, 0] * 0.2126
        + coal_rgb[:, :, 1] * 0.7152
        + coal_rgb[:, :, 2] * 0.0722
    ) / 255.0
    coal_dark = np.array([4, 5, 8], dtype=np.float32)
    coal_light = np.array([82, 86, 96], dtype=np.float32)
    coal_pixels[:, :, :3] = (
        coal_dark + (coal_light - coal_dark) * coal_luma[:, :, None]
    ).astype(np.uint8)
    Image.fromarray(coal_pixels, "RGBA").save(textures / "crate_key_coal.png")
    write_generated_model("crate_key_coal", "merelyme:item/crate_key_coal")

    def fill_enclosed_transparency(image: Image.Image, fill: tuple[int, int, int, int]) -> Image.Image:
        """Fill transparent holes inside the key while keeping its outer cutout."""
        pixels = np.asarray(image).copy()
        transparent = pixels[:, :, 3] < 16
        height, width = transparent.shape
        outside = np.zeros_like(transparent, dtype=bool)
        stack = [(x, y) for x in range(width) for y in (0, height - 1)]
        stack += [(x, y) for y in range(height) for x in (0, width - 1)]
        while stack:
            x, y = stack.pop()
            if x < 0 or y < 0 or x >= width or y >= height:
                continue
            if outside[y, x] or not transparent[y, x]:
                continue
            outside[y, x] = True
            stack.extend(((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)))
        holes = transparent & ~outside
        pixels[holes] = fill
        return Image.fromarray(pixels, "RGBA")

    # The downloaded pack has no emerald key. Recolour the supplied ice key
    # while preserving its original pixel shading. Its ring receives a dark
    # green centre like the other keys instead of a transparent hole.
    emerald_key = Image.open(
        keys_zip.open(f"{key_source_root}/iron_key1.png")
    ).convert("RGBA")
    emerald_pixels = np.asarray(emerald_key).copy()
    emerald_rgb = emerald_pixels[:, :, :3].astype(np.float32)
    emerald_luma = (
        emerald_rgb[:, :, 0] * 0.2126
        + emerald_rgb[:, :, 1] * 0.7152
        + emerald_rgb[:, :, 2] * 0.0722
    ) / 255.0
    emerald_dark = np.array([0, 82, 38], dtype=np.float32)
    emerald_light = np.array([65, 255, 132], dtype=np.float32)
    emerald_pixels[:, :, :3] = (
        emerald_dark
        + (emerald_light - emerald_dark) * emerald_luma[:, :, None]
    ).astype(np.uint8)
    # Keep the centre of the round handle transparent, matching Iron and Gold.
    emerald_finished = Image.fromarray(emerald_pixels, "RGBA")
    emerald_finished.save(textures / "crate_key_emerald.png")
    write_generated_model("crate_key_emerald", "merelyme:item/crate_key_emerald")

    # Special uses the second key shape so it differs from every normal tier,
    # recoloured to light blue while retaining the transparent round centre.
    special_key = Image.open(
        keys_zip.open(f"{key_source_root}/iron_key2.png")
    ).convert("RGBA")
    special_pixels = np.asarray(special_key).copy()
    special_rgb = special_pixels[:, :, :3].astype(np.float32)
    special_luma = (
        special_rgb[:, :, 0] * 0.2126
        + special_rgb[:, :, 1] * 0.7152
        + special_rgb[:, :, 2] * 0.0722
    ) / 255.0
    special_dark = np.array([0, 75, 155], dtype=np.float32)
    special_light = np.array([90, 244, 255], dtype=np.float32)
    special_pixels[:, :, :3] = (
        special_dark + (special_light - special_dark) * special_luma[:, :, None]
    ).astype(np.uint8)
    # Preserve every transparent opening in the ornate Special key too.
    special_finished = Image.fromarray(special_pixels, "RGBA")
    special_finished.save(textures / "crate_key_special.png")
    write_generated_model("crate_key_special", "merelyme:item/crate_key_special")

write_cit(
    "crate_key_coal",
    "minecraft:black_candle",
    ".*COAL (CRATE )?KEY(S)?.*",
)
write_cit(
    "crate_key_iron",
    "minecraft:white_candle",
    ".*IRON (CRATE )?KEY(S)?.*",
)
write_cit(
    "crate_key_gold",
    "minecraft:yellow_candle",
    ".*GOLD (CRATE )?KEY(S)?.*",
)
write_cit(
    "crate_key_emerald",
    "minecraft:lime_candle",
    ".*EMERALD (CRATE )?KEY(S)?.*",
)
write_cit(
    "crate_key_special",
    "minecraft:light_blue_candle",
    ".*SPECIAL (CRATE )?KEY(S)?.*",
)

# Blue tooltip sprites from the supplied Simply Tooltips pack.  The server
# attaches `simply_tooltips:blue` only to MerelyMe special tools, so ordinary
# items retain Minecraft's normal tooltip.
tooltip_out = OUT / "assets/merelyme/textures/gui/sprites/tooltip"
tooltip_out.mkdir(parents=True, exist_ok=True)
tooltip_source = (
    "Drag & Drop/ItemsAdder/contents/simply_tooltips/assets/"
    "simply_tooltips/textures/gui/sprites/tooltip"
)
with zipfile.ZipFile(TOOLTIPS_ZIP) as tooltip_zip:
    for source_name, target_name in (
        ("blue_background.png", "special_tools_background.png"),
        ("blue_background.png.mcmeta", "special_tools_background.png.mcmeta"),
        ("blue_frame.png", "special_tools_frame.png"),
        ("blue_frame.png.mcmeta", "special_tools_frame.png.mcmeta"),
    ):
        (tooltip_out / target_name).write_bytes(
            tooltip_zip.read(f"{tooltip_source}/{source_name}")
        )

# Turn the supplied square MerelySMP artwork into proportional, centred font
# glyphs for the scoreboard and TAB header.  Keeping it square avoids the
# stretched wordmark effect from the previous version.
logo = Image.open(SERVER_LOGO_IMAGE).convert("RGBA")
alpha = np.asarray(logo)[:, :, 3]
ys, xs = np.where(alpha > 8)
if len(xs):
    logo = logo.crop((int(xs.min()), int(ys.min()), int(xs.max()) + 1, int(ys.max()) + 1))
# The scoreboard variant uses only the Merely/SMP lettering.  Removing the
# robot and floating pieces makes the header considerably shorter and cleaner.
logo = logo.crop((0, int(logo.height * 0.515), logo.width, logo.height))
# Remove the remaining purple connector/diamond so the glyph contains letters
# only, as requested.
logo_pixels = np.asarray(logo).copy()
purple = (
    (logo_pixels[:, :, 3] > 0)
    & (logo_pixels[:, :, 2] > logo_pixels[:, :, 1] * 1.08)
    & (logo_pixels[:, :, 2] > logo_pixels[:, :, 0] * 1.02)
)
logo_pixels[purple, 3] = 0
# Remove the last dark, neutral fragments from the old mascot above the word.
# Keep the gold/brown letter outlines intact by limiting this to low-saturation
# pixels in the upper strip of the cropped artwork.
rgb = logo_pixels[:, :, :3].astype(np.int16)
yy, xx = np.indices(logo_pixels.shape[:2])
neutral_fragment = (
    (logo_pixels[:, :, 3] > 0)
    & (yy < int(logo_pixels.shape[0] * 0.24))
    & (xx > int(logo_pixels.shape[1] * 0.20))
    & (xx < int(logo_pixels.shape[1] * 0.62))
    & ((rgb.max(axis=2) - rgb.min(axis=2)) < 42)
    & (rgb.max(axis=2) < 220)
)
logo_pixels[neutral_fragment, 3] = 0
logo = Image.fromarray(logo_pixels, "RGBA")
alpha = np.asarray(logo)[:, :, 3]
ys, xs = np.where(alpha > 8)
if len(xs):
    logo = logo.crop((int(xs.min()), int(ys.min()), int(xs.max()) + 1, int(ys.max()) + 1))
logo_dir = OUT / "assets/merelyme/textures/font"
logo_dir.mkdir(parents=True, exist_ok=True)
small_logo_cell = Image.new("RGBA", (190, 105), (0, 0, 0, 0))
large_logo_cell = Image.new("RGBA", (220, 120), (0, 0, 0, 0))
small_logo = logo.resize((104, 50), Image.Resampling.LANCZOS)
large_logo = logo.resize((125, 59), Image.Resampling.LANCZOS)
small_logo_cell.alpha_composite(
    small_logo,
    ((small_logo_cell.width - small_logo.width) // 2, 38),
)
large_logo_cell.alpha_composite(
    large_logo,
    ((large_logo_cell.width - large_logo.width) // 2, 37),
)
small_logo_cell.save(logo_dir / "server_logo_small.png")
large_logo_cell.save(logo_dir / "server_logo_large.png")

# Bubble Icons supplied by the user.  Each source is trimmed and centred in an
# identical 20x20 cell so all five scoreboard rows keep the same alignment.
stat_icons = Image.new("RGBA", (100, 20), (0, 0, 0, 0))

def bubble_icon(raw: bytes, blue_star: bool = False) -> Image.Image:
    icon = Image.open(io.BytesIO(raw)).convert("RGBA")
    bbox = icon.getchannel("A").getbbox()
    if bbox:
        icon = icon.crop(bbox)
    if blue_star:
        px = np.asarray(icon).copy()
        visible = px[:, :, 3] > 0
        light = px[:, :, :3].max(axis=2)
        px[:, :, 0][visible] = np.clip(light[visible] * 0.12, 0, 255)
        px[:, :, 1][visible] = np.clip(95 + light[visible] * 0.63, 0, 255)
        px[:, :, 2][visible] = np.clip(155 + light[visible] * 0.42, 0, 255)
        icon = Image.fromarray(px, "RGBA")
    # A 14-pixel drawing leaves breathing room inside the glyph cell and keeps
    # the symbols from touching the labels at common Minecraft GUI scales.
    scale = min(14 / icon.width, 14 / icon.height)
    size = (max(1, round(icon.width * scale)), max(1, round(icon.height * scale)))
    icon = icon.resize(size, Image.Resampling.NEAREST)
    cell = Image.new("RGBA", (20, 20), (0, 0, 0, 0))
    cell.alpha_composite(icon, ((20 - icon.width) // 2, (20 - icon.height) // 2))
    return cell

with zipfile.ZipFile(BUBBLE_ICONS_ZIP) as bubble_zip:
    icon_names = ["star.png", "dollar.png", "trophy.png", "clock.png", "skull.png"]
    for index, name in enumerate(icon_names):
        raw = bubble_zip.read(f"Raw Icons Folder/{name}")
        stat_icons.alpha_composite(bubble_icon(raw, blue_star=index == 0), (index * 20, 0))

stat_icons.save(logo_dir / "stat_icons.png")

default_font_path = OUT / "assets/minecraft/font/default.json"
default_font = json.loads(default_font_path.read_text(encoding="utf-8"))
for provider in default_font.get("providers", []):
    if provider.get("file", "").startswith("merelyme:font/ranks/"):
        provider["ascent"] = 8
        provider["height"] = 10
default_font["providers"] = [
    {
        "type": "bitmap",
        "file": "merelyme:font/stat_icons.png",
        "ascent": 9,
        "height": 11,
        "chars": ["\ue120\ue121\ue122\ue123\ue124"],
    },
    {
        "type": "bitmap",
        "file": "merelyme:font/server_logo_small.png",
        "ascent": 25,
        "height": 44,
        "chars": ["\ue101"],
    },
    {
        "type": "bitmap",
        "file": "merelyme:font/server_logo_large.png",
        "ascent": 35,
        "height": 52,
        "chars": ["\ue102"],
    },
    *default_font["providers"],
]
default_font_path.write_text(
    json.dumps(default_font, indent=2, ensure_ascii=False), encoding="utf-8"
)

# A small cosmetic sample from the free packs. The original namespaces and
# model IDs are retained so the assets remain easy to migrate to ItemsAdder.
sample_models = [
    *[(9000 + index, "ax_wings_pack", name) for index, name in enumerate([
        "angel_wings", "astronaut_wings", "bluefire_wings", "brown_wings",
        "butterfly_wings", "demon_wings", "dragon_wings", "eagle_wings",
        "golden_wings", "ocean_wings", "purple_wings", "phoenix_wings",
    ])],
    *[(10000 + index, "ax_free_hats_pack", name) for index, name in enumerate([
        "diving_helmet", "chicken_hat", "flight_hat", "military_helmet",
        "motorcycle_helmet", "pot_hat",
    ])],
]

wing_root = "Wing Cosmetics - Artillex-Studios/ItemsAdder/contents/ax_wings_pack/resourcepack/assets/ax_wings_pack"
hat_root = "Hats Pack - Artillex-Studios/ItemsAdder/contents/ax_free_hats_pack/resourcepack/assets/ax_free_hats_pack"

with zipfile.ZipFile(WINGS_ZIP) as wing_zip, zipfile.ZipFile(HATS_ZIP) as hat_zip:
    for cosmetic_cmd, namespace, name in sample_models:
        source_zip = wing_zip if namespace == "ax_wings_pack" else hat_zip
        source_root = wing_root if namespace == "ax_wings_pack" else hat_root
        model_data = json.loads(source_zip.read(f"{source_root}/models/{name}.json"))
        used_textures = {
            value.split(":", 1)[1]
            for value in model_data.get("textures", {}).values()
            if isinstance(value, str) and value.startswith(f"{namespace}:")
        }
        # Minecraft 1.21's item atlas automatically discovers textures under
        # textures/item. The commercial pack stores them at the namespace root,
        # which produces a missing-texture model when used without ItemsAdder.
        for key, value in list(model_data.get("textures", {}).items()):
            if isinstance(value, str) and value.startswith(f"{namespace}:"):
                texture_name = value.split(":", 1)[1]
                model_data["textures"][key] = f"{namespace}:item/{texture_name}"
        if namespace == "ax_wings_pack":
            # The source pack expects a cosmetics plugin to attach the model
            # to the torso. When worn directly as a Paper helmet, its original
            # Y=-60/-75 transform moves it to the player's feet. This transform
            # anchors every wing model between the shoulder blades.
            model_data.setdefault("display", {})["head"] = {
                "rotation": [0, 0, 0],
                "translation": [0, -18, 5],
                "scale": [1.15, 1.15, 1.15],
            }
        model_out = OUT / f"assets/{namespace}/models/item/{name}.json"
        model_out.parent.mkdir(parents=True, exist_ok=True)
        model_out.write_text(json.dumps(model_data, indent=2), encoding="utf-8")

        for texture_name in used_textures:
            texture_out = OUT / f"assets/{namespace}/textures/item/{texture_name}.png"
            texture_out.parent.mkdir(parents=True, exist_ok=True)
            texture_out.write_bytes(source_zip.read(f"{source_root}/textures/{texture_name}.png"))

        entries.append(
            {
                "threshold": cosmetic_cmd,
                "model": {
                    "type": "minecraft:model",
                    "model": f"{namespace}:item/{name}",
                },
            }
        )


def convert_crate(bbmodel: dict, name: str, pose: str = "closed") -> None:
    texture_refs = {}
    for index, texture in enumerate(bbmodel.get("textures", [])):
        raw_source = texture.get("source", "")
        if not raw_source.startswith("data:image/png;base64,"):
            continue
        texture_name = f"crate_{name}_{index}"
        texture_refs[str(index)] = f"merelyme:item/{texture_name}"
        (textures / f"{texture_name}.png").write_bytes(
            base64.b64decode(raw_source.split(",", 1)[1])
        )

    converted_elements = []
    # BBModel coordinates are centred around 0 and its rotations are XYZ
    # arrays. Java item models expect coordinates around 8 and one-axis
    # rotation objects. Convert both formats and normalise UVs to 0..16.
    all_coords = [v for e in bbmodel.get("elements", []) for v in e["from"] + e["to"]]
    largest = max(abs(min(all_coords)), abs(max(all_coords)), 1)
    geometry_scale = min(1.0, 15.0 / largest)
    tex_w = bbmodel.get("resolution", {}).get("width", 64)
    tex_h = bbmodel.get("resolution", {}).get("height", 64)
    groups_by_name = {g.get("name"): g for g in bbmodel.get("groups", [])}

    def group_element_ids(group_name: str) -> set[str]:
        group = groups_by_name.get(group_name)
        ids: set[str] = set()
        if not group:
            return ids

        def collect_children(nodes):
            for node in nodes:
                if isinstance(node, str):
                    ids.add(node)
                elif isinstance(node, dict):
                    collect_children(node.get("children", []))

        for node in bbmodel.get("outliner", []):
            if isinstance(node, dict) and node.get("uuid") == group.get("uuid"):
                collect_children(node.get("children", []))
            elif isinstance(node, dict):
                for child in node.get("children", []):
                    if isinstance(child, dict) and child.get("uuid") == group.get("uuid"):
                        collect_children(child.get("children", []))
        return ids

    top_group = groups_by_name.get("top")
    key_group = groups_by_name.get("key")
    top_ids = group_element_ids("top")
    key_ids = group_element_ids("key")
    lock_ids = group_element_ids("lock")

    poses = {
        # Thirteen short poses make the supplied BBModel animation feel much
        # smoother than the original seven-frame test while remaining valid
        # vanilla item JSON (element rotations are capped at 45 degrees).
        "closed": {"lid": 0, "key_scale": 0, "key_z": -6, "key_rot": 0, "lock_y": 0},
        "keyappear1": {"lid": 0, "key_scale": 0.45, "key_z": -5, "key_rot": 0, "lock_y": 0},
        "keyappear2": {"lid": 0, "key_scale": 0.9, "key_z": -2, "key_rot": 0, "lock_y": 0},
        "keyshow": {"lid": 0, "key_scale": 1.3, "key_z": 0, "key_rot": 0, "lock_y": 0},
        "keyinsert1": {"lid": 0, "key_scale": 1.15, "key_z": 4, "key_rot": 0, "lock_y": 0},
        "keyinsert2": {"lid": 0, "key_scale": 1, "key_z": 8, "key_rot": 0, "lock_y": 0},
        "keyturn1": {"lid": 0, "key_scale": 1, "key_z": 8, "key_rot": 22.5, "lock_y": 0},
        "keyturn2": {"lid": 0, "key_scale": 1, "key_z": 8, "key_rot": 45, "lock_y": 0},
        "lid1": {"lid": 22.5, "key_scale": 1, "key_z": 8, "key_rot": 45, "lock_y": -3},
        "lid2": {"lid": 45, "key_scale": 1, "key_z": 8, "key_rot": 45, "lock_y": -6},
        "open": {"lid": 45, "key_scale": 0, "key_z": 8, "key_rot": 0, "lock_y": -10},
        "drop1": {"lid": 45, "key_scale": 0, "key_z": 8, "key_rot": 0, "lock_y": -14},
        "drop2": {"lid": 45, "key_scale": 0, "key_z": 8, "key_rot": 0, "lock_y": -18},
    }
    stage = poses[pose]
    for element in bbmodel.get("elements", []):
        source_from = list(element["from"])
        source_to = list(element["to"])
        source_origin = list(element.get("origin", [0, 0, 0]))

        if element.get("uuid") in key_ids and key_group:
            key_origin = key_group.get("origin", [0, 0, 0])
            scale = stage["key_scale"]
            source_from = [key_origin[i] + (source_from[i] - key_origin[i]) * scale for i in range(3)]
            source_to = [key_origin[i] + (source_to[i] - key_origin[i]) * scale for i in range(3)]
            source_origin = [key_origin[i] + (source_origin[i] - key_origin[i]) * scale for i in range(3)]
            source_from[2] += stage["key_z"]
            source_to[2] += stage["key_z"]
            source_origin[2] += stage["key_z"]

        if element.get("uuid") in lock_ids:
            source_from[1] += stage["lock_y"]
            source_to[1] += stage["lock_y"]
            source_origin[1] += stage["lock_y"]

        converted = {
            "from": [8 + value * geometry_scale for value in source_from],
            "to": [8 + value * geometry_scale for value in source_to],
        }
        rotation = element.get("rotation") or [0, 0, 0]
        active_axes = [(axis, angle) for axis, angle in zip("xyz", rotation) if angle]
        if active_axes:
            axis, angle = active_axes[0]
            converted["rotation"] = {
                "angle": angle,
                "axis": axis,
                "origin": [8 + value * geometry_scale for value in source_origin],
            }
        if stage["lid"] and element.get("uuid") in top_ids and top_group:
            converted["rotation"] = {
                "angle": stage["lid"],
                "axis": "x",
                "origin": [8 + value * geometry_scale for value in top_group["origin"]],
            }
        if stage["key_rot"] and element.get("uuid") in key_ids and key_group:
            converted["rotation"] = {
                "angle": stage["key_rot"],
                "axis": "z",
                "origin": [8 + value * geometry_scale for value in key_group["origin"]],
            }
        faces = {}
        for direction, face in element.get("faces", {}).items():
            converted_face = {
                key: value
                for key, value in face.items()
                if key in {"rotation", "tintindex"}
            }
            if "uv" in face:
                u1, v1, u2, v2 = face["uv"]
                converted_face["uv"] = [
                    u1 * 16 / tex_w, v1 * 16 / tex_h,
                    u2 * 16 / tex_w, v2 * 16 / tex_h,
                ]
            texture_index = str(face.get("texture", 0))
            converted_face["texture"] = f"#{texture_index}"
            faces[direction] = converted_face
        converted["faces"] = faces
        converted_elements.append(converted)

    model_name = f"crate_{name}{'' if pose == 'closed' else '_' + pose}"
    (models / f"{model_name}.json").write_text(
        json.dumps(
            {
                "credit": "Artillex Studios free crate sample; opening animation pose",
                "ambientocclusion": False,
                "gui_light": "front",
                "texture_size": [
                    bbmodel.get("resolution", {}).get("width", 64),
                    bbmodel.get("resolution", {}).get("height", 64),
                ],
                "textures": {**texture_refs, "particle": texture_refs.get("0", "minecraft:block/stone")},
                "elements": converted_elements,
                "display": {
                    "gui": {"rotation": [30, 225, 0], "translation": [0, -1, 0], "scale": [0.55, 0.55, 0.55]},
                    "ground": {"scale": [0.3, 0.3, 0.3]},
                    "fixed": {"rotation": [0, 180, 0], "scale": [0.6, 0.6, 0.6]},
                },
            },
            indent=2,
        ),
        encoding="utf-8",
    )


def convert_key(bbmodel: dict, name: str) -> None:
    texture_refs = {}
    for index, texture in enumerate(bbmodel.get("textures", [])):
        raw_source = texture.get("source", "")
        if not raw_source.startswith("data:image/png;base64,"):
            continue
        texture_name = f"crate_key_{name}_{index}"
        texture_refs[str(index)] = f"merelyme:item/{texture_name}"
        (textures / f"{texture_name}.png").write_bytes(
            base64.b64decode(raw_source.split(",", 1)[1])
        )

    tex_w = bbmodel.get("resolution", {}).get("width", 16)
    tex_h = bbmodel.get("resolution", {}).get("height", 16)
    converted_elements = []
    for element in bbmodel.get("elements", []):
        converted = {
            "from": element["from"],
            "to": element["to"],
        }
        rotation = element.get("rotation") or [0, 0, 0]
        active_axes = [(axis, angle) for axis, angle in zip("xyz", rotation) if angle]
        if active_axes:
            axis, angle = active_axes[0]
            converted["rotation"] = {
                "angle": angle,
                "axis": axis,
                "origin": element.get("origin", [8, 8, 8]),
            }
        faces = {}
        for direction, face in element.get("faces", {}).items():
            converted_face = {}
            if "uv" in face:
                u1, v1, u2, v2 = face["uv"]
                converted_face["uv"] = [
                    u1 * 16 / tex_w, v1 * 16 / tex_h,
                    u2 * 16 / tex_w, v2 * 16 / tex_h,
                ]
            converted_face["texture"] = f"#{face.get('texture', 0)}"
            faces[direction] = converted_face
        converted["faces"] = faces
        converted_elements.append(converted)

    fixed = bbmodel.get("display", {}).get("fixed", {})
    (models / f"crate_key_{name}.json").write_text(
        json.dumps(
            {
                "credit": "Artillex Studios free crate key sample",
                "texture_size": [tex_w, tex_h],
                "textures": {**texture_refs, "particle": texture_refs.get("0", "minecraft:block/stone")},
                "elements": converted_elements,
                "display": {
                    "fixed": {
                        "rotation": fixed.get("rotation", [0, 0, 0]),
                        "translation": fixed.get("translation", [0, 0, 0]),
                        "scale": [1, 1, 1],
                    }
                },
            },
            indent=2,
        ),
        encoding="utf-8",
    )


with zipfile.ZipFile(CRATES_ZIP) as crate_zip:
    for crate_cmd, crate_name in ((11000, "common"), (11001, "rare"), (11002, "epic"), (11003, "legendary")):
        bbmodel = json.loads(
            crate_zip.read(
                f"crate_pack_2/crate_pack_2/.bbmodels/{crate_name}_crate.bbmodel"
            )
        )
        crate_stages = (
            "closed", "keyappear1", "keyappear2", "keyshow", "keyinsert1",
            "keyinsert2", "keyturn1", "keyturn2", "lid1", "lid2", "open",
            "drop1", "drop2",
        )
        for stage_index, stage_name in enumerate(crate_stages):
            convert_crate(bbmodel, crate_name, stage_name)
            entries.append(
                {
                    "threshold": crate_cmd + stage_index * 100,
                    "model": {
                        "type": "minecraft:model",
                        "model": f"merelyme:item/crate_{crate_name}{'' if stage_name == 'closed' else '_' + stage_name}",
                    },
                }
            )
        key_model = json.loads(
            crate_zip.read(f"crate_pack_2/crate_pack_2/keys/{crate_name}_key.bbmodel")
        )
        convert_key(key_model, crate_name)
        entries.append(
            {
                "threshold": 15000 + (crate_cmd - 11000),
                "model": {
                    "type": "minecraft:model",
                    "model": f"merelyme:item/crate_key_{crate_name}",
                },
            }
        )

with zipfile.ZipFile(MODEL_ZIP) as model_zip:
    earth_model = json.loads(model_zip.read("model_earth.bbmodel"))
    convert_crate(earth_model, "earth")
    entries.append(
        {
            "threshold": 13000,
            "model": {
                "type": "minecraft:model",
                "model": "merelyme:item/crate_earth",
            },
        }
    )
entries.sort(key=lambda entry: entry["threshold"])
item_def = OUT / "assets/minecraft/items/paper.json"
item_def.parent.mkdir(parents=True, exist_ok=True)
item_def.write_text(
    json.dumps(
        {
            "model": {
                "type": "minecraft:range_dispatch",
                "property": "minecraft:custom_model_data",
                "index": 0,
                "fallback": {
                    "type": "minecraft:model",
                    "model": "minecraft:item/paper",
                },
                "entries": entries,
            }
        },
        indent=2,
    ),
    encoding="utf-8",
)

(OUT / "README.txt").write_text(
    "MerelyMeSMP Visual Test v2\n\n"
    "Client test pack for Minecraft 1.21.11 Fabric + OptiGUI.\n"
    "Based on MerelyMeSMP-Menu-Ranks-BanHammer-v5.1-Fixed.\n"
    "The BanHammer assets are preserved.\n\n"
    "The blue GUI is limited by inventory title to Crates (including AFK), "
    "Stats, Rank Permissions, Help, Shop, Playtime, 1v1, 2v2, Tournaments, "
    "Kits, Media, Rules, Battle Pass, Update and Daily Wheel. Only the upper "
    "server-menu section is blue; the player inventory and hotbar retain the "
    "vanilla Minecraft texture. Other chest menus remain vanilla.\n\n"
    "Cosmetic samples (paper CustomModelData):\n"
    "9000 Angel Wings; 9002 BlueFire Wings; 9011 Phoenix Wings\n"
    "10000 Diving Helmet; 10003 Military Helmet; 10005 Pot Hat\n"
    "11000 Common Crate; 11001 Legendary Crate\n\n"
    "12000 Animated Earth source (static item preview)\n\n"
    "The crate samples are static item previews. Their Blockbench animations "
    "need a server model/cosmetics plugin for playback.\n",
    encoding="utf-8",
)

# Preview the exact test GUI placement without requiring a server change.
preview = gui_texture.crop((0, 0, 176, 222)).resize(
    (528, 666), Image.Resampling.NEAREST
)
draw = ImageDraw.Draw(preview)
slot_positions = [10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24]
for (rank, src), slot in zip(rank_sources.items(), slot_positions):
    row, col = divmod(slot, 9)
    item = badge_canvas(src).resize((48, 48), Image.Resampling.NEAREST)
    x = (8 + col * 18) * 3 + 3
    y = (18 + row * 18) * 3 + 3
    preview.alpha_composite(item, (x, y))

for slot, icon_cmd in zip(range(27, 49), sorted(icon_map)):
    row, col = divmod(slot, 9)
    icon_name, icon_src = icon_map[icon_cmd]
    icon = Image.open(icon_src).convert("RGBA").resize((42, 42), Image.Resampling.NEAREST)
    x = (8 + col * 18) * 3 + 6
    y = (18 + row * 18) * 3 + 6
    preview.alpha_composite(icon, (x, y))

preview.save(ROOT / "RankMenu-Test-Preview.png")

# Clean visual proof of the final composition without test items on top.
gui_texture.crop((0, 0, 176, 222)).resize(
    (528, 666), Image.Resampling.NEAREST
).save(ROOT / "Blue-Menu-Top-Only-Preview.png")

zip_path = ROOT / "MerelyMeSMP-RankMenu-Test-v1.zip"
if zip_path.exists():
    zip_path.unlink()
with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED) as archive:
    for path in sorted(OUT.rglob("*")):
        if path.is_file():
            archive.write(path, path.relative_to(OUT).as_posix())

print(zip_path)
print(ROOT / "RankMenu-Test-Preview.png")
print(ROOT / "Blue-Menu-Top-Only-Preview.png")
