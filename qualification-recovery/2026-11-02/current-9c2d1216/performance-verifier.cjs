#!/usr/bin/env node
'use strict';
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),assert=require('node:assert/strict');
const {execFileSync}=require('node:child_process');
const recovery=__dirname,repo='/tmp/sandbox-bls-on-native',target=path.join(repo,'qa/math-optimization/target');
const evidence=path.join(recovery,'current-measurement-9c2d1216'),head='9c2d121686dd2bfcd774d820312ca603d8fc4b4f',tree='d9736c8cd1e397508784e7d79fd1cb08ac978312';
const sdkRevision='70c13fb580062b1fd653f35b81b2fb7ed1903c28',sdkSha='ba0293dedb3fde9ca2f3042dcef8ae4d6bb2d576adedff8ba7a2b4d186f1cbe1';
const expectedOutputs={
 'bc-bls-jmh.json':'394aac651f89a40ef6d09469bb1e3a75b096da3cecfa456f90a85fe97184e7e4',
 'bc-bls-performance.json':'52b888d8d7788d67be15b530de8942355ef68e763955c8c79ff6d6ed5aa8c618',
 'bc-hash-to-curve.json':'ee587b4068fbf5d47fa0207e4522e404e834c7e3f9d65ed5ed3d63ce49fb4b8c',
 'bc-performance-review.json':'9854f88a817318039bcd2a1e777905523355635eac60a2d963f5fa11f994b9d0'};
