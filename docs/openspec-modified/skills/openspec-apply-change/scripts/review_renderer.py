"""Render a structural Markdown comparison. Never edits or merges source documents."""
from __future__ import annotations

from copy import deepcopy
from dataclasses import dataclass, field
from difflib import SequenceMatcher
from html import escape
import json
import re
from typing import Callable

import regex
from markdown_it import MarkdownIt
from markdown_it.token import Token


COLORS = {
    "old": ("#b42318", "#fee4e2", "#d92d20", "#fecdca", "#912018", "−"),
    "new": ("#067647", "#dcfae6", "#079455", "#abefc6", "#054f31", "+"),
}


@dataclass
class Node:
    token: Token
    children: list[Node] = field(default_factory=list)
    close: Token | None = None

    def tokens(self) -> list[Token]:
        return [self.token] + [t for c in self.children for t in c.tokens()] + (
            [self.close] if self.close else []
        )

    def key(self) -> str:
        # Positions change when a preceding paragraph is inserted; content does not.
        def key_token(t: Token):
            return [t.type, t.tag, t.attrs, t.content, t.info, t.markup, t.hidden,
                    [key_token(c) for c in t.children or []]]
        return json.dumps([key_token(t) for t in self.tokens()], ensure_ascii=False,
                          sort_keys=True)


def row_style(side: str) -> str:
    color, bg, border, _, _, _ = COLORS[side]
    return (f"color:{color};background-color:{bg};padding:2px 5px;"
            f"border-left:4px solid {border};border-radius:4px;")


def sign(side: str) -> str:
    return ('<strong style="font-size:10px;margin-right:10px;" aria-hidden="true">'
            + COLORS[side][5] + "</strong> ")


def changed_ranges(old: str, new: str) -> tuple[set[int], set[int]]:
    """Align words/values first, then trim shared graphemes inside each replacement."""
    def words(text):
        chunks, last_kind = [], None
        for cluster in regex.findall(r"\X", text):
            kind = "space" if cluster.isspace() else (
                "word" if regex.match(r"[\p{L}\p{N}\p{M}_]", cluster) else "symbol")
            if chunks and kind == last_kind and kind != "symbol":
                chunks[-1] += cluster
            else:
                chunks.append(cluster)
            last_kind = kind
        return chunks

    def offsets(parts):
        result = [0]
        for value in parts:
            result.append(result[-1] + len(value))
        return result

    a, b = words(old), words(new)
    starts = [offsets(a), offsets(b)]
    result: tuple[set[int], set[int]] = (set(), set())
    for op, i, j, k, l in SequenceMatcher(None, a, b, autojunk=False).get_opcodes():
        if op == "equal":
            continue
        for n in range(max(j - i, l - k)):
            left = a[i + n] if i + n < j else ""
            right = b[k + n] if k + n < l else ""
            x, y = regex.findall(r"\X", left), regex.findall(r"\X", right)
            prefix = 0
            while prefix < min(len(x), len(y)) and x[prefix] == y[prefix]:
                prefix += 1
            suffix = 0
            while suffix < min(len(x), len(y)) - prefix and x[-1 - suffix] == y[-1 - suffix]:
                suffix += 1
            # Keeping the common suffix fixed prevents 1000 -> 1500 from marking
            # the final zero as a deletion plus a separate insertion of 5.
            middle_x, middle_y = x[prefix:len(x) - suffix], y[prefix:len(y) - suffix]
            for change, p, q, r, s in SequenceMatcher(None, middle_x, middle_y, autojunk=False).get_opcodes():
                if change == "equal":
                    continue
                for side, clusters, index, bound, begin, end in (
                    (0, x, i + n, j, p, q), (1, y, k + n, l, r, s)
                ):
                    if index < bound:
                        positions = offsets(clusters)
                        start = starts[side][index] + positions[prefix + begin]
                        stop = starts[side][index] + positions[prefix + end]
                        result[side].update(range(start, stop))
    return result


def highlighted(text: str, positions: set[int], side: str, start: int = 0) -> str:
    _, _, _, bg, fg, _ = COLORS[side]
    parts = []
    previous = None
    for i, character in enumerate(text):
        marked = start + i in positions
        if marked != previous:
            if previous:
                parts.append("</span>")
            if marked:
                parts.append(f'<span style="background-color:{bg};color:{fg};font-weight:700;">')
            previous = marked
        parts.append(escape(character, quote=False))
    if previous:
        parts.append("</span>")
    return "".join(parts)


def inline_text(token: Token) -> str:
    parts = []
    for child in token.children or []:
        if child.type in ("text", "code_inline", "image"):
            parts.append(child.content)
        elif child.type in ("softbreak", "hardbreak"):
            parts.append("\n")
    return "".join(parts)


def destinations(token: Token):
    return [(t.type, t.attrGet("href") or t.attrGet("src"), t.attrGet("title"))
            for t in token.children or [] if t.type in ("link_open", "image")]


