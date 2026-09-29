#!/usr/bin/env python3
"""Git-backed single-copy review. No command writes master, merges, or commits."""
from __future__ import annotations

import argparse
from dataclasses import dataclass, replace
import hashlib
from importlib.metadata import version
import json
import os
from pathlib import Path
import re
import subprocess
import sys
from urllib.parse import quote, unquote, urlsplit, urlunsplit

from review_renderer import ReviewRenderer


class ReviewError(ValueError):
    pass


def digest(data: bytes | None) -> str | None:
    return hashlib.sha256(data).hexdigest() if data is not None else None


def json_bytes(value) -> bytes:
    return (json.dumps(value, ensure_ascii=False, indent=2, sort_keys=True) + "\n").encode("utf-8")


def read_json(path: Path):
    def unique(pairs):
        result = {}
        for key, value in pairs:
            if key in result:
                raise ReviewError(f"Duplicate JSON key: {key}")
            result[key] = value
        return result
    return json.loads(path.read_bytes(), object_pairs_hook=unique)


def relative_path(value: str) -> str:
    if not isinstance(value, str) or not value or "\\" in value or ":" in value:
        raise ReviewError(f"Expected a POSIX relative path: {value!r}")
    parts = value.split("/")
    if any(p in ("", ".", "..") or p.endswith((".", " ")) or
           any(ord(c) < 32 or c in '<>"|?*' for c in p) for p in parts):
        raise ReviewError(f"Unsafe relative path: {value!r}")
    if any(p.lower() == ".git" for p in parts):
        raise ReviewError("Paths inside .git are not document paths")
    return value


def child(root: Path, value: str) -> Path:
    path = root / relative_path(value)
    for item in (path, *path.parents):
        if item == root.parent:
            break
        if item.is_symlink() or (hasattr(item, "is_junction") and item.is_junction()):
            raise ReviewError(f"Symlink/junction is not allowed: {item}")
    if not path.resolve().is_relative_to(root.resolve()):
        raise ReviewError(f"Path escapes root: {value}")
    return path


def write_bytes(path: Path, data: bytes):
    path.parent.mkdir(parents=True, exist_ok=True)
    # check() independently rebuilds every review file after an interrupted render.
    path.write_bytes(data)


def git(repo: Path, *args: str) -> bytes:
    run = subprocess.run(["git", "-C", str(repo), *args], capture_output=True, check=False)
    if run.returncode:
        raise ReviewError(run.stderr.decode("utf-8", errors="replace").strip())
    return run.stdout


def repository(root: Path) -> Path:
    return Path(git(root, "rev-parse", "--show-toplevel").decode("utf-8").strip()).resolve()


def git_bytes(repo: Path, commit: str, path: str) -> bytes:
    if not isinstance(commit, str) or not re.fullmatch(r"[0-9a-f]{40}|[0-9a-f]{64}", commit):
        raise ReviewError("History requires a full immutable commit SHA, not a branch name")
    relative_path(path)
    if git(repo, "cat-file", "-t", commit).strip() != b"commit":
        raise ReviewError("History revision must be a commit")
    records = git(repo, "ls-tree", "-z", commit, "--", path).split(b"\0")
    expected = path.encode("utf-8")
    found = [entry.split(b"\t", 1)[0].split() for entry in records
             if b"\t" in entry and entry.split(b"\t", 1)[1] == expected]
    if len(found) != 1 or found[0][0] not in (b"100644", b"100755") or found[0][1] != b"blob":
        raise ReviewError(f"History document is missing or is not a regular file: {path}")
    return git(repo, "cat-file", "blob", found[0][2].decode("ascii"))


def source(repo: Path, revision: str, path: str) -> dict:
    if not revision or revision.startswith("-"):
        raise ReviewError("Invalid revision")
    commit = git(repo, "rev-parse", "--verify", "--end-of-options", revision + "^{commit}").decode().strip()
    data = git_bytes(repo, commit, path)
    return {"commit": commit, "path": path, "sha256": digest(data)}


def read_source(repo: Path, ref: dict | None) -> bytes | None:
    if ref is None:
        return None
    if not isinstance(ref, dict) or set(ref) != {"commit", "path", "sha256"}:
        raise ReviewError("History source must contain commit, path, sha256")
    data = git_bytes(repo, ref["commit"], ref["path"])
    if digest(data) != ref["sha256"]:
        raise ReviewError(f"History hash mismatch: {ref['path']}")
    return data