const hashCache=new Map(),checked=new Map();
const sha=b=>crypto.createHash('sha256').update(b).digest('hex');
const read=p=>fs.readFileSync(p);
const text=p=>fs.readFileSync(p,'utf8');
const json=p=>JSON.parse(text(p));
function hash(p){if(!hashCache.has(p))hashCache.set(p,sha(read(p)));return hashCache.get(p);}
function checkHash(p,h){assert.match(h,/^[0-9a-f]{64}$/);assert.equal(hash(p),h,p);checked.set(p,h);}
function git(...args){return execFileSync('git',['-C',repo,...args],{encoding:'utf8',stdio:['ignore','pipe','pipe'],timeout:30000,env:{...process.env,GIT_OPTIONAL_LOCKS:'0'}}).trim();}
function identity(){assert.equal(git('rev-parse','HEAD'),head);assert.equal(git('rev-parse','HEAD^{tree}'),tree);assert.equal(git('status','--porcelain=v1','--untracked-files=no'),'');return {head,tree,trackedStatus:''};}
const before=identity();
function receipt(dir){
 for(const n of ['exit-code.txt','maven-exit-code.txt'])assert.equal(text(path.join(dir,n)).trim(),'0',n);
 assert.equal(text(path.join(dir,'source-current.txt')).trim(),'true');
 for(const n of ['identity-before.txt','identity-after.txt'])assert.equal(text(path.join(dir,n)).trim(),head+'\n'+tree);
 for(const n of ['tracked-before.txt','tracked-after.txt'])assert.equal(text(path.join(dir,n)).trim(),'');
 const started=text(path.join(dir,'started-utc.txt')).trim(),finished=text(path.join(dir,'finished-utc.txt')).trim();
 assert(Date.parse(started)<=Date.parse(finished));
 return {directory:dir,started,finished,exitCode:0,mavenExitCode:0,sourceCurrent:true};
}
const completion=receipt(evidence),fullCompletion=receipt(path.join(recovery,'full-integration-9c2d1216-retry1'));
assert.equal(completion.started,'2026-11-02T18:31:13Z');assert.equal(completion.finished,'2026-11-02T18:36:20Z');
const marker=fs.statSync(path.join(evidence,'start-marker')).mtimeMs;
const files={};
for(const [name,expected] of Object.entries(expectedOutputs)){
 const p=path.join(target,name),s=fs.statSync(p);checkHash(p,expected);
 assert(s.mtimeMs>=marker && s.mtimeMs<Date.parse(completion.finished)+1000,name+' freshness');
 files[name]={path:p,bytes:s.size,sha256:expected,mtimeUTC:s.mtime.toISOString(),newerThanMeasurementStart:true};
}
const rawBls=json(path.join(target,'bc-bls-jmh.json')),rawSwu=json(path.join(target,'bc-hash-to-curve.json'));
const reportBls=json(path.join(target,'bc-bls-performance.json')),reportSwu=json(path.join(target,'bc-performance-review.json'));
assert.equal(rawBls.length,6);assert.equal(rawSwu.length,16);
assert.equal(reportBls.rawJmhSha256,files['bc-bls-jmh.json'].sha256);assert.equal(reportSwu.rawJmhSha256,files['bc-hash-to-curve.json'].sha256);
// Independent closed-form Student-t CDF for 9 degrees of freedom:
// t = 3*tan(theta); integrate cos(theta)^8 exactly, then bisect CDF=0.9995.
function tCdf9(t){const a=Math.atan(t/3);return 0.5+(35*a+28*Math.sin(2*a)+7*Math.sin(4*a)+(4/3)*Math.sin(6*a)+(1/8)*Math.sin(8*a))/(35*Math.PI);}
let lower=0,upper=20;
for(let i=0;i<100;i++){const mid=(lower+upper)/2;if(tCdf9(mid)<0.9995)lower=mid;else upper=mid;}
const critical=(lower+upper)/2;
assert(critical>4.7809&&critical<4.7810);
let maxMeanDifference=0,maxErrorDifference=0,maxIntervalDifference=0,maxPercentileDifference=0;
function close(actual,expected,label){
 assert(Number.isFinite(actual)&&Number.isFinite(expected),label);
 assert(Math.abs(actual-expected)<=Math.max(1e-9,Math.abs(expected)*2e-10),label+': '+actual+' != '+expected);
}
function percentile(sorted,p){const pos=(sorted.length+1)*p/100;if(pos<=1)return sorted[0];if(pos>=sorted.length)return sorted.at(-1);const k=Math.floor(pos);return sorted[k-1]+(pos-k)*(sorted[k]-sorted[k-1]);}
function reconstruct(metric,label,unit){
 assert.equal(metric.scoreUnit,unit,label);assert.equal(metric.rawData.length,2,label);
 metric.rawData.forEach(f=>assert.equal(f.length,5,label));
 const samples=metric.rawData.flat();assert(samples.every(x=>Number.isFinite(x)&&x>0),label);
 const mean=samples.reduce((a,b)=>a+b,0)/10;
 const variance=samples.reduce((a,x)=>a+(x-mean)**2,0)/9;
 const error=critical*Math.sqrt(variance/10),ci=[mean-error,mean+error];
 close(metric.score,mean,label+' mean');close(metric.scoreError,error,label+' error');
 ci.forEach((v,i)=>close(metric.scoreConfidence[i],v,label+' CI'));
 const sorted=[...samples].sort((a,b)=>a-b);
 for(const [p,v] of Object.entries(metric.scorePercentiles)){const expected=percentile(sorted,+p);close(v,expected,label+' percentile '+p);maxPercentileDifference=Math.max(maxPercentileDifference,Math.abs(v-expected));}
 if(unit==='us/op'){
 maxMeanDifference=Math.max(maxMeanDifference,Math.abs(metric.score-mean));
 maxErrorDifference=Math.max(maxErrorDifference,Math.abs(metric.scoreError-error));
 maxIntervalDifference=Math.max(maxIntervalDifference,...ci.map((v,i)=>Math.abs(v-metric.scoreConfidence[i])));
 }
 return {samples:10,forks:2,samplesPerFork:5,meanMicros:mean,standardDeviationMicros:Math.sqrt(variance),standardErrorMicros:Math.sqrt(variance/10),error99_9Micros:error,confidence99_9Micros:ci};
}
const settings={mode:'avgt',threads:1,forks:2,warmupIterations:3,warmupTime:'500 ms',warmupBatchSize:1,measurementIterations:5,measurementTime:'500 ms',measurementBatchSize:1,jmhVersion:'1.37'};
const runtime={jvm:'/tmp/jdt-pr5614-tools/jdk-25.0.5+8/bin/java',jdkVersion:'25.0.5',vmName:'OpenJDK 64-Bit Server VM',vmVersion:'25.0.5+8-LTS'};
const statisticalRows=[],comparisons={bls:[],swu:[]},runtimes={};
for(const [group,rows,report,prefix] of [
 ['bls',rawBls,reportBls,'org.sandbox.math.qa.BlsBenchmark.'],
 ['swu',rawSwu,reportSwu,'org.sandbox.math.qa.BouncyCastleHashToCurveBenchmark.']]){
 const pairs=new Map();runtimes[group]={...runtime,jvmArgs:rows[0].jvmArgs};
 for(const row of rows){
  for(const [key,value] of Object.entries(settings))assert.equal(row[key],value,group+' '+key);
  for(const [key,value] of Object.entries(runtime))assert.equal(row[key],value,group+' '+key);
  assert.deepEqual(row.jvmArgs,rows[0].jvmArgs);assert(row.jvmArgs.includes('-Xmx512m'));
  if(group==='bls')assert(row.jvmArgs.includes('-Xms128m'));
  assert(row.benchmark.startsWith(prefix));const variant=row.benchmark.slice(prefix.length);assert(['original','generated'].includes(variant));
  const name=group==='bls'?row.params.workload:row.params.curveProfile+'/'+row.params.messageBytes;
  if(!pairs.has(name))pairs.set(name,{});assert(!pairs.get(name)[variant]);pairs.get(name)[variant]=row;
  const stats=reconstruct(row.primaryMetric,group+'/'+name+'/'+variant,'us/op');
  reconstruct(row.secondaryMetrics['gc.alloc.rate.norm'],group+'/'+name+'/'+variant+'/allocations','B/op');
  statisticalRows.push({group,case:name,variant,...stats});
 }
 assert.equal(pairs.size,group==='bls'?3:8);assert.equal(report.comparisons.length,pairs.size);
 for(const [name,pair] of pairs){
  assert(pair.original&&pair.generated);assert.deepEqual(pair.original.params,pair.generated.params);
  const o=statisticalRows.find(x=>x.group===group&&x.case===name&&x.variant==='original');
  const g=statisticalRows.find(x=>x.group===group&&x.case===name&&x.variant==='generated');
  const timing=g.confidence99_9Micros[1]<o.confidence99_9Micros[0]?'LOWER_TIME':g.confidence99_9Micros[0]>o.confidence99_9Micros[1]?'HIGHER_TIME':'INCONCLUSIVE';
  const matches=report.comparisons.filter(x=>x.case===name);assert.equal(matches.length,1);const c=matches[0];
  assert.equal(c.timing,timing);const ratio=g.meanMicros/o.meanMicros;close(c.generatedOverOriginal??c.timeRatio,ratio,name+' ratio');
  for(const [variant,metricName,allocName] of [['original','originalMicros','originalAllocations'],['generated','generatedMicros','generatedAllocations']]){
   assert.deepEqual(c[metricName].rawData,pair[variant].primaryMetric.rawData);
   reconstruct(c[metricName],name+'/'+metricName,'us/op');
   assert.deepEqual(c[allocName],pair[variant].secondaryMetrics['gc.alloc.rate.norm']);
  }
  comparisons[group].push({case:name,originalMicros:pair.original.primaryMetric.score,originalCI99_9:pair.original.primaryMetric.scoreConfidence,generatedMicros:pair.generated.primaryMetric.score,generatedCI99_9:pair.generated.primaryMetric.scoreConfidence,generatedOverOriginal:ratio,reportedRatio:c.generatedOverOriginal??c.timeRatio,timing,intervalOverlapMicros:Math.min(o.confidence99_9Micros[1],g.confidence99_9Micros[1])-Math.max(o.confidence99_9Micros[0],g.confidence99_9Micros[0])});
 }
}
assert.equal(statisticalRows.length,22);assert.equal(statisticalRows.reduce((s,r)=>s+r.samples,0),220);
function properties(p){const result={};for(const line of text(p).split(/\r?\n/)){if(!line||line.startsWith('#')||line.startsWith('!'))continue;const i=line.indexOf('=');assert(i>0,p);result[line.slice(0,i)]=line.slice(i+1);}return result;}
const blsDir=path.join(target,'bc-bls'),swuDir=path.join(target,'bc-mathematical');
const blsManifest=properties(path.join(blsDir,'benchmark.properties')),swuManifest=properties(path.join(swuDir,'benchmark.properties'));
const cohort=json(path.join(blsDir,'cohort.json')),scanBls=json(path.join(blsDir,'review.json')),inputBls=json(path.join(blsDir,'inputs.json'));
const scanSwu=json(path.join(swuDir,'review.json')),inputSwu=json(path.join(swuDir,'inputs.json'));
const binary=rawBls[0].params.binaryArchive,sources=rawBls[0].params.sourceArchive;
const sourceSha='b37ac84b1d5435ab7b8d166c16ab9f75e09f68f8ec50479bae433939b241b03f',binarySha='986b0fb92ec10e0c66b43e036ce0077e6150cfaecd1db9fb92b56672e157afe5';
checkHash(binary,binarySha);checkHash(sources,sourceSha);
const manifestSha=hash(path.join(blsDir,'benchmark.properties'));assert.equal(reportBls.fixtureManifestSha256,manifestSha);
for(const row of rawBls){assert.equal(row.params.fixtureDirectory,blsDir);assert.equal(row.params.fixtureManifestSha256,manifestSha);assert.equal(row.params.binaryArchive,binary);assert.equal(row.params.sourceArchive,sources);}
for(const row of rawSwu){assert.equal(row.params.scanDirectory,swuDir);assert.equal(row.params.binaryArchive,binary);}
for(const [dir,manifest,scan,inputs,profileName] of [[blsDir,blsManifest,scanBls,inputBls,'bc-bls.properties'],[swuDir,swuManifest,scanSwu,inputSwu,'bc-mathematical.properties']]){
 assert.equal(manifest.schema,'sandbox.generated-corpus-fixture/v1');assert.equal(manifest.targetJava,'17');assert.equal(text(path.join(dir,'COMPLETED')).split('\n')[0],scan.status);
 assert.equal(manifest.binarySha256,binarySha);assert.equal(manifest.sourcesSha256,sourceSha);
 checkHash(path.join(dir,'review.json'),manifest.reviewSha256);
 const profilePath=path.join(repo,'qa/math-optimization/profiles',profileName);
 if(dir===blsDir)checkHash(path.join(dir,'profile.properties'),manifest.profileSha256);
 checkHash(profilePath,manifest.profileSha256);
 for(const key of ['binarySha256','sourcesSha256','profileSha256'])assert.equal(scan[key],manifest[key]);
 assert.equal(scan.targetJava,17);
 if(dir===blsDir){assert.equal(scan.status,'COMPLETED');assert.equal(scan.parseErrorFiles,0);}
 else assert(['COMPLETED','COMPLETED_WITH_PARSE_ERRORS'].includes(scan.status));
 for(const key of ['schema','binarySha256','sourcesSha256','profileSha256','targetJava','options','selectedEntries','javaRuntime','runtimeInputs'])assert.deepEqual(inputs[key],scan[key],key);
 assert.equal(scan.javaRuntime,runtime.vmVersion);assert.equal(scan.options['cleanup.mathematics.tailRecursion'],'false');
 assert.equal(Object.keys(scan.options).length,14);
 const profile=properties(profilePath);
 for(const [key,value] of Object.entries(profile)){
  if(key.endsWith('.numericKinds'))assert.deepEqual(value.split(',').sort(),scan.options[key].split(',').sort());else assert.equal(value,scan.options[key],key);
 }
}
assert.equal(scanBls.acceptedRegions,9);assert.equal(scanBls.parsedFiles,5);assert.equal(scanBls.files.length,5);
assert.equal(scanBls.options['cleanup.mathematics.valueRuntime'],'AUDITED_OPENJDK_25_0_5_8');
assert.equal(scanSwu.options['cleanup.mathematics.valueRuntime'],'UNSPECIFIED');
assert(Date.parse(scanBls.startedAt)>=marker&&Date.parse(scanBls.finishedAt)<=Date.parse(completion.finished));
assert(Date.parse(scanSwu.startedAt)>=Date.parse(fullCompletion.started)&&Date.parse(scanSwu.finishedAt)<=Date.parse(fullCompletion.finished)+1000);
assert.equal(reportBls.reviewSha256,blsManifest.reviewSha256);assert.deepEqual(reportBls.classes,cohort.classes);assert.deepEqual(reportBls.options,scanBls.options);
assert.equal(reportBls.javaRuntime,runtime.vmVersion);assert.deepEqual(reportBls.measurementRuntime,runtime);assert.deepEqual(reportBls.jvmArgs,rawBls[0].jvmArgs);
for(const name of ['inputs','cohort'])checkHash(path.join(blsDir,name+'.json'),blsManifest[name+'Sha256']);
assert.equal(Object.keys(cohort.files).length,17);assert.equal(Object.keys(cohort.sourceDependencies).length,7);
for(const [name,h] of Object.entries(cohort.files))checkHash(path.join(blsDir,name),h);
function archiveEntry(name){return execFileSync('unzip',['-p',sources,name],{stdio:['ignore','pipe','pipe'],timeout:30000,maxBuffer:10*1024*1024});}
for(const [name,h] of Object.entries(cohort.sourceDependencies)){
 checkHash(path.join(blsDir,'source-context',name),h);assert.equal(sha(archiveEntry(name)),h,name);
}
const classBindings=[];
for(const [name,c] of Object.entries(cohort.classes)){
 const matches=scanBls.files.filter(f=>f.path===name);assert.equal(matches.length,1);const row=matches[0];
 assert.equal(row.status,'ANALYZED');assert.deepEqual(row.parseErrors,[]);assert.equal(row.acceptedRegions,c.acceptedRegions);assert.equal(row.sourceSha256,c.originalSha256);
 assert.equal(c.generatedSha256,row.acceptedRegions?row.afterSha256:row.sourceSha256);
 for(const variant of ['original','generated']){assert.equal(blsManifest[variant+'.'+name],c[variant+'Sha256']);checkHash(path.join(blsDir,variant,name),c[variant+'Sha256']);}
 assert.deepEqual(read(path.join(blsDir,'original',name)),archiveEntry(name));
 if(c.acceptedRegions>0)assert(cohort.files['evidence/'+name+'.json']);
 for(const dep of row.sourceDependencies){const relative=dep.identity.split('/source-context/')[1];assert.equal(cohort.sourceDependencies[relative],dep.sha256);}
 classBindings.push({path:name,...c,originalArchiveBytesEqual:true});
}
assert.equal(classBindings.length,5);
const historicalCohort=path.join(recovery,'current-evidence-7fcd719-ee20d376/jmh/fixtures/bc-bls/cohort.json');
assert.deepEqual(json(historicalCohort).classes,cohort.classes);
const swuEntry='org/bouncycastle/crypto/hash2curve/impl/SimplifiedShallueVanDeWoestijneMapToCurve.java';
const swuFile=scanSwu.files.filter(f=>f.path===swuEntry);assert.equal(swuFile.length,1);const sr=swuFile[0];assert.equal(sr.acceptedRegions,3);assert.equal(sr.status,'ANALYZED');assert.deepEqual(sr.parseErrors,[]);
for(const variant of ['original','generated']){const h=variant==='original'?sr.sourceSha256:sr.afterSha256;assert.equal(swuManifest[variant+'.'+swuEntry],h);checkHash(path.join(swuDir,variant,swuEntry),h);}
assert.deepEqual(read(path.join(swuDir,'original',swuEntry)),archiveEntry(swuEntry));
assert.deepEqual(inputBls.runtimeInputs,inputSwu.runtimeInputs);
let runtimeFiles=0,loadedBindings={};
for(const [p,h] of Object.entries(inputBls.runtimeInputs)){
 if(p.startsWith('loaded:')){assert(path.isAbsolute(h));assert(fs.existsSync(h));loadedBindings[p]=h;}
 else {assert(path.isAbsolute(p));checkHash(p,h);runtimeFiles++;}
}
assert.equal(runtimeFiles,475);assert.equal(Object.keys(loadedBindings).length,5);
const sdk=path.join(repo,'sandbox_math_cleanup/lib/regelsuche-optimization-sdk.jar'),qualification=path.join(repo,'sandbox_math_cleanup/lib/qualification.json');
checkHash(sdk,sdkSha);const sq=json(qualification);assert.equal(sq.sourceRevision,sdkRevision);assert.equal(sq.standaloneJarSha256,sdkSha);
assert.equal(loadedBindings['loaded:de.regelsuche.sdk.optimization.ComputationOptimizer'],sdk);
assert.equal(loadedBindings['loaded:org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis'],path.join(target,'classes'));
const jdkRelease=path.resolve(runtime.jvm,'../../release'),release=text(jdkRelease);assert(release.includes('JAVA_VERSION="25.0.5"'));assert(release.includes('JAVA_RUNTIME_VERSION="25.0.5+8-LTS"'));
const logPath=path.join(evidence,'run.log'),log=text(logPath),commandPath=path.join(evidence,'command.sh'),command=text(commandPath);
assert(log.includes('BLS_COHORT classes=5 acceptedRegions=9 parseErrorFiles=0'));
const prep=log.indexOf('exec (prepare-bls-source-cohort)'),measure=log.indexOf('exec (measure-complete-bls-calls)');assert(prep>=0&&measure>prep);
assert(log.includes('[INFO] BUILD SUCCESS'));assert(log.includes('[INFO] Total time:  05:06 min'));
const finished=log.match(/\[INFO\] Finished at: (.+)/)[1].trim();assert.equal(Date.parse(finished),Date.parse(completion.finished));
assert.equal((log.match(/# Fork: 1 of 2/g)||[]).length,22);assert.equal((log.match(/# Fork: 2 of 2/g)||[]).length,22);
assert.equal((log.match(/^Iteration\s+\d+:\s+[\d.]+ us\/op/gm)||[]).length,220);
assert(command.includes('exec:exec@prepare-bls-source-cohort')&&command.includes('exec:exec@full-call-calibration'));
const after=identity();
const criticalFiles=['bc-bls/benchmark.properties','bc-bls/cohort.json','bc-bls/inputs.json','bc-bls/review.json','bc-bls/profile.properties','bc-mathematical/benchmark.properties','bc-mathematical/inputs.json','bc-mathematical/review.json'];
for(const name of criticalFiles){const p=path.join(target,name),s=fs.statSync(p);files[name]={path:p,bytes:s.size,sha256:hash(p),mtimeUTC:s.mtime.toISOString()};}
for(const [name,p] of Object.entries({'measurement/run.log':logPath,'measurement/command.sh':commandPath,'sdk/qualification.json':qualification,'jdk/release':jdkRelease,'jdk/bin/java':runtime.jvm,'profiles/bc-bls.properties':path.join(repo,'qa/math-optimization/profiles/bc-bls.properties'),'profiles/bc-mathematical.properties':path.join(repo,'qa/math-optimization/profiles/bc-mathematical.properties')})){const s=fs.statSync(p);files[name]={path:p,bytes:s.size,sha256:hash(p)};}
const summary={
 schema:'sandbox.independent-performance-review/v1',status:'VERIFIED',source:{before,after,checkout:repo},completion,fullCompilationCompletion:fullCompletion,
 files,measurement:{rows:22,primarySamples:220,blsRows:6,swuRows:16,completedForks:44,commonSettings:settings,runtimes,
 independentStatistics:{method:'Closed-form Student-t CDF for df=9, independent bisection at probability 0.9995; unbiased sample variance over ten iteration observations per row.',confidence:0.999,degreesOfFreedom:9,criticalValue:critical,maxMeanDifferenceMicros:maxMeanDifference,maxErrorDifferenceMicros:maxErrorDifference,maxIntervalDifferenceMicros:maxIntervalDifference,maxPercentileDifference,allocationMetricReconstructionAlsoPassed:true}},
 comparisons,statisticalRows,bindings:{fixtureManifestSha256:manifestSha,cohortFilesVerified:17,sourceDependenciesVerified:7,classBindings,allTenBlsSourceVariantsEqualHistorical7fcd:true,historicalCohort:{path:historicalCohort,sha256:hash(historicalCohort)},blsScan:{status:scanBls.status,parsedFiles:scanBls.parsedFiles,parseErrorFiles:scanBls.parseErrorFiles},swuCorpusScan:{status:scanSwu.status,parsedFiles:scanSwu.parsedFiles,parseErrorFiles:scanSwu.parseErrorFiles},blsProfile:scanBls.options,swuProfile:scanSwu.options,swuSource:{path:swuEntry,originalSha256:sr.sourceSha256,generatedSha256:sr.afterSha256,acceptedRegions:3,status:sr.status,parseErrors:sr.parseErrors},binary:{path:binary,sha256:binarySha},sources:{path:sources,sha256:sourceSha},runtimeFilesVerified:runtimeFiles,loadedBindings,sharedBlsSwuRuntimeInputs:true,sdk:{path:sdk,sha256:sdkSha,sourceRevision:sdkRevision,version:sq.version},currentFileHashesVerified:checked.size},
 limits:['Results are local directional observations for these complete calls and this JVM; no optimizer costs or budgets are changed.','G2_32 confidence intervals overlap; its larger mean does not establish a directional timing conclusion.','GC profiler observations include JVM/harness activity and trial-end validation/cleanup; they do not isolate candidate allocations.','Output equality checks exercise fixed samples; neither timing nor those checks prove constant-time behavior.','BLS raw rows directly carry the fixture manifest digest; SWU rows carry the scan directory, whose manifest and source hashes are checked by benchmark setup/teardown.','The broader SWU corpus receipt records '+scanSwu.parseErrorFiles+' parse-error files; the measured SWU source itself is ANALYZED with no parse errors and three accepted regions.','This reviewer ran no JVM, Python, Maven, benchmark or new production test.']};
const output=path.join(recovery,'current-performance-review-9c2d1216.json'),serialized=JSON.stringify(summary,null,2)+'\n';
if(fs.existsSync(output))assert.equal(text(output),serialized,'Refusing to replace a different review summary');
else fs.writeFileSync(output,serialized,{flag:'wx'});
console.log(JSON.stringify({result:'VERIFIED',output,rows:22,primarySamples:220,criticalValue:critical,maxMeanDifferenceMicros:maxMeanDifference,maxErrorDifferenceMicros:maxErrorDifference,maxIntervalDifferenceMicros:maxIntervalDifference,runtimeFilesVerified:runtimeFiles,sourceFilesIdenticalTo7fcd:true,bls:comparisons.bls,swu:comparisons.swu.map(x=>({case:x.case,ratio:x.generatedOverOriginal,timing:x.timing}))},null,2));

