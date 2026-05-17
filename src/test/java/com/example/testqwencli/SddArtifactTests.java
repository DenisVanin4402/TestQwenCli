package com.example.testqwencli;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SddArtifactTests {

	private static final Path ROOT = Path.of("").toAbsolutePath();

	@Test
	void bootstrapArtifactsArePresent() {
		assertFilesExist(List.of(
				"docs/specs/0001-sdd-bootstrap/spec.md",
				"docs/specs/0001-sdd-bootstrap/plan.md",
				"docs/specs/0001-sdd-bootstrap/tasks.md",
				"docs/specs/0001-sdd-bootstrap/test-plan.md",
				"docs/specs/0001-sdd-bootstrap/task-state.md",
				"docs/specs/0001-sdd-bootstrap/work-log.md",
				"docs/specs/0001-sdd-bootstrap/handoff.md"
		));
	}

	@Test
	void diffDrivenBootstrapArtifactsArePresent() {
		assertFilesExist(List.of(
				"docs/specs/0002-diff-driven-spec-synthesis/spec.md",
				"docs/specs/0002-diff-driven-spec-synthesis/plan.md",
				"docs/specs/0002-diff-driven-spec-synthesis/tasks.md",
				"docs/specs/0002-diff-driven-spec-synthesis/test-plan.md",
				"docs/specs/0002-diff-driven-spec-synthesis/task-state.md",
				"docs/specs/0002-diff-driven-spec-synthesis/work-log.md",
				"docs/specs/0002-diff-driven-spec-synthesis/handoff.md"
		));
	}

	@Test
	void governanceArtifactsArePresent() {
		assertFilesExist(List.of(
				"docs/sdd/constitution.md",
				"docs/sdd/instruction.md",
				"docs/sdd/workflow.md",
				"docs/sdd/gates.md",
				"docs/sdd/context-management.md",
				"CONVENTIONS.md"
		));
	}

	@Test
	void qwenSddEntrypointsArePresent() {
		assertFilesExist(List.of(
				".qwen/commands/sdd/specify.md",
				".qwen/commands/sdd/clarify.md",
				".qwen/commands/sdd/intake-diff.md",
				".qwen/commands/sdd/impact-map.md",
				".qwen/commands/sdd/synthesize-spec.md",
				".qwen/commands/sdd/plan.md",
				".qwen/commands/sdd/tasks.md",
				".qwen/commands/sdd/implement.md",
				".qwen/commands/sdd/review.md",
				".qwen/skills/sdd-spec-review/SKILL.md",
				".qwen/skills/sdd-plan/SKILL.md",
				".qwen/skills/sdd-task-slice/SKILL.md",
				".qwen/skills/sdd-test-gap/SKILL.md",
				".qwen/skills/sdd-review/SKILL.md",
				".qwen/skills/sdd-diff-intake/SKILL.md",
				".qwen/skills/sdd-impact-map/SKILL.md",
				".qwen/skills/sdd-openapi-diff/SKILL.md",
				".qwen/skills/sdd-dbml-diff/SKILL.md",
				".qwen/skills/sdd-spec-synthesis/SKILL.md",
				".qwen/agents/orchestrator.md",
				".qwen/agents/spec-writer.md",
				".qwen/agents/planner.md",
				".qwen/agents/builder.md",
				".qwen/agents/test-engineer.md",
				".qwen/agents/reviewer.md",
				".qwen/agents/security-reviewer.md",
				".qwen/agents/doc-diff-analyst.md",
				".qwen/agents/contract-analyst.md",
				".qwen/agents/data-model-analyst.md",
				".qwen/agents/spec-synthesizer.md"
		));
	}

	@Test
	void templateArtifactsArePresent() {
		assertFilesExist(List.of(
				"docs/specs/_template/spec.md",
				"docs/specs/_template/requirements.md",
				"docs/specs/_template/plan.md",
				"docs/specs/_template/research.md",
				"docs/specs/_template/intake.md",
				"docs/specs/_template/diff-map.md",
				"docs/specs/_template/impact-map.md",
				"docs/specs/_template/source-context.md",
				"docs/specs/_template/data-model.md",
				"docs/specs/_template/test-plan.md",
				"docs/specs/_template/tasks.md",
				"docs/specs/_template/quickstart.md",
				"docs/specs/_template/task-state.md",
				"docs/specs/_template/work-log.md",
				"docs/specs/_template/handoff.md",
				"docs/specs/_template/contracts/README.md",
				"docs/specs/_template/contracts/openapi-diff.md",
				"docs/specs/_template/contracts/dbml-diff.md"
		));
	}

	private static void assertFilesExist(List<String> paths) {
		for (String path : paths) {
			assertTrue(Files.isRegularFile(ROOT.resolve(path)), "Missing file: " + path);
		}
	}
}
