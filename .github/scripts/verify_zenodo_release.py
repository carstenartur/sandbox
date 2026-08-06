#!/usr/bin/env python3
"""Verify that Zenodo archived one stable release with traceable DOI evidence."""

from __future__ import annotations

import argparse
import json
import math
import re
import time
import urllib.parse
import urllib.request
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Any, Iterable

STABLE_VERSION = re.compile(r"[0-9]+\.[0-9]+\.[0-9]+")
SNAPSHOT_MARKER = re.compile(r"snapshot", re.IGNORECASE)
ZENODO_PAGE_SIZE = 25
ZENODO_MAX_PAGES = 1000


class ZenodoVerificationError(RuntimeError):
    """Raised when the expected stable Zenodo record cannot be proven."""


class ZenodoRetryError(ZenodoVerificationError):
    """Raised after all online verification attempts have failed."""

    def __init__(self, message: str, attempts: int) -> None:
        super().__init__(message)
        self.attempts = attempts


@dataclass(frozen=True)
class VerifiedRecord:
    record_id: str
    title: str
    version: str
    version_doi: str
    concept_doi: str
    record_url: str
    repository_identifier: str


def normalize_version(value: object) -> str:
    text = "" if value is None else str(value).strip()
    return text[1:] if text.startswith("v") else text


def normalize_repository_url(value: str) -> str:
    normalized = value.strip().rstrip("/")
    return normalized.removesuffix(".git")


def mappings(value: object) -> Iterable[dict[str, Any]]:
    if isinstance(value, list):
        for item in value:
            if isinstance(item, dict):
                yield item


def records(payload: object) -> list[dict[str, Any]]:
    if isinstance(payload, list):
        return [item for item in payload if isinstance(item, dict)]
    if not isinstance(payload, dict):
        return []
    hits = payload.get("hits")
    if isinstance(hits, dict):
        nested = hits.get("hits")
        if isinstance(nested, list):
            return [item for item in nested if isinstance(item, dict)]
    for key in ("records", "items"):
        nested = payload.get(key)
        if isinstance(nested, list):
            return [item for item in nested if isinstance(item, dict)]
    return [payload] if "metadata" in payload else []


def metadata(record: dict[str, Any]) -> dict[str, Any]:
    value = record.get("metadata")
    return value if isinstance(value, dict) else {}


def repository_identifiers(record: dict[str, Any]) -> list[str]:
    result: list[str] = []
    meta = metadata(record)
    for item in mappings(meta.get("related_identifiers")):
        identifier = item.get("identifier")
        if isinstance(identifier, str) and identifier.strip():
            result.append(identifier.strip())
    for item in mappings(meta.get("identifiers")):
        identifier = item.get("identifier")
        if isinstance(identifier, str) and identifier.strip():
            result.append(identifier.strip())
    return result


def doi_from(value: object) -> str:
    if isinstance(value, str):
        return value.strip()
    if isinstance(value, dict):
        for key in ("identifier", "doi"):
            nested = value.get(key)
            if isinstance(nested, str) and nested.strip():
                return nested.strip()
    return ""


def version_doi(record: dict[str, Any]) -> str:
    pids = record.get("pids")
    pid_doi = pids.get("doi") if isinstance(pids, dict) else None
    for value in (record.get("doi"), metadata(record).get("doi"), pid_doi):
        doi = doi_from(value)
        if doi:
            return doi
    return ""


def concept_doi(record: dict[str, Any]) -> str:
    for value in (record.get("conceptdoi"), record.get("concept_doi")):
        doi = doi_from(value)
        if doi:
            return doi
    parent = record.get("parent")
    if isinstance(parent, dict):
        pids = parent.get("pids")
        if isinstance(pids, dict):
            doi = doi_from(pids.get("doi"))
            if doi:
                return doi
        doi = doi_from(parent.get("doi"))
        if doi:
            return doi
    return ""


def record_url(record: dict[str, Any]) -> str:
    links = record.get("links")
    if isinstance(links, dict):
        for key in ("self_html", "html", "record", "self"):
            value = links.get(key)
            if isinstance(value, str) and value.startswith("http"):
                return value
    identifier = record.get("id")
    return f"https://zenodo.org/records/{identifier}" if identifier is not None else ""


