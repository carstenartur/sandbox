package org.sandbox.jdt.internal.corext.fix.helper;

import org.eclipse.ui.IMarkerResolution;
import org.eclipse.ui.IMarkerResolutionGenerator2;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.runtime.CoreException;

public class ExsdMarkerResolutionGenerator implements IMarkerResolutionGenerator2 {

    @Override
    public boolean hasResolutions(IMarker marker) {
        // Überprüfe, ob der Marker vom richtigen Typ ist
        try {
			return "my.exsd.cleanup.marker".equals(marker.getType());
		} catch (CoreException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return false;
    }

    @Override
    public IMarkerResolution[] getResolutions(IMarker marker) {
        // Rückgabe der möglichen Quickfixes
        return new IMarkerResolution[] {
            new ReplaceSpacesWithTabsQuickFix()
        };
    }
}
