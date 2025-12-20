package org.sandbox.jdt.ui.tests.quickfix;
import java.io.UnsupportedEncodingException;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
public class E2 {
	static void bla(Throwable e) throws UnsupportedEncodingException {
		IStatus status = new Status(IStatus.WARNING, "plugin id","important message",null);
		byte[] bytes = "asdf".getBytes("Utf-8");
	}
}