def matching_repository_identifier(record: dict[str, Any], repository_url: str) -> str:
    expected = normalize_repository_url(repository_url)
    for identifier in repository_identifiers(record):
        normalized = normalize_repository_url(identifier)
        if normalized == expected or normalized.startswith(expected + "/"):
            return identifier
    return ""


def verify_record(
    record: dict[str, Any],
    repository_url: str,
    expected_version: str,
    expected_concept_doi: str = "",
) -> VerifiedRecord:
    meta = metadata(record)
    title = str(meta.get("title") or record.get("title") or "").strip()
    actual_version = normalize_version(meta.get("version") or record.get("version"))
    repository_identifier = matching_repository_identifier(record, repository_url)
    found_version_doi = version_doi(record)
    found_concept_doi = concept_doi(record)

    errors: list[str] = []
    if not repository_identifier:
        errors.append(f"record is not linked to {normalize_repository_url(repository_url)}")
    if actual_version != expected_version:
        errors.append(f"record version {actual_version!r} does not equal {expected_version!r}")
    if SNAPSHOT_MARKER.search(title) or SNAPSHOT_MARKER.search(actual_version):
        errors.append("record title or version contains SNAPSHOT")
    if not title:
        errors.append("record title is empty")
    if not found_version_doi:
        errors.append("record has no version DOI")
    if not found_concept_doi:
        errors.append("record has no concept DOI")
    if expected_concept_doi and found_concept_doi.lower() != expected_concept_doi.lower():
        errors.append(
            f"concept DOI {found_concept_doi!r} does not equal preserved DOI {expected_concept_doi!r}"
        )
    if errors:
        raise ZenodoVerificationError("; ".join(errors))

    return VerifiedRecord(
        record_id=str(record.get("id") or ""),
        title=title,
        version=actual_version,
        version_doi=found_version_doi,
        concept_doi=found_concept_doi,
        record_url=record_url(record),
        repository_identifier=repository_identifier,
    )


def find_verified_record(
    payload: object,
    repository_url: str,
    expected_version: str,
    expected_concept_doi: str = "",
) -> VerifiedRecord:
    failures: list[str] = []
    linked_records = 0
    for record in records(payload):
        if not matching_repository_identifier(record, repository_url):
            continue
        linked_records += 1
        try:
            return verify_record(record, repository_url, expected_version, expected_concept_doi)
        except ZenodoVerificationError as error:
            failures.append(str(error))
    if linked_records == 0:
        raise ZenodoVerificationError(
            f"Zenodo response contains no record linked to {normalize_repository_url(repository_url)}"
        )
    detail = " | ".join(failures[:5])
    raise ZenodoVerificationError(
        f"No linked Zenodo record proves stable version {expected_version!r}: {detail}"
    )


def fetch_json(url: str, timeout_seconds: float) -> object:
    request = urllib.request.Request(
        url,
        headers={
            "Accept": "application/json",
            "User-Agent": "sandbox-release-verifier/1.0",
        },
    )
    with urllib.request.urlopen(request, timeout=timeout_seconds) as response:
        return json.load(response)


def next_page_url(payload: object) -> str:
    if not isinstance(payload, dict):
        return ""
    links = payload.get("links")
    if isinstance(links, dict):
        value = links.get("next")
        if isinstance(value, str) and value.startswith("http"):
            return value
    return ""


def total_records(payload: object) -> int | None:
    if not isinstance(payload, dict):
        return None
    hits = payload.get("hits")
    if isinstance(hits, dict):
        total = hits.get("total")
        if isinstance(total, int):
            return total
        if isinstance(total, dict) and isinstance(total.get("value"), int):
            return int(total["value"])
    return None


def first_page_url(api_url: str, repository_url: str) -> str:
    query = urllib.parse.urlencode(
        {
            "q": f'"{normalize_repository_url(repository_url)}"',
            "all_versions": "true",
            "size": str(ZENODO_PAGE_SIZE),
            "page": "1",
            "sort": "-mostrecent",
        }
    )
    separator = "&" if "?" in api_url else "?"
    return api_url + separator + query


def page_url(api_url: str, repository_url: str, page: int) -> str:
    parsed = urllib.parse.urlsplit(first_page_url(api_url, repository_url))
    query = urllib.parse.parse_qs(parsed.query, keep_blank_values=True)
    query["page"] = [str(page)]
    encoded = urllib.parse.urlencode(query, doseq=True)
    return urllib.parse.urlunsplit((parsed.scheme, parsed.netloc, parsed.path, encoded, parsed.fragment))


