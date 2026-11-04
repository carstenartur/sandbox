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
    <xsl:param name="trim-documentation" select="false()"/>

    <xsl:template match="/">
        <!-- The PDE writer emits this provenance marker before schemas using
             these unprefixed QName and namespace conventions. Keep all other
             comments, including identical text inside or after the root. -->
        <xsl:variable name="pde-schema" select="
            /*[(self::schema or self::xs:schema) and name() = 'schema'
                and not(@xml:space = 'preserve')]
            /*[name() = 'annotation' and namespace-uri() = namespace-uri(/*)]
            /*[(name() = 'appInfo' or name() = 'appinfo') and namespace-uri() = namespace-uri(/*)]
            /*[name() = 'meta.schema' and namespace-uri() = namespace-uri(/*)]"/>
        <xsl:for-each select="*|processing-instruction()|comment()[
            not($pde-schema and not(preceding-sibling::*)
                and . = ' Schema file written by PDE ')]">
            <xsl:if test="$indent"><xsl:text>&#10;</xsl:text></xsl:if>
            <xsl:apply-templates select="."/>
        </xsl:for-each>
        <xsl:if test="$indent"><xsl:text>&#10;</xsl:text></xsl:if>
    </xsl:template>

    <xsl:template match="*">
        <xsl:param name="padding" select="''"/>
        <xsl:param name="pde-metadata-leaf" select="false()"/>
        <!-- These are the common element-only XSD/EXSD containers. Unknown schema
             constructs, documentation and arbitrary appinfo remain untouched. -->
        <xsl:variable name="schema-container" select="
            /*[self::schema or self::xs:schema]
            and namespace-uri() = namespace-uri(/*)
            and contains('|schema|annotation|element|attribute|complexType|simpleType|sequence|choice|all|restriction|extension|',
                         concat('|', local-name(), '|'))"/>
        <!-- PDE reads these four metadata elements only through their attributes.
             Both spellings are accepted, but appinfo is otherwise arbitrary XML.
             Require recognized, leaf-only metadata in its consumer's annotation
             context before treating any whitespace in the container as layout. -->
        <xsl:variable name="pde-appinfo" select="
            /*[self::schema or self::xs:schema]
            and namespace-uri() = namespace-uri(/*)
            and (local-name() = 'appinfo' or local-name() = 'appInfo')
            and parent::*[local-name() = 'annotation' and namespace-uri() = namespace-uri(/*)]
            and *
            and not(*[namespace-uri() != namespace-uri(/*) or * or text()[normalize-space(.) != '']
                or not((local-name(../../..) = 'schema' and (local-name() = 'meta.schema' or local-name() = 'meta.section'))
                    or (local-name(../../..) = 'element' and local-name() = 'meta.element')
                    or (local-name(../../..) = 'attribute' and local-name() = 'meta.attribute'))])"/>
        <!-- Extension payloads have their own schemas; only their enclosing
             descriptor and extension elements have a known content model here. -->
        <xsl:variable name="descriptor-container" select="
            self::plugin[not(parent::*)] or self::fragment[not(parent::*)]
            or self::feature[not(parent::*)]
            or self::extension[parent::plugin[not(parent::*)] or parent::fragment[not(parent::*)]]"/>
        <!-- Only modernize the PDE metadata vocabulary in its reader context.
             Unknown appinfo payloads may belong to a different consumer. -->
        <xsl:variable name="pde-legacy-appinfo" select="
            $pde-appinfo and local-name() = 'appInfo' and not(text()[normalize-space(.) != ''])"/>
        <!-- PDE normalizes these reference sections on full loading and omits
             them in abbreviated/lazy readers. Overview, element and attribute
             descriptions have raw lazy readers and must retain their boundaries.
             Require the ordinary unqualified PDE vocabulary and section order. -->
        <xsl:variable name="pde-schema" select="/schema/annotation/*[self::appinfo or self::appInfo]/meta.schema"/>
        <xsl:variable name="pde-reference-section" select="self::documentation
            and parent::annotation[parent::schema[not(parent::*)]]
            and count(../node()[not(self::text()[normalize-space(.) = ''])]) = 2
            and preceding-sibling::*[1][self::appinfo or self::appInfo][count(*) = 1 and not(text()[normalize-space(.) != ''])]
                /meta.section[not(*) and not(text()[normalize-space(.) != ''])
                    and (@type = 'since' or @type = 'apiinfo' or @type = 'implementation' or @type = 'copyright')]"/>
        <xsl:variable name="plain-pde-description" select="$trim-documentation and $pde-schema and $pde-reference-section
            and not(node()[not(self::text())]) and normalize-space(.) != ''
            and not(contains(., '&lt;'))
            and not(ancestor-or-self::*[@xml:space][1]/@xml:space = 'preserve')"/>
        <xsl:choose>
            <xsl:when test="$plain-pde-description">
                <xsl:copy>
                    <xsl:copy-of select="@*"/>
                    <xsl:variable name="without-leading">
                        <xsl:call-template name="trim-xml-boundary">
                            <xsl:with-param name="text" select="string(.)"/>
                            <xsl:with-param name="leading" select="true()"/>
                        </xsl:call-template>
                    </xsl:variable>
                    <xsl:call-template name="trim-xml-boundary">
                        <xsl:with-param name="text" select="string($without-leading)"/>
                        <xsl:with-param name="leading" select="false()"/>
                    </xsl:call-template>
                </xsl:copy>
            </xsl:when>
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
                        <appinfo xmlns="http://www.w3.org/2001/XMLSchema">
                            <xsl:copy-of select="namespace::*|@*"/>
                            <xsl:call-template name="format-children">
                                <xsl:with-param name="padding" select="$padding"/>
                                <xsl:with-param name="pde-appinfo" select="$pde-appinfo"/>
                            </xsl:call-template>
                        </appinfo>
                    </xsl:when>
                    <xsl:otherwise>
                        <xsl:element name="{$modern-name}" namespace="{namespace-uri()}">
                            <xsl:copy-of select="namespace::*|@*"/>
                            <xsl:call-template name="format-children">
                                <xsl:with-param name="padding" select="$padding"/>
                                <xsl:with-param name="pde-appinfo" select="$pde-appinfo"/>
                            </xsl:call-template>
                        </xsl:element>
                    </xsl:otherwise>
                </xsl:choose>
            </xsl:when>

            <xsl:when test="($schema-container or $descriptor-container or $pde-appinfo or $pde-metadata-leaf)
                            and not(text()[normalize-space(.) != ''])
                            and not(ancestor-or-self::*[@xml:space][1]/@xml:space = 'preserve')">
                <xsl:copy>
                    <xsl:copy-of select="@*"/>
                    <xsl:call-template name="format-children">
                        <xsl:with-param name="padding" select="$padding"/>
                        <xsl:with-param name="pde-appinfo" select="$pde-appinfo"/>
                    </xsl:call-template>
                </xsl:copy>
            </xsl:when>
            <xsl:otherwise>
                <!-- Preserve all text in unknown, mixed-content and xml:space
                     subtrees, including whitespace-only separators and blanks. -->
                <xsl:copy-of select="."/>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

    <!-- Renamed appinfo must receive the same layout on this pass as canonical
         appinfo, including whitespace-only content in its metadata leaves. -->
    <xsl:template name="format-children">
        <xsl:param name="padding" select="''"/>
        <xsl:param name="pde-appinfo" select="false()"/>
        <xsl:variable name="children" select="node()[not(self::text()[normalize-space(.) = ''])]"/>
        <xsl:for-each select="$children">
            <xsl:if test="$indent">
                <xsl:text>&#10;</xsl:text>
                <xsl:value-of select="concat($padding, '&#9;')"/>
            </xsl:if>
            <xsl:apply-templates select=".">
                <xsl:with-param name="padding" select="concat($padding, '&#9;')"/>
                <xsl:with-param name="pde-metadata-leaf" select="$pde-appinfo"/>
            </xsl:apply-templates>
        </xsl:for-each>
        <xsl:if test="$indent and $children">
            <xsl:text>&#10;</xsl:text>
            <xsl:value-of select="$padding"/>
        </xsl:if>
    </xsl:template>

    <xsl:template match="comment()|processing-instruction()|text()">
        <xsl:copy-of select="."/>
    </xsl:template>
    <!-- Binary subdivision bounds recursion depth even for very long whitespace
         runs. normalize-space is only a predicate; output uses original chunks. -->
    <xsl:template name="trim-xml-boundary">
        <xsl:param name="text"/>
        <xsl:param name="leading"/>
        <xsl:choose>
            <xsl:when test="string-length($text) &lt;= 1">
                <xsl:if test="normalize-space($text) != ''"><xsl:value-of select="$text"/></xsl:if>
            </xsl:when>
            <xsl:otherwise>
                <xsl:variable name="half" select="floor(string-length($text) div 2)"/>
                <xsl:variable name="first" select="substring($text, 1, $half)"/>
                <xsl:variable name="last" select="substring($text, $half + 1)"/>
                <xsl:choose>
                    <xsl:when test="$leading">
                        <xsl:choose>
                            <xsl:when test="normalize-space($first) = ''">
                                <xsl:call-template name="trim-xml-boundary">
                                    <xsl:with-param name="text" select="$last"/>
                                    <xsl:with-param name="leading" select="true()"/>
                                </xsl:call-template>
                            </xsl:when>
                            <xsl:otherwise>
                                <xsl:call-template name="trim-xml-boundary">
                                    <xsl:with-param name="text" select="$first"/>
                                    <xsl:with-param name="leading" select="true()"/>
                                </xsl:call-template>
                                <xsl:value-of select="$last"/>
                            </xsl:otherwise>
                        </xsl:choose>
                    </xsl:when>
                    <xsl:otherwise>
                        <xsl:choose>
                            <xsl:when test="normalize-space($last) = ''">
                                <xsl:call-template name="trim-xml-boundary">
                                    <xsl:with-param name="text" select="$first"/>
                                    <xsl:with-param name="leading" select="false()"/>
                                </xsl:call-template>
                            </xsl:when>
                            <xsl:otherwise>
                                <xsl:value-of select="$first"/>
                                <xsl:call-template name="trim-xml-boundary">
                                    <xsl:with-param name="text" select="$last"/>
                                    <xsl:with-param name="leading" select="false()"/>
                                </xsl:call-template>
                            </xsl:otherwise>
                        </xsl:choose>
                    </xsl:otherwise>
                </xsl:choose>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>
</xsl:stylesheet>
