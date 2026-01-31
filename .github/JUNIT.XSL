<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0"
        xmlns:lxslt="http://xml.apache.org/xslt"
        xmlns:string="xalan://java.lang.String">
<xsl:output method="html" indent="yes" encoding="UTF-8"
  doctype-system="about:legacy-compat" />
<xsl:decimal-format decimal-separator="." grouping-separator="," />
<!--
   Licensed to the Apache Software Foundation (ASF) under one or more
   contributor license agreements.  See the NOTICE file distributed with
   this work for additional information regarding copyright ownership.
   The ASF licenses this file to You under the Apache License, Version 2.0
   (the "License"); you may not use this file except in compliance with
   the License.  You may obtain a copy of the License at

       https://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
   
   Customized for Sandbox Project based on Eclipse Platform's JUNIT.XSL
 -->

<xsl:param name="TITLE">Unit Test Results</xsl:param>
<xsl:param name="GITHUB_REPO"/>
<xsl:param name="GITHUB_BRANCH">main</xsl:param>
<xsl:param name="CONFIG_FILE">junit-report-config.xml</xsl:param>

<!-- Load configuration file if available -->
<xsl:variable name="config" select="document($CONFIG_FILE, /)/junit-report-config"/>

<!-- Configuration values with fallbacks -->
<xsl:variable name="project-name">
  <xsl:choose>
    <xsl:when test="$config/project/name"><xsl:value-of select="$config/project/name"/></xsl:when>
    <xsl:otherwise>Sandbox</xsl:otherwise>
  </xsl:choose>
</xsl:variable>

<xsl:variable name="repository-url">
  <xsl:choose>
    <xsl:when test="$config/project/repository-url"><xsl:value-of select="$config/project/repository-url"/></xsl:when>
    <xsl:otherwise>https://github.com/carstenartur/sandbox</xsl:otherwise>
  </xsl:choose>
</xsl:variable>

<xsl:variable name="github-pages-base">
  <xsl:choose>
    <xsl:when test="$config/project/github-pages-base"><xsl:value-of select="$config/project/github-pages-base"/></xsl:when>
    <xsl:otherwise>https://carstenartur.github.io/sandbox</xsl:otherwise>
  </xsl:choose>
</xsl:variable>

<xsl:variable name="tests-base">
  <xsl:choose>
    <xsl:when test="$config/paths/tests-base"><xsl:value-of select="$config/paths/tests-base"/></xsl:when>
    <xsl:otherwise>/tests</xsl:otherwise>
  </xsl:choose>
</xsl:variable>

<xsl:variable name="coverage-base">
  <xsl:choose>
    <xsl:when test="$config/paths/coverage-base"><xsl:value-of select="$config/paths/coverage-base"/></xsl:when>
    <xsl:otherwise>/coverage</xsl:otherwise>
  </xsl:choose>
</xsl:variable>

<xsl:variable name="test-class-suffix">
  <xsl:choose>
    <xsl:when test="$config/mapping/test-class-suffix"><xsl:value-of select="$config/mapping/test-class-suffix"/></xsl:when>
    <xsl:otherwise>Test</xsl:otherwise>
  </xsl:choose>
</xsl:variable>

<xsl:variable name="show-coverage-links">
  <xsl:choose>
    <xsl:when test="$config/ui/show-coverage-links"><xsl:value-of select="$config/ui/show-coverage-links"/></xsl:when>
    <xsl:otherwise>true</xsl:otherwise>
  </xsl:choose>
</xsl:variable>

<xsl:variable name="show-repository-link">
  <xsl:choose>
    <xsl:when test="$config/ui/show-repository-link"><xsl:value-of select="$config/ui/show-repository-link"/></xsl:when>
    <xsl:otherwise>true</xsl:otherwise>
  </xsl:choose>
</xsl:variable>

<!--
 Sample stylesheet to be used with Ant JUnitReport output.

 It creates a non-framed report that can be useful to send via
 e-mail or such.
 
 Enhanced with Eclipse Platform features:
 - Navigation buttons to jump between errors/failures
 - Keyboard shortcuts (Ctrl+. and Ctrl+,)
 - Improved styling
