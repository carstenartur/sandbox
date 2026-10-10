from pathlib import Path
import hashlib, json, os, shutil, subprocess, sys, zipfile

BASE = '6dc33a833f187cdf89d3e99e7cfcf9e871cabf23'
SDK = '409083a58ab28d14fb3ce02adb2938dd9b92eb5f'
ARCHIVE_SHA = '9b15d2ccb83c76f9f393cb8cb97d0cb971d04dee667cc3f9d7f4ac28f582cb91'
FILES = {
 'SHA1Digest.java':'5818247460b60c2b93d9c52b081fb0dd1dab803df1720f54e1ca4f6d5efba534',
 'SHA256Digest.java':'dc5d6fe54171af285b6779f37b01ca3839b383348d7ea5dfd71b57fd7246db65',
 'LongDigest.java':'d1f4784bb7f6c668dcc40414bfd3e2556583d56ae2a7cdba7f426290b3072a8e',
 'MD4Digest.java':'98b69ba1f337dd1208b6920e43a1aad6be5992763a48629954fbb11d162fc81f',
 'MD5Digest.java':'65e341b50e57f21e3bfa0128af7a6bc6f9921662f0cc8135e22cd45743db1271',
}
EXAMPLES = [
 ('01-bouncy-castle-choice','SHA1Digest.java','INT','return ((u & v) | ((~u) & w));','return (w ^ (u & (v ^ w)));'),
 ('02-bouncy-castle-majority','MD4Digest.java','INT','return (u & v) | (u & w) | (v & w);','return (((v | w) & u) | (v & w));'),
 ('03-bouncy-castle-long','LongDigest.java','LONG','return ((x & y) ^ (x & z) ^ (y & z));','return (((y ^ z) & x) ^ (y & z));'),
 ('04-bouncy-castle-swapped-choice','MD5Digest.java','INT','return (u & w) | (v & ~w);','return (v ^ (w & (u ^ v)));'),
]
OLD = ['01-bouncy-castle-prime-product','02-jdt-core-shared-value','03-jdt-ui-multiple-lines','04-jdt-ui-nested-arithmetic']
ROOT = Path.cwd()
CONTROL = Path(__file__).resolve().parent
FIXTURES = ROOT/'sandbox_eclipse_help_swtbot_test/fixtures/mathematics'
LIB = ROOT/'sandbox_math_cleanup/lib'

def replace(path, old, new):
    text = path.read_text()
    assert text.count(old) == 1, (path, old)
    path.write_text(text.replace(old, new))

def prepare():
    assert subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip() == BASE
    assert not subprocess.check_output(['git','status','--porcelain']).strip()
    reports = ROOT.parent/'sdk/maven-build-contract/target/surefire-reports'
    receipts = list(reports.glob('sdk-distribution-*/reproducibility.json'))
    assert len(receipts) == 1, receipts
    receipt = json.loads(receipts[0].read_text())
    assert receipt['status'] == 'PASS' and receipt['sourceRevision'] == SDK
    assert receipt['cleanBuilds'] == 2 and receipt['freshConsumers'] == 2
    first = receipts[0].parent/'first'
    proofs = list(first.glob('*-qualification.json'))
    assert len(proofs) == 1
    proof = json.loads(proofs[0].read_text())
    assert proof['sourceRevision'] == SDK
    jars = [p for p in first.glob('*.jar') if hashlib.sha256(p.read_bytes()).hexdigest() == proof['standaloneJarSha256']]
    assert len(jars) == 1
    shutil.copyfile(jars[0], LIB/'regelsuche-optimization-sdk.jar')
    shutil.copyfile(proofs[0], LIB/'qualification.json')
    shutil.copyfile(receipts[0], LIB/'reproducibility.json')
    (LIB/'README.md').write_text('# Pinned Regelsuche optimization SDK\n\nSource revision: `'+SDK+'`.\nStandalone JAR SHA-256: `'+proof['standaloneJarSha256']+'`.\n\nTwo clean Maven SDK builds and two fresh consumers passed using the repository-owned distribution contract. Exact receipts are qualification.json and reproducibility.json. This is a development distribution, not a public Maven release. Generated Java needs no SDK at runtime. Existing license notices remain applicable.\n')
    tests = ROOT/'sandbox_math_cleanup_test/src/org/sandbox/jdt/math/tests'
    for name in ('BouncyCastleRuntimeCorpusTest.java','DocumentedSourceSnapshotTest.java'):
        shutil.copyfile(CONTROL/name,tests/name)
    subprocess.run(['git','apply','--check',str(CONTROL/'native.patch')],check=True)
    subprocess.run(['git','apply',str(CONTROL/'native.patch')],check=True)
    path = ROOT/'sandbox_math_cleanup_test/META-INF/MANIFEST.MF'
    replace(path,'Require-Bundle: ','Require-Bundle: bcprov,\n bcprov.source,\n ')
    evidence = ROOT/'sandbox_eclipse_help_swtbot_test/src/org/sandbox/jdt/ui/helper/views/EclipseHelpScreenshotEvidenceTest.java'
    for old, example in zip(OLD,EXAMPLES):
        replace(evidence,old,example[0])
        shutil.rmtree(FIXTURES/old)
        for suffix in ('.png','.provenance.json'):
            path = ROOT/('sandbox_math_cleanup_help/images/'+old+suffix)
            assert path.is_file()
            path.unlink()
    shutil.copyfile(CONTROL/'examples.html',ROOT/'sandbox_math_cleanup_help/html/examples.html')
    path = ROOT/'sandbox_math_cleanup_help/html/cleanup.html'
    replace(path,'<h2 id="configure">', '<h2 id="runtime-only">Runtime calculations, not constant folding</h2>\n<p>Authored constant declarations and constant-only changes are preserved.\nThe diagnostic is <code>CONSTANT_COMPUTATION_PRESERVED</code>. Genuine runtime\nsimplification and shared computations remain eligible. See the\n<a href="examples.html">source-bound runtime Bouncy Castle gallery</a>.</p>\n\n<h2 id="configure">')
    path = ROOT/'docs/ECLIPSE_HELP_COVERAGE.md'
    replace(path,'four pinned Bouncy Castle/JDT sources; constant evaluation, shared values, multi-statement propagation, nested expressions; explicit limits','four pinned whole Bouncy Castle production sources; runtime bitwise operations; constant-only changes preserved; explicit performance limits')
    (FIXTURES/'README.md').write_text('# Runtime mathematics Help fixtures\n\nFour complete unchanged source classes from bcprov.source-1.85.2.jar. BouncyCastleRuntimeCorpusTest checks each input against the SHA-256-pinned source archive and runs original/generated full digest implementations.\n\nThe same registered cleanup is then exercised in the native Clean Up wizard: exact preview, Apply, resolved bindings and byte-exact Undo precede screenshot/provenance retention. The source archive is not represented as an unrelated Git checkout. Expected outputs are test data, never optimization inputs.\n\nRun the standard mathematics module first, then the documented help-screenshots profile with MathematicsHelpScreenshotsSWTBotTest. Keep intentional constants unchanged. Operator counts do not establish measured throughput or cryptographic constant-time behavior.\n')
    provenance = ROOT/'sandbox_math_cleanup_test/fixtures/bcprov-1.85.2'
    provenance.mkdir(parents=True, exist_ok=True)
    (provenance/'provenance.json').write_text(json.dumps({'source':'Eclipse 2026-09 bcprov.source 1.85.2','sourceArchiveSha256':ARCHIVE_SHA,'gitRevision':None,'files':FILES},indent=2)+'\n')
    for example in EXAMPLES: (FIXTURES/example[0]).mkdir()
    print('Qualified SDK:',proof['sourceRevision'],proof['standaloneJarSha256'])

