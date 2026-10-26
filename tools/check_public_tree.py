"""Reject private resource paths and generated artifacts in the current Git index.

Checks paths, not file contents or history. Run before every commit/push.
"""
from pathlib import PurePosixPath
import subprocess
import sys


def is_private_or_generated(path):
    path = path.lower()
    parts = PurePosixPath(path).parts
    # Existing public launcher artwork is UI branding, not a personal photo.
    if (path.startswith("app/src/main/res/mipmap-") and
            PurePosixPath(path).name in {"ic_launcher.webp", "ic_launcher_round.webp"}):
        return False
    forbidden = {".pio", ".gradle", ".kotlin", "__pycache__", "build", "out", "private", "diagnostics"}
    if any(part in forbidden or part.startswith("build-") for part in parts):
        return True
    if path.startswith(("assets/images/", "assets/phrases/", "app/src/main/assets/personal_content/")):
        return True
    if PurePosixPath(path).name in {"love_phrases.json", "personal_content.json", "personalassets.generated.h", "catalog.json", "secrets.h", ".env"}:
        return True
    if PurePosixPath(path).name.startswith(".env.") and PurePosixPath(path).name != ".env.example":
        return True
    if PurePosixPath(path).name.startswith("ink-calendar-diagnostico"):
        return True
    return PurePosixPath(path).suffix in {".epd", ".png", ".jpg", ".jpeg", ".webp", ".heic", ".gif", ".bmp", ".ppm", ".tif", ".tiff", ".apk", ".aab", ".exe", ".bin", ".elf", ".hex", ".pdb", ".obj", ".lib", ".o", ".a", ".pyc", ".pyo", ".jks", ".keystore"}


def main():
    result = subprocess.run(["git", "ls-files", "-z"], check=True, capture_output=True)
    paths = result.stdout.decode("utf-8").split("\0")
    blocked = [path for path in paths if path and is_private_or_generated(path)]
    if blocked:
        print("NO PUBLICAR: rutas privadas o generadas en el índice Git:")
        print("\n".join(blocked))
        return 1
    print("Índice Git sin rutas de recursos privados ni artefactos generados.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
