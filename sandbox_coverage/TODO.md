# Coverage Aggregation - TODO

## Status Summary

**Current State**: Stable build infrastructure for coverage aggregation

### Completed
- ✅ JaCoCo Maven plugin integration
- ✅ Multi-module coverage aggregation
- ✅ HTML report generation
- ✅ Maven profile activation (jacoco)
- ✅ All test modules included in aggregation

### In Progress
- 🔄 Documentation (ARCHITECTURE.md complete, this TODO in progress)

### Pending
- [ ] Coverage quality gates
- [ ] Coverage trend tracking
- [ ] Coverage diff reports
- [ ] CI integration improvements
- [ ] Coverage badges for README

## Priority Tasks

### 1. Coverage Quality Gates
**Priority**: High  
**Effort**: 2-3 hours

Add Maven configuration to enforce minimum coverage thresholds:

```xml
<execution>
    <id>check-coverage</id>
    <goals>
        <goal>check</goal>
    </goals>
    <configuration>
        <rules>
            <rule>
                <element>BUNDLE</element>
                <limits>
                    <limit>
                        <counter>LINE</counter>
                        <value>COVEREDRATIO</value>
                        <minimum>0.80</minimum>
                    </limit>
                    <limit>
                        <counter>BRANCH</counter>
                        <value>COVEREDRATIO</value>
                        <minimum>0.75</minimum>
                    </limit>
                </limits>
            </rule>
        </rules>
    </configuration>
</execution>
```

**Benefits**:
- Prevent coverage regressions
- Ensure new code is tested
- Fail build if coverage drops below threshold
- Enforce quality standards

**Configuration Options**:
- Set threshold per module
- Different thresholds for line/branch coverage
- Exclude specific classes (e.g., generated code)

### 2. Coverage Trend Tracking
**Priority**: Medium  
**Effort**: 4-6 hours

Track coverage metrics over time:

**Implementation**:
1. Extract coverage percentage after each build
2. Store in CSV file or database
3. Generate trend chart (HTML/PNG)
4. Display in CI dashboard

**Script Example**:
```bash
#!/bin/bash
# extract-coverage.sh

REPORT="sandbox_coverage/target/site/jacoco-aggregate/index.html"
COVERAGE=$(grep -oP 'Total.*?\K[0-9]+(?=%)' $REPORT)
DATE=$(date +%Y-%m-%d)
COMMIT=$(git rev-parse --short HEAD)

echo "$DATE,$COMMIT,$COVERAGE%" >> coverage-history.csv

# Generate trend chart
gnuplot coverage-trend.gnuplot
```

**Benefits**:
- Visualize coverage improvement over time
- Identify coverage regressions early
- Track impact of refactoring efforts
- Report progress to stakeholders

### 3. Coverage Diff Reports
**Priority**: Medium  
**Effort**: 6-8 hours

Generate reports showing coverage changes between commits/branches:

**Features**:
- Compare coverage before/after PR
- Highlight newly covered lines (green)
- Highlight newly uncovered lines (red)
- Show net coverage change (+2.3% / -1.5%)

**Implementation**:
```bash
# Generate diff report
mvn verify -Pjacoco
cp sandbox_coverage/target/site/jacoco-aggregate coverage-current

git checkout base-branch
mvn verify -Pjacoco  
cp sandbox_coverage/target/site/jacoco-aggregate coverage-base

# Use JaCoCo diff tool or custom script
java -jar jacoco-diff.jar coverage-base coverage-current > coverage-diff.html
```

**Benefits**:
- Review coverage impact of changes
- Require coverage increase for PRs
- Identify regressions before merge
- Improve code review process

### 4. Coverage Badges
**Priority**: Low  
**Effort**: 2-3 hours

Add coverage badges to README.md:

**Example Badge**:
```markdown
![Coverage](https://img.shields.io/badge/coverage-85%25-brightgreen)
```

**Implementation**:
1. Extract coverage percentage from report
2. Generate badge via shields.io or similar service
3. Update README.md automatically
4. Store badge in repository or use dynamic URL