def sources():
    archives = list((Path.home()/'.m2/repository').glob('**/bcprov.source/1.85.2/*.jar'))
    archives = [p for p in archives if hashlib.sha256(p.read_bytes()).hexdigest() == ARCHIVE_SHA]
    assert len(archives) == 1, archives
    proof = json.loads((LIB/'qualification.json').read_text())
    with zipfile.ZipFile(archives[0]) as jar:
        for filename, expected in FILES.items():
            data = jar.read('org/bouncycastle/crypto/digests/'+filename)
            assert hashlib.sha256(data).hexdigest() == expected
        license = jar.read('META-INF/LICENSE.md')
        (FIXTURES/'LICENSE.md').write_bytes(license)
        (ROOT/'sandbox_math_cleanup_test/fixtures/bcprov-1.85.2/LICENSE.md').write_bytes(license)
        for id, filename, kind, before, after in EXAMPLES:
            data = jar.read('org/bouncycastle/crypto/digests/'+filename)
            assert before in data.decode()
            (FIXTURES/id/'before.java.txt').write_bytes(data)
            properties = dict(id=id,fileName=filename,packageName='org.bouncycastle.crypto.digests',kinds=kind,
                goal='LOWER_ESTIMATED_RUNTIME',bundles='bcprov',beforeFragment=before,afterFragment=after,
                sourceRepository='bcgit/bc-java',sourceArchive='bcprov.source-1.85.2.jar',sourceArchiveSha256=ARCHIVE_SHA,
                sourcePath='org/bouncycastle/crypto/digests/'+filename,sourceLines='1-'+str(len(data.splitlines())),
                excerptSha256=FILES[filename],upstreamSubmissionStatus='review-candidate; not submitted; no measured speedup',
                sdkSha256=proof['standaloneJarSha256'],sdkOptimizationSourceRevision=proof['sourceRevision'],adapterBaseRevision=BASE,
                adapterSourceState='general search and source-helper integration; actual bundle hash retained with capture')
            (FIXTURES/id/'example.properties').write_text(''.join(k+'='+v+'\n' for k,v in properties.items()))

def results():
    output = Path(os.environ['MATH_BC_ARCHIVE_OUTPUT'])
    for id, filename, kind, before, after in EXAMPLES:
        path = 'org/bouncycastle/crypto/digests/'+filename
        original = (output/'original'/path).read_bytes()
        generated = (output/'generated'/path).read_bytes()
        assert original == (FIXTURES/id/'before.java.txt').read_bytes()
        assert generated != original and after in generated.decode()
        (FIXTURES/id/'after.java.txt').write_bytes(generated)

{'prepare':prepare,'sources':sources,'results':results}[sys.argv[1]]()