-->
<xsl:template match="testsuites">
    <html>
        <head>
            <title><xsl:value-of select="$TITLE"/></title>
    <style type="text/css">
      /* Modern CSS Reset and Base Styles */
      * {
        box-sizing: border-box;
      }
      
      body {
        font-family: system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, Cantarell, sans-serif;
        font-size: 16px;
        line-height: 1.6;
        color: #2c3e50;
        background-color: #f8f9fa;
        margin: 0;
        padding: 20px;
        transition: background-color 0.3s, color 0.3s;
      }
      
      /* Modern Typography */
      h1 {
        margin: 0 0 20px;
        font-size: 2.5rem;
        font-weight: 700;
        color: #1a1a1a;
      }
      
      h2 {
        margin: 2rem 0 1rem;
        font-size: 1.8rem;
        font-weight: 600;
        color: #2c3e50;
      }
      
      h3 {
        margin: 1.5rem 0 0.8rem;
        font-size: 1.4rem;
        font-weight: 600;
        color: #34495e;
      }
      
      h4, h5, h6 {
        margin: 1rem 0 0.5rem;
        font-size: 1.1rem;
        font-weight: 600;
        color: #34495e;
      }
      
      p {
        line-height: 1.6;
        margin: 0.8em 0;
      }
      
      a {
        color: #3498db;
        text-decoration: none;
        transition: color 0.2s;
      }
      
      a:hover {
        color: #2980b9;
        text-decoration: underline;
      }
      
      hr {
        border: none;
        border-top: 2px solid #e1e4e8;
        margin: 2rem 0;
      }
      
      /* Modern Table Styling */
      table.details {
        width: 100%;
        max-width: 100%;
        border-collapse: collapse;
        background-color: white;
        border-radius: 8px;
        overflow: hidden;
        box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
        margin: 1rem 0;
      }
      
      table.details th {
        background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
        color: white;
        font-weight: 600;
        text-align: left;
        padding: 14px 16px;
        font-size: 0.95rem;
        text-transform: uppercase;
        letter-spacing: 0.5px;
        cursor: pointer;
        user-select: none;
        position: relative;
      }
      
      table.details th:hover {
        background: linear-gradient(135deg, #5568d3 0%, #6a3f92 100%);
      }
      
      table.details th.sortable::after {
        content: ' ⇅';
        opacity: 0.5;
        font-size: 0.8em;
      }
      
      table.details th.sort-asc::after {
        content: ' ▲';
        opacity: 1;
      }
      
      table.details th.sort-desc::after {
        content: ' ▼';
        opacity: 1;
      }
      
      table.details td {
        padding: 12px 16px;
        border-bottom: 1px solid #e1e4e8;
        font-size: 0.95rem;
      }
      
      table.details tr:last-child td {
        border-bottom: none;
      }
      
      table.details tr:hover td {
        background-color: #f8f9fa;
      }
      
      /* Status Colors */
      .Error {
        background-color: #fee;
        font-weight: 600;
        color: #c92a2a;
      }
      
      .Failure {
        background-color: #fef3cd;
        font-weight: 600;
        color: #856404;
      }
      
      .Skipped {
        background-color: #e8f4fd;
        color: #0969da;
      }
      
      .Success {
        background-color: #e6ffe6;
      }
      
      /* Progress Bar */
      .progress-bar-container {
        width: 100%;
        height: 40px;
        background-color: #e1e4e8;
        border-radius: 20px;
        overflow: hidden;
        margin: 1.5rem 0;
        box-shadow: inset 0 2px 4px rgba(0, 0, 0, 0.1);
        position: relative;
      }
      
      .progress-bar {
        display: flex;
        height: 100%;
        transition: all 0.5s ease;
      }
      
      .progress-segment {
        display: flex;
        align-items: center;
        justify-content: center;
        color: white;
        font-weight: 600;
        font-size: 0.9rem;
        text-shadow: 0 1px 2px rgba(0, 0, 0, 0.2);
        transition: all 0.3s ease;
      }
      
      .progress-success {
        background: linear-gradient(135deg, #4caf50 0%, #45a049 100%);
      }
      
      .progress-error {
        background: linear-gradient(135deg, #f44336 0%, #d32f2f 100%);
      }
      
      .progress-skipped {
        background: linear-gradient(135deg, #ff9800 0%, #f57c00 100%);
      }
      
      /* Navigation and Filter Buttons */
      .nav-buttons {
        position: fixed;
        top: 20px;
        right: 20px;
        z-index: 1000;
        display: flex;
        gap: 10px;
        flex-wrap: wrap;
        max-width: 300px;
      }
      
      .nav-buttons button,
      .filter-buttons button {
        font-size: 14px;
        padding: 10px 16px;
        cursor: pointer;
        background: white;
        color: #2c3e50;
        border: 2px solid #3498db;
        border-radius: 6px;
        font-weight: 600;
        transition: all 0.2s;
        box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
      }
      
      .nav-buttons button:hover,
      .filter-buttons button:hover {
        background: #3498db;
        color: white;
        transform: translateY(-2px);
        box-shadow: 0 4px 8px rgba(0, 0, 0, 0.15);
      }
      
      .nav-buttons button:active,
      .filter-buttons button:active {
        transform: translateY(0);
      }
      
      .filter-buttons {
        display: flex;
        gap: 10px;
        margin: 1.5rem 0;
        flex-wrap: wrap;
      }
      
      .filter-buttons button.active {
        background: #3498db;
        color: white;
      }
      
      /* Collapsible Stacktraces */
      .stacktrace-container {
        margin-top: 10px;
      }
      
      .stacktrace-toggle {
        background: #3498db;
        color: white;
        border: none;
        padding: 6px 12px;
        border-radius: 4px;
        cursor: pointer;
        font-size: 0.85rem;
        font-weight: 600;
        transition: all 0.2s;
      }
      
      .stacktrace-toggle:hover {
        background: #2980b9;
      }
      
      .stacktrace-content {
        display: none;
        margin-top: 10px;
        padding: 12px;
        background: #1e1e1e;
        color: #d4d4d4;
        border-radius: 6px;
        font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
        font-size: 0.85rem;
        line-height: 1.5;
        overflow-x: auto;
        box-shadow: inset 0 2px 4px rgba(0, 0, 0, 0.3);
      }
      
      .stacktrace-content.show {
        display: block;
      }
      
      .stacktrace-content code {
        color: #d4d4d4;
      }
      
      .stacktrace-line {
        white-space: pre;
      }
      
      .stacktrace-line-number {
        color: #858585;
        margin-right: 10px;
      }
      
      /* Permalink functionality */
      .test-row {
        scroll-margin-top: 80px;
      }
      
      .permalink {
        margin-left: 8px;
        opacity: 0;
        transition: opacity 0.2s;
        font-size: 0.8em;
        cursor: pointer;
      }
      
      .coverage-link {
        margin-left: 10px;
        font-size: 1.2em;
        text-decoration: none;
        transition: transform 0.2s;
        display: inline-block;
      }
      
      .coverage-link:hover {
        transform: scale(1.2);
      }
      
      h3 .coverage-link {
        vertical-align: middle;
      }
      
      .test-row:hover .permalink,
      .test-row:target .permalink {
        opacity: 1;
      }
      
      .test-row:target td {
        background-color: #fff3cd !important;
        animation: highlight-fade 2s ease-out;
      }
      
      @keyframes highlight-fade {
        0% { background-color: #ffeb3b; }
        100% { background-color: #fff3cd; }
      }
      
      /* Properties styling */
      .Properties {
        text-align: right;
        margin: 1rem 0;
      }
      
      /* Responsive Design */
      @media (max-width: 768px) {
        body {
          padding: 10px;
          font-size: 14px;
        }
        
        h1 {
          font-size: 1.8rem;
        }
        
        h2 {
          font-size: 1.4rem;
        }
        
        h3 {
          font-size: 1.2rem;
        }
        
        .nav-buttons {
          position: static;
          margin-bottom: 20px;
          max-width: 100%;
        }
        
        table.details {
          font-size: 0.85rem;
          display: block;
          overflow-x: auto;
          white-space: nowrap;
        }
        
        table.details th,
        table.details td {
          padding: 8px 10px;
        }
        
        .progress-bar-container {
          height: 30px;
        }
        
        .progress-segment {
          font-size: 0.75rem;
        }
      }
      
      @media (max-width: 480px) {
        body {
          font-size: 13px;
        }
        
        h1 {
          font-size: 1.5rem;
        }
        
        .filter-buttons button,
        .nav-buttons button {
          font-size: 12px;
          padding: 8px 12px;
        }
      }
      
      /* Dark Mode Support */
      @media (prefers-color-scheme: dark) {
        body {
          background-color: #1a1a1a;
          color: #e1e4e8;
        }
        
        h1, h2, h3, h4, h5, h6 {
          color: #e1e4e8;
        }
        
        a {
          color: #58a6ff;
        }
        
        a:hover {
          color: #79b8ff;
        }
        
        hr {
          border-top-color: #30363d;
        }
        
        table.details {
          background-color: #161b22;
          box-shadow: 0 2px 8px rgba(0, 0, 0, 0.4);
        }
        
        table.details th {
          background: linear-gradient(135deg, #4a5568 0%, #2d3748 100%);
        }
        
        table.details th:hover {
          background: linear-gradient(135deg, #3a4555 0%, #1d2738 100%);
        }
        
        table.details td {
          border-bottom-color: #30363d;
        }
        
        table.details tr:hover td {
          background-color: #21262d;
        }
        
        .Error {
          background-color: #3d1616;
          color: #ff6b6b;
        }
        
        .Failure {
          background-color: #3d3416;
          color: #ffc078;
        }
        
        .Skipped {
          background-color: #16293d;
          color: #79b8ff;
        }
        
        .Success {
          background-color: #163d16;
        }
        
        .progress-bar-container {
          background-color: #30363d;
        }
        
        .nav-buttons button,
        .filter-buttons button {
          background: #21262d;
          color: #e1e4e8;
          border-color: #58a6ff;
        }
        
        .nav-buttons button:hover,
        .filter-buttons button:hover {
          background: #58a6ff;
          color: #0d1117;
        }
        
        .filter-buttons button.active {
          background: #58a6ff;
          color: #0d1117;
        }
        
        .stacktrace-toggle {
          background: #58a6ff;
          color: #0d1117;
        }
        
        .stacktrace-toggle:hover {
          background: #79b8ff;
        }
      }
      
      /* Print Styles */
      @media print {
        body {
          background: white;
          color: black;
        }
        
        .nav-buttons,
        .filter-buttons,
        .stacktrace-toggle {
          display: none;
        }
        
        .stacktrace-content {
          display: block !important;
        }
        
        table.details {
          box-shadow: none;
          border: 1px solid #ddd;
        }
      }
      
      /* Hidden class for filtering */
      .hidden {
        display: none !important;
      }
      
      /* Style for default package indication */
      .default-package {
        font-style: italic;
        color: #888;
      }
      </style>
      <script type="text/javascript" language="JavaScript">
        var TestCases = new Array();
        var cur;
        var GITHUB_REPO = '<xsl:value-of select="$GITHUB_REPO"/>';
        var GITHUB_BRANCH = '<xsl:value-of select="$GITHUB_BRANCH"/>';
        <xsl:for-each select="./testsuite">
            <xsl:apply-templates select="properties"/>
        </xsl:for-each>

       </script>
       <script type="text/javascript" language="JavaScript"><![CDATA[
        function displayProperties (name) {
          var win = window.open('','JUnitSystemProperties','scrollbars=1,resizable=1');
          var doc = win.document;
          doc.open();
          doc.write("<html><head><title>Properties of " + escapeHtml(name) + "</title>");
          doc.write("<style>")
          doc.write("body {font-family: system-ui, sans-serif; font-size: 16px; color:#2c3e50; background:#f8f9fa; padding:20px; }");
          doc.write("table.properties { border-collapse:collapse; width:100%; background:white; border-radius:8px; overflow:hidden; box-shadow:0 2px 8px rgba(0,0,0,0.1); }");
          doc.write("table.properties th { text-align:left; padding:12px; background:#667eea; color:white; font-weight:600; }");
          doc.write("table.properties td { padding:12px; border-bottom:1px solid #e1e4e8; }");
          doc.write("h3 { margin-bottom: 1rem; font-size:1.4rem; font-weight:600; }");
          doc.write("a { color:#3498db; text-decoration:none; }");
          doc.write("a:hover { text-decoration:underline; }");
          doc.write("@media (prefers-color-scheme: dark) {");
          doc.write("  body { background:#1a1a1a; color:#e1e4e8; }");
          doc.write("  table.properties { background:#161b22; }");
          doc.write("  table.properties th { background:#4a5568; }");
          doc.write("  table.properties td { border-bottom-color:#30363d; }");
          doc.write("  a { color:#58a6ff; }");
          doc.write("}");
          doc.write("</style>");
          doc.write("</head><body>");
          doc.write("<h3>Properties of " + escapeHtml(name) + "</h3>");
          doc.write("<div align=\"right\"><a href=\"javascript:window.close();\">Close</a></div>");
          doc.write("<table class='properties'>");
          doc.write("<tr><th>Name</th><th>Value</th></tr>");
          for (prop in TestCases[name]) {
            doc.write("<tr><td>" + escapeHtml(prop) + "</td><td>" + escapeHtml(TestCases[name][prop]) + "</td></tr>");
          }
          doc.write("</table>");
          doc.write("</body></html>");
          doc.close();
          win.focus();
        }
        
        // HTML escape function to prevent XSS
        function escapeHtml(text) {
          var div = document.createElement('div');
          div.textContent = text;
          return div.innerHTML;
        }
        
        // Jump to next/previous error or failure
        function jumpToNextError(forward) {
          var errors = document.querySelectorAll('tr.Error, tr.Failure');
          if (errors.length === 0) return;
          
          var currentScroll = window.pageYOffset || document.documentElement.scrollTop;
          var target = null;
          
          if (forward) {
            // Find first error below current position
            for (var i = 0; i < errors.length; i++) {
              var errorTop = errors[i].getBoundingClientRect().top + currentScroll;
              if (errorTop > currentScroll + 10) {
                target = errors[i];
                break;
              }
            }
            // If none found, wrap to first
            if (!target) target = errors[0];
          } else {
            // Find last error above current position
            for (var i = errors.length - 1; i >= 0; i--) {
              var errorTop = errors[i].getBoundingClientRect().top + currentScroll;
              if (errorTop < currentScroll - 10) {
                target = errors[i];
                break;
              }
            }
            // If none found, wrap to last
            if (!target) target = errors[errors.length - 1];
          }
          
          if (target) {
            target.scrollIntoView({behavior: 'smooth', block: 'center'});
          }
        }
        
        // Filter functionality
        var currentFilter = 'all';
        
        function filterTests(filterType) {
          currentFilter = filterType;
          // Only filter test-row elements, not summary/package rows
          var allRows = document.querySelectorAll('tr.test-row');
          var buttons = document.querySelectorAll('.filter-buttons button');
          
          // Update button states
          buttons.forEach(function(btn) {
            if (btn.getAttribute('data-filter') === filterType) {
              btn.classList.add('active');
            } else {
              btn.classList.remove('active');
            }
          });
          
          // Filter rows
          allRows.forEach(function(row) {
            if (filterType === 'all') {
              row.classList.remove('hidden');
            } else if (filterType === 'errors') {
              if (row.classList.contains('Error') || row.classList.contains('Failure')) {
                row.classList.remove('hidden');
              } else {
                row.classList.add('hidden');
              }
            } else if (filterType === 'skipped') {
              if (row.classList.contains('Skipped')) {
                row.classList.remove('hidden');
              } else {
                row.classList.add('hidden');
              }
            } else if (filterType === 'success') {
              if (!row.classList.contains('Error') && !row.classList.contains('Failure') && !row.classList.contains('Skipped')) {
                row.classList.remove('hidden');
              } else {
                row.classList.add('hidden');
              }
            }
          });
        }
        
        // Toggle stacktrace visibility
        function toggleStacktrace(testId) {
          var content = document.getElementById('stacktrace-' + testId);
          var button = document.getElementById('toggle-' + testId);
          if (content && button) {
            content.classList.toggle('show');
            if (content.classList.contains('show')) {
              button.textContent = 'Hide Details';
            } else {
              button.textContent = 'Show Details';
            }
          }
        }
        
        // Copy permalink to clipboard
        function copyPermalink(testId) {
          var url = window.location.href.split('#')[0] + '#test-' + testId;
          if (navigator.clipboard && navigator.clipboard.writeText) {
            navigator.clipboard.writeText(url).then(function() {
              showTooltip('Link copied!');
            });
          } else {
            // Fallback for older browsers
            var input = document.createElement('input');
            input.value = url;
            document.body.appendChild(input);
            input.select();
            document.execCommand('copy');
            document.body.removeChild(input);
            showTooltip('Link copied!');
          }
          window.location.hash = '#test-' + testId;
        }
        
        function showTooltip(message) {
          var tooltip = document.createElement('div');
          tooltip.textContent = message;
          tooltip.style.position = 'fixed';
          tooltip.style.top = '50%';
          tooltip.style.left = '50%';
          tooltip.style.transform = 'translate(-50%, -50%)';
          tooltip.style.background = '#4caf50';
          tooltip.style.color = 'white';
          tooltip.style.padding = '12px 24px';
          tooltip.style.borderRadius = '8px';
          tooltip.style.fontSize = '16px';
          tooltip.style.fontWeight = '600';
          tooltip.style.boxShadow = '0 4px 12px rgba(0,0,0,0.3)';
          tooltip.style.zIndex = '10000';
          tooltip.style.animation = 'fadeInOut 2s ease';
          document.body.appendChild(tooltip);
          setTimeout(function() {
            document.body.removeChild(tooltip);
          }, 2000);
        }
        
        // Table sorting functionality
        function sortTable(tableId, columnIndex) {
          var table = document.getElementById(tableId);
          if (!table) return;
          
          var tbody = table.querySelector('tbody');
          if (!tbody) {
            tbody = table;
          }
          
          var rows = Array.from(tbody.querySelectorAll('tr'));
          var header = table.querySelector('thead th:nth-child(' + (columnIndex + 1) + ')');
          if (!header) {
            header = table.querySelector('th:nth-child(' + (columnIndex + 1) + ')');
          }
          
          if (!header) return;
          
          var isAsc = header.classList.contains('sort-asc');
          var isDesc = header.classList.contains('sort-desc');
          
          // Remove sorting classes from all headers
          var allHeaders = table.querySelectorAll('th');
          allHeaders.forEach(function(h) {
            h.classList.remove('sort-asc', 'sort-desc');
          });
          
          // Determine new sort direction
          var ascending = !isAsc;
          if (isDesc) ascending = true;
          
          // Apply new sort class
          header.classList.add(ascending ? 'sort-asc' : 'sort-desc');
          
          // Sort rows
          rows.sort(function(a, b) {
            var aCell = a.cells[columnIndex];
            var bCell = b.cells[columnIndex];
            if (!aCell || !bCell) return 0;
            
            var aValue = aCell.textContent.trim();
            var bValue = bCell.textContent.trim();
            
            // Try to parse as number
            var aNum = parseFloat(aValue);
            var bNum = parseFloat(bValue);
            
            if (!isNaN(aNum) && !isNaN(bNum)) {
              return ascending ? aNum - bNum : bNum - aNum;
            }
            
            // String comparison
            return ascending ? aValue.localeCompare(bValue) : bValue.localeCompare(aValue);
          });
          
          // Re-append rows in sorted order
          rows.forEach(function(row) {
            tbody.appendChild(row);
          });
        }
        
        // Initialize sortable tables
        function initSortableTables() {
          var tables = document.querySelectorAll('table.details');
          tables.forEach(function(table, tableIdx) {
            var tableId = 'sortable-table-' + tableIdx;
            table.id = tableId;
            
            var headers = table.querySelectorAll('th');
            headers.forEach(function(header, colIdx) {
              header.classList.add('sortable');
              header.onclick = function() {
                sortTable(tableId, colIdx);
              };
            });
          });
        }
        
        // Add GitHub links to stacktraces
        function addGitHubLinks() {
          var githubRepo = GITHUB_REPO;
          var githubBranch = GITHUB_BRANCH;
          
          if (!githubRepo) return;
          
          // Find all code elements in stacktraces
          var codeElements = document.querySelectorAll('.stacktrace-content code');
          codeElements.forEach(function(codeEl) {
            var text = codeEl.innerHTML;
            // Match patterns like "at org.example.Test.method(Test.java:42)"
            var pattern = /at\s+([\w.$]+)\(([\w.]+):(\d+)\)/g;
            var newHtml = text.replace(pattern, function(match, fullClass, fileName, lineNum) {
              // Extract package from class name (e.g., org.example.Test -> org/example)
              var parts = fullClass.split('.');
              var className = parts[parts.length - 1];
              var packagePath = parts.slice(0, -1).join('/');
              
              // Try to construct a reasonable file path
              // Assumes standard Maven/Gradle structure
              var possiblePaths = [
                'src/test/java/' + packagePath + '/' + fileName,
                'src/main/java/' + packagePath + '/' + fileName,
                packagePath + '/' + fileName,
                fileName
              ];
              
              // Use the first path (most likely for tests)
              var filePath = possiblePaths[0];
              var url = 'https://github.com/' + githubRepo + '/blob/' + githubBranch + '/' + filePath + '#L' + lineNum;
              
              return 'at ' + fullClass + '(<a href="' + url + '" target="_blank" style="color:#58a6ff;text-decoration:underline;">' + fileName + ':' + lineNum + '</a>)';
            });
            codeEl.innerHTML = newHtml;
          });
        }
        
        // Keyboard shortcuts
        document.addEventListener('keydown', function(e) {
          // Ctrl+. (period) - next error
          if (e.ctrlKey && e.key === '.') {
            e.preventDefault();
            jumpToNextError(true);
          }
          // Ctrl+, (comma) - previous error
          else if (e.ctrlKey && e.key === ',') {
            e.preventDefault();
            jumpToNextError(false);
          }
        });
        
        // Initialize on page load
        document.addEventListener('DOMContentLoaded', function() {
          initSortableTables();
          addGitHubLinks();
          
          // Scroll to hash target if present
          if (window.location.hash) {
            var target = document.getElementById(window.location.hash.substring(1));
            if (target) {
              setTimeout(function() {
                target.scrollIntoView({behavior: 'smooth', block: 'center'});
              }, 100);
            }
          }
        });
        
        // CSS animation for tooltip
        var style = document.createElement('style');
        style.textContent = '@keyframes fadeInOut { 0% { opacity: 0; transform: translate(-50%, -60%); } 10% { opacity: 1; transform: translate(-50%, -50%); } 90% { opacity: 1; transform: translate(-50%, -50%); } 100% { opacity: 0; transform: translate(-50%, -40%); } }';
        document.head.appendChild(style);
      ]]>
      </script>
        </head>
        <body>
            <a name="top"></a>
            <xsl:call-template name="pageHeader"/>

            <!-- Summary part -->
            <xsl:call-template name="summary"/>
            <hr size="1" width="95%" align="left"/>

            <!-- Package List part -->
            <xsl:call-template name="packagelist"/>
            <hr size="1" width="95%" align="left"/>

            <!-- For each package create its part -->
            <xsl:call-template name="packages"/>
            <hr size="1" width="95%" align="left"/>

            <!-- For each class create the  part -->
            <xsl:call-template name="classes"/>
            
            <!-- Footer with navigation links -->
            <hr size="1"/>
            <p style="text-align: center; color: #666; margin-top: 30px;">
                <a>
                    <xsl:attribute name="href"><xsl:value-of select="$tests-base"/>/</xsl:attribute>
                    All Test Reports
                </a>
                 | 
                <a>
                    <xsl:attribute name="href"><xsl:value-of select="$github-pages-base"/><xsl:value-of select="$coverage-base"/>/</xsl:attribute>
                    Coverage Reports
                </a>
                <xsl:if test="$show-repository-link = 'true'">
                     | 
                    <a>
                        <xsl:attribute name="href"><xsl:value-of select="$repository-url"/></xsl:attribute>
                        GitHub Repository
                    </a>
                </xsl:if>
            </p>
            <p style="text-align: center; color: #999; font-size: 0.9em;">
                Generated by <a>
                    <xsl:attribute name="href"><xsl:value-of select="$repository-url"/></xsl:attribute>
                    <xsl:value-of select="$project-name"/>
                </a>
            </p>

        </body>
    </html>
</xsl:template>



    <!-- ================================================================== -->
    <!-- Write a list of all packages with an hyperlink to the anchor of    -->
    <!-- of the package name.                                               -->
    <!-- ================================================================== -->
    <xsl:template name="packagelist">
        <h2>Packages</h2>
        Note: package statistics are not computed recursively, they only sum up all of its testsuites numbers.
        <table class="details" border="0" cellpadding="5" cellspacing="2" width="95%">
            <xsl:call-template name="testsuite.test.header"/>
            <!-- list all packages recursively -->
            <xsl:for-each select="./testsuite[not(./@package = preceding-sibling::testsuite/@package)]">
                <xsl:sort select="@package"/>
                <xsl:variable name="testsuites-in-package" select="/testsuites/testsuite[./@package = current()/@package]"/>
                <xsl:variable name="testCount" select="sum($testsuites-in-package/@tests)"/>
                <xsl:variable name="errorCount" select="sum($testsuites-in-package/@errors)"/>
                <xsl:variable name="failureCount" select="sum($testsuites-in-package/@failures)"/>
                <xsl:variable name="skippedCount" select="sum($testsuites-in-package/@skipped)" />
                <xsl:variable name="timeCount" select="sum($testsuites-in-package/@time)"/>

                <!-- write a summary for the package -->
                <tr valign="top">
                    <!-- set a nice color depending if there is an error/failure -->
                    <xsl:attribute name="class">
                        <xsl:choose>
                            <xsl:when test="$failureCount &gt; 0">Failure</xsl:when>
                            <xsl:when test="$errorCount &gt; 0">Error</xsl:when>
                        </xsl:choose>
                    </xsl:attribute>
                    <td>
                        <a href="#{@package}">
                            <xsl:choose>
                                <xsl:when test="normalize-space(@package)">
                                    <xsl:value-of select="@package"/>
                                </xsl:when>
                                <xsl:otherwise>
                                    <span class="default-package">(default package)</span>
                                </xsl:otherwise>
                            </xsl:choose>
                        </a>
                    </td>
                    <td><xsl:value-of select="$testCount"/></td>
                    <td><xsl:value-of select="$errorCount"/></td>
                    <td><xsl:value-of select="$failureCount"/></td>
                    <td><xsl:value-of select="$skippedCount" /></td>
                    <td>
                    <xsl:call-template name="display-time">
                        <xsl:with-param name="value" select="$timeCount"/>
                    </xsl:call-template>
                    </td>
                    <td><xsl:value-of select="$testsuites-in-package/@timestamp"/></td>
                    <td><xsl:value-of select="$testsuites-in-package/@hostname"/></td>
                </tr>
            </xsl:for-each>
        </table>
    </xsl:template>


    <!-- ================================================================== -->
    <!-- Write a package level report                                       -->
    <!-- It creates a table with values from the document:                  -->
    <!-- Name | Tests | Errors | Failures | Time                            -->
    <!-- ================================================================== -->
    <xsl:template name="packages">
        <!-- create an anchor to this package name -->
        <xsl:for-each select="/testsuites/testsuite[not(./@package = preceding-sibling::testsuite/@package)]">
            <xsl:sort select="@package"/>
                <a name="{@package}"></a>
                <h3>
                    <xsl:text>Package </xsl:text>
                    <xsl:choose>
                        <xsl:when test="normalize-space(@package)">
                            <xsl:value-of select="@package"/>
                        </xsl:when>
                        <xsl:otherwise>
                            <span class="default-package">(default package)</span>
                        </xsl:otherwise>
                    </xsl:choose>
                </h3>

                <table class="details" border="0" cellpadding="5" cellspacing="2" width="95%">
                    <xsl:call-template name="testsuite.test.header"/>

                    <!-- match the testsuites of this package -->
                    <xsl:apply-templates select="/testsuites/testsuite[./@package = current()/@package]" mode="print.test"/>
                </table>
                <a href="#top">Back to top</a>
                <p/>
                <p/>
        </xsl:for-each>
    </xsl:template>

    <xsl:template name="classes">
        <xsl:for-each select="testsuite">
            <xsl:sort select="@name"/>
            <!-- create an anchor to this class name -->
            <a name="{@name}"></a>
            <h3>
                TestCase <xsl:value-of select="@name"/>
                <!-- Extract module name from TITLE parameter -->
                <xsl:variable name="test-module">
                    <xsl:choose>
                        <xsl:when test="contains($TITLE, ' - ')">
                            <xsl:value-of select="substring-after($TITLE, ' - ')"/>
                        </xsl:when>
                        <xsl:otherwise></xsl:otherwise>
                    </xsl:choose>
                </xsl:variable>
                <xsl:call-template name="generate-coverage-link">
                    <xsl:with-param name="test-module" select="$test-module"/>
                    <xsl:with-param name="package" select="@package"/>
                    <xsl:with-param name="class" select="@name"/>
                </xsl:call-template>
            </h3>

            <table class="details" border="0" cellpadding="5" cellspacing="2" width="95%">
              <xsl:call-template name="testcase.test.header"/>
              <!--
              test can even not be started at all (failure to load the class)
              so report the error directly
              -->
                <xsl:if test="./error">
                    <tr class="Error">
                        <td colspan="4"><xsl:apply-templates select="./error"/></td>
                    </tr>
                </xsl:if>
                <xsl:apply-templates select="./testcase" mode="print.test"/>
            </table>
            <div class="Properties">
                <a>
                    <xsl:attribute name="href">javascript:displayProperties('<xsl:value-of select="@package"/>.<xsl:value-of select="@name"/>');</xsl:attribute>
                    Properties &#187;
                </a>
            </div>
            <p/>

            <a href="#top">Back to top</a>
        </xsl:for-each>
    </xsl:template>

    <xsl:template name="summary">
        <h2>Summary</h2>
        <xsl:variable name="testCount" select="sum(testsuite/@tests)"/>
        <xsl:variable name="errorCount" select="sum(testsuite/@errors)"/>
        <xsl:variable name="failureCount" select="sum(testsuite/@failures)"/>
        <xsl:variable name="skippedCount" select="sum(testsuite/@skipped)" />
        <xsl:variable name="timeCount" select="sum(testsuite/@time)"/>
        <xsl:variable name="successCount" select="$testCount - $failureCount - $errorCount - $skippedCount"/>
        <xsl:variable name="successRate" select="$successCount div $testCount"/>
        <xsl:variable name="errorRate" select="($failureCount + $errorCount) div $testCount"/>
        <xsl:variable name="skippedRate" select="$skippedCount div $testCount"/>
        
        <!-- Progress Bar -->
        <div class="progress-bar-container">
            <div class="progress-bar">
                <xsl:if test="$successCount &gt; 0">
                    <div class="progress-segment progress-success">
                        <xsl:attribute name="style">width: <xsl:value-of select="$successRate * 100"/>%;</xsl:attribute>
                        <xsl:if test="$successRate &gt; 0.15">
                            <xsl:value-of select="format-number($successRate, '0.0%')"/>
                        </xsl:if>
                    </div>
                </xsl:if>
                <xsl:if test="($failureCount + $errorCount) &gt; 0">
                    <div class="progress-segment progress-error">
                        <xsl:attribute name="style">width: <xsl:value-of select="$errorRate * 100"/>%;</xsl:attribute>
                        <xsl:if test="$errorRate &gt; 0.08">
                            <xsl:value-of select="format-number($errorRate, '0.0%')"/>
                        </xsl:if>
                    </div>
                </xsl:if>
                <xsl:if test="$skippedCount &gt; 0">
                    <div class="progress-segment progress-skipped">
                        <xsl:attribute name="style">width: <xsl:value-of select="$skippedRate * 100"/>%;</xsl:attribute>
                        <xsl:if test="$skippedRate &gt; 0.08">
                            <xsl:value-of select="format-number($skippedRate, '0.0%')"/>
                        </xsl:if>
                    </div>
                </xsl:if>
            </div>
        </div>
        
        <table class="details" border="0" cellpadding="5" cellspacing="2" width="95%">
        <tr valign="top">
            <th>Tests</th>
            <th>Failures</th>
            <th>Errors</th>
            <th>Skipped</th>
            <th>Success rate</th>
            <th>Time</th>
        </tr>
        <tr valign="top">
            <xsl:attribute name="class">
                <xsl:choose>
                    <xsl:when test="$failureCount &gt; 0">Failure</xsl:when>
                    <xsl:when test="$errorCount &gt; 0">Error</xsl:when>
                    <xsl:otherwise>Success</xsl:otherwise>
                </xsl:choose>
            </xsl:attribute>
            <td><xsl:value-of select="$testCount"/></td>
            <td><xsl:value-of select="$failureCount"/></td>
            <td><xsl:value-of select="$errorCount"/></td>
            <td><xsl:value-of select="$skippedCount" /></td>
            <td>
                <xsl:call-template name="display-percent">
                    <xsl:with-param name="value" select="$successRate"/>
                </xsl:call-template>
            </td>
            <td>
                <xsl:call-template name="display-time">
                    <xsl:with-param name="value" select="$timeCount"/>
                </xsl:call-template>
            </td>

        </tr>
        </table>
        <table border="0" width="95%">
        <tr>
        <td style="text-align: justify;">
        Note: <i>failures</i> are anticipated and checked for with assertions while <i>errors</i> are unanticipated.
        </td>
        </tr>
        </table>
    </xsl:template>

  <!--
   Write properties into a JavaScript data structure.
   This is based on the original idea by Erik Hatcher (ehatcher@apache.org)
   -->
  <xsl:template match="properties">
    cur = TestCases['<xsl:value-of select="../@package"/>.<xsl:value-of select="../@name"/>'] = new Array();
    <xsl:for-each select="property">
    <xsl:sort select="@name"/>
        cur['<xsl:value-of select="@name"/>'] = '<xsl:call-template name="JS-escape"><xsl:with-param name="string" select="@value"/></xsl:call-template>';
    </xsl:for-each>
  </xsl:template>

<!-- Page HEADER -->
<xsl:template name="pageHeader">
    <h1><xsl:value-of select="$TITLE"/></h1>
    <div class="nav-buttons">
        <button title="Scroll to previous failure/error (Ctrl+,)" onclick="javascript:jumpToNextError(false);">⬆ Prev Error</button>
        <button title="Scroll to next failure/error (Ctrl+.)" onclick="javascript:jumpToNextError(true);">⬇ Next Error</button>
    </div>
    <table width="100%">
    <tr>
        <td align="left">
            <a href="../">All Test Modules</a>
            <xsl:if test="$show-repository-link = 'true'">
                 | <a>
                    <xsl:attribute name="href"><xsl:value-of select="$repository-url"/></xsl:attribute>
                    GitHub Repository
                </a>
            </xsl:if>
             | <a>
                <xsl:attribute name="href"><xsl:value-of select="$github-pages-base"/><xsl:value-of select="$coverage-base"/>/</xsl:attribute>
                Coverage Reports
            </a>
        </td>
        <td align="right">Designed for use with <a href='http://junit.org/'>JUnit</a>.</td>
    </tr>
    </table>
    <hr size="1"/>
    <div class="filter-buttons">
        <button data-filter="all" class="active" onclick="filterTests('all')">📊 Show All</button>
        <button data-filter="errors" onclick="filterTests('errors')">❌ Errors/Failures Only</button>
        <button data-filter="skipped" onclick="filterTests('skipped')">⏭ Skipped Only</button>
        <button data-filter="success" onclick="filterTests('success')">✅ Success Only</button>
    </div>
</xsl:template>

<xsl:template match="testsuite" mode="header">
    <tr valign="top">
        <th width="80%">Name</th>
        <th>Tests</th>
        <th>Errors</th>
        <th>Failures</th>
        <th>Skipped</th>
        <th nowrap="nowrap">Time(s)</th>
    </tr>
</xsl:template>

<!-- class header -->
<xsl:template name="testsuite.test.header">
    <tr valign="top">
        <th width="80%">Name</th>
        <th>Tests</th>
        <th>Errors</th>
        <th>Failures</th>
        <th>Skipped</th>
        <th nowrap="nowrap">Time(s)</th>
        <th nowrap="nowrap">Time Stamp</th>
        <th>Host</th>
    </tr>
</xsl:template>

<!-- method header -->
<xsl:template name="testcase.test.header">
    <tr valign="top">
        <th>Name</th>
        <th>Status</th>
        <th width="80%">Type</th>
        <th nowrap="nowrap">Time(s)</th>
    </tr>
</xsl:template>


<!-- class information -->
<xsl:template match="testsuite" mode="print.test">
    <tr valign="top">
        <!-- set a nice color depending if there is an error/failure -->
        <xsl:attribute name="class">
            <xsl:choose>
                <xsl:when test="@failures[.&gt; 0]">Failure</xsl:when>
                <xsl:when test="@errors[.&gt; 0]">Error</xsl:when>
            </xsl:choose>
        </xsl:attribute>

        <!-- print testsuite information -->
        <td><a href="#{@name}"><xsl:value-of select="@name"/></a></td>
        <td><xsl:value-of select="@tests"/></td>
        <td><xsl:value-of select="@errors"/></td>
        <td><xsl:value-of select="@failures"/></td>
        <td><xsl:value-of select="@skipped" /></td>
        <td>
            <xsl:call-template name="display-time">
                <xsl:with-param name="value" select="@time"/>
            </xsl:call-template>
        </td>
        <td><xsl:apply-templates select="@timestamp"/></td>
        <td><xsl:apply-templates select="@hostname"/></td>
    </tr>
</xsl:template>

<xsl:template match="testcase" mode="print.test">
    <xsl:variable name="testId" select="generate-id()"/>
    <tr valign="top" class="test-row">
        <xsl:attribute name="id">test-<xsl:value-of select="$testId"/></xsl:attribute>
        <xsl:attribute name="class">
            <xsl:text>test-row </xsl:text>
            <xsl:choose>
                <xsl:when test="failure">Failure</xsl:when>
                <xsl:when test="error">Error</xsl:when>
                <xsl:when test="skipped">Skipped</xsl:when>
                <xsl:otherwise>Success</xsl:otherwise>
            </xsl:choose>
        </xsl:attribute>
        <td>
            <xsl:value-of select="@name"/>
            <span class="permalink" title="Copy link to this test">
                <xsl:attribute name="onclick">copyPermalink('<xsl:value-of select="$testId"/>')</xsl:attribute>
                🔗
            </span>
        </td>
        <xsl:choose>
            <xsl:when test="failure">
                <td>Failure</td>
                <td><xsl:apply-templates select="failure"><xsl:with-param name="testId" select="$testId"/></xsl:apply-templates></td>
            </xsl:when>
            <xsl:when test="error">
                <td>Error</td>
                <td><xsl:apply-templates select="error"><xsl:with-param name="testId" select="$testId"/></xsl:apply-templates></td>
            </xsl:when>
            <xsl:when test="skipped">
            	<td>Skipped</td>
            	<td><xsl:apply-templates select="skipped"><xsl:with-param name="testId" select="$testId"/></xsl:apply-templates></td>
            </xsl:when>
            <xsl:otherwise>
                <td>Success</td>
                <td></td>
            </xsl:otherwise>
        </xsl:choose>
        <td>
            <xsl:call-template name="display-time">
                <xsl:with-param name="value" select="@time"/>
            </xsl:call-template>
        </td>
    </tr>
</xsl:template>


<xsl:template match="failure">
    <xsl:param name="testId"/>
    <xsl:call-template name="display-failures">
        <xsl:with-param name="testId" select="$testId"/>
    </xsl:call-template>
</xsl:template>

<xsl:template match="error">
    <xsl:param name="testId"/>
    <xsl:call-template name="display-failures">
        <xsl:with-param name="testId" select="$testId"/>
    </xsl:call-template>
</xsl:template>

<xsl:template match="skipped">
    <xsl:param name="testId"/>
    <xsl:call-template name="display-failures">
        <xsl:with-param name="testId" select="$testId"/>
    </xsl:call-template>
</xsl:template>

<!-- Style for the error, failure and skipped in the testcase template -->
<xsl:template name="display-failures">
    <xsl:param name="testId"/>
    <div>
        <xsl:choose>
            <xsl:when test="not(@message)">N/A</xsl:when>
            <xsl:otherwise>
                <strong><xsl:value-of select="@message"/></strong>
            </xsl:otherwise>
        </xsl:choose>
        <!-- Collapsible stacktrace - only if testId is provided -->
        <xsl:choose>
            <xsl:when test="normalize-space(.) and $testId">
                <div class="stacktrace-container">
                    <button class="stacktrace-toggle">
                        <xsl:attribute name="id">toggle-<xsl:value-of select="$testId"/></xsl:attribute>
                        <xsl:attribute name="onclick">toggleStacktrace('<xsl:value-of select="$testId"/>')</xsl:attribute>
                        Show Details
                    </button>
                    <div class="stacktrace-content">
                        <xsl:attribute name="id">stacktrace-<xsl:value-of select="$testId"/></xsl:attribute>
                        <code>
                            <xsl:call-template name="br-replace">
                                <xsl:with-param name="word" select="."/>
                            </xsl:call-template>
                        </code>
                    </div>
                </div>
            </xsl:when>
            <xsl:when test="normalize-space(.)">
                <!-- For class-level errors without testId, show stacktrace directly -->
                <code>
                    <br/><br/>
                    <xsl:call-template name="br-replace">
                        <xsl:with-param name="word" select="."/>
                    </xsl:call-template>
                </code>
            </xsl:when>
        </xsl:choose>
    </div>
</xsl:template>

<xsl:template name="JS-escape">
    <xsl:param name="string"/>
    <!-- Simple escape for JavaScript - just escape quotes and newlines -->
    <xsl:variable name="apos">'</xsl:variable>
    <xsl:variable name="escaped-apos">\'</xsl:variable>
    <xsl:choose>
        <xsl:when test="contains($string, $apos)">
            <xsl:call-template name="string-replace">
                <xsl:with-param name="string" select="$string"/>
                <xsl:with-param name="from" select="$apos"/>
                <xsl:with-param name="to" select="$escaped-apos"/>
            </xsl:call-template>
        </xsl:when>
        <xsl:otherwise>
            <xsl:value-of select="$string"/>
        </xsl:otherwise>
    </xsl:choose>
</xsl:template>

<xsl:template name="string-replace">
    <xsl:param name="string"/>
    <xsl:param name="from"/>
    <xsl:param name="to"/>
    <xsl:choose>
        <xsl:when test="contains($string, $from)">
            <xsl:value-of select="substring-before($string, $from)"/>
            <xsl:value-of select="$to"/>
            <xsl:call-template name="string-replace">
                <xsl:with-param name="string" select="substring-after($string, $from)"/>
                <xsl:with-param name="from" select="$from"/>
                <xsl:with-param name="to" select="$to"/>
            </xsl:call-template>
        </xsl:when>
        <xsl:otherwise>
            <xsl:value-of select="$string"/>
        </xsl:otherwise>
    </xsl:choose>
</xsl:template>


<!--
    template that will convert a carriage return into a br tag
    @param word the text from which to convert CR to BR tag
-->
<xsl:template name="br-replace">
    <xsl:param name="word"/>
    <xsl:param name="splitlimit">32</xsl:param>
    <xsl:variable name="secondhalfstartindex" select="(string-length($word)+(string-length($word) mod 2)) div 2"/>
    <xsl:variable name="secondhalfword" select="substring($word, $secondhalfstartindex)"/>
    <!-- When word is very big, a recursive replace is very heap/stack expensive, so subdivide on line break after middle of string -->
    <xsl:choose>
      <xsl:when test="(string-length($word) > $splitlimit) and (contains($secondhalfword, '&#xa;'))">
        <xsl:variable name="secondhalfend" select="substring-after($secondhalfword, '&#xa;')"/>
        <xsl:variable name="firsthalflen" select="string-length($word) - string-length($secondhalfword)"/>
        <xsl:variable name="firsthalfword" select="substring($word, 1, $firsthalflen)"/>
        <xsl:variable name="firsthalfend" select="substring-before($secondhalfword, '&#xa;')"/>
        <xsl:call-template name="br-replace">
          <xsl:with-param name="word" select="concat($firsthalfword,$firsthalfend)"/>
        </xsl:call-template>
        <br/>
        <xsl:call-template name="br-replace">
          <xsl:with-param name="word" select="$secondhalfend"/>
        </xsl:call-template>
      </xsl:when>
      <xsl:when test="contains($word, '&#xa;')">
        <xsl:value-of select="substring-before($word, '&#xa;')"/>
        <br/>
        <xsl:call-template name="br-replace">
          <xsl:with-param name="word" select="substring-after($word, '&#xa;')"/>
        </xsl:call-template>
      </xsl:when>
      <xsl:otherwise>
        <xsl:value-of select="$word"/>
      </xsl:otherwise>
    </xsl:choose>
</xsl:template>

<!-- Template to get source module from test module name -->
<xsl:template name="get-source-module">
    <xsl:param name="test-module"/>
    <xsl:choose>
        <xsl:when test="$config/mapping/module-mapping/map[@test=$test-module]">
            <xsl:value-of select="$config/mapping/module-mapping/map[@test=$test-module]/@source"/>
        </xsl:when>
        <xsl:otherwise>
            <!-- Default: remove _test suffix -->
            <xsl:choose>
                <xsl:when test="contains($test-module, '_test')">
                    <xsl:value-of select="substring-before($test-module, '_test')"/>
                </xsl:when>
                <xsl:otherwise>
                    <xsl:value-of select="$test-module"/>
                </xsl:otherwise>
            </xsl:choose>
        </xsl:otherwise>
    </xsl:choose>
</xsl:template>

<!-- Template to get source class from test class name -->
<xsl:template name="get-source-class">
    <xsl:param name="test-class"/>
    <xsl:variable name="suffix-length" select="string-length($test-class-suffix)"/>
    <xsl:variable name="class-length" select="string-length($test-class)"/>
    <xsl:choose>
        <xsl:when test="substring($test-class, $class-length - $suffix-length + 1) = $test-class-suffix">
            <xsl:value-of select="substring($test-class, 1, $class-length - $suffix-length)"/>
        </xsl:when>
        <xsl:otherwise>
            <xsl:value-of select="$test-class"/>
        </xsl:otherwise>
    </xsl:choose>
</xsl:template>

<!-- Template to generate coverage link for a test class -->
<xsl:template name="generate-coverage-link">
    <xsl:param name="test-module"/>
    <xsl:param name="package"/>
    <xsl:param name="class"/>
    
    <!-- Only show coverage links for source packages (org.sandbox), not test-only packages -->
    <xsl:if test="$show-coverage-links = 'true' and string-length($test-module) > 0 and starts-with(concat($package, '.'), 'org.sandbox.')">
        <xsl:variable name="source-class">
            <xsl:call-template name="get-source-class">
                <xsl:with-param name="test-class" select="$class"/>
            </xsl:call-template>
        </xsl:variable>
        
        <!-- Generate link: {github-pages-base}{coverage-base}/{package-path}/{ClassName}.html -->
        <!-- Note: JaCoCo aggregate reports do not include module names in the path -->
        <xsl:variable name="package-path" select="translate($package, '.', '/')"/>
        <xsl:variable name="coverage-url">
            <xsl:value-of select="$github-pages-base"/>
            <xsl:value-of select="$coverage-base"/>
            <xsl:text>/</xsl:text>
            <xsl:value-of select="$package-path"/>
            <xsl:text>/</xsl:text>
            <xsl:value-of select="$source-class"/>
            <xsl:text>.html</xsl:text>
        </xsl:variable>
        
        <xsl:text> </xsl:text>
        <a class="coverage-link">
            <xsl:attribute name="title">View coverage for <xsl:value-of select="$source-class"/></xsl:attribute>
            <xsl:attribute name="href"><xsl:value-of select="$coverage-url"/></xsl:attribute>
            <xsl:attribute name="target">_blank</xsl:attribute>
            📊
        </a>
    </xsl:if>
</xsl:template>

<xsl:template name="display-time">
    <xsl:param name="value"/>
    <xsl:value-of select="format-number($value,'0.000')"/>
</xsl:template>

<xsl:template name="display-percent">
    <xsl:param name="value"/>
    <xsl:value-of select="format-number($value,'0.00%')"/>
</xsl:template>

</xsl:stylesheet>