**Dynamic Badge Service**:
- Upload coverage data to service (Codecov, Coveralls)
- Service generates real-time badge
- Badge updates automatically after each build

**Benefits**:
- Visibility of coverage at a glance
- Show commitment to quality
- Encourage contributions with tests
- Track quality trend publicly

### 5. CI Integration Improvements
**Priority**: High  
**Effort**: 3-4 hours

Enhance GitHub Actions integration:

**Current State**: Coverage runs, but results not analyzed

**Improvements**:
1. **Comment on PRs**: Post coverage summary as PR comment
   ```yaml
   - name: Coverage Report
     uses: codecov/codecov-action@v3
     with:
       files: sandbox_coverage/target/site/jacoco-aggregate/jacoco.xml
   ```

2. **Coverage Checks**: Fail PR if coverage decreases
   ```yaml
   - name: Check Coverage
     run: |
       COVERAGE=$(extract_coverage.sh)
       if [ $COVERAGE -lt 80 ]; then
         echo "Coverage below 80%: $COVERAGE%"
         exit 1
       fi
   ```

3. **Upload Artifacts**: Store coverage reports
   ```yaml
   - name: Upload Coverage Report
     uses: actions/upload-artifact@v3
     with:
       name: coverage-report
       path: sandbox_coverage/target/site/jacoco-aggregate/
   ```

**Benefits**:
- Automated coverage checks in CI
- Visibility of coverage in PRs
- Prevent merging PRs that decrease coverage
- Historical coverage data available

### 6. Module-Specific Coverage Reports
**Priority**: Low  
**Effort**: 3-4 hours

Generate separate coverage reports for each major module category:

**Categories**:
- Cleanup implementations (encoding, platform helper, etc.)
- UI components
- Test infrastructure
- Build modules

**Report Structure**:
```
sandbox_coverage/target/site/
├── jacoco-aggregate/           # All modules
├── jacoco-cleanups/            # Only cleanup implementations
├── jacoco-ui/                  # Only UI components
└── jacoco-test-infra/          # Only test infrastructure
```

**Benefits**:
- Focused coverage metrics per category
- Different coverage targets per category
- Better visibility into module health
- Easier to identify improvement areas

## Known Issues

### 1. Coverage Aggregation Incomplete for New Modules
**Severity**: Medium

When a new module is added, it must be manually added to sandbox_coverage/pom.xml.

**Workaround**: Update pom.xml when adding new modules

**Potential Fix**: Use Maven dependency plugin to auto-discover modules

### 2. No XML Report Generation
**Severity**: Low

Currently only HTML reports are generated. XML format needed for CI tools.

**Workaround**: Manually add XML goal to plugin configuration

**Fix**:
```xml
<execution>
    <id>report</id>
    <goals>
        <goal>report-aggregate</goal>
    </goals>
    <configuration>
        <formats>
            <format>HTML</format>
            <format>XML</format>
        </formats>
    </configuration>
</execution>
```

### 3. Coverage Report Size
**Severity**: Low

HTML reports can be large (10-20 MB) for big projects.

**Impact**: Slow to load, large CI artifacts

**Potential Solution**: Generate compressed reports, exclude source code view

## Future Enhancements

### Integration with Codecov/Coveralls
**Priority**: Medium  
**Effort**: 2-3 hours

Upload coverage to hosted service:

```yaml
# GitHub Actions
- name: Upload to Codecov
  uses: codecov/codecov-action@v3
  with:
    files: sandbox_coverage/target/site/jacoco-aggregate/jacoco.xml
    fail_ci_if_error: true
```

**Benefits**:
- Professional coverage dashboards
- PR annotations with coverage changes
- Public coverage badges
- Historical trend charts

### Differential Coverage
**Priority**: Medium  
**Effort**: 4-6 hours

Only check coverage for changed lines:

