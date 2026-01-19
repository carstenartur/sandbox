<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
    <xsl:output method="xml" indent="yes" />
    
    <!-- Copy all elements -->
    <xsl:template match="*">
        <xsl:copy>
            <xsl:apply-templates select="@* | node()" />
        </xsl:copy>
    </xsl:template>
    
    <!-- Copy all attributes -->
    <xsl:template match="@*">
        <xsl:copy />
    </xsl:template>
    
    <!-- Preserve comments -->
    <xsl:template match="comment()">
        <xsl:copy />
    </xsl:template>
    
    <!-- Preserve processing instructions -->
    <xsl:template match="processing-instruction()">
        <xsl:copy />
    </xsl:template>
</xsl:stylesheet>