def change_header(data: bytes) -> tuple[str | None, str]:
    """Only the initial metadata block contains a lifecycle status, not body quotes."""
    lines = data.decode("utf-8-sig").splitlines(keepends=True)
    content, status = [], None
    header, title_seen = True, False
    for line in lines:
        if header and not title_seen and line.startswith("# "):
            title_seen = True
        elif header and line.startswith(">"):
            match = re.match(r"^> \*\*Статус\*\*:\s*(.*?)\s*$", line)
            if match:
                if status is not None:
                    raise ReviewError("Duplicate lifecycle status in change header")
                status = match[1]
                continue
        elif line.strip():
            header = False
        content.append(line)
    return status, "".join(content).replace("\r\n", "\n")


def requirements_hash(data: bytes) -> str:
    # Metadata status and checkout EOL do not change the approved requirements.
    _, content = change_header(data)
    return digest(content.encode("utf-8"))


def scope_hash(manifest: dict) -> str:
    fields = ("id", "path", "operation", "change_ids", "base")
    scope = {"master_root": manifest["master_root"],
             "documents": [{key: doc[key] for key in fields} for doc in manifest["documents"]]}
    return digest(json_bytes(scope))


def intent_documents_hash(documents: list[dict]) -> str:
    return digest(json_bytes([{key: d.get(key) for key in ("id", "base", "approved")}
                              for d in documents]))


@dataclass
class Context:
    root: Path
    change: Path
    copies: Path
    repo: Path
    master: Path
    manifest: dict


def context(root: Path, change: str) -> Context:
    root = root.resolve()
    change_dir = child(root, change)
    copies = child(change_dir, ".spec-copy")
    manifest = read_json(child(copies, "manifest.json"))
    if manifest.get("schema_version") != 3 or manifest.get("apply_policy") != "ai-strict":
        raise ReviewError("Requires manifest schema v3 / ai-strict; migrate legacy copies first")
    if manifest.get("review_status") not in ("draft", "ready", "conflict"):
        raise ReviewError("review_status must be draft, ready or conflict")
    master = child(root, manifest["master_root"])
    if copies.is_relative_to(master) or master.is_relative_to(change_dir):
        raise ReviewError("Master and change must have separate roots")
    documents = manifest.get("documents")
    if not isinstance(documents, list) or not documents:
        raise ReviewError("Manifest must list documents")
    seen, ids = set(), set()
    for doc in documents:
        path = relative_path(doc["path"])
        if not path.endswith(".md") or path.casefold() in seen or doc["id"] in ids:
            raise ReviewError(f"Duplicate or unsupported document: {path}")
        seen.add(path.casefold())
        ids.add(doc["id"])
        if not doc.get("change_ids") or not all(re.fullmatch(r"CH-[A-Za-z0-9-]+", v) for v in doc["change_ids"]):
            raise ReviewError(f"Missing/invalid CH-ID: {path}")
        if doc.get("operation") not in ("modify", "add", "delete"):
            raise ReviewError("Rename must be an explicit delete/add pair")
        if "base" not in doc or "review_base" not in doc:
            raise ReviewError("base and review_base must be explicit (null means absent)")
        if (doc["operation"] == "add") != (doc["base"] is None):
            raise ReviewError(f"Invalid original baseline for {doc['operation']}: {path}")
        child(master, path)
        child(copies, "content/" + path)
        child(copies, "review/" + path)
    return Context(root, change_dir, copies, repository(root), master, manifest)


def content_bytes(ctx: Context, doc: dict) -> bytes | None:
    path = child(ctx.copies, "content/" + doc["path"])
    if doc["operation"] == "delete":
        if path.exists():
            raise ReviewError(f"Delete must not have a content file: {path}")
        return None
    return path.read_bytes()


def actual_bytes(ctx: Context, doc: dict) -> bytes | None:
    path = child(ctx.master, doc["path"])
    return path.read_bytes() if path.exists() else None


def check_change(ctx: Context):
    saved = ctx.manifest.get("change_sha256")
    if not saved:
        raise ReviewError("Missing change_sha256 in manifest: save it when preparing content")
    current = requirements_hash(child(ctx.change, "change.md").read_bytes())
    if saved != current:
        raise ReviewError("change.md changed since content was prepared; update content before continuing "
                          f"(saved={saved}, current={current})")


def renderer_hash() -> str:
    here = Path(__file__).resolve().parent
    hashes = {name: digest((here / name).read_bytes().replace(b"\r\n", b"\n"))
              for name in ("spec_review.py", "review_renderer.py", "requirements.txt")}
    hashes["packages"] = {name: version(name) for name in ("markdown-it-py", "mdurl", "regex")}
    return digest(json_bytes(hashes))


