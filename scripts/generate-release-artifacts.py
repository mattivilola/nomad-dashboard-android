#!/usr/bin/env python3
from __future__ import annotations

import argparse
import re
import subprocess
import sys
from collections import OrderedDict
from dataclasses import dataclass
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parent.parent
CHANGELOG_INTRO = (
    "# Changelog\n\n"
    "All notable changes to this project will be documented in this file.\n"
)
CATEGORY_ORDER = ("Added", "Improved", "Fixed", "Build & Release")
DOC_PATH_PREFIXES = ("docs/",)
DOC_FILES = {"README.md", "AGENTS.md", "CLAUDE.md"}
BUILD_RELEASE_PATH_PREFIXES = (
    ".github/",
    "scripts/",
    "gradle/",
    "Config/",
)
BUILD_RELEASE_FILES = {
    "Makefile",
    "gradlew",
    "gradlew.bat",
    "settings.gradle.kts",
    "build.gradle.kts",
    ".gitignore",
}
KNOWN_PATTERNS = (
    (
        re.compile(r"implement travel alerts and the visited world map", re.IGNORECASE),
        "Added",
        "Added travel alerts plus a visited world map with saved-place pins and yearly country shading.",
        "travel alerts plus a visited world map with saved-place pins and yearly country shading",
    ),
    (
        re.compile(r"implement secure in-app provider credential storage", re.IGNORECASE),
        "Added",
        "Added encrypted on-device storage for provider credentials entered in Settings.",
        "encrypted on-device storage for provider credentials entered in Settings",
    ),
    (
        re.compile(r"implement time tracking feature with foreground service and persistence", re.IGNORECASE),
        "Added",
        "Added local time tracking with projects, session history, and a persistent foreground timer.",
        "local time tracking with projects, session history, and a persistent foreground timer",
    ),
    (
        re.compile(r"add multi-country fuel price fetching and dashboard integration", re.IGNORECASE),
        "Added",
        "Added dashboard fuel prices across Spain, France, Italy, and Germany.",
        "dashboard fuel prices across Spain, France, Italy, and Germany",
    ),
    (
        re.compile(r"add robust visited places and country day tracking features", re.IGNORECASE),
        "Added",
        "Added visited places history with country-day aggregation.",
        "visited places history with country-day aggregation",
    ),
    (
        re.compile(r"initial android port scaffold", re.IGNORECASE),
        "Added",
        "Added the initial Android app shell with the local-first Nomad Dashboard foundation.",
        None,
    ),
    (
        re.compile(r"mark time tracking feature as implemented and enhance local run workflow", re.IGNORECASE),
        "Improved",
        "Improved the time-tracking flow and polished the local Android run workflow.",
        None,
    ),
    (
        re.compile(r"add automated ui screenshot capture for review", re.IGNORECASE),
        "Build & Release",
        "Added deterministic emulator screenshot capture for UI review.",
        None,
    ),
    (
        re.compile(r"configure emulator-first android connected test workflow", re.IGNORECASE),
        "Build & Release",
        "Configured the default connected-test workflow to target the emulator first.",
        None,
    ),
    (
        re.compile(r"configure androidx instrumentation tests for modules and update smoke test assertion", re.IGNORECASE),
        "Build & Release",
        "Improved Android instrumentation-test coverage and smoke-test reliability.",
        None,
    ),
    (
        re.compile(r"add compose ui smoke test suite and update .*make test.* script", re.IGNORECASE),
        "Build & Release",
        "Added a Compose UI smoke-test suite and tightened the repo test workflow.",
        None,
    ),
    (
        re.compile(r"add android docs and wireless install helpers", re.IGNORECASE),
        "Build & Release",
        "Added Android release and install helper tooling for local development.",
        None,
    ),
    (
        re.compile(r"add pairing code prompt for wireless adb connection", re.IGNORECASE),
        "Build & Release",
        "Improved the wireless ADB helper flow with explicit pairing-code prompts.",
        None,
    ),
)
PAST_TENSE_PREFIXES = (
    ("add ", "Added "),
    ("implement ", "Implemented "),
    ("fix ", "Fixed "),
    ("update ", "Updated "),
    ("improve ", "Improved "),
    ("enhance ", "Enhanced "),
    ("configure ", "Configured "),
    ("mark ", "Marked "),
    ("support ", "Supported "),
    ("enable ", "Enabled "),
    ("refine ", "Refined "),
    ("wire ", "Wired "),
    ("remove ", "Removed "),
)
ADDED_KEYWORDS = ("add", "implement", "support", "enable", "introduce", "create", "wire")
FIXED_KEYWORDS = ("fix", "resolve", "repair", "correct", "stabilize", "harden")
IMPROVED_KEYWORDS = ("improve", "enhance", "update", "refine", "polish", "complete")
BUILD_KEYWORDS = (
    "release",
    "publish",
    "test",
    "lint",
    "build",
    "workflow",
    "script",
    "gradle",
    "adb",
    "screenshot",
    "smoke",
)


