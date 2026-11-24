import argparse
import json
import re
import sys
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description="Prepare the Vendroid GitHub release assets.")
    parser.add_argument("tag")
    parser.add_argument("apk_dir", type=Path)
    args = parser.parse_args()

    if not re.fullmatch(r"v[0-9][A-Za-z0-9._-]*", args.tag):
        parser.error("expected a version tag such as v1.1.0")

    apk = args.apk_dir / "app-release.apk"
    metadata = json.loads((args.apk_dir / "output-metadata.json").read_text(encoding="utf-8"))
    output = next((entry for entry in metadata["elements"] if entry["outputFile"] == apk.name), {})
    version_code = output.get("versionCode")
    version_name = output.get("versionName")

    if type(version_code) is not int or not 0 < version_code <= 2_147_483_647:
        parser.error(f"{apk.name} must have a positive 32-bit integer versionCode")
    if not isinstance(version_name, str) or not version_name.strip():
        parser.error(f"{apk.name} must have a non-empty versionName")

    changelog = json.load(sys.stdin).get("body")
    if not isinstance(changelog, str):
        parser.error("GitHub did not return release notes")

    manifest = {
        "tag": args.tag,
        "versionCode": version_code,
        "versionName": version_name,
        "changelog": changelog,
    }
    apk.rename(f"Vendroid-{args.tag}.apk")
    Path("changelog.md").write_text(changelog, encoding="utf-8")
    Path("latest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
