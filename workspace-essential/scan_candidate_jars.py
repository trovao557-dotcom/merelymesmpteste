from pathlib import Path
from zipfile import ZipFile
import re, shutil

base = Path('.tmp-live')
out = base / 'candidate-extracted'
if out.exists():
    shutil.rmtree(out)
out.mkdir(parents=True)
patterns = re.compile(rb'InventoryClickEvent|InventoryDragEvent|COLLECT_TO_CURSOR|DOUBLE_CLICK|PlayerSwapHandItemsEvent|InventoryMoveItemEvent|EntityPickupItemEvent|PlayerDropItemEvent|setItemStack', re.I)
for jar in (base / 'candidate-jars').glob('*.jar'):
    dest = out / jar.stem
    dest.mkdir()
    with ZipFile(jar) as z:
        z.extractall(dest)
for f in out.rglob('*'):
    if not f.is_file():
        continue
    try:
        data = f.read_bytes()
    except OSError:
        continue
    if patterns.search(data):
        hits = sorted(set(m.group(0).decode(errors='ignore') for m in patterns.finditer(data)))
        print(f'{f}: {", ".join(hits)}')
