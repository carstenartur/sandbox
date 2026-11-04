<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:xs="http://www.w3.org/2001/XMLSchema"
                exclude-result-prefixes="xs" version="1.0">
    <!-- Serializer indentation is unsafe for mixed content. Generate layout only
         where the vocabulary establishes element-only content. -->
    <xsl:output method="xml" indent="no"/>
    <xsl:param name="indent" select="false()"/>
    <!-- Explicit user choice for the Eclipse 3.4+ schema writer convention. -->
    <xsl:param name="modernize-appinfo" select="false()"/>

    <xsl:template match="/">
        <xsl:for-each select="*|comment()|processing-instruction()">
            <xsl:if test="$indent"><xsl:text>&#10;</xsl:text></xsl:if>
            <xsl:apply-templates select="."/>
        </xsl:for-each>
        <xsl:if test="$indent"><xsl:text>&#10;</xsl:text></xsl:if>
    </xsl:template>

    <xsl:template match="*">
        <xsl:param name="padding" select="''"/>
        <!-- These are the common element-only XSD/EXSD containers. Unknown schema
             constructs, documentation and appinfo remain untouched subtrees. -->
        <xsl:variable name="schema-container" select="
            /*[self::schema or self::xs:schema]
            and namespace-uri() = namespace-uri(/*)
            and contains('|schema|annotation|element|attribute|complexType|simpleType|sequence|choice|all|restriction|extension|',
                         concat('|', local-name(), '|'))"/>
        <!-- Extension payloads have their own schemas; only their enclosing
             descriptor and extension elements have a known content model here. -->
        <xsl:variable name="descriptor-container" select="
            self::plugin[not(parent::*)] or self::fragment[not(parent::*)]
            or self::feature[not(parent::*)]
            or self::extension[parent::plugin[not(parent::*)] or parent::fragment[not(parent::*)]]"/>
        <!-- Only modernize the PDE metadata vocabulary in its reader context.
             Unknown appinfo payloads may belong to a different consumer. -->
        <xsl:variable name="pde-legacy-appinfo" select="
            /*[self::schema or self::xs:schema]
            and namespace-uri() = namespace-uri(/*) and local-name() = 'appInfo'
            and parent::*[local-name() = 'annotation' and namespace-uri() = namespace-uri(/*)]
            and * and not(text()[normalize-space(.) != ''])
            and not(*[namespace-uri() != namespace-uri(/*) or * or text()[normalize-space(.) != '']
                or not((local-name(../../..) = 'schema' and (local-name() = 'meta.schema' or local-name() = 'meta.section'))
                    or (local-name(../../..) = 'element' and local-name() = 'meta.element')
                    or (local-name(../../..) = 'attribute' and local-name() = 'meta.attribute'))])"/>
        <xsl:choose>
            <xsl:when test="$modernize-appinfo and $pde-legacy-appinfo
                            and not(ancestor-or-self::*[@xml:space][1]/@xml:space = 'preserve')">
                <xsl:variable name="modern-name">
                    <xsl:if test="contains(name(), ':')">
                        <xsl:value-of select="substring-before(name(), ':')"/><xsl:text>:</xsl:text>
                    </xsl:if>
                    <xsl:text>appinfo</xsl:text>
                </xsl:variable>
                <xsl:choose>
                    <!-- XSLTC invents a prefix for a dynamic unprefixed QName
                         with a nonempty namespace. PDE readers compare qName,
                         so retain the default XSD namespace explicitly. -->
                    <xsl:when test="namespace-uri() = 'http://www.w3.org/2001/XMLSchema' and not(contains(name(), ':'))">
                        <appinfo xmlns="http://www.w3.org/2001/XMLSchema"><xsl:copy-of select="namespace::*|@*|node()"/></appinfo>
                    </xsl:when>
                    <xsl:otherwise>
                        <xsl:element name="{$modern-name}" namespace="{namespace-uri()}">
                            <xsl:copy-of select="namespace::*|@*|node()"/>
                        </xsl:element>
                    </xsl:otherwise>
                </xsl:choose>
            </xsl:when>
            <xsl:when test="($schema-container or $descriptor-container)
                            and not(text()[normalize-space(.) != ''])
                            and not(ancestor-or-self::*[@xml:space][1]/@xml:space = 'preserve')">
                <xsl:variable name="children" select="node()[not(self::text()[normalize-space(.) = ''])]"/>
                <xsl:copy>
                    <xsl:copy-of select="@*"/>
                    <xsl:for-each select="$children">
                        <xsl:if test="$indent">
                            <xsl:text>&#10;</xsl:text>
                            <xsl:value-of select="concat($padding, '&#9;')"/>
                        </xsl:if>
                        <xsl:apply-templates select=".">
                            <xsl:with-param name="padding" select="concat($padding, '&#9;')"/>
                        </xsl:apply-templates>
                    </xsl:for-each>
                    <xsl:if test="$indent and $children">
                        <xsl:text>&#10;</xsl:text>
                        <xsl:value-of select="$padding"/>
                    </xsl:if>
                </xsl:copy>
            </xsl:when>
            <xsl:otherwise>
                <!-- Preserve all text in unknown, mixed-content and xml:space
                     subtrees, including whitespace-only separators and blanks. -->
                <xsl:copy-of select="."/>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

    <xsl:template match="comment()|processing-instruction()|text()">
        <xsl:copy-of select="."/>
    </xsl:template>
</xsl:stylesheet>
