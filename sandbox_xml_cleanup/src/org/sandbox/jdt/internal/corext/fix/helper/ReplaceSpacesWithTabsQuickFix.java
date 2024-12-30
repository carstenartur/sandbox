package org.sandbox.jdt.internal.corext.fix.helper;

import org.eclipse.ui.IMarkerResolution;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;

public class ReplaceSpacesWithTabsQuickFix implements IMarkerResolution {
    @Override
    public String getLabel() {
        return "Replace 4 spaces with a tab";
    }

    @Override
    public void run(IMarker marker) {
        try {
            // Datei holen
            IResource resource = marker.getResource();
            Path filePath = resource.getLocation().toFile().toPath();
            String content = Files.readString(filePath, StandardCharsets.UTF_8);

            // Ersetze 4 Leerzeichen durch Tabs
            content = content.replace("    ", "\t");

            // Schreibe den aktualisierten Inhalt zurück
            Files.writeString(filePath, content, StandardCharsets.UTF_8);

            // Entferne den Marker
            marker.delete();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
