
# adapter script (legacy reference tool)
from pathlib import Path

SOURCE = Path(__file__).resolve().parent / 'HSOSKYServerCdLootNoClipPatcher.java'
with SOURCE.open('r', encoding='utf-8') as f:
    c = f.read()
