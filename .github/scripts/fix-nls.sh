#!/bin/bash
# Adds missing //$NON-NLS-n$ comments to string literals in Java files
# ONLY for plugin modules (not for test modules)

set -e

echo "Fixing NLS comments in plugin sources..."

# Find all plugin source directories (exclude test modules and special modules)
# Exclude: *_test/, sandbox_test_commons/, sandbox_web/, sandbox_target/, sandbox_coverage/
PLUGIN_DIRS=$(find . -type d -name "src" | grep -E "^\\./sandbox_[^/]+/src$" | grep -v "_test/" | grep -v "sandbox_test_commons/" | grep -v "sandbox_web/" | grep -v "sandbox_target/" | grep -v "sandbox_coverage/" | sort)

if [ -z "$PLUGIN_DIRS" ]; then
    echo "No plugin source directories found"
    exit 0
fi

echo "Found plugin directories to process:"
echo "$PLUGIN_DIRS"
echo ""

for dir in $PLUGIN_DIRS; do
    echo "Processing: $dir"
    
    # Find all Java files in this directory
    find "$dir" -name "*.java" -type f | while read -r file; do
        # Create temporary file for processing
        temp_file=$(mktemp)
        
        # Process each line
        awk '
        {
            line = $0
            
            # Skip lines that already have NLS comments
            if (line ~ /\/\/\$NON-NLS-[0-9]+\$/) {
                print line
                next
            }
            
            # Skip comment lines (single-line comments, multi-line comment start/content)
            if (line ~ /^[[:space:]]*(\/\/|\*|\/\*)/) {
                print line
                next
            }
            
            # Count string literals in the line (simple heuristic)
            # Remove escaped quotes first
            temp = line
            gsub(/\\"/, "", temp)
            
            # Count remaining quotes (each pair is one string)
            n = gsub(/"/, "\"", temp)
            string_count = int(n / 2)
            
            # If string literals exist and line ends with statement terminator
            if (string_count > 0 && line ~ /[;)}][[:space:]]*$/) {
                # Build NLS comments
                nls_comments = ""
                for (i = 1; i <= string_count; i++) {
                    nls_comments = nls_comments " //$NON-NLS-" i "$"
                }
                # Remove trailing whitespace and add comments
                gsub(/[[:space:]]+$/, "", line)
                line = line nls_comments
            }
            
            print line
        }
        ' "$file" > "$temp_file"
        
        # Replace original only if changes were made
        if ! diff -q "$file" "$temp_file" > /dev/null 2>&1; then
            mv "$temp_file" "$file"
            echo "  Fixed: $file"
        else
            rm "$temp_file"
        fi
    done
done

echo ""
echo "NLS comment fix complete."