@dataclass
class CommitRecord:
    sha: str
    subject: str
    files: list[str]


@dataclass
class ReleaseEntry:
    category: str
    changelog_text: str
    play_fragment: str | None


def git_output(*args: str) -> str:
    result = subprocess.run(
        ["git", "-C", str(REPO_ROOT), *args],
        check=True,
        capture_output=True,
        text=True,
    )
    return result.stdout.strip()


def latest_release_tag() -> str | None:
    result = subprocess.run(
        ["git", "-C", str(REPO_ROOT), "describe", "--tags", "--abbrev=0", "--match", "v*"],
        check=False,
        capture_output=True,
        text=True,
    )
    if result.returncode != 0:
        return None
    return result.stdout.strip() or None


def commits_since(tag: str | None) -> list[CommitRecord]:
    rev_range = f"{tag}..HEAD" if tag else "HEAD"
    shas = [line.strip() for line in git_output("rev-list", "--reverse", rev_range).splitlines() if line.strip()]
    commits: list[CommitRecord] = []
    for sha in shas:
        subject = git_output("show", "-s", "--format=%s", sha)
        files = [
            path
            for path in git_output("diff-tree", "--root", "--no-commit-id", "--name-only", "-r", sha).splitlines()
            if path
        ]
        commits.append(CommitRecord(sha=sha, subject=subject, files=files))
    return commits


def is_docs_only(files: list[str], subject: str) -> bool:
    if not files:
        return subject.lower().startswith("docs:")
    return all(path.startswith(DOC_PATH_PREFIXES) or path in DOC_FILES for path in files)


def is_agent_guidance_only(files: list[str]) -> bool:
    if not files:
        return False
    return all(path in {"AGENTS.md", "CLAUDE.md"} for path in files)


def is_build_release_only(files: list[str]) -> bool:
    if not files:
        return False
    return all(path.startswith(BUILD_RELEASE_PATH_PREFIXES) or path in BUILD_RELEASE_FILES for path in files)


def classify_category(subject: str, files: list[str]) -> str:
    lowered = subject.lower()
    if any(keyword in lowered for keyword in FIXED_KEYWORDS):
        return "Fixed"
    if is_build_release_only(files) or any(keyword in lowered for keyword in BUILD_KEYWORDS):
        return "Build & Release"
    if any(keyword in lowered for keyword in IMPROVED_KEYWORDS):
        return "Improved"
    if any(keyword in lowered for keyword in ADDED_KEYWORDS):
        return "Added"
    return "Improved"


def sentence_case(subject: str) -> str:
    cleaned = re.sub(r"`([^`]+)`", r"\1", subject.strip())
    cleaned = re.sub(r"\s+", " ", cleaned)
    for prefix, replacement in PAST_TENSE_PREFIXES:
        if cleaned.lower().startswith(prefix):
            cleaned = replacement + cleaned[len(prefix) :]
            break
    else:
        cleaned = cleaned[:1].upper() + cleaned[1:]

    if not cleaned.endswith("."):
        cleaned += "."
    return cleaned


def play_fragment_from_sentence(sentence: str) -> str:
    fragment = sentence.rstrip(".")
    for prefix in ("Added ", "Improved ", "Fixed ", "Implemented ", "Enhanced ", "Updated ", "Completed "):
        if fragment.startswith(prefix):
            fragment = fragment[len(prefix) :]
            break
    if fragment:
        fragment = fragment[:1].lower() + fragment[1:]
    return fragment


def classify_commit(commit: CommitRecord) -> ReleaseEntry | None:
    if is_docs_only(commit.files, commit.subject) or is_agent_guidance_only(commit.files):
        return None

    for pattern, category, changelog_text, play_fragment in KNOWN_PATTERNS:
        if pattern.search(commit.subject):
            return ReleaseEntry(category=category, changelog_text=changelog_text, play_fragment=play_fragment)

    category = classify_category(commit.subject, commit.files)
    changelog_text = sentence_case(commit.subject)
    play_fragment = None if category == "Build & Release" else play_fragment_from_sentence(changelog_text)
    return ReleaseEntry(category=category, changelog_text=changelog_text, play_fragment=play_fragment)


