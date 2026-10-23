#!/usr/bin/env python3
"""Compares two builds of the same commit: every APK of DIR_A must be byte-identical to the one in DIR_B.

Usage: compare.py DIR_A DIR_B

For APKs that differ, the entries that differ are listed (for example libi2pd.so of one ABI), so the
cause can be found. Signature files are not compared: build unsigned APKs (no keystore.properties).
Exits with 1 when anything differs or an APK is missing on one side.
"""
import hashlib
import os
import sys
import zipfile


def apks(directory):
    return sorted(f for f in os.listdir(directory) if f.endswith(".apk"))


def sha256(path):
    with open(path, "rb") as f:
        return hashlib.sha256(f.read()).hexdigest()


def entries(path):
    with zipfile.ZipFile(path) as z:
        return {i.filename: hashlib.sha256(z.read(i)).hexdigest() for i in z.infolist()}


def main():
    a, b = sys.argv[1], sys.argv[2]
    names_a, names_b = apks(a), apks(b)
    failed = False
    if names_a != names_b:
        print(f"different APK sets:\n  {a}: {names_a}\n  {b}: {names_b}")
        failed = True
    if not names_a:
        print(f"no APKs in {a}")
        failed = True
    for name in sorted(set(names_a) & set(names_b)):
        path_a, path_b = os.path.join(a, name), os.path.join(b, name)
        if sha256(path_a) == sha256(path_b):
            print(f"identical  {name}")
            continue
        failed = True
        print(f"DIFFERENT  {name}")
        ea, eb = entries(path_a), entries(path_b)
        for entry in sorted(set(ea) | set(eb)):
            if ea.get(entry) != eb.get(entry):
                print(f"    {entry}: {'missing' if entry not in ea else ea[entry][:16]} / {'missing' if entry not in eb else eb[entry][:16]}")
        if ea == eb:
            print("    same entries: the difference is in the zip layout (order, timestamps, alignment)")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