def fetch_records(api_url: str, repository_url: str, timeout_seconds: float) -> list[dict[str, Any]]:
    collected: list[dict[str, Any]] = []
    url = first_page_url(api_url, repository_url)
    page = 1
    expected_pages: int | None = None

    while url:
        if page > ZENODO_MAX_PAGES:
            raise ZenodoVerificationError(
                f"Zenodo pagination exceeded {ZENODO_MAX_PAGES} pages"
            )
        payload = fetch_json(url, timeout_seconds)
        current = records(payload)
        collected.extend(current)

        total = total_records(payload)
        if total is not None:
            expected_pages = max(1, math.ceil(total / ZENODO_PAGE_SIZE))
        explicit_next = next_page_url(payload)
        if explicit_next:
            url = explicit_next
        elif expected_pages is not None and page < expected_pages:
            url = page_url(api_url, repository_url, page + 1)
        elif expected_pages is None and len(current) == ZENODO_PAGE_SIZE:
            url = page_url(api_url, repository_url, page + 1)
        else:
            url = ""
        page += 1

    return collected


def verify_with_retry(
    api_url: str,
    repository_url: str,
    expected_version: str,
    expected_concept_doi: str,
    max_attempts: int,
    interval_seconds: float,
    timeout_seconds: float,
) -> tuple[VerifiedRecord, int]:
    last_error: Exception | None = None
    for attempt in range(1, max_attempts + 1):
        try:
            payload = fetch_records(api_url, repository_url, timeout_seconds)
            return find_verified_record(
                payload, repository_url, expected_version, expected_concept_doi
            ), attempt
        except (OSError, ValueError, ZenodoVerificationError) as error:
            last_error = error
            if attempt < max_attempts:
                time.sleep(interval_seconds)
    raise ZenodoRetryError(
        f"Zenodo did not expose a valid stable record after {max_attempts} attempts: {last_error}",
        max_attempts,
    )


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repository-url", required=True)
    parser.add_argument("--expected-version", required=True)
    parser.add_argument("--expected-tag", required=True)
    parser.add_argument("--expected-concept-doi", default="")
    parser.add_argument("--records-json", type=Path)
    parser.add_argument("--api-url", default="https://zenodo.org/api/records")
    parser.add_argument("--max-attempts", type=int, default=20)
    parser.add_argument("--interval-seconds", type=float, default=60.0)
    parser.add_argument("--timeout-seconds", type=float, default=30.0)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    if STABLE_VERSION.fullmatch(args.expected_version) is None:
        raise ZenodoVerificationError(
            f"Expected stable version X.Y.Z, found {args.expected_version!r}"
        )
    if args.expected_tag != f"v{args.expected_version}":
        raise ZenodoVerificationError(
            f"Expected tag {args.expected_tag!r} does not match version {args.expected_version!r}"
        )
    if args.max_attempts < 1 or args.interval_seconds < 0 or args.timeout_seconds <= 0:
        raise ZenodoVerificationError("Retry and timeout values must be positive")

    attempts = 1
    failure = ""
    verified: VerifiedRecord | None = None
    try:
        if args.records_json is not None:
            payload = json.loads(args.records_json.read_text(encoding="utf-8"))
            verified = find_verified_record(
                payload,
                args.repository_url,
                args.expected_version,
                args.expected_concept_doi,
            )
        else:
            verified, attempts = verify_with_retry(
                args.api_url,
                args.repository_url,
                args.expected_version,
                args.expected_concept_doi,
                args.max_attempts,
                args.interval_seconds,
                args.timeout_seconds,
            )
    except (OSError, ValueError, ZenodoVerificationError) as error:
        failure = str(error)
        if isinstance(error, ZenodoRetryError):
            attempts = error.attempts

    report: dict[str, object] = {
        "schemaVersion": 1,
        "status": "PASS" if verified else "FAIL",
        "repositoryUrl": normalize_repository_url(args.repository_url),
        "expectedVersion": args.expected_version,
        "expectedTag": args.expected_tag,
        "expectedConceptDoi": args.expected_concept_doi,
        "attempts": attempts,
        "failure": failure,
        "record": asdict(verified) if verified else None,
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    if verified is None:
        raise ZenodoVerificationError(failure)
    print(json.dumps(report, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
