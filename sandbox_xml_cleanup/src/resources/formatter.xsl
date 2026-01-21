<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
    <xsl:output method="xml" indent="no" />
    
    <!-- Copy elements - empty elements will be self-closing due to output method -->
    <xsl:template match="*">
        <xsl:copy>
            <xsl:apply-templates select="@*"/>
            <xsl:apply-templates select="node()"/>
        </xsl:copy>
    </xsl:template>
    
    <!-- Copy all attributes -->
    <xsl:template match="@*">
        <xsl:copy/>
    </xsl:template>
    
    <!-- Preserve comments -->
    <xsl:template match="comment()">
        <xsl:copy/>
    </xsl:template>
    
    <!-- Preserve processing instructions -->
    <xsl:template match="processing-instruction()">
        <xsl:copy/>
    </xsl:template>
    
    <!-- Preserve text nodes but normalize whitespace-only nodes -->
    <xsl:template match="text()">
        <xsl:if test="normalize-space(.) != ''">
            <xsl:value-of select="."/>
        </xsl:if>
    </xsl:template>
</xsl:stylesheet>