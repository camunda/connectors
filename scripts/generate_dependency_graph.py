#!/usr/bin/env python3
"""
Generate an inter-module dependency graph for this multi-module Maven repo.

Usage:
    python3 scripts/generate_dependency_graph.py [--output-dir out]

Requirements:
- Maven must be installed and available on PATH to run the non-scan mode.
- Graphviz `dot` is required to generate SVG.

The script logs progress with print statements but does not dump the raw dependency lists.
"""
import argparse
import subprocess
from pathlib import Path
import tempfile
from typing import Any, Literal, Tuple, Dict, List
import json
from dataclasses import dataclass


@dataclass(frozen=True)
class Module:
    groupId: str
    artifactId: str

    def __str__(self) -> str:
        return f"{self.groupId}:{self.artifactId}"

    def __lt__(self, other: object) -> bool:
        if not isinstance(other, Module):
            return NotImplemented
        return str(self) < str(other)


@dataclass(frozen=True)
class Dependency(Module):
    """A dependency which is a Module plus a scope string (e.g. 'compile' or 'test')."""

    scope: Literal["compile", "test"] = "compile"

    def __str__(self) -> str:
        return f"{self.groupId}:{self.artifactId} with scope={self.scope}"


def write_adj_dict_json(adj: Dict[Module, List[Dependency]], out_path: Path):
    serial = {
        str(k): [f"{d.groupId}:{d.artifactId} with scope={d.scope}" for d in vals] for k, vals in adj.items()
    }
    out_path.write_text(json.dumps(serial, indent=2, sort_keys=True))


def _extract_json_objects_from_text(text: str):
    """Extract JSON objects from text that may contain multiple concatenated/pretty-printed
    JSON objects (as produced when mvn writes one JSON tree per module into the same file).

    Returns a list of parsed JSON objects. It attempts to find balanced top-level JSON objects and
    json.loads each candidate.
    """
    objs = []
    if not text:
        return objs
    import json

    n = len(text)
    i = 0
    while i < n:
        # find next opening brace
        start = text.find("{", i)
        if start == -1:
            break
        depth = 0
        in_str = False
        esc = False
        j = start
        while j < n:
            ch = text[j]
            if in_str:
                if esc:
                    esc = False
                elif ch == "\\":
                    esc = True
                elif ch == '"':
                    in_str = False
            else:
                if ch == '"':
                    in_str = True
                elif ch == "{":
                    depth += 1
                elif ch == "}":
                    depth -= 1
                    if depth == 0:
                        candidate = text[start : j + 1]
                        try:
                            objs.append(json.loads(candidate))
                        except Exception:
                            # ignore malformed candidate
                            pass
                        i = j + 1
                        break
            j += 1
        else:
            # no matching closing brace found
            break
    return objs


def extract_edges(module_json: dict) -> Tuple[Module, List[Dependency]]:
    """Extract the top-level module and its direct children from a parsed mvn JSON object.

    Returns a tuple (Module, List[Dependency]) where the first element is the module described by
    the provided dict (uses 'groupId' and 'artifactId') and the second element is a list of
    Module instances for each direct child in the optional 'children' list and their scope. Only first-level
    children are considered (no recursive descent).

    Behavior/edge-cases:
    - If 'children' is missing or not a list, an empty list is returned for dependencies.
    - Entries missing 'groupId' or 'artifactId' are skipped.
    """
    if not isinstance(module_json, dict):
        raise TypeError("module_json must be a dict representing a mvn JSON module")

    gid = module_json.get("groupId") or ""
    aid = module_json.get("artifactId") or ""
    top = Module(groupId=gid, artifactId=aid)

    deps: List[Dependency] = []
    children = module_json.get("children")
    if isinstance(children, list):
        for c in children:
            if not isinstance(c, dict):
                continue
            cg = c.get("groupId")
            ca = c.get("artifactId")
            if not cg or not ca:
                # skip malformed child entries
                continue
            scope = c.get("scope")
            assert scope is not None
            deps.append(Dependency(groupId=cg, artifactId=ca, scope=scope))

    return top, deps