def inline_lines(node: Node) -> list[Node] | None:
    """Split soft lines only at balanced inline boundaries; preserve multiline markup."""
    if node.token.type != "paragraph_open":
        return None
    inline = node.children[0].token
    lines, current, depth = [], [], 0
    for token in inline.children or []:
        if token.type in ("softbreak", "hardbreak"):
            if depth:
                return None
            lines.append(current)
            current = []
        else:
            depth += token.nesting
            current.append(token)
    lines.append(current)
    output = []
    for children in lines:
        item = deepcopy(node)
        item.token.hidden = False
        item.close.hidden = False
        item.children[0].token.children = deepcopy(children)
        item.children[0].token.content = ""
        output.append(item)
    return output


class ReviewRenderer:
    def __init__(self, resolve_link: Callable[[str], str] = lambda value: value):
        self.md = MarkdownIt("commonmark", {"html": True}).enable(["table", "strikethrough"])
        self.resolve_link = resolve_link

    def parse(self, source: str) -> list[Node]:
        tokens = self.md.parse(source)
        headings: dict[str, int] = {}
        for i, token in enumerate(tokens):
            if token.type == "html_block":
                if re.fullmatch(r"\s*<!--(?!.*--\>.*<!--).*?-->\s*", token.content, re.S):
                    token.content = ""  # Ordinary non-visible Markdown comments.
                else:
                    raise ValueError("Raw HTML требует отдельного renderer; review не создан.")
            for child in token.children or []:
                if child.type == "html_inline":
                    raise ValueError("Inline HTML требует отдельного renderer; review не создан.")
                for attr in ("href", "src"):
                    if child.attrGet(attr):
                        child.attrSet(attr, self.resolve_link(child.attrGet(attr)))
            if token.type == "heading_open":
                title = inline_text(tokens[i + 1])
                slug = regex.sub(r"[^\p{L}\p{N}\p{M}_\- ]", "", title.lower()).replace(" ", "-")
                count = headings.get(slug, 0)
                headings[slug] = count + 1
                token.attrSet("id", slug + (f"-{count}" if count else ""))
        root: list[Node] = []
        stack: list[Node] = []
        for token in tokens:
            if token.nesting == -1:
                stack.pop().close = token
                continue
            node = Node(token)
            (stack[-1].children if stack else root).append(node)
            if token.nesting == 1:
                stack.append(node)
        return root

    def render(self, nodes: list[Node]) -> str:
        return self.md.renderer.render([t for n in nodes for t in n.tokens()], self.md.options, {})

    def inline(self, token: Token, positions: set[int], side: str) -> str:
        children = deepcopy(token.children or [])
        offset = 0
        for child in children:
            if child.type in ("text", "code_inline"):
                content = highlighted(child.content, positions, side, offset)
                offset += len(child.content)
                if child.type == "code_inline":
                    content = "<code>" + content + "</code>"
                child.type, child.tag, child.content = "html_inline", "", content
            elif child.type in ("softbreak", "hardbreak"):
                offset += 1
            elif child.type == "image":
                offset += len(child.content)
        return self.md.renderer.renderInline(children, self.md.options, {})

    def pair(self, old: Node, new: Node) -> str:
        left, right = inline_lines(old), inline_lines(new)
        if left and right and (len(left) > 1 or len(right) > 1):
            return self.compare(left, right)
        a, b = old.children[0].token, new.children[0].token
        ranges = changed_ranges(inline_text(a), inline_text(b))
        output = []
        for node, token, marks, side in zip((old, new), (a, b), ranges, ("old", "new")):
            body = self.inline(token, marks, side)
            if side == "old":
                body = "<del>" + body + "</del>"
            attrs = dict(node.token.attrs)
            attrs["style"] = row_style(side)
            if side == "old" and "id" in attrs:
                attrs["id"] = "removed-" + attrs["id"]
            tag = node.token.tag or "p"
            attributes = "".join(f' {k}="{escape(str(v), quote=True)}"' for k, v in attrs.items())
            output.append(f"<{tag}{attributes}>{sign(side)}{body}</{tag}>\n")
        # A changed link/image destination must not hide behind an unchanged label.
        if destinations(a) != destinations(b):
            for token, side in ((a, "old"), (b, "new")):
                values = ", ".join((url or "") + (f" ({title})" if title else "")
                                   for _, url, title in destinations(token)) or "нет"
                output.append(f'<p style="{row_style(side)}">{sign(side)}Адреса ссылок: '
                              + escape(values) + "</p>\n")
        return "".join(output)

    def whole(self, node: Node, side: str) -> str:
        node = deepcopy(node)
        for token in node.tokens():
            if side == "old" and token.attrGet("id"):
                token.attrSet("id", "removed-" + token.attrGet("id"))
        body = self.render([node])
        if side == "old":
            body = "<del>" + body + "</del>"
        return f'<div style="{row_style(side)}">{sign(side)}{body}</div>\n'

    def table_row(self, old: Node | None, new: Node | None) -> str:
        output = []
        for row, other, side in ((old, new, "old"), (new, old, "new")):
            if row is None:
                continue
            output.append(f'<tr style="{row_style(side)}">')
            for i, cell in enumerate(row.children):
                token = cell.children[0].token
                marks: set[int] = set()
                if other and i < len(other.children):
                    opposite = other.children[i].children[0].token
                    marks = changed_ranges(inline_text(token), inline_text(opposite))[0]
                body = self.inline(token, marks, side)
                if other and i < len(other.children) and destinations(token) != destinations(opposite):
                    addresses = ", ".join(url or "" for _, url, _ in destinations(token)) or "нет"
                    body += "<br>Адреса ссылок: " + escape(addresses)
                if side == "old":
                    body = "<del>" + body + "</del>"
                if i == 0:
                    body = sign(side) + body
                attrs = "".join(f' {k}="{escape(str(v), quote=True)}"'
                                for k, v in cell.token.attrs.items())
                output.append(f"<{cell.token.tag}{attrs}>{body}</{cell.token.tag}>")
            output.append("</tr>\n")
        return "".join(output)

    def code_pair(self, old: Node, new: Node) -> str:
        lines = []
        a, b = old.token.content.splitlines(), new.token.content.splitlines()
        for op, i, j, k, l in SequenceMatcher(None, a, b, autojunk=False).get_opcodes():
            if op == "equal":
                lines.extend("  " + escape(line) for line in a[i:j])
            else:
                for offset in range(max(j - i, l - k)):
                    left = a[i + offset] if i + offset < j else None
                    right = b[k + offset] if k + offset < l else None
                    ranges = changed_ranges(left or "", right or "") if left is not None and right is not None else (set(), set())
                    for value, marks, side in zip((left, right), ranges, ("old", "new")):
                        if value is not None:
                            body = highlighted(value, marks, side)
                            if side == "old":
                                body = "<del>" + body + "</del>"
                            lines.append(f'<span style="{row_style(side)}">{COLORS[side][5]} {body}</span>')
        return "<pre><code>" + "\n".join(lines) + "\n</code></pre>\n"

    def compare_node(self, old: Node, new: Node) -> str:
        if old.key() == new.key():
            return self.render([new])
        if old.token.type != new.token.type or old.token.tag != new.token.tag:
            return self.whole(old, "old") + self.whole(new, "new")
        if old.token.type in ("paragraph_open", "heading_open"):
            return self.pair(old, new)
        if old.token.type in ("fence", "code_block") and old.token.info == new.token.info:
            return self.code_pair(old, new)
        if old.token.type == "tr_open":
            return self.table_row(old, new)
        if old.children and new.children and old.token.attrs == new.token.attrs:
            start = self.md.renderer.render([new.token], self.md.options, {})
            end = self.md.renderer.render([new.close], self.md.options, {}) if new.close else ""
            return start + self.compare(old.children, new.children) + end
        return self.whole(old, "old") + self.whole(new, "new")

    def compare(self, old: list[Node], new: list[Node]) -> str:
        output = []
        for op, i, j, k, l in SequenceMatcher(None, [n.key() for n in old],
                                             [n.key() for n in new], autojunk=False).get_opcodes():
            if op == "equal":
                output.append(self.render(new[k:l]))
                continue
            for offset in range(max(j - i, l - k)):
                a = old[i + offset] if i + offset < j else None
                b = new[k + offset] if k + offset < l else None
                if a and b:
                    output.append(self.compare_node(a, b))
                else:
                    node, side = (a, "old") if a else (b, "new")
                    if node.token.type == "tr_open":
                        output.append(self.table_row(a, b))
                    elif node.token.type == "list_item_open":
                        body = self.render(node.children)
                        if side == "old":
                            body = "<del>" + body + "</del>"
                        output.append(f'<li style="{row_style(side)}">{sign(side)}{body}</li>\n')
                    elif node.token.type in ("thead_open", "tbody_open"):
                        start = self.md.renderer.render([node.token], self.md.options, {})
                        end = self.md.renderer.render([node.close], self.md.options, {})
                        rows = self.compare(node.children if a else [], node.children if b else [])
                        output.append(start + rows + end)
                    else:
                        output.append(self.whole(node, side))
        return "".join(output)

    def document(self, before: bytes | None, after: bytes | None, status: str) -> bytes:
        old = (before or b"").decode("utf-8-sig")
        new = (after or b"").decode("utf-8-sig")
        banners = {
            "draft": "Проект изменения. Требуется согласование.",
            "ready": "Результат для применения. Зелёным показаны добавления, красным — удаления.",
            "conflict": "КОНФЛИКТ. Показан предложенный вариант; итог применения ещё не определён.",
        }
        header = "<!-- Generated by spec_review.py; edit content/, then render. -->\n\n"
        header += "> " + banners[status] + "\n\n"
        if after is None:
            header += "> Документ будет удалён.\n\n"
        elif before is None:
            header += "> Новый документ.\n\n"
        a, b = self.parse(old), self.parse(new)
        if before != after and [n.key() for n in a] == [n.key() for n in b]:
            header += "> Изменилось только исходное оформление Markdown (например, переводы строк).\n\n"
        return (header + self.compare(a, b)).encode("utf-8")
