#!/usr/bin/env python3
"""
Generate a comprehensive test overview report for the sandbox project.

This script scans all test modules in the repository and generates:
- Total test count per plugin
- Disabled test count per plugin
- List of all test methods with their status
- Summary statistics for the entire project
"""

import os
import re
import json
from pathlib import Path
from dataclasses import dataclass, asdict
from typing import List, Dict
from collections import defaultdict


@dataclass
class TestMethod:
    """Represents a single test method"""
    plugin: str
    file_path: str
    class_name: str
    method_name: str
    is_disabled: bool
    disabled_reason: str
    test_type: str  # @Test, @ParameterizedTest, etc.
    line_number: int


@dataclass
class PluginSummary:
    """Summary statistics for a plugin"""
    plugin_name: str
    total_tests: int
    disabled_tests: int
    enabled_tests: int
    test_files: int


class TestScanner:
    """Scans Java test files and extracts test information"""
    
    def __init__(self, repo_root: Path):
        self.repo_root = repo_root
        self.tests: List[TestMethod] = []
        
    def find_test_modules(self) -> List[Path]:
        """Find all test module directories"""
        test_modules = []
        for item in self.repo_root.iterdir():
            if item.is_dir() and item.name.endswith('_test'):
                test_modules.append(item)
        return sorted(test_modules)
    
    def scan_java_file(self, file_path: Path, plugin_name: str):
        """Scan a single Java file for test methods"""
        try:
            with open(file_path, 'r', encoding='utf-8') as f:
                content = f.read()
                lines = content.split('\n')
        except Exception as e:
            print(f"Error reading {file_path}: {e}")
            return
        
        # Extract package and class name
        package_match = re.search(r'package\s+([\w.]+);', content)
        class_match = re.search(r'(?:public\s+)?class\s+(\w+)', content)
        
        if not class_match:
            return
            
        class_name = class_match.group(1)
        full_class_name = f"{package_match.group(1)}.{class_name}" if package_match else class_name
        
        # Find all test methods
        i = 0
        while i < len(lines):
            line = lines[i]
            
            # Check for test annotations
            test_annotation = None
            is_disabled = False
            disabled_reason = ""
            
            # Look back a few lines for annotations
            for j in range(max(0, i-10), i):
                prev_line = lines[j].strip()
                
                if '@Test' in prev_line:
                    test_annotation = 'Test'
                elif '@ParameterizedTest' in prev_line:
                    test_annotation = 'ParameterizedTest'
                elif '@RepeatedTest' in prev_line:
                    test_annotation = 'RepeatedTest'
                    
                # Check for @Disabled
                if '@Disabled' in prev_line:
                    is_disabled = True
                    # Extract reason if present
                    reason_match = re.search(r'@Disabled\s*\(\s*["\']([^"\']+)["\']\s*\)', prev_line)
                    if reason_match:
                        disabled_reason = reason_match.group(1)
                    else:
                        disabled_reason = "No reason specified"
                        
                # Check for commented @Disabled
                if '//' in prev_line and '@Disabled' in prev_line:
                    # This is a commented-out disabled annotation
                    # The test is actually enabled
                    pass
            
            # Check if current line is a method declaration
            method_match = re.match(r'\s*(?:public|private|protected)?\s+(?:static\s+)?(?:void|\w+)\s+(\w+)\s*\(', line)
            if method_match and test_annotation:
                method_name = method_match.group(1)
                
                # Relative path from repo root
                rel_path = file_path.relative_to(self.repo_root)
                
                test = TestMethod(
                    plugin=plugin_name,
                    file_path=str(rel_path),
                    class_name=full_class_name,
                    method_name=method_name,
                    is_disabled=is_disabled,
                    disabled_reason=disabled_reason,
                    test_type=test_annotation,
                    line_number=i + 1
                )
                self.tests.append(test)
            
            i += 1
    
    def scan_module(self, module_path: Path):
        """Scan all Java files in a test module"""
        plugin_name = module_path.name
        src_dir = module_path / 'src'
        
        if not src_dir.exists():
            return
        
        # Find all Java files and let annotation scanning decide which contain tests
        for java_file in src_dir.rglob('*.java'):
            self.scan_java_file(java_file, plugin_name)
    
    def scan_all(self):
        """Scan all test modules in the repository"""
        test_modules = self.find_test_modules()
        print(f"Found {len(test_modules)} test modules")
        
        for module in test_modules:
            print(f"Scanning {module.name}...")
            self.scan_module(module)
        
        print(f"\nTotal tests found: {len(self.tests)}")
    
    def generate_summary(self) -> Dict[str, PluginSummary]:
        """Generate summary statistics per plugin"""
        summaries = {}
        
        # Group tests by plugin
        plugin_tests = defaultdict(list)
        for test in self.tests:
            plugin_tests[test.plugin].append(test)
        
        # Calculate statistics for each plugin
        for plugin_name, tests in plugin_tests.items():
            total = len(tests)
            disabled = sum(1 for t in tests if t.is_disabled)
            enabled = total - disabled
            
            # Count unique test files
            test_files = len(set(t.file_path for t in tests))
            
            summaries[plugin_name] = PluginSummary(
                plugin_name=plugin_name,
                total_tests=total,
                disabled_tests=disabled,
                enabled_tests=enabled,
                test_files=test_files
            )
        
        return summaries
    
    def generate_markdown_report(self) -> str:
        """Generate a markdown-formatted report"""
        summaries = self.generate_summary()
        
        lines = []
        lines.append("# JUnit Test Overview Report\n")
        lines.append(f"Generated on: {os.environ.get('GITHUB_RUN_ID', 'local')}\n")
        
        # Overall statistics
        total_tests = sum(s.total_tests for s in summaries.values())
        total_disabled = sum(s.disabled_tests for s in summaries.values())
        total_enabled = sum(s.enabled_tests for s in summaries.values())
        total_files = sum(s.test_files for s in summaries.values())
        
        lines.append("## Overall Statistics\n")
        lines.append(f"- **Total Test Modules:** {len(summaries)}")
        lines.append(f"- **Total Test Files:** {total_files}")
        lines.append(f"- **Total Tests:** {total_tests}")
        lines.append(f"- **Enabled Tests:** {total_enabled} ({100*total_enabled//total_tests if total_tests > 0 else 0}%)")
        lines.append(f"- **Disabled Tests:** {total_disabled} ({100*total_disabled//total_tests if total_tests > 0 else 0}%)\n")
        
        # Per-plugin summary table
        lines.append("## Test Summary by Plugin\n")
        lines.append("| Plugin | Test Files | Total Tests | Enabled | Disabled | Disabled % |")
        lines.append("|--------|------------|-------------|---------|----------|-----------|")
        
        for plugin_name in sorted(summaries.keys()):
            summary = summaries[plugin_name]
            disabled_pct = (100 * summary.disabled_tests // summary.total_tests) if summary.total_tests > 0 else 0
            lines.append(
                f"| {summary.plugin_name} | {summary.test_files} | {summary.total_tests} | "
                f"{summary.enabled_tests} | {summary.disabled_tests} | {disabled_pct}% |"
            )
        
        lines.append("\n## Disabled Tests Details\n")
        
        # Group disabled tests by plugin
        disabled_by_plugin = defaultdict(list)
        for test in self.tests:
            if test.is_disabled:
                disabled_by_plugin[test.plugin].append(test)
        
        if disabled_by_plugin:
            for plugin_name in sorted(disabled_by_plugin.keys()):
                disabled_tests = disabled_by_plugin[plugin_name]
                lines.append(f"\n### {plugin_name} ({len(disabled_tests)} disabled)\n")
                
                for test in sorted(disabled_tests, key=lambda t: (t.file_path, t.line_number)):
                    lines.append(f"- `{test.class_name}.{test.method_name}()` - {test.disabled_reason}")
                    lines.append(f"  - File: `{test.file_path}:{test.line_number}`")
        else:
            lines.append("*No disabled tests found!* 🎉\n")
        
        return "\n".join(lines)
    
    def generate_json_report(self) -> str:
        """Generate a JSON-formatted report"""
        summaries = self.generate_summary()
        
        report = {
            "summary": {
                "total_modules": len(summaries),
                "total_tests": sum(s.total_tests for s in summaries.values()),
                "enabled_tests": sum(s.enabled_tests for s in summaries.values()),
                "disabled_tests": sum(s.disabled_tests for s in summaries.values()),
            },
            "plugins": {
                plugin_name: asdict(summary)
                for plugin_name, summary in summaries.items()
            },
            "disabled_tests": [
                asdict(test) for test in self.tests if test.is_disabled
            ]
        }
        
        return json.dumps(report, indent=2)


def main():
    """Main entry point"""
    repo_root = Path(__file__).parent.parent.parent
    
    scanner = TestScanner(repo_root)
    scanner.scan_all()
    
    # Generate markdown report
    markdown_report = scanner.generate_markdown_report()
    
    # Write to file
    output_dir = Path(os.environ.get('GITHUB_WORKSPACE', repo_root))
    markdown_path = output_dir / 'test-report.md'
    
    with open(markdown_path, 'w', encoding='utf-8') as f:
        f.write(markdown_report)
    
    print(f"\nMarkdown report written to: {markdown_path}")
    
    # Also generate JSON for potential GitHub Actions integration
    json_report = scanner.generate_json_report()
    json_path = output_dir / 'test-report.json'
    
    with open(json_path, 'w', encoding='utf-8') as f:
        f.write(json_report)
    
    print(f"JSON report written to: {json_path}")
    
    # Print summary to console
    print("\n" + "="*60)
    print(markdown_report)


if __name__ == '__main__':
    main()
