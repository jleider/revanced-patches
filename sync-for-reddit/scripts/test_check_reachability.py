#!/usr/bin/env python3
"""Offline reachability regression tests; run with python3 -I."""
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import textwrap
import unittest

ROOT = Path(__file__).resolve().parents[1]   # sync-for-reddit/
CHECKER = Path(__file__).with_name('check_reachability.py')
HOOK = 'invoke-static {}, Lapp/revanced/extension/Test;->hook()V'


class ReachabilityTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.patched = self.root / 'patched'
        self.original = self.root / 'original'
        self.original.mkdir()

    def write(self, body, root=None, class_path='example/Test.smali'):
        file = (root or self.patched) / 'smali_classes2' / class_path
        file.parent.mkdir(parents=True, exist_ok=True)
        file.write_text('.class public Lexample/Test;\n.super Ljava/lang/Object;\n'
                        '.method public static run()V\n.locals 2\n'
                        + textwrap.dedent(body) + '\n.end method\n')
        return file

    def run_checker(self, root=None):
        result = subprocess.run([sys.executable, '-I', str(CHECKER),
                                 str(root or self.patched), '--original', str(self.original)],
                                capture_output=True, text=True)
        self.assertEqual(result.stderr, '')
        return result

    def test_current_build(self):
        result = subprocess.run([sys.executable, '-I', str(CHECKER),
                                 str(ROOT / 'out/patched-smali')], capture_output=True, text=True)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertRegex(result.stdout, r'checked [1-9]\d* methods with extension calls; 0 unreachable')
        print('positive build: ' + result.stdout.strip())

    def test_request_queue_negative_control(self):
        files = list((ROOT / 'out/patched-smali').glob('*/com/android/volley/RequestQueue.smali'))
        self.assertEqual(len(files), 1)
        source = files[0]
        text = source.read_text()
        begin = re.search(r'(:[\w$-]+)\s+(?:nop\s+)?(?:\.line \d+\s+)?'
                          r'invoke-virtual [^\n]*->beginRequest\(', text)
        self.assertIsNotNone(begin)
        self.assertIn('if-eqz v0, :cond_0', text)
        text = text.replace('if-eqz v0, :cond_0', 'if-eqz v0, ' + begin.group(1), 1)
        destination = self.patched / source.relative_to(ROOT / 'out/patched-smali')
        destination.parent.mkdir(parents=True)
        destination.write_text(text)
        result = self.run_checker()
        self.assertEqual(result.returncode, 1, result.stdout)
        hook_lines = [line for line in result.stdout.splitlines()
                      if 'unreachable extension invoke' in line]
        self.assertEqual(len(hook_lines), 1, result.stdout)
        self.assertIn('FixImgurPatch;->localResponse', hook_lines[0])
        self.assertIn('add(Lcom/android/volley/Request;)', hook_lines[0])
        print('negative RequestQueue: ' + hook_lines[0])
        print(result.stdout.splitlines()[-1])

    def test_switches_try_handlers_and_payloads(self):
        self.write('''
            .annotation runtime Lexample/Annotation;
                value = {
                    "not an instruction",
                    "# nor a comment"
                }
            .end annotation
            .param p0, "unused"
            .prologue
            :try_start
            const/4 v0, 0x0
            :try_end
            .catch Ljava/lang/Exception; {:try_start .. :try_end} :handler
            .catchall {:try_start .. :try_end} :catchall
            packed-switch v0, :packed_table
            goto/32 :sparse
            :packed_case
        ''' + HOOK + '''
            return-void
            :sparse
            sparse-switch v0, :sparse_table
            goto/16 :done
            :sparse_case
        ''' + HOOK + '''
            return-void
            :handler
            move-exception v1
        ''' + HOOK + '''
            throw v1
            :catchall
            move-exception v1
        ''' + HOOK + '''
            throw v1
            :done
            fill-array-data v0, :array
            return-void
            :packed_table
            .packed-switch 0x0
                :packed_case
            .end packed-switch
            :sparse_table
            .sparse-switch
                0x2 -> :sparse_case
            .end sparse-switch
            :array
            .array-data 1
                0x1t
                0x2t
            .end array-data
        ''')
        result = self.run_checker()
        self.assertEqual(result.returncode, 0, result.stdout)
        print('synthetic try/catch + packed/sparse switch: ' + result.stdout.strip())

    def test_dead_instructions_and_original_baseline(self):
        self.write('''
            goto :end_old
            const-string v0, "already dead # :literal"
            :end_old
            return-void
        ''', root=self.original)
        self.write(HOOK + '''
            goto :end_new
            const-string v0, "already dead # :literal"
            const/4 v0, 0x1
            nop
            :end_new
            return-void
        ''')
        result = self.run_checker()
        self.assertEqual(result.returncode, 1)
        self.assertIn('unreachable instruction: const/4 v0, 0x1', result.stdout)
        self.assertNotIn('already dead', result.stdout)
        self.assertIn('1 unreachable', result.stdout)

    def test_conditional_fallthrough_and_extension_exclusion(self):
        self.write('''
            const/4 v0, 0x0
            if-eqz v0, :taken
        ''' + HOOK + '''
            :taken
        ''' + HOOK + '''
            return-void
        ''')
        self.write('return-void\n' + HOOK, class_path='app/revanced/extension/Ignored.smali')
        result = self.run_checker()
        self.assertEqual(result.returncode, 0, result.stdout)
        self.assertIn('checked 1 methods', result.stdout)

    def test_unreachable_extension_is_never_discounted(self):
        body = 'return-void\n' + HOOK
        self.write(body, root=self.original)
        self.write(body)
        result = self.run_checker()
        self.assertEqual(result.returncode, 1)
        self.assertIn('unreachable extension invoke', result.stdout)

    def test_try_range_is_half_open(self):
        self.write('''
            goto :try_end
            :try_start
            const/4 v0, 0x0
            :try_end
            return-void
            .catchall {:try_start .. :try_end} :handler
            :handler
            move-exception v0
        ''' + HOOK + '''
            throw v0
        ''')
        result = self.run_checker()
        self.assertEqual(result.returncode, 1)
        self.assertIn('unreachable extension invoke', result.stdout)

    def test_whole_method_replacement_allows_dead_original(self):
        self.write(f"""
            {HOOK}
            return-void
            const/4 v0, 0x1
            if-eqz v0, :skip
            const/4 v1, 0x2
            :skip
            return-void
        """)
        self.assertEqual(self.run_checker().returncode, 0)

    def test_replacement_does_not_hide_a_dead_extension_call(self):
        self.write(f"""
            {HOOK}
            return-void
            {HOOK}
            return-void
        """)
        result = self.run_checker()
        self.assertEqual(result.returncode, 1)
        self.assertIn('unreachable extension invoke', result.stdout)

    def test_branch_before_return_is_not_a_replacement(self):
        self.write(f"""
            const/4 v0, 0x1
            if-eqz v0, :end
            {HOOK}
            :end
            return-void
            const/4 v1, 0x2
        """)
        self.assertEqual(self.run_checker().returncode, 1)

    def test_missing_target_fails(self):
        self.write(HOOK + '\ngoto :missing')
        result = self.run_checker()
        self.assertEqual(result.returncode, 1)
        self.assertIn('CFG error: unknown label :missing', result.stdout)


if __name__ == '__main__':
    unittest.main(verbosity=2)
