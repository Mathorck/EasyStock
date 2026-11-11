#!/usr/bin/env python3
"""Print a markdown coverage table from a Kover (JaCoCo format) XML report."""
import sys
import xml.etree.ElementTree as ET


def counters(element):
    # Direct children only: nested elements have their own counters
    return {c.get("type"): (int(c.get("covered")), int(c.get("missed")))
            for c in element.findall("counter")}


def percent(counts, kind):
    covered, missed = counts.get(kind, (0, 0))
    total = covered + missed
    if total == 0:
        return "n/a"
    return f"{100 * covered / total:.1f}% ({covered}/{total})"


root = ET.parse(sys.argv[1]).getroot()
total = counters(root)

print("## Test coverage")
print()
print("Host tests only (`commonTest` + `androidHostTest`). "
      "Device tests (`androidDeviceTest`) need an emulator and are not measured.")
print()
print("| Metric | Coverage |")
print("|---|---|")
print(f"| Lines | {percent(total, 'LINE')} |")
print(f"| Branches | {percent(total, 'BRANCH')} |")
print()
print("<details><summary>Per package</summary>")
print()
print("| Package | Lines | Branches |")
print("|---|---|---|")
for pkg in sorted(root.findall("package"), key=lambda p: p.get("name")):
    c = counters(pkg)
    name = pkg.get("name").replace("/", ".") or "(default)"
    print(f"| `{name}` | {percent(c, 'LINE')} | {percent(c, 'BRANCH')} |")
print()
print("</details>")