def write_dependency_trees_to_file(repo_root: Path, use_output_file: Path) -> None:
    """Run a single repo-level mvn dependency:tree invocation.
    """
    # use -DappendOutput=true to ensure plugin writes all output (some versions append)
    cmd = [
        "mvn",
        "dependency:tree",
        f"-DoutputFile={str(use_output_file)}",
        "-DoutputType=json",
        "-DappendOutput=true",
    ]
    print(f"Running mvn command: {' '.join(cmd)} in {repo_root}")
    try:
        subprocess.run(
            cmd, cwd=repo_root, stdout=subprocess.DEVNULL, text=True, check=True
        )
    except FileNotFoundError:
        raise RuntimeError(
            "Maven (mvn) not found on PATH; please install Maven or add it to PATH"
        )


def parse_depency_tree(repo_root: Path) -> list[dict[str, Any]]:
    """Attempt to run mvn and return cleaned output (with maven prefixes stripped)."""
    # Try JSON output first
    with tempfile.NamedTemporaryFile(
        prefix="mvn-deps-", suffix=".json", delete=True
    ) as tf:
        out_path = Path(tf.name)
        write_dependency_trees_to_file(repo_root, use_output_file=out_path)
        with out_path.open("r") as f:
            file_content = f.read()

        # File can contain multiple JSON objects, extract individually
        multi = _extract_json_objects_from_text(file_content)
        print(f"Extracted {len(multi)} JSON object(s) from mvn output file {out_path}")
        return multi


def write_dot_file(edges: list[tuple[Module, Module, str]], out_path: Path):
    with out_path.open("w") as f:
        f.write("digraph connectors {\n")
        # layout attributes to reduce squishing and improve spacing
        f.write("  rankdir=LR;\n")
        f.write("  nodesep=0.6;\n")
        f.write("  ranksep=0.8;\n")
        f.write("  splines=true;\n")
        f.write("  overlap=false;\n")
        f.write('  sep="+4";\n')
        f.write("  fontsize=10;\n")
        # set a default minimum edge length to encourage spread
        f.write("  edge [minlen=2];\n")
        for a, b, color in sorted(edges, key=lambda x: str(x)):
            f.write(f'  "{a.artifactId}" -> "{b.artifactId}" [color={color}];\n')
        f.write("}\n")


def filter_dependency_dict_modules(
    adj: Dict[Module, List[Dependency]],
    keep_prefix: str = "io.camunda.connector",
    excluded_artifacts: list[str] = ["e2e", "bundle"],
    omit_tests: bool = False,
) -> Dict[Module, List[Dependency]]:
    """Filter adjacency dict (Module keyed) .

    Returns a new adjacency dict containing only modules whose string form startswith keep_prefix
    and do not contain any exclude patterns.
    """

    def ok(m: Module) -> bool:
        if not m.groupId.startswith(keep_prefix):
            return False
        if any(pat in m.artifactId for pat in excluded_artifacts):
            return False
        return True

    out: Dict[Module, List[Dependency]] = {}
    for k, vals in adj.items():
        if not ok(k):
            continue
        kept: List[Dependency] = []
        for v in vals:
            if omit_tests and v.scope == "test":
                continue
            if ok(v):
                kept.append(v)
        out[k] = kept

    return out


def write_dotfile_from_dict(adj: Dict[Module, List[Dependency]], out_path: Path):
    edges: List[tuple[Module, Module, str]] = []
    for a, vals in adj.items():
        for dep in vals:
            color = "red" if dep.scope == "test" else "black"
            edges.append(
                (
                    a,
                    dep,
                    color,
                )
            )
    write_dot_file(edges, out_path)


