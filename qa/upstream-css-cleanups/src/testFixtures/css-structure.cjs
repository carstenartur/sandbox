// Parser adapter only: Java/JUnit compares the returned structural fingerprints.
const fs = require("node:fs");
const path = require("node:path");
const toolRequire = require("node:module").createRequire(path.join(process.argv[2], "stylelint/package.json"));
const postcss = toolRequire("postcss");
const values = toolRequire("postcss-value-parser");
const selectors = toolRequire("postcss-selector-parser");

const colorProperties = /^(?:color|background(?:-color|-image)?|border(?:-(?:top|right|bottom|left))?(?:-color)?|outline(?:-color)?|(?:box|text)-shadow|fill|stroke|caret-color|accent-color|column-rule(?:-color)?|text-decoration(?:-color)?|swt-titlebar-color)$/;
const colorFunctions = /^(?:var|color-mix|(?:repeating-)?(?:linear|radial|conic)-gradient)$/;

function valueNodes(nodes, colors = false) {
  return nodes.map(node => {
    let value = node.value;
    if (node.type === "space") value = " ";
    if (colors && node.type === "word" && /^(?:#[\da-f]{3}|#[\da-f]{4}|#[\da-f]{6}|#[\da-f]{8})$/i.test(value)) value = value.toLowerCase();
    const result = {type: node.type, value};
    if (node.nodes) result.nodes = valueNodes(node.nodes, colors && colorFunctions.test(node.value.toLowerCase()));
    if (node.unclosed) result.unclosed = true;
    return result;
  });
}

function selectorNode(node) {
  const result = {type: node.type};
  for (const key of ["value", "attribute", "operator", "namespace", "insensitive"]) {
    if (node[key] !== undefined) result[key] = node[key];
  }
  if (node.nodes) result.nodes = node.nodes.map(selectorNode);
  return result;
}

function structure(node) {
  const result = {type: node.type};
  if (node.type === "rule") result.selector = selectorNode(selectors().astSync(node.selector));
  if (node.type === "decl") {
    result.property = node.prop;
    result.important = Boolean(node.important);
    result.value = valueNodes(values(node.value).nodes, colorProperties.test(node.prop.toLowerCase()));
  }
  if (node.type === "atrule") {
    result.name = node.name;
    result.parameters = valueNodes(values(node.params).nodes);
  }
  if (node.type === "comment") result.text = node.text.replace(/\s+/g, " ").trim();
  if (node.nodes) result.nodes = node.nodes.map(structure);
  return result;
}

const file = process.argv[3];
process.stdout.write(JSON.stringify(structure(postcss.parse(fs.readFileSync(file, "utf8"), {from: file}))));
