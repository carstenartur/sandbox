# Reconstructed mathematics adapter core

This is fresh Java/JUnit evidence for reconstructed source. It does not recover the qualification or byte identity of the deleted checkout.

`junit-green.log` records 62 tests with no failures or skips, using Java 25 and a 512 MiB heap. The synthetic generated programs are separately compiled at Java 8 for integral/BigInteger cases and Java 17 for floating point. Production never executes user code.

`receipt.json` states the scope and failed attempts. `sources.sha256` binds the tested sources; `classpath-inputs.json` inventories the dependency inputs. The SDK checker hash is identical before/after the run. These dependency class directories are not a final embedded-JAR or native-product receipt.

`RunTests.java` is only a thin JUnit Platform launcher; all assertions live in repository Java test classes. The normal PDE/Maven test discovery remains required. Compile invocation: `javac -J-Xmx512m -Xlint:unchecked -cp <listed dependency classpath> -d <isolated classes> <production sources>`, followed by the same invocation for tests with production classes on the classpath. Execute `java -Xmx512m -cp <test classes>:<production classes>:<listed dependency classpath> RunTests` and the six fully qualified test classes recorded in the receipt.
