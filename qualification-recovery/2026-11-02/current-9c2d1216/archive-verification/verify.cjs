'use strict';
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const zlib = require('node:zlib');
const {execFileSync} = require('node:child_process');
const {TextDecoder} = require('node:util');
const root = '/workspace/scratch/0eb32b15fbb4/recovery';
const stage = path.join(root, 'complete-current-evidence-9c2d1216');
const archive = stage + '.tar.gz';
const output = __dirname;
const extraction = path.join(output, 'extracted');
const source = '/tmp/sandbox-bls-on-native';
const expectedHead = '9c2d121686dd2bfcd774d820312ca603d8fc4b4f';
const expectedTree = 'd9736c8cd1e397508784e7d79fd1cb08ac978312';
const expectedArchiveSha = '253eb11d40ef97685b5469de49e353c889b784e179f95274b26686fac4765def';
const expectedManifestSha = 'cedda43c1733bc0b009467ca996bc54ceff133e97d99bf0e078a157a28a5137a';
const expectedSumsSha = '85ec13ffe475df27d8aaaf26711cf4d44284f201d1da70233d33b1383f2761c7';
const started = new Date().toISOString();
function assert(condition, message) { if (!condition) throw Error(message); }
function digest(buffer, algorithm = 'sha256') { return crypto.createHash(algorithm).update(buffer).digest('hex'); }
function hashFile(p) {
  const stat = fs.lstatSync(p);
  assert(stat.isFile(), 'Expected regular file: ' + p);
  const hash = crypto.createHash('sha256'), chunk = Buffer.allocUnsafe(1024 * 1024), fd = fs.openSync(p, 'r');
  try { let count; while ((count = fs.readSync(fd, chunk, 0, chunk.length, null))) hash.update(chunk.subarray(0, count)); }
  finally { fs.closeSync(fd); }
  return hash.digest('hex');
}
function snapshot(p) { const s = fs.lstatSync(p); return {dev:s.dev, ino:s.ino, bytes:s.size, mtimeMs:s.mtimeMs, ctimeMs:s.ctimeMs}; }
function json(p) { return JSON.parse(fs.readFileSync(p, 'utf8')); }
function text(p) { return fs.readFileSync(p, 'utf8').trim(); }
function git(...args) { return execFileSync('git', ['-C', source, ...args], {encoding:'utf8', stdio:['ignore','pipe','pipe'], timeout:30000, maxBuffer:8*1024*1024, env:{...process.env,GIT_OPTIONAL_LOCKS:'0'}}); }
function identity() {
  const result = {head:git('rev-parse','HEAD').trim(), tree:git('rev-parse','HEAD^{tree}').trim(), trackedStatus:git('status','--porcelain','--untracked-files=no').trim()};
  assert(result.head === expectedHead && result.tree === expectedTree && result.trackedStatus === '', 'Qualification source identity changed');
  return result;
}
function safeRelative(name, allowRoot = false) {
  assert(typeof name === 'string' && !/[\0\r\n\\]/.test(name), 'Invalid member name');
  if (allowRoot && name === '.') return name;
  assert(name !== '' && !name.startsWith('/') && !/^[A-Za-z]:/.test(name), 'Absolute/empty member path');
  assert(name.split('/').every(x => x !== '' && x !== '.' && x !== '..'), 'Unsafe path component: ' + name);
  assert(path.posix.normalize(name) === name, 'Noncanonical member path: ' + name);
  return name;
}
function checkSourceIdentity(value, label) {
  assert(value.head === expectedHead && value.tree === expectedTree && value.trackedStatus === '' && value.checkout === source, label + ' mismatch');
}
function walk(directory, prefix = '', omittedRoot = []) {
  const result = new Map();
  function visit(p, relative, first) {
    const s = fs.lstatSync(p);
    assert(s.isFile() || s.isDirectory(), 'Link/special filesystem entry: ' + p);
    result.set(relative || '.', {type:s.isDirectory()?'directory':'file', bytes:s.size, mode:s.mode & 0o777});
    if (s.isDirectory()) for (const name of fs.readdirSync(p).sort()) if (!first || !omittedRoot.includes(name)) visit(path.join(p,name), relative ? relative+'/'+name : name, false);
  }
  visit(directory,prefix,true);
  return result;
}
function sameInventory(actual, expected, label) {
  assert(actual.size === expected.size, label + ' count mismatch: ' + actual.size + ' vs ' + expected.size);
  for (const [name, entry] of actual) {
    const reference = expected.get(name);
    assert(reference && entry.type === reference.type, label + ' unexpected/mistyped entry: ' + name);
    if (entry.type === 'file') assert(entry.bytes === reference.bytes, label + ' size mismatch: ' + name);
    assert(entry.mode === reference.mode, label + ' mode mismatch: ' + name);
  }
}
async function main() {
  for (const destination of [extraction,path.join(output,'verification.json'),path.join(output,'review.md')]) {
    try { fs.lstatSync(destination); throw Error('Refusing existing verification output: ' + destination); }
    catch (error) { if (error.code !== 'ENOENT') throw error; }
  }
  const archiveBefore = snapshot(archive), sourceBefore = identity();
  assert(archiveBefore.bytes === 52199315, 'Unexpected archive size');
  assert(hashFile(archive) === expectedArchiveSha, 'Archive digest mismatch');
  assert(hashFile(path.join(stage,'MANIFEST.json')) === expectedManifestSha, 'Stage manifest digest mismatch');
  assert(hashFile(path.join(stage,'SHA256SUMS')) === expectedSumsSha, 'Stage sums digest mismatch');
  assert(text(archive+'.sha256') === expectedArchiveSha+'  '+path.basename(archive), 'Outer SHA256 sidecar mismatch');
  const receipt = json(archive+'.receipt.json');
  assert(receipt.archive === archive && receipt.bytes === archiveBefore.bytes && receipt.sha256 === expectedArchiveSha && receipt.inputFiles === 11167 && receipt.manifestSha256 === expectedManifestSha && receipt.sha256sumsSha256 === expectedSumsSha && receipt.gzipIntegrity === 'PASS', 'Outer receipt mismatch');
  checkSourceIdentity(receipt.source, 'Receipt source');
  const gzipStart = Date.now();
  execFileSync('gzip',['-t',archive],{stdio:['ignore','pipe','pipe'],timeout:60000});
  const gzipMs = Date.now()-gzipStart;
  const manifest = json(path.join(stage,'MANIFEST.json'));
  assert(manifest.schema === 'sandbox.complete-current-evidence/v1', 'Unexpected manifest schema');
  checkSourceIdentity(manifest.source.before,'Manifest before');
  checkSourceIdentity(manifest.source.afterCopy,'Manifest afterCopy');
  assert(JSON.stringify(manifest.generatedMetadata) === JSON.stringify(['MANIFEST.json','SHA256SUMS']), 'Unexpected generated metadata');
  const expected = new Map([['.',{type:'directory',mode:0o755}]]), declared = new Map();
  for (const entry of manifest.entries) {
    safeRelative(entry.archivePath);
    assert(!declared.has(entry.archivePath), 'Duplicate manifest path: '+entry.archivePath);
    assert(entry.type === 'directory' || entry.type === 'file', 'Unsupported manifest type');
    assert(path.isAbsolute(entry.originalPath), 'Non-absolute original path');
    if(entry.type === 'file') assert(/^[a-f0-9]{64}$/.test(entry.sha256) && Number.isSafeInteger(entry.bytes) && entry.bytes >= 0, 'Invalid file metadata');
    declared.set(entry.archivePath,entry);
    expected.set(entry.archivePath,entry);
  }
  const inputFiles = manifest.entries.filter(e=>e.type==='file');
  assert(inputFiles.length === 11167, 'Input file count mismatch');
  for (const filename of manifest.generatedMetadata) expected.set(filename,{type:'file',mode:0o644,bytes:fs.statSync(path.join(stage,filename)).size,sha256:filename==='MANIFEST.json'?expectedManifestSha:expectedSumsSha});
  for (const name of [...expected.keys()]) for(let parent=path.posix.dirname(name); parent!=='.'; parent=path.posix.dirname(parent)) {
    if(expected.has(parent)) assert(expected.get(parent).type==='directory','Non-directory parent');
    else expected.set(parent,{type:'directory',mode:0o755});
  }
  const list = fs.readFileSync(archive+'.file-list.nul');
  const expectedList = Buffer.from([...expected.keys()].sort().map(p=>p==='.'?'.':'./'+p).join('\0')+'\0');
  assert(list.equals(expectedList),'External regular NUL inventory differs from expected entries');
  const stageInventoryBefore=walk(stage);
  const stageExtras=[...stageInventoryBefore].filter(([name])=>!expected.has(name)).map(([name,entry])=>({path:name,...entry,snapshot:snapshot(path.join(stage,name)),sha256:entry.type==='file'?hashFile(path.join(stage,name)):null}));
  for(const extra of stageExtras){
    if(extra.path==='runs/retention/reports/bc-mathematical/.retention.json.dOF5kS'){
      const listed=declared.get('runs/retention/reports/bc-mathematical/retention.json');
      assert(listed,'Listed retention file is missing');
      const originalExtraPath=path.join(path.dirname(listed.originalPath),path.basename(extra.path));
      let originalExtraExists=true;try{fs.lstatSync(originalExtraPath);}catch(error){if(error.code!=='ENOENT')throw error;originalExtraExists=false;}
      extra.originalSourceCounterpart={path:originalExtraPath,exists:originalExtraExists};
      extra.listedRetention={archivePath:listed.archivePath,originalPath:listed.originalPath,bytes:listed.bytes,sha256:listed.sha256,sameSha256:listed.sha256===extra.sha256};
    }
  }
  sameInventory(new Map([...stageInventoryBefore].filter(([name])=>expected.has(name))),expected,'Manifested original stage inventory');
  fs.mkdirSync(extraction,{mode:0o755});
  const observed = new Map();
  let queue=Buffer.alloc(0), current=null, padding=0, zeroHeaders=0, ended=false, longName=null;
  let physicalHeaders=0,longNameHeaders=0,uncompressedBytes=0,regularBytes=0,trailingZeroBytes=0;
  const decoder = new TextDecoder('utf-8',{fatal:true});
  function decode(buffer) { return decoder.decode(buffer); }
  function stringField(buffer) { const zero=buffer.indexOf(0);return decode(zero<0?buffer:buffer.subarray(0,zero)); }
  function numberField(buffer,label) {
    assert((buffer[0]&0x80)===0,'Unexpected base-256 numeric field: '+label);
    const value=buffer.toString('ascii').replace(/\0.*$/s,'').trim();
    assert(value===''||/^[0-7]+$/.test(value),'Invalid octal '+label);
    const n=value===''?0:parseInt(value,8);assert(Number.isSafeInteger(n)&&n>=0,'Invalid '+label);return n;
  }
  function finishCurrent() {
    const entry=current;current=null;
    padding=(512-entry.bytes%512)%512;
    if(entry.metadata){
      const bytes=Buffer.concat(entry.parts);
      assert(bytes.length>1&&bytes[bytes.length-1]===0&&bytes.subarray(0,-1).indexOf(0)<0,'Malformed GNU long name');
      longName=decode(bytes.subarray(0,-1));
    }else{
      fs.closeSync(entry.fd);
      const sha256=entry.hash.digest('hex');
      assert(sha256===expected.get(entry.name).sha256,'TAR payload hash mismatch: '+entry.name);
      observed.get(entry.name).sha256=sha256;
      regularBytes+=entry.bytes;
    }
  }
  const compressed=fs.createReadStream(archive),gunzip=zlib.createGunzip();
  compressed.on('error',error=>gunzip.destroy(error));compressed.pipe(gunzip);
  for await(const chunk of gunzip){
    uncompressedBytes+=chunk.length;
    queue=queue.length?Buffer.concat([queue,chunk]):chunk;
    while(true){
      if(current){
        if(current.remaining===0){finishCurrent();continue;}
        if(queue.length===0)break;
        const n=Math.min(current.remaining,queue.length),data=queue.subarray(0,n);queue=queue.subarray(n);
        if(current.metadata)current.parts.push(Buffer.from(data));
        else{current.hash.update(data);let offset=0;while(offset<n)offset+=fs.writeSync(current.fd,data,offset,n-offset);}
        current.remaining-=n;continue;
      }
      if(padding){if(queue.length===0)break;const n=Math.min(padding,queue.length);assert(queue.subarray(0,n).every(b=>b===0),'Nonzero TAR padding');queue=queue.subarray(n);padding-=n;continue;}
      if(ended){assert(queue.every(b=>b===0),'Nonzero trailing data after TAR terminator');trailingZeroBytes+=queue.length;queue=Buffer.alloc(0);break;}
      if(queue.length<512)break;
      const h=queue.subarray(0,512);queue=queue.subarray(512);
      if(h.every(b=>b===0)){zeroHeaders++;if(zeroHeaders===2)ended=true;continue;}
      assert(zeroHeaders===0,'Nonzero member after single TAR zero block');
      physicalHeaders++;
      const recorded=numberField(h.subarray(148,156),'checksum');
      let calculated=0;for(let i=0;i<512;i++)calculated+=(i>=148&&i<156)?32:h[i];
      assert(recorded===calculated,'TAR header checksum mismatch');
      const magic=h.subarray(257,263).toString('ascii');
      assert(magic==='ustar\0'||magic==='ustar ','Unexpected TAR format');
      let name=stringField(h.subarray(0,100));
      if(magic==='ustar\0'){const prefix=stringField(h.subarray(345,500));if(prefix)name=prefix+'/'+name;}
      const type=h[156]===0?'0':String.fromCharCode(h[156]);
      const bytes=numberField(h.subarray(124,136),'size');
      const mode=numberField(h.subarray(100,108),'mode');
      assert(numberField(h.subarray(108,116),'uid')===0&&numberField(h.subarray(116,124),'gid')===0,'Unexpected TAR ownership');
      assert(numberField(h.subarray(136,148),'mtime')===0,'Unexpected TAR mtime');
      if(type==='L'){
        assert(longName===null&&name==='././@LongLink'&&bytes<=1024*1024,'Unexpected GNU name extension');
        longNameHeaders++;current={metadata:true,bytes,remaining:bytes,parts:[]};continue;
      }
      assert(type==='0'||type==='5','Links/special/PAX entries are not permitted: '+type);
      assert(stringField(h.subarray(157,257))==='','Unexpected link target');
      if(longName!==null){name=longName;longName=null;}
      if(name.startsWith('./'))name=name.slice(2);
      if(type==='5'&&name.endsWith('/'))name=name.slice(0,-1);
      if(name==='')name='.';
      safeRelative(name,true);
      assert(!observed.has(name),'Duplicate TAR member: '+name);
      const reference=expected.get(name),entryType=type==='5'?'directory':'file';
      assert(reference&&reference.type===entryType,'Unexpected/mistyped TAR member: '+name);
      assert(mode===reference.mode,'TAR mode mismatch: '+name);
      const destination=name==='.'?extraction:path.join(extraction,name);
      assert(destination===extraction||destination.startsWith(extraction+path.sep),'Extraction escaped destination');
      observed.set(name,{type:entryType,bytes:entryType==='file'?bytes:undefined,mode});
      if(type==='5'){
        assert(bytes===0,'Directory has nonempty payload');
        if(name!=='.')fs.mkdirSync(destination,{recursive:true,mode});
      }else{
        assert(bytes===reference.bytes,'TAR size mismatch: '+name);
        fs.mkdirSync(path.dirname(destination),{recursive:true,mode:0o755});
        current={metadata:false,name,bytes,remaining:bytes,hash:crypto.createHash('sha256'),fd:fs.openSync(destination,'wx',mode)};
      }
    }
  }
  assert(ended&&zeroHeaders===2&&current===null&&padding===0&&queue.length===0&&longName===null,'Truncated TAR or missing terminator');
  assert(uncompressedBytes%512===0,'Unaligned TAR size');
  sameInventory(observed,expected,'Decoded TAR inventory');
  sameInventory(walk(extraction),expected,'Extracted inventory');
  console.log(JSON.stringify({phase:'gzip-tar-extract',result:'PASS',members:observed.size,physicalHeaders,longNameHeaders,uncompressedBytes}));
  const sumsText=fs.readFileSync(path.join(extraction,'SHA256SUMS'),'utf8');
  assert(sumsText.endsWith('\n'),'Checksum manifest missing newline');
  const sums=new Map();
  for(const line of sumsText.trimEnd().split('\n')){
    const match=/^([a-f0-9]{64})  (.+)$/.exec(line);assert(match,'Malformed checksum line');
    const name=safeRelative(match[2]);assert(!sums.has(name),'Duplicate checksum path');sums.set(name,match[1]);
  }
  const regular=[...expected].filter(([,e])=>e.type==='file');
  assert(sums.size===regular.length-1&&!sums.has('SHA256SUMS'),'Checksum manifest inventory mismatch');
  let checks=0;
  for(const [name,entry]of regular){
    assert(hashFile(path.join(extraction,name))===entry.sha256,'Extracted filesystem hash mismatch: '+name);
    assert(hashFile(path.join(stage,name))===entry.sha256,'Stage filesystem hash mismatch: '+name);
    if(name!=='SHA256SUMS')assert(sums.get(name)===entry.sha256,'Checksum manifest mismatch: '+name);
    if(entry.originalPath){
      const st=fs.lstatSync(entry.originalPath);
      assert(st.isFile()&&st.size===entry.bytes&&(st.mode&0o777)===entry.mode,'Original file metadata mismatch: '+name);
      assert(hashFile(entry.originalPath)===entry.sha256,'Original file hash mismatch: '+name);
    }
    checks++;
    if(checks%3000===0)console.log(JSON.stringify({phase:'all-file-hashes',verified:checks,total:regular.length}));
  }
  for(const entry of manifest.entries.filter(e=>e.type==='directory')){
    const st=fs.lstatSync(entry.originalPath);assert(st.isDirectory()&&(st.mode&0o777)===entry.mode,'Original directory mismatch: '+entry.archivePath);
  }
  const sourceRoots=[['target',manifest.target.originalPath,[]],...manifest.completedRuns.map((run,i)=>[['runs/full-retry','runs/retention','runs/measurement'][i],run.originalPath,[]]),['runs/first-attempt',manifest.firstAttempt.originalPath,['preserved-target']]];
  for(const [prefix,directory,exclusions]of sourceRoots){
    const currentInventory=walk(directory,prefix,exclusions);
    const declaredInventory=new Map([...declared].filter(([p])=>p===prefix||p.startsWith(prefix+'/')));
    sameInventory(currentInventory,declaredInventory,'Current original input inventory '+prefix);
  }
  assert(hashFile(path.join(extraction,'MANIFEST.json'))===expectedManifestSha&&hashFile(path.join(extraction,'SHA256SUMS'))===expectedSumsSha,'Extracted metadata hashes');
  const completedRuns=[];
  for(const [index,label]of ['full-retry','retention','measurement'].entries()){
    const d=path.join(extraction,'runs',label),entry=manifest.completedRuns[index];
    const before=text(path.join(d,'identity-before.txt')),after=text(path.join(d,'identity-after.txt'));
    assert(before===expectedHead+'\n'+expectedTree&&after===before,'Run identities mismatch: '+label);
    assert(text(path.join(d,'tracked-before.txt'))===''&&text(path.join(d,'tracked-after.txt'))==='','Run tracked state dirty');
    assert(text(path.join(d,'source-current.txt'))==='true'&&text(path.join(d,'checkout.txt'))===source&&text(path.join(d,'expected-head.txt'))===expectedHead,'Run source receipt mismatch');
    assert(text(path.join(d,'exit-code.txt'))==='0'&&text(path.join(d,'maven-exit-code.txt'))==='0','Run exit receipt mismatch');
    const startedUTC=text(path.join(d,'started-utc.txt')),finishedUTC=text(path.join(d,'finished-utc.txt'));
    assert(startedUTC===entry.started&&finishedUTC===entry.finished&&Number.isFinite(Date.parse(startedUTC))&&Date.parse(finishedUTC)>=Date.parse(startedUTC),'Run timestamp mismatch');
    assert(entry.head===expectedHead&&entry.tree===expectedTree&&entry.status==='COMPLETED_RECEIPTS_VERIFIED','Manifest run identity mismatch');
    assert(/BUILD SUCCESS/.test(fs.readFileSync(path.join(d,'run.log'),'utf8')),'Missing successful Maven log marker');
    completedRuns.push({label,startedUTC,finishedUTC,wrapperExit:0,mavenExit:0,identitiesMatch:true,trackedStatesClean:true,sourceCurrent:true});
  }
  const first=manifest.firstAttempt,firstDir=path.join(extraction,'runs/first-attempt'),observation=json(path.join(firstDir,'recovery-observation.json'));
  assert(first.status==='INCOMPLETE_COMPLETION_CHAIN'&&first.notAQualifiedProcessExit===true,'First attempt completion mislabel');
  assert(JSON.stringify(first.observation)===JSON.stringify(observation)&&observation.processExitCode===null&&observation.missingAutomaticPostRunReceipt===true,'First attempt receipt mislabel');
  assert(observation.head===expectedHead&&observation.tree===expectedTree&&observation.logSha256===hashFile(path.join(firstDir,'run.log')),'First attempt source/log mismatch');
  assert(text(path.join(firstDir,'identity-before.txt'))===expectedHead+'\n'+expectedTree,'First attempt start identity mismatch');
  assert(!fs.existsSync(path.join(firstDir,'exit-code.txt'))&&!fs.existsSync(path.join(firstDir,'maven-exit-code.txt'))&&!fs.existsSync(path.join(firstDir,'preserved-target')),'Unexpected first-attempt completion/excluded payload');
  const mapping=json(path.join(extraction,'reviews/010-publication-ready-mapping.json'));
  const mappingGzip=fs.readFileSync(path.join(extraction,'reviews/011-publication-ready-all-blobs.tsv.gz'));
  const mappingPlain=zlib.gunzipSync(mappingGzip);
  assert(digest(mappingGzip)===mapping.completeMapping.sha256&&digest(mappingPlain)===mapping.completeMapping.uncompressedSha256&&mappingPlain.length===mapping.completeMapping.uncompressedBytes,'Complete mapping digest mismatch');
  assert(mapping.qualifiedSource.head===expectedHead&&mapping.qualifiedSource.tree===expectedTree,'Mapping qualified source mismatch');
  function treeEntries(revision){
    const rows=git('ls-tree','-r','-z',revision).split('\0').filter(Boolean),result=new Map();
    for(const row of rows){const match=/^(\d+) (\S+) ([a-f0-9]{40})\t(.+)$/.exec(row);assert(match,'Invalid git tree listing');result.set(match[4],{mode:match[1],type:match[2],blob:match[3]});}
    return result;
  }
  assert(git('rev-parse',mapping.candidate.head+'^{tree}').trim()===mapping.candidate.tree,'Mapping candidate tree mismatch');
  assert(git('rev-parse',mapping.nativeBase.localHead+'^{tree}').trim()===mapping.nativeBase.tree,'Mapping native base tree mismatch');
  const qualified=treeEntries(expectedHead),candidate=treeEntries(mapping.candidate.head);
  const rows=mappingPlain.toString('utf8').trimEnd().split('\n');
  assert(rows.shift()==='path\tmode\ttype\tqualified_blob\tcandidate_blob\tequal','Mapping column schema mismatch');
  const mapped=new Set(),differences=[];
  for(const row of rows){const [name,mode,type,qualifiedBlob,candidateBlob,equal]=row.split('\t');
    assert(!mapped.has(name),'Duplicate complete mapping path');mapped.add(name);
    const q=qualified.get(name),c=candidate.get(name);assert(q&&c&&q.mode===mode&&c.mode===mode&&q.type===type&&c.type===type&&q.blob===qualifiedBlob&&c.blob===candidateBlob,'Mapping Git blob mismatch: '+name);
    assert(equal===String(qualifiedBlob===candidateBlob),'Mapping equality mismatch');if(equal==='false')differences.push(name);
  }
  assert(mapped.size===qualified.size&&mapped.size===candidate.size&&mapped.size===3556&&differences.length===5,'Mapping totals mismatch');
  assert(JSON.stringify(differences.sort())===JSON.stringify(mapping.differences.map(e=>e.path).sort()),'Mapping differences mismatch');
  assert(differences.every(p=>/^sandbox_math_cleanup_help\/images\/0[1-5]-.+\.provenance\.json$/.test(p)),'Unexpected source difference');
  for(const entry of mapping.payloadFiles){
    assert(qualified.get(entry.path).blob===entry.qualifiedBlob&&candidate.get(entry.path).blob===entry.payloadBlob,'QA payload mapping mismatch');
    const bytes=execFileSync('git',['-C',source,'cat-file','blob',entry.payloadBlob],{stdio:['ignore','pipe','pipe'],timeout:30000,maxBuffer:2*1024*1024,env:{...process.env,GIT_OPTIONAL_LOCKS:'0'}});
    assert(bytes.length===entry.bytes&&digest(bytes)===entry.sha256,'QA payload byte mapping mismatch');
  }
  const native=treeEntries(mapping.nativeBase.localHead),scope=[...new Set([...native.keys(),...candidate.keys()])].filter(p=>JSON.stringify(native.get(p))!==JSON.stringify(candidate.get(p)));
  assert(scope.length===11&&JSON.stringify(scope.sort())===JSON.stringify(mapping.payloadFiles.map(e=>e.path).sort()),'BLS candidate scope changed');
  const expectedMeasuredFiles={
    'target/bc-bls-jmh.json':'394aac651f89a40ef6d09469bb1e3a75b096da3cecfa456f90a85fe97184e7e4',
    'target/bc-bls-performance.json':'52b888d8d7788d67be15b530de8942355ef68e763955c8c79ff6d6ed5aa8c618',
    'target/bc-hash-to-curve.json':'ee587b4068fbf5d47fa0207e4522e404e834c7e3f9d65ed5ed3d63ce49fb4b8c',
    'target/bc-performance-review.json':'9854f88a817318039bcd2a1e777905523355635eac60a2d963f5fa11f994b9d0',
    'reviews/003-current-performance-review-9c2d1216.md':'672deb86df650aac948c6396d1a46e0c5c29f8edbfc6b81bf9312707ba47f432',
    'reviews/004-current-performance-review-9c2d1216.json':'ed34a43835b027c65a22d4803a7698b53412feb5c2182de3d3c619ed395765e7',
    'reviews/005-current-performance-review-9c2d1216.cjs':'a242e77d1529a6c785855be7b0471b20ae1fb3cce064cab63b3e9b3ba10f1918'
  };
  for(const[name,sha]of Object.entries(expectedMeasuredFiles))assert(observed.get(name)?.sha256===sha,'Previously reviewed current evidence changed: '+name);
  const externalHelp=[];
  for(const help of manifest.externalHelp){assert(help.embedded===false&&help.status==='VERIFIED_EXTERNAL_NOT_EMBEDDED','External Help scope mismatch');assert(fs.statSync(help.originalPath).size===help.bytes&&hashFile(help.originalPath)===help.sha256,'External Help identity mismatch');externalHelp.push({runId:help.runId,artifactId:help.artifactId,bytes:help.bytes,sha256:help.sha256,embedded:false,identityVerified:true});}
  const sourceAfter=identity(),archiveAfter=snapshot(archive);
  assert(JSON.stringify(archiveBefore)===JSON.stringify(archiveAfter)&&hashFile(archive)===expectedArchiveSha,'Final archive changed during verification');
  assert(hashFile(path.join(stage,'MANIFEST.json'))===expectedManifestSha&&hashFile(path.join(stage,'SHA256SUMS'))===expectedSumsSha,'Stage metadata changed during verification');
  sameInventory(walk(stage),stageInventoryBefore,'Stage inventory stability');
  for(const extra of stageExtras)assert(JSON.stringify(snapshot(path.join(stage,extra.path)))===JSON.stringify(extra.snapshot)&&hashFile(path.join(stage,extra.path))===extra.sha256,'Additional stage file changed during verification');
  const canonicalInventory=[...observed].sort(([a],[b])=>a<b?-1:a>b?1:0).map(([p,e])=>[e.type,p,e.mode,e.bytes??'',e.sha256??''].join('\t')).join('\n')+'\n';
  const result={schema:'sandbox.independent-archive-verification/v1',result:'PASS',startedUTC:started,finishedUTC:new Date().toISOString(),archive:{path:archive,bytes:archiveAfter.bytes,sha256:expectedArchiveSha,gitBlob:crypto.createHash('sha1').update(Buffer.from('blob '+archiveAfter.bytes+'\0')).update(fs.readFileSync(archive)).digest('hex'),snapshotBefore:archiveBefore,snapshotAfter:archiveAfter,unchangedDuringVerification:true},metadata:{manifestSha256:expectedManifestSha,sha256sumsSha256:expectedSumsSha,outerReceiptSha256:hashFile(archive+'.receipt.json'),fileListSha256:digest(list),fileListBytes:list.length},gzip:{externalGzipTestExit:0,externalGzipTestDurationMs:gzipMs,nodeZlibStreamIntegrity:'PASS'},tar:{physicalHeaders,gnuLongNameHeaders:longNameHeaders,uncompressedBytes,trailingZeroBytes,allHeaderChecksumsValid:true,terminatorZeroBlocks:zeroHeaders,onlyRegularFilesAndDirectories:true,uniqueCanonicalPaths:true,rootConfined:true,entryInventoryExact:true,uid:0,gid:0,mtime:0},inventory:{inputFiles:inputFiles.length,manifestDirectories:manifest.entries.length-inputFiles.length,regularFiles:regular.length,directories:[...expected.values()].filter(e=>e.type==='directory').length,members:expected.size,inputBytes:inputFiles.reduce((sum,e)=>sum+e.bytes,0),regularFileBytes:regularBytes,checksumLines:sums.size,allPayloadHashesMatchManifest:true,allChecksumEntriesVerified:true,allExtractedFilesRehashed:true,allOriginalStageFilesRehashed:true,allOriginalInputFilesRehashed:true,originalInputInventoriesExact:true,canonicalInventorySha256:digest(Buffer.from(canonicalInventory))},extractionDirectory:extraction,source:{checkout:source,before:sourceBefore,after:sourceAfter},completedRuns,firstAttempt:{status:first.status,processExitCode:null,missingAutomaticPostRunReceipt:true,notAQualifiedProcessExit:true,omittedPreservedTarget:true,observationAndLogHashMatch:true},sourceMapping:{qualifiedHead:expectedHead,qualifiedTree:expectedTree,candidateHead:mapping.candidate.head,candidateTree:mapping.candidate.tree,nativeLocalHead:mapping.nativeBase.localHead,nativeTree:mapping.nativeBase.tree,trackedEntries:3556,equalBlobs:3551,differentBlobs:5,differentPaths:differences,elevenQaPayloadBlobsVerified:true,scopeAgainstNative:11,completeMappingMatchesActualGitTrees:true},currentMeasurementEvidenceHashes:expectedMeasuredFiles,externalHelp,scope:'Archive integrity, complete inventories, byte preservation and source/completion receipts only. No JVM, Maven, benchmark, archived code execution, remote write, or new semantic/performance review.',residualPartial:{path:archive+'.partial',included:false,treatment:'Separate unqualified residual; untouched and excluded from completion claims. Bounded /proc check found no matching active archiver before verification.'},verifier:{path:__filename,sha256:hashFile(__filename)}};
  result.copyStage={manifestedInventoryComplete:true,unmanifestedEntries:stageExtras,unmanifestedEntriesInArchive:false,inventoryUnchangedDuringVerification:true,causeNotEstablished:true};
  fs.writeFileSync(path.join(output,'verification.json'),JSON.stringify(result,null,2)+'\n',{flag:'wx'});
  const md=`# Unabhängige Archivprüfung: aktueller Nachweis 9c2d1216\n\n**Ergebnis: PASS.** Das exakt benannte fertige Archiv wurde vollständig gelesen, sicher in einen neuen Ordner extrahiert und gegen beide internen Verzeichnisse sowie die ursprünglichen Eingaben geprüft.\n\n## Identität\n\n| Gegenstand | Verifizierter Wert |\n|---|---|\n| Archiv | \`complete-current-evidence-9c2d1216.tar.gz\` |\n| Bytes | ${archiveAfter.bytes} |\n| SHA256 | \`${expectedArchiveSha}\` |\n| Git-Blob | \`${result.archive.gitBlob}\` |\n| MANIFEST.json SHA256 | \`${expectedManifestSha}\` |\n| SHA256SUMS SHA256 | \`${expectedSumsSha}\` |\n| Qualifizierter HEAD | \`${expectedHead}\` |\n| Qualifizierter Tree | \`${expectedTree}\` |\n\n## Vollständige Prüfung\n\n- GNU gzip-Integritätstest: tatsächlicher Exit 0; zusätzlich vollständig erfolgreiches Node-zlib-Dekomprimieren mit CRC/ISIZE-Prüfung.\n- Alle ${physicalHeaders} TAR-Header-Prüfsummen stimmen, einschließlich ${longNameHeaders} GNU-Langnamen-Header. Alle tatsächlichen Mitglieder sind reguläre Dateien oder Verzeichnisse; keine Links, Spezialdateien, doppelten Pfade, absoluten Pfade oder Traversierung. Zwei Abschlussblöcke und sämtliche Padding-/Endbytes sind null.\n- Genau ${expected.size} Mitglieder: ${regular.length} reguläre Dateien und ${result.inventory.directories} Verzeichnisse. Das entspricht sämtlichen Manifest-Einträgen, den zwei erzeugten Metadatendateien und den impliziten Elternverzeichnissen einschließlich Archivwurzel. Die separate NUL-Liste stimmt exakt damit überein.\n- **${inputFiles.length}/${inputFiles.length} Eingabedateien** (${result.inventory.inputBytes} Bytes) stimmen mit ihrem Manifest-SHA256 überein. Alle ${sums.size} SHA256SUMS-Einträge sind vorhanden und korrekt. SHA256SUMS selbst stimmt mit dem außen festgelegten Hash überein.\n- Nach der Extraktion wurden alle ${regular.length} Dateien erneut vom Dateisystem gehasht. Die ursprünglichen Stage-Dateien und sämtliche ${inputFiles.length} ursprünglichen Eingabedateien wurden ebenfalls vollständig gehasht. Inventare, Dateigrößen, Typen und Modi stimmen; der vollständige aktuelle target-Baum und die aufgenommenen Run-Verzeichnisse enthalten keine unerklärten Auslassungen.\n- Finale Archiv-Identität (Inode, Größe, Änderungszeiten und SHA256) sowie die Quellen-Identität wurden vor und nach der Prüfung bestätigt. Kein bestehendes Archiv, Stage oder Checkout wurde verändert.\n\n## Quellen- und Abschlussbelege\n\n| Aufgenommener Lauf | UTC-Start | UTC-Ende | Wrapper / Maven | Quellenbindung |\n|---|---|---|---|---|\n${completedRuns.map(r=>`| ${r.label} | ${r.startedUTC} | ${r.finishedUTC} | 0 / 0 | HEAD/Tree exakt; vorher/nachher sauber; source-current=true |`).join('\n')}\n\nDer ursprüngliche Full-Versuch bleibt ausdrücklich **INCOMPLETE_COMPLETION_CHAIN**: Prozess-Exit unbekannt (null), automatischer Abschlussbeleg fehlt; sein Log-Hash und seine Startidentität stimmen. Es wurde kein Exit 0 ergänzt. Der dortige preserved-target-Baum ist explizit ausgelassen; der vollständige qualifizierte aktuelle target-Baum des Wiederholungslaufs ist enthalten.\n\nDie archivierte vollständige Blob-Zuordnung wurde gegen die tatsächlich vorhandenen Git-Trees erneut geprüft: **3556 Einträge, 3551 gleiche Blobs, exakt fünf unterschiedliche Help-Provenance-JSONs**. Kandidat \`${mapping.candidate.head}\` / Tree \`${mapping.candidate.tree}\` enthält gegenüber Native \`${mapping.nativeBase.localHead}\` exakt die elf angegebenen QA-Dateien. Ihre Bytes/Hashes und sämtliche Zuordnungszeilen stimmen. Die vier aktuellen JMH-Ausgaben und die drei bereits abgeschlossenen unabhängigen Messprüfungsdateien haben unverändert ihre zuvor geprüften Hashes; hier erfolgte keine neue statistische oder fachliche Bewertung.\n\nBeide Help-ZIPs sind als **externe, nicht eingebettete** Dateien ausgewiesen. Ihre Größen und SHA256 wurden gelesen und bestätigt (Runs 41497649570 und 41500326710); sie sind kein Bestandteil dieses TAR-Payloads.\n\n## Grenzen und Ausgabe\n\nDie separate gleichnamige \`.partial\`-Datei gehört nicht zum fertigen Archiv und wurde weder verändert noch als erfolgreicher Abschluss gewertet. Eine begrenzte Prozess-Metadatenprüfung vor der Archivprüfung fand keinen passenden laufenden Archivierer; ihre Entstehungsursache wird hier nicht behauptet.\n\nKeine JVM, kein Maven, keine neue Messung, kein ausgeführter archivierter Code und keine Remote-Schreiboperation. Prüfzeit: ${started} bis ${result.finishedUTC}. Vollständige maschinenlesbare Zusammenfassung: \`verification.json\`; nachvollziehbarer Prüfcode: \`verify.cjs\`; neue Extraktion: \`extracted/\`.\n`;
  const extraParagraph=stageExtras.length?'## Separater Stage-Befund\n\nIm ursprünglichen Copy-Stage liegt zusätzlich außerhalb des Manifest-Inventars '+stageExtras.map(e=>'`'+e.path+'` ('+e.bytes+' Bytes, SHA256 `'+e.sha256+'`)').join(', ')+'. Dieser Eintrag ist nicht im finalen TAR enthalten. Der gleichnamige Eintrag fehlt im entsprechenden ursprünglichen Quellverzeichnis; der Hash stimmt nicht mit der manifestierten vollständigen retention.json überein. Sämtliche ursprünglichen Eingaben und die manifestierte retention.json stimmen dagegen vollständig mit dem Archiv überein. Der zusätzliche Stage-Eintrag blieb während dieser Prüfung unverändert. Seine Entstehungsursache ist aus den vorliegenden Daten nicht belegt und wird nicht behauptet. Dieser Befund wurde Root unmittelbar gemeldet.\n\n':'';
  const finalMd=md.replace('Inventare, Dateigrößen, Typen und Modi stimmen;','Die manifestierten Dateien und ursprünglichen Eingabeverzeichnisse stimmen in Inventar, Größen, Typen und Modi;').replace('## Quellen- und Abschlussbelege',extraParagraph+'## Quellen- und Abschlussbelege');
  fs.writeFileSync(path.join(output,'review.md'),finalMd,{flag:'wx'});
  const receipts=['verify.cjs','verification.json','review.md'].map(name=>({path:path.join(output,name),bytes:fs.statSync(path.join(output,name)).size,sha256:hashFile(path.join(output,name))}));
  fs.writeFileSync(path.join(output,'SHA256SUMS'),receipts.map(e=>e.sha256+'  '+path.basename(e.path)).join('\n')+'\n',{flag:'wx'});
  console.log(JSON.stringify({result:'PASS',archive:result.archive,inventory:result.inventory,sourceMapping:result.sourceMapping,receipts}));
}
main().catch(error=>{console.error(error.stack);process.exitCode=1;});
