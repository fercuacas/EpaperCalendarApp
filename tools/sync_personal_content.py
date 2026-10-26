"""Copy an explicit Ink_calendar32 source catalog into the APK's portable content asset.

No USB, Wi-Fi, firmware upload or modification of the source images.
"""
import argparse
import importlib.util
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--ink-root", type=Path, required=True)
    args = parser.parse_args()
    source = args.ink_root.resolve() / "tools/generate_personal_assets.py"
    spec = importlib.util.spec_from_file_location("ink_assets", source)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    destination = Path(__file__).resolve().parents[1] / "app/src/main/assets/personal_content/catalog.json"
    module.export_android(args.ink_root.resolve(), destination)


if __name__ == "__main__":
    main()
