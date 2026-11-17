"""Execute IE2's release authorization shell with a fake git/GitHub CLI.

This is a release-policy regression, not a real tag, network call, or native
AlbionMC acceptance. Any wrong source/version or preexisting tag must fail
before the publish job can create a release.
"""
from pathlib import Path
import os
import subprocess
import tempfile
import textwrap
import unittest

ROOT = Path(__file__).resolve().parents[3]
WORKFLOW = ROOT / ".github/workflows/ci.yml"
VERSION = "2.0.19"
OUTPUT = "SF_InfinityExpansion2.0.19.jar"


def authorization_script(workflow):
    start = workflow.split("      - name: Validate explicit release publication request\n", 1)[1]
    segment = start.split("      - name: Publish canonical raw JAR release asset\n", 1)[0]
    return textwrap.dedent(segment.split("        run: |\n", 1)[1]).rstrip() + "\n"


class ImmutableIE2ReleaseSourceTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.workflow = WORKFLOW.read_text(encoding="utf-8")
        cls.shell = authorization_script(cls.workflow)

    def execute(self, ref="refs/heads/master", head="verified-sha",
                master="verified-sha", confirmation=VERSION, event="workflow_dispatch",
                existing_tag=False, existing_release=False):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            binaries = root / "bin"
            binaries.mkdir()
            (root / "dist").mkdir()
            (root / "docs/releases").mkdir(parents=True)
            (root / "dist" / OUTPUT).write_bytes(b"candidate jar - isolated fake only")
            (root / "docs/releases" / (VERSION + ".md")).write_text("Candidate notes\n")
            output = root / "github-output"
            calls = root / "calls"
            git = binaries / "git"
            git.write_text(
                "#!/bin/sh\n"
                "printf 'git %s\\n' \"$*\" >> \"$FAKE_CALLS\"\n"
                "case \"$1\" in\n"
                "  fetch) exit 0;;\n"
                "  rev-parse)\n"
                "    if [ \"$2\" = refs/remotes/origin/master ]; then printf '%s\\n' \"$FAKE_MASTER\"; exit 0; fi\n"
                "    if [ \"$2\" = HEAD ]; then printf '%s\\n' \"$FAKE_HEAD\"; exit 0; fi\n"
                "    exit 98;;\n"
                "  ls-remote) exit \"$FAKE_TAG_EXISTS\";;\n"
                "esac\n"
                "exit 97\n")
            git.chmod(0o755)
            gh = binaries / "gh"
            gh.write_text(
                "#!/bin/sh\n"
                "printf 'gh %s\\n' \"$*\" >> \"$FAKE_CALLS\"\n"
                "if [ \"$1\" = release ] && [ \"$2\" = view ]; then exit \"$FAKE_RELEASE_EXISTS\"; fi\n"
                "exit 96\n")
            gh.chmod(0o755)
            # ls-remote returns zero if an existing tag is found, one otherwise;
            # gh release view uses the same convention.
            env = os.environ.copy()
            env.update({
                "PATH": str(binaries) + os.pathsep + env.get("PATH", ""),
                "GITHUB_REF": ref,
                "GITHUB_EVENT_NAME": event,
                "GITHUB_SHA": head,
                "GITHUB_REPOSITORY": "wickidcow/SF_InfinityExpansion2",
                "GITHUB_OUTPUT": str(output),
                "VERSION": VERSION,
                "OUTPUT_NAME": OUTPUT,
                "CONFIRM_VERSION": confirmation,
                "FAKE_MASTER": master,
                "FAKE_HEAD": head,
                "FAKE_TAG_EXISTS": "0" if existing_tag else "1",
                "FAKE_RELEASE_EXISTS": "0" if existing_release else "1",
                "FAKE_CALLS": str(calls),
            })
            run = subprocess.run(["bash", "-c", self.shell], cwd=root, env=env,
                                 capture_output=True, text=True, check=False)
            return run.returncode, output.read_text() if output.exists() else "", (
                calls.read_text() if calls.exists() else ""), run.stderr

    def test_release_only_from_exact_current_master_with_explicit_version(self):
        code, output, calls, detail = self.execute()
        self.assertEqual(0, code, detail)
        self.assertEqual("tag=v2.0.19\npublish=true\n", output)
        self.assertIn("git fetch --no-tags origin", calls)
        self.assertIn("git rev-parse HEAD", calls)
        self.assertIn("gh release view v2.0.19", calls)

    def test_automatic_master_push_publishes_only_new_verified_version(self):
        code, output, calls, detail = self.execute(event="push")
        self.assertEqual(0, code, detail)
        self.assertEqual("tag=v2.0.19\npublish=true\n", output)
        self.assertIn("gh release view v2.0.19", calls)

    def test_existing_version_is_noop_on_automatic_master_push(self):
        code, output, calls, detail = self.execute(event="push", existing_release=True)
        self.assertEqual(0, code, detail)
        self.assertEqual("publish=false\n", output)
        self.assertNotIn("git ls-remote", calls)

    def test_refuses_dispatch_from_candidate_branch(self):
        code, output, calls, _ = self.execute(ref="refs/heads/release/ie2-2.0.19")
        self.assertNotEqual(0, code)
        self.assertEqual("", output)
        self.assertEqual("", calls, "Reject branch before any release API or git lookup")

    def test_refuses_stale_master_or_other_checkout(self):
        code, output, calls, _ = self.execute(master="newer-verified-commit")
        self.assertNotEqual(0, code)
        self.assertEqual("", output)
        self.assertNotIn("gh ", calls)

    def test_refuses_wrong_explicit_confirmation(self):
        code, output, calls, _ = self.execute(confirmation="v2.0.19")
        self.assertNotEqual(0, code)
        self.assertEqual("", output)
        self.assertNotIn("gh ", calls)

    def test_refuses_existing_release(self):
        code, output, calls, _ = self.execute(existing_release=True)
        self.assertNotEqual(0, code)
        self.assertEqual("", output)
        self.assertIn("gh release view", calls)

    def test_refuses_existing_tag(self):
        code, output, calls, _ = self.execute(existing_tag=True)
        self.assertNotEqual(0, code)
        self.assertEqual("", output)
        self.assertIn("git ls-remote", calls)

    def test_release_job_requires_native_and_source_gates(self):
        workflow = self.workflow
        self.assertIn('VERSION: "2.0.19"', workflow)
        self.assertIn('OUTPUT_NAME: "SF_InfinityExpansion2.0.19.jar"', workflow)
        publisher = workflow.split("  publish:\n    name: Publish validated immutable IE2 release", 1)[1]
        self.assertIn("needs: [compatibility, source-safety, native-runtime]", publisher)
        self.assertIn("github.event_name == 'workflow_dispatch' && inputs.publish == true", publisher)
        self.assertIn("github.event_name == 'push' && github.ref == 'refs/heads/master'", publisher)
        self.assertIn("gh release create", publisher)
        self.assertNotIn("--clobber", publisher)
        self.assertLess(publisher.index('test "$GITHUB_REF" = "refs/heads/master"'),
                        publisher.index('gh release view "$TAG"'))
        self.assertLess(publisher.index('test "$GITHUB_SHA" = "$MASTER_SHA"'),
                        publisher.index('gh release create "$TAG"'))
        self.assertIn("python3 -m unittest discover -s scripts/runtime/release -p 'test_*.py' -v",
                      workflow)


if __name__ == "__main__":
    unittest.main()