def generate_svg(dot_path: Path, svg_path: Path):
    try:
        proc = subprocess.run(
            ["dot", "-Tsvg", str(dot_path), "-o", str(svg_path)],
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
        )
        if proc.returncode != 0:
            print("Graphviz `dot` failed:", proc.stderr)
            return False
        return True
    except FileNotFoundError:
        print("Graphviz `dot` not found on PATH; skipping SVG generation")
        return False


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--output-dir", "-o", default="dependency-graph-output")
    parser.add_argument("--verbose", "-v", action="store_true")
    parser.add_argument(
        "--emit-json",
        action="store_true",
        help="Emit connectors-deps.json alongside the DOT",
    )
    parser.add_argument(
        "--entrypoints",
        nargs="+",
        help="List of artifactIds to use as roots for the printed graph",
    )
    parser.add_argument(
        "--omit-tests",
        action="store_true",
        help="Omit test-scoped dependencies from the generated graph",
    )
    args = parser.parse_args()

    repo_root = Path(__file__).resolve().parents[1]
    print(f"Scanning repository at {repo_root}")

    out_dir = Path(args.output_dir)
    out_dir.mkdir(parents=True, exist_ok=True)

    try:
        dependency_json_dicts = parse_depency_tree(repo_root)
    except RuntimeError as e:
        print("Could not obtain dependency output from Maven:", e)
        return

    # data is already parsed JSON (object)
    repo_json = out_dir / "repo-deps.json"
    try:
        repo_json.write_text(json.dumps(dependency_json_dicts, indent=2))
        print(f"Saved parsed dependency JSON to {repo_json}")
    except Exception:
        # best-effort saving; ignore errors
        pass

    adj = {}
    # aggregate edges across all parsed JSON objects
    for module_json in dependency_json_dicts:
        module, dependencies = extract_edges(module_json)
        adj[module] = dependencies

    adj_filtered = filter_dependency_dict_modules(adj, omit_tests=args.omit_tests)

    # If entrypoints provided, further restrict to nodes reachable from them
    if args.entrypoints:

        def restrict_to_entrypoints(
            adj_in: Dict[Module, List[Dependency]], roots: List[str]
        ) -> Dict[Module, List[Dependency]]:
            # BFS/DFS from roots over adj_in to collect reachable nodes
            seen = set()
            stack = []
            for r in roots:
                for module in adj_in.keys():
                    if module.artifactId == r:
                        stack.append(module)
            while stack:
                cur = stack.pop()
                if cur in seen:
                    continue
                seen.add(cur)
                for nb in adj_in.get(cur, []):
                    # nb is a Dependency; normalize to Module for reachability
                    nb_mod = Module(groupId=nb.groupId, artifactId=nb.artifactId)
                    if nb_mod not in seen:
                        stack.append(nb_mod)
            # build subgraph
            out = {}
            for n in seen:
                # keep only Dependency entries whose Module part is in `seen`
                deps = []
                for d in adj_in.get(n, []):
                    if Module(groupId=d.groupId, artifactId=d.artifactId) in seen:
                        deps.append(d)
                out[n] = deps
            return out

        adj_filtered = restrict_to_entrypoints(adj_filtered, args.entrypoints)

    # write DOT from filtered dict
    dot_path = out_dir / "connectors-deps.dot"
    svg_path = out_dir / "connectors-deps.svg"
    write_dotfile_from_dict(adj_filtered, dot_path)
    print(f"Wrote DOT file: {dot_path}")

    # optionally emit JSON adjacency
    if args.emit_json:
        json_path = out_dir / "connectors-deps.json"
        write_adj_dict_json(adj_filtered, json_path)
        print(f"Wrote adjacency JSON: {json_path}")

    # generate SVG
    if generate_svg(dot_path, svg_path):
        print(f"Wrote SVG: {svg_path}")
    else:
        print("SVG not generated. Install Graphviz 'dot' to produce an SVG.")
        print("Alternatively, run without --emit-json to only produce the DOT file.")


if __name__ == "__main__":
    main()
