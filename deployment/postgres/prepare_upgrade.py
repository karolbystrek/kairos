"""Prepare exact migration objects/checksums for the reviewed operator upgrade."""
import argparse
from pathlib import Path
import re
import zlib


def checksum(source):
    value = zlib.crc32("".join(source.lstrip("\ufeff").splitlines()).encode("utf-8"))
    return value if value < 2**31 else value - 2**32


def prepare(migration, old_migration, output, runtime):
    if not re.fullmatch(r"[a-z][a-z0-9_]{0,62}", runtime):
        raise ValueError("runtime role must be a lowercase identifier")
    source = migration.read_text()
    tail = "-- Ownership remains normalized;" + source.split("-- Ownership remains normalized;", 1)[1]
    output.mkdir(parents=True, exist_ok=False)
    (output / "rls_objects.sql").write_text(tail.replace("${runtimeUser}", runtime))
    old = checksum(old_migration.read_text())
    new = checksum(source)
    (output / "checksums.txt").write_text(f"oldChecksum={old}\nnewChecksum={new}\n")
    return old, new


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("migration", type=Path)
    parser.add_argument("old_migration", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--runtime", required=True)
    args = parser.parse_args()
    print(prepare(args.migration, args.old_migration, args.output, args.runtime))
