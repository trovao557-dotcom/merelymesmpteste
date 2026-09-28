from pathlib import Path
import json
import shutil
import zipfile

from PIL import Image


ROOT = Path(r"C:\Users\MerelyMe\Documents\MerelyMeSMP\rank-menu-test")
PACK = ROOT / "MerelyMeSMP-RankMenu-Test-v1"
PROFILE = Path(r"C:\Users\MerelyMe\AppData\Roaming\ModrinthApp\profiles\Just Minecraft 120.0")
MINECRAFT_JAR = PROFILE / ".fabric/remappedJars/minecraft-1.21.11-0.19.3/client-intermediary.jar"
PLAYER_GUI_PACK = PROFILE / "resourcepacks/PigPack.zip"

MENUS = [
    ("crates", 4, "\uE200"), ("afk_crate", 3, "\uE201"),
    ("stats", 3, "\uE202"), ("rank_perks", 6, "\uE203"),
    ("help", 4, "\uE204"), ("shop_3", 3, "\uE205"),
    ("shop_4", 4, "\uE206"), ("shop_5", 5, "\uE207"),
    ("playtime", 6, "\uE208"), ("one_v_one", 3, "\uE209"),
    ("two_v_two", 3, "\uE20A"), ("duel_players", 6, "\uE20B"),
    ("tournaments", 3, "\uE20C"), ("kits", 3, "\uE20D"),
    ("kit_preview", 4, "\uE20E"), ("media", 3, "\uE20F"),
    ("rules", 3, "\uE210"), ("battle_pass", 6, "\uE211"),
    ("update", 3, "\uE212"), ("daily_wheel", 5, "\uE213"),
    ("glory_shop", 5, "\uE214"), ("glory", 6, "\uE215"),
    ("afk_crate_4", 4, "\uE216"),
]

AUTO_MENU_IDS = [
    "crates", "claim", "shop", "stats", "rank_perks", "sell", "orders", "auction_house",
    "afk_crate", "vote", "update", "battle_pass", "kits", "kit_member",
    "kit_knight", "kit_warrior", "kit_macer", "kit_prime", "homes",
    "tournaments", "rtp", "playtime", "rewards", "two_v_two", "one_v_one",
]

rules_dir = PACK / "assets/minecraft/optifine/gui/container/merelyme"
rules_dir.mkdir(parents=True, exist_ok=True)
for old in rules_dir.glob("menu_*.properties"):
    old.unlink()
for old in rules_dir.glob("menu_*.png"):
    old.unlink()

overlay_dir = PACK / "assets/merelyme/textures/gui"
overlay_dir.mkdir(parents=True, exist_ok=True)
for old in overlay_dir.glob("menu_*.png"):
    old.unlink()
helper_overlay_dir = ROOT / "virtual-menu-helper/resources/assets/merelyme/textures/gui"
helper_overlay_dir.mkdir(parents=True, exist_ok=True)
for old in helper_overlay_dir.glob("menu_*.png"):
    old.unlink()

font_textures = PACK / "assets/minecraft/textures/font"
with zipfile.ZipFile(PLAYER_GUI_PACK) as player_pack:
    with player_pack.open("assets/minecraft/textures/gui/container/generic_54.png") as gui_file:
        player_gui = Image.open(gui_file).convert("RGBA").copy()

for menu_id, rows, marker in MENUS:
    painted = Image.open(font_textures / f"gui_blue_{menu_id}.png").convert("RGBA")
    top_height = 17 + rows * 18
    # Preserve the player's selected lower inventory texture. The second chest
    # draw reads from y=126, where the blue bottom rail now replaces the unused
    # Inventory-label strip without moving any player slots.
    result = player_gui.copy()
    result.paste((0, 0, 0, 0), (0, 0, 176, top_height))
    result.alpha_composite(painted.crop((0, 0, 176, top_height)), (0, 0))
    result.alpha_composite(
        painted.crop((0, top_height, 176, top_height + 8)), (0, 126)
    )
    texture_name = f"menu_{menu_id}.png"
    result.save(overlay_dir / texture_name)
    result.save(helper_overlay_dir / texture_name)

for menu_id in AUTO_MENU_IDS:
    for rows in range(1, 7):
        painted = Image.open(
            font_textures / f"gui_blue_auto_{menu_id}_{rows}.png"
        ).convert("RGBA")
        top_height = 17 + rows * 18
        result = player_gui.copy()
        result.paste((0, 0, 0, 0), (0, 0, 176, top_height))
        result.alpha_composite(painted.crop((0, 0, 176, top_height)), (0, 0))
        result.alpha_composite(
            painted.crop((0, top_height, 176, top_height + 8)), (0, 126)
        )
        texture_name = f"menu_auto_{menu_id}_{rows}.png"
        result.save(overlay_dir / texture_name)
        result.save(helper_overlay_dir / texture_name)

# Keep the original marker glyphs as a fallback. The helper hides them while it
# replaces the chest texture, but they can still paint the blue frame if a
# client has not loaded the helper yet (especially the marker-only AFK menu).
font_path = PACK / "assets/minecraft/font/default.json"
font = json.loads(font_path.read_text(encoding="utf-8"))
providers = [
    provider for provider in font.get("providers", [])
    if not (
        provider.get("type") == "bitmap"
        and "gui_menu_markers" in str(provider.get("file", ""))
    )
]
font["providers"] = providers
font_path.write_text(json.dumps(font, indent=2, ensure_ascii=False), encoding="utf-8")

# Create the distributable pack and replace the active profile copy atomically.
archive = ROOT / "MerelyMeSMP-RankMenu-Test-v1.zip"
temporary = archive.with_suffix(".zip.tmp")
with zipfile.ZipFile(temporary, "w", zipfile.ZIP_DEFLATED, compresslevel=7) as out:
    for file in PACK.rglob("*"):
        if file.is_file():
            out.write(file, file.relative_to(PACK).as_posix())
temporary.replace(archive)
installed = PROFILE / "resourcepacks/MerelyMeSMP-RankMenu-Test-v1.zip"
shutil.copy2(archive, installed)
print(
    f"Updated {len(MENUS)} marked menus and "
    f"{len(AUTO_MENU_IDS) * 6} automatic title/row variants"
)
print(installed)