def link_resolver(ctx: Context, doc: dict):
    origin = child(ctx.master, doc["path"]).parent
    review_parent = child(ctx.copies, "review/" + doc["path"]).parent
    changed = {child(ctx.master, d["path"]).resolve(): child(ctx.copies, "review/" + d["path"])
               for d in ctx.manifest["documents"]}

    def resolve(url: str) -> str:
        parts = urlsplit(url)
        if parts.scheme or parts.netloc or not parts.path or parts.path.startswith("/"):
            return url
        target = (origin / unquote(parts.path)).resolve()
        destination = changed.get(target, target)
        relative = os.path.relpath(destination, review_parent).replace(os.sep, "/")
        return urlunsplit(("", "", quote(relative, safe="/-._~"), parts.query, parts.fragment))
    return resolve


def build(ctx: Context) -> tuple[dict[str, bytes], dict]:
    check_change(ctx)
    output: dict[str, bytes] = {}
    # Approval records the fingerprint; including it would make that fingerprint circular.
    inputs = {key: value for key, value in ctx.manifest.items() if key != "review_approval"}
    metadata = {"schema_version": 3, "renderer_sha256": renderer_hash(),
                "manifest_sha256": digest(json_bytes(inputs)),
                "requirements_sha256": requirements_hash(child(ctx.change, "change.md").read_bytes()),
                "documents": []}
    for doc in ctx.manifest["documents"]:
        read_source(ctx.repo, doc["base"])
        if "approved" in doc:
            read_source(ctx.repo, doc["approved"])
        before = read_source(ctx.repo, doc["review_base"])
        after = content_bytes(ctx, doc)
        if before is None and after is None:
            raise ReviewError(f"Both sides absent: {doc['path']}")
        renderer = ReviewRenderer(link_resolver(ctx, doc))
        review = renderer.document(before, after, ctx.manifest["review_status"])
        path = "review/" + doc["path"]
        output[path] = review
        metadata["documents"].append({"path": doc["path"], "change_ids": doc["change_ids"],
                                      "before_sha256": digest(before), "content_sha256": digest(after),
                                      "review_sha256": digest(review)})
    metadata["fingerprint"] = digest(json_bytes(metadata))
    return output, metadata


def render(ctx: Context, *, save_change_hash: bool = False) -> dict:
    if save_change_hash:
        ctx = replace(ctx, manifest={**ctx.manifest, "change_sha256":
                      requirements_hash(child(ctx.change, "change.md").read_bytes())})
    output, metadata = build(ctx)
    # Parse/validate the whole set before writing even the first derived document.
    if save_change_hash:
        write_bytes(child(ctx.copies, "manifest.json"), json_bytes(ctx.manifest))
    for name, data in output.items():
        write_bytes(child(ctx.copies, name), data)
    return metadata


def check(ctx: Context, *, ready: bool = False, result: bool = False) -> dict:
    output, metadata = build(ctx)
    for name, data in output.items():
        if child(ctx.copies, name).read_bytes() != data:
            raise ReviewError(f"Generated presentation was edited or is stale: {name}")
    if ready:
        status, _ = change_header(child(ctx.change, "change.md").read_bytes())
        if status not in ("Согласовано", "В реализации"):
            raise ReviewError("Change lifecycle does not permit application")
        state_path = child(ctx.copies, "application-state.json")
        if state_path.exists() and read_json(state_path).get("status") in (
            "in-progress", "needs-recovery", "blocked"
        ):
            raise ReviewError("Previous application is blocked or needs recovery")
        if ctx.manifest["review_status"] != "ready":
            raise ReviewError("Draft/conflict review cannot be applied")
        intent = ctx.manifest.get("intent_approval")
        if not intent or intent["scope_sha256"] != scope_hash(ctx.manifest):
            raise ReviewError("Scope differs from the pinned intent")
        if intent.get("documents_sha256") != intent_documents_hash(ctx.manifest["documents"]):
            raise ReviewError("Approved history differs from the pinned intent")
        if intent["requirements_sha256"] != metadata["requirements_sha256"]:
            raise ReviewError("Requirements differ from the pinned intent")
        approved_change = read_source(ctx.repo, intent["change"])
        if requirements_hash(approved_change) != intent["requirements_sha256"]:
            raise ReviewError("Pinned requirements do not match the approval")
        approval = ctx.manifest.get("review_approval")
        if (not isinstance(approval, dict) or approval.get("fingerprint") != metadata["fingerprint"] or
                not isinstance(approval.get("evidence"), str) or not approval["evidence"].strip()):
            raise ReviewError("The current review has not been acknowledged")
        for doc in ctx.manifest["documents"]:
            if "approved" not in doc or ((doc["approved"] is None) != (doc["operation"] == "delete")):
                raise ReviewError(f"Missing approved intent: {doc['path']}")
            if doc["operation"] == "add" and doc["review_base"] is not None:
                raise ReviewError(f"Add target already exists: {doc['path']}")
            if actual_bytes(ctx, doc) != read_source(ctx.repo, doc["review_base"]):
                raise ReviewError(f"Master changed since review: {doc['path']}")
    if result:
        for doc in ctx.manifest["documents"]:
            if actual_bytes(ctx, doc) != content_bytes(ctx, doc):
                raise ReviewError(f"Master does not match the reviewed result: {doc['path']}")
    return metadata


