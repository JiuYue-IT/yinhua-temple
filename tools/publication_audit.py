"""Check Git-visible files and existing history without printing credentials."""
import json
import re
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PATTERNS = {
    "api_token": re.compile(rb"\bsk-[A-Za-z0-9_-]{20,}"),
    "github_token": re.compile(rb"\b(?:gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{40,})"),
    "private_key": re.compile(rb"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----"),
}


def git(*args):
    return subprocess.check_output(["git", *args], cwd=ROOT)


def main():
    known = []
    for local in (ROOT / ".env", ROOT / "server/.env"):
        if local.is_file():
            for line in local.read_text(encoding="utf-8-sig").splitlines():
                key, sep, value = line.partition("=")
                value = value.strip().strip('"\'')
                if sep and re.search(r"(?:API_KEY|TOKEN|SECRET|PASSWORD)$", key.strip(), re.I) and len(value) >= 16:
                    known.append(value.encode())
    def findings(data):
        types = [name for name, pattern in PATTERNS.items() if pattern.search(data)]
        if any(value in data for value in known):
            types.append("matches_local_credential")
        return types

    paths = sorted(set(git("ls-files", "-z", "--cached", "--others", "--exclude-standard").decode().strip("\0").split("\0")))
    issues, largest = [], []
    total = 0
    for relative in paths:
        if not relative:
            continue
        path = ROOT / relative
        if not path.is_file():
            continue
        size = path.stat().st_size
        total += size
        largest.append((size, relative))
        if path.name == ".env" or (path.name.startswith(".env.") and path.name != ".env.example"):
            issues.append({"path": relative, "types": ["environment_file_is_git_visible"]})
        hits = findings(path.read_bytes())
        if hits:
            issues.append({"path": relative, "types": hits})
    objects = git("rev-list", "--objects", "--all").decode().splitlines()
    history = []
    batch_input = "\n".join(row.split(" ", 1)[0] for row in objects).encode() + b"\n"
    batch = subprocess.run(["git", "cat-file", "--batch"], input=batch_input, stdout=subprocess.PIPE, check=True, cwd=ROOT).stdout
    offset = 0
    for row in objects:
        line_end = batch.index(b"\n", offset)
        header = batch[offset:line_end].decode().split()
        size = int(header[2])
        data = batch[line_end + 1:line_end + 1 + size]
        offset = line_end + 1 + size + 1
        if header[1] != "blob":
            continue
        hits = findings(data)
        label = row.partition(" ")[2]
        if label == ".env" or label.endswith("/.env"):
            hits.append("environment_file_in_history")
        if hits:
            history.append({"object": header[0], "path": label, "types": hits})
    result = {
        "gitVisibleFiles": len(paths), "gitVisibleMiB": round(total / 1024 / 1024, 2),
        "credentialFindings": issues, "historyCredentialFindings": history,
        "largestFiles": [{"path": path, "MiB": round(size / 1024 / 1024, 2)} for size, path in sorted(largest, reverse=True)[:10]],
        "overGitHub100MiB": [path for size, path in largest if size >= 100 * 1024 * 1024],
    }
    print(json.dumps(result, ensure_ascii=False, indent=2))
    return 1 if issues or history or result["overGitHub100MiB"] else 0


if __name__ == "__main__":
    import sys
    sys.stdout.reconfigure(encoding="utf-8")
    raise SystemExit(main())