```bash
# Get changed lines
git diff origin/main --unified=0 | parse_diff.sh > changed-lines.txt

# Check coverage only for changed lines
jacoco-check --lines changed-lines.txt --threshold 90%
```

**Benefits**:
- New code held to higher standards
- Don't penalize for legacy code
- Encourage incremental improvement
- More realistic coverage goals

### Coverage Heatmap
**Priority**: Low  
**Effort**: 6-8 hours

Generate visual heatmap of coverage:
- Color-code files by coverage percentage
- Show package hierarchy
- Interactive drill-down
- Identify low-coverage areas quickly

### Multi-Profile Support
**Priority**: Low  
**Effort**: 2-3 hours

Support different coverage profiles:

```bash
# Full coverage (default)
mvn verify -Pjacoco

# Fast coverage (skip slow tests)
mvn verify -Pjacoco-fast

# Incremental coverage (only changed files)
mvn verify -Pjacoco-incremental
```

## Maintenance Tasks

### Regular Maintenance
- [ ] Update JaCoCo plugin version quarterly
- [ ] Review coverage trends monthly
- [ ] Clean up old coverage artifacts
- [ ] Verify all modules included in aggregation

### After Adding New Module
- [ ] Add module dependency to sandbox_coverage/pom.xml
- [ ] Add test module dependency to sandbox_coverage/pom.xml
- [ ] Verify module appears in coverage report
- [ ] Set coverage targets for new module

### After Removing Module
- [ ] Remove module dependencies from sandbox_coverage/pom.xml
- [ ] Update documentation
- [ ] Verify aggregate report still generates

## Testing Strategy

### How to Test Coverage Module

Since this is a build infrastructure module, testing is done by:

1. **Verification**: Run build and check report generation
   ```bash
   mvn clean verify -Pjacoco
   test -f sandbox_coverage/target/site/jacoco-aggregate/index.html
   ```

2. **Smoke Tests**: Verify report contains expected modules
   ```bash
   grep "sandbox_encoding_quickfix" sandbox_coverage/target/site/jacoco-aggregate/index.html
   ```

3. **Coverage Check**: Ensure coverage is being calculated
   ```bash
   grep -oP 'Total.*?\K[0-9]+(?=%)' sandbox_coverage/target/site/jacoco-aggregate/index.html
   ```

### CI Testing

Coverage module is tested in CI on every build:
- Build with coverage enabled
- Verify report generation
- Check for expected modules
- Fail if report is empty or missing

## Documentation Improvements

### Completed Documentation
- ✅ ARCHITECTURE.md: Design and implementation details
- ✅ TODO.md: This file

### Additional Documentation Needed
- [ ] Coverage best practices guide
- [ ] How to interpret coverage reports
- [ ] Setting coverage targets
- [ ] Troubleshooting coverage issues
- [ ] Coverage in the development workflow

## Performance Considerations

### Build Time Impact

**Without Coverage**: ~3-5 minutes  
**With Coverage**: ~3.5-6 minutes  
**Overhead**: ~10-20%

### Optimization Opportunities

1. **Parallel Test Execution**: Reduce test time
2. **Selective Coverage**: Only measure changed modules
3. **Cached Reports**: Reuse reports for unchanged modules
4. **Faster Instrumentation**: Use JaCoCo agent options

## References

- [JaCoCo Documentation](https://www.jacoco.org/jacoco/trunk/doc/)
- [JaCoCo Maven Plugin Goals](https://www.jacoco.org/jacoco/trunk/doc/maven.html)
- [Maven Multi-Module Projects](https://maven.apache.org/guides/mini/guide-multiple-modules.html)
- [Code Coverage Best Practices](https://martinfowler.com/bliki/TestCoverage.html)

## Contact

For questions about coverage configuration or reporting:
- Open an issue in the repository
- Submit a pull request with improvements
- Contact: See project contributors

## Dependencies

This module depends on:
- All implementation modules (compile scope)
- All test modules (test scope)
- JaCoCo Maven Plugin (0.8.11+)

Changes to module structure require updating this module's pom.xml.