def ordered_unique(items: list[str]) -> list[str]:
    return list(OrderedDict((item, None) for item in items).keys())


def render_changelog(version: str, release_date: str, entries: list[ReleaseEntry], existing: str) -> str:
    grouped: dict[str, list[str]] = {category: [] for category in CATEGORY_ORDER}
    for entry in entries:
        grouped[entry.category].append(entry.changelog_text)

    sections: list[str] = [f"## [{version}] - {release_date}"]
    for category in CATEGORY_ORDER:
        category_items = ordered_unique(grouped[category])
        if not category_items:
            continue
        sections.append("")
        sections.append(f"### {category}")
        sections.extend(f"- {item}" for item in category_items)

    entry_text = "\n".join(sections).strip()
    if existing:
        existing = existing.strip()
        if f"## [{version}] -" in existing:
            raise RuntimeError(f"CHANGELOG already contains version {version}.")

        remainder = existing
        if remainder.startswith(CHANGELOG_INTRO.strip()):
            remainder = remainder[len(CHANGELOG_INTRO.strip()) :].lstrip()
        elif remainder.startswith("# Changelog"):
            remainder = remainder[len("# Changelog") :].lstrip()

        return f"{CHANGELOG_INTRO}\n{entry_text}\n\n{remainder}\n"
    return f"{CHANGELOG_INTRO}\n{entry_text}\n"


def join_fragments(prefix: str, fragments: list[str], limit: int = 500) -> str:
    chosen: list[str] = []
    for fragment in fragments:
        candidate_items = chosen + [fragment]
        if len(candidate_items) == 1:
            candidate = prefix + candidate_items[0] + "."
        elif len(candidate_items) == 2:
            candidate = prefix + f"{candidate_items[0]} and {candidate_items[1]}."
        else:
            candidate = prefix + ", ".join(candidate_items[:-1]) + f", and {candidate_items[-1]}."
        if len(candidate) <= limit:
            chosen = candidate_items
        else:
            break

    if not chosen:
        fallback = prefix + fragments[0]
        return (fallback[: limit - 1] + ".") if len(fallback) > limit else fallback + "."

    if len(chosen) == 1:
        return prefix + chosen[0] + "."
    if len(chosen) == 2:
        return prefix + f"{chosen[0]} and {chosen[1]}."
    return prefix + ", ".join(chosen[:-1]) + f", and {chosen[-1]}."


def render_play_notes(previous_tag: str | None, entries: list[ReleaseEntry]) -> str:
    fragments = ordered_unique([entry.play_fragment for entry in entries if entry.play_fragment])
    if fragments:
        prefix = "First Android release with " if previous_tag is None else "This update adds "
        return join_fragments(prefix, fragments)

    fallback = "Maintenance, reliability, and release-quality improvements."
    return fallback if len(fallback) <= 500 else fallback[:499] + "."


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Generate changelog and Play release notes from git history.")
    parser.add_argument("--version", required=True, help="Version to write into CHANGELOG.md.")
    parser.add_argument("--release-date", required=True, help="Release date in YYYY-MM-DD format.")
    parser.add_argument("--changelog", required=True, help="Path to CHANGELOG.md.")
    parser.add_argument("--play-notes", required=True, help="Path to Play release notes file.")
    parser.add_argument(
        "--since-tag",
        help="Start the release range after this git tag. Defaults to the latest reachable v* tag, if any.",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    previous_tag = args.since_tag or latest_release_tag()
    commits = commits_since(previous_tag)

    entries = [entry for commit in commits if (entry := classify_commit(commit)) is not None]
    if not entries:
        print("No releasable commits found for changelog generation.", file=sys.stderr)
        return 1

    changelog_path = Path(args.changelog)
    existing = changelog_path.read_text() if changelog_path.exists() else ""
    changelog_text = render_changelog(args.version, args.release_date, entries, existing)
    play_notes_text = render_play_notes(previous_tag, entries)

    changelog_path.write_text(changelog_text)
    play_notes_path = Path(args.play_notes)
    play_notes_path.parent.mkdir(parents=True, exist_ok=True)
    play_notes_path.write_text(play_notes_text + "\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
