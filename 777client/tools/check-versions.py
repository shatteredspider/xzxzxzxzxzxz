#!/usr/bin/env python3
# Print currently-valid gradle.properties values for a target Minecraft version.
#
# Usage:  python3 tools/check-versions.py [mc_version]
#
# Queries meta.fabricmc.net and Modrinth live, so it cannot go stale the way a
# hardcoded version list in a README does. If a build ever fails on dependency
# resolution, run this first and paste the output into gradle.properties.

import json
import sys
import urllib.request


def fetch(url):
    req = urllib.request.Request(url, headers={"User-Agent": "777client-version-check"})
    return json.loads(urllib.request.urlopen(req, timeout=30).read())


def main():
    mc = sys.argv[1] if len(sys.argv) > 1 else None

    games = fetch("https://meta.fabricmc.net/v2/versions/game")
    known = [g["version"] for g in games]
    stable = [g["version"] for g in games if g["stable"]]

    if mc is None:
        mc = stable[0]
        print("# no version given, using newest stable: " + mc)
        print()
    elif mc not in known:
        print("! " + mc + " is not a version Fabric knows about.")
        print("  recent stable: " + ", ".join(stable[:10]))
        return 1

    yarn = fetch("https://meta.fabricmc.net/v2/versions/yarn/" + mc)
    loader = fetch("https://meta.fabricmc.net/v2/versions/loader")
    api = fetch(
        "https://api.modrinth.com/v2/project/fabric-api/version"
        '?game_versions=%5B%22' + mc + '%22%5D'
    )

    if not yarn:
        print("! no yarn mappings published for " + mc + " yet")
        return 1
    if not api:
        print("! no Fabric API build published for " + mc + " yet")
        return 1

    print("minecraft_version=" + mc)
    print("yarn_mappings=" + yarn[0]["version"])
    print("loader_version=" + next(l["version"] for l in loader if l["stable"]))
    print("fabric_version=" + api[0]["version_number"])
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
