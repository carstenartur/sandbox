from pathlib import Path
import shutil
control = Path(__file__).resolve().parent
source = Path('sandbox_eclipse_help_swtbot_test/src/org/sandbox/jdt/ui/helper/views')
for name in ('BundleClassFingerprint.java', 'BundleClassFingerprintTest.java'):
    shutil.copyfile(control/name, source/name)
path = source/'MathematicsHelpScreenshots.java'
text = path.read_text()
before = '            if (Files.isRegularFile(adapter)) evidence.put("adapterBundleSha256", sha256(Files.readAllBytes(adapter))); //$NON-NLS-1$'
after = '''            evidence.put("adapterClassesSha256", BundleClassFingerprint.sha256(adapter)); //$NON-NLS-1$
            // The complete archive changes with the Tycho build qualifier. Retain
            // it in the execution log, not the reproducible source/image metadata.
            if (Files.isRegularFile(adapter)) System.out.println("MATHEMATICS_CAPTURE_BUNDLE " + id //$NON-NLS-1$
                    + " sha256=" + sha256(Files.readAllBytes(adapter))); //$NON-NLS-1$'''
assert text.count(before) == 1
path.write_text(text.replace(before, after))
