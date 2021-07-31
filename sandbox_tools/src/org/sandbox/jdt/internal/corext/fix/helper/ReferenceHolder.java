package org.sandbox.jdt.internal.corext.fix.helper;

import java.util.HashMap;

public class ReferenceHolder extends HashMap<String,Hit> {

	public Hit possibleHit(String string) {
		Hit hit=new Hit();
		put(string, hit);
		return hit;		
	}
}