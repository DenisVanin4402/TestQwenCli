"""Observable review integrity and rendering tests; isolated Git repositories only."""
from copy import deepcopy
from contextlib import redirect_stderr, redirect_stdout
from html.parser import HTMLParser
from io import StringIO
from pathlib import Path
import subprocess
import tempfile
import unittest

from review_renderer import ReviewRenderer, changed_ranges
import spec_review as sr


class HtmlStructure(HTMLParser):
    def __init__(self):
        super().__init__()
        self.stack = []
        self.errors = []
        self.text = []

    def handle_starttag(self, tag, attrs):
        parent = self.stack[-1] if self.stack else None
        if parent in ("table", "thead", "tbody", "tr", "ul", "ol"):
            allowed = {"table": ("thead", "tbody"), "thead": ("tr",), "tbody": ("tr",),
                       "tr": ("td", "th"), "ul": ("li",), "ol": ("li",)}[parent]
            if tag not in allowed:
                self.errors.append((parent, tag))
        if tag not in ("img", "br", "hr", "input"):
            self.stack.append(tag)

    def handle_endtag(self, tag):
        if not self.stack or self.stack.pop() != tag:
            self.errors.append(("unbalanced", tag))

    def handle_data(self, data):
        self.text.append(data)


class RendererTests(unittest.TestCase):
    def render(self, before, after):
        result = ReviewRenderer().document(before.encode(), after.encode(), "ready").decode()
        parsed = HtmlStructure()
        parsed.feed(result)
        self.assertEqual([], parsed.errors)
        self.assertEqual([], parsed.stack)
        return result, "".join(parsed.text)

    def test_operator_and_entities(self):
        html, text = self.render("# Скидка\n\nСумма > 10000 & лимит.\n",
                                 "# Скидка\n\nСумма >= 10000 & лимит.\n")
        self.assertIn("Сумма >= 10000 & лимит.", text)
        self.assertIn('font-weight:700;">=</span>', html)
        self.assertNotIn('font-weight:700;">&gt;', html)
        self.assertEqual(1, html.count('<h1 id="скидка">'))

    def test_graphemes(self):
        old, new = "a e\u0301 👩‍💻", "a e\u0300 👩‍🔬"
        a, b = changed_ranges(old, new)
        self.assertTrue({2, 3}.issubset(a))
        self.assertTrue({2, 3}.issubset(b))
        self.assertTrue(set(range(5, len(old))).issubset(a))

    def test_disjoint_changes_within_a_word_do_not_mark_the_middle(self):
        a, b = changed_ranges("a0b0c", "a5b5c")
        self.assertEqual(({1, 3}, {1, 3}), (a, b))
        self.assertEqual(({1}, {1}), changed_ranges("10000", "15000"))

    def test_tables_keep_shape_and_unchanged_rows(self):
        before = "| Rule | Limit |\n|---|---:|\n| A | 1000 |\n| B | 77 |\n"
        html, text = self.render(before, before.replace("1000", "1500"))
        self.assertEqual(1, html.count("<table>"))
        self.assertEqual(1, text.count("77"))
        self.assertIn('font-weight:700;">5</span>', html)
        self.assertIn('1<span style="background-color:#fecdca;color:#912018;font-weight:700;">0</span>00', html)

    def test_unchanged_soft_line_is_not_repeated_or_marked_as_deleted(self):
        old = "Сумма > 10000.\nИначе скидки нет.\n"
        html, text = self.render(old, old.replace(">", ">="))
        self.assertEqual(1, text.count("Иначе скидки нет."))
        self.assertIn("<p>Иначе скидки нет.</p>", html)

    def test_multiline_emphasis_remains_balanced(self):
        html, text = self.render("**first\nsecond** paragraph.\n", "**first\nnew second** paragraph.\n")
        self.assertIn("new second", text)
        self.assertIn("<strong>", html)

    def test_first_table_row_and_nested_list_additions(self):
        self.render("| A |\n|---|\n", "| A |\n|---|\n| added |\n")
        html, text = self.render("- one\n  - child\n", "- one\n  - child\n- two\n")
        self.assertIn("two", text)
        self.assertIn("<li style=", html)

    def test_code_and_heading_changes(self):
        html, text = self.render("## Old\n\n```python\nif a > 1:\n    run()\n```\n",
                                 "## New\n\n```python\nif a >= 1:\n    run()\n```\n")
        self.assertIn('<h2 id="new"', html)
        self.assertIn("if a >= 1:", text)
        self.assertIn("    run()", text)
        self.assertIn("<pre><code>", html)

    def test_changed_link_destination_is_visible(self):
        html, text = self.render("[API](old.md)\n", "[API](new.md)\n")
        self.assertIn('href="new.md"', html)
        self.assertIn("old.md", text)
        self.assertIn("new.md", text)
        _, text = self.render("| API |\n|---|\n|[doc](old.md)|\n",
                              "| API |\n|---|\n|[doc](new.md)|\n")
        self.assertIn("old.md", text)
        self.assertIn("new.md", text)

    def test_unsupported_html_fails_instead_of_misrepresenting(self):
        for value in ("<script>alert(1)</script>", "Text <b>value</b>"):
            with self.assertRaises(ValueError):
                ReviewRenderer().document(b"", value.encode(), "ready")

    def test_crlf_only_change_is_visible(self):
        html = ReviewRenderer().document(b"# A\r\n", b"# A\n", "ready").decode()
        self.assertIn("исходное оформление", html)


class WorkflowTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="spec-review-test-")
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.git("init", "--quiet")
        self.git("config", "user.name", "Review tests")
        self.git("config", "user.email", "review@example.invalid")
        self.git("config", "core.autocrlf", "false")
        self.change_name = "openspec/changes/boundary"
        self.change = self.root / self.change_name
        self.copies = self.change / ".spec-copy"
        self.master = self.root / "openspec/orders/workflow/discount.md"
        self.original = "# Скидка\n\nСумма > 10000.\n\nЛимит 1000.\n".encode()
        self.proposed = self.original.replace(b"> 10000", b">= 10000")
        self.put(self.master, self.original)
        self.put(self.change / "change.md", "# Change\n\n> **Статус**: Согласовано\n\nCH-01: включить границу.\n".encode())
        self.commit()
        ref = sr.source(self.root, "HEAD", self.master.relative_to(self.root).as_posix())
        self.put(self.copies / "content/workflow/discount.md", self.proposed)
        self.manifest = {"schema_version": 3, "apply_policy": "ai-strict", "review_status": "ready",
                         "change_sha256": sr.requirements_hash((self.change / "change.md").read_bytes()),
                         "master_root": "openspec/orders", "documents": [{"id": "D001",
                         "path": "workflow/discount.md", "operation": "modify", "change_ids": ["CH-01"],
                         "base": ref, "review_base": ref}]}
        self.save()
        self.commit()
        sr.pin_intent(self.ctx(), "HEAD")
        self.manifest = sr.read_json(self.copies / "manifest.json")

    def git(self, *args):
        result = subprocess.run(["git", "-C", str(self.root), *args], capture_output=True)
        if result.returncode:
            self.fail(result.stderr.decode(errors="replace"))
        return result.stdout

    def commit(self):
        self.git("add", ".")
        self.git("commit", "--quiet", "-m", "fixture")

    def put(self, path, data):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)

    def save(self):
        self.put(self.copies / "manifest.json", sr.json_bytes(self.manifest))

    def ctx(self):
        return sr.context(self.root, self.change_name)

    def command(self, name, *args):
        stdout, stderr = StringIO(), StringIO()
        with redirect_stdout(stdout), redirect_stderr(stderr):
            code = sr.main(["--root", str(self.root), name, self.change_name, *args])
        return code, stdout.getvalue(), stderr.getvalue()

    def review(self):
        sr.render(self.ctx())
        sr.acknowledge(self.ctx(), "Isolated test fixture: review acknowledged")

    def test_ready_is_read_only_and_result_is_exact(self):
        self.review()
        sr.check(self.ctx(), ready=True)
        self.assertEqual(self.original, self.master.read_bytes())
        self.master.write_bytes(self.proposed)
        sr.check(self.ctx(), result=True)
        with self.assertRaisesRegex(sr.ReviewError, "Master changed"):
            sr.check(self.ctx(), ready=True)

    def test_changed_content_invalidates_review_and_acknowledgement(self):
        self.review()
        self.put(self.copies / "content/workflow/discount.md", self.proposed + b"\nExtra\n")
        with self.assertRaisesRegex(sr.ReviewError, "stale"):
            sr.check(self.ctx())
        sr.render(self.ctx())
        with self.assertRaisesRegex(sr.ReviewError, "acknowledged"):
            sr.check(self.ctx(), ready=True)

    def test_generated_view_cannot_be_edited_even_with_approval_in_manifest(self):
        self.review()
        path = self.copies / "review/workflow/discount.md"
        path.write_bytes(path.read_bytes().replace(b"1000", b"9999"))
        self.assertIn("review_approval", self.ctx().manifest)
        with self.assertRaises(sr.ReviewError):
            sr.check(self.ctx())

    def test_parallel_release_is_preserved_in_the_only_content_file(self):
        current = self.original.replace(b"1000.", b"1500.")
        self.master.write_bytes(current)
        self.commit()
        doc = self.manifest["documents"][0]
        original_intent = deepcopy(doc["approved"])
        doc["review_base"] = sr.source(self.root, "HEAD", self.master.relative_to(self.root).as_posix())
        self.put(self.copies / "content/workflow/discount.md", current.replace(b"> 10000", b">= 10000"))
        self.save()
        self.review()
        sr.check(self.ctx(), ready=True)
        view = (self.copies / "review/workflow/discount.md").read_text(encoding="utf-8")
        self.assertIn("Лимит 1500.", view)
        self.assertNotIn("Лимит 1000.", view)
        self.assertEqual(original_intent, self.ctx().manifest["documents"][0]["approved"])
        self.assertIn(b"1000.", sr.read_source(self.root, original_intent))

    def test_master_movement_blocks_even_when_review_files_are_valid(self):
        self.review()
        self.master.write_bytes(self.original.replace(b"10000", b"15000"))
        sr.check(self.ctx())
        with self.assertRaisesRegex(sr.ReviewError, "Master changed"):
            sr.check(self.ctx(), ready=True)

    def test_recovery_and_pinned_intent_tampering_block_apply(self):
        self.review()
        self.put(self.copies / "application-state.json", sr.json_bytes({"status": "needs-recovery"}))
        with self.assertRaisesRegex(sr.ReviewError, "recovery"):
            sr.check(self.ctx(), ready=True)
        (self.copies / "application-state.json").unlink()
        self.manifest["documents"][0]["approved"] = self.manifest["documents"][0]["base"]
        self.save()
        self.review()
        with self.assertRaisesRegex(sr.ReviewError, "Approved history"):
            sr.check(self.ctx(), ready=True)

    def test_add_empty_file_and_unexpected_existing_target(self):
        doc = {"id": "D002", "path": "workflow/new.md", "operation": "add", "change_ids": ["CH-02"],
               "base": None, "review_base": None}
        self.manifest["documents"].append(doc)
        self.put(self.copies / "content/workflow/new.md", b"")
        self.save()
        self.commit()
        sr.pin_intent(self.ctx(), "HEAD")
        self.manifest = sr.read_json(self.copies / "manifest.json")
        self.review()
        sr.check(self.ctx(), ready=True)
        self.put(self.root / "openspec/orders/workflow/new.md", b"")
        with self.assertRaisesRegex(sr.ReviewError, "Master changed"):
            sr.check(self.ctx(), ready=True)
        self.assertEqual(self.original, self.master.read_bytes())

    def test_bad_second_document_does_not_publish_partial_review(self):
        self.review()
        old_review = (self.copies / "review/workflow/discount.md").read_bytes()
        self.manifest["documents"].append({"id": "D002", "path": "bad.md", "operation": "add",
                                           "change_ids": ["CH-02"], "base": None, "review_base": None})
        self.put(self.copies / "content/bad.md", b"<script>bad()</script>")
        self.put(self.copies / "content/workflow/discount.md", self.proposed + b"\nNew text\n")
        self.save()
        old_manifest = (self.copies / "manifest.json").read_bytes()
        change = self.change / "change.md"
        change.write_bytes(change.read_bytes() + b"\nNew requirement\n")
        with self.assertRaises(ValueError):
            sr.render(self.ctx(), save_change_hash=True)
        self.assertEqual(old_manifest, (self.copies / "manifest.json").read_bytes())
        self.assertEqual(old_review, (self.copies / "review/workflow/discount.md").read_bytes())
        self.assertEqual(self.original, self.master.read_bytes())

    def test_compact_layout_and_approval_fingerprint_are_stable(self):
        metadata = sr.render(self.ctx())
        sr.acknowledge(self.ctx(), "Fixture: analyst reviewed these bytes")
        before = (self.copies / "manifest.json").read_bytes()
        self.assertEqual(metadata["fingerprint"], self.ctx().manifest["review_approval"]["fingerprint"])
        self.assertEqual(metadata, sr.check(self.ctx(), ready=True))
        self.assertEqual(metadata, sr.render(self.ctx()))
        self.assertEqual(before, (self.copies / "manifest.json").read_bytes())
        self.assertEqual({"manifest.json", "content/workflow/discount.md", "review/workflow/discount.md"},
                         {p.relative_to(self.copies).as_posix() for p in self.copies.rglob("*") if p.is_file()})

    def test_unacknowledged_result_can_be_viewed_but_not_applied(self):
        sr.render(self.ctx())
        sr.check(self.ctx())
        with self.assertRaisesRegex(sr.ReviewError, "not been acknowledged"):
            sr.check(self.ctx(), ready=True)
        self.assertNotIn("review_approval", self.ctx().manifest)

    def test_one_state_file_covers_partial_and_completed_application(self):
        added = {"id": "D002", "path": "workflow/new.md", "operation": "add", "change_ids": ["CH-02"],
                 "base": None, "review_base": None}
        self.manifest["documents"].append(added)
        self.put(self.copies / "content/workflow/new.md", b"# New document\n")
        self.save()
        self.commit()
        sr.pin_intent(self.ctx(), "HEAD")
        self.review()
        metadata = sr.check(self.ctx(), ready=True)
        state_path = self.copies / "application-state.json"
        state = {"schema_version": 3, "status": "in-progress", "fingerprint": metadata["fingerprint"],
                 "documents": metadata["documents"]}
        self.put(state_path, sr.json_bytes(state))
        self.master.write_bytes(self.proposed)
        with self.assertRaisesRegex(sr.ReviewError, "recovery"):
            sr.check(self.ctx(), ready=True)
        with self.assertRaisesRegex(sr.ReviewError, "does not match"):
            sr.check(self.ctx(), result=True)
        self.put(self.root / "openspec/orders/workflow/new.md", b"# New document\n")
        sr.check(self.ctx(), result=True)
        self.put(state_path, sr.json_bytes({**state, "status": "verified", "outcome": "applied"}))
        sr.check(self.ctx(), result=True)
        self.assertEqual({"manifest.json", "application-state.json"},
                         {p.name for p in self.copies.iterdir() if p.is_file()})

    def test_conflict_blocks_entire_set(self):
        self.manifest["review_status"] = "conflict"
        self.save()
        sr.render(self.ctx())
        with self.assertRaises(sr.ReviewError):
            sr.acknowledge(self.ctx(), "test")
        with self.assertRaisesRegex(sr.ReviewError, "Draft/conflict"):
            sr.check(self.ctx(), ready=True)
        self.assertEqual(self.original, self.master.read_bytes())

    def test_scope_and_requirements_cannot_silently_change(self):
        self.review()
        self.manifest["documents"][0]["change_ids"] = ["CH-02"]
        self.save()
        self.review()
        with self.assertRaisesRegex(sr.ReviewError, "Scope"):
            sr.check(self.ctx(), ready=True)

    def test_requirements_changed_but_status_may_change(self):
        self.review()
        change = self.change / "change.md"
        change.write_bytes(change.read_bytes().replace("Согласовано".encode(), "В реализации".encode()))
        sr.check(self.ctx(), ready=True)
        change.write_bytes(change.read_bytes() + b"\nNew requirement\n")
        sr.render(self.ctx(), save_change_hash=True)
        self.review()
        with self.assertRaisesRegex(sr.ReviewError, "Requirements"):
            sr.check(self.ctx(), ready=True)

    def test_missing_history_and_hash_mismatch_fail_closed(self):
        doc = self.manifest["documents"][0]
        doc["base"] = {**doc["base"], "sha256": "0" * 64}
        self.save()
        with self.assertRaisesRegex(sr.ReviewError, "hash mismatch"):
            sr.render(self.ctx())
        doc["base"]["commit"] = "0" * 40
        self.save()
        with self.assertRaises(sr.ReviewError):
            sr.render(self.ctx())

    def test_status_in_body_is_a_requirement_and_draft_cannot_apply(self):
        original = (self.change / "change.md").read_bytes()
        body_status = original + '\n## Правило\n\n> **Статус**: OPEN\n'.encode()
        self.assertNotEqual(sr.requirements_hash(body_status),
                            sr.requirements_hash(body_status.replace(b'OPEN', b'CLOSED')))
        self.review()
        self.put(self.change / "change.md", original.replace('Согласовано'.encode(), 'На согласовании'.encode()))
        sr.check(self.ctx())
        with self.assertRaisesRegex(sr.ReviewError, "lifecycle"):
            sr.check(self.ctx(), ready=True)

    def test_absence_is_not_empty_and_delete_has_no_working_copy(self):
        doc = self.manifest["documents"][0]
        doc["operation"] = "delete"
        path = self.copies / "content/workflow/discount.md"
        self.save()
        with self.assertRaisesRegex(sr.ReviewError, "Delete must not"):
            sr.render(self.ctx())
        path.unlink()
        self.commit()
        sr.pin_intent(self.ctx(), "HEAD")
        self.manifest = sr.read_json(self.copies / "manifest.json")
        self.review()
        sr.check(self.ctx(), ready=True)
        self.master.write_bytes(b"")
        with self.assertRaisesRegex(sr.ReviewError, "does not match"):
            sr.check(self.ctx(), result=True)
        self.master.unlink()
        sr.check(self.ctx(), result=True)

    def test_manual_change_blocks_all_consumers_without_rewriting_metadata(self):
        self.review()
        original_files = {p: p.read_bytes() for p in self.copies.rglob("*") if p.is_file()}
        change = self.change / "change.md"
        change.write_bytes(change.read_bytes() + "\nCH-02: лимит теперь 2000.\n".encode())
        commands = [("render",), ("check",), ("check", "--ready"),
                    ("check", "--result"), ("pin-intent", "--revision", "HEAD"),
                    ("acknowledge", "--evidence", "Old decision")]
        for command in commands:
            with self.subTest(command=command):
                code, _, error = self.command(*command)
                self.assertEqual(1, code)
                self.assertIn("change.md changed since content was prepared", error)
        self.assertEqual(original_files, {p: p.read_bytes() for p in self.copies.rglob("*") if p.is_file()})
        self.assertEqual(self.original, self.master.read_bytes())

    def test_missing_checksum_fails_even_before_initial_approval(self):
        self.manifest.pop("change_sha256")
        self.manifest.pop("intent_approval")
        self.save()
        for command in (("check",), ("render",), ("pin-intent", "--revision", "HEAD")):
            with self.subTest(command=command):
                code, _, error = self.command(*command)
                self.assertEqual(1, code)
                self.assertIn("Missing change_sha256", error)
        self.assertNotIn("change_sha256", self.ctx().manifest)
        code, _, error = self.command("render", "--save-change-hash")
        self.assertEqual(0, code, error)
        sr.check(self.ctx())

    def test_reconciled_content_needs_separate_intent_and_review_approval(self):
        self.review()
        change = self.change / "change.md"
        change.write_bytes(change.read_bytes() + "\nCH-01: также лимит 2000.\n".encode())
        self.put(self.copies / "content/workflow/discount.md", self.proposed.replace(b"1000.", b"2000."))
        code, _, error = self.command("render", "--save-change-hash")
        self.assertEqual(0, code, error)
        self.assertEqual(sr.requirements_hash(change.read_bytes()), self.ctx().manifest["change_sha256"])
        sr.check(self.ctx())
        with self.assertRaisesRegex(sr.ReviewError, "Requirements differ"):
            sr.check(self.ctx(), ready=True)
        self.commit()
        sr.pin_intent(self.ctx(), "HEAD")
        sr.render(self.ctx())
        with self.assertRaisesRegex(sr.ReviewError, "not been acknowledged"):
            sr.check(self.ctx(), ready=True)
        sr.acknowledge(self.ctx(), "Fixture: revised result approved")
        sr.check(self.ctx(), ready=True)
        self.assertEqual(self.original, self.master.read_bytes())

    def test_checksum_ignores_only_lifecycle_and_change_checkout_format(self):
        self.review()
        path = self.copies / "manifest.json"
        previous = path.read_bytes()
        change = self.change / "change.md"
        revised = change.read_bytes().replace("Согласовано".encode(), "В реализации".encode())
        change.write_bytes(b"\xef\xbb\xbf" + revised.replace(b"\n", b"\r\n"))
        sr.check(self.ctx(), ready=True)
        self.assertEqual(previous, path.read_bytes())
        change.write_bytes(change.read_bytes() + "\r\n## Правило\r\n> **Статус**: OPEN\r\n".encode())
        with self.assertRaisesRegex(sr.ReviewError, "changed since"):
            sr.check(self.ctx())

    def test_unsafe_paths_and_duplicate_destinations_rejected(self):
        for path in ("../escape.md", "/absolute.md", "C:/file.md", "a\\b.md"):
            with self.subTest(path=path), self.assertRaises(sr.ReviewError):
                sr.relative_path(path)
        self.manifest["documents"].append({**self.manifest["documents"][0], "id": "D002"})
        self.save()
        with self.assertRaisesRegex(sr.ReviewError, "Duplicate"):
            self.ctx()

    def test_relative_links_resolve_from_original_document_location(self):
        resolver = sr.link_resolver(self.ctx(), self.manifest["documents"][0])
        self.assertEqual("discount.md#скидка", resolver("discount.md#скидка"))
        review_parent = self.copies / "review/workflow"
        linked = (review_parent / resolver("../api.md")).resolve()
        self.assertEqual(self.root / "openspec/orders/api.md", linked)
        self.assertEqual("https://example.com/x", resolver("https://example.com/x"))


if __name__ == "__main__":
    unittest.main()
