from pathlib import Path
import hashlib
import io
import json
import os
import re
import sys
import urllib.error
import urllib.request
import zipfile
root = Path(sys.argv[1])
ARTIFACT = 11668316074
DIGEST = '7fed8589c28e8d2d80b9b4f0a35a82db65077dc69e7e57ddf7e80f3ce0e6539b'
SOURCE = 'c48d160800a5d1ab82a7adbb0804be768256e94b'
TREE = '84d305bdd23d94fcc36762b6b0c187655fd3f889'
class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None
url = f'https://api.github.com/repos/carstenartur/Regelsuche/actions/artifacts/{ARTIFACT}/zip'
request = urllib.request.Request(url, headers={'Authorization':'Bearer '+os.environ['GH_TOKEN'], 'Accept':'application/vnd.github+json'})
try:
    with urllib.request.build_opener(NoRedirect).open(request, timeout=60) as response:
        data = response.read(100_000_001)
except urllib.error.HTTPError as response:
    if response.code not in (301,302,303,307,308): raise
    location = response.headers['Location']
    assert location.startswith('https://')
    # Do not forward credentials to the signed artifact host.
    with urllib.request.urlopen(location, timeout=120) as artifact:
        data = artifact.read(100_000_001)
assert len(data) <= 100_000_000
assert hashlib.sha256(data).hexdigest() == DIGEST
with zipfile.ZipFile(io.BytesIO(data)) as archive:
    names = archive.namelist()
    def unique(predicate):
        matches = [name for name in names if predicate(name)]
        assert len(matches) == 1, matches
        return matches[0]
    record_path = unique(lambda n: '/sdk-distribution-' in n and n.endswith('/reproducibility.json'))
    record_bytes = archive.read(record_path)
    record = json.loads(record_bytes)
    assert record == {'status':'PASS','sourceRevision':SOURCE,'sourceTree':TREE,'cleanBuilds':2,
                      'sdkTestsPerBuild':105,'freshConsumers':2,'identicalDistributionFiles':10}, record
    first = record_path.rsplit('/',1)[0] + '/first/'
    jar_path = unique(lambda n: n.startswith(first) and n.endswith('-all.jar'))
    qualification_path = unique(lambda n: n.startswith(first) and n.endswith('-qualification.json'))
    jar = archive.read(jar_path)
    receipt_bytes = archive.read(qualification_path)
    receipt = json.loads(receipt_bytes)
    digest = hashlib.sha256(jar).hexdigest()
    assert receipt['sourceRevision'] == SOURCE and receipt['standaloneJarSha256'] == digest
    assert jar == archive.read(jar_path.replace('/first/','/second/',1))
    assert receipt_bytes == archive.read(qualification_path.replace('/first/','/second/',1))
    with zipfile.ZipFile(io.BytesIO(jar)) as standalone:
        assert 'de/regelsuche/sdk/optimization/SearchDerivation.class' in standalone.namelist()
        assert 'de/regelsuche/sdk/optimization/SearchDerivations.class' in standalone.namelist()
        provenance = json.loads(standalone.read('META-INF/regelsuche/optimization-provenance.json'))
        assert provenance['sourceRevision'] == SOURCE
    lib = root/'sandbox_math_cleanup/lib'
    (lib/'regelsuche-optimization-sdk.jar').write_bytes(jar)
    (lib/'qualification.json').write_bytes(receipt_bytes)
    (lib/'reproducibility.json').write_bytes(record_bytes)
    (lib/'README.md').write_text(f'''# Pinned Regelsuche optimization SDK\n\nSource revision: `{SOURCE}`.\nStandalone JAR SHA-256: `{digest}`.\n\nThe repository-owned distribution contract passed two clean builds, 105 SDK tests\nper build, two freshly compiled consumers (including selected-path replay), and\nbyte equality of all ten distribution files. Receipts are qualification.json and\nreproducibility.json. Source run: 38046991786; artifact {ARTIFACT}.\n\nThis is a source-bound development distribution, not a public Maven release.\nGenerated Java needs no SDK runtime. Existing license notices remain applicable.\n''')
    fixtures = list((root/'sandbox_eclipse_help_swtbot_test/fixtures/mathematics').glob('*/example.properties'))
    assert len(fixtures) == 4, fixtures
    for path in fixtures:
        text = path.read_text()
        for key,value in [('sdkSha256',digest),('sdkOptimizationSourceRevision',SOURCE)]:
            text,count = re.subn(r'(?m)^'+key+r'=.*$',key+'='+value,text)
            assert count == 1, (path,key,count)
        path.write_text(text)
    print('IMPORTED_VERIFIED_SDK', json.dumps({'artifact':ARTIFACT,'artifactSha256':DIGEST,'jarSha256':digest,**record}))
