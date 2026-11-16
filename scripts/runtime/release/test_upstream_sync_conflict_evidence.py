"""Fail-closed source-contract tests for scheduled upstream sync conflict handling.

These lexical tests do not prove the safety of an upstream merge. Actual
conflicts still require human code review and all native/data acceptance gates.
"""
from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[3]
WORKFLOW = ROOT / ".github" / "workflows" / "upstream-sync.yml"


class UpstreamSyncEvidenceContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.workflow = WORKFLOW.read_text(encoding="utf-8")

    def test_conflicted_paths_and_both_source_shas_are_saved_before_abort(self):
        workflow = self.workflow
        required = (
            'git rev-parse origin/master',
            'git rev-parse upstream/master',
            'git diff --name-only --diff-filter=U | LC_ALL=C sort',
            'git ls-files -u',
        )
        for clause in required:
            with self.subTest(clause=clause):
                self.assertIn(clause, workflow)
                self.assertLess(workflow.index(clause), workflow.index("git merge --abort"))
        self.assertIn("git merge --abort || true", workflow)
        self.assertIn("exit 1", workflow.split("git merge --abort || true", 1)[1])

    def test_failed_merge_artifact_cannot_be_silently_skipped(self):
        workflow = self.workflow
        self.assertIn("if: ${{ failure() && steps.merge.outcome == 'failure' }}", workflow)
        self.assertIn("name: ie2-upstream-merge-conflict-evidence", workflow)
        self.assertIn("path: .ci/upstream-sync/", workflow)
        self.assertIn("if-no-files-found: error", workflow)
        self.assertIn("retention-days: 14", workflow)

    def test_upstream_changes_are_never_directly_pushed_to_master(self):
        workflow = self.workflow
        self.assertIn('BRANCH="automation/upstream-sync"', workflow)
        self.assertIn('git checkout -B "$BRANCH" origin/master', workflow)
        self.assertIn('git push --force-with-lease origin "$BRANCH"', workflow)
        self.assertNotIn('git push origin master', workflow)
        self.assertNotIn('git merge -X theirs', workflow)
        self.assertNotIn('git merge -X ours', workflow)


if __name__ == "__main__":
    unittest.main()