def pin_intent(ctx: Context, revision: str) -> dict:
    """Record the already approved committed intent, not an approval decision."""
    check_change(ctx)
    docs = []
    for doc in ctx.manifest["documents"]:
        if doc["operation"] == "delete":
            ref = None
        else:
            path = child(ctx.copies, "content/" + doc["path"]).relative_to(ctx.repo).as_posix()
            ref = source(ctx.repo, revision, path)
        if read_source(ctx.repo, ref) != content_bytes(ctx, doc):
            raise ReviewError(f"Commit differs from working content: {doc['path']}")
        docs.append({**doc, "approved": ref})
    change_ref = source(ctx.repo, revision, child(ctx.change, "change.md").relative_to(ctx.repo).as_posix())
    approved_hash = requirements_hash(read_source(ctx.repo, change_ref))
    if approved_hash != requirements_hash(child(ctx.change, "change.md").read_bytes()):
        raise ReviewError("Commit differs from working requirements")
    manifest = {**ctx.manifest, "documents": docs, "intent_approval": {
        "change": change_ref, "requirements_sha256": approved_hash,
        "documents_sha256": intent_documents_hash(docs),
        "scope_sha256": scope_hash(ctx.manifest)}}
    write_bytes(child(ctx.copies, "manifest.json"), json_bytes(manifest))
    return manifest["intent_approval"]


def acknowledge(ctx: Context, evidence: str) -> dict:
    if not evidence.strip():
        raise ReviewError("Reference the user's actual review decision in --evidence")
    metadata = check(ctx)
    if ctx.manifest["review_status"] != "ready":
        raise ReviewError("A draft/conflict has no final result to acknowledge")
    record = {"fingerprint": metadata["fingerprint"], "evidence": evidence}
    write_bytes(child(ctx.copies, "manifest.json"), json_bytes({**ctx.manifest, "review_approval": record}))
    return record


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path.cwd(), help="Project root; Git root may be an ancestor")
    sub = parser.add_subparsers(dest="command", required=True)
    history = sub.add_parser("source", help="Print an immutable Git source descriptor")
    history.add_argument("--revision", required=True)
    history.add_argument("--path", required=True, help="Path relative to Git repository root")
    for name in ("render", "check", "pin-intent", "acknowledge"):
        command = sub.add_parser(name)
        command.add_argument("change", help="Change directory relative to --root")
        if name == "check":
            modes = command.add_mutually_exclusive_group()
            modes.add_argument("--ready", action="store_true", help="Check intent, review acknowledgement and unchanged master")
            modes.add_argument("--result", action="store_true", help="Compare master with reviewed content after application")
        elif name == "pin-intent":
            command.add_argument("--revision", required=True)
        elif name == "acknowledge":
            command.add_argument("--evidence", required=True)
        elif name == "render":
            command.add_argument("--save-change-hash", action="store_true",
                                 help="Save the change.md checksum after creating/updating content")
    args = parser.parse_args(argv)
    try:
        if args.command == "source":
            response = source(repository(args.root), args.revision, args.path)
        else:
            ctx = context(args.root, args.change)
            if args.command == "render":
                response = render(ctx, save_change_hash=args.save_change_hash)
            elif args.command == "check":
                response = check(ctx, ready=args.ready, result=args.result)
            elif args.command == "pin-intent":
                response = pin_intent(ctx, args.revision)
            else:
                response = acknowledge(ctx, args.evidence)
        print(json.dumps({"ok": True, "command": args.command,
                          "result": response}, ensure_ascii=False, indent=2))
        return 0
    except (ValueError, OSError, KeyError, TypeError) as exc:
        print(f"spec-review: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